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
- `audio/AudioHttpEventLogger.kt` — OkHttp `EventListener` recording every connection-state transition (DNS, connect, TLS, headers, body) of a chapter fetch
- `audio/AudioLogMessage.kt`, `audio/AudioLogEntry.kt` — A log event as a string resource plus args, and the timestamped entry the service resolves it into
- `audio/ui/AudioBar.kt`, `ui/SpeedBottomSheet.kt`, `ui/AudioLogBottomSheet.kt`, `ui/AudioHighlightColor.kt`, `ui/AudioTheme.kt` — Compose UI (playback bar, speed picker, load log, highlight color)

## Load Status and Logging

`PlaybackState.logs` carries a timestamped account of the chapter currently loading, reset on every `loadChapter` so a retry starts clean and capped at 500 entries. Three producers feed it, all resolved against the service's resources so the log reads in the user's language:

- `AudioHttpEventLogger`, attached to a player-scoped OkHttp client so only chapter fetches are traced. On a non-2xx response it also logs the body when it is under 1 KB, folding newlines to spaces, or a hex dump of the first 16 bytes when the body is not printable ASCII.
- `BibleAudioPlayer`, for buffering/ready/ended/error transitions. media3's `ERROR_CODE_` prefix is stripped for display.
- `AudioBarController`, for button presses and bar state transitions.

The bar shows a tappable status line above the play button once a load has been preparing for 5 seconds, or immediately when it fails, carrying the error text in the failure case and the latest log entry otherwise. Tapping it opens `AudioLogBottomSheet` with the full timestamped log.

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
