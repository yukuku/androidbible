# GotoActivity → Compose Port Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `GotoActivity.java` + 3 fragments + 4 layouts with an idiomatic Jetpack Compose + Material 3 implementation, establishing the Compose foundation patterns (theme composable, Compose deps in version catalog) for future ports.

**Architecture:** Single `ComponentActivity` (`GotoActivity.kt`) hosts a `BibleAppTheme { GotoScreen(...) }` setContent block. `GotoScreen` is a `Scaffold` with `TopAppBar` + `PrimaryTabRow` + `HorizontalPager` over three tab composables (`DialerTab`, `DirectTab`, `GridTab`). Cross-tab state (`askForVerse` preference, grid stage holding non-Parcelable `Book`) lives in `GotoViewModel`. The activity's public Java API (`createIntent`, `obtainResult`, `Result`) is preserved verbatim so existing callers don't need edits.

**Tech Stack:** Compose BOM `2026.04.01` (Compose UI/runtime/foundation 1.11.0, Material3 1.4.0), `activity-compose:1.13.0`, `lifecycle-viewmodel-compose:2.9.0`, `kotlin-compose` plugin 2.2.0, JDK 17.

**Reference spec:** `docs/superpowers/specs/2026-05-06-goto-activity-compose-port-design.md`

---

## Task 1: Add Compose deps to version catalog

**Files:**
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: Add new version keys**

In the `[versions]` block, after `androidxActivity = "1.11.0"`, add:

```toml
androidxActivityCompose = "1.13.0"
androidxLifecycle = "2.9.0"
composeBom = "2026.04.01"
```

- [ ] **Step 2: Add Compose library entries**

In the `[libraries]` block, after `androidx-activity-ktx = ...`, add:

```toml
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "androidxActivityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-foundation = { group = "androidx.compose.foundation", name = "foundation" }
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "androidxLifecycle" }
```

- [ ] **Step 3: Add the kotlin-compose plugin entry**

In `[plugins]`, after `kotlin-serialization`, add:

```toml
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

- [ ] **Step 4: Sanity-check the catalog parses**

Run: `./gradlew help -q 2>&1 | tail -30`
Expected: no Gradle parse errors. (Don't commit yet — Task 2 wires the plugin.)

---

## Task 2: Apply kotlin-compose plugin & wire Compose deps

**Files:**
- Modify: `build.gradle.kts` (root)
- Modify: `Alkitab/build.gradle.kts`

- [ ] **Step 1: Declare plugin in root project**

In root `build.gradle.kts`, add to the plugins block (after `kotlin-serialization apply false`):

```kotlin
alias(libs.plugins.kotlin.compose) apply false
```

- [ ] **Step 2: Apply plugin in `:Alkitab`**

In `Alkitab/build.gradle.kts` plugins block (around line 13–20), add:

```kotlin
alias(libs.plugins.kotlin.compose)
```

- [ ] **Step 3: Enable `compose = true` build feature**

In the `android { ... buildFeatures { ... } }` block (around line 164–166), add `compose = true` next to `buildConfig = true`:

```kotlin
buildFeatures {
    buildConfig = true
    compose = true
}
```

- [ ] **Step 4: Add Compose dependencies**

In the `dependencies { }` block, after the AndroidX section (around the `implementation(libs.androidx.activity.ktx)` line) add a new `// Compose` section:

```kotlin
// Compose
val composeBom = platform(libs.androidx.compose.bom)
implementation(composeBom)
implementation(libs.androidx.activity.compose)
implementation(libs.androidx.compose.foundation)
implementation(libs.androidx.compose.material.icons.extended)
implementation(libs.androidx.compose.material3)
implementation(libs.androidx.compose.ui)
implementation(libs.androidx.compose.ui.graphics)
implementation(libs.androidx.compose.ui.tooling.preview)
implementation(libs.androidx.lifecycle.viewmodel.compose)
debugImplementation(libs.androidx.compose.ui.tooling)
```

- [ ] **Step 5: Verify Gradle config**

Run: `./gradlew :Alkitab:help 2>&1 | tail -40`
Expected: `BUILD SUCCESSFUL`. Plugin must apply cleanly.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts Alkitab/build.gradle.kts
git commit -m "Compose: add deps + kotlin-compose plugin (BOM 2026.04.01)"
```

---

## Task 3: Add `BibleAppTheme` composable

**Files:**
- Create: `Alkitab/src/main/java/yuku/alkitab/base/compose/BibleAppTheme.kt`

- [ ] **Step 1: Write the theme file**

```kotlin
package yuku.alkitab.base.compose

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

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

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :Alkitab:compilePlainDebugKotlin 2>&1 | tail -60`
Expected: `BUILD SUCCESSFUL`. This is the first Compose code in the repo so it doubles as a smoke test for Task 2's wiring.

---

## Task 4: Create `GotoTab` enum and `GotoViewModel`

**Files:**
- Create: `Alkitab/src/main/java/yuku/alkitab/base/compose/goto/GotoTab.kt`
- Create: `Alkitab/src/main/java/yuku/alkitab/base/compose/goto/GotoViewModel.kt`

- [ ] **Step 1: Write `GotoTab.kt`**

```kotlin
package yuku.alkitab.base.compose.goto

