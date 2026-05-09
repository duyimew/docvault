import { NextRequest, NextResponse } from 'next/server';
import { getRequestOrigin } from '../request-origin';

const KC_BROWSER_BASE =
  process.env.KEYCLOAK_BROWSER_BASE_URL ??
  process.env.KEYCLOAK_BASE_URL ??
  'http://localhost:8080';
const KC_REALM = process.env.KEYCLOAK_REALM ?? 'docvault';
const CLIENT_ID = process.env.KEYCLOAK_CLIENT_ID ?? 'docvault-gateway';

export async function GET(req: NextRequest) {
  const state = Math.random().toString(36).slice(2);
  const nonce = Math.random().toString(36).slice(2);
  const callbackUrl = `${getRequestOrigin(req)}/api/auth/callback`;

  const authUrl = `${KC_BROWSER_BASE}/realms/${KC_REALM}/protocol/openid-connect/auth?${new URLSearchParams({
    client_id: CLIENT_ID,
    redirect_uri: callbackUrl,
    response_type: 'code',
    scope: 'openid profile email',
    state,
    nonce,
  })}`;

  const response = NextResponse.redirect(authUrl);

  response.cookies.set('kc_state', state, {
    httpOnly: true,
    sameSite: 'lax',
    maxAge: 5 * 60,
    path: '/',
  });

  return response;
}
