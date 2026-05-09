import { Injectable, UnauthorizedException } from '@nestjs/common';
import { PassportStrategy } from '@nestjs/passport';
import { ExtractJwt, Strategy } from 'passport-jwt';
import * as jwksRsa from 'jwks-rsa';

type KeycloakAccessToken = {
  sub: string;
  exp?: number;
  preferred_username?: string;
  email?: string;
  realm_access?: { roles?: string[] };
  resource_access?: Record<string, { roles?: string[] }>;
  aud?: string | string[];
  azp?: string;
  iss?: string;
};

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

    super({
      jwtFromRequest: ExtractJwt.fromAuthHeaderAsBearerToken(),
      issuer: issuers,
      algorithms: ['RS256'],
      // Keycloak in Docker may have clock drift causing tokens to appear expired
      // minutes after issuance. Signature is still verified by JWKS, so we bypass
      // automatic expiry check and validate manually with a generous tolerance below.
      ignoreExpiration: true,
      secretOrKeyProvider: jwksRsa.passportJwtSecret({
        cache: true,
        rateLimit: true,
        jwksRequestsPerMinute: 10,
        jwksUri,
      }),
    });

    this.audience = audience;
  }

  validate(payload: KeycloakAccessToken) {
    // Manually validate expiry with generous clock tolerance (5 min) to handle
    // Keycloak Docker clock drift that causes valid tokens to appear expired.
    if (payload.exp) {
      const now = Math.floor(Date.now() / 1000);
      const CLOCK_DRIFT_TOLERANCE_SECONDS = 300;
      if (payload.exp + CLOCK_DRIFT_TOLERANCE_SECONDS < now) {
        throw new UnauthorizedException('Token expired');
      }
    }

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

    const roles = new Set(payload.realm_access?.roles ?? []);
    if (roles.has('co')) {
      roles.add('compliance_officer');
    }

    return {
      sub: payload.sub,
      username: payload.preferred_username,
      email: payload.email,
      roles: Array.from(roles),
      raw: payload,
    };
  }
}
