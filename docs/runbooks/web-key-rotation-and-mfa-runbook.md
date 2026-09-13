# DocVault Web Key Rotation and MFA Runbook

Scope: Web/runtime controls only. This runbook excludes CI/CD, GitOps, cluster policy, and registry operations.

## MFA Demo Posture

The local Keycloak realm enables the `CONFIGURE_TOTP` required action in `infra/keycloak/realm-docvault.json`.

Two dedicated demo users require TOTP setup on first interactive login:

- `co-mfa-demo` with roles `co` and `compliance_officer`
- `admin-mfa-demo` with role `admin`

The existing automation users `co1` and `admin1` intentionally do not require TOTP. They are used by password-grant smoke tests and E2E evidence scripts; forcing TOTP on those users would break non-interactive test runs before the users complete OTP enrollment.

Demo steps:

1. Start the local infrastructure and import the realm.
2. Open Keycloak login for the DocVault realm.
3. Log in as `co-mfa-demo` or `admin-mfa-demo` with the local demo password.
4. Keycloak should require OTP configuration before the user can continue.
5. Capture the OTP setup screen and successful post-enrollment login as MFA evidence.

Production note: in a non-demo realm, require TOTP or stronger MFA for all admin and compliance officer accounts. Keep separate non-human service accounts for automation.

## Grant Token Rotation Model

DocVault uses short-lived signed grants for preview and download flows.

Metadata-service signs new grants with the current key id:

- `GRANT_TOKEN_CURRENT_KID`
- `DOWNLOAD_GRANT_SECRET_<kid>`
- `PREVIEW_GRANT_SECRET_<kid>`

Document-service accepts only the current and previous key ids:

- `GRANT_TOKEN_CURRENT_KID`
- `GRANT_TOKEN_PREVIOUS_KID`
- `DOWNLOAD_GRANT_SECRET_<current|previous>`
- `PREVIEW_GRANT_SECRET_<current|previous>`

The current local example uses:

```text
GRANT_TOKEN_CURRENT_KID=2026_05
GRANT_TOKEN_PREVIOUS_KID=2026_04
DOWNLOAD_GRANT_SECRET_2026_05=replace-with-current-download-grant-secret
DOWNLOAD_GRANT_SECRET_2026_04=replace-with-previous-download-grant-secret
PREVIEW_GRANT_SECRET_2026_05=replace-with-current-preview-grant-secret
PREVIEW_GRANT_SECRET_2026_04=replace-with-previous-preview-grant-secret
```

## Zero-Downtime Rotation Procedure

Assume the active key is `2026_05` and the new key is `2026_06`.

1. Generate strong random secrets for both grant types:
   - `DOWNLOAD_GRANT_SECRET_2026_06`
   - `PREVIEW_GRANT_SECRET_2026_06`
2. Deploy document-service with:
   - `GRANT_TOKEN_CURRENT_KID=2026_06`
   - `GRANT_TOKEN_PREVIOUS_KID=2026_05`
   - both `2026_06` and `2026_05` grant secrets present
3. Deploy metadata-service with:
   - `GRANT_TOKEN_CURRENT_KID=2026_06`
   - `GRANT_TOKEN_PREVIOUS_KID=2026_05`
   - both `2026_06` and `2026_05` grant secrets present
4. Verify new preview/download grants include `kid=2026_06`.
5. Wait longer than the maximum grant TTL. The current runtime grant TTL is 300 seconds, so wait at least 5 minutes plus deployment skew.
6. Remove `2026_05` from `GRANT_TOKEN_PREVIOUS_KID` and remove old secrets from both services.
7. Run the targeted grant-token tests and Web E2E download/preview checks.

## Verification Checklist

- New grants are signed with the current `kid`.
- Existing grants signed by the previous `kid` remain valid during the TTL grace period.
- Unknown or removed `kid` values are rejected.
- Expired grants are rejected.
- Compliance officer remains blocked from preview, stream, presign, and download.
- MFA demo users are forced through OTP setup on first interactive login.
