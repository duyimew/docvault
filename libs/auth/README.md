# `@docvault/auth`

Shared authentication primitives for all DocVault services. Eliminates duplicate JWT strategy, roles guard, and decorator code across 6 services.

## What's included

| Export | Description |
|---|---|
| `JwtStrategy` | Passport strategy for Keycloak JWKS + RS256 JWT validation |
| `RolesGuard` | NestJS guard that checks user roles from the JWT |
| `Roles(...roles)` | Decorator to declare required roles on a route |
| `AuthModule` | Re-exportable NestJS module |
| `ServiceUser`, `RequestContext` | Shared TypeScript types |
| `buildActorId()` | Derive actor ID from a ServiceUser |
| `buildRequestContext()` | Build RequestContext from an Express Request |
| `ROLES` | Canonical role constants |
| `READER_ROLES` | Roles with implicit read access to published documents |

## Usage in a service

```bash
# Add as a local dependency (monorepo)
pnpm add -w @docvault/auth
```

```typescript
// src/auth/auth.module.ts
import { AuthModule } from '@docvault/auth';

@Module({
  imports: [AuthModule],
  // ...
})
export class SomeModule {}

// src/some.controller.ts
import { Roles } from '@docvault/auth';

@Post(':docId/submit')
@UseGuards(AuthGuard('jwt'), RolesGuard)
@Roles('editor', 'admin')   // requires editor OR admin
async submit(@Req() req: any) {
  const user = req.user; // ServiceUser shape
}
```

## Migration from inline auth

1. Replace `src/auth/roles.decorator.ts` import with `@docvault/auth`
2. Replace `src/auth/roles.guard.ts` import with `@docvault/auth`
3. Replace `src/auth/jwt.strategy.ts` import with `@docvault/auth`
4. Replace `src/auth/auth.module.ts` to use `AuthModule` from `@docvault/auth`
5. Update `src/common/request-context.ts` to import types from `@docvault/auth`
6. Remove the local auth files

## Environment variables (required in every service)

```env
KEYCLOAK_BASE_URL=http://localhost:8080
KEYCLOAK_ISSUER=http://localhost:8080/realms/docvault # optional when token issuer differs from internal URL
KEYCLOAK_REALM=docvault
KEYCLOAK_AUDIENCE=docvault-gateway   # optional
DOWNLOAD_GRANT_SECRET=...             # used by metadata-service only
```

## Status

✅ Production-ready. All 6 services currently use inlined copies; migration to this shared lib is recommended but not yet done.
