# REM-37: Bible Audio — Observe MediaSession-Driven State & Split-View Wiring

**Addresses:** PB-31, PB-33, PB-34
**Module:** Bible audio (`audio/BibleAudioPlayer`, `BibleAudioService`, `AudioBarController`) + `SplitViewManager` + `IsiActivity`
**BRICE:** B=4 R=3 I=4 C=4 E=4 → **3.8**
**Phase:** 2 — Architecture Improvements (2026-07 audit)

**Status:** Not started.

**Steps:**
1. **PB-31:** Forward `onIsPlayingChanged` (or `onPlayWhenReadyChanged`) through `BibleAudioPlayer.Listener` and drive `_playbackState.isPlaying` + the position-polling job from it, so play/pause from the notification, lock screen, or Bluetooth is reflected in the in-app bar, slider, and verse highlight — and so the 100 ms poller stops while paused. `SongAudioService` already does this; mirror it.
2. **PB-33:** Emit a version-changed signal from `SplitViewManager` open/close/version-swap (reuse `AppEvents.activeVersionChanged` or add a dedicated event) so `AudioBarController.onActiveVersionChanged()` actually runs for splits, and call `invalidateOptionsMenu()` so the toolbar audio icon appears/disappears with split versions.
3. **PB-34:** Invalidate `IsiActivity.audioHighlightColorCached` on every theme/appearance change (night-mode toggle, TextAppearancePanel), not just in `onStart`.
4. Tests: extend `AudioBarControllerReshowTest`/coordinator tests for the notification-resume path; a Robolectric test for highlight-color invalidation is optional.

**Difficulty:** Easy-Medium (~1 day).

---

[← Back to remediation index](../tech-debt-remediation.md)
