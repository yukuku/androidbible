# Design: Port GotoActivity to Jetpack Compose + Material 3

**Date:** 2026-05-06
**Tracks:** REM-22 (Jetpack Compose adoption) — first concrete screen
**Replaces:** `GotoActivity.java`, `GotoDialerFragment.java`, `GotoDirectFragment.java`, `GotoGridFragment.java`, `BaseGotoFragment.java` and their associated layouts

## Goal

Replace the verse-jump screen — currently 1 activity + 3 fragments + 4 layouts (~1,400 lines of Java/XML) — with an idiomatic Jetpack Compose + Material 3 implementation. Establish the foundation patterns (theme composable, ViewModel + Compose interop, Compose dependencies in the version catalog) that subsequent Compose ports in this codebase will reuse.

The user-visible behavior of the screen does not change. Public API of `GotoActivity` (`createIntent`, `obtainResult`, `Result`) does not change so existing callers (`IsiActivity`, etc.) need no edits.

## Compose version

Per Google's [BOM-to-library mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping):

- **Compose BOM `2026.04.01`** — pulls `compose-ui`, `compose-runtime`, `compose-foundation` at **1.11.0** and `compose-material3` at **1.4.0**. This is the user-requested "Compose 1.11.0" version.
- **`androidx.activity:activity-compose:1.13.0`** — set separately; activity-compose is not part of the Compose BOM and has its own version line.

The existing `androidxActivity = "1.11.0"` in the catalog (used for `activity-ktx`) is kept as-is. A new version key `androidxActivityCompose = "1.13.0"` is added for `activity-compose`. Note: Gradle's version resolution will likely upgrade the transitive `androidx.activity:activity` dependency to 1.13.0 (since activity-compose 1.13.0 depends on activity 1.13.0); `activity-ktx 1.11.0 → 1.13.0` is a low-risk minor bump that comes "for free" via conflict resolution. We do not bump the catalog entry to avoid making the diff misleading; the implementer should verify with `./gradlew :Alkitab:dependencies` that nothing else regresses.

## Architecture

### Composition root

`GotoActivity.kt` is now a `ComponentActivity` (no longer extends `BaseActivity`/AppCompat). Its only responsibilities are:

1. Read the three intent extras (`bookId`, `chapter_1`, `verse_1`).
2. Provide a `ViewModelStore` (default for `ComponentActivity`).
3. Call `setContent { BibleAppTheme { GotoScreen(...) } }`.
4. Receive the `onGotoFinished` callback and translate it into `setResult(RESULT_OK, data)` + `finish()` + writing `Prefkey.goto_last_tab`.

Public API kept identical:

```kotlin
companion object {
    fun createIntent(bookId: Int, chapter_1: Int, verse_1: Int): Intent { ... }
    fun obtainResult(data: Intent): Result { ... }
}
class Result { var bookId: Int; var chapter_1: Int; var verse_1: Int }
```

### Composable hierarchy

```
BibleAppTheme
└── GotoScreen(initialBookId, initialChapter, initialVerse, onGotoFinished)
    ├── Scaffold
    │   ├── topBar = GotoTopBar(askForVerse, onAskForVerseToggle, onUp)
    │   │   ├── TopAppBar
    │   │   │   ├── navigationIcon = back arrow
    │   │   │   └── actions = overflow menu with "Ask for verse" toggle
    │   │   └── PrimaryTabRow (3 tabs: Dialer, Direct, Grid)
    │   └── content = HorizontalPager(state = pagerState, count = 3)
    │       ├── DialerTab(initialBookId, initialChapter, initialVerse, askForVerse, onGotoFinished)
    │       ├── DirectTab(initialBookId, initialChapter, initialVerse, autofocus, onGotoFinished)
    │       └── GridTab(initialBookId, askForVerse, viewModel, onGotoFinished)
```

### State ownership

