# REM-44: Make the Daily-Verse Widget's Version Fallback Explicit

**Addresses:** TD (widget) — daily verse
**Module:** Daily-verse widget (`DailyVerseData`)
**BRICE:** B=2 R=2 I=3 C=3 E=3 → **2.6**
**Phase:** 4 — Long-term / Minor

**Problem:** `DailyVerseData.getVersion()` falls back to the internal version whenever the user's selected version can't be loaded (no data file, version deleted, or unset). A warning is logged, but from the user's side the widget quietly switches language/translation with no signal that the configured version is unavailable.

**Steps:**
1. When the configured version can't load, show an explicit hint in the widget (e.g. a one-line "tap to reconfigure" or the fallback version's name) rather than silently substituting.
2. Offer to re-open the widget configuration activity so the user can pick an available version.
3. Optionally, detect at configuration time that a version has since been deleted and prompt then.

**Difficulty:** Easy. Low priority — behavior is safe, just opaque.

---

[← Back to remediation index](../tech-debt-remediation.md)
