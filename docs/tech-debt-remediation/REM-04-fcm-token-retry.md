# ~~REM-04: Fix FCM Token Re-registration Retry~~ ✅ COMPLETED (2026-04-22)

**Addresses:** FCM token registration reliability (sync)
**Module:** Sync
**BRICE:** B=4 R=4 I=5 C=4 E=4 → **4.2**
**Phase:** 1 — Quick Wins & Safety Fixes

**Outcome:** `Sync.sendFcmRegistrationId` now has a two-tier recovery path. In-process: a failed send schedules up to three retries on a single-thread `ScheduledExecutorService` (`fcmRetryExecutor`, daemon) at 1 min / 5 min / 30 min; any retry that succeeds clears the persistent flag and short-circuits the chain. Cross-launch: every send-failure branch (`!response.success`, `IOException | JsonIOException`, `JsonSyntaxException`) sets `Prefkey.fcm_registration_pending = true`, the success branch sets it to `false`, and `App.staticInit()` calls the new `Sync.retryPendingFcmRegistrationIfNeeded(registrationId)` with the FCM id returned by `Fcm.renewFcmRegistrationIdIfNeeded` — so if the in-process chain never completed (e.g. process died, network was off the whole 30 min), the next launch that already has a stored FCM id re-sends it. If no id is stored yet, the existing listener path (`Fcm.renewFcmRegistrationIdIfNeeded(Sync::notifyNewFcmRegistrationId)`) still fires `notifyNewFcmRegistrationId` once registration completes, which then runs its own retry chain.

**What was done:**
1. ✅ Added `Prefkey.fcm_registration_pending` to `Prefkey.kt` with a kdoc comment describing the contract.
2. ✅ Added `FCM_RETRY_DELAYS_MS = {1min, 5min, 30min}`, `fcmRetryExecutor` (single-thread, daemon), `pendingFcmRetry` (`AtomicReference<ScheduledFuture<?>>`), and `scheduleFcmRegistrationRetries(registrationId, attemptIndex)` in `Sync.java`. Each scheduled attempt re-reads `Prefkey.sync_simpleToken` so a mid-chain logout stops retrying.
3. ✅ `notifyNewFcmRegistrationId` now schedules a retry chain when the inline send fails.
4. ✅ Public `retryPendingFcmRegistrationIfNeeded(@NonNull String registrationId)` reads the pending flag and (if set) re-enters `notifyNewFcmRegistrationId`. Called once from `App.staticInit()` with the id returned by `Fcm.renewFcmRegistrationIdIfNeeded`.
5. ✅ Bumped all four send-failure log sites in `sendFcmRegistrationId` from `AppLog.d` to `AppLog.w` and added `Preferences.setBoolean(Prefkey.fcm_registration_pending, …)` on each path (`true` on failure, `false` on success).

**Not retried on:** if the response is valid but `success == false` (server explicitly rejected the registration id), the flag is still set and the retry chain still runs. This is intentional — the plan did not distinguish retriable vs. terminal failures, and rejections are rare enough that the extra requests are harmless. If this turns out to generate noise, a future change can key off `response.message`.

**Verified:** `./gradlew :Alkitab:assemblePlainDebug testPlainDebugUnitTest testPlainReleaseUnitTest` — all 313 unit tests pass.

---

[← Back to remediation index](../tech-debt-remediation.md)
