package yuku.alkitab.base.settings

import yuku.afw.storage.Preferences
import yuku.alkitab.debug.R

object ExperimentalFlags {
    fun useComposeVerseItem(): Boolean =
        Preferences.getBoolean(R.string.pref_useComposeVerseItem_key, R.bool.pref_useComposeVerseItem_default)
}
