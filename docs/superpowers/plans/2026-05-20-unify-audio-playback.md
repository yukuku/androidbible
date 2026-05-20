# Unify Bible + Kidung Audio Playback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Bible audio and kidung (hymn) audio mutually exclusive (starting one stops the other, guaranteed in code), and give kidung audio — both MP3 and MIDI — background playback with a media notification, matching Bible audio.

**Architecture:** Approach **(B) shared coordinator**. `BibleAudioService` stays as-is functionally. A new foreground `SongAudioService` (`MediaSessionService` + media3 `ExoPlayer`) hosts hymn audio. A process-wide `AudioPlaybackCoordinator` singleton arbitrates: each service `acquire()`s on play; acquiring stops the previously-active session. MIDI is **fully unified onto ExoPlayer** via the experimental `media3-exoplayer-midi` module (JSyn synth), so `MidiController` and `ExoplayerController` are deleted and both hymn formats ride the one `SongAudioService` — getting background + notification + mutual exclusion for free.

**Tech Stack:** Kotlin, AndroidX media3 1.9.2 (`exoplayer`, `session`, `datasource-okhttp`, **new** `exoplayer-midi`), kotlinx coroutines `StateFlow`, JUnit (+ existing Robolectric available).

**MIDI scope decision:** MIDI plays through ExoPlayer's experimental MIDI decoder (bundled JSyn software synth). Sound may differ audibly from the old `android.media.MediaPlayer` system synth — this CANNOT be verified headless and must be checked by ear in manual QA.

**Style (per CLAUDE.md):** Kotlin for new code; NO default parameter values (explicit at every call site); default to no comments (only non-obvious WHY); surgical but clean (no dead code, no compat shims); match existing style.

---

## File Structure

**New:**
- `Alkitab/src/main/java/yuku/alkitab/base/audio/AudioPlaybackCoordinator.kt` — process-wide single-session arbiter. Pure logic, unit-tested.
- `Alkitab/src/main/java/yuku/alkitab/songs/SongAudioService.kt` — foreground `MediaSessionService` owning an `ExoPlayer` that decodes MP3 **and** MIDI; `StateFlow<SongPlaybackState>`; `LocalBinder`. Implements `AudioPlaybackCoordinator.Session`.
- `Alkitab/src/main/java/yuku/alkitab/songs/SongPlaybackState.kt` — UI snapshot data class for `SongAudioService`.
- `Alkitab/src/main/java/yuku/alkitab/songs/SongAudioController.kt` — `MediaController` subclass; binds `SongViewActivity` to `SongAudioService`, maps service state → `MediaController.State`. Replaces `ExoplayerController` + `MidiController`.
- `Alkitab/src/test/java/yuku/alkitab/base/audio/AudioPlaybackCoordinatorTest.kt` — coordinator unit tests.

**Modified:**
- `gradle/libs.versions.toml` — add `androidx-media3-exoplayer-midi` library entry.
- `Alkitab/build.gradle.kts` — add the new dependency.
- `Alkitab/src/main/AndroidManifest.xml` — declare `SongAudioService` (`mediaPlayback` foreground type).
- `Alkitab/src/main/java/yuku/alkitab/base/App.java` — create `audio_song` notification channel.
- `Alkitab/src/main/res/values/strings.xml` (+ `values-in/strings.xml`) — `audio_song_notification_channel_name`.
- `Alkitab/src/main/java/yuku/alkitab/base/audio/BibleAudioService.kt` — implement `AudioPlaybackCoordinator.Session`; `acquire` on `play`/`loadChapter`, `release` on `stop`/`onDestroy`.
- `Alkitab/src/main/java/yuku/alkitab/base/audio/AudioBarController.kt` — hide bar on external (non-pending) IDLE stop.
- `Alkitab/src/main/java/yuku/alkitab/songs/SongViewActivity.kt` — single `SongAudioController` instead of dual companion controllers; drop MP3-vs-MIDI branching in `checkAudioExistance`.

**Deleted:**
- `Alkitab/src/main/java/yuku/alkitab/songs/ExoplayerController.kt`
- `Alkitab/src/main/java/yuku/alkitab/songs/MidiController.kt`

`MediaController.kt` (abstract base + `State` enum) and `MediaStateListener.kt` are KEPT — `SongAudioController` extends `MediaController`, preserving the `SongViewActivity` UI contract.

---

## Task 1: AudioPlaybackCoordinator (TDD)

