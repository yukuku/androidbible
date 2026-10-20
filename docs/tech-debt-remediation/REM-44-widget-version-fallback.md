# REM-44: Make the Daily-Verse Widget's Version Fallback Explicit

**Status:** Complete on 2026-10-20.

**Addresses:** TD (widget): daily verse
**Module:** Daily-verse widget (`DailyVerseData`)
**BRICE:** B=2 R=2 I=3 C=3 E=3 → **2.6**
**Phase:** 4: Long-term / Minor

**Problem addressed:** An unavailable configured Bible version silently substituted the internal version, leaving users unaware of a translation or language change.

**Completed behavior:**

- `DailyVerseData.resolveVersion` returns the readable version and an explicit fallback flag. Deleted records, missing files, unreadable files, and unset selections use the internal Bible; explicitly selecting the internal Bible is not a fallback.
- `DailyVerseFactory` shows the fallback Bible's name and a tappable notice beneath the reference. The notice follows the app language and uses the widget's text color at 80% opacity, 80% of its text size, and italics. It remains available with the app icon hidden or no available passage, and disappears when the configured version becomes readable.
- The notice opens `DailyVerseAppWidgetConfigurationActivity` for the tapped widget. Saving a replacement preserves appearance and navigation; canceling preserves the saved selection. Fallback loading does not replace the configured version ID, so restored content can be used again.

**Validation:** Widget regression coverage includes version resolution, rendered notice visibility and localization, recovery, per-widget configuration intents, and saving a replacement without resetting appearance or navigation. Launcher-specific presentation and activity launching still require device validation.

---

[← Back to remediation index](../tech-debt-remediation.md)
