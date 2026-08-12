package yuku.alkitab.base.audio

/**
 * media3's [androidx.media3.common.PlaybackException.errorCodeName] values are
 * all prefixed `ERROR_CODE_` (e.g. `ERROR_CODE_IO_NETWORK_CONNECTION_FAILED`).
 * The prefix is redundant in every place this app shows the code to a user, so
 * strip it: the status line and log then read `IO_NETWORK_CONNECTION_FAILED`.
 */
internal fun stripErrorCodePrefix(errorCodeName: String): String =
    errorCodeName.removePrefix("ERROR_CODE_")
