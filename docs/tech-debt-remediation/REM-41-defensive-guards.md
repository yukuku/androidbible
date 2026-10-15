# REM-41: Defensive Data Guards (Downgrade, Verse-0, Plan Revisions, Widget, Devotions)

**Source:** 2026-07 code audit
**Module:** Storage, verses, reading plans, widget, devotions
**BRICE:** B=3 R=3 I=4 C=4 E=3 → **3.4**
**Phase:** 2 — Architecture Improvements (2026-07 audit)

**Status:** Partial, reviewed on 2026-10-15. Devotion request deduplication (step 6) is complete; steps 1 through 5 remain open.

**Steps:**
1. **Do first — one line, user-visible:** `DailyVerseAppWidgetReceiver`: pass `1` (or the intended default direction), not `appWidgetId`, as `EXTRA_direction`. Consider clamping/resetting persisted `click` state that the bug inflated.
2. Add `mapOffset < 0` guards (log + skip, matching the existing "too many" branch) in `InternalDb.putAttributes` and both loops in `VerseAttributeLoader.load`, so a verse-0 ari from sync/import can't crash-loop a chapter.
3. Guard `readMarks[sequence]` in `ReadingPlanManager.filterProgressForReadingPlan` with a bounds check (log + ignore out-of-range progress rows).
4. Override `InternalDbHelper.onDowngrade` as a no-op (schema is append-only across releases; `onUpgrade` logic keys off the stored version, and a downgrade-crash-loop is strictly worse than running older code against a newer-stamped schema). Do the same for `SongDbHelper` if applicable.
5. Move the RPB footer check before `reader.close()` in `ReadingPlanManager.readVersion1`.
6. ✅ **Complete:** `DevotionDownloader` keys pending and active requests by `Key(name, date)` through `workByKey`, independent of `DevotionArticle` equality. `DevotionDownloaderTest` covers queued/in-flight deduplication and distinct sources sharing a date.
7. Tests: verse-0 marker via `InternalDbTest`; out-of-range plan progress; widget direction extra (unit-testable via `DailyVerseData.getAris`).

**Difficulty:** Easy (each item ≤ 1 hour; ~1 day total with tests).

---

[← Back to remediation index](../tech-debt-remediation.md)
