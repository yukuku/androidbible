# ~~REM-20: Replace AmbilWarna with Material Color Picker~~ ✅ COMPLETED (2026-05-12)

**Addresses:** TD-12
**Module:** UI — Color settings
**BRICE:** B=1 R=1 I=4 C=4 E=5 → **3.0**
**Phase:** 3 — Modernization

**Steps:**
1. `AmbilWarnaDialog` is used in 2 files: `MarkersActivity.java` (lines 43, 233-243 — label color picker) and `TypeHighlightDialog.java` (line 19 — highlight color selection). There is no `ColorSettingsActivity`.
2. Replace with `MaterialColorPickerDialog` from a Material-compatible library or implement custom using Material 3 color palette
3. Ensure selected colors are stored in the same format (hex int)
4. Delete `AmbilWarna` module

**Difficulty:** Easy (3-4 hours).

**Outcome:** Done in two PRs.

The first PR (2026-05-11) replaced the two `AmbilWarnaDialog` call sites — `MarkersActivity` and `TypeHighlightDialog` — with a new iOS-style Compose color picker, inspired by Apple's UIColorPickerViewController and the Flutter `ios_color_picker` package. The picker lives in [Alkitab/src/main/java/yuku/alkitab/base/compose/colorpicker/IosColorPicker.kt](../../Alkitab/src/main/java/yuku/alkitab/base/compose/colorpicker/IosColorPicker.kt) and exposes three tabs: a **Grid** (50-color preset palette: 10 greys × 1 row + 10 hues × 4 brightness rows), a **Spectrum** tab (saturation × value 2D box driven by tap & drag, with a hue slider underneath), and a **Sliders** tab (R/G/B channel sliders plus a 6-digit hex input field). The Java call sites reach the picker through a thin `ColorPickerDialog` wrapper ([ColorPickerDialog.kt](../../Alkitab/src/main/java/yuku/alkitab/base/compose/colorpicker/ColorPickerDialog.kt)) that hosts the Compose view inside a `ModalBottomSheet` via `ComposeBottomSheetHost`. Color storage is unchanged — the picker returns the same `0xff000000 | rgb` int the legacy code expected, and `LabelColorUtil.encodeBackground` still masks to 24-bit RGB before persisting. The picker uses the existing `BibleAppTheme` so it inherits dynamic colors on Android 12+. Java callers reach the picker via a `fun interface Listener` (single-method, lambda-compatible).

The follow-up PR (2026-05-12) finished the migration by replacing the 9 `<yuku.ambilwarna.widget.AmbilWarnaPreference …/>` entries across `color_settings.xml`, `color_settings_night.xml`, and `settings_display.xml` with a new [ColorPreference](../../Alkitab/src/main/java/yuku/alkitab/base/widget/ColorPreference.kt) (`androidx.preference.Preference` subclass). It draws a 24dp rounded color swatch as its widget area and opens the same Compose bottom-sheet picker on click — persisting through the existing `persistInt` path, so all `Preferences.getInt(R.string.pref_textColor_key, …)` call sites elsewhere in the codebase keep reading the same ARGB int format. With the last consumer gone, the `AmbilWarna` Gradle module was deleted in full: `include(":AmbilWarna")` removed from `settings.gradle.kts`, `implementation(project(":AmbilWarna"))` removed from `Alkitab/build.gradle.kts`, and the entire `AmbilWarna/` directory deleted.

**Verification:** `./gradlew assemblePlainDebug` and `./gradlew testPlainDebugUnitTest testPlainReleaseUnitTest` both pass after each PR.

---

[← Back to remediation index](../tech-debt-remediation.md)
