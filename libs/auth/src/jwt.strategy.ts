import { Injectable, UnauthorizedException } from '@nestjs/common';
import { PassportStrategy } from '@nestjs/passport';
import { ExtractJwt, Strategy } from 'passport-jwt';
import * as jwksRsa from 'jwks-rsa';
import { KeycloakAccessToken, ServiceUser } from './types';

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
      ignoreExpiration: false,
      secretOrKeyProvider: jwksRsa.passportJwtSecret({
        cache: true,
        rateLimit: true,
        jwksRequestsPerMinute: 10,
        jwksUri,
      }),
    });

    this.audience = audience;
  }

  validate(payload: KeycloakAccessToken): ServiceUser {
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
    // Normalize "co" shorthand to full role name
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
