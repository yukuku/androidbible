# Architecture Deep Dive

## Singleton & Service Locator Pattern

The app does not use dependency injection. Instead, it relies on singletons accessed through `S.kt`:

```
S.db         → InternalDb (lazy)     — main database
S.songDb     → SongDb (lazy)         — songs database
S.activeVersion()                     — currently selected Bible version
S.applied()  → CalculatedDimensions  — computed UI metrics from preferences
```

`App.staticInit()` must be called before any `S` access. This is done in `App.onCreate()` and also defensively in the `ContentProvider.onCreate()`.

## Module Dependency Graph

```
Alkitab (main app)
├── AlkitabModel (data models: Ari, Version, Book, MVersion)
├── AlkitabIo (BibleReader interface, I/O utilities)
│   └── AlkitabModel
├── AlkitabYes2 (YES2 format reader/writer)
│   ├── AlkitabIo
│   ├── AlkitabModel
│   ├── BintexReader / BintexWriter
│   └── Snappy (native JNI compression)
├── AlkitabIntegration (inter-app API)
├── AlkitabFeedback
├── BiblePlus (PDB format reader)
├── KpriModel (song data model)
├── FlowLayout
├── Afw (base framework: Preferences, EasyAdapter, App context)
├── ImportedDesktopVerseUtil (verse reference parser)
└── PrDownloaderFixed (HTTP file downloader)
```

## Activity Architecture

The app uses traditional Activity/Fragment architecture (no Navigation Component, no Jetpack Compose):

- **IsiActivity** — main reader (monolithic, ~2900 lines). Handles:
  - Bible text display via `VersesControllerImpl` (RecyclerView)
  - Split-screen parallel version comparison
  - Navigation history (`BackForwardListController`)
  - Action mode for verse selection (copy, share, bookmark, etc.)
  - Two-finger gestures (pinch zoom, chapter swipe)
  - Volume button navigation
  - Extension integration

- **GotoActivity** — verse navigation (book/chapter/verse picker)
- **SearchActivity** — full-text search with results
- **VersionsActivity** — version download/management
- **MarkerListActivity** — bookmark/note/highlight browser
- **DevotionActivity** — daily devotional reader
- **ReadingPlanActivity** — reading plan progress tracker
- **SongListActivity / SongViewActivity** — song browser and viewer
- **SettingsActivity** — app preferences
- **DataTransferActivity** — export/import

## Split View System

`IsiActivity` supports displaying two Bible versions simultaneously:
- `activeSplit0` (primary, always present) — `ActiveSplit0(mv, version)` data class
- `activeSplit1` (secondary, optional) — `ActiveSplit1(mv, version)` data class
- Layout toggles between horizontal and vertical via `splitHandleButton`
- Verse scroll position and selection are synchronized between splits
- Each split has its own `VersesControllerImpl` instance (`lsSplit0`, `lsSplit1`)

## Preferences Architecture

`Prefkey.kt` defines 100+ preference keys as an enum. Values are accessed via `Afw.Preferences` which wraps `SharedPreferences` with:
- In-memory caching for frequently accessed values
- Batch commit optimization
- Weak-reference observer pattern for change notification

`S.applied()` returns `CalculatedDimensions` — a computed snapshot of all UI-relevant preferences (font sizes, colors, line spacing, indentation). This is recalculated on preference changes and applied globally.

## Extension System

The app supports external extensions (other apps) that can:
- Provide additional verse information (maps, dictionaries, commentaries)
- Register via broadcast intents
- Be invoked from the verse action mode in `IsiActivity`
- Communicate via the `AlkitabIntegration` API

## Version Loading Architecture

```
MVersion.getVersion()
  → VersionImpl (cached via SoftReference in ConcurrentHashMap)
    → YesReaderFactory.createYesReader(filename)
      → Yes2Reader (or Yes1Reader for legacy files)
        → BibleReader interface
          → loadBooks(), loadChapterText(), loadPericope(), etc.
```

`SoftReference` caching means version data can be GC'd under memory pressure and transparently reloaded on next access.

## Threading Model

- UI thread for all display and user interaction
- Background threads for:
  - Sync operations (via WorkManager)
  - Devotion downloads (`DevotionDownloader` — dedicated thread with queue)
  - Song book downloads
  - File imports
  - Widget updates
- No RxJava or coroutines for async — uses traditional threads, `AsyncTask` remnants, and WorkManager