| State | Where it lives | Why |
|-------|----------------|-----|
| Current pager page | `pagerState = rememberPagerState(...)` initialized from `Prefkey.goto_last_tab` | Standard Compose pattern; survives rotation via internal saver |
| `askForVerse` preference | `GotoViewModel.askForVerse: StateFlow<Boolean>` backed by `Preferences.registerObserver` wrapped in `callbackFlow` | Replaces the `SharedPreferences.OnSharedPreferenceChangeListener` in the old DialerFragment. One observer for the whole screen. |
| Dialer tab: chapter/verse text, active field, `firstTime` flags | `rememberSaveable` inside `DialerTab` | Cheap, primitive; no need for VM |
| Dialer tab: selected book index | `rememberSaveable` | Same |
| Direct tab: query text | `rememberSaveable` | Same |
| Direct tab: autocomplete results | `derivedStateOf { computeCandidates(query, books) }` | Pure derivation from query + books |
| Grid tab: navigation stage (Books / Chapters(book) / Verses(book, chapter)) | `GotoViewModel.gridStage: MutableState<GridStage>` | 3-step flow with rotation survival; `Book` is not `Parcelable` so plain `rememberSaveable` won't serialize it — VM keeps it in memory |
| Books array (sorted per pref) | `remember { … }` per tab | Cached for lifetime of composable; cheap to recompute |

### Lambda instead of `Listener` interface

The `BaseGotoFragment.GotoFinishListener` interface (with constants `GOTO_TAB_dialer = 1`, `GOTO_TAB_direct = 2`, `GOTO_TAB_grid = 3` and method `onGotoFinished(tab, bookId, chapter, verse)`) becomes:

```kotlin
enum class GotoTab(val ordinal1Based: Int) {
    DIALER(1), DIRECT(2), GRID(3);
    companion object {
        fun fromOrdinal1Based(v: Int): GotoTab? = values().firstOrNull { it.ordinal1Based == v }
    }
}

typealias OnGotoFinished = (tab: GotoTab, bookId: Int, chapter_1: Int, verse_1: Int) -> Unit
```

The `1`/`2`/`3` ordinals are preserved on `GotoTab.ordinal1Based` so the `Prefkey.goto_last_tab` integer value stays compatible with previously-saved preferences from old app versions.

## Per-tab design

### DialerTab

- **Book picker:** `ExposedDropdownMenuBox` (M3) showing the books from `S.activeVersion().getConsecutiveBooks()`, sorted alphabetically when `pref_alphabeticBookSort_key` is true. Items show `book.shortName` colored via `BookColorUtil.getForegroundOnDark(book.bookId)`. Replaces the legacy `Spinner` + `BookAdapter`.
- **Chapter/Verse "fields":** Two `Surface(onClick = ...)` boxes (NOT real `TextField`s — the user types via the on-screen keypad below, not the soft keyboard). Each shows the current numeric text. The active field has `MaterialTheme.colorScheme.primaryContainer` background; inactive is transparent. This replaces the `goto_dialer_active` drawable. Verse field is hidden via `if (askForVerse)` when the preference is false.
- **Keypad:** `LazyVerticalGrid(GridCells.Fixed(3))` with 12 cells: 1, 2, 3, 4, 5, 6, 7, 8, 9, ⌫ (`Icons.AutoMirrored.Filled.Backspace`), 0, OK. Digit cells are `TextButton`s. OK is a filled `Button` styled with `MaterialTheme.colorScheme.primary`. Backspace is an `IconButton`.
- **Logic ported verbatim** from `GotoDialerFragment`:
  - `tryReadChapter()` / `tryReadVerse()` — `Integer.parseInt("0" + text)` with NumberFormatException → 0.
  - `fixChapterOverflow()` / `fixVerseOverflow()` — clamp to `[1, max]`.
  - `press(s)` — handles `"backspace"` and digit input including the `firstTime` reset behavior.
  - `activate(active, passive)` — toggles which field is active; defaults to chapter on first composition.
- **OK button** parses the two fields, looks up the selected book's `bookId` from the picker, and invokes `onGotoFinished(GotoTab.DIALER, bookId, chapter, verse)`.

### DirectTab

