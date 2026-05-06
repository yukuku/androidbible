# Client Implementation Plan — Bible Audio Playback

**Repo:** `yukuku/androidbible`
**Target branch:** cut a fresh feature branch from `develop` (e.g. `feature/audio-bible`); close #127 and #124 afterwards.
**Estimated effort:** ~3 sprint-weeks for one engineer (v1 must-haves). Pre-download (v2) is another week.

This plan assumes the [PRD](prd.md) is approved and the [backend plan](backend-plan.md) lands (at least catalog + timing endpoints) before milestone M3.

**Backend status:** the [backend plan](backend-plan.md) is now grounded in the real `alkitab-host` stack (Python 3.12 / Flask on App Engine Standard, NDB Datastore for the timing cache, `web.cache` LRU for in-memory hits, top-level `/audio/*` namespace served by the `web-py3` service). No `dispatch.yaml` change is required — the catch-all already routes `/audio/*` to web-py3. The client is therefore unblocked: we ship with the bundled fallback catalog and the feature works end-to-end against `media.sabda.org` even if the backend deploy slips.

---

## 0. Prerequisites

- [ ] Read the PRD in `prd.md` and the backend plan in `backend-plan.md`.
- [ ] **Do not cherry-pick from PR #127 or PR #124.** Both PRs will be closed unmerged once this work lands. They are useful as inspiration only — the SABDA timing-API tables in PR #127 have moved to the backend, the activity-scoped player from both PRs is replaced by a `MediaSessionService`, and the entire UI surface is rewritten in Compose. Re-write the player wrapper (`BibleAudioPlayer`), the repository (`BibleAudioRepository`), the highlight tracker, and the verse-overlay code from scratch with the conventions called out in this plan.
- [ ] Confirm `androidx.media3:media3-session` is not yet in the build; add it in M1.
- [ ] Confirm Jetpack Compose is not yet in the build (it isn't — this feature is the project's first Compose surface); add the dependency family + Kotlin Compose Compiler plugin in M1.
- [ ] Access to the backend staging environment (base URL is `BuildConfig.SERVER_HOST`).

---

## 1. Milestones

### M1 — Skeleton, deps, & catalog (≈ 3 days, +1 for the Compose bootstrap)

**Goal:** app builds with the new dependency family wired in (media3-session, Compose 1.11.0, Compose Compiler plugin); catalog loads from backend with a bundled fallback.

