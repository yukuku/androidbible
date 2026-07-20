# REM-38: Reader Lifecycle Fixes (History Pollution, Double Navigation, Dialog Leak)

**Source:** 2026-07 code audit
**Module:** Main reader (`IsiActivity`, `ShareUrl`, `VerseActionModeController`)
**BRICE:** B=3 R=3 I=4 C=4 E=3 → **3.4**
**Phase:** 2 — Architecture Improvements (2026-07 audit)

**Status:** Not started.

**Steps:**
1. Distinguish "restored after rotation" from "launched via intent" in `IsiActivity.onCreate` — restore the saved ari without `history.add(...)` / `backForwardListController.newEntry(...)` / the sync-triggering side effects. (REM-09's ViewModel would make this structural; this is the tactical fix.)
2. In `tryGetIntentResultFromView`, make the `lid` branch return the `IntentResult` without calling `jumpToAri` as a side effect (mirror the `ari` branch), so external `lid` intents navigate once and insert one back/forward entry.
3. In `ShareUrl`, dismiss the progress dialog when the activity is destroyed (lifecycle observer or `DialogFragment`) and guard completion callbacks with `isDestroyed` in addition to `isFinishing`.
4. Add the `bindingAdapterPosition != RecyclerView.NO_POSITION` guard to the history-dialog adapter click handler (every other adapter in the file has it).

**Difficulty:** Easy (~half a day for steps 2–4; step 1 needs a careful pass over the `onCreate` intent flow).

---

[← Back to remediation index](../tech-debt-remediation.md)
