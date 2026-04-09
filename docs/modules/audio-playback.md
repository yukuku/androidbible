# Audio Playback Module

## Overview

Audio playback is used exclusively within the Songs module for playing song accompaniments. Supports MP3 (via ExoPlayer) and MIDI (via Android MediaPlayer).

## Key Files

- `Alkitab/src/main/java/yuku/alkitab/songs/MediaController.kt` — Abstract base with shared state machine
- `Alkitab/src/main/java/yuku/alkitab/songs/ExoplayerController.kt` — ExoPlayer-based MP3 playback
- `Alkitab/src/main/java/yuku/alkitab/songs/MidiController.kt` — MediaPlayer-based MIDI playback
- `Alkitab/src/main/java/yuku/alkitab/songs/MediaStateListener.kt` — Listener interface for UI updates

## State Machine

Both controllers share the same state machine:
```
reset → preparing → playing ⇄ paused → complete
                  ↘ error
```

## ExoPlayer Controller

- Uses AndroidX media3 ExoPlayer
- OkHttp integration for HTTP streaming
- Supports loop playback
- `getProgress()` returns `[position, duration]` in milliseconds

## MIDI Controller

- Uses Android's built-in `MediaPlayer`
- Downloads MIDI files to local cache before playback (background thread)
- Same state machine and progress reporting as ExoPlayer

## Integration

`SongFragment.kt` instantiates the appropriate controller based on the audio type and provides play/pause/stop UI controls. The `MediaStateListener` callbacks update the fragment's UI to reflect playback state.
