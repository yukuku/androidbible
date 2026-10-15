# REM-50: Centralize Sync API Calls and Failure Handling

**Module:** Sync (`Sync`, `SyncAdapter`, connection helpers)
**Status:** Open.
**BRICE:** Not scored; scope retry and failure semantics before scheduling.

## Problem

`Sync` constructs endpoint paths for `register_gcm_client`, `create_own_user`, `login_own_user`, `forgot_password`, and `change_password`. `SyncAdapter` repeats `/sync/api/sync` calls for the individual sync sets. Endpoint construction and response/error handling are spread across callers instead of one API boundary.

Sync failures commonly reach callers as `NotOkException` without consistent distinctions between transport, validation/authentication, and server failures. FCM registration has backoff through [REM-04](REM-04-fcm-token-retry.md), but the data-sync calls lack an equivalent transient-failure retry policy.

## Remaining work

1. Centralize endpoint construction and request/response handling in a small sync API layer. Preserve the effective server-prefix override used for diagnostics; a Retrofit migration is optional.
2. Classify failures so callers can distinguish retryable transport/server failures from authentication or validation failures.
3. Define bounded backoff for transient data-sync failures. Check delta/revision semantics and idempotency before replaying requests, especially when the server may have applied a response that the client did not receive.
4. Verify each sync set and account operation against representative success, rejected, malformed, and transient-failure responses.

Queue delivery and shared-state races are tracked in [REM-34](REM-34-sync-delivery.md); cross-device conflict behavior is tracked in [REM-43](REM-43-sync-conflict-handling.md). Neither is resolved by centralizing URLs alone.

---

[← Back to remediation index](../tech-debt-remediation.md)