- [ ] Add `androidx-media3-session = { group = "androidx.media3", name = "media3-session", version.ref = "androidxMedia3" }` to `gradle/libs.versions.toml` and `implementation(libs.androidx.media3.session)` in `Alkitab/build.gradle.kts`.
- [ ] Add Compose 1.11.0 to the project as **the first Compose surface in the codebase**:
    - In `gradle/libs.versions.toml`:
        ```toml
        androidxComposeUi = "1.11.0"
        androidxComposeMaterial3 = "1.4.0"   # paired with compose-ui 1.11 per Compose BOM 2026.04.01
        ```
        Plus library entries for `androidx-compose-ui`, `androidx-compose-foundation`, `androidx-compose-runtime`, `androidx-compose-material3`, `androidx-compose-ui-tooling-preview`, `androidx-compose-ui-tooling`, `androidx-activity-compose`.
    - In `Alkitab/build.gradle.kts`:
        - Apply the `org.jetbrains.kotlin.plugin.compose` plugin (Kotlin 2.x ships the Compose compiler as a separate plugin; matches the project's `kotlin = "2.2.0"`).
        - Add `buildFeatures { compose = true }` to the `android {}` block.
        - Add the `implementation(libs.androidx.compose.ui)` etc. dependencies.
    - Add `id("org.jetbrains.kotlin.plugin.compose") apply false` to the root `build.gradle.kts` plugin list (root project alias).
- [ ] Verify `./gradlew assemblePlainDebug` succeeds with the new deps in place. If `compose-material3:1.5.0` is not yet published, bump to the most recent stable that pairs with `compose-ui:1.11.0`.
- [ ] Create package `yuku.alkitab.base.audio` and skeleton files per PRD §5.7.
- [ ] Model classes: `AudioVersion.kt`, `ChapterTiming.kt`, `VerseTiming.kt`, `AudioCatalog.kt`.
- [ ] `AudioCatalogRepository`:
    - Uses `Connections.okHttp` and `BuildConfig.SERVER_HOST` (defined at `Alkitab/build.gradle.kts:123`; resolves to `https://alkitab.app` for production flavors).
    - `GET ${SERVER_HOST}/audio/catalog?${App.getAppIdentifierParamsEncoded()}` with conditional `If-None-Match` when we have an ETag. Mirror the `appIdentifier`/`packageName`/`versionCode` query-param style used by `VersionConfigUpdaterService` (`Alkitab/src/main/java/yuku/alkitab/base/sv/VersionConfigUpdaterService.java:93`) and `DevotionDownloader` (`DevotionDownloader.java:51`).
    - 200 → parse JSON, persist to `files/audio_catalog.json`, store ETag in `Prefkey.audioCatalog_etag`.
    - 304 → keep current cache.
    - Non-2xx → fall back to bundled `Alkitab/src/main/assets/audio_catalog.json` (checked in with the four known SABDA versions; same shape as the live `/audio/catalog` response). The feature works end-to-end on the bundled fallback alone, so client and backend can ship independently.
    - `suspend fun loadCatalog(): AudioCatalog` returns the cached+merged view.
    - `fun isAudioAvailable(versionId: String): Boolean` for the toolbar icon visibility.
- [ ] Add a Prefkey entry `audioCatalog_etag` (and `audioPlaybackSpeed` for M5) in `Prefkey.kt`.
- [ ] Per-flavor `BuildConfig.INTERNAL_VERSION_AUDIO_ID` in `Alkitab/build.gradle.kts` (see PRD §5.4.1). Default `""` in `defaultConfig`; override `"preset/in-tb"` for `plain`/`yuku_alkitab`/`sabda_alkitab` and `"preset/en-kjv"` for `yuku_quick_bible`. Wire the substitution into `AudioCatalogRepository.findEntry` so `MVersion.getVersionId() == "internal"` resolves to the correct catalog row.
- [ ] Background refresh: piggyback on the existing `VersionConfigUpdaterService` (see `docs/backend-communication.md:39-41`) — add a parallel catalog fetch so we don't spawn a new worker.
- [ ] Unit tests (`Alkitab/src/test/java/.../audio/AudioCatalogRepositoryTest.kt`): parsing, ETag handling, cache fallback.

**Exit criteria:** `./gradlew testPlainDebugUnitTest` green; the repository returns a populated catalog on device against staging.

### M2 — Player + repository (≈ 3 days)

**Goal:** single-stream audio plays end-to-end for the active chapter; verses highlight and scroll.

- [ ] `BibleAudioPlayer.kt` — write from scratch. Single-responsibility wrapper around media3 ExoPlayer with a `Listener` (`onReady`, `onEnded`, `onError`). Main-thread-only API. Use the OkHttp `DataSource.Factory` pattern from `yuku.alkitab.songs.ExoplayerController.kt:8-17` (same project convention, same dependency).
- [ ] `BibleAudioRepository.kt` — written from scratch, lives at `yuku.alkitab.base.audio.BibleAudioRepository`. Surface:
    - `suspend fun buildChapterUrl(versionId, bookId, chapter_1): String?` — expands the catalog's `chapterUrlTemplate` against `${SERVER_HOST}` (the backend issues a 302 to the real CDN).
    - `suspend fun fetchTiming(versionId, bookId, chapter_1): ChapterTiming?` — `GET ${SERVER_HOST}/audio/timing?...` via `Connections.okHttp`; parses the v1 JSON schema (see backend plan §4); returns `null` on network/parse failure, an empty `verses` list when the backend signals "no timing for this chapter". OkHttp's 50MB disk cache supplies implicit per-URL caching via the `Cache-Control` headers set by the backend.
    - **No SABDA URLs** in the client. All book-code / filename / SABDA-folder construction lives on the backend (`audio/adapters.py`). Verify with `grep -r "sabda" Alkitab/src/main` after the PR is up: zero hits expected.
- [ ] `HighlightTracker.kt` — a small class that owns a `StateFlow<Int>` of currently-active `verse_1`. Input: `positionMs` + timing list. Internal: a last-index hint so we don't re-binary-search every tick. 100ms poll interval (same as PR #127).
- [ ] `BibleAudioService.kt` — `androidx.media3.session.MediaSessionService`:
    - Owns a `BibleAudioPlayer`.
    - Wraps it with `MediaSession` so the OS gets transport metadata.
    - Exposes a `BibleAudioController` binder that the UI uses via coroutines.
    - State exposed as `StateFlow<PlaybackState>` where `PlaybackState` contains `isPlaying`, `preparing`, `verse_1`, `positionMs`, `durationMs`, `speed`, `error`.
    - Posts a `MediaStyle` notification on foreground transition (channel `audio_bible`).
    - Handles audio focus: pause on transient-loss, duck on can-duck, resume on regain.
- [ ] Register the service in `AndroidManifest.xml`:
    ```xml
    <service
        android:name=".audio.BibleAudioService"
        android:foregroundServiceType="mediaPlayback"
        android:exported="true">
      <intent-filter>
        <action android:name="androidx.media3.session.MediaSessionService"/>
      </intent-filter>
    </service>
    ```
    Also add `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permissions.
- [ ] Notification channel setup in `App.staticInit()` (mirror the existing channel creation for sync/devotion notifications).
- [ ] Unit tests: `HighlightTrackerTest`, `BibleAudioRepositoryTest` (mock OkHttp), `ChapterTimingParsingTest`.

**Exit criteria:** A throwaway button in a debug-only screen starts the service, plays a chapter, updates a text view with the active verse. No UI polish yet.

### M3 — UI surface (Compose, ≈ 4 days)

**Goal:** the feature is wired into `IsiActivity` and matches the mock in PRD §4.2 — peek bottom-sheet that swipes up into a full mini-player. Implemented entirely in Compose 1.11.0; this is the project's first Compose surface.

#### 3.1 Compose host

- [ ] Add a `<androidx.compose.ui.platform.ComposeView android:id="@+id/audio_bottom_sheet" />` to `activity_isi.xml` at the bottom of the root layout (`android:layout_gravity="bottom"`). The sheet handles its own peek/expand behavior; no XML siblings move.
- [ ] `AudioBarController.kt` (in `audio/`) — Kotlin glue between the View-based `IsiActivity` and the Compose UI:
    - Owns a `MutableStateFlow<AudioBarUiState>`.
    - `attach(activity: IsiActivity, composeView: ComposeView)` — `composeView.setContent { AudioBottomSheet(state, onCommand) }`.
    - Exposes `fun show()` / `fun hide()` / `fun isAvailable: Boolean` for `IsiActivity` to call.
    - Forwards play/pause/seek/speed/chapter-nav commands to the `BibleAudioService` (M2) via the binder.
    - Forwards chapter-navigation events back to `IsiActivity.display(book, chapter_1, 0)`.

#### 3.2 Composable surface

- [ ] `audio/ui/AudioTheme.kt` — wraps Compose `MaterialTheme` and bridges the project's existing `?attr/colorSurface*` etc. into Compose `ColorScheme` so dark mode works without a separate Compose theme. Uses `MaterialTheme.colorScheme.surfaceContainerHigh` for the sheet background.
- [ ] `audio/ui/AudioBottomSheet.kt` — top-level `@Composable`:
    - `BottomSheetScaffold` from `androidx.compose.material3` with `sheetPeekHeight = 96.dp`, `sheetSwipeEnabled = true`, `sheetDragHandle = { BottomSheetDefaults.DragHandle() }`, `sheetContainerColor = colorScheme.surfaceContainerHigh`, `sheetTonalElevation = 6.dp`, `sheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)`.
    - `sheetContent = { AudioBarExpanded(...) }` (the full mini-player).
    - The peek row (`AudioBarPeek`) is the part of `sheetContent` rendered above the fold within `sheetPeekHeight`.
- [ ] `audio/ui/AudioBarPeek.kt` — peek row matching PRD §4.2.1:
    - Row: prev-chapter (icon + label) | prev-verse | play/pause FAB | next-verse | next-chapter (icon + label) | speed | close.
    - Slider below the row.
    - Slider uses Material 3 `Slider` with a custom `SliderState` and a label rendered above the thumb: `"${formatMmSs(snappedMs)} · v.${verse_1}"`. Snap-to-verse on `onValueChangeFinished`.
    - Play/pause button uses `AnimatedContent` to crossfade between `Icons.Filled.PlayArrow` and `Icons.Filled.Pause` — Compose's idiomatic equivalent of the AVD morph.
    - On preparing state: render a `CircularProgressIndicator` overlay around the play/pause button.
    - Chapter-nav button labels (`Jn 4`) drawn with `Modifier.alpha(if (target != null) 1f else 0f)` — invisible-not-gone, layout doesn't reflow at Bible boundaries.
    - Haptics: `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.LongPress)` on speed change, `HapticFeedbackType.TextHandleMove` on play/pause.
- [ ] `audio/ui/AudioBarExpanded.kt` — expanded state matching PRD §4.2.2:
    - Big chapter art (square, 240dp) — for v1, the app icon over a tinted background.
    - Title row (`John 3 · KJV`) in `MaterialTheme.typography.headlineSmall`.
    - Same scrubber, larger transport row, speed + verse counter chips.
    - **"Up next" `LazyColumn`** of upcoming verses' first 80 chars; tap to seek.
- [ ] `audio/ui/SpeedBottomSheet.kt` — `ModalBottomSheet` with a `FilterChip` row for `0.5×, 0.8×, 1.0×, 1.25×, 1.5×, 1.75×, 2.0×`. Single-selection. Persisted via `Prefkey.audioPlaybackSpeed`.
- [ ] `audio/ui/AudioHighlightColor.kt` — pure-function logic:
    ```kotlin
    fun pickHighlightColor(readingBackground: Int, verseTextColor: Int): Int {
        val yellow = Color.argb(0x33, 0xFF, 0xEB, 0x3B)  // 20% alpha, Material Yellow 500
        val onBackground = ColorUtils.compositeColors(yellow, readingBackground)
        val contrast = ColorUtils.calculateContrast(verseTextColor, onBackground)
        if (contrast >= 4.5) return yellow
        // Fallback: pick black or white at 20% alpha, whichever scores better on the reading background
        val blackOverlay = Color.argb(0x33, 0, 0, 0)
        val whiteOverlay = Color.argb(0x33, 0xFF, 0xFF, 0xFF)
        val blackContrast = ColorUtils.calculateContrast(verseTextColor, ColorUtils.compositeColors(blackOverlay, readingBackground))
        val whiteContrast = ColorUtils.calculateContrast(verseTextColor, ColorUtils.compositeColors(whiteOverlay, readingBackground))
        return if (blackContrast >= whiteContrast) blackOverlay else whiteOverlay
    }
    ```
    Unit-tested.

#### 3.3 Verse highlight (still XML / View-based, since `VerseItem` is)

- [ ] `VerseItem.kt`: add `var audioHighlightColor: Int = 0` (0 = no highlight). The `onDraw` overlay paints a rounded rect of that color, alpha-animated in (200ms) and out (150ms) via a `ValueAnimator`. `0` clears.
- [ ] `VersesController.kt` / `VersesControllerImpl.kt`: add `fun setAudioHighlight(verse_1: Int, color: Int)`. The color is computed once via `pickHighlightColor(...)` using `VersesView.background` and `?attr/textColor*` and cached on the controller until the theme changes.
- [ ] On each `PlaybackState.verse_1` change, the controller calls `setAudioHighlight` on the active split's `VersesController` **and** smooth-scrolls the highlighted verse into the upper-third of the viewport using a `LinearSmoothScroller` with `getVerticalSnapPreference() = SNAP_TO_START` and `calculateDtToFit` returning `viewportHeight * 0.33`.

#### 3.4 Toolbar icon

- [ ] Menu item in `res/menu/activity_isi.xml`: `<item android:id="@+id/menuAudio" app:showAsAction="always" android:icon="@drawable/ic_audio" android:title="@string/menu_audio" />`. Matches `menuSearch`'s `always` treatment — never spills into overflow. Wire into `IsiActivity.buildMenu` / `onOptionsItemSelected` (see `IsiActivity.kt:1368-1399`).
- [ ] Visibility — observe the catalog. Set `menuItem.isVisible = repo.isAudioAvailable(visibleVersionId0) || repo.isAudioAvailable(visibleVersionId1)`. Refresh on active-version change and on split-view enter/exit. Hiding (not disabling) is intentional — a permanently-greyed icon is more confusing than no icon.
- [ ] Preparing-state spinner: clone the Kidung pattern (`SongViewActivity.kt:178, 443-449, 1088-1090`). Add a `circular_progress` view to the toolbar layout in `activity_isi.xml`, initially `GONE`. In `onPrepareOptionsMenu`, when `audioBarController.isPreparing`, set `menuAudio.isVisible = false` and `circular_progress.visibility = VISIBLE`; otherwise the inverse. Trigger `invalidateOptionsMenu()` from the state collector whenever `preparing` flips.

#### 3.5 Tests

- [ ] `AudioHighlightColorTest` — pure-function unit tests covering yellow/black/white selection across light, sepia, and dark reading backgrounds. Assert ≥4.5 contrast.
- [ ] Compose preview functions for `AudioBarPeek`, `AudioBarExpanded`, `SpeedBottomSheet` — for visual review in Android Studio. (Previews don't replace device testing but are cheap insurance.)
- [ ] Instrumented test (`connectedCheck`): open chapter → tap audio → verify the bottom sheet appears in peek state → swipe up via Compose `performTouchInput` → assert the expanded state is rendered.

**Exit criteria:** End-to-end demo of §1–§6 of the PRD's must-haves (screen-on usage); the bottom sheet swipes smoothly between peek and expanded; verse highlight uses yellow on light themes and the contrast-fallback color on dark themes.

### M4 — Lock-screen & background (≈ 2 days)

**Goal:** closing the app or locking the screen does not stop playback.

- [ ] Verify MediaSession metadata: title = `${book.shortName} ${chapter_1}`, subtitle = `${version.shortName}`, artwork = app icon + chapter art if available (for v1, app icon only).
- [ ] Pre-compute available actions on each state change: Play ↔ Pause, Prev-chapter, Next-chapter. (Verse-level actions are exposed only through the app UI to keep the notification small.)
- [ ] Handle `ACTION_MEDIA_BUTTON` — MediaSession does it automatically, but verify with a Bluetooth headset.
- [ ] Swipe-from-recents (`onTaskRemoved`): if audio is currently playing, keep the service alive and the notification up — matches Spotify / YouTube Music convention (swiping recents is a task switcher, not a stop button). If audio is paused, call `stopSelf()` to release the service. The user can always stop explicitly via the notification's Stop action or the in-app audio bar's Close button.
- [ ] Audio focus: `AudioFocusRequest` with `AUDIOFOCUS_GAIN`; pause/duck/resume on loss. media3 handles this by default with `setHandleAudioBecomingNoisy(true)` — enable it.
- [ ] Make sure the service correctly ends foreground state on stop (calls `stopForeground(STOP_FOREGROUND_REMOVE)`).
- [ ] Manual test matrix:
    - [ ] Lock screen → play/pause works.
    - [ ] Incoming call → ducks/pauses and resumes.
    - [ ] Bluetooth headset connect/disconnect.
    - [ ] Notification swipe dismiss → playback stops.
    - [ ] Back to launcher → keeps playing.

**Exit criteria:** Run the manual matrix on API 29, 33, 36.

### M5 — Behavior polish & design review (≈ 2 days)

The visual scaffolding (Compose `BottomSheetScaffold`, Material 3 theming, `AnimatedContent` icon crossfades, smooth verse-scroll, `LocalHapticFeedback`) is already baked into M3. M5 is the behavior polish:

- [ ] Speed persistence: `Prefkey.audioPlaybackSpeed` (enum default `1.0f`). Read on service start, write on each change.
- [ ] Auto-advance: on `onEnded`, navigate to the next chapter. Centralise the cross-book logic in a new `BibleNavigationUtil.kt` (it's useful outside audio too). No repeat toggle in v1.
- [ ] Snackbar error handling (§4.6 of PRD). Snackbars come from `IsiActivity` (host), not the Compose layer, since they need to overlay the toolbar.
- [ ] Split-view source dialog (§4.5 of PRD). On play-tap when split view is active and both visible versions have audio, render a Compose `AlertDialog` with the two version short names and a Cancel. The state is held in `AudioBarController` via `MutableStateFlow`; reset to `null` whenever split view toggles, either visible version changes, or the audio bar is closed.
- [ ] **Design review pass.** Walk the bottom sheet against this checklist before tagging M5 done:
    - Peek-to-expanded swipe is smooth on a low-end device (Pixel 4a / API 30 emulator OK).
    - Highlight overlay fades, doesn't strobe, at 1.0× speed.
    - Play/pause icon crossfades (no swap).
    - Toolbar icon crossfades into the spinner (no swap).
    - Auto-scroll feels like "the page is following me," not jumping.
    - Dark mode renders without any baked-in light-theme color leaking through (compare side-by-side against `IsiActivity` reading mode); yellow highlight falls back to the contrast-corrected color when needed.
    - Up-Next list scrolls in tandem with playback (active row stays visible).
    - Haptics fire on play/pause toggle and speed change.
    - All text is in `strings.xml` and translates correctly (test with `in` locale).

**Exit criteria:** PRD §2 must-have + should-have items are all testable on a device, and the design-review checklist passes one full sweep on at least one physical device in both light and dark modes.

### M6 — Pre-download (v2, deferred)

Out of scope for the first merge. Confirmed design for when we pick it up:

- **Audio:** one MP3 per chapter, stored at `files/audio/<versionId>/<bookId>/<chapter>.mp3`, downloaded via the existing `PRDownloader`. `BibleAudioRepository.buildChapterUrl` prefers the local `file://` path when present and falls back to the HTTPS URL otherwise.
- **Timing:** kept as per-chapter JSON, stored next to the MP3 (`files/audio/<versionId>/<bookId>/<chapter>.timing.json`). No binary packing; ~1 MB for a full Bible's worth is not worth engineering away.
- **UI:** a per-book "Download for offline" action in the version-details dialog (alongside the existing version download). Progress and cancel UI mirror the existing version-download pattern in `VersionListFragment`.
- **Storage management:** a "Downloaded audio" screen under Settings with per-version size and a Delete action; integrates with the existing space reporting in `files/`.

