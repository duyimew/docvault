# DocVault SCA Triage

Updated: 2026-05-09

This record tracks the current OWASP Dependency Check / npm audit handling for the demo branch. The Jenkins artifact remains the source of truth for the formal report, while this file records fix decisions and accepted exceptions.

## Policy

- Critical or High findings in direct runtime dependencies must be fixed before demo unless a written exception exists.
- Medium and Low findings may be accepted for the MVP demo when they are dev-tooling-only, unreachable in the deployed container, or require a risky major framework upgrade.
- Exceptions must include package, CVE/advisory, dependency path, reason, mitigation and follow-up action.

## Fixed in this pass

| Package | Previous | New | Reason |
|---|---:|---:|---|
| `mammoth` | `1.8.0` | `1.12.0` | Fixes CVE-2025-11849 directory traversal in DOCX conversion. |
| `dompurify` | `3.2.6` | `3.4.2` | Fixes multiple DOMPurify XSS/prototype-pollution advisories. |
| `follow-redirects` | transitive | `1.16.0` override | Fixes auth header leak advisory through Axios transitive dependency. |
| `flatted` | transitive | `3.4.2` override | Fixes prototype pollution advisory in ESLint cache dependency path. |
| `fast-uri` | transitive | `3.1.2` override | Fixes host confusion advisory through AJV/schema-utils paths. |
| `fast-xml-builder` | transitive | `1.2.0` override | Fixes XML builder attribute/comment parsing advisories. |
| `picomatch` | transitive | `4.0.4` override | Fixes ReDoS/method injection advisories in glob matching paths. |
| `postcss` | transitive | `8.5.14` override | Fixes CSS stringify XSS advisory. |
| `glob` | transitive | `10.5.0` override | Fixes glob CLI command injection advisory where compatible. |
| `tmp` | transitive | `0.2.4` override | Fixes temporary file symlink advisory in CLI tooling path. |
| `@hono/node-server` | transitive | `1.19.13` override | Fixes repeated-slash middleware bypass advisory in Prisma dev dependency path. |
| `hono` | transitive | `4.12.18` override | Fixes cache/JWT/JSX advisories in Prisma dev dependency path. |

## Current exceptions

| Package | Severity | CVE/advisory | Path | Reason | Mitigation / follow-up |
|---|---|---|---|---|---|
| `@nestjs/core` | Medium | CVE-2026-35515 | Direct NestJS services on v10 | Patched range starts at NestJS 11.1.18. Upgrading all services to NestJS 11 is a framework migration and should not be mixed with the demo stabilization PR. | Keep services behind gateway auth/RBAC. Plan a dedicated NestJS 11 migration after demo with full service regression tests. |
| `file-type` | Medium | CVE-2026-31808, CVE-2026-32630 | Transitive via `@nestjs/common` | Patched range is `>=21.3.1` / `>=21.3.2`, which may require ESM/runtime compatibility changes. This is not directly used by DocVault request handlers. | Track under NestJS upgrade work; retest upload/download handling after framework dependency refresh. |
| `ajv` v6 | Medium | CVE-2025-69873 | Transitive via webpack/schema-utils dev tooling | A blanket override from AJV 6 to AJV 8 can break webpack/schema-utils consumers. Finding is limited to build/dev tooling in this repo. | Keep Jenkins SCA as UNSTABLE for demo if this remains; resolve by upgrading Nest CLI/webpack toolchain in a separate tooling PR. |
| `webpack` | Low | CVE-2025-68458, CVE-2025-68157 | Transitive via Nest CLI build tooling | Low severity and build-time-only. Upgrading through Nest CLI 11 is larger than the demo hardening pass. | Upgrade Nest CLI/webpack toolchain in the same post-demo tooling refresh. |
| `js-yaml` v3/v4.1.0 | Medium | CVE-2025-64718 | Transitive via Swagger/Nest tooling paths | Existing lockfile already includes `js-yaml@4.1.1`, but older transitive paths remain controlled by upstream packages. | Verify after lockfile refresh; otherwise resolve through NestJS Swagger/tooling upgrade. |

Current local `pnpm audit` residual count after this pass: five Moderate advisories and two Low advisories, grouped into the exceptions above. There are no High or Critical findings in the local npm audit result.

## Evidence to collect from Jenkins

- `dependency-check-report/*.html`
- `dependency-check-report/*.json`
- Screenshot of SCA stage result and build summary
- Short note listing fixed packages and accepted exceptions above
