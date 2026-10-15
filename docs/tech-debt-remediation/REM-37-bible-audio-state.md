# REM-37: Bible Audio State and Appearance

**Source:** 2026-07 code audit, verified against implementation on 2026-10-15
**Module:** Bible audio, `SplitViewManager`, `IsiActivity`
**BRICE:** B=4 R=3 I=4 C=4 E=4 → **3.8**
**Phase:** 2

**Status:** Complete on 2026-10-15.

## Confirmed findings and implementation

- MediaSession controls reach the underlying player without invoking the service's `play` and `pause` methods. After an in-app pause cancels polling, external resume could leave playback state and position frozen. External pause could also leave the poller running.
- `BibleAudioPlayer.Listener` forwards `onIsPlayingChanged`. `BibleAudioService` updates playback state and starts or cancels polling from that callback, including buffering and audio-focus suppression. Actual playback also acquires the audio coordinator. Reaching READY while paused does not start polling.
- The reader cached its audio highlight color until `onStart`, so night mode and text-appearance changes could retain the old color. `applyPreferences` invalidates the cache and reapplies the current highlight to both panes immediately, including while playback is paused.
- Split-version notification wiring was already complete: `loadSplitVersion` and `disableSplitVersion` call `onVisibleVersionsChanged`, which refreshes audio sources and controls. No additional split wiring was needed.

## Validation

`BibleAudioServicePlaybackTest` exercises external resume, pause, resumed position updates, coordinator ownership, and READY while paused. `IsiActivityIntentTest` also verifies immediate highlight repainting after an appearance change without a playback event. Existing audio-bar, coordinator, and highlight-color tests cover related behavior. Local verification uses `assemblePlainDebug` and the relevant `testPlainDebugUnitTest` classes. Physical lock-screen and Bluetooth interaction are not covered by these JVM tests.

---

[← Back to remediation index](../tech-debt-remediation.md)
