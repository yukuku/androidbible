# REM-41: Defensive Data Guards (Downgrade, Verse-0, Plan Revisions, Widget, Devotions)

**Source:** 2026-07 code audit
**Module:** Storage, verses, reading plans, widget, devotions
**BRICE:** B=3 R=3 I=4 C=4 E=3 → **3.4**
**Phase:** 2 — Architecture Improvements (2026-07 audit)

**Status:** Completed for supported behavior on 2026-10-15. Database downgrades are unsupported.

**Implementation:**

- Widget updates deliberately use `appWidgetId` as the retry stride so two widgets do not follow the same daily-verse sequence. This behavior and existing click offsets are preserved.
- Marker, progress-mark, and map-location attribute loading logs and skips verse indexes outside the chapter.
- `ReadingPlanManager.writeReadMarksByDay` ignores progress sequences outside the current day's readings.
- Database downgrades are unsupported. `InternalDbHelper` and `SongDbHelper` retain the default downgrade rejection.
- `ReadingPlanManager.readVersion1` validates the RPB footer while the stream is open and closes it on success or failure.
- `DevotionDownloader` already keys queued and active work by `(name, date)`. Its existing deduplication regression test covers this requirement.
- Regression coverage includes verse-0 markers, progress and map locations, revised-plan progress, RPB footers, and widget-specific retry strides and fallback traversal.

**Steps:**
1. ✅ **Reviewed, deliberate behavior:** `DailyVerseAppWidgetReceiver` passes `appWidgetId` as `EXTRA_direction`. It supplies a widget-specific retry stride so two widgets do not follow the same daily-verse sequence. Preserve this value and persisted click offsets; an inline comment and regression tests document the intent.
2. Add `mapOffset < 0` guards (log + skip, matching the existing "too many" branch) in `InternalDb.putAttributes` and both loops in `VerseAttributeLoader.load`, so a verse-0 ari from sync/import can't crash-loop a chapter.
3. Guard `readMarks[sequence]` in `ReadingPlanManager.writeReadMarksByDay` with a bounds check (log + ignore out-of-range progress rows).
4. **Not applicable:** Database downgrades are unsupported. Do not override `onDowngrade` in `InternalDbHelper` or `SongDbHelper` to allow them.
5. Move the RPB footer check before `reader.close()` in `ReadingPlanManager.readVersion1`.
6. ✅ **Complete:** `DevotionDownloader` keys pending and active requests by `Key(name, date)` through `workByKey`, independent of `DevotionArticle` equality. `DevotionDownloaderTest` covers queued/in-flight deduplication and distinct sources sharing a date.
7. Tests: verse-0 marker via `InternalDbTest`; out-of-range plan progress; widget direction extra (unit-testable via `DailyVerseData.getAris`). Cover the widget receiver/service/data update flow as well as the direction value, including persisted navigation state and unavailable selected versions. The explicit fallback UX is tracked in [REM-44](REM-44-widget-version-fallback.md).

**Difficulty:** Easy (each item ≤ 1 hour; ~1 day total with tests).

---

[← Back to remediation index](../tech-debt-remediation.md)