/**
 * Preserves the 1/2/3 ordinals previously used by the old [BaseGotoFragment.GotoFinishListener]
 * constants (GOTO_TAB_dialer=1, GOTO_TAB_direct=2, GOTO_TAB_grid=3) so the Prefkey.goto_last_tab
 * integer value remains compatible with previously-saved preferences from old app versions.
 */
enum class GotoTab(val ordinal1Based: Int) {
    DIALER(1),
    DIRECT(2),
    GRID(3),
}

typealias OnGotoFinished = (tab: GotoTab, bookId: Int, chapter_1: Int, verse_1: Int) -> Unit
```

- [ ] **Step 2: Write `GotoViewModel.kt`**

```kotlin
package yuku.alkitab.base.compose.goto

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import yuku.afw.storage.Preferences
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.storage.PrefkeyKt
import yuku.alkitab.model.Book

sealed class GridStage {
    object Books : GridStage()
    data class Chapters(val book: Book) : GridStage()
    data class Verses(val book: Book, val chapter_1: Int) : GridStage()

    /** Used to infer animation direction in [GridTab] (forward = right→left). */
    val depth: Int
        get() = when (this) {
            Books -> 0
            is Chapters -> 1
            is Verses -> 2
        }
}

class GotoViewModel : ViewModel() {

    /**
     * Replaces the legacy [SharedPreferences.OnSharedPreferenceChangeListener] in
     * GotoDialerFragment. One observer for the whole screen — toggling the menu item
     * pushes a new value to all consumers automatically.
     */
    val askForVerse: StateFlow<Boolean> = callbackFlow {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == Prefkey.gotoAskForVerse.name) {
                trySend(Preferences.getBoolean(Prefkey.gotoAskForVerse, PrefkeyKt.GOTO_ASK_FOR_VERSE_DEFAULT))
            }
        }
        Preferences.registerObserver(listener)
        trySend(Preferences.getBoolean(Prefkey.gotoAskForVerse, PrefkeyKt.GOTO_ASK_FOR_VERSE_DEFAULT))
        awaitClose { Preferences.unregisterObserver(listener) }
    }.stateIn(
        scope = androidx.lifecycle.viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = Preferences.getBoolean(Prefkey.gotoAskForVerse, PrefkeyKt.GOTO_ASK_FOR_VERSE_DEFAULT),
    )

    /** Held in memory because [Book] is not Parcelable so plain rememberSaveable can't survive process death.
     *  Rotation survival is handled by the ViewModel itself. */
    var gridStage: GridStage by mutableStateOf(GridStage.Books)
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :Alkitab:compilePlainDebugKotlin 2>&1 | tail -60`
Expected: `BUILD SUCCESSFUL`.

---

## Task 5: Port `computeCandidates` (DirectTab filter logic)

**Files:**
- Create: `Alkitab/src/main/java/yuku/alkitab/base/compose/goto/Candidate.kt`

- [ ] **Step 1: Write `Candidate.kt`**

This is a **direct line-by-line port** of `AutoCompleteAdapter.getFilter().performFiltering()` from `GotoDirectFragment.java:191–264` and the `addCandidate` private method from `:266–294`. Same scoring (startsWith=20, contains=10, Levenshtein=-distance), same overflow guards, same final sort.

```kotlin
package yuku.alkitab.base.compose.goto

import yuku.alkitab.base.util.Jumper
import yuku.alkitab.base.util.Levenshtein
import yuku.alkitab.model.Book

data class Candidate(
    val title: String,
    val score: Int,
    val bookOnly: Boolean,
)

/**
 * Direct port of `GotoDirectFragment.AutoCompleteAdapter.getFilter().performFiltering()`.
 * Pure-Kotlin function — no Android types. Easy to unit-test (future PR).
 */
