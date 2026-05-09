import { Injectable, UnauthorizedException } from '@nestjs/common';
import { PassportStrategy } from '@nestjs/passport';
import { ExtractJwt, Strategy, StrategyOptions } from 'passport-jwt';
import * as jwksRsa from 'jwks-rsa';

type TokenPayload = {
  sub: string;
  preferred_username?: string;
  email?: string;
  realm_access?: { roles?: string[] };
  resource_access?: Record<string, { roles?: string[] }>;
  aud?: string | string[];
  azp?: string;
  iss?: string;
};

/** Parse raw Cookie header without external deps. */
function parseCookies(raw: string): Record<string, string> {
  return Object.fromEntries(
    raw.split(';').map((c) => {
      const [k, ...v] = c.trim().split('=');
      return [k, decodeURIComponent(v.join('='))];
    }),
  );
}

/** Get Bearer token from Authorization header, falling back to dv_access_token cookie. */
function extractToken(req: any): string | undefined {
  const fromHeader = ExtractJwt.fromAuthHeaderAsBearerToken()(req);
  if (fromHeader) return fromHeader;

  // Keycloak cookie-auth flow: read from dv_access_token cookie
  const rawCookies = req.headers.cookie ?? '';
  const cookies = parseCookies(rawCookies);
  return cookies['dv_access_token'];
}

function normalizeUrl(value: string): string {
  return value.replace(/\/$/, '');
}

function getKeycloakIssuers(baseUrl: string, realm: string): string[] {
  const configuredIssuers = (process.env.KEYCLOAK_ISSUER ?? '')
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean)
    .map(normalizeUrl);
  const internalIssuer = `${normalizeUrl(baseUrl)}/realms/${realm}`;

  return Array.from(new Set([...configuredIssuers, internalIssuer]));
}

function getKeycloakJwksUri(baseUrl: string, realm: string): string {
  const explicitJwksUri = process.env.KEYCLOAK_JWKS_URI?.trim();
  if (explicitJwksUri) {
    return explicitJwksUri;
  }

  const jwksBaseUrl = normalizeUrl(
    process.env.KEYCLOAK_JWKS_BASE_URL ?? baseUrl,
  );
  return `${jwksBaseUrl}/realms/${realm}/protocol/openid-connect/certs`;
}

@Injectable()
export class JwtStrategy extends PassportStrategy(Strategy) {
  private readonly audience?: string;

  constructor() {
    const baseUrl = process.env.KEYCLOAK_BASE_URL!;
    const realm = process.env.KEYCLOAK_REALM!;
    const issuers = getKeycloakIssuers(baseUrl, realm);
    const jwksUri = getKeycloakJwksUri(baseUrl, realm);
    const audience = process.env.KEYCLOAK_AUDIENCE;

    const opts: StrategyOptions = {
      jwtFromRequest: extractToken as any,
      // Keycloak in Docker may have clock drift causing tokens to appear expired
      // minutes after issuance. Signature is still verified by JWKS, so we bypass
      // automatic expiry check and validate manually with a generous tolerance below.
      ignoreExpiration: true,
      algorithms: ['RS256'],
      issuer: issuers,
      secretOrKeyProvider: jwksRsa.passportJwtSecret({
        cache: true,
        rateLimit: true,
        jwksRequestsPerMinute: 10,
        jwksUri,
      }),
    };

    super(opts);
    this.audience = audience;
  }

  private normalizePayload(payload: TokenPayload | any) {
    if (this.audience) {
      const audiences = Array.isArray(payload.aud)
        ? payload.aud
        : payload.aud
          ? [payload.aud]
          : [];

      if (!audiences.includes(this.audience) && payload.azp !== this.audience) {
        throw new UnauthorizedException('Invalid token audience');
      }
    }

    const roles = new Set<string>(payload.realm_access?.roles ?? []);
    if (roles.has('co')) {
      roles.add('compliance_officer');
    }

    return {
      sub: payload.sub,
      username: payload.preferred_username ?? payload.username,
      email: payload.email,
      firstName: payload.given_name,
      lastName: payload.family_name,
      displayName:
        payload.name ??
        ([payload.given_name, payload.family_name].filter(Boolean).join(' ') ||
          undefined),
      roles: Array.from(roles),
      raw: payload,
    };
  }

  /** Called by passport-jwt after verifying the JWT. */
  validate(payload: TokenPayload | any) {
    // Manually validate expiry with generous clock tolerance (5 min) to handle
    // Keycloak Docker clock drift that causes valid tokens to appear expired.
    if (payload.exp) {
      const now = Math.floor(Date.now() / 1000);
      const CLOCK_DRIFT_TOLERANCE_SECONDS = 300;
      if (payload.exp + CLOCK_DRIFT_TOLERANCE_SECONDS < now) {
        throw new UnauthorizedException('Token expired');
      }
    }
    return this.normalizePayload(payload);
  }
}
