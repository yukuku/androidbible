package yuku.alkitab.base

import android.content.ContentResolver
import android.content.Context
import yuku.alkitab.base.verses.VerseAttributeLoader
import yuku.alkitab.base.verses.VersesController
import yuku.alkitab.base.verses.VersesDataModel
import yuku.alkitab.model.Book
import yuku.alkitab.model.PericopeBlock
import yuku.alkitab.model.SingleChapterVerses
import yuku.alkitab.model.Version
import yuku.alkitab.util.Ari
import yuku.alkitab.util.IntArrayList

object IsiActivityUtil {
    @JvmStatic
    fun loadChapterToVersesController(
        cr: ContentResolver,
        versesController: VersesController,
        dataSetter: (VersesDataModel) -> Unit,
        version: Version,
        versionId: String,
        book: Book,
        chapter_1: Int,
        current_chapter_1: Int,
        uncheckAllVerses: Boolean
    ): Boolean {
        val verses = version.loadChapterText(book, chapter_1) ?: return false

        //# max is set to 30 (one chapter has max of 30 blocks. Already almost impossible)
        val max = 30
        val tmp_pericope_aris = IntArray(max)
        val tmp_pericope_blocks = arrayOfNulls<PericopeBlock>(max)
        val nblock = version.loadPericope(book.bookId, chapter_1, tmp_pericope_aris, tmp_pericope_blocks, max)
        val pericope_aris = tmp_pericope_aris.copyOf(nblock)
        val pericope_blocks = tmp_pericope_blocks.copyOf(nblock).map { block -> block!! }.toTypedArray()

        val retainSelectedVerses = !uncheckAllVerses && chapter_1 == current_chapter_1
        setDataWithRetainSelectedVerses(cr, versesController, dataSetter, retainSelectedVerses, Ari.encode(book.bookId, chapter_1, 0), pericope_aris, pericope_blocks, nblock, verses, version, versionId)

        return true
    }

    // Moved from the old VersesView method
    @JvmStatic
    fun setDataWithRetainSelectedVerses(
        cr: ContentResolver,
        versesController: VersesController,
        dataSetter: (VersesDataModel) -> Unit,
        retainSelectedVerses: Boolean,
        ariBc: Int,
        pericope_aris: IntArray,
        pericope_blocks: Array<PericopeBlock>,
        nblock: Int,
        verses: SingleChapterVerses,
        version: Version,
        versionId: String) {

        var selectedVerses_1: IntArrayList? = null
        if (retainSelectedVerses) {
            selectedVerses_1 = versesController.getCheckedVerses_1()
        }

        //# fill adapter with new data. make sure all checked states are reset
        versesController.uncheckAllVerses(true)

        val versesAttributes = VerseAttributeLoader.load(S.getDb(), cr, ariBc, verses)

        val newData = VersesDataModel(ariBc, verses, nblock, pericope_aris, pericope_blocks, version, versionId, versesAttributes)
        dataSetter(newData)
        versesController.versesDataModel = newData

        if (selectedVerses_1 != null) {
            versesController.checkVerses(selectedVerses_1, true)
        }
    }

    @JvmStatic
    fun reloadAttributeMapsToVerseDataModel(
        context: Context,
        data: VersesDataModel
    ): VersesDataModel {
        val versesAttributes = VerseAttributeLoader.load(
            S.getDb(),
            context.contentResolver,
            data.ari_bc_,
            data.verses_
        )

        return data.copy(versesAttributes = versesAttributes)
    }
}
