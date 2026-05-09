'use client';

import React, { createContext, useContext, useState, useCallback, useEffect } from 'react';
import type { Session } from '@/features/auth/auth.types';
import type { AuthContextValue } from '@/features/auth/auth.types';
import type { UserRole } from '@/types/enums';
import { saveSession, loadSession, clearSession } from '@/lib/auth/session';
import { hasRole, hasAnyRole } from '@/lib/auth/roles';
import { queryClient } from '@/providers/query-provider';

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [session, setSession] = useState<Session | null>(null);
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => {
    let cancelled = false;

    async function hydrateSession() {
      const storedSession = loadSession() as Session | null;
      if (storedSession) {
        if (!cancelled) {
          setSession(storedSession);
          setHydrated(true);
        }
        return;
      }

      try {
        const response = await fetch('/api/auth/me', { cache: 'no-store' });
        if (!response.ok) {
          throw new Error('No active cookie session');
        }

        const data = await response.json();
        const cookieSession: Session = {
          accessToken: data.accessToken,
          user: data.user,
        };

        saveSession(cookieSession as Parameters<typeof saveSession>[0]);
        if (!cancelled) {
          setSession(cookieSession);
        }
      } catch {
        clearSession();
        if (!cancelled) {
          setSession(null);
        }
      } finally {
        if (!cancelled) {
          setHydrated(true);
        }
      }
    }

    hydrateSession();

    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback((newSession: Session) => {
    saveSession(newSession as Parameters<typeof saveSession>[0]);
    setSession(newSession);
    setHydrated(true);
  }, []);

  const logout = useCallback(() => {
    clearSession();
    queryClient.clear();        // Wipe all cached queries so no stale data leaks into the next session
    setSession(null);
    setHydrated(true);
  }, []);

  const value: AuthContextValue = {
    session,
    isAuthenticated: session !== null,
    hydrated,
    login,
    logout,
    hasRole: (role: UserRole) => hasRole(session as Parameters<typeof hasRole>[0], role),
    hasAnyRole: (roles: UserRole[]) => hasAnyRole(session as Parameters<typeof hasAnyRole>[0], roles),
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