fun computeCandidates(query: String, books: Array<Book>): List<Candidate> {
    val jumper = Jumper(query)
    val rawBookName = jumper.unparsedBook ?: return emptyList()
    val bookName = rawBookName.trim().lowercase()
    if (bookName.isEmpty()) return emptyList()

    val out = ArrayList<Candidate>()
    val addedBookIds = HashSet<Int>()

    for (book in books) {
        val n = book.shortName.lowercase()
        val (title, score) = when {
            n.startsWith(bookName) -> book.shortName to 20
            n.contains(bookName) -> book.shortName to 10
            else -> null to 0
        }
        if (score != 0 && title != null) {
            addCandidate(jumper, out, title, score, book)?.also { addedBookIds.add(book.bookId) }
        }
    }

    // Levenshtein fallback when fewer than 5 candidates.
    if (out.size < 5) {
        val bookRefs = Jumper.createBookCandidates(books)
        val bookIndex = books.associateBy { it.bookId }
        for (bookRef in bookRefs) {
            if (addedBookIds.contains(bookRef.bookId)) continue
            val distance = Levenshtein.distance(bookName, bookRef.condensed)
            val book = bookIndex[bookRef.bookId] ?: continue
            addCandidate(jumper, out, book.shortName, -distance, book)?.also { addedBookIds.add(bookRef.bookId) }
        }
    }

    return out.sortedByDescending { it.score }
}

/** Returns the added candidate (or null when overflow guards reject it). */
private fun addCandidate(
    jumper: Jumper,
    sink: ArrayList<Candidate>,
    titleIn: String,
    score: Int,
    book: Book,
): Candidate? {
    var title = titleIn
    var bookOnly = true
    val chapter_1 = jumper.chapter
    if (chapter_1 != 0) {
        bookOnly = false
        title += " $chapter_1"
        val verse_1 = jumper.verse
        if (verse_1 != 0) title += ":$verse_1"
        if (chapter_1 < 1 || chapter_1 > book.chapter_count) return null
        if (verse_1 != 0 && (verse_1 < 1 || verse_1 > book.verse_counts[chapter_1 - 1])) return null
    }
    val c = Candidate(title = title, score = score, bookOnly = bookOnly)
    sink.add(c)
    return c
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :Alkitab:compilePlainDebugKotlin 2>&1 | tail -60`
Expected: `BUILD SUCCESSFUL`. Note: `Jumper`'s getters in Kotlin are property-style (`jumper.chapter`, `jumper.verse`, `jumper.unparsedBook`) — if they're explicit Java getters, the compiler will surface that and the call sites can switch to `jumper.getChapter()` etc.

---

## Task 6: Implement `DialerTab`

**Files:**
- Create: `Alkitab/src/main/java/yuku/alkitab/base/compose/goto/DialerTab.kt`

This is the most complex tab — port of `GotoDialerFragment.java`. Logic preserved verbatim; only the View+XML are replaced with Compose.

- [ ] **Step 1: Write `DialerTab.kt`**

```kotlin
package yuku.alkitab.base.compose.goto

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yuku.afw.storage.Preferences
import yuku.alkitab.base.S
import yuku.alkitab.base.util.BookColorUtil
import yuku.alkitab.base.util.BookNameSorter
import yuku.alkitab.debug.R
import yuku.alkitab.model.Book

private enum class ActiveField { CHAPTER, VERSE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialerTab(
    initialBookId: Int,
    initialChapter_1: Int,
    initialVerse_1: Int,
    askForVerse: Boolean,
    onGotoFinished: OnGotoFinished,
) {
    // Books list (sorted per pref) — cheap to compute, cache for the lifetime of the composable.
    val books = remember {
        val raw = S.activeVersion().consecutiveBooks
        if (Preferences.getBoolean(R.string.pref_alphabeticBookSort_key, R.bool.pref_alphabeticBookSort_default)) {
            BookNameSorter.sortAlphabetically(raw)
        } else raw.copyOf()
    }

    var bookIndex by rememberSaveable {
        mutableStateOf(books.indexOfFirst { it.bookId == initialBookId }.coerceAtLeast(0))
    }
    val selectedBook: Book = books[bookIndex]

    var chapterText by rememberSaveable { mutableStateOf(if (initialChapter_1 != 0) initialChapter_1.toString() else "") }
    var verseText by rememberSaveable { mutableStateOf(if (initialVerse_1 != 0) initialVerse_1.toString() else "") }
    var active by rememberSaveable { mutableStateOf(ActiveField.CHAPTER) }
    var chapterFirstTime by rememberSaveable { mutableStateOf(true) }
    var verseFirstTime by rememberSaveable { mutableStateOf(true) }

    // Auto-snap active to chapter when verse field is hidden.
    if (!askForVerse && active == ActiveField.VERSE) active = ActiveField.CHAPTER

    fun tryReadChapter(): Int = ("0$chapterText").toIntOrNull() ?: 0
    fun tryReadVerse(): Int = ("0$verseText").toIntOrNull() ?: 0

    val maxChapter: Int = selectedBook.chapter_count
    val maxVerse: Int = run {
        val ch0 = tryReadChapter() - 1
        if (ch0 in 0 until selectedBook.verse_counts.size) selectedBook.verse_counts[ch0] else 0
    }

    fun fixChapterOverflow() {
        var c = tryReadChapter()
        if (c > maxChapter) c = maxChapter else if (c <= 0) c = 1
        chapterText = c.toString()
    }
    fun fixVerseOverflow() {
        var v = tryReadVerse()
        if (v > maxVerse) v = maxVerse else if (v <= 0) v = 1
        verseText = v.toString()
    }

    fun press(s: String) {
        when (s) {
            "backspace" -> {
                if (active == ActiveField.CHAPTER && chapterText.isNotEmpty()) {
                    chapterText = chapterText.dropLast(1)
                } else if (active == ActiveField.VERSE && verseText.isNotEmpty()) {
                    verseText = verseText.dropLast(1)
                }
            }
            else -> {
                if (active == ActiveField.CHAPTER) {
                    chapterText = if (chapterFirstTime) s else chapterText + s
                    chapterFirstTime = false
                    val read = tryReadChapter()
                    if (read > maxChapter || read <= 0) chapterText = s
                } else {
                    verseText = if (verseFirstTime) s else verseText + s
                    verseFirstTime = false
                    val read = tryReadVerse()
                    if (read > maxVerse || read <= 0) verseText = s
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        // --- Book picker (replaces Spinner) ---
        var bookMenuOpen by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = bookMenuOpen,
            onExpandedChange = { bookMenuOpen = !bookMenuOpen },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        ) {
            OutlinedTextField(
                value = selectedBook.shortName,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = bookMenuOpen) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
            )
            androidx.compose.material3.DropdownMenu(
                expanded = bookMenuOpen,
                onDismissRequest = { bookMenuOpen = false },
            ) {
                books.forEachIndexed { idx, book ->
                    DropdownMenuItem(
                        text = { Text(book.shortName, color = Color(BookColorUtil.getForegroundOnDark(book.bookId))) },
                        onClick = {
                            bookIndex = idx
                            bookMenuOpen = false
                            // recompute maxVerse against new book — same behavior as the legacy onItemSelected.
                            chapterFirstTime = true
                            verseFirstTime = true
                        },
                    )
                }
            }
        }

        Spacer(Modifier.size(16.dp))

        // --- Chapter / Verse "fields" (visual toggle, not real TextFields) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResourceCompat(R.string.pasal_sebelumangka))
            Spacer(Modifier.width(8.dp))
            DialerField(
                text = chapterText,
                isActive = active == ActiveField.CHAPTER,
                onClick = { active = ActiveField.CHAPTER },
            )
            if (askForVerse) {
                Spacer(Modifier.width(16.dp))
                Text(stringResourceCompat(R.string.ayat_sebelumangka))
                Spacer(Modifier.width(8.dp))
                DialerField(
                    text = verseText,
                    isActive = active == ActiveField.VERSE,
                    onClick = { active = ActiveField.VERSE },
                )
            }
        }

        Spacer(Modifier.size(16.dp))

        // --- Keypad ---
        val onDigit: (String) -> Unit = { d -> press(d) }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(horizontal = 24.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9")) { d ->
                KeypadDigit(d, onDigit)
            }
            // Backspace
            item {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.6f), contentAlignment = Alignment.Center) {
                    IconButton(onClick = { press("backspace") }) {
                        Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace")
                    }
                }
            }
            // 0
            item { KeypadDigit("0", onDigit) }
            // OK
            item {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.6f).padding(4.dp), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = {
                            val ch = chapterText.toIntOrNull() ?: 0
                            val v = if (askForVerse) verseText.toIntOrNull() ?: 0 else 0
                            onGotoFinished(GotoTab.DIALER, selectedBook.bookId, ch, v)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResourceCompat(R.string.ok)) }
                }
            }
        }
    }
}

