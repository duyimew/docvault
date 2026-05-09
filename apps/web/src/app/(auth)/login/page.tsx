'use client';

import { useEffect, useState, useMemo } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useAuth } from '@/lib/auth/auth-context';
import { ROUTES } from '@/lib/constants/routes';
import { Shield, Key, GitBranch, Zap, Lock } from 'lucide-react';

function deleteCookie(name: string) {
  if (typeof document === 'undefined') return;
  document.cookie = `${name}=; Max-Age=0; path=/`;
}

export default function LoginPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { login, logout } = useAuth();

  const [ssoLoading, setSsoLoading] = useState(false);

  const errorParam = searchParams.get('error');
  const authStatus = searchParams.get('auth');
  const loggedOut = searchParams.get('logged_out');
  const callbackError = useMemo(() => {
    return errorParam ? `Login failed: ${errorParam}` : null;
  }, [errorParam]);

  // ── Handle Keycloak callback redirects ─────────────────────────────────────
  useEffect(() => {
    if (loggedOut) {
      logout();
      deleteCookie('dv_user');
      deleteCookie('kc_state');
      router.replace('/login');
      return;
    }

    if (errorParam) {
      // Clean the URL
      router.replace('/login');
      return;
    }

    if (authStatus === 'ok') {
      // /api/auth/callback set HttpOnly cookies on this origin.
      // Call /api/auth/me (server-side) to read them and hydrate localStorage.
      fetch('/api/auth/me')
        .then((r) => {
          if (!r.ok) throw new Error('Session invalid');
          return r.json();
        })
        .then((data) => {
          login({
            accessToken: data.accessToken,
            user: data.user,
          });
          // Clear only the short-lived user bootstrap cookie. Keep HttpOnly
          // token cookies so the app can rehydrate after refreshes or redirects.
          deleteCookie('dv_user');
          router.push(ROUTES.DASHBOARD);
        })
        .catch(() => {
          router.replace('/login');
        });
    }
  }, [authStatus, errorParam, loggedOut, login, logout, router]);

  function handleSSOLogin() {
    setSsoLoading(true);
    // Navigate to gateway auth endpoint — browser will redirect to Keycloak
    window.location.href = '/api/auth/login';
  }

  return (
    <div className="min-h-screen flex" style={{ background: 'var(--login-page-bg)' }}>
      {/* Left panel — Brand */}
      <div
        className="hidden lg:flex flex-col justify-between relative w-[460px] shrink-0 overflow-hidden"
        style={{
          background: 'linear-gradient(160deg, rgba(15,23,42,0.97) 0%, rgba(15,23,42,1) 60%, rgba(30,41,59,0.95) 100%)',
          backdropFilter: 'blur(24px)',
        }}
      >
        <div className="absolute inset-0 pointer-events-none overflow-hidden">
          <div className="absolute -top-24 -left-24 w-80 h-80 rounded-full bg-blue-500/10 blur-3xl" />
          <div className="absolute top-1/3 right-0 w-60 h-60 rounded-full bg-violet-500/10 blur-3xl translate-x-1/2" />
          <div className="absolute bottom-0 left-1/4 w-96 h-96 rounded-full bg-indigo-500/5 blur-3xl translate-y-1/2" />
          <div
            className="absolute inset-0 opacity-[0.03]"
            style={{
              backgroundImage:
                'linear-gradient(rgba(255,255,255,0.5) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,0.5) 1px, transparent 1px)',
              backgroundSize: '40px 40px',
            }}
          />
        </div>

        <div className="relative flex flex-col justify-between h-full p-10 z-10">
          <div>
            <div className="flex items-center gap-3 mb-16">
              <div className="relative h-11 w-11 rounded-xl overflow-hidden">
                <div className="absolute inset-0 bg-gradient-to-br from-blue-500 to-violet-600" />
                <div className="absolute inset-0.5 rounded-[8px] bg-[#0F172A] flex items-center justify-center">
                  <Shield className="h-5 w-5 text-blue-400" />
                </div>
              </div>
              <div>
                <h1 className="text-xl font-bold text-white tracking-tight">DocVault</h1>
                <p className="text-[10px] uppercase tracking-widest font-medium" style={{ color: 'var(--login-hint)' }}>
                  Document System
                </p>
              </div>
            </div>

            <div className="mb-8">
              <h2 className="text-[28px] font-semibold text-white leading-[1.25] mb-4 tracking-tight">
                Secure document management for{' '}
                <span className="bg-gradient-to-r from-blue-400 to-violet-400 bg-clip-text text-transparent">
                  modern enterprises.
                </span>
              </h2>
              <p className="text-sm leading-relaxed" style={{ color: 'var(--login-hint)' }}>
                Control access, manage workflows, and ensure compliance across every document
                lifecycle stage.
              </p>
            </div>
          </div>

          <div className="space-y-3">
            {[
              {
                Icon: Key,
                label: 'Role-based access control',
                glow: 'rgba(59,130,246,0.4)',
              },
              { Icon: GitBranch, label: 'Immutable audit trail', glow: 'rgba(124,58,237,0.4)' },
              {
                Icon: Zap,
                label: 'Workflow state management',
                glow: 'rgba(59,130,246,0.3)',
              },
              { Icon: Lock, label: 'Secure file storage', glow: 'rgba(34,197,94,0.4)' },
            ].map((feat) => (
              <div key={feat.label} className="flex items-center gap-3 group">
                <div
                  className="h-8 w-8 rounded-xl flex items-center justify-center shrink-0 transition-transform group-hover:scale-105"
                  style={{
                    background: `linear-gradient(135deg, ${feat.glow.replace('0.4', '0.15').replace('0.3', '0.12')}, ${feat.glow.replace('0.4', '0.05').replace('0.3', '0.04')})`,
                    border: `1px solid ${feat.glow.replace('0.4', '0.25').replace('0.3', '0.2')}`,
                  }}
                >
                  <feat.Icon size={15} className="transition-colors" style={{ color: 'var(--login-hint)' }} />
                </div>
                <span className="text-sm transition-colors group-hover:text-white" style={{ color: 'var(--login-sub)' }}>
                  {feat.label}
                </span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Right panel */}
      <div className="flex-1 flex items-center justify-center p-8">
        <div className="w-full max-w-[420px]">
          {/* Logo mobile */}
          <div className="flex items-center gap-3 mb-8 lg:hidden">
            <div className="relative h-9 w-9 rounded-xl overflow-hidden">
              <div className="absolute inset-0 bg-gradient-to-br from-blue-500 to-violet-600" />
              <div className="absolute inset-0.5 rounded-[8px] bg-[#0F172A] flex items-center justify-center">
                <Shield className="h-4 w-4 text-blue-400" />
              </div>
            </div>
            <span className="text-xl font-bold" style={{ color: 'var(--login-heading)' }}>DocVault</span>
          </div>

          <div
            className="rounded-2xl p-8"
            style={{
              background: 'var(--login-card-bg)',
              backdropFilter: 'blur(16px)',
              border: `1px solid var(--login-card-border)`,
              boxShadow: 'var(--login-card-shadow)',
            }}
          >
            <div className="mb-6">
              <h2 className="text-2xl font-semibold mb-1" style={{ color: 'var(--login-heading)' }}>Welcome back</h2>
              <p className="text-sm" style={{ color: 'var(--login-sub)' }}>Sign in to your secure document portal</p>
            </div>

            {callbackError && (
              <div
                className="mb-4 p-3 rounded-xl text-sm"
                style={{
                  background: 'var(--login-error-bg)',
                  border: '1px solid var(--login-error-border)',
                  color: 'var(--login-error-text)',
                }}
              >
                {callbackError}
              </div>
            )}

            {/* SSO Login */}
            <button
              onClick={handleSSOLogin}
              disabled={ssoLoading}
              className="w-full py-3 rounded-xl text-white text-sm font-semibold transition-all active:scale-[0.98] btn-primary disabled:opacity-60 disabled:cursor-not-allowed mb-6"
            >
              {ssoLoading ? 'Redirecting to SSO...' : 'Sign in with SSO'}
            </button>

            <div className="relative mb-6">
              <div className="absolute inset-0 flex items-center">
                <div className="w-full" style={{ borderTop: '1px solid var(--login-divider)' }} />
              </div>
              <div className="relative flex justify-center text-xs">
                <span className="px-2" style={{ background: 'var(--login-divider-label-bg)', color: 'var(--login-hint)' }}>
                  Use your Keycloak credentials
                </span>
              </div>
            </div>

            {/* Credentials info */}
            <div className="space-y-2 text-xs mb-4" style={{ color: 'var(--login-sub)' }}>
              <p className="font-medium" style={{ color: 'var(--login-heading)' }}>Demo accounts:</p>
              <div className="grid grid-cols-2 gap-1">
                {[
                  { user: 'viewer1', role: 'viewer' },
                  { user: 'editor1', role: 'editor' },
                  { user: 'approver1', role: 'approver' },
                  { user: 'co1', role: 'compliance' },
                ].map(({ user, role }) => (
                  <div key={user} className="flex items-center gap-1.5">
                    <span className="font-mono" style={{ color: 'var(--login-demo-user)' }}>{user}</span>
                    <span style={{ color: 'var(--login-demo-sep)' }}>/</span>
                    <span style={{ color: 'var(--login-demo-pwd)' }}>Passw0rd!</span>
                    <span style={{ color: 'var(--login-demo-sep)' }}>·</span>
                    <span style={{ color: 'var(--color-primary)' }}>{role}</span>
                  </div>
                ))}
              </div>
            </div>
          </div>

          <p className="text-center text-xs mt-6" style={{ color: 'var(--login-hint)' }}>
            DocVault — Secure Document Management System
          </p>
        </div>
      </div>
    </div>
  );
}