**Files:**
- Create: `Alkitab/src/main/java/yuku/alkitab/base/audio/AudioPlaybackCoordinator.kt`
- Test: `Alkitab/src/test/java/yuku/alkitab/base/audio/AudioPlaybackCoordinatorTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package yuku.alkitab.base.audio

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Test

class AudioPlaybackCoordinatorTest {

    private class FakeSession : AudioPlaybackCoordinator.Session {
        var stopCount = 0
        override fun stopPlayback() {
            stopCount++
        }
    }

    @After
    fun tearDown() {
        AudioPlaybackCoordinator.resetForTest()
    }

    @Test
    fun `acquiring a second session stops the first`() {
        val first = FakeSession()
        val second = FakeSession()

        AudioPlaybackCoordinator.acquire(first)
        AudioPlaybackCoordinator.acquire(second)

        assertEquals(1, first.stopCount)
        assertEquals(0, second.stopCount)
        assertSame(second, AudioPlaybackCoordinator.activeForTest())
    }

    @Test
    fun `acquiring the same session twice does not stop it`() {
        val only = FakeSession()

        AudioPlaybackCoordinator.acquire(only)
        AudioPlaybackCoordinator.acquire(only)

        assertEquals(0, only.stopCount)
        assertSame(only, AudioPlaybackCoordinator.activeForTest())
    }

    @Test
    fun `releasing the active session clears the active slot`() {
        val session = FakeSession()

        AudioPlaybackCoordinator.acquire(session)
        AudioPlaybackCoordinator.release(session)

        assertNull(AudioPlaybackCoordinator.activeForTest())
    }

    @Test
    fun `releasing a non-active session is a no-op`() {
        val active = FakeSession()
        val other = FakeSession()

        AudioPlaybackCoordinator.acquire(active)
        AudioPlaybackCoordinator.release(other)

        assertSame(active, AudioPlaybackCoordinator.activeForTest())
        assertEquals(0, active.stopCount)
    }

    @Test
    fun `a session can be re-acquired after release`() {
        val a = FakeSession()
        val b = FakeSession()

        AudioPlaybackCoordinator.acquire(a)
        AudioPlaybackCoordinator.release(a)
        AudioPlaybackCoordinator.acquire(b)

        // a was already released, so taking over must NOT stop it again.
        assertEquals(0, a.stopCount)
        assertSame(b, AudioPlaybackCoordinator.activeForTest())
    }

    @Test
    fun `stopPlayback callback re-entrantly releasing itself does not clear the new owner`() {
        // Models the real flow: acquire(bible) calls song.stopPlayback(),
        // and song.stop() calls release(song) re-entrantly.
        val newOwner = FakeSession()
        val reentrantReleaser = object : AudioPlaybackCoordinator.Session {
            var stopCount = 0
            override fun stopPlayback() {
                stopCount++
                AudioPlaybackCoordinator.release(this)
            }
        }

        AudioPlaybackCoordinator.acquire(reentrantReleaser)
        AudioPlaybackCoordinator.acquire(newOwner)

        assertEquals(1, reentrantReleaser.stopCount)
        assertSame(newOwner, AudioPlaybackCoordinator.activeForTest())
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew testPlainDebugUnitTest --tests "yuku.alkitab.base.audio.AudioPlaybackCoordinatorTest" 2>&1 | tail -60`
Expected: FAIL — `AudioPlaybackCoordinator` unresolved.

- [ ] **Step 3: Implement the coordinator**

