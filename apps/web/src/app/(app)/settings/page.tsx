'use client';

import { useAuth } from '@/lib/auth/auth-context';
import { PageShell } from '@/components/layout/page-shell';
import { RoleBadge } from '@/components/badges/role-badge';
import { User, Globe, Shield, KeyRound, AtSign, Hash } from 'lucide-react';
import { UserRole } from '@/types/auth';
import { cn } from '@/lib/utils/cn';
import { env } from '@/config/env';

function SectionLabel({ children }: { children: React.ReactNode }) {
  return (
    <div className="mt-10 mb-4 flex items-center gap-3">
      <div className="h-px flex-1" style={{ background: 'var(--border-soft)' }} />
      <span className="text-[11px] font-semibold uppercase tracking-widest text-[var(--text-faint)]">
        {children}
      </span>
      <div className="h-px flex-1" style={{ background: 'var(--border-soft)' }} />
    </div>
  );
}

interface InfoCardProps {
  icon: React.ReactNode;
  label: string;
  value: string;
  mono?: boolean;
  muted?: boolean;
}

function InfoCard({ icon, label, value, mono, muted }: InfoCardProps) {
  return (
    <div
      className={cn(
        'flex items-start gap-3 rounded-xl border px-4 py-3.5',
        'bg-[var(--bg-card)] transition-all duration-200',
        'hover:border-[var(--color-primary)]/30 hover:shadow-[0_0_0_1px_var(--color-primary)/10,0_2px_8px_var(--color-primary-glow)]',
      )}
      style={{ borderColor: 'var(--border-soft)' }}
    >
      <div className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-[var(--bg-muted)]">
        {icon}
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-[11px] font-medium uppercase tracking-wider text-[var(--text-faint)]">{label}</p>
        <p
          className={cn(
            'mt-0.5 truncate text-sm font-medium',
            mono ? 'tabular-nums text-xs tracking-tight' : '',
            muted ? 'text-[var(--text-faint)]' : 'text-[var(--text-strong)]',
          )}
        >
          {value || '—'}
        </p>
      </div>
    </div>
  );
}

export default function SettingsPage() {
  const { session } = useAuth();
  const user = session?.user;

  return (
    <PageShell
      title="System Information"
      description="Session details and environment configuration."
    >
      {/* ── Session Info ────────────────────────────────────── */}
      <div className="animate-in delay-1">
        <SectionLabel>Login Session</SectionLabel>
        <div className="grid gap-3 sm:grid-cols-2">
        <InfoCard
          icon={<AtSign size={15} className="text-[var(--color-primary)]" />}
          label="Username"
          value={user?.username ?? user?.sub ?? '—'}
          mono
        />
        <InfoCard
          icon={<KeyRound size={15} className="text-[var(--color-primary)]" />}
          label="Session Type"
          value={user ? 'Authenticated (Keycloak SSO)' : 'Demo / Not logged in'}
        />
        <InfoCard
          icon={<Hash size={15} className="text-[var(--color-primary)]" />}
          label="User ID (sub)"
          value={user?.sub ?? '—'}
          mono
          muted
        />
      </div>
      </div>

      {/* ── Roles ───────────────────────────────────────────── */}
      <div className="animate-in delay-2">
        <SectionLabel>Roles &amp; Permissions</SectionLabel>
        <div
          className="relative overflow-hidden rounded-xl border p-5"
          style={{ background: 'var(--bg-card)', borderColor: 'var(--border-soft)' }}
        >
          <div className="flex items-center gap-4">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-[var(--bg-muted)]">
              <Shield size={18} className="text-[var(--text-muted)]" />
            </div>
            {user?.roles && user.roles.length > 0 ? (
              <div className="flex flex-wrap gap-2">
                {user.roles.map((role) => (
                  <RoleBadge key={role} role={role as UserRole} size="md" />
                ))}
              </div>
            ) : (
              <p className="text-sm text-[var(--text-muted)]">No roles have been assigned.</p>
            )}
          </div>
        </div>
      </div>

      {/* ── Environment ──────────────────────────────────────── */}
      <div className="animate-in delay-3">
        <SectionLabel>Environment</SectionLabel>
        <div className="grid gap-3 sm:grid-cols-2">
        <InfoCard
          icon={<User size={15} className="text-[var(--color-primary)]" />}
          label="Application"
          value={env.APP_NAME}
        />
        <InfoCard
          icon={<Globe size={15} className="text-[var(--color-primary)]" />}
          label="API Gateway URL"
          value={env.API_BASE_URL}
          mono
          muted
        />
      </div>
      </div>
    </PageShell>
  );
}
