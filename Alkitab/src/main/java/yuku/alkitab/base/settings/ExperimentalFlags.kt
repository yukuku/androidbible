package yuku.alkitab.base.settings

import yuku.afw.storage.Preferences
import yuku.alkitab.debug.R

object ExperimentalFlags {
    fun useComposeVerseItem(): Boolean =
        Preferences.getBoolean(R.string.pref_useComposeVerseItem_key, R.bool.pref_useComposeVerseItem_default)

    fun useComposeGoto(): Boolean =
        Preferences.getBoolean(R.string.pref_useComposeGoto_key, R.bool.pref_useComposeGoto_default)

    fun useComposeSong(): Boolean =
        Preferences.getBoolean(R.string.pref_useComposeSong_key, R.bool.pref_useComposeSong_default)
}