---

## 2. Integration points

### 2.1 IsiActivity changes

`IsiActivity.kt` is already ~2900 lines; the PRD goal of "thin glue" means the audio logic should add well under 100 lines. Concretely:

- One new field: `private val audioBinder: AudioBarBinder by lazy { AudioBarBinder(this, findViewById(R.id.audio_bar_host)) }`.
- In `buildMenu`: toggle `R.id.menuAudio` visibility based on `audioBinder.isAvailable`.
- In `onOptionsItemSelected`: `R.id.menuAudio -> audioBinder.toggle(); true`.
- In `onDestroy`: `audioBinder.detach()` — does **not** stop the service; the service is independently managed.
- A host `<include layout="@layout/audio_bar" android:id="@+id/audio_bar" />` at the bottom of `activity_isi.xml`.
- When `audioBinder` receives a `ChapterNavigation(nextChapter)` event, call the existing `display(book, chapter_1, 0)`.

### 2.2 VersesController / VerseItem changes

- `VerseItem.kt`: add the `audioHighlighted` var and the `onDraw` overlay. Same diff as PR #127 but use `?attr/colorPrimaryContainer` alpha 0.2 instead of `#334FC3F7`. Keep the overlay below the selection overlay (draw it first).
- `VersesController.kt`: add `fun setAudioHighlight(verse_1: Int)`.
- `VersesControllerImpl.kt`: implement `setAudioHighlight`:
    1. Find the current row (by `verse_1` → `itemPointer`, same helper as `callAttentionForVerse`).
    2. Toggle that row's `VerseItem.audioHighlighted = true` and any previous one to false.
    3. No-op if the row isn't currently bound — the adapter will pick up the state on the next bind because we store the active verse in the UI model.

