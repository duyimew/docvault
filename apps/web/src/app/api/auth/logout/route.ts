import { NextRequest, NextResponse } from 'next/server';

const KC_BASE =
  process.env.KEYCLOAK_INTERNAL_BASE_URL ??
  process.env.KEYCLOAK_BASE_URL ??
  'http://localhost:8080';
const KC_REALM = process.env.KEYCLOAK_REALM ?? 'docvault';
const CLIENT_ID = process.env.KEYCLOAK_CLIENT_ID ?? 'docvault-gateway';
const CLIENT_SECRET = process.env.KEYCLOAK_CLIENT_SECRET ?? 'dev-gateway-secret';
const FRONTEND_URL = process.env.FRONTEND_URL ?? 'http://localhost:3006';

/**
 * GET /api/auth/logout
 *
 * Back-channel logout: calls Keycloak's logout endpoint server-side using the
 * refresh token to terminate the SSO session, then redirects straight to /login.
 * This avoids showing the Keycloak "Do you want to log out?" confirmation page.
 */
export async function GET(req: NextRequest) {
  const refreshToken = req.cookies.get('dv_refresh_token')?.value;

  // Revoke session on Keycloak server-side (best-effort)
  if (refreshToken) {
    try {
      await fetch(
        `${KC_BASE}/realms/${KC_REALM}/protocol/openid-connect/logout`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body: new URLSearchParams({
            client_id: CLIENT_ID,
            client_secret: CLIENT_SECRET,
            refresh_token: refreshToken,
          }),
        },
      );
    } catch {
      // If Keycloak is unreachable we still clear local cookies and redirect
    }
  }

  const response = NextResponse.redirect(`${FRONTEND_URL}/login`);

  // Clear all auth cookies
  response.cookies.delete('dv_access_token');
  response.cookies.delete('dv_refresh_token');
  response.cookies.delete('dv_user');
  response.cookies.delete('kc_state');

  return response;
}
