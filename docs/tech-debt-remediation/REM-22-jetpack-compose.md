# REM-22: Introduce Jetpack Compose for New Screens

**Status:** Ongoing across multiple screens, reviewed on 2026-10-15. Navigation, verse lists/dialogs, the normal reader toolbar, song content, and sync login default to Compose. Experimental settings retain legacy alternatives. The search toolbar and several picker/audio/feedback surfaces also use Compose; their containing screens can still contain Views.
**Addresses:** General modernization
**Module:** UI
**BRICE:** B=3 R=1 I=2 C=2 E=5 → **2.6**
**Phase:** 4 — Long-term / Major Refactors

**Progress:**
- Compose BOM and `androidx.activity:activity-compose` added (versions pinned in `gradle/libs.versions.toml`); `buildFeatures { compose = true }` enabled.
- `BibleAppTheme` introduced under `yuku.alkitab.base.compose`. Since 2026-08-09 it applies `BibleAppDarkColorScheme`, a static dark Material 3 scheme built from the app's XML palette instead of wallpaper-derived dynamic colors, so Compose surfaces show the same brand colors on every device as the View screens: `primary` = the light-blue `accent`, the `secondary` family = the blue-gray toolbar chrome (`primary`/`primary_dark` resources; Compose top bars paint `secondaryContainer`), `tertiary` = the rarely-used teal `escape`, surfaces anchored on the `window` gray. `BibleAppThemeColorsTest` pins the scheme roles to the XML color resources, and `ComposeThemeSnapshotTest` renders the real Compose surfaces into a side-by-side HTML report (dynamic vs branded) under `Alkitab/build/snapshots/compose-theme/`.
- `GotoActivity` and the three goto tabs (Dialer / Direct / Grid) implemented as Composables under `compose/goto/` (including the app's first `ViewModel`, `GotoViewModel`). Public API on `GotoActivity` (`createIntent` / `obtainResult` / `Result`) is preserved so call sites in `IsiActivity` keep working unchanged. `ExperimentalFlags.useComposeGoto()` picks the Compose path by default, with the legacy View-based screens as the opt-out. Both implementations coexist inside `GotoActivity`.
- The iOS-style color picker added in [REM-20](REM-20-ambilwarna-replacement.md) is also a Compose surface (`IosColorPicker` + `ColorPickerDialog`) hosted inside a `ModalBottomSheet`.
- The reader's verse content view has a fully Compose-based implementation (2026-08): `VersesComposeControllerImpl` + `VersesComposeView` (LazyColumn) implement the complete `VersesController` contract and replace the two reader RecyclerViews unless the "Verse (legacy views)" experimental setting is ticked; verse rows and pericope headers render via `VerseRendererCompose` / `VerseItemComposeContent`, including dictionary-mode word links. `VersesDialog`/`XrefDialog` build their list the same way, through the shared `createVersesController`, and the screens that show verse text outside a verse list (search results, the marker list, the progress-mark list, the report screen) swap their snippet TextView for a `VerseTextComposeView` through `VerseTextSlot`. The RecyclerView pipeline remains as the opt-out behind that setting.
- `ReaderToolbar` is hosted in a `ComposeView` in `IsiActivity` and selected by default through `ExperimentalFlags.useComposeToolbar()`. Selection actions use a separate View-based `FloatingSelectionToolbar` with `ReaderSelectionActionMode`; that surface is not part of the normal toolbar port.
- Song content uses `SongComposeFragment` / `SongComposeContent` by default through `ExperimentalFlags.useComposeSong()`. Sync login uses `SyncLoginScreen` through `SyncLoginComposeHost` by default through `ExperimentalFlags.useComposeSyncLogin()`. Both retain legacy alternatives.
- `SearchActivity` hosts `SearchToolbar` in Compose; its results remain a View-based list with Compose verse snippets. Audio controls and bottom sheets, plus prerelease feedback, also have Compose surfaces.

**Remaining steps:**
1. Continue with simpler screens where beneficial: `AboutActivity.kt` (Kotlin, View-based with custom animations), `HelpActivity.java` (Java, WebView-based; assess whether its host needs conversion)
2. Gradually migrate: `SettingsActivity` → Compose Preference screens
3. Reader (`IsiActivity`): verse rendering and the normal toolbar have Compose implementations. The selection toolbar, split container/handle, gestures, floater, back/forward panel, and drawer remain in [REM-45](REM-45-isiactivity-compose-shell.md). Retiring the legacy verse pipeline is a prerequisite for the split-container migration, not for independent toolbar or overlay work.

**Difficulty:** Hard (ongoing effort over months). Risk: Compose interop with existing View-based code requires careful fragment/activity management.

---

[← Back to remediation index](../tech-debt-remediation.md)