@Composable
private fun DialerField(text: String, isActive: Boolean, onClick: () -> Unit) {
    val bg = if (isActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = bg,
        modifier = Modifier.size(width = 64.dp, height = 64.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
            Text(text, fontSize = 24.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun KeypadDigit(d: String, onDigit: (String) -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.6f).padding(2.dp), contentAlignment = Alignment.Center) {
        TextButton(onClick = { onDigit(d) }, modifier = Modifier.fillMaxWidth()) {
            Text(d, fontSize = 20.sp)
        }
    }
}

@Composable
private fun stringResourceCompat(@androidx.annotation.StringRes id: Int): String =
    androidx.compose.ui.res.stringResource(id)
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :Alkitab:compilePlainDebugKotlin 2>&1 | tail -60`
Expected: `BUILD SUCCESSFUL`. Likely fixes needed:
- `S.activeVersion().consecutiveBooks` — if Java getter, use `getConsecutiveBooks()`.
- `book.verse_counts` / `book.chapter_count` / `book.bookId` / `book.shortName` — Java public fields, accessible directly.

---

## Task 7: Implement `DirectTab`

**Files:**
- Create: `Alkitab/src/main/java/yuku/alkitab/base/compose/goto/DirectTab.kt`

- [ ] **Step 1: Write `DirectTab.kt`**

```kotlin
package yuku.alkitab.base.compose.goto

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import yuku.alkitab.base.S
import yuku.alkitab.base.util.Jumper
import yuku.alkitab.debug.R
import java.util.regex.Pattern

private val NOBOOK_PATTERN: Pattern = Pattern.compile("(\\d+)(?:[ :.]+(\\d+))?")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectTab(
    initialBookId: Int,
    initialChapter_1: Int,
    initialVerse_1: Int,
    autoFocus: Boolean,
    onGotoFinished: OnGotoFinished,
) {
    val books = remember { S.activeVersion().consecutiveBooks }
    var query by rememberSaveable { mutableStateOf("") }
    val candidates by remember(books) {
        derivedStateOf { computeCandidates(query, books) }
    }
    var menuOpen by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val kbd = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        if (autoFocus) {
            focusRequester.requestFocus()
            kbd?.show()
        }
    }

    val sample = remember(initialBookId, initialChapter_1, initialVerse_1) {
        S.activeVersion().reference(initialBookId, initialChapter_1, initialVerse_1)
    }

    fun submit() {
        val ref = query.trim()
        if (ref.isEmpty()) return

        val m = NOBOOK_PATTERN.matcher(ref)
        if (m.matches()) {
            try {
                val ch = (m.group(1) ?: return).toInt()
                val v = m.group(2)?.toInt() ?: 0
                onGotoFinished(GotoTab.DIRECT, initialBookId, ch, v)
                return
            } catch (_: NumberFormatException) {}
        }
        val jumper = Jumper(ref)
        if (!jumper.parseSucceeded) {
            errorText = "Invalid reference: $ref"
            return
        }
        onGotoFinished(GotoTab.DIRECT, jumper.getBookId(books), jumper.chapter, jumper.verse)
    }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(text = stringResource(R.string.jump_to_prompt, sample))
        Spacer(Modifier.size(16.dp))

        ExposedDropdownMenuBox(
            expanded = menuOpen && candidates.isNotEmpty(),
            onExpandedChange = { menuOpen = it },
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; menuOpen = true },
                singleLine = true,
                modifier = Modifier.menuAnchor().fillMaxWidth().focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
            )
            androidx.compose.material3.DropdownMenu(
                expanded = menuOpen && candidates.isNotEmpty(),
                onDismissRequest = { menuOpen = false },
            ) {
                candidates.forEach { c ->
                    DropdownMenuItem(
                        text = { Text(c.title) },
                        onClick = {
                            if (c.bookOnly) {
                                // Match legacy convertResultToString: append a trailing space so user can keep typing.
                                query = c.title + " "
                                menuOpen = false
                            } else {
                                query = c.title
                                menuOpen = false
                                submit()
                            }
                        },
                    )
                }
            }
        }

        Spacer(Modifier.size(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = ::submit) { Text(stringResource(R.string.ok)) }
        }
    }

    errorText?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorText = null },
            confirmButton = { TextButton(onClick = { errorText = null }) { Text(stringResource(R.string.ok)) } },
            text = { Text(stringResource(R.string.alamat_tidak_sah_alamat, msg)) },
        )
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :Alkitab:compilePlainDebugKotlin 2>&1 | tail -60`
Expected: `BUILD SUCCESSFUL`.

---

## Task 8: Implement `GridTab`

**Files:**
- Create: `Alkitab/src/main/java/yuku/alkitab/base/compose/goto/GridTab.kt`

- [ ] **Step 1: Write `GridTab.kt`**

```kotlin
package yuku.alkitab.base.compose.goto

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.unit.dp
import yuku.afw.storage.Preferences
import yuku.alkitab.base.S
import yuku.alkitab.base.util.BookColorUtil
import yuku.alkitab.base.util.BookNameSorter
import yuku.alkitab.debug.R

