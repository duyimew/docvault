import { NextRequest, NextResponse } from 'next/server';

export async function GET(req: NextRequest) {
  const accessToken = req.cookies.get('dv_access_token')?.value;

  if (!accessToken) {
    return NextResponse.json({ error: 'Not authenticated' }, { status: 401 });
  }

  try {
    const defaultGatewayUrl =
      process.env.NODE_ENV === 'production'
        ? 'http://docvault-gateway:3000'
        : 'http://localhost:3000';
    const gatewayUrl = process.env.GATEWAY_URL ?? defaultGatewayUrl;
    const response = await fetch(`${gatewayUrl}/api/users/profile`, {
      headers: {
        Authorization: `Bearer ${accessToken}`,
      },
      credentials: 'include',
      cache: 'no-store',
    });

    if (!response.ok) {
      return NextResponse.json({ error: 'Failed to fetch profile' }, { status: response.status });
    }

    const data = await response.json();
    return NextResponse.json(data);
  } catch {
    return NextResponse.json({ error: 'Internal error' }, { status: 500 });
  }
}
