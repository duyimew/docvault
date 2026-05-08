import { NextRequest, NextResponse } from 'next/server';

const KC_BASE =
  process.env.KEYCLOAK_INTERNAL_BASE_URL ??
  process.env.KEYCLOAK_BASE_URL ??
  'http://localhost:8080';
const KC_REALM = process.env.KEYCLOAK_REALM ?? 'docvault';
const CLIENT_ID = process.env.KEYCLOAK_CLIENT_ID ?? 'docvault-gateway';
const CLIENT_SECRET = process.env.KEYCLOAK_CLIENT_SECRET ?? 'dev-gateway-secret';
const FRONTEND_URL = process.env.FRONTEND_URL ?? 'http://localhost:3006';

/** Only set Secure flag when actually serving over HTTPS */
function isSecure(req: NextRequest): boolean {
  return req.nextUrl.protocol === 'https:' ||
    req.headers.get('x-forwarded-proto') === 'https';
}

interface JwtPayload {
  sub: string;
  preferred_username?: string;
  username?: string;
  email?: string;
  realm_access?: { roles?: string[] };
}

export async function GET(req: NextRequest) {
  const { searchParams } = new URL(req.url);
  const code = searchParams.get('code');
  const state = searchParams.get('state');
  const error = searchParams.get('error');

  if (error) {
    return NextResponse.redirect(`${FRONTEND_URL}/login?error=${encodeURIComponent(error)}`);
  }

  // Validate state (CSRF protection)
  const savedState = req.cookies.get('kc_state')?.value;
  if (!savedState || savedState !== state) {
    return NextResponse.redirect(`${FRONTEND_URL}/login?error=invalid_state`);
  }

  if (!code) {
    return NextResponse.redirect(`${FRONTEND_URL}/login?error=no_code`);
  }

  try {
    const tokenRes = await fetch(
      `${KC_BASE}/realms/${KC_REALM}/protocol/openid-connect/token`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({
          grant_type: 'authorization_code',
          client_id: CLIENT_ID,
          client_secret: CLIENT_SECRET,
          code,
          redirect_uri: `${FRONTEND_URL}/api/auth/callback`,
        }),
      },
    );

    if (!tokenRes.ok) {
      throw new Error(`Token exchange failed: ${tokenRes.status}`);
    }

    const tokens = await tokenRes.json();
    const { access_token, refresh_token, expires_in } = tokens;

    const payload = JSON.parse(atob(access_token.split('.')[1])) as JwtPayload;
    const user = {
      sub: payload.sub,
      username: payload.preferred_username ?? payload.username,
      email: payload.email,
      roles: payload.realm_access?.roles ?? [],
    };

    const response = NextResponse.redirect(`${FRONTEND_URL}/login?auth=ok`);

    response.cookies.delete('kc_state');

    response.cookies.set('dv_access_token', access_token, {
      httpOnly: true,
      secure: isSecure(req),
      sameSite: 'lax',
      maxAge: expires_in ?? 3600,
      path: '/',
    });

    if (refresh_token) {
      response.cookies.set('dv_refresh_token', refresh_token, {
        httpOnly: true,
        secure: isSecure(req),
        sameSite: 'lax',
        maxAge: 7 * 24 * 60 * 60,
        path: '/',
      });
    }

    response.cookies.set('dv_user', JSON.stringify(user), {
      httpOnly: false,
      secure: isSecure(req),
      sameSite: 'lax',
      maxAge: 10,
      path: '/',
    });

    return response;
  } catch (err) {
    console.error('Keycloak token exchange failed:', err);
    return NextResponse.redirect(`${FRONTEND_URL}/login?error=token_exchange_failed`);
  }
}
