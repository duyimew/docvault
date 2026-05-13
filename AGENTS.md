# Repository Guidelines

## Project Structure & Module Organization

DocVault is a pnpm/Turbo monorepo. The Next.js frontend lives in `apps/web`, with pages under `src/app`, reusable UI in `src/components`, feature APIs/hooks in `src/features`, and shared utilities in `src/lib`. NestJS services live in `services/*-service` and `services/gateway`, each with source in `src`, optional E2E tests in `test`, and service-specific `.env.example` files. Shared packages are in `libs/auth`, `libs/throttler`, and `libs/contracts`. Infrastructure and deployment assets are in `infra`, CI pipeline code is in `ci` and `vars`, and product/API documentation is in `docs`.

## Build, Test, and Development Commands

- `pnpm install`: install workspace dependencies using the locked pnpm version.
- `pnpm dev`: run all workspace `dev` tasks in parallel through Turbo.
- `pnpm build`: compile all buildable packages and apps.
- `pnpm lint`: run workspace lint tasks.
- `pnpm test`: run workspace Jest tests.
- `pnpm test:e2e`: run the scripted end-to-end check in `scripts/e2e-check.mjs`.
- `pnpm --filter web dev`: start the frontend on port `3006`.
- `pnpm --filter metadata-service prisma:generate`: regenerate Prisma client after schema changes.

## Coding Style & Naming Conventions

Use TypeScript for application code and keep formatting delegated to Prettier (`pnpm format`) and package ESLint scripts. Follow existing NestJS names: `*.module.ts`, `*.controller.ts`, `*.service.ts`, DTOs under `dto/`, and specs as `*.spec.ts`. In React, prefer PascalCase components, kebab-case route folders, and feature-local files like `documents.api.ts`, `documents.hooks.ts`, and `documents.types.ts`. Keep shared cross-service code in `libs` instead of duplicating guards, decorators, or contracts.

## Testing Guidelines

Backend unit tests use Jest with `ts-jest`; place tests beside source as `*.spec.ts`. Service E2E tests use `test/app.e2e-spec.ts` and `jest-e2e.json` where present. Run focused tests with `pnpm --filter gateway test` or `pnpm --filter metadata-service test:e2e`; run `pnpm test` before broad changes. Coverage commands exist on several NestJS services as `test:cov`; no global threshold is currently enforced, so add meaningful tests for changed behavior.

## Commit & Pull Request Guidelines

Recent history follows Conventional Commits: `fix: ...`, `fix(ci): ...`, `chore(pipeline): ...`, and `refactor(ci): ...`. Keep subjects imperative and scoped when helpful, for example `fix(workflow): reject stale approvals`.

Pull requests should describe the change, list verification commands, link related issues, and include screenshots for UI changes. Note any required `.env` additions, Prisma migrations, infra changes, or CI/security scan impacts.

## Security & Configuration Tips

Do not commit secrets. Copy from the nearest `.env.example` and document new variables there. Keep OpenAPI contracts in `libs/contracts/openapi` aligned with gateway and service changes.