```kotlin
package yuku.alkitab.base.audio

import androidx.annotation.VisibleForTesting

/**
 * Process-wide arbiter guaranteeing that at most one logical audio session
 * (Bible chapter audio or kidung/hymn audio) is active at a time.
 *
 * OS audio-focus arbitration alone is unreliable here — the legacy hymn player
 * kept playing over Bible audio — so mutual exclusion is enforced in code:
 * whoever [acquire]s last wins, and the previous owner is told to
 * [Session.stopPlayback] (a full stop, not a pause).
 *
 * All calls happen on the main thread (media3's [androidx.media3.common.Player]
 * contract); the lock is cheap insurance for the test harness, which runs off
 * the main thread.
 */
object AudioPlaybackCoordinator {
    interface Session {
        /** Stop playback completely and clear any media notification. */
        fun stopPlayback()
    }

    private val lock = Any()
    private var active: Session? = null

    fun acquire(session: Session) {
        synchronized(lock) {
            val previous = active
            // Set the new owner BEFORE stopping the previous one: stopPlayback()
            // typically calls release() re-entrantly, and that release must not
            // clear the slot we just claimed (it checks active === itself).
            active = session
            if (previous != null && previous !== session) {
                previous.stopPlayback()
            }
        }
    }

    fun release(session: Session) {
        synchronized(lock) {
            if (active === session) {
                active = null
            }
        }
    }

    @VisibleForTesting
    fun activeForTest(): Session? = synchronized(lock) { active }

    @VisibleForTesting
    fun resetForTest() {
        synchronized(lock) { active = null }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew testPlainDebugUnitTest --tests "yuku.alkitab.base.audio.AudioPlaybackCoordinatorTest" 2>&1 | tail -60`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add Alkitab/src/main/java/yuku/alkitab/base/audio/AudioPlaybackCoordinator.kt \
        Alkitab/src/test/java/yuku/alkitab/base/audio/AudioPlaybackCoordinatorTest.kt