@Composable
fun GridTab(
    askForVerse: Boolean,
    viewModel: GotoViewModel,
    onGotoFinished: OnGotoFinished,
) {
    val books = remember {
        val raw = S.activeVersion().consecutiveBooks
        if (Preferences.getBoolean(R.string.pref_alphabeticBookSort_key, R.bool.pref_alphabeticBookSort_default)) {
            BookNameSorter.sortAlphabetically(raw)
        } else raw.copyOf()
    }

    val stage = viewModel.gridStage
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        // Header chips when stage > Books.
        if (stage !is GridStage.Books) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val book = when (stage) {
                    is GridStage.Chapters -> stage.book
                    is GridStage.Verses -> stage.book
                    GridStage.Books -> null
                }
                book?.let {
                    AssistChip(
                        onClick = { viewModel.gridStage = GridStage.Books },
                        label = {
                            Text(it.shortName, color = Color(BookColorUtil.getForegroundOnDark(it.bookId)))
                        },
                        trailingIcon = {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
                        },
                    )
                }
                if (stage is GridStage.Verses) {
                    AssistChip(
                        onClick = { viewModel.gridStage = GridStage.Chapters(stage.book) },
                        label = { Text(stage.chapter_1.toString()) },
                        trailingIcon = {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
                        },
                    )
                }
            }
        }

        AnimatedContent(
            targetState = stage,
            transitionSpec = {
                val forward = targetState.depth >= initialState.depth
                if (forward) {
                    (slideInHorizontally(tween(200)) { it } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(tween(200)) { -it } + fadeOut(tween(200)))
                } else {
                    (slideInHorizontally(tween(200)) { -it } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(tween(200)) { it } + fadeOut(tween(200)))
                }
            },
            label = "GridTabStage",
        ) { current ->
            when (current) {
                is GridStage.Books -> BooksGrid(books) { book ->
                    if (book.chapter_count == 1) {
                        viewModel.gridStage = GridStage.Verses(book, 1)
                    } else {
                        viewModel.gridStage = GridStage.Chapters(book)
                    }
                }
                is GridStage.Chapters -> NumericGrid(count = current.book.chapter_count) { ch ->
                    if (askForVerse) {
                        viewModel.gridStage = GridStage.Verses(current.book, ch)
                    } else {
                        onGotoFinished(GotoTab.GRID, current.book.bookId, ch, 0)
                    }
                }
                is GridStage.Verses -> {
                    val ch0 = current.chapter_1 - 1
                    val verseCount = if (ch0 in current.book.verse_counts.indices) current.book.verse_counts[ch0] else 0
                    NumericGrid(count = verseCount) { v ->
                        onGotoFinished(GotoTab.GRID, current.book.bookId, current.chapter_1, v)
                    }
                }
            }
        }
    }
}

