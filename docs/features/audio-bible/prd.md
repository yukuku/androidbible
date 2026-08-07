# PRD: Bible Audio Playback with Verse Highlighting

**Status:** Draft v1 (2026-04)
**Owners:** yukuku (client), backend owner TBD
**Related PRs:** [#127](https://github.com/yukuku/androidbible/pull/127) (yukuku), [#124](https://github.com/yukuku/androidbible/pull/124) (elpafras) — both open, both implementations of this feature.

---

## 1. Problem

Users can already read any chapter in the app but cannot *listen* to it. SABDA runs a free Bible-audio service with aligned verse timing for some recordings, and both existing PRs prove the data is usable — but the two implementations disagree on architecture and each has gaps (hard-coded URLs, activity-scoped players, no lock-screen controls, unclear split-view behavior). This PRD defines one coherent feature, with the integration points that will make it sustainable.

## 2. Goals

**Must have (v1):**

1. User can play/pause audio for the chapter they are reading.
2. The currently-playing verse is visually highlighted and auto-scrolled into view.
3. Skip to previous/next verse within the chapter.
4. Skip to previous/next chapter via the system transport controls (lock screen, Bluetooth), with auto-advance at end of chapter.
5. Audio keeps playing when the screen is locked or the app is backgrounded (foreground service + lock-screen controls).
6. Works on at least the versions with audio today: Indonesian TB, AYT, BIMK, Malay AVB, and KJV. TB carries several recordings; see §5.3.
7. Backend endpoints mediate the relationship to the audio origin — the client does not hard-code upstream URLs.

**Should have (v1.1):**

8. Adjustable playback speed (0.5× – 2×), persisted across chapters and sessions.
9. A scrub bar with elapsed / total time **and a live verse-preview while dragging** (see §4.2).
10. Graceful offline message + retry.

**Nice to have (v2):**

13. Pre-download a book or plan range for offline listening — one MP3 per chapter in app-internal storage under `files/audio/<versionId>/<bookId>/<chapter>.mp3`, using the existing `PRDownloader` infrastructure. Timing stays as JSON on disk (one small file per chapter); no binary packing — a whole Bible of timing is ~1 MB and the simplicity is worth more than the savings.
14. Android Auto / Wear OS support (mostly free once we adopt MediaSession).
15. Chromecast route.
16. "Listen to today's reading plan" entry point from the reading-plan screen.

**Non-goals (v1):**

- Video playback.
- Audio editing, bookmarking a moment inside a verse.
- Dramatized or multi-voice productions (sound effects, cast recordings). A version may offer several *narrator* recordings — see §5.3 — but each is a plain single-voice reading.
- Verse-by-verse interleaving of two versions in split view. (See §5.6 — we deliberately replace #127/#124's "dual mode" with a simpler behavior.)

## 3. Users & Scenarios

- **Commuter / driver** — opens the chapter, taps play, puts phone away. Expects lock-screen pause button and uninterrupted audio through a Bluetooth headset.
- **Visually-impaired reader** — uses the app with TalkBack and wants the audio to be the primary reading modality; the highlight helps when they hand the phone to a sighted helper.
- **Student in split view** — reading TB next to KJV. Wants to hear one of the two while following along in both; picks which side drives audio.
- **Reading-plan user** — opens today's passage and wants to listen while following the plan's progress tracker.
- **Low-bandwidth user** — is on 3G/wifi-limited data; should see a buffering state and be warned (not fail silently) if offline or if the version has no audio.

## 4. UX

### 4.1 Entry point

A new "Audio" icon in the `IsiActivity` toolbar (`activity_isi.xml`, `app:showAsAction="always"` — same treatment as the existing Search action, never pushed to overflow). The icon is shown **only when the currently-visible version has audio covering the current book** (see §5.3); when the user switches to a version without audio, or opens a book the selected recording doesn't cover, the icon is hidden outright (via `menuItem.isVisible = false`), rather than being present-but-disabled. Book coverage is ragged — a recording may skip whole books — so a version-level check alone would leave a button that only 404s. In split view, the icon appears if **either** visible version has audio; if both do, the split-source picker from §4.5 decides which one plays. Tapping the icon toggles the audio bar.

Availability is resolved per version, so the first answer isn't available synchronously: `onPrepareOptionsMenu` reads a non-blocking cache peek, hides the icon on a miss, and kicks off the fetch, calling `invalidateOptionsMenu()` when the answer lands. Prefetching on version change means the icon is present on first composition in practice. A brief absence on a genuinely cold open beats a main-thread network call, and beats an icon that is present but dead.

**Preparing state.** The moment the user taps Audio, the work that runs before the first byte of audio plays — audio set lookup (cache), timing fetch if not already cached, ExoPlayer `prepare()` + initial buffer — can take a perceptible fraction of a second on mobile networks. The bar's play button is the single loading indicator: it swaps to a progress ring while the load is in flight, debounced by ~100 ms so a load that completes within the window (a verse skip landing in already-buffered data, a chapter served from the HTTP cache) never flashes the ring at all. The toolbar Audio icon stays static throughout — a second spinner in the activity chrome would only duplicate the play button's signal. The ring reverts to play/pause when the service reports `ready` (or `error`, in which case the play button shows the error state from §4.6). While a new file is loading, the position label and scrubber sit at 0:00 / leftmost and the duration label is blank — the duration is unknown until the player reports it, and a "0:00" there would read as a measurement. Tapping play during the preparing window is a no-op; a second tap does **not** cancel (that would require tearing down the service mid-prepare and feels unpredictable).

### 4.2 Audio bar — fixed-height Compose surface

Anchored at the bottom of `IsiActivity`, hosted in a `ComposeView`. Implemented in **Jetpack Compose 1.11.0** (the first piece of Compose in the codebase; we will progressively migrate `IsiActivity` to Compose, with the audio bar as the beachhead). The bar is a **fixed-height Material 3 surface** — no peek/expand mechanic, no drag handle, no swipe.

Height ≈ 96dp:

```
┌──────────────────────────────────────────────────────────────────┐
│  1.0×  Davar          ⏮  ▶/⏸ (+progress ring)  ⏭             ╳   │
│  ▓▓▓▓▓▓▓▓▓▓▓▓▒▒▒░░░░░░░░   0:42 / 3:15                           │
└──────────────────────────────────────────────────────────────────┘
```

- **Play/pause** — center; shows a progress ring around the FAB-style button while preparing, debounced ~100 ms so cache-fast loads don't flash it. On a failed load the button renders an error icon instead; tapping it retries (§4.6).
- **Prev / next verse** — plain icon-only buttons that seek to the start of the neighboring verse using timing data. No label on the button (the active verse is already conveyed by the highlighted row and the scrubber bubble).
- **Chapter navigation** — not in the bar. Auto-advance continues into the next chapter at end of playback, the system transport controls (§4.4) carry prev/next-chapter for deliberate jumps, and in-app the reader's own navigation moves the audio with it.
- **Speed** — taps open the speed bottom sheet (§4.2.2).
- **Audio set** — a compact button showing the current recording's title, next to the speed control, opening the set bottom sheet (§4.2.3). **Shown only when the sheet has a choice to offer** — several recordings of the version, or (split view) a second visible version with audio. The button occupies a flexible slot: a long title ellipsizes rather than squeezing the transport controls.
- **Close (╳)** — hides the bar; stops audio and clears highlight.
- **Scrubber** — Material 3 `Slider`. Drag-to-seek with a live preview label that shows `mm:ss · v.7` while dragging — both the proposed position and the verse that would play on release. On release, audio seeks to the start of that verse's `startMs` (snapping to verse boundary feels better than snapping to a raw millisecond, and matches what the tooltip is showing). If timing data is missing, the label falls back to `mm:ss` only and seek is plain.

#### 4.2.2 Speed bottom sheet

Tapping `1.0×` opens a small Compose `ModalBottomSheet` with a horizontal `FilterChip` row: `0.5×, 0.8×, 1.0×, 1.25×, 1.5×, 1.75×, 2.0×`. Single-selection. Persisted via `Prefkey.audioPlaybackSpeed`. The thumb-friendly bottom-sheet pattern matches what Spotify, YouTube Music, and Audible do for the same control.

#### 4.2.3 Audio set bottom sheet

Tapping the set button opens a `ModalBottomSheet` with a single-selection list of recording titles — same pattern and placement as the speed sheet.

The sheet lists one group per visible version with audio, headed by the version's short name (the header is omitted when there is a single group). In split view with audio on both sides, picking a row from the other version's group moves the audio source to that version as well as selecting the recording (§4.5).

Sets that don't cover the current book are **listed but disabled**, with the reason shown ("not available for this book"). Hiding them would make the list appear to change size as the user moves through the Bible.

Switching sets reloads the current chapter in the new recording, seeking to the start of the verse that was playing when both recordings have timing, and to the chapter start otherwise. Timing differs per recording, so preserving the raw millisecond would land somewhere arbitrary.

### 4.3 Verse highlight

A semi-transparent **yellow highlighter** overlay on the active verse, drawn in `VerseItem.onDraw` (same pattern used for selection/attention). The base color is the same hue family as a physical highlighter pen — concretely `#FFEB3B` (Material Yellow 500) at **20% alpha** by default.

Because the user's reading background can be customised (light, sepia, dark, custom theme), 20%-alpha yellow is not always legible. The drawing layer must therefore **adapt at runtime**:

- Compute the contrast ratio between the yellow overlay (composited onto the reading background) and the verse text color, using `androidx.core.graphics.ColorUtils.calculateContrast`.
- If contrast ≥ 4.5 (WCAG AA), use the yellow overlay.
- Otherwise (typically dark backgrounds), fall back to a neutral overlay at 20% alpha — `#000000` on light backgrounds, `#FFFFFF` on dark — choosing whichever produces the better text contrast.
- The decision is made once per call to `setAudioHighlight`, cached on the `VerseItem` via a small `audioHighlightColor: Int` field, and refreshed when the reading theme changes.

The fade-in / fade-out alpha animation (200ms in, 150ms out) prevents strobe at speed 1.0×. Auto-scroll uses a `LinearSmoothScroller` to keep the highlighted verse in the upper third of the viewport (PR #127's instant `scrollToVerse` jumps too aggressively).

The `VersesController` exposes `setAudioHighlight(verse_1)`. `verse_1 = 0` clears.

### 4.4 Lock screen & notification

A foreground service posts a MediaStyle notification with Play/Pause, Prev-chapter, Next-chapter actions. Title = book + chapter; subtitle = version name; large icon = app icon. Notification channel `audio_bible` at `IMPORTANCE_LOW` (no sound).

### 4.5 Split view

When two versions are open in split view, audio plays from exactly one side.

- **Only one side has audio** → that side is the source automatically; no prompt.
- **Both sides have audio** → the first time the user taps play in this split-view session, a dialog appears: **"Play audio from which version?"** with the two version short names as options (e.g. `TB (top)` / `KJV (bottom)`) and a `Cancel` button. The user's choice is remembered for the rest of the session and reused for subsequent chapters. Switching the source later is done from the recording sheet (§4.2.3), which lists both versions' recordings; closing the bar and tapping play again also re-prompts.

Highlight applies only to the chosen side; the other split's rows show no audio highlight, even when the verse numbers coincide. Closing the audio bar, exiting split view, or changing one of the visible versions clears the remembered choice so the next play starts fresh.

This deliberately replaces PR #127's sequential interleaving (which plays verse 1 from version A, then verse 1 from version B, then verse 2 from version A, etc.). The reasoning is design-first, not evidence-based: (a) no major Bible-audio product behaves this way — YouVersion, Olive Tree, Dwell, Bible.is all play one stream and let the user switch sources; (b) the two SABDA recordings have different tempo and reader cadence, so sequential interleaving produces jarring silences and overlaps; (c) the feature forces extra state (which verse each stream has reached independently) and doubles the failure surface (two network requests, two decoders) for a behavior most users would never opt into. If someone later produces evidence that interleaving is desired — e.g. for language learners pairing L1+L2 — we can revisit, but the default should be simpler.

### 4.6 Error and empty states

- **Version has no audio:** toolbar icon is hidden.
- **Selected recording doesn't cover the current book:** toolbar icon is hidden for that book. The selection is *not* silently switched to a recording that does cover it — an unannounced narrator change mid-book is worse than a temporarily absent button.
- **Network failure on chapter load:** the play button becomes an error icon; tapping it retries the load from scratch — including re-resolving the version's set list, so a failure cached as "no audio" during resolution isn't sticky. The audio bar stays open.
- **Timing data missing:** audio plays, but verse-skip buttons and verse highlight are disabled (greyed). No error shown. This is knowable up front from the set's `hasTiming`, so the controls render disabled from the start rather than going inert once an empty timing fetch returns.
- **Audio URL 404 (e.g. an upstream gap in coverage):** the same error state on the play button; the bar stays open so the user can retry or close it.

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
 │  ├─ AudioSets (cached per version)              │
 │  ├─ TimingCache (disk)                          │
 │  └─ BackendApi (Retrofit-shape over OkHttp)     │
 └────────┬───────────────────────────────────────┘
          │   HTTPS (api.alkitab.app)
 ┌────────▼───────────────────────────────────────┐
 │                    backend                      │   Backend
 │     /audio/sets, /audio/file, /audio/timing     │
 └─────────────────────────────────────────────────┘
```

### 5.2 Why a foreground service, not an Activity-scoped player

Both existing PRs put ExoPlayer directly in `IsiActivity`, so audio dies on config change, process kill, and when the user navigates anywhere else. For anything longer than a few minutes — and a chapter of Psalms can be 10+ minutes — this is unacceptable. A `MediaSessionService` (media3's `MediaSessionService` or `MediaLibraryService`) gives us:

- Lock-screen transport controls "for free."
- Bluetooth / Android Auto / Wear OS compatibility.
- Correct audio focus handling (duck on notification, pause on call).
- Survival of activity recreation.

ExoPlayer's media3 library already includes all of this; the incremental complexity over #127's standalone ExoPlayer is small.

### 5.3 Backend-mediated audio sets

Both PRs hard-code the list of supported versions and the sabda URL-building scheme inside the app. This creates two problems:

1. Adding a fifth supported version (e.g. ESV when SABDA adds it) requires an app release.
2. If sabda.org changes their folder scheme or CDN, every installed app breaks until users update.

**Decision:** the client asks the backend which audio it has for **one version at a time**, and gets back URL templates to expand. Chapter audio and timing JSON are served by the backend, which is free to proxy, redirect, or bake them into a CDN as needed. The app never learns the origin host.

A version can carry **several audio sets** — different recordings of the same text, distinguished by an opaque `audioId` (`alkitabsuara`, `davar`, `hosanna`, `wordproject`, …). They differ in narrator, in which books they cover, and in whether verse timing exists. The user picks one, and the choice persists per version (§5.5).

Availability is queried per version rather than downloaded as a single catalog of every audio-capable version. Upstream has 94 recording trees, 44 of which match real app versions, each with a growing set list and a per-set book-coverage array — a global document has no bound, and the app only ever needs the version in front of the user.

Endpoints, all on `${SERVER_HOST}`:

| Endpoint | Purpose |
|---|---|
| `GET /audio/sets/<preset>` | Which recordings exist for one version, with per-set book coverage |
| `GET /audio/file/<preset>/<audioId>/<book_1>/<chapter_1>.mp3` | Chapter audio, proxied and edge-cached |
| `GET /audio/timing/<preset>/<audioId>/<book_1>/<chapter_1>.json` | Verse timing, normalized to integer milliseconds |

`<preset>` is the version's preset name (`preset/in-tb` → `in-tb`). Book and chapter are **1-based** in every audio URL, matching the backend and its origin so a URL can be read against its source by eye. `Ari`'s `bookId` is 0-based, so `book_1 = bookId + 1` happens once in `BibleAudioRepository` and nowhere else; the `_1` suffix is this codebase's existing marker for 1-based values.

This adds one layer of indirection but removes the brittle coupling.

### 5.4 Models

```kotlin
data class AudioSets(
    val schema: Int,
    val preset: String,               // e.g. "in-tb"
    val sets: List<AudioSet>,         // ordered; sets[0] is the default
)

data class AudioSet(
    val audioId: String,              // opaque recording id, e.g. "alkitabsuara"
    val title: String,                // display name; falls back to audioId server-side
    val hasTiming: Boolean,           // false → plays, but no verse highlight
    val books_1: List<Int>,           // 1-based book coverage; ragged upstream
    val mp3UrlTemplate: String,       // "/audio/file/in-tb/davar/{book_1}/{chapter_1}.mp3"
    val timingUrlTemplate: String?,   // null exactly when hasTiming is false
) {
    fun coversBook(bookId: Int): Boolean = (bookId + 1) in books_1
}

data class VerseTiming(
    val verse_1: Int,     // 1-based
    val startMs: Long,
    val endMs: Long,
)

data class ChapterTiming(
    val preset: String,
    val audioId: String,
    val book_1: Int,
    val chapter_1: Int,
    val durationMs: Long,
    val verses: List<VerseTiming>,
)
```

An empty `sets` list means "no audio for this version" — a normal `200`, not an error.

`books_1` arrives as a `List<Int>` and is converted to a `Set<Int>` once at parse time, since `coversBook` is consulted on every menu preparation.

`MAudio` from PR #124 (`audio1..audio5`) is rejected — the slot-numbered fields don't map onto anything meaningful. `MAudio` from PR #127 is close to this but pins the sabda folder name in the client; we keep the shape and push the folder-name responsibility to the backend.

### 5.4.1 Internal-version mapping (per flavor)

Every `MVersion` subtype carries a `preset_name` naming the version it was built from — `MVersionPreset.preset_name` and `MVersionDb.preset_name` are what `/versions/get_yes?preset_name=…` downloads against. `MVersionInternal` (see [MVersionInternal.java](../../../Alkitab/src/main/java/yuku/alkitab/base/model/MVersionInternal.java)) is the exception: it reports `getVersionId() == "internal"` and nothing else, so a user reading the internal version on, say, the `yuku_alkitab` build would see no audio icon at all.

The bundled version *is* a preset build — TB on `yuku_alkitab` and `sabda_alkitab`, KJV on `yuku_quick_bible` — the app just never recorded which. So we record it generally rather than inventing an audio-only lookup: each product flavor declares **`BuildConfig.INTERNAL_VERSION_PRESET_NAME`** in [Alkitab/build.gradle.kts](../../../Alkitab/build.gradle.kts), and `MVersionInternal` exposes it as its `preset_name` like every other subtype.

| Flavor | Internal version content | `INTERNAL_VERSION_PRESET_NAME` |
|---|---|---|
| `plain` (open-source dev build) | placeholder Indonesian (`ddd_*`) | `in-tb` (so dev builds can play audio) |
| `yuku_alkitab` | TB | `in-tb` |
| `yuku_quick_bible` | KJV | `en-kjv` |
| `sabda_alkitab` | TB | `in-tb` |

This is deliberately **not** an audio-specific field — anything else that wants to know which preset the bundled version corresponds to (update checks, diagnostics, version-list grouping) gets the answer for free.

`AudioSetsRepository` then reads `preset_name` uniformly across every version type, with no audio-specific special case. Empty value = the flavor's internal version has no preset identity, so audio resolves to an empty set list and the toolbar icon stays hidden. A new flavor that doesn't set the field gets that default. A `file/…` version has no preset either, and short-circuits the same way.

### 5.5 Persistence

- `AudioSets` cached in memory per `versionId` — including negative results, so a version without audio doesn't re-query on every chapter turn — and on disk via OkHttp's existing 50MB cache (`Connections.okHttp`), honoring the backend's `Cache-Control`. No bundled asset, no hand-rolled ETag bookkeeping.
- `ChapterTiming` cached via the same OkHttp disk cache, keyed on the normalized URL. No custom SQLite tables.
- Selected audio set per version in `Prefkey.audioSelectedSets`, a JSON object (`{"preset/in-tb": "davar"}`). One key rather than a key per version — `Prefkey` is an enum, so per-version keys aren't expressible, and the map holds one entry per version the user has actually played. A remembered `audioId` that no longer exists falls back to `sets[0]` and rewrites the stored map.
- Playback-speed preference in `Prefkey.audioPlaybackSpeed` (new enum entry).
- Last position (for "resume where you left off") is stored in-memory on the service only — not persisted across kills, since most users will re-open the chapter anyway.

### 5.6 Split-view behavior

When split view is active and the user taps Audio:

- If only one of the two versions has audio → that side becomes the source, no prompt.
- If both have audio → a one-time prompt asks "Audio from top or bottom?" and the choice is sticky for the session.
- Highlight is applied only to the chosen split. Switching source restarts audio from the current verse using the other split's timing.

This replaces the "dual-audio interleaved" mode from both PRs. Reason: neither PR's split-view behavior matches any real-world audio Bible product, and both produce playback that sounds like a glitch. If a future requirement is "listen in English while reading Indonesian," the correct answer is that all verses come from one stream and the other split's highlight follows the timing of the chosen stream by verse number.

### 5.7 Module placement

New Kotlin files live under `Alkitab/src/main/java/yuku/alkitab/base/audio/`. The UI layer is **Jetpack Compose** (the audio bar is the project's first Compose surface; future migration of `IsiActivity` will follow this beachhead).

```
audio/
├─ AudioSets.kt                ← AudioSets + AudioSet
├─ AudioSetsRepository.kt
├─ BibleAudioRepository.kt
├─ BibleAudioPlayer.kt
├─ BibleAudioService.kt        ← media3 MediaSessionService
├─ HighlightTracker.kt
├─ ui/
│  ├─ AudioBar.kt              ← Compose @Composable: the fixed-height bar from §4.2
│  ├─ SpeedBottomSheet.kt      ← Compose: ModalBottomSheet with FilterChip row
│  ├─ AudioSetBottomSheet.kt   ← Compose: recording picker, shown only when a version has >1 set
│  ├─ AudioTheme.kt            ← Compose MaterialTheme bridged from app's ?attr/colorSurface*
│  └─ AudioHighlightColor.kt   ← yellow / fallback contrast logic
├─ AudioBarController.kt       ← Kotlin glue between IsiActivity (View) and Compose UI; holds StateFlow<UiState>
└─ model/
   ├─ AudioVersion.kt
   ├─ VerseTiming.kt
   └─ ChapterTiming.kt
```

Hosting in `IsiActivity`:

```xml
<!-- res/layout/activity_isi.xml — at the bottom of the root container -->
<androidx.compose.ui.platform.ComposeView
    android:id="@+id/audio_bar"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_gravity="bottom" />
```

Reuse: `Connections.okHttp` for HTTP, `BuildConfig.SERVER_HOST` for the base URL, existing `S`, `App`, `AppLog`. We deliberately do **not** reuse `yuku.alkitab.songs.ExoplayerController` (PR #124's approach) — the songs controller carries song-specific behavior (inline MaterialDialog error UI, loop-centric state machine, `MediaController.State` enum oriented at short clips) that pushes the bible-audio case into awkward shapes. We do borrow its pattern (OkHttp data source, MP3-only extractor factory) verbatim.

### 5.8 Library choices

- **media3 ExoPlayer** — already a dependency (`androidx.media3:media3-exoplayer:1.8.0`, via `ExoplayerController.kt:8-17`). Keep.
- **media3 session** — add `androidx.media3:media3-session:1.8.0` (`MediaSessionService`, notification provider).
- **Jetpack Compose 1.11.0** — new dependency family (this feature is the project's first Compose surface):
    - `androidx.compose.ui:ui:1.11.0`
    - `androidx.compose.material3:material3` (paired version that ships with Compose 1.11)
    - `androidx.compose.foundation:foundation:1.11.0`
    - `androidx.compose.runtime:runtime:1.11.0`
    - `androidx.compose.ui:ui-tooling-preview:1.11.0`
    - `androidx.activity:activity-compose:1.11.0` (already-aligned with project's `androidx.activity:activity-ktx:1.11.0`)
    - Kotlin Compose Compiler plugin (`org.jetbrains.kotlin.plugin.compose`, version-aligned with the project's Kotlin 2.2.0).
- **Kotlin coroutines + Flow** — already a project convention.
- **OkHttp** — already used.

Compose adds ~2 MB to the APK. We accept this once, since the audio bar is the migration beachhead and future Compose work amortises the cost.

## 6. Privacy, security, licensing

- Audio and timing data are served by `api.alkitab.app`, which may 302 to sabda.org or a CDN. The backend carries the licensing responsibility — there is no per-version attribution surface in the client UI for v1.
- No new user data collected beyond existing analytics. Playback events are **not** logged off-device in v1.
- No microphone / no audio capture.

## 7. Rollout

1. Land the backend `sets`/`file`/`timing` endpoints and deploy them to production. The client has no bundled fallback, so this must be live first. Availability is all-or-nothing — no client feature flag, no remote kill switch.
2. Ship the client on a feature branch, merge to `develop` only when M1–M4 of the client plan are done and the manual test matrix passes.
3. Release as part of the next normal version bump. If something breaks, fix-forward with a patch release, the same as any other feature.

## 8. Open questions

None open for v1. v2 items (pre-download, Android Auto, Chromecast, reading-plan entry) are scoped in their own sections; see §2.

## 9. Why not just merge PR #127?

Short answer: PR #127 is the better starting point — its separation of player/controller/repository, coroutine usage, and typed models are right. But three things need to change before it's production-ready:

| | PR #127 | This PRD |
|---|---|---|
| Player ownership | Activity | Foreground MediaSessionService |
| Version/URL resolution | Hard-coded in client | Queried per version from backend |
| Recordings per version | One | Several, user-selectable |
| Split-view audio | Sequential interleaved | Single-source, user-selectable |
| Lock-screen controls | Absent | First-class |
| Error UX | Silent log | Error state + tap-to-retry |

PR #124 is not the better starting point — its reuse of `ExoplayerController` couples two unrelated features, its URL construction bypasses `Connections.okHttp` (defeating the user-agent interceptor and HTTP cache), and it uses display-name keys that break under locale changes. We intend to close #124 with a note thanking the author.

## 10. References

- PR #127 implementation files (split across packages — keep this in mind when porting):
    - `Alkitab/src/main/java/yuku/alkitab/base/audio/{BibleAudioPlayer,AudioPlaybackController}.kt`
    - `Alkitab/src/main/java/yuku/alkitab/base/util/BibleAudioRepository.kt` (will move to `base/audio/` and shrink — all origin/folder/filename construction moves to the backend; see [backend-contract.md](backend-contract.md))
    - `Alkitab/src/main/java/yuku/alkitab/base/model/{MAudio,MTiming}.kt`
    - `Alkitab/src/main/java/yuku/alkitab/base/verses/{VerseItem,VersesController,VersesControllerImpl}.kt` (highlight diff)
    - Resources: `res/drawable/ic_audio_*.xml`, `res/layout/activity_audio.xml`, `res/menu/activity_isi.xml`
    - Full diff: [pull/127/head](https://github.com/yukuku/androidbible/pull/127/files)
- PR #124 implementation files: `Alkitab/src/main/java/yuku/alkitab/base/util/{Audio,Bible,Timing,MediaList}*`, [pull/124/head](https://github.com/yukuku/androidbible/pull/124/files).
- Existing song-audio design: `docs/modules/audio-playback.md`, `Alkitab/src/main/java/yuku/alkitab/songs/ExoplayerController.kt:33-194`.
- Verses/highlighting surface: `Alkitab/src/main/java/yuku/alkitab/base/verses/VersesController.kt`, `VerseItem.kt`.
- Backend conventions: `docs/backend-communication.md`, `docs/modules/sync.md`, `docs/modules/devotions.md`.
- Server host macro: `build.gradle:29`, `Alkitab/build.gradle:119`.
