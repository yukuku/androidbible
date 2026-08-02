# REM-22: Introduce Jetpack Compose for New Screens

**Status:** Kicked off — `GotoActivity` + 3 fragments ported as the first screen. **Note:** the legacy View-based Goto screen was later restored as the default, with the Compose port gated behind an experimental flag (`ExperimentalFlags.useComposeGoto()`, PR #212).
**Addresses:** General modernization
**Module:** UI
**BRICE:** B=3 R=1 I=2 C=2 E=5 → **2.6**
**Phase:** 4 — Long-term / Major Refactors

**Progress:**
- Compose BOM and `androidx.activity:activity-compose` added (versions pinned in `gradle/libs.versions.toml`); `buildFeatures { compose = true }` enabled.
- `BibleAppTheme` (dynamic color on Android 12+) introduced under `yuku.alkitab.base.compose`.
- `GotoActivity` and the three goto tabs (Dialer / Direct / Grid) implemented as Composables under `compose/goto/` (including the app's first `ViewModel`, `GotoViewModel`). Public API on `GotoActivity` (`createIntent` / `obtainResult` / `Result`) is preserved so call sites in `IsiActivity` keep working unchanged. The legacy View-based screens were later restored as the default (PR #212) and the Compose path is opt-in via `ExperimentalFlags.useComposeGoto()` — both implementations currently coexist inside `GotoActivity`.
- The iOS-style color picker added in [REM-20](REM-20-ambilwarna-replacement.md) is also a Compose surface (`IosColorPicker` + `ColorPickerDialog`) hosted inside a `ModalBottomSheet`.
- The reader's verse content view has a fully Compose-based implementation (2026-08): `VersesComposeControllerImpl` + `VersesComposeView` (LazyColumn) implement the complete `VersesController` contract and replace the two reader RecyclerViews when the Verse (Compose) experimental setting is on; verse rows and pericope headers render via `VerseRendererCompose` / `VerseItemComposeContent`, including dictionary-mode word links. The RecyclerView pipeline remains the default and always serves `VersesDialog`/`XrefDialog`.

**Remaining steps:**
1. Continue with simpler screens: `AboutActivity.kt` (Kotlin, View-based with custom animations), `HelpActivity.java` (Java, WebView-based — convert to Kotlin first)
2. Gradually migrate: `SettingsActivity` → Compose Preference screens
3. Reader (`IsiActivity`): verse rendering is covered by the experimental Compose verse list above; the rest of the reader shell (split container/handle, gestures, toolbar/action mode, floater, drawer) is tracked as a staged plan in [REM-45](REM-45-isiactivity-compose-shell.md), gated on the Compose verse list being promoted to default first

**Difficulty:** Hard (ongoing effort over months). Risk: Compose interop with existing View-based code requires careful fragment/activity management.

---

[← Back to remediation index](../tech-debt-remediation.md)
