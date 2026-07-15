# REM-22: Introduce Jetpack Compose for New Screens

**Status:** Kicked off — `GotoActivity` + 3 fragments ported as the first screen. **Note:** the legacy View-based Goto screen was later restored as the default, with the Compose port gated behind an experimental flag (`ExperimentalFlags.useComposeGoto()`, PR #212).
**Addresses:** General modernization
**Module:** UI
**BRICE:** B=3 R=1 I=2 C=2 E=5 → **2.6**
**Phase:** 4 — Long-term / Major Refactors

**Progress:**
- Compose BOM `2026.04.01` (Compose 1.11.0 / Material 3 1.4.0) and `androidx.activity:activity-compose:1.13.0` added; `buildFeatures { compose = true }` enabled.
- `BibleAppTheme` (dynamic color on Android 12+) introduced under `yuku.alkitab.base.compose`.
- `GotoActivity` and the three goto tabs (Dialer / Direct / Grid) implemented as Composables under `compose/goto/` (including the app's first `ViewModel`, `GotoViewModel`). Public API on `GotoActivity` (`createIntent` / `obtainResult` / `Result`) is preserved so call sites in `IsiActivity` keep working unchanged. The legacy View-based screens were later restored as the default (PR #212) and the Compose path is opt-in via `ExperimentalFlags.useComposeGoto()` — both implementations currently coexist inside `GotoActivity`.
- The iOS-style color picker added in [REM-20](REM-20-ambilwarna-replacement.md) is also a Compose surface (`IosColorPicker` + `ColorPickerDialog`) hosted inside a `ModalBottomSheet`.

**Remaining steps:**
1. Continue with simpler screens: `AboutActivity.kt` (Kotlin, View-based with custom animations), `HelpActivity.java` (Java, WebView-based — convert to Kotlin first)
2. Gradually migrate: `SettingsActivity` → Compose Preference screens
3. Do NOT migrate `IsiActivity` verse rendering — too complex and performance-critical for initial Compose adoption

**Difficulty:** Hard (ongoing effort over months). Risk: Compose interop with existing View-based code requires careful fragment/activity management.

---

[← Back to remediation index](../tech-debt-remediation.md)
