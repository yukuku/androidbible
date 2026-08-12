package yuku.alkitab.base.audio

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

/**
 * [ForwardingPlayer] wrapper that re-purposes the standard "skip to next/
 * previous media item" commands as **chapter** navigation for Bible audio.
 *
 * Why a forwarding player? The lock-screen and Bluetooth-headset transport
 * controls are fed by [androidx.media3.session.MediaSession], which reads the
 * wrapped [Player]'s [getAvailableCommands]. The underlying
 * [androidx.media3.exoplayer.ExoPlayer] only ever has a single MediaItem (the
 * current chapter), so its native `seekToNext` / `seekToPrevious` commands are
 * unavailable and the lock screen would show no chapter-skip buttons at all.
 *
 * Behavior:
 *  - [getAvailableCommands] always advertises both
 *    `COMMAND_SEEK_TO_NEXT[_MEDIA_ITEM]` and
 *    `COMMAND_SEEK_TO_PREVIOUS[_MEDIA_ITEM]`, and
 *    [hasNextMediaItem] / [hasPreviousMediaItem] always return `true`, so the
 *    notification provider doesn't grey the skip buttons out. At the Bible
 *    boundary the buttons stay rendered and simply no-op, matching the in-app
 *    audio bar.
 *  - All four skip overloads dispatch to the supplied callbacks rather than the
 *    inner player. This intentionally overrides media3's default semantics,
 *    where `seekToPrevious` rewinds to the start of the current item once past
 *    a few seconds; for Bible audio "skip previous" always means "previous
 *    chapter".
 *  - Everything else is delegated, including the `seekBack` / `seekForward`
 *    long-press seeks inside the current chapter.
 *
 * Threading: same constraint as the inner [Player], main thread only.
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
