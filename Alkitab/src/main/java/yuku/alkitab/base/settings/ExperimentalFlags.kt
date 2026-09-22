package yuku.alkitab.base.settings

import yuku.afw.storage.Preferences
import yuku.alkitab.debug.R

object ExperimentalFlags {
    fun useComposeVerseItem(): Boolean =
        !Preferences.getBoolean(R.string.pref_useLegacyVerseItem_key, R.bool.pref_useLegacyVerseItem_default)

    fun useComposeToolbar(): Boolean =
        !Preferences.getBoolean(R.string.pref_useLegacyToolbar_key, R.bool.pref_useLegacyToolbar_default)

    fun useComposeGoto(): Boolean =
        !Preferences.getBoolean(R.string.pref_useLegacyGoto_key, R.bool.pref_useLegacyGoto_default)

    fun useComposeSong(): Boolean =
        !Preferences.getBoolean(R.string.pref_useLegacySong_key, R.bool.pref_useLegacySong_default)

    fun useComposeSyncLogin(): Boolean =
        !Preferences.getBoolean(R.string.pref_useLegacySyncLogin_key, R.bool.pref_useLegacySyncLogin_default)

    fun debugRubyGeometry(): Boolean =
        Preferences.getBoolean(R.string.pref_debugRubyGeometry_key, R.bool.pref_debugRubyGeometry_default)
}
