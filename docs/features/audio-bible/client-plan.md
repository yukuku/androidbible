# Client Implementation Plan — Bible Audio Playback

**Repo:** `yukuku/androidbible`
**Target branch:** cut a fresh feature branch from `develop` (e.g. `feature/audio-bible`); close #127 and #124 afterwards.
**Estimated effort:** ~3 sprint-weeks for one engineer (v1 must-haves). Pre-download (v2) is another week.

This plan assumes the [PRD](prd.md) is approved and the [backend plan](backend-plan.md) lands (at least catalog + timing endpoints) before milestone M3.

---

## 0. Prerequisites

- [ ] Read the PRD in `prd.md` and the backend plan in `backend-plan.md`.
- [ ] Skim PR #127 — it is the template for the player/repository layer and most of its code carries over.
- [ ] Confirm `androidx.media3:media3-session` is not yet in the build; add it in M1.
- [ ] Access to the backend staging environment (base URL + simple-token, same as sync).

---

## 1. Milestones

### M1 — Skeleton & catalog (≈ 2 days)

**Goal:** app builds with a stubbed AudioBar; catalog loads from backend.

- [ ] Add `androidx.media3:media3-session:<same-version-as-exoplayer>` to `Alkitab/build.gradle` (match the existing media3 version used by `ExoplayerController.kt:8-17`).
- [ ] Create package `yuku.alkitab.base.audio` and skeleton files per PRD §5.7.
- [ ] Model classes: `AudioVersion.kt`, `ChapterTiming.kt`, `VerseTiming.kt`, `AudioCatalog.kt`.
- [ ] `AudioCatalogRepository`:
    - Uses `Connections.okHttp` and `BuildConfig.SERVER_HOST`.
    - `GET /audio/catalog?appIdentifier=...` with conditional `If-None-Match` when we have an ETag.
    - 200 → parse JSON, persist to `files/audio_catalog.json`, store ETag in `Prefkey.audioCatalog_etag`.
    - 304 → keep current cache.
    - Non-2xx → fall back to bundled `assets/audio_catalog.json` (checked in with the known SABDA versions, so the feature degrades to the #127 behavior if backend is down).
    - `suspend fun loadCatalog(): AudioCatalog` returns the cached+merged view.
    - `fun isAudioAvailable(versionId: String): Boolean` for the toolbar icon visibility.
- [ ] Add a Prefkey entry `audioCatalog_etag` in `Prefkey.kt`.
- [ ] Background refresh: piggyback on the existing `VersionConfigUpdaterService` (see `docs/backend-communication.md:39-41`) — add a parallel catalog fetch so we don't spawn a new worker.
- [ ] Unit tests (`Alkitab/src/test/java/.../audio/AudioCatalogRepositoryTest.kt`): parsing, ETag handling, cache fallback.

**Exit criteria:** `./gradlew testPlainDebugUnitTest` green; the repository returns a populated catalog on device against staging.

### M2 — Player + repository (≈ 3 days)

**Goal:** single-stream audio plays end-to-end for the active chapter; verses highlight and scroll.

- [ ] `BibleAudioPlayer.kt` — port from PR #127 verbatim (the file is already clean). Single-responsibility wrapper around ExoPlayer with a `Listener` (`onReady`, `onEnded`, `onError`). Main-thread-only API.
- [ ] `BibleAudioRepository.kt` — replaces the sabda-hard-coded repository from PR #127:
    - `suspend fun buildChapterUrl(versionId, bookId, chapter_1): String?` — expands the catalog's `chapterUrlTemplate`.
    - `suspend fun fetchTiming(versionId, bookId, chapter_1): ChapterTiming?` — GET via `Connections.okHttp`; parses new JSON schema (see backend plan §4); returns `null` on network/parse failure. OkHttp's 50MB disk cache supplies implicit per-URL caching via `Cache-Control` headers set by the backend.
    - All book-code / filename construction moves to the backend.
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

### M3 — UI surface (≈ 3 days)

**Goal:** the feature is wired into `IsiActivity` and looks like the mock in PRD §4.2.

- [ ] `layout/audio_bar.xml` — the bar from PRD §4.2 (close → scrubber → controls). Include a `SeekBar` and `TextView` for time labels. Use Material 3 theming attributes so it respects dark mode (`?attr/colorSurface`, etc.) — PR #127's hard-coded `#455A64` and `#FFFFFF` do not.
- [ ] `drawable/ic_audio*.xml` — carry over the SVG assets from PR #127 (they are generic Material icons).
- [ ] `AudioBarView.kt` — a `LinearLayout` subclass that inflates `audio_bar.xml` and binds to a `AudioBarViewModel`.
- [ ] `AudioBarViewModel.kt`:
    - Collects the service's `StateFlow<PlaybackState>` (exposed through the binder).
    - Exposes `LiveData` or `StateFlow` for the view.
    - Commands: `togglePlayPause()`, `seekTo(ms)`, `seekToVerse(verse_1)`, `nextVerse()`, `prevVerse()`, `nextChapter()`, `prevChapter()`, `setSpeed(f)`.
    - Scrubber preview: exposes `fun previewAtPosition(ms: Long): ScrubPreview` returning `{ snappedMs, verse_1 }` derived from the current chapter's timing, so the view can update the drag bubble without touching the player.
    - Navigates by firing an event flow; `IsiActivity` observes and calls its existing chapter-navigation methods.
- [ ] Menu item: edit `res/menu/activity_isi.xml` to add `<item android:id="@+id/menuAudio" app:showAsAction="always" android:icon="@drawable/ic_audio" android:title="@string/menu_audio" />` — matches the existing `menuSearch` entry's `always` treatment so it never spills into overflow. Wire in `IsiActivity.buildMenu`/`onOptionsItemSelected` (see `IsiActivity.kt:1368-1399`).
- [ ] Toolbar icon visibility — observe the catalog. Set `menuItem.isVisible = repo.isAudioAvailable(visibleVersionId0) || repo.isAudioAvailable(visibleVersionId1)`. Refresh on active-version change and on split-view enter/exit. Hiding (not disabling) is intentional — a permanently-greyed icon is more confusing than no icon.
- [ ] Verse highlight:
    - Add `var audioHighlighted: Boolean` on `VerseItem.kt` (PR #127's diff transfers directly). Uses `?attr/colorPrimaryContainer` at 20% alpha, not the hard-coded `#334FC3F7`.
    - Add `fun setAudioHighlight(verse_1: Int)` on `VersesController` / `VersesControllerImpl`. `verse_1 = 0` clears.
    - On each `PlaybackState.verse_1` change, call `setAudioHighlight` on the active split's controller and `scrollToVerse(verse_1, prop = 0.33f)` (using existing `scrollToVerse(verse_1, prop)` from `VersesController.kt:86`).
- [ ] Close button hides the bar and stops the service.
- [ ] Accessibility: all buttons have `contentDescription`; the scrubber announces "verse X of Y"; the "currently playing" highlight adds `contentDescription` suffix.
- [ ] Instrumented test (`connectedCheck`) for: open chapter → tap audio → verify service starts → tap pause → verify state flows back.

**Exit criteria:** End-to-end demo of §1–§6 of the PRD's must-haves (screen-on usage).

### M4 — Lock-screen & background (≈ 2 days)

**Goal:** closing the app or locking the screen does not stop playback.

- [ ] Verify MediaSession metadata: title = `${book.shortName} ${chapter_1}`, subtitle = `${version.shortName}`, artwork = app icon + chapter art if available (for v1, app icon only).
- [ ] Pre-compute available actions on each state change: Play ↔ Pause, Prev-chapter, Next-chapter. (Verse-level actions are exposed only through the app UI to keep the notification small.)
- [ ] Handle `ACTION_MEDIA_BUTTON` — MediaSession does it automatically, but verify with a Bluetooth headset.
- [ ] Stop-on-swipe: `onTaskRemoved` → pause (don't kill the service unless the user explicitly closes). Configurable later.
- [ ] Audio focus: `AudioFocusRequest` with `AUDIOFOCUS_GAIN`; pause/duck/resume on loss. media3 handles this by default with `setHandleAudioBecomingNoisy(true)` — enable it.
- [ ] Make sure the service correctly ends foreground state on stop (calls `stopForeground(STOP_FOREGROUND_REMOVE)`).
- [ ] Manual test matrix:
    - [ ] Lock screen → play/pause works.
    - [ ] Incoming call → ducks/pauses and resumes.
    - [ ] Bluetooth headset connect/disconnect.
    - [ ] Notification swipe dismiss → playback stops.
    - [ ] Back to launcher → keeps playing.

**Exit criteria:** Run the manual matrix on API 29, 33, 36.

### M5 — Polish (≈ 2 days)

- [ ] Speed persistence: `Prefkey.audioPlaybackSpeed` (enum default `1.0f`). Read on service start, write on each change.
- [ ] Auto-advance: on `onEnded`, navigate to the next chapter (using the same cross-book logic from PR #124's `getNextOrPreviousChapter`, but centralised — it's useful outside audio too). No repeat toggle in v1.
- [ ] Scrubber drag-bubble: custom view above the `SeekBar` thumb. On `SeekBar.OnSeekBarChangeListener.onProgressChanged(fromUser=true)`, call `viewModel.previewAtPosition(progressMs)` and render `"${formatMmSs(preview.snappedMs)} · v.${preview.verse_1}"`. On `onStopTrackingTouch`, seek to `preview.snappedMs`. Hide the bubble when not dragging.
- [ ] Snackbar error handling (§4.6 of PRD).
- [ ] "Mark progress after chapter" preference + checkbox in Settings' reading section.
- [ ] Split-view source picker (§4.5 of PRD, §5.6 of PRD). Persist choice in a `savedStateRegistry` so config-change doesn't lose it.
- [ ] Analytics events (`App.trackEvent` or whatever the existing wrapper is):
    - `audio_play` (versionId, bookId, chapter)
    - `audio_complete_chapter` (versionId, bookId, chapter, listenedRatio)
    - `audio_error` (versionId, bookId, chapter, errorCode)
    - `audio_catalog_fetch_error`

**Exit criteria:** PRD §2 must-have + should-have items are all testable on a device.

### M6 — Pre-download (v2, deferred)

Out of scope for the first merge. Design note only: the `chapterUrlTemplate` from the catalog is stable for a given version, so PRDownloader can materialise a book's worth of MP3s into `files/audio/<versionId>/<bookId>/` and the repository can prefer the file:// URL when present. Timing JSON is small (~1 KB/chapter) so the whole Bible's worth is ~1 MB and can be downloaded as a single blob.

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
audioMarkProgressOnEnd,
audioSplitSource,
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
