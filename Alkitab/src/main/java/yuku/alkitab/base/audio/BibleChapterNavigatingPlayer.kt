package yuku.alkitab.base.audio

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

/**
 * [ForwardingPlayer] wrapper that re-purposes the standard "skip to next/
 * previous media item" commands as **chapter** navigation for Bible audio.
 *
 * Why a forwarding player? The lock-screen and Bluetooth-headset transport
 * controls are fed by [androidx.media3.session.MediaSession], which in turn
 * reads the wrapped [Player]'s [getAvailableCommands]. Our underlying
 * [androidx.media3.exoplayer.ExoPlayer] only ever has a single MediaItem
 * (the current chapter), so its native `seekToNext` / `seekToPrevious`
 * commands are unavailable — meaning the lock-screen would show no chapter-
 * skip buttons at all without this wrapper.
 *
 * Behavior:
 *  - [getAvailableCommands] always advertises both
 *    `COMMAND_SEEK_TO_NEXT[_MEDIA_ITEM]` and
 *    `COMMAND_SEEK_TO_PREVIOUS[_MEDIA_ITEM]`. We don't fire
 *    `Player.Listener.onAvailableCommandsChanged` at Bible boundaries because
 *    the spec calls for "invisible-not-gone" — keep the buttons rendered,
 *    no-op at Genesis 1 / Revelation 22 — same as the in-app audio bar.
 *  - [hasNextMediaItem] / [hasPreviousMediaItem] always return `true` so the
 *    notification provider doesn't grey out the skip buttons.
 *  - All four skip overloads (`seekToNext`, `seekToNextMediaItem`,
 *    `seekToPrevious`, `seekToPreviousMediaItem`) dispatch to the supplied
 *    callbacks rather than acting on the inner player. This intentionally
 *    overrides the default media3 semantics (where `seekToPrevious` rewinds
 *    to the start of the current item if past 3s) — for Bible audio, "skip
 *    previous" should always mean "previous chapter".
 *  - Everything else is delegated to the inner player. This includes the
 *    `seekBack` / `seekForward` long-press behaviors (default 5s/15s seek
 *    inside the current chapter) which we deliberately want to keep as-is.
 *
 * Threading: same constraints as the inner [Player] — main thread only.
 */
class BibleChapterNavigatingPlayer(
    inner: Player,
    private val onSeekToNextChapter: () -> Unit,
    private val onSeekToPrevChapter: () -> Unit,
) : ForwardingPlayer(inner) {

    override fun getAvailableCommands(): Player.Commands {
        return super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()
    }

    override fun isCommandAvailable(command: Int): Boolean {
        return when (command) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            -> true
            else -> super.isCommandAvailable(command)
        }
    }

    override fun hasNextMediaItem(): Boolean = true

    override fun hasPreviousMediaItem(): Boolean = true

    override fun seekToNext() {
        onSeekToNextChapter()
    }

    override fun seekToNextMediaItem() {
        onSeekToNextChapter()
    }

    override fun seekToPrevious() {
        onSeekToPrevChapter()
    }

    override fun seekToPreviousMediaItem() {
        onSeekToPrevChapter()
    }
}