- **OutlinedTextField** for the reference query, with the prompt text (from `R.string.jump_to_prompt` with `S.activeVersion().reference(bookId, chapter, verse)` substituted) shown above as `supportingText` or a sibling `Text`. IME action `Go` triggers submit.
- **Autocomplete dropdown:** `ExposedDropdownMenuBox` anchored to the text field. Tapping a non-`bookOnly` candidate auto-submits (matches the legacy `setOnItemClickListener { if (!bookOnly) bOk.performClick() }`).
- **Filter logic — direct port, NOT a rewrite.** The body of `AutoCompleteAdapter.getFilter().performFiltering()` (lines 191–264 of `GotoDirectFragment.java`) is moved into a top-level Kotlin function:

  ```kotlin
  fun computeCandidates(query: String, books: Array<Book>): List<Candidate>
  ```

  Same `Jumper(query)` construction, same `book.shortName` `startsWith` (score=20) / `contains` (score=10) scan, same Levenshtein fallback when `candidates.size < 5` using `Jumper.createBookCandidates(books)`, same `addCandidate` helper with chapter/verse overflow guards, same `score`-descending sort. The `Candidate` class moves to a top-level Kotlin data class. `Jumper`, `Levenshtein`, and `Jumper.BookRef` are unchanged.

  The composable wraps the call in `derivedStateOf` so it recomputes only when the query changes:
  ```kotlin
  val candidates by remember(books) { derivedStateOf { computeCandidates(query, books) } }
  ```
- **Submit logic — direct port** of `bOk_click` (lines 113–159 of `GotoDirectFragment.java`):
  1. Trim, return if empty.
  2. `nobookPattern = Pattern.compile("(\\d+)(?:[ :.]+(\\d+))?")` — try as chapter[:verse]. If it matches, call `onGotoFinished(GotoTab.DIRECT, currentBookId, chapter, verse)`.
  3. Else `Jumper(reference)`. If `parseSucceeded` is false, show a Material 3 `AlertDialog` with `R.string.alamat_tidak_sah_alamat` (replaces `MaterialDialogJavaHelper.showOkDialog`).
  4. Else `onGotoFinished(GotoTab.DIRECT, jumper.getBookId(books), jumper.getChapter(), jumper.getVerse())`.
- **Auto-focus:** when the saved `Prefkey.goto_last_tab == 2` (Direct), a `LaunchedEffect(Unit)` calls `focusRequester.requestFocus()` and shows the soft keyboard. Replaces the old `postDelayed { showSoftInput }` hack.

### GridTab

- **Stage state machine** stored in `GotoViewModel`:
  ```kotlin
  sealed class GridStage {
      object Books : GridStage()
      data class Chapters(val book: Book) : GridStage()
      data class Verses(val book: Book, val chapter_1: Int) : GridStage()
  }
  ```
  Held in memory in the VM since `Book` is not `Parcelable`.
- **Header chips** (visible when `stage != Books`):
  - Selected-book `AssistChip` with delete icon → returns to `Books`.
  - Selected-chapter `AssistChip` with delete icon → returns to `Chapters` (only visible in `Verses` stage).
  - Book chip text colored via `BookColorUtil.getForegroundOnDark`.
- **Grids** (`LazyVerticalGrid`):
  - Books: `GridCells.Fixed(6)`. Cell text = `BookNameSorter.getBookAbbr(book)`. Color = `BookColorUtil.getForegroundOnDark(book.bookId)`. Click → if `book.chapter_count == 1`, jump straight to `Verses(book, 1)` (preserves the single-chapter shortcut for Obadiah, Philemon, etc.); else go to `Chapters(book)`.
  - Chapters: `GridCells.Fixed(integerResource(R.integer.goto_grid_numeric_num_columns))`. Cell text = `(position + 1).toString()`. Click → if `askForVerse` is true, go to `Verses(book, chapter)`; else `onGotoFinished(GotoTab.GRID, book.bookId, chapter, 0)`.
  - Verses: same column count. Click → `onGotoFinished(GotoTab.GRID, book.bookId, chapter, position + 1)`.
- **Animations:** `AnimatedContent(targetState = stage)` with directional `slideInHorizontally + fadeIn` / `slideOutHorizontally + fadeOut`, 200ms duration (matches the old `ANIM_DURATION = 200`). Direction inferred by comparing the new stage's "depth" (Books=0, Chapters=1, Verses=2) to the previous: forward → slide in from right; back → slide in from left. Replaces the old `animateFadeOutAndSlideLeft` / `animateFadeOutAndSlideRight` `ViewPropertyAnimator` glue.
- **Cell appearance:** `Surface(onClick = ...)` with the existing `goto_grid_cell` look ported as `Modifier.background(colorScheme.surfaceContainerHigh) + .border(1.dp, colorScheme.outlineVariant)`. The legacy `R.layout.item_goto_grid_cell` and any associated drawables can be deleted.