### 2.3 Prefkey additions

Add to `Prefkey.kt`:
```kotlin
audioCatalog_etag,
audioPlaybackSpeed,
```

### 2.4 Proprietary assets

No change expected — SABDA audio is available for all flavors. If we later add a flavor-specific catalog (e.g. a flavor that ships without audio for licensing reasons), we can resolve it at the backend level by keying the catalog on `applicationId`. The catalog request already carries `App.getAppIdentifierParamsEncoded()`.

## 3. Testing strategy

### Unit tests (JUnit + Robolectric where needed)

- `AudioCatalogRepositoryTest` — ETag, bundled fallback, JSON parse.
- `ChapterTimingParsingTest` — schema v1, missing/out-of-range verses.
- `HighlightTrackerTest` — monotonic progress, rewind, seek across verses.
- `NextChapterNavigatorTest` — cross-book, end-of-Bible boundary.

### Instrumented tests (androidTest)

- Service start/stop lifecycle.
- MediaSession transport controls.
- Menu visibility toggles when switching active version.

### Manual test checklist

See M4's matrix. Add:

- [ ] Rotate device during playback: no re-buffer, highlight persists.
- [ ] Enter/exit split view during playback: audio continues on chosen source.
- [ ] Switch active version during playback: audio stops and icon updates.
- [ ] Tap verse-next at end of chapter: advances to next chapter.

