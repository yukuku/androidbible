# Audio Playback Module

## Overview

The app has two audio subsystems that are mutually exclusive at runtime:

- **Bible chapter audio** (`Alkitab/src/main/java/yuku/alkitab/base/audio/`) — streams per-chapter MP3s with verse-level highlight/auto-scroll in the reader.
- **Song (kidung/hymn) audio** (`Alkitab/src/main/java/yuku/alkitab/songs/`) — plays MP3 or MIDI accompaniments from the song viewer.

Both ride AndroidX media3 `ExoPlayer` inside a foreground `MediaSessionService`, so both get background playback and a lock-screen media notification. `AudioPlaybackCoordinator` (a process-wide singleton) guarantees at most one logical audio session is active: each service `acquire()`s on play, and acquiring stops the previously active session in code (OS audio-focus arbitration alone proved unreliable).

## Bible Audio — Key Files

- `audio/BibleAudioService.kt` — Foreground `MediaSessionService` owning the player
- `audio/BibleAudioPlayer.kt` — Wrapper around media3 `ExoPlayer`; fetches over the shared OkHttp client
- `audio/BibleChapterNavigatingPlayer.kt` — `ForwardingPlayer` that re-purposes skip-next/previous transport commands as chapter navigation
- `audio/BibleNeighborResolver.kt` — Resolves previous/next chapter targets across book boundaries
- `audio/BibleAudioRepository.kt` — Turns `(versionId, bookId, chapter_1)` into a chapter MP3 URL + verse timing, via catalog URL templates
- `audio/AudioCatalogRepository.kt` — Source of truth for the audio catalog (which versions have audio + URL templates); backend-served with a bundled fallback
- `audio/HighlightTracker.kt` — Maps playback position to the active 1-based verse number as a `StateFlow`, driving verse highlight and auto-scroll
- `audio/AudioBarController.kt` — Glue between the View-based `IsiActivity` and the Compose audio bar
- `audio/ui/AudioBar.kt`, `ui/SpeedBottomSheet.kt`, `ui/AudioHighlightColor.kt`, `ui/AudioTheme.kt` — Compose UI (playback bar, speed picker, highlight color)

## Song Audio — Key Files

- `songs/SongAudioService.kt` — Foreground `MediaSessionService` for hymn audio. A single `ExoPlayer` decodes both MP3 (over OkHttp) and MIDI (via the experimental `media3-exoplayer-midi` JSyn synth), so one code path covers both formats
- `songs/SongAudioController.kt` — Activity-facing `MediaController` that drives playback through the background service. Replaces the former activity-scoped `ExoplayerController` and `MidiController`; format selection happens inside the service
- `songs/MediaController.kt` — Abstract base with the shared state machine
- `songs/MediaStateListener.kt` — Listener interface for UI updates
- `songs/SongPlaybackState.kt` — Service-side playback state, mapped onto the `MediaController.State` machine

## State Machine

Song audio uses the `MediaController` state machine, fed from `SongPlaybackState`:
```
reset → preparing → playing ⇄ paused → complete
                  ↘ error
```

## Integration

- Bible audio: `IsiActivity` shows the Compose `AudioBar` via `AudioBarController`; `HighlightTracker`'s flow drives the verse highlight while audio plays.
- The verse action-mode item "Play audio from this verse" is offered only when a visible version has a recording with verse timing that covers the book being read. When the recording that would play lacks timing, the recording sheet opens first, listing only recordings with timing (grouped per version, like the audio bar's recording chip), and the pick starts playback at the requested verse.
- Song audio: `SongViewActivity`'s toolbar drives `SongAudioController`, whose service keeps playing (with notification) after the activity is backgrounded.
- Starting either kind of audio stops the other via `AudioPlaybackCoordinator`.
