import type { NextRequest } from 'next/server';

function firstHeaderValue(value: string | null): string | undefined {
  return value?.split(',')[0]?.trim() || undefined;
}

function isWildcardHost(host: string): boolean {
  return host.startsWith('0.0.0.0') || host.startsWith('[::]');
}

export function getRequestOrigin(req: NextRequest): string {
  const forwardedHost = firstHeaderValue(req.headers.get('x-forwarded-host'));
  const host = forwardedHost ?? req.headers.get('host') ?? '';

  if (!host || isWildcardHost(host)) {
    const fallback = process.env.FRONTEND_URL?.replace(/\/$/, '');
    if (fallback) return fallback;
  }

  const proto =
    firstHeaderValue(req.headers.get('x-forwarded-proto')) ??
    req.nextUrl.protocol.replace(/:$/, '') ??
    'http';

  return `${proto}://${host}`;
}
