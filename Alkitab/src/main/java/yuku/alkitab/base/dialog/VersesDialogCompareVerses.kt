package yuku.alkitab.base.dialog

import android.content.Context
import yuku.alkitab.base.S
import yuku.alkitab.base.model.MVersion
import yuku.alkitab.debug.R
import yuku.alkitab.model.SingleChapterVerses
import yuku.alkitab.model.Version

class VersesDialogCompareVerses(
    private val context: Context,
    private val ari: Int,
    private val mversions: MutableList<out MVersion>,
    private val displayedVersion: Array<Version?>
) : SingleChapterVerses.WithTextSizeMult {

    override val verseCount: Int
        get() = mversions.size

    override fun getVerse(verse_0: Int): String {
        // load version or take from existing if already loaded
        val mversion = mversions[verse_0]
        val loaded = displayedVersion[verse_0]

        val version: Version?
        if (loaded == null) {
            version = mversion.version
            displayedVersion[verse_0] = version
        } else {
            version = loaded
        }

        return if (version == null) {
            context.getString(R.string.version_error_opening, mversion.versionId)
        } else {
            val verseText = version.loadVerseText(ari)
            verseText ?: return context.getString(R.string.generic_verse_not_available_in_this_version)
        }
    }

    override fun getVerseNumberText(verse_0: Int): String {
        // load version or take from existing if already loaded
        val mversion = mversions[verse_0]
        val loaded = displayedVersion[verse_0]

        val version: Version?
        if (loaded == null) {
            version = mversion.version
            displayedVersion[verse_0] = version
        } else {
            version = loaded
        }

        if (version == null) {
            return "ERROR" // could not load version
        }

        return version.shortName ?:  mversion.shortName ?: version.longName ?: ""
    }

    override fun getTextSizeMult(verse_0: Int): Float {
        val mversion = mversions[verse_0]
        return S.getDb().getPerVersionSettings(mversion.versionId).fontSizeMultiplier
    }
}
