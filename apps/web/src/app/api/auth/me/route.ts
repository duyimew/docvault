import { NextRequest, NextResponse } from 'next/server';

interface JwtPayload {
  sub?: string;
  preferred_username?: string;
  username?: string;
  email?: string;
  realm_access?: { roles?: string[] };
  exp?: number;
}

function decodeJwtPayload(token: string): JwtPayload | null {
  try {
    const payload = token.split('.')[1];
    if (!payload) return null;

    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
    const padded = normalized.padEnd(normalized.length + ((4 - normalized.length % 4) % 4), '=');
    return JSON.parse(Buffer.from(padded, 'base64').toString('utf8')) as JwtPayload;
  } catch {
    return null;
  }
}

function userFromToken(accessToken: string) {
  const payload = decodeJwtPayload(accessToken);
  if (!payload?.sub) return null;

  const roles = new Set(payload.realm_access?.roles ?? []);
  if (roles.has('co')) {
    roles.add('compliance_officer');
  }

  return {
    sub: payload.sub,
    username: payload.preferred_username ?? payload.username ?? 'unknown',
    email: payload.email,
    roles: Array.from(roles),
  };
}

export async function GET(req: NextRequest) {
  const accessToken = req.cookies.get('dv_access_token')?.value;
  const userCookie = req.cookies.get('dv_user')?.value;

  if (!accessToken) {
    return NextResponse.json({ error: 'Not authenticated' }, { status: 401 });
  }

  if (userCookie) {
    try {
      const user = JSON.parse(userCookie);
      return NextResponse.json({ accessToken, user });
    } catch {
      // Fall back to the JWT payload below.
    }
  }

  const user = userFromToken(accessToken);
  if (!user) {
    return NextResponse.json({ error: 'Invalid session' }, { status: 401 });
  }

  return NextResponse.json({ accessToken, user });
}
