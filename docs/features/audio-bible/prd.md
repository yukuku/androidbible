# PRD: Bible Audio Playback with Verse Highlighting

**Status:** Draft v1 (2026-04)
**Owners:** yukuku (client), backend owner TBD (`yukuku/alkitab-host`)
**Related PRs:** [#127](https://github.com/yukuku/androidbible/pull/127) (yukuku), [#124](https://github.com/yukuku/androidbible/pull/124) (elpafras) — both open, both implementations of this feature.

---

## 1. Problem

Users can already read any chapter in the app but cannot *listen* to it. SABDA runs a free Bible-audio service (media.sabda.org) with aligned verse timing (karaoke.sabda.org), and both existing PRs prove the data is usable — but the two implementations disagree on architecture and each has gaps (hard-coded URLs, activity-scoped players, no lock-screen controls, unclear split-view behavior). This PRD defines one coherent feature, with the integration points that will make it sustainable.

## 2. Goals

**Must have (v1):**

1. User can play/pause audio for the chapter they are reading.
2. The currently-playing verse is visually highlighted and auto-scrolled into view.
3. Skip to previous/next verse within the chapter.
4. Skip to previous/next chapter (with auto-advance at end of chapter when enabled).
5. Audio keeps playing when the screen is locked or the app is backgrounded (foreground service + lock-screen controls).
6. Works on at least the four SABDA-supported versions today: Indonesian TB, AYT, AVB, and KJV.
7. Backend endpoints mediate the relationship to sabda.org — the client does not hard-code sabda URLs.

**Should have (v1.1):**

8. Adjustable playback speed (0.5× – 2×), persisted across chapters and sessions.
9. A scrub bar with elapsed / total time **and a live verse-preview while dragging** (see §4.2).
10. Graceful offline message + retry.

**Nice to have (v2):**

13. Pre-download a book or plan range for offline listening (uses the existing PRDownloader infrastructure).
14. Android Auto / Wear OS support (mostly free once we adopt MediaSession).
15. Chromecast route.
16. "Listen to today's reading plan" entry point from the reading-plan screen.

**Non-goals (v1):**

- Video playback.
- Audio editing, bookmarking a moment inside a verse.
- Multi-voice or dramatized audio catalogs (just the existing SABDA streams).
- Verse-by-verse interleaving of two versions in split view. (See §5.6 — we deliberately replace #127/#124's "dual mode" with a simpler behavior.)

## 3. Users & Scenarios

- **Commuter / driver** — opens the chapter, taps play, puts phone away. Expects lock-screen pause button and uninterrupted audio through a Bluetooth headset.
- **Visually-impaired reader** — uses the app with TalkBack and wants the audio to be the primary reading modality; the highlight helps when they hand the phone to a sighted helper.
- **Student in split view** — reading TB next to KJV. Wants to hear one of the two while following along in both; picks which side drives audio.
- **Reading-plan user** — opens today's passage and wants to listen while following the plan's progress tracker.
- **Low-bandwidth user** — is on 3G/wifi-limited data; should see a buffering state and be warned (not fail silently) if offline or if the version has no audio.

## 4. UX

### 4.1 Entry point

A new "Audio" icon in the `IsiActivity` toolbar (`activity_isi.xml`, `app:showAsAction="always"` — same treatment as the existing Search action, never pushed to overflow). The icon is shown **only when the currently-visible version has audio available** per the catalog (see §5.3); when the user switches to a version without audio the icon is hidden outright (via `menuItem.isVisible = false`), rather than being present-but-disabled. In split view, the icon appears if **either** visible version has audio; if both do, the split-source picker from §4.5 decides which one plays. Tapping the icon toggles the audio bar.

**Preparing state.** The moment the user taps Audio, the work that runs before the first byte of audio plays — catalog lookup (cache), timing fetch if not already cached, ExoPlayer `prepare()` + initial buffer — can take a perceptible fraction of a second on mobile networks. During this window the toolbar icon morphs into an indeterminate spinner, mirroring the Kidung (Songs) play button's behavior: while `MediaController.State == preparing`, `SongViewActivity` hides the play menu item and swaps in an indeterminate `circular_progress` view (`SongViewActivity.kt:178, 443-449, 1088-1090`). We use the exact same pattern — `onPrepareOptionsMenu` hides `R.id.menuAudio` and shows a sibling progress view anchored in the toolbar — so the UX is consistent with the rest of the app. The spinner reverts to the Audio icon when the service reports `ready` (or `error`, in which case the snackbar in §4.6 fires). Tapping during the preparing window is a no-op; a second tap does **not** cancel (that would require tearing down the service mid-prepare and feels unpredictable).

### 4.2 Audio bar (bottom sheet)

Anchored at the bottom of `IsiActivity`, above the reading-history panel. Height ≈ 72dp. Contents left-to-right:

```
┌──────────────────────────────────────────────────────────────────┐
│  ⏮ Jn 2   ⏮   ▶/⏸ (+progress ring)   ⏭   Jn 4 ⏭   1.0×   ╳       │
├──────────────────────────────────────────────────────────────────┤
│  ▓▓▓▓▓▓▓▓▓▓▓▓▒▒▒░░░░░░░░   0:42 / 3:15                           │
└──────────────────────────────────────────────────────────────────┘
```

- **Play/pause** — center; shows a spinner while preparing.
- **Prev / next verse** — plain icon-only buttons that seek to the start of the neighboring verse using timing data. No label on the button (the active verse is already conveyed by the highlighted row and the scrubber bubble; adding "v.7" to the button is redundant and the label would go blank/grey at chapter boundaries).
- **Prev / next chapter** — the button shows the target chapter next to its icon (e.g. `⏮ Jn 2` and `Jn 4 ⏭`), using the same `book.shortName` + chapter format that appears in the main toolbar. At cross-book boundaries the next button reads `⏮ Mt 28` from Mark 1, making jumps predictable. At the Bible boundaries (Genesis 1 prev, Revelation 22 next) the button is disabled and the label hidden.
- **Speed** — opens a popup: 0.5 / 0.8 / 1.0 / 1.25 / 1.5 / 1.75 / 2.0×. Persisted via Prefkey.
- **Close (╳)** — closes the bar; stops audio and clears highlight.
- **Scrubber** — drag-to-seek. While the user is dragging, the thumb shows a tooltip/bubble with **both** the proposed position `mm:ss` **and** the verse that would play on release (e.g. `1:23 · v.7`). The bubble updates live as the thumb moves so the user can aim at a verse they remember hearing, not just a time offset. On release, audio seeks to the start of that verse's `startMs` (snapping to verse boundary feels better than snapping to the raw scrubbed millisecond — and matches what the tooltip was showing). If timing data is missing, the bubble falls back to `mm:ss` only and seek is a plain time seek.

### 4.3 Verse highlight

A semi-transparent blue rectangle overlay on the active verse, drawn in `VerseItem.onDraw` (same pattern used for selection/attention). The `VersesController` exposes `setAudioHighlight(verse_1)` (already prototyped in PR #127). Auto-scroll keeps the highlighted verse in the upper third of the viewport.

### 4.4 Lock screen & notification

A foreground service posts a MediaStyle notification with Play/Pause, Prev-chapter, Next-chapter actions. Title = book + chapter; subtitle = version name; large icon = app icon. Notification channel `audio_bible` at `IMPORTANCE_LOW` (no sound).

### 4.5 Split view

When two versions are open in split view, audio plays from exactly one side.

- **Only one side has audio** → that side is the source automatically; no prompt.
- **Both sides have audio** → the first time the user taps play in this split-view session, a dialog appears: **"Play audio from which version?"** with the two version short names as options (e.g. `TB (top)` / `KJV (bottom)`) and a `Cancel` button. The user's choice is remembered for the rest of the session and reused for subsequent chapters. Switching the source later is done by closing the bar and tapping play again — the dialog reappears. (Or, when we build out the overflow menu in v1.1, via a "Change audio source" item there.)

Highlight applies only to the chosen side; the other split's rows show no audio highlight, even when the verse numbers coincide. Closing the audio bar, exiting split view, or changing one of the visible versions clears the remembered choice so the next play starts fresh.

This deliberately replaces PR #127's sequential interleaving (which plays verse 1 from version A, then verse 1 from version B, then verse 2 from version A, etc.). The reasoning is design-first, not evidence-based: (a) no major Bible-audio product behaves this way — YouVersion, Olive Tree, Dwell, Bible.is all play one stream and let the user switch sources; (b) the two SABDA recordings have different tempo and reader cadence, so sequential interleaving produces jarring silences and overlaps; (c) the feature forces extra state (which verse each stream has reached independently) and doubles the failure surface (two network requests, two decoders) for a behavior most users would never opt into. If someone later produces evidence that interleaving is desired — e.g. for language learners pairing L1+L2 — we can revisit, but the default should be simpler.

### 4.6 Error and empty states

- **Version has no audio:** toolbar icon is hidden.
- **Network failure on chapter load:** snackbar "Cannot load audio. Retry?" with a Retry action. Audio bar stays open, play button disabled.
- **Timing data missing:** audio plays, but verse-skip buttons and verse highlight are disabled (greyed). No error shown.
- **Audio URL 404 (e.g. deuterocanonical chapter):** snackbar "Audio not available for this chapter," and the bar auto-closes after 3s.

## 5. Architecture

### 5.1 High-level layers

```
 ┌────────────────────────────────────────────────┐
 │           IsiActivity (thin glue)              │
 └────────┬───────────────────────────────────────┘
          │   binds, observes StateFlow
 ┌────────▼───────────────────────────────────────┐
 │     AudioBarView + AudioBarViewModel           │   UI layer
 └────────┬───────────────────────────────────────┘
          │   commands (play/pause/seek/...), state flow
 ┌────────▼───────────────────────────────────────┐
 │  BibleAudioService (foreground + MediaSession) │   Process layer
 │  ├─ BibleAudioPlayer (ExoPlayer wrapper)       │
 │  ├─ HighlightTracker                           │
 │  └─ PlaybackNotification                       │
 └────────┬───────────────────────────────────────┘
          │   fetches audio URL + timing
 ┌────────▼───────────────────────────────────────┐
 │           BibleAudioRepository                  │   Data layer
 │  ├─ AudioCatalog (cached)                       │
 │  ├─ TimingCache (disk)                          │
 │  └─ BackendApi (Retrofit-shape over OkHttp)     │
 └────────┬───────────────────────────────────────┘
          │   HTTPS (api.alkitab.app)
 ┌────────▼───────────────────────────────────────┐
 │               alkitab-host backend              │   Backend
 │  /audio/catalog, /audio/chapter, /audio/timing  │
 └─────────────────────────────────────────────────┘
```

### 5.2 Why a foreground service, not an Activity-scoped player

Both existing PRs put ExoPlayer directly in `IsiActivity`, so audio dies on config change, process kill, and when the user navigates anywhere else. For anything longer than a few minutes — and a chapter of Psalms can be 10+ minutes — this is unacceptable. A `MediaSessionService` (media3's `MediaSessionService` or `MediaLibraryService`) gives us:

- Lock-screen transport controls "for free."
- Bluetooth / Android Auto / Wear OS compatibility.
- Correct audio focus handling (duck on notification, pause on call).
- Survival of activity recreation.

ExoPlayer's media3 library already includes all of this; the incremental complexity over #127's standalone ExoPlayer is small.

### 5.3 Backend-mediated audio catalog

Both PRs hard-code the list of supported versions and the sabda URL-building scheme inside the app. This creates two problems:

1. Adding a fifth supported version (e.g. ESV when SABDA adds it) requires an app release.
2. If sabda.org changes their folder scheme or CDN, every installed app breaks until users update.

**Decision:** the client asks the backend for a catalog describing which versions have audio and how to fetch it. The catalog is cached locally (ETag) and refreshed periodically (like `version_config.json` already is). Individual chapter URLs and timing JSON are served by the backend, which is free to proxy, redirect, or bake them into a CDN as needed.

This adds one layer of indirection but removes the brittle coupling.

### 5.4 Models

```kotlin
data class AudioCatalog(
    val etag: String,
    val entries: List<AudioVersion>,
)

data class AudioVersion(
    val versionId: String,            // matches MVersion.getVersionId(), e.g. "preset/in-tb"
    val displayLocaleHint: String?,   // for ordering when user has no active version
    val chapterUrlTemplate: String,   // e.g. "/audio/chapter/in-tb/{book}/{chapter}"
    val timingUrlTemplate: String?,   // nullable: some versions have audio but no timing
    val copyrightNotice: String?,
    val license: String?,             // e.g. "Public Domain", "SABDA"
)

data class VerseTiming(
    val verse_1: Int,     // 1-based
    val startMs: Long,
    val endMs: Long,
)

data class ChapterTiming(
    val versionId: String,
    val bookId: Int,
    val chapter_1: Int,
    val durationMs: Long,
    val verses: List<VerseTiming>,
    val generatedAt: Long,  // server timestamp; lets client cache per-chapter
)
```

`MAudio` from PR #124 (`audio1..audio5`) is rejected — the slot-numbered fields don't map onto anything meaningful. `MAudio` from PR #127 is close to this but pins the sabda folder name in the client; we keep the shape and push the folder-name responsibility to the backend.

### 5.5 Persistence

- `AudioCatalog` cached at `files/audio_catalog.json` with ETag stored in `Prefkey`.
- `ChapterTiming` cached via OkHttp's existing 50MB disk cache (`Connections.okHttp`), keyed on the normalized URL. No custom SQLite tables.
- Playback-speed preference in `Prefkey.audioPlaybackSpeed` (new enum entry).
- Last position (for "resume where you left off") is stored in-memory on the service only — not persisted across kills, since most users will re-open the chapter anyway.

### 5.6 Split-view behavior

When split view is active and the user taps Audio:

- If only one of the two versions has audio → that side becomes the source, no prompt.
- If both have audio → a one-time prompt asks "Audio from top or bottom?" and the choice is sticky for the session.
- Highlight is applied only to the chosen split. Switching source restarts audio from the current verse using the other split's timing.

This replaces the "dual-audio interleaved" mode from both PRs. Reason: neither PR's split-view behavior matches any real-world audio Bible product, and both produce playback that sounds like a glitch. If a future requirement is "listen in English while reading Indonesian," the correct answer is that all verses come from one stream and the other split's highlight follows the timing of the chosen stream by verse number.

### 5.7 Module placement

New Kotlin files live under `Alkitab/src/main/java/yuku/alkitab/base/audio/`:

```
audio/
├─ AudioCatalog.kt
├─ AudioCatalogRepository.kt
├─ BibleAudioRepository.kt
├─ BibleAudioPlayer.kt
├─ BibleAudioService.kt        ← MediaSessionService
├─ AudioBarView.kt / .xml
├─ AudioBarViewModel.kt
├─ HighlightTracker.kt
└─ model/
   ├─ AudioVersion.kt
   ├─ VerseTiming.kt
   └─ ChapterTiming.kt
```

Reuse: `Connections.okHttp` for HTTP, `BuildConfig.SERVER_HOST` for the base URL, existing `S`, `App`, `AppLog`. We deliberately do **not** reuse `yuku.alkitab.songs.ExoplayerController` (PR #124's approach) — the songs controller carries song-specific behavior (inline MaterialDialog error UI, loop-centric state machine, `MediaController.State` enum oriented at short clips) that pushes the bible-audio case into awkward shapes. We do borrow its pattern (OkHttp data source, MP3-only extractor factory) verbatim.

### 5.8 Library choices

- **media3 ExoPlayer** — already a dependency (`androidx.media3:media3-exoplayer`, via `ExoplayerController.kt:8-17`). Keep.
- **media3 session** — add `androidx.media3:media3-session` (`MediaSessionService`, notification provider).
- **Kotlin coroutines + Flow** — already a project convention.
- **OkHttp** — already used.

No new heavyweight dependencies.

## 6. Privacy, security, licensing

- Audio and timing data are served by `api.alkitab.app`, which may 302 to sabda.org or a CDN. The backend carries the licensing responsibility; the client displays the catalog's `copyrightNotice` and `license` strings in the audio bar's overflow menu ("About this audio").
- No new user data collected beyond existing analytics. Playback events are **not** logged off-device in v1.
- No microphone / no audio capture.

## 7. Success metrics

- **Activation**: % of monthly-active users who tap Play at least once / week.
- **Completion**: % of started chapters listened to ≥ 80%.
- **Lock-screen usage**: notification interaction rate (proxy for "did they use it backgrounded").
- **Errors**: chapter-load failure rate by version (exposes backend/CDN issues early).

Metrics funnel through the existing analytics surface (Firebase); exact event names listed in §3 of the client implementation plan.

## 8. Rollout

1. Merge the backend catalog/timing endpoints behind a feature flag first (§2 of backend plan).
2. Ship the client behind `BuildConfig.DEBUG` only for an internal build.
3. Beta-flag via remote config (`audio_bible_enabled` in the catalog response itself — if absent, the client hides the Audio toolbar icon).
4. Once green, remove the remote flag.

## 9. Open questions

- **Pre-download**: uses `PRDownloader`, but where do the files live — per-chapter MP3s in app storage, or a single large archive per book? Deferred to v2.
- **ReadAloud voices** for versions SABDA doesn't cover: explicitly out of scope for v1, but the catalog shape (`chapterUrlTemplate` per version) doesn't preclude it.
- **Offline timing format**: JSON is fine for now (~1 KB per chapter), but for a future pre-downloaded-book feature we may want to pack timings into a single YES2-like binary — revisit when v2 comes up.

## 10. Why not just merge PR #127?

Short answer: PR #127 is the better starting point — its separation of player/controller/repository, coroutine usage, and typed models are right. But three things need to change before it's production-ready:

| | PR #127 | This PRD |
|---|---|---|
| Player ownership | Activity | Foreground MediaSessionService |
| Version/URL catalog | Hard-coded in client | Served by backend |
| Split-view audio | Sequential interleaved | Single-source, user-selectable |
| Lock-screen controls | Absent | First-class |
| Error UX | Silent log | Snackbar + retry |

PR #124 is not the better starting point — its reuse of `ExoplayerController` couples two unrelated features, its URL construction bypasses `Connections.okHttp` (defeating the user-agent interceptor and HTTP cache), and it uses display-name keys that break under locale changes. We intend to close #124 with a note thanking the author.

## 11. References

- PR #127 implementation files: `Alkitab/src/main/java/yuku/alkitab/base/audio/*.kt`, [pull/127/head](https://github.com/yukuku/androidbible/pull/127/files).
- PR #124 implementation files: `Alkitab/src/main/java/yuku/alkitab/base/util/{Audio,Bible,Timing,MediaList}*`, [pull/124/head](https://github.com/yukuku/androidbible/pull/124/files).
- Existing song-audio design: `docs/modules/audio-playback.md`, `Alkitab/src/main/java/yuku/alkitab/songs/ExoplayerController.kt:33-194`.
- Verses/highlighting surface: `Alkitab/src/main/java/yuku/alkitab/base/verses/VersesController.kt`, `VerseItem.kt`.
- Backend conventions: `docs/backend-communication.md`, `docs/modules/sync.md`, `docs/modules/devotions.md`.
- Server host macro: `build.gradle:29`, `Alkitab/build.gradle:119`.