## 4. Risks

| Risk | Mitigation |
|---|---|
| Backend endpoints not ready by M3 | Bundled `assets/audio_catalog.json` mirrors the SABDA versions, letting the client ship independently |
| media3-session API churn (still pre-1.0 in some releases) | Pin version; wrap in our own interface |
| Foreground-service restrictions on API 34+ | Declare `foregroundServiceType="mediaPlayback"` and grant permission; test on API 34/35/36 |
| ANR on timing fetch blocking first-play | Timing fetch is async; first-play kicks off audio immediately, highlight activates when timing arrives |
| Accessibility regressions from VerseItem drawing change | Covered by existing VerseItem TalkBack tests; new overlay is behind an alpha state and does not affect `contentDescription` |

## 5. Work that's explicitly NOT part of this plan

- Song module changes — keep `ExoplayerController` exactly as is.
- Changes to the YES2 format or `Version` interface.
- Adding audio to versions that don't have SABDA coverage.
- Reading-plan audio ("play today's passage") — deferred to v2.

## 6. Review checklist before merge

- [ ] No new hard-coded sabda.org URLs anywhere in the client tree (`grep -r "sabda" Alkitab/src/main | grep -v docs`).
- [ ] No new singletons holding an ExoPlayer (all go through the service).
- [ ] No UI-thread network calls.
- [ ] All new strings in `strings.xml`, none inline.
- [ ] `./gradlew testPlainDebugUnitTest testPlainReleaseUnitTest assemblePlainDebug` all green.
- [ ] Manual test matrix from M4 run on at least two API levels.
- [ ] Privacy/analytics review against PRD §6.
