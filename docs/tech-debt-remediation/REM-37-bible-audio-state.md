# REM-37: Bible Audio — Observe MediaSession-Driven State & Split-View Wiring

**Source:** 2026-07 code audit
**Module:** Bible audio (`audio/BibleAudioPlayer`, `BibleAudioService`, `AudioBarController`) + `SplitViewManager` + `IsiActivity`
**BRICE:** B=4 R=3 I=4 C=4 E=4 → **3.8**
**Phase:** 2 — Architecture Improvements (2026-07 audit)

**Status:** Partial, reviewed on 2026-10-15. Split-version notification wiring (step 2) is complete. MediaSession-driven playback observation and appearance invalidation remain open.

**Steps:**
1. Forward `onIsPlayingChanged` (or `onPlayWhenReadyChanged`) through `BibleAudioPlayer.Listener` and drive `_playbackState.isPlaying` + the position-polling job from it, so play/pause from the notification, lock screen, or Bluetooth is reflected in the in-app bar, slider, and verse highlight — and so the 100 ms poller stops while paused. `SongAudioService` already does this; mirror it.
2. ✅ **Complete:** `SplitViewManager.loadSplitVersion` and `disableSplitVersion` call `SplitViewActions.onVisibleVersionsChanged`. `IsiActivity.onVisibleVersionsChanged` updates reading state, resolves audio sources, calls `audioBinder.onActiveVersionChanged()`, and refreshes both toolbar variants through `refreshAudioControls()`.
3. Invalidate `IsiActivity.audioHighlightColorCached` on every theme/appearance change (night-mode toggle, TextAppearancePanel), not just in `onStart`.
4. Tests: extend `AudioBarControllerReshowTest`/coordinator tests for the notification-resume path; a Robolectric test for highlight-color invalidation is optional.

**Difficulty:** Easy-Medium (~1 day).

---

[← Back to remediation index](../tech-debt-remediation.md)
