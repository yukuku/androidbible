# Architecture Deep Dive

## Singleton & Service Locator Pattern

The app does not use a DI framework. State is reached through two layers:

```
App.services.storage.db       → InternalDb (lazy)     — main database (markers, labels, etc.)
App.services.storage.songDb   → SongDb (lazy)         — songs database
App.services.versions.activeVersion()                 — currently selected Bible version
App.services.uiDimensions.applied() → CalculatedDimensions
```

`App.services` is an `AppServices` container holding three interfaces — `StorageProvider`, `VersionManager`, `UiDimensionsProvider` — introduced by REM-24. In production all three are implemented by adapter properties on `S` (`S.storage`, `S.versions`, `S.uiDimensions`). The original `S.db` / `S.applied()` / `S.activeVersion()` accessors still work for legacy callers, and `App.services` is initialized eagerly at the class-load of `App.java` so it is non-null even from tests that bypass `App.onCreate()` / `App.staticInit()`.

`App.staticInit()` must be called before features that depend on Firebase, FCM, extensions, or feedback senders. It is invoked from `App.onCreate()` and defensively from `ContentProvider.onCreate()`.

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
└── ImportedDesktopVerseUtil (verse reference parser)
```

HTTP file downloads (Bible versions) run inside `VersionDownloadWorker` (`CoroutineWorker`) using OkHttp; the previous `PrDownloaderFixed` module was deleted in REM-19, and the `AmbilWarna` color-picker module was deleted in REM-20.

## Activity Architecture

The app uses traditional Activity/Fragment architecture for the reader, with some surfaces on Jetpack Compose (REM-20, REM-22): the color picker, the Bible-audio bar, and a Compose port of the Goto screen that is currently gated behind an experimental flag (`ExperimentalFlags.useComposeGoto()`) with the legacy View-based screens as the default:

- **IsiActivity** — main reader (~2155 lines, down from ~2900). After REM-06/07/08 it still owns:
  - Bible text display via `VersesControllerImpl` (RecyclerView)
  - Navigation history (`BackForwardListController`)
  - Volume-button navigation
  - Extension integration
  Extracted siblings (under `yuku.alkitab.base.widget`):
  - `ReaderGestureHandler` — two-finger pinch zoom, chapter swipe, goto-button floater drag
  - `VerseActionModeController` — copy/share/bookmark action mode
  - `SplitViewManager` — split-screen parallel version comparison

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

Split-pane display of two Bible versions is owned by `SplitViewManager` (`Alkitab/src/main/java/yuku/alkitab/base/widget/SplitViewManager.kt`), wired into `IsiActivity` via the `SplitViewHost` and `SplitViewActions` interfaces:
- `activeSplit0` (primary, always present) — `ActiveSplit0(mv, version)` data class still on `IsiActivity`
- `activeSplit1` (secondary, optional) — `ActiveSplit1` data class lives in `yuku.alkitab.base.widget`; the manager owns the mutable state and `IsiActivity.activeSplit1` is a read-only getter that delegates
- Layout toggles between horizontal and vertical via `splitHandleButton`; the manager persists `lastSplitOrientation` / `lastSplitProp` / `lastSplitVersionId` across launches
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
- Background work:
  - Sync operations run as a `WorkManager` worker (`SyncAdapter`)
  - Bible-version downloads run as a `CoroutineWorker` (`VersionDownloadWorker`) using OkHttp + Range-based resume; observed via `WorkManager.getWorkInfoByIdFlow` in `DownloadMapper` (REM-19)
  - `DevotionDownloader` uses a single-thread `ExecutorService` with a `LinkedBlockingDeque` queue and a clean shutdown path (REM-05)
  - Song-book downloads use raw OkHttp via `Connections.downloadCall(...)`
  - File imports and widget updates
- Audio playback runs in two foreground `MediaSessionService`s (`BibleAudioService` for Bible chapter audio, `SongAudioService` for hymns), arbitrated to at most one active session by `AudioPlaybackCoordinator` — see `docs/modules/audio-playback.md`
- Coroutines are used in the migrated paths above (workers, the audio subsystem, gesture-flow collectors, the `AppEvents` `SharedFlow` buses introduced by REM-03) but the codebase still has `Thread` / `Handler` remnants — see `docs/tech-debt.md`