## Cross-tab pieces in `GotoScreen`

- `pagerState = rememberPagerState(initialPage = ((Preferences.getInt(Prefkey.goto_last_tab, 0) - 1).coerceIn(0, 2)), pageCount = { 3 })`.
- `LaunchedEffect(pagerState.currentPage)` clears focus from the Direct tab's `TextField` when the user pages away (replaces `imm.hideSoftInputFromWindow` on `onPageSelected`).
- TopAppBar overflow:
  ```kotlin
  IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, ...) }
  DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
      DropdownMenuItem(
          text = { Text(stringResource(R.string.menuAskForVerse)) },
          leadingIcon = { Checkbox(checked = askForVerse, onCheckedChange = null) },
          onClick = {
              Preferences.setBoolean(Prefkey.gotoAskForVerse, !askForVerse)
              menuOpen = false
          },
      )
  }
  ```
  The checkbox state comes from `viewModel.askForVerse.collectAsState()`. Toggling rewrites the preference; the observer pushes the new value back through the StateFlow; all tabs that consume it (Dialer, Grid) recompose automatically.

## Theme: `BibleAppTheme`

New file `Alkitab/src/main/java/yuku/alkitab/base/compose/BibleAppTheme.kt`:

```kotlin
@Composable
fun BibleAppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        if (dark) darkColorScheme() else lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}
```

`minSdk = 26` so the Build.VERSION check is needed. No mapping from the existing `Theme.MaterialComponents.NoActionBar.Bridge` AppCompat XML theme — Compose Material 3 runs independently. The AppCompat theme stays untouched for the rest of the app.

This is the foundation theme composable; future Compose screens reuse it.

## Build setup

### `gradle/libs.versions.toml`

Add to `[versions]`:
```toml
composeBom = "2026.04.01"
androidxActivityCompose = "1.13.0"
androidxLifecycle = "2.9.0"
```
(`androidxActivity = "1.11.0"` is already present and stays — it's reused for `activity-ktx`. `activity-compose` gets its own version key because it's published on a separate line and the user wants 1.13.0 specifically.)

Add to `[libraries]`:
```toml
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "androidxActivityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "androidxLifecycle" }
```

Add to `[plugins]`:
```toml
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

### Root `build.gradle.kts`

Add `alias(libs.plugins.kotlin.compose) apply false`.

### `Alkitab/build.gradle.kts`

```kotlin
plugins {
    // existing plugins...
    alias(libs.plugins.kotlin.compose)
}

