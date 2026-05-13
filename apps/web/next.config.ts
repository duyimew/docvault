import type { NextConfig } from "next";

const GATEWAY_URL = process.env.GATEWAY_URL ?? 'http://localhost:3000';

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
