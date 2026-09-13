# Thu muc `services`

Thu muc nay chua cac backend service cua DocVault.

## Trang thai hien tai

Tat ca service duoi day deu da co source code NestJS chay that:

| Service | Port mac dinh | Vai tro |
|--------|---------------|--------|
| `gateway` | `3000` | Xac thuc JWT, RBAC, proxy vao downstream service |
| `metadata-service` | `3001` | Metadata, ACL, workflow history, download authorization |
| `document-service` | `3002` | Upload/download blob, MinIO, presign/stream |
| `workflow-service` | `3003` | Submit/approve/reject/archive |
| `audit-service` | `3004` | Append/query audit event, hash chain |
| `notification-service` | `3005` | Notification sink cho dev |

## Cach chay nhanh

Mỗi service co file `.env.example` rieng. Can copy thanh `.env` truoc khi chay.

Lenh chay watch mode:

```bash
pnpm --filter metadata-service start:dev
pnpm --filter audit-service start:dev
pnpm --filter document-service start:dev
pnpm --filter notification-service start:dev
pnpm --filter workflow-service start:dev
pnpm --filter gateway start:dev
```

Huong dan chay full stack chi tiet nam o `../docs/runbooks/RUN_PROJECT.md`.