android {
    buildFeatures {
        compose = true
        // existing flags...
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // existing deps...
}
```

## Files removed when this lands

- `Alkitab/src/main/java/yuku/alkitab/base/ac/GotoActivity.java`
- `Alkitab/src/main/java/yuku/alkitab/base/fr/GotoDialerFragment.java`
- `Alkitab/src/main/java/yuku/alkitab/base/fr/GotoDirectFragment.java`
- `Alkitab/src/main/java/yuku/alkitab/base/fr/GotoGridFragment.java`
- `Alkitab/src/main/java/yuku/alkitab/base/fr/base/BaseGotoFragment.java` (if no other fragment extends it; verify with grep)
- `Alkitab/src/main/res/layout/activity_goto.xml`
- `Alkitab/src/main/res/layout/fragment_goto_dialer.xml`
- `Alkitab/src/main/res/layout-land/fragment_goto_dialer.xml`
- `Alkitab/src/main/res/layout/fragment_goto_direct.xml`
- `Alkitab/src/main/res/layout/fragment_goto_grid.xml`
- `Alkitab/src/main/res/layout/item_goto_dialer_book.xml`
- `Alkitab/src/main/res/layout/item_goto_dialer_book_dropdown.xml`
- `Alkitab/src/main/res/layout/item_goto_grid_cell.xml`
- `Alkitab/src/main/res/menu/activity_goto.xml`
- `Alkitab/src/main/res/drawable/goto_dialer_active.xml`

## Files added

- `Alkitab/src/main/java/yuku/alkitab/base/ac/GotoActivity.kt` — `ComponentActivity` shell, ~60 lines.
- `Alkitab/src/main/java/yuku/alkitab/base/compose/BibleAppTheme.kt` — shared M3 theme composable, ~25 lines.
- `Alkitab/src/main/java/yuku/alkitab/base/compose/goto/GotoScreen.kt` — top-level screen with Scaffold + tabs + pager, ~120 lines.
- `Alkitab/src/main/java/yuku/alkitab/base/compose/goto/GotoViewModel.kt` — `askForVerse` flow + grid stage state, ~50 lines.
- `Alkitab/src/main/java/yuku/alkitab/base/compose/goto/DialerTab.kt` — ~200 lines.
- `Alkitab/src/main/java/yuku/alkitab/base/compose/goto/DirectTab.kt` — ~150 lines.
- `Alkitab/src/main/java/yuku/alkitab/base/compose/goto/GridTab.kt` — ~180 lines.
- `Alkitab/src/main/java/yuku/alkitab/base/compose/goto/Candidate.kt` — data class (1 line) and `computeCandidates(query, books)` function (~70 lines, direct port from `AutoCompleteAdapter.performFiltering`).
- `Alkitab/src/main/java/yuku/alkitab/base/compose/goto/GotoTab.kt` — enum (~10 lines) preserving the `1`/`2`/`3` ordinals for `Prefkey.goto_last_tab` compatibility.

## Files kept unchanged

- All string resources (`R.string.jump_to_prompt`, `goto_tab_dialer_label`, `goto_tab_direct_label`, `goto_tab_grid_label`, `pasal_sebelumangka`, `ayat_sebelumangka`, `alamat_tidak_sah_alamat`, `menuAskForVerse`, `ok`).
- `R.integer.goto_grid_numeric_num_columns`.
- All Bible model classes: `Book`, `Version`, etc.
- `Jumper`, `Levenshtein`, `Jumper.BookRef`, `Jumper.createBookCandidates`.
- `BookColorUtil`, `BookNameSorter`.
- `Prefkey.goto_last_tab`, `Prefkey.gotoAskForVerse`, `PrefkeyKt.GOTO_ASK_FOR_VERSE_DEFAULT`.
- All callers of `GotoActivity.createIntent` / `obtainResult` (no edits needed — public API preserved).

## Tests

No Compose UI tests in this PR — the legacy screen has zero tests, so no coverage regresses. Compose test infrastructure setup is a separate follow-up. The `computeCandidates` function is pure-Kotlin and could be unit-tested without Robolectric in a future PR.

## Tech-debt doc updates

### `docs/tech-debt-remediation.md`

- **REM-22 (Jetpack Compose adoption):** change status from "long-term, not started" to "**kicked off — first screen ported.**" Document the patterns established by this PR:
  - `BibleAppTheme` composable (Material 3 + dynamic color on Android 12+).
  - `ComponentActivity` + `setContent` (drop `BaseActivity`/AppCompat for new screens that don't need ActionBar).
  - `(Tab, Int, Int, Int) -> Unit` lambda instead of activity-implements-`Listener` interface.
  - Compose BOM `2026.04.01` (Compose 1.11.0 / Material3 1.4.0) in `gradle/libs.versions.toml` with `activity-compose:1.13.0` pinned.
  - `ExposedDropdownMenuBox` replacing `Spinner`+`Adapter`.
  - `LazyVerticalGrid` replacing `RecyclerView`+`GridLayoutManager`+`Adapter`.
  - `derivedStateOf` for autocomplete derivation.
  - `AnimatedContent` with directional slide for in-screen navigation.
  - BRICE I bumped from 1 → 2 (no longer greenfield).
  - Next candidate ports listed (matches the existing REM-22 plan): `AboutActivity` (Kotlin, View-based), `HelpActivity` (Java, WebView).

### `docs/tech-debt.md`

- Add a note under TD-12 that `androidx.percentlayout` is now used by fewer files (the most complex consumer, `fragment_goto_dialer.xml`, is gone). Once the remaining consumers are Compose'd, the dependency can be dropped.

## Out of scope

- Migrating `IsiActivity` or any other screen to Compose. This PR is the *foundation*.
- Removing `androidx.percentlayout` (still used by other layouts).
- Compose UI tests (separate setup PR).
- Migrating the existing AppCompat XML theme. The two coexist.
- Migrating `MaterialDialogJavaHelper` callers globally to `AlertDialog` Compose. The Direct tab uses the Compose `AlertDialog` for its own error case only.
