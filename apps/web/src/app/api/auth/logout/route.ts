import { NextRequest, NextResponse } from 'next/server';

const KC_BROWSER_BASE =
  process.env.KEYCLOAK_BROWSER_BASE_URL ??
  process.env.KEYCLOAK_BASE_URL ??
  'http://localhost:8080';
const KC_REALM = process.env.KEYCLOAK_REALM ?? 'docvault';
const CLIENT_ID = process.env.KEYCLOAK_CLIENT_ID ?? 'docvault-gateway';

/**
 * GET /api/auth/logout
 *
 * Front-channel logout: redirect the browser through Keycloak so the SSO
 * cookies on the Keycloak origin are cleared, then return to /login.
 */
export async function GET(req: NextRequest) {
  const idToken = req.cookies.get('dv_id_token')?.value;
  const postLogoutRedirectUri = `${req.nextUrl.origin}/login?logged_out=1`;
  const params = new URLSearchParams({
    client_id: CLIENT_ID,
    post_logout_redirect_uri: postLogoutRedirectUri,
  });

  if (idToken) {
    params.set('id_token_hint', idToken);
  }

  const keycloakLogoutUrl =
    `${KC_BROWSER_BASE}/realms/${KC_REALM}/protocol/openid-connect/logout?${params}`;
  const response = NextResponse.redirect(keycloakLogoutUrl);

  // Clear all auth cookies
  response.cookies.delete('dv_access_token');
  response.cookies.delete('dv_refresh_token');
  response.cookies.delete('dv_id_token');
  response.cookies.delete('dv_user');
  response.cookies.delete('kc_state');

  return response;
}
