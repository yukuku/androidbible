# REM-38: Reader Lifecycle Fixes

**Source:** 2026-07 code audit, verified against implementation on 2026-10-15
**Module:** `IsiActivity`, `ShareUrl`
**BRICE:** B=3 R=3 I=4 C=4 E=3 → **3.4**
**Phase:** 2

**Status:** Complete on 2026-10-15.

## Confirmed findings and implementation

- Restoring a saved ARI called `History.add`, replacing the entry's GID and timestamp and making the next history save eligible for sync. Only a fresh intent launch adds opening history.
- The initial back/forward entry remains necessary: `BackForwardListController` owns a new activity-local list. Initializing that list does not write history or trigger sync. Skipping initialization on restoration would leave the first subsequent navigation without a source entry. Preserving the entire back/forward list across recreation is separate from this tactical fix.
- The `lid` intent parser called `jumpToAri` before returning the destination for normal opening. It returns the same result shape as the ARI branch without navigation side effects.
- `ShareUrl` observes the activity lifecycle, cancels its request, and dismisses the dialog on destruction without invoking UI callbacks. Completion checks both `isFinishing` and `isDestroyed`. Dismissal unregisters the observer, and response bodies close even when cancellation wins the race.
- History clicks ignore `RecyclerView.NO_POSITION` and capture the destination before dismissing the dialog.

## Validation

`IsiActivityIntentTest` covers LID parsing, saved-state history preservation, immediate highlight repainting, and detached history-row clicks. `ShareUrlLifecycleTest` covers destruction during a request, a completion already queued at destruction, ordinary user cancellation, and successful completion with response cleanup. Local verification uses `assemblePlainDebug` and relevant `testPlainDebugUnitTest` classes.

---

[← Back to remediation index](../tech-debt-remediation.md)