git commit -m "feat(audio): add AudioPlaybackCoordinator for single-session arbitration"
```

---

## Task 2: Wire BibleAudioService into the coordinator

**Files:**
- Modify: `Alkitab/src/main/java/yuku/alkitab/base/audio/BibleAudioService.kt`

- [ ] **Step 1: Declare the Session implementation**

Change the class header (line 71) to implement the interface:

```kotlin
class BibleAudioService : MediaSessionService(), AudioPlaybackCoordinator.Session {
```

Add the override near the public API section (after `onDestroy`, before `loadChapter`):

```kotlin
override fun stopPlayback() {
    stop()
}
```

- [ ] **Step 2: Acquire on user-initiated playback**

In `loadChapter` (line 306), as the first statement of the method body:

```kotlin
fun loadChapter(request: AudioRequest) {
    AudioPlaybackCoordinator.acquire(this)
    loadJob?.cancel()
    // ... unchanged ...
```

In `play` (line 373), as the first statement:

```kotlin
fun play() {
    AudioPlaybackCoordinator.acquire(this)
    player.play()
    // ... unchanged ...
```

- [ ] **Step 3: Release on stop and destroy**

In `stop` (line 430), after `stopSelf()`:

```kotlin
fun stop() {
    loadJob?.cancel()
    timingJob?.cancel()
    positionJob?.cancel()
    currentRequest = null
    player.pause()
    _playbackState.value = PlaybackState.IDLE
    stopSelf()
    AudioPlaybackCoordinator.release(this)
}
```

In `onDestroy` (line 287), before `super.onDestroy()`:

```kotlin
    player.release()
    AudioPlaybackCoordinator.release(this)
    super.onDestroy()
```

- [ ] **Step 4: Build**

Run: `./gradlew assemblePlainDebug 2>&1 | tail -60`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add Alkitab/src/main/java/yuku/alkitab/base/audio/BibleAudioService.kt
git commit -m "feat(audio): register BibleAudioService with the playback coordinator"
```

---

## Task 3: Hide the audio bar when Bible audio is stopped externally

**Files:**
- Modify: `Alkitab/src/main/java/yuku/alkitab/base/audio/AudioBarController.kt:491-502`

When the coordinator stops `BibleAudioService` (because a hymn started), the service emits `PlaybackState.IDLE`. If `IsiActivity` is still bound, the bar must hide instead of lingering in a dead paused state.

- [ ] **Step 1: Add the external-stop guard in `projectToUi`**

After the `pendingLoad` drain block (currently lines 499-502), insert:

```kotlin
        // A coordinator-driven external stop (a hymn took over audio focus)
        // resets the service to IDLE. Tear the bar down so it doesn't linger
        // in a dead, un-resumable state. `isPending` guards the startup race
        // where the freshly-created service replays IDLE before our queued
        // loadChapter has run.
        if (!isPending && requestedVisible && state == PlaybackState.IDLE) {
            hide()
            return
        }
```

- [ ] **Step 2: Build**

Run: `./gradlew assemblePlainDebug 2>&1 | tail -60`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add Alkitab/src/main/java/yuku/alkitab/base/audio/AudioBarController.kt
git commit -m "feat(audio): hide audio bar when Bible playback is stopped externally"
```

---

## Task 4: Add the media3 MIDI dependency

**Files:**
- Modify: `gradle/libs.versions.toml:73`
- Modify: `Alkitab/build.gradle.kts:423`

`settings.gradle.kts:14` already declares `maven("https://jitpack.io")` (JSyn's host), so no repository change is needed.

- [ ] **Step 1: Add the library coordinate**

In `gradle/libs.versions.toml`, after the `androidx-media3-session` line (73):

```toml
androidx-media3-exoplayer-midi = { group = "androidx.media3", name = "media3-exoplayer-midi", version.ref = "androidxMedia3" }
```

- [ ] **Step 2: Add the dependency**

In `Alkitab/build.gradle.kts`, after `implementation(libs.androidx.media3.session)` (line 423):

```kotlin
    implementation(libs.androidx.media3.exoplayer.midi)
```

- [ ] **Step 3: Verify resolution**

Run: `./gradlew :Alkitab:dependencies --configuration plainDebugRuntimeClasspath 2>&1 | grep -i midi`
Expected: shows `androidx.media3:media3-exoplayer-midi:1.9.2` and its `com.github.philburk:jsyn` transitive.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml Alkitab/build.gradle.kts
git commit -m "build(audio): add media3-exoplayer-midi for hymn MIDI playback"
```

---

## Task 5: SongPlaybackState

**Files:**
- Create: `Alkitab/src/main/java/yuku/alkitab/songs/SongPlaybackState.kt`

- [ ] **Step 1: Create the state snapshot**

```kotlin
package yuku.alkitab.songs

/**
 * UI-facing snapshot of [SongAudioService]'s playback, emitted via a
 * `StateFlow` and mapped by [SongAudioController] onto the legacy
 * [MediaController.State] machine that drives [SongViewActivity]'s toolbar.
 *
 *  - [hasMedia]   — a song URL has been loaded into the player.
 *  - [preparing]  — between `load` and the player reporting READY.
 *  - [isPlaying]  — the player is actually producing audio.
 *  - [ended]      — playback reached the end of a non-looping song.
 *  - [error]      — non-null when the player hit a fatal error this session.
 *  - [positionMs] / [durationMs] — last known transport values, `-1` if unknown.
 */
data class SongPlaybackState(
    val hasMedia: Boolean,
    val preparing: Boolean,
    val isPlaying: Boolean,
    val ended: Boolean,
    val error: String?,
    val positionMs: Long,
    val durationMs: Long,
) {
    companion object {
        val IDLE = SongPlaybackState(
            hasMedia = false,
            preparing = false,
            isPlaying = false,
            ended = false,
            error = null,
            positionMs = -1L,
            durationMs = -1L,
        )
    }
}
```

- [ ] **Step 2: Commit** (committed together with Task 6 — no standalone build needed.)

---

## Task 6: SongAudioService (foreground MediaSessionService, MP3 + MIDI)

**Files:**
- Create: `Alkitab/src/main/java/yuku/alkitab/songs/SongAudioService.kt`

Mirrors `BibleAudioService`'s foreground/notification/local-binder patterns, minus the Bible-specific verse-timing, highlight, and chapter navigation. The `ExoPlayer` uses `DefaultRenderersFactory` with `EXTENSION_RENDERER_MODE_ON` (so the reflectively-loaded `MidiRenderer` activates) and an `ExtractorsFactory` that returns both `Mp3Extractor` and `MidiExtractor`, fed by the shared OkHttp data source. Format selection is automatic — no MP3-vs-MIDI branching.

- [ ] **Step 1: Create the service**

```kotlin
package yuku.alkitab.songs

import android.app.PendingIntent
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.decoder.midi.MidiExtractor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import yuku.alkitab.base.audio.AudioPlaybackCoordinator
import yuku.alkitab.base.connection.Connections
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.debug.R

/**
 * Foreground [MediaSessionService] for kidung (hymn) audio. Owns a single
 * media3 [ExoPlayer] that decodes both MP3 (over OkHttp) and MIDI (via the
 * experimental `media3-exoplayer-midi` JSyn synth), so one code path covers
 * both formats and both get a lock-screen / background media notification.
 *
 * Registers with [AudioPlaybackCoordinator] so starting a hymn stops Bible
 * audio and vice-versa. Mirrors [yuku.alkitab.base.audio.BibleAudioService]'s
 * lifecycle: clients `startService` then `bindService` with [ACTION_LOCAL_BIND];
 * media3 promotes us to foreground when the player becomes user-engaged.
 */
@OptIn(UnstableApi::class)
class SongAudioService : MediaSessionService(), AudioPlaybackCoordinator.Session {

    companion object {
        const val ACTION_LOCAL_BIND = "yuku.alkitab.songs.ACTION_LOCAL_BIND"
        const val NOTIFICATION_CHANNEL_ID = "audio_song"
        private const val TAG = "SongAudioService"
    }

    data class SongRequest(
        val url: String,
        val displayTitle: String,
        val displaySubtitle: String,
        val loop: Boolean,
    )

    class LocalBinder internal constructor(service: SongAudioService) : Binder() {
        private val ref = WeakReference(service)
        val service: SongAudioService? get() = ref.get()
    }

    private val localBinder = LocalBinder(this)

    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null

    private val _playbackState = MutableStateFlow(SongPlaybackState.IDLE)
    val playbackState: StateFlow<SongPlaybackState> = _playbackState.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> _playbackState.update { it.copy(preparing = true, error = null) }
                Player.STATE_READY -> _playbackState.update {
                    it.copy(preparing = false, ended = false, isPlaying = player.isPlaying, error = null)
                }
                Player.STATE_ENDED -> _playbackState.update {
                    it.copy(preparing = false, isPlaying = false, ended = true)
                }
                else -> Unit
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playbackState.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlayerError(error: PlaybackException) {
            AppLog.w(TAG, "player error: ${error.errorCodeName} ${error.message}")
            _playbackState.update { it.copy(preparing = false, isPlaying = false, error = error.errorCodeName) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        player = buildPlayer()
        player.addListener(playerListener)

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            SongViewActivity.createIntent().addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
            .also { addSession(it) }

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(NOTIFICATION_CHANNEL_ID)
                .setChannelName(R.string.audio_song_notification_channel_name)
                .build()
        )
    }

    private fun buildPlayer(): ExoPlayer {
        val okHttpDataSourceFactory = OkHttpDataSource.Factory(Connections.okHttp)
            .setUserAgent(Connections.httpUserAgent)
        val extractorsFactory = ExtractorsFactory {
            arrayOf<Extractor>(Mp3Extractor(), MidiExtractor())
        }
        // DefaultRenderersFactory + EXTENSION_RENDERER_MODE_ON activates the
        // reflectively-loaded MidiRenderer (JSyn) shipped by media3-exoplayer-midi.
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        return ExoPlayer.Builder(
            this,
            renderersFactory,
            ProgressiveMediaSource.Factory(okHttpDataSourceFactory, extractorsFactory),
        )
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        if (intent?.action == ACTION_LOCAL_BIND) {
            return localBinder
        }
        return super.onBind(intent)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession!!

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (player.isPlaying) {
            return
        }
        stopSelf()
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        player.release()
        AudioPlaybackCoordinator.release(this)
        super.onDestroy()
    }

    override fun stopPlayback() {
        stop()
    }

    // -- public API surfaced via LocalBinder ---------------------------------

    fun load(request: SongRequest) {
        AudioPlaybackCoordinator.acquire(this)
        val metadata = MediaMetadata.Builder()
            .setTitle(request.displayTitle)
            .setArtist(request.displaySubtitle)
            .build()
        val mediaItem = MediaItem.Builder()
            .setUri(request.url)
            .setMediaMetadata(metadata)
            .build()
        player.repeatMode = if (request.loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        player.setMediaItem(mediaItem)
        player.playWhenReady = true
        player.prepare()
        _playbackState.update {
            it.copy(hasMedia = true, preparing = true, isPlaying = false, ended = false, error = null)
        }
    }

    fun play() {
        AudioPlaybackCoordinator.acquire(this)
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0L)
        }
        player.playWhenReady = true
    }

    fun pause() {
        player.playWhenReady = false
    }

    fun setLoop(loop: Boolean) {
        player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    val currentPositionMs: Long
        get() = player.currentPosition.coerceAtLeast(0L)

    val durationMs: Long
        get() = player.duration.let { if (it < 0L) -1L else it }

    fun stop() {
        player.stop()
        player.clearMediaItems()
        _playbackState.value = SongPlaybackState.IDLE
        stopSelf()
        AudioPlaybackCoordinator.release(this)
    }
}
```

> NOTE for the implementer: confirm the `MidiExtractor` import path resolves to `androidx.media3.decoder.midi.MidiExtractor` after Task 4 syncs. If the build reports it unresolved, run `./gradlew :Alkitab:dependencies | grep midi` and inspect the artifact's classes — the renderer is `androidx.media3.decoder.midi.MidiRenderer` (loaded reflectively by `DefaultRenderersFactory`, so it is NOT imported directly here).

- [ ] **Step 2: Commit** (with Task 5 + Task 7; build verified in Task 9.)

```bash
git add Alkitab/src/main/java/yuku/alkitab/songs/SongPlaybackState.kt \
        Alkitab/src/main/java/yuku/alkitab/songs/SongAudioService.kt
git commit -m "feat(audio): add SongAudioService for background hymn MP3/MIDI playback"
```

---

## Task 7: SongAudioController (binds SongViewActivity to the service)

**Files:**
- Create: `Alkitab/src/main/java/yuku/alkitab/songs/SongAudioController.kt`

A `MediaController` subclass that preserves the `SongViewActivity` contract (`setUI`, `mediaKnownToExist`, `playOrPause`, `getProgress`, `reset`, `canHaveNewUrl`, `State`) while delegating actual playback to the bound `SongAudioService`. Maps `SongPlaybackState` → `MediaController.State`.

- [ ] **Step 1: Create the controller**

```kotlin
package yuku.alkitab.songs

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import yuku.alkitab.base.util.AppLog

private const val TAG = "SongAudioController"

/**
 * Activity-facing [MediaController] that drives playback through the
 * background [SongAudioService]. Binds with `startService` + [ACTION_LOCAL_BIND]
 * (app context) so the service outlives the activity and keeps playing in the
 * background with its media notification.
 *
 * It maps the service's [SongPlaybackState] onto the [MediaController.State]
 * machine that [SongViewActivity]'s toolbar already understands, so the UI code
 * does not change.
 */
class SongAudioController(private val appContext: Context) : MediaController() {

    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var collectJob: Job? = null
    private var service: SongAudioService? = null
    private var bound = false
    private var loop = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = (binder as? SongAudioService.LocalBinder)?.service ?: return
            service = svc
            collectJob?.cancel()
            collectJob = scope.launch {
                svc.playbackState.collect { mapState(it) }
            }
            // Fire the pending load queued before the service connected.
            pendingRequest?.let { req ->
                svc.load(req)
                pendingRequest = null
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            collectJob?.cancel()
            collectJob = null
        }
    }

    private var pendingRequest: SongAudioService.SongRequest? = null

    override fun reset() {
        super.reset() // sets State.reset + notifies UI
        service?.stop()
        unbind()
    }

    override fun playOrPause(playInLoop: Boolean) {
        loop = playInLoop
        when (state) {
            State.reset -> Unit
            State.reset_media_known_to_exist, State.complete, State.error -> {
                val url = url ?: return
                state = State.preparing
                ensureBound()
                val request = SongAudioService.SongRequest(
                    url = url,
                    displayTitle = url.substringAfterLast('/'),
                    displaySubtitle = "",
                    loop = playInLoop,
                )
                service?.load(request) ?: run { pendingRequest = request }
            }
            State.preparing -> Unit
            State.playing -> {
                if (playInLoop) {
                    service?.setLoop(true)
                } else {
                    service?.pause()
                }
            }
            State.paused -> {
                service?.setLoop(playInLoop)
                service?.play()
            }
        }
    }

    override fun getProgress(): LongArray {
        val svc = service ?: return longArrayOf(-1, -1)
        return when (state) {
            State.playing, State.paused, State.complete -> longArrayOf(svc.currentPositionMs, svc.durationMs)
            else -> longArrayOf(-1, -1)
        }
    }

    private fun mapState(s: SongPlaybackState) {
        state = when {
            s.error != null -> State.error
            s.preparing -> State.preparing
            s.isPlaying -> State.playing
            s.ended -> State.complete
            s.hasMedia -> State.paused
            else -> State.reset_media_known_to_exist
        }
    }

    private fun ensureBound() {
        if (bound) return
        val startIntent = Intent(appContext, SongAudioService::class.java)
        appContext.startService(startIntent)
        val bindIntent = Intent(appContext, SongAudioService::class.java)
            .setAction(SongAudioService.ACTION_LOCAL_BIND)
        try {
            if (appContext.bindService(bindIntent, connection, Context.BIND_AUTO_CREATE)) {
                bound = true
            } else {
                AppLog.w(TAG, "bindService returned false — service not bound")
            }
        } catch (e: SecurityException) {
            AppLog.e(TAG, "bindService denied: ${e.message}")
        }
    }

    private fun unbind() {
        if (bound) {
            try {
                appContext.unbindService(connection)
            } catch (e: IllegalArgumentException) {
                AppLog.w(TAG, "unbindService: ${e.message}")
            }
            bound = false
        }
        service = null
        pendingRequest = null
        collectJob?.cancel()
        collectJob = null
    }
}
```

> The `MediaController` base exposes `url` and `state` as `protected`; `SongAudioController` is in the same package so it can read `url`. `super.reset()` already sets `state = State.reset` and notifies the listener.

- [ ] **Step 2: Commit** (with Task 6.)

---

## Task 8: Rewire SongViewActivity to the single controller; delete legacy controllers

**Files:**
- Modify: `Alkitab/src/main/java/yuku/alkitab/songs/SongViewActivity.kt`
- Delete: `Alkitab/src/main/java/yuku/alkitab/songs/ExoplayerController.kt`
- Delete: `Alkitab/src/main/java/yuku/alkitab/songs/MidiController.kt`

- [ ] **Step 1: Replace the companion controllers**

In the `companion object` (lines 1089-1100) replace:

```kotlin
        var activeMediaController: MediaController? = null

        val midiController = MidiController()
        val exoplayerController = ExoplayerController(App.context)

        var audioDisclaimerAcknowledged = false
```

with:

```kotlin
        val songAudioController = SongAudioController(App.context)

        var audioDisclaimerAcknowledged = false
```

- [ ] **Step 2: Remove the obsolete imports**

Delete lines 56-57:

```kotlin
import yuku.alkitab.songs.SongViewActivity.Companion.exoplayerController
import yuku.alkitab.songs.SongViewActivity.Companion.midiController
```

- [ ] **Step 3: Collapse `checkAudioExistance` format branching**

Replace the body of the `runOnUiThread { ... }` block inside `checkAudioExistance` (lines 375-388) so it no longer picks a controller by extension:

```kotlin
                        runOnUiThread {
                            if (songAudioController.canHaveNewUrl()) {
                                val url = "${BuildConfig.SERVER_HOST}/addon/audio/${getAudioFilename(currentBookName, currentSong.code)}"
                                songAudioController.setUI(this, this)
                                songAudioController.mediaKnownToExist(url)
                            } else {
                                AppLog.d(TAG, "songAudioController can't have new URL at this moment.")
                            }
                        }
```

(The `/exists` endpoint is still queried — it gates whether audio is offered at all — but the `extension=mid` check is gone; ExoPlayer auto-detects MP3 vs MIDI.)

- [ ] **Step 4: Replace remaining `activeMediaController` references**

Replace every `activeMediaController?.` / `activeMediaController` read with `songAudioController`:
- `obtainSongProgress` (lines 184-186): replace the `val activeMediaController = activeMediaController ?: return` guard + usage with direct `songAudioController.getProgress()`.
- `onResume` (lines 356-359): `songAudioController.setUI(this, this); songAudioController.updateMediaState()`.
- `onOptionsItemSelected` long-press loop handler (line 422) and `menuMediaControl` handler (line 500): `songAudioController.playOrPause(...)`.
- `displaySong` (line 786): `if (!onCreate) songAudioController.reset()`.

Delete the now-unused `setActiveMediaController` method (lines 399-408) entirely.

- [ ] **Step 5: Delete the legacy controller files**

```bash
git rm Alkitab/src/main/java/yuku/alkitab/songs/ExoplayerController.kt \
       Alkitab/src/main/java/yuku/alkitab/songs/MidiController.kt
```

- [ ] **Step 6: Commit** (build verified in Task 9.)

```bash
git add Alkitab/src/main/java/yuku/alkitab/songs/SongViewActivity.kt \
        Alkitab/src/main/java/yuku/alkitab/songs/SongAudioController.kt
git commit -m "refactor(audio): route SongViewActivity through unified SongAudioService"
```

---

## Task 9: Manifest, notification channel, and strings

**Files:**
- Modify: `Alkitab/src/main/AndroidManifest.xml:414` (after the `BibleAudioService` `<service>` block)
- Modify: `Alkitab/src/main/java/yuku/alkitab/base/App.java:107-113`
- Modify: `Alkitab/src/main/res/values/strings.xml:583`
- Modify: `Alkitab/src/main/res/values-in/strings.xml`

- [ ] **Step 1: Declare the service in the manifest**

After the closing `</service>` of `BibleAudioService` (line 414):

```xml
		<!-- Kidung (hymn) audio: media3 MediaSessionService for MP3 + MIDI. -->
		<service
			android:name="yuku.alkitab.songs.SongAudioService"
			android:exported="true"
			android:foregroundServiceType="mediaPlayback">
			<intent-filter>
				<action android:name="androidx.media3.session.MediaSessionService" />
			</intent-filter>
		</service>
```

- [ ] **Step 2: Create the notification channel**

In `App.java`, after the `audio_bible` `createNotificationChannel(...)` call (ends line 113):

```java
        notificationManager.createNotificationChannel(
            new NotificationChannelCompat.Builder("audio_song", NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.audio_song_notification_channel_name))
                .setVibrationEnabled(false)
                .setSound(null, null)
                .build()
        );
```

- [ ] **Step 3: Add the channel-name strings**

In `Alkitab/src/main/res/values/strings.xml`, after line 583:

```xml
	<string name="audio_song_notification_channel_name">Song audio</string>
```

In `Alkitab/src/main/res/values-in/strings.xml`, add alongside the Indonesian `audio_bible_notification_channel_name`:

```xml
	<string name="audio_song_notification_channel_name">Audio kidung</string>
```

- [ ] **Step 4: Full build**

Run: `./gradlew assemblePlainDebug 2>&1 | tail -60`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run the full audio test suite**

Run: `./gradlew testPlainDebugUnitTest --tests "yuku.alkitab.base.audio.*" 2>&1 | tail -60`
Expected: PASS (coordinator + existing audio tests).

- [ ] **Step 6: Commit**

```bash
git add Alkitab/src/main/AndroidManifest.xml \
        Alkitab/src/main/java/yuku/alkitab/base/App.java \
        Alkitab/src/main/res/values/strings.xml \
        Alkitab/src/main/res/values-in/strings.xml
git commit -m "feat(audio): declare SongAudioService + audio_song notification channel"
```

---

## Task 10: Verification & manual QA checklist

- [ ] **Step 1: Full unit-test + build gate**

Run: `./gradlew testPlainDebugUnitTest testPlainReleaseUnitTest assemblePlainDebug 2>&1 | tail -60`
Expected: all PASS / BUILD SUCCESSFUL.

- [ ] **Step 2: Document manual cases that CANNOT be verified headless**

State explicitly in the PR description that playback/notification/background and MIDI sound quality were not verifiable in the sandbox. Manual cases:
1. Bible playing → start hymn MP3 ⇒ Bible stops, hymn plays + shows notification.
2. Hymn MP3 playing → start Bible ⇒ hymn stops, Bible plays.
3. Bible playing → start MIDI hymn ⇒ Bible stops, MIDI plays (and **listen**: does the JSyn synth sound acceptable vs. the old system synth?).
4. MIDI hymn playing → start Bible ⇒ MIDI stops.
5. Close app (swipe from recents) while hymn MP3 playing ⇒ keeps playing + notification persists.
6. Notification transport controls (play/pause) work for hymn audio.
7. "Play in loop" long-press still loops.
8. Hymn audio bar / toolbar play-pause icon + progress timer still update.

---

## Self-Review notes

- **Spec coverage:** Req 1 (background + notification for kidung MP3 *and* MIDI) → Tasks 6/7/9. Req 2 (code-guaranteed mutual exclusion incl. MIDI) → Tasks 1/2/6 (MIDI is inside `SongAudioService`, which acquires the coordinator — no separate MIDI owner). Bible behavior preserved → Task 2 only adds acquire/release; Task 3 adds external-stop hide.
- **Coordinator tests** cover "one stops the other", idempotent re-acquire, release semantics, and re-entrant release (the real stop→release path).
- **Type consistency:** `AudioPlaybackCoordinator.Session.stopPlayback()`, `acquire`/`release` used identically in Tasks 1/2/6. `SongAudioService.SongRequest`, `load/play/pause/setLoop/stop`, `currentPositionMs/durationMs`, `playbackState` consistent across Tasks 6/7. `SongPlaybackState` fields consistent across Tasks 5/6/7.
- **Risk flag:** `androidx.media3.decoder.midi.MidiExtractor` import path + `EXTENSION_RENDERER_MODE_ON` reflective `MidiRenderer` loading are the only unverified-until-build pieces (Task 6 note). Everything else follows existing in-repo patterns.