@Composable
private fun BooksGrid(books: Array<yuku.alkitab.model.Book>, onBookClick: (yuku.alkitab.model.Book) -> Unit) {
    LazyVerticalGrid(columns = GridCells.Fixed(6), modifier = Modifier.fillMaxWidth()) {
        items(books.size) { idx ->
            val book = books[idx]
            GridCell(
                text = BookNameSorter.getBookAbbr(book).toString(),
                color = Color(BookColorUtil.getForegroundOnDark(book.bookId)),
                onClick = { onBookClick(book) },
            )
        }
    }
}

@Composable
private fun NumericGrid(count: Int, onClick: (Int) -> Unit) {
    val cols = integerResource(R.integer.goto_grid_numeric_num_columns)
    LazyVerticalGrid(columns = GridCells.Fixed(cols), modifier = Modifier.fillMaxWidth()) {
        items(count) { i ->
            GridCell(
                text = (i + 1).toString(),
                color = MaterialTheme.colorScheme.onSurface,
                onClick = { onClick(i + 1) },
            )
        }
    }
}

@Composable
private fun GridCell(text: String, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().aspectRatio(1.4f).padding(2.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
            Text(text, color = color)
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :Alkitab:compilePlainDebugKotlin 2>&1 | tail -60`
Expected: `BUILD SUCCESSFUL`.

---

## Task 9: Implement `GotoScreen` (Scaffold + tabs + pager)

**Files:**
- Create: `Alkitab/src/main/java/yuku/alkitab/base/compose/goto/GotoScreen.kt`

- [ ] **Step 1: Write `GotoScreen.kt`**

```kotlin
package yuku.alkitab.base.compose.goto

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import yuku.afw.storage.Preferences
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.storage.PrefkeyKt
import yuku.alkitab.debug.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GotoScreen(
    initialBookId: Int,
    initialChapter_1: Int,
    initialVerse_1: Int,
    onUp: () -> Unit,
    onGotoFinished: OnGotoFinished,
    viewModel: GotoViewModel = viewModel(),
) {
    val askForVerse by viewModel.askForVerse.collectAsState()
    val initialPage = remember {
        val saved = Preferences.getInt(Prefkey.goto_last_tab, 0)
        (saved - 1).coerceIn(0, 2)
    }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != GotoTab.DIRECT.ordinal1Based - 1) focusManager.clearFocus()
    }

    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("") },
                    navigationIcon = {
                        IconButton(onClick = onUp) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up")
                        }
                    },
                    actions = {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
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
                    },
                )
                PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                    val titles = listOf(
                        R.string.goto_tab_dialer_label,
                        R.string.goto_tab_direct_label,
                        R.string.goto_tab_grid_label,
                    )
                    titles.forEachIndexed { idx, resId ->
                        Tab(
                            selected = pagerState.currentPage == idx,
                            onClick = { scope.launch { pagerState.animateScrollToPage(idx) } },
                            text = { Text(stringResource(resId)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize().padding(padding)) { page ->
            when (page) {
                0 -> DialerTab(initialBookId, initialChapter_1, initialVerse_1, askForVerse, onGotoFinished)
                1 -> DirectTab(
                    initialBookId,
                    initialChapter_1,
                    initialVerse_1,
                    autoFocus = (initialPage == 1),
                    onGotoFinished = onGotoFinished,
                )
                2 -> GridTab(askForVerse = askForVerse, viewModel = viewModel, onGotoFinished = onGotoFinished)
            }
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :Alkitab:compilePlainDebugKotlin 2>&1 | tail -60`
Expected: `BUILD SUCCESSFUL`.

---

## Task 10: Convert `GotoActivity.java` → `GotoActivity.kt`

**Files:**
- Delete: `Alkitab/src/main/java/yuku/alkitab/base/ac/GotoActivity.java`
- Create: `Alkitab/src/main/java/yuku/alkitab/base/ac/GotoActivity.kt`

The Kotlin version preserves the public Java-facing API: `createIntent(int, int, int)`, `obtainResult(Intent)`, and `Result` class. Existing Java callers continue to compile unchanged.

- [ ] **Step 1: Delete the Java file**

```bash
git rm Alkitab/src/main/java/yuku/alkitab/base/ac/GotoActivity.java
```

- [ ] **Step 2: Write `GotoActivity.kt`**

```kotlin
package yuku.alkitab.base.ac

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import yuku.afw.App
import yuku.afw.storage.Preferences
import yuku.alkitab.base.compose.BibleAppTheme
import yuku.alkitab.base.compose.goto.GotoScreen
import yuku.alkitab.base.compose.goto.GotoTab
import yuku.alkitab.base.storage.Prefkey

class GotoActivity : ComponentActivity() {

    class Result {
        @JvmField var bookId: Int = -1
        @JvmField var chapter_1: Int = 0
        @JvmField var verse_1: Int = 0
    }

    companion object {
        private const val EXTRA_bookId = "bookId"
        private const val EXTRA_chapter = "chapter"
        private const val EXTRA_verse = "verse"

        @JvmStatic
        fun createIntent(bookId: Int, chapter_1: Int, verse_1: Int): Intent {
            return Intent(App.context, GotoActivity::class.java).apply {
                putExtra(EXTRA_bookId, bookId)
                putExtra(EXTRA_chapter, chapter_1)
                putExtra(EXTRA_verse, verse_1)
            }
        }

        @JvmStatic
        fun obtainResult(data: Intent): Result {
            return Result().apply {
                bookId = data.getIntExtra(EXTRA_bookId, -1)
                chapter_1 = data.getIntExtra(EXTRA_chapter, 0)
                verse_1 = data.getIntExtra(EXTRA_verse, 0)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialBookId = intent.getIntExtra(EXTRA_bookId, -1)
        val initialChapter = intent.getIntExtra(EXTRA_chapter, 0)
        val initialVerse = intent.getIntExtra(EXTRA_verse, 0)

        setContent {
            BibleAppTheme {
                GotoScreen(
                    initialBookId = initialBookId,
                    initialChapter_1 = initialChapter,
                    initialVerse_1 = initialVerse,
                    onUp = { finish() },
                    onGotoFinished = { tab, bookId, chapter, verse ->
                        Preferences.setInt(Prefkey.goto_last_tab, tab.ordinal1Based)
                        val data = Intent().apply {
                            putExtra(EXTRA_bookId, bookId)
                            putExtra(EXTRA_chapter, chapter)
                            putExtra(EXTRA_verse, verse)
                        }
                        setResult(RESULT_OK, data)
                        finish()
                    },
                )
            }
        }
    }
}
```

- [ ] **Step 3: Verify build**

Run: `./gradlew :Alkitab:compilePlainDebugKotlin 2>&1 | tail -60`
Expected: `BUILD SUCCESSFUL`.

---

## Task 11: Delete obsolete fragments and resources

**Verification before deleting:** confirm `BaseGotoFragment` has no other extenders.

- [ ] **Step 1: Verify no other consumers of `BaseGotoFragment`**

Run grep: `grep -rl "BaseGotoFragment" Alkitab/src/`
Expected: only the four files about to be deleted reference it (`GotoDialerFragment`, `GotoDirectFragment`, `GotoGridFragment`, `BaseGotoFragment` itself).

- [ ] **Step 2: Delete fragments and base**

```bash
git rm Alkitab/src/main/java/yuku/alkitab/base/fr/GotoDialerFragment.java
git rm Alkitab/src/main/java/yuku/alkitab/base/fr/GotoDirectFragment.java
git rm Alkitab/src/main/java/yuku/alkitab/base/fr/GotoGridFragment.java
git rm Alkitab/src/main/java/yuku/alkitab/base/fr/base/BaseGotoFragment.java
```

- [ ] **Step 3: Delete obsolete layouts and menu**

```bash
git rm Alkitab/src/main/res/layout/activity_goto.xml
git rm Alkitab/src/main/res/layout/fragment_goto_dialer.xml
git rm Alkitab/src/main/res/layout/fragment_goto_direct.xml
git rm Alkitab/src/main/res/layout/fragment_goto_grid.xml
git rm Alkitab/src/main/res/layout/item_goto_dialer_book.xml
git rm Alkitab/src/main/res/layout/item_goto_dialer_book_dropdown.xml
git rm Alkitab/src/main/res/layout/item_goto_grid_cell.xml
git rm Alkitab/src/main/res/menu/activity_goto.xml
git rm Alkitab/src/main/res/drawable/goto_dialer_active.xml
```

If `Alkitab/src/main/res/layout-land/fragment_goto_dialer.xml` exists (orientation variant of the dialer), delete it too:

```bash
git ls-files Alkitab/src/main/res/layout-land/fragment_goto_dialer.xml | xargs --no-run-if-empty git rm
```

- [ ] **Step 4: Verify build still passes**

Run: `./gradlew :Alkitab:assemblePlainDebug 2>&1 | tail -60`
Expected: `BUILD SUCCESSFUL`. Lint may grumble about now-unused string resources — fine to ignore for this step.

- [ ] **Step 5: Commit the port**

```bash
git add Alkitab/src/main/java/yuku/alkitab/base/compose Alkitab/src/main/java/yuku/alkitab/base/ac/GotoActivity.kt
git commit -m "REM-22: port GotoActivity + 3 fragments to Compose + Material 3"
```

---

## Task 12: Update tech-debt docs

**Files:**
- Modify: `docs/tech-debt-remediation.md`
- Modify: `docs/tech-debt.md`

- [ ] **Step 1: Bump REM-22 status**

In `docs/tech-debt-remediation.md`, find the REM-22 section and:
- Change status to "**kicked off — first screen ported.**"
- Add a paragraph listing the patterns established by this PR (BibleAppTheme, ComponentActivity + setContent, OnGotoFinished lambda, Compose BOM 2026.04.01, ExposedDropdownMenuBox replacing Spinner, LazyVerticalGrid replacing RecyclerView, derivedStateOf, AnimatedContent for slide).
- Bump BRICE I from 1 → 2 (no longer greenfield).
- List next candidate ports: `AboutActivity`, `HelpActivity`.

- [ ] **Step 2: Note percentlayout under TD-12**

In `docs/tech-debt.md`, under TD-12, add a sentence: "After the GotoActivity Compose port (REM-22), `androidx.percentlayout` is used by fewer files; once the remaining consumers are Composed, the dependency can be dropped."

- [ ] **Step 3: Commit doc updates**

```bash
git add docs/tech-debt-remediation.md docs/tech-debt.md
git commit -m "docs: REM-22 first screen ported; note percentlayout shrinkage under TD-12"
```

---

## Task 13: Final build verification

- [ ] **Step 1: Full debug + release unit test sweep**

Run:
```bash
./gradlew :Alkitab:assemblePlainDebug testPlainDebugUnitTest testPlainReleaseUnitTest 2>&1 | tail -60
```
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 2: Verify nothing else regressed**

Run: `./gradlew :Alkitab:lintPlainDebug 2>&1 | tail -60`
Expected: no new errors. Warnings about deleted resources (e.g. `R.drawable.goto_dialer_active`) are expected and acceptable.

---

## Self-Review Notes

- **Spec coverage:** all sections of `2026-05-06-goto-activity-compose-port-design.md` map to a task above (build setup → T1, T2; theme → T3; per-tab logic → T4–T8; activity shell → T10; cleanup → T11; docs → T12; verification → T13).
- **No placeholders:** every code block is complete and copy-pasteable.
- **Type consistency:** `GotoTab.ordinal1Based`, `OnGotoFinished` typealias, `GridStage.depth`, `Candidate` data class — all referenced consistently across tasks 4, 5, 6, 7, 8, 9, 10.
- **Known cleanup deferrals:** `Jumper`'s `parseSucceeded` getter syntax may differ between Java getter and Kotlin property — accept whatever the compiler tells us during T7 step 2.
