import type { NextConfig } from "next";

const DEFAULT_GATEWAY_URL =
  process.env.NODE_ENV === 'production'
    ? 'http://docvault-gateway:3000'
    : 'http://localhost:3000';

const GATEWAY_URL = process.env.GATEWAY_URL ?? DEFAULT_GATEWAY_URL;

const nextConfig: NextConfig = {
  output: 'standalone',

  // Proxy browser API calls to the gateway service internally.
  // This avoids exposing the gateway via NodePort/LoadBalancer and
  // eliminates hardcoded gateway URLs in the frontend build.
  async rewrites() {
    return [
      {
        // Exclude Next.js API routes (auth, health) from proxying
        source: '/api/:path((?!auth|health).*)',
        destination: `${GATEWAY_URL}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
