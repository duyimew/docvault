import { NextRequest, NextResponse } from 'next/server';

const DEFAULT_GATEWAY_URL =
  process.env.NODE_ENV === 'production'
    ? 'http://docvault-gateway:3000'
    : 'http://localhost:3000';

const GATEWAY_URL = process.env.GATEWAY_URL ?? DEFAULT_GATEWAY_URL;

type RouteContext = {
  params: { path: string[] } | Promise<{ path: string[] }>;
};

function buildUpstreamUrl(pathSegments: string[], search: string): string {
  const path = pathSegments.join('/');
  return `${GATEWAY_URL}/api/${path}${search}`;
}

function responseHeaders(upstreamHeaders: Headers): Headers {
  const headers = new Headers(upstreamHeaders);
  headers.delete('connection');
  headers.delete('keep-alive');
  headers.delete('transfer-encoding');
  headers.delete('content-encoding');
  headers.delete('content-length');
  return headers;
}

async function proxy(req: NextRequest, pathSegments: string[]) {
  const headers = new Headers(req.headers);
  headers.set('host', new URL(GATEWAY_URL).host);

  const hasBody = !['GET', 'HEAD'].includes(req.method);
  const body = hasBody ? await req.arrayBuffer() : undefined;

  let response: Response;
  try {
    response = await fetch(buildUpstreamUrl(pathSegments, req.nextUrl.search), {
      method: req.method,
      headers,
      body,
      cache: 'no-store',
      redirect: 'manual',
    });
  } catch {
    return NextResponse.json({ message: 'Gateway proxy error' }, { status: 502 });
  }

  return new NextResponse(response.body, {
    status: response.status,
    headers: responseHeaders(response.headers),
  });
}

export async function GET(
  req: NextRequest,
  { params }: RouteContext,
) {
  const { path } = await Promise.resolve(params);
  return proxy(req, path);
}

export async function POST(
  req: NextRequest,
  { params }: RouteContext,
) {
  const { path } = await Promise.resolve(params);
  return proxy(req, path);
}

export async function PUT(
  req: NextRequest,
  { params }: RouteContext,
) {
  const { path } = await Promise.resolve(params);
  return proxy(req, path);
}

export async function PATCH(
  req: NextRequest,
  { params }: RouteContext,
) {
  const { path } = await Promise.resolve(params);
  return proxy(req, path);
}

export async function DELETE(
  req: NextRequest,
  { params }: RouteContext,
) {
  const { path } = await Promise.resolve(params);
  return proxy(req, path);
}
