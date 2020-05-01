package yuku.alkitab.base.verses

import android.content.ContentResolver
import android.net.Uri
import yuku.alkitab.base.App
import yuku.alkitab.base.storage.InternalDb
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.base.util.Highlights
import yuku.alkitab.base.widget.AttributeView
import yuku.alkitab.model.SingleChapterVerses
import yuku.alkitab.util.Ari

private const val TAG = "VerseAttributeLoader"

object VerseAttributeLoader {
    @JvmStatic
    fun load(db: InternalDb, contentResolver: ContentResolver, ari_bc: Int, verses: SingleChapterVerses): VersesAttributes {
        // book_ can be empty when the selected (book, chapter) is not available in this version
        if (ari_bc == 0) return VersesAttributes.createEmpty(verses.verseCount)
        val verseCount = verses.verseCount

        // 1/3: Bookmarks/Notes/Highlights
        val bookmarkCountMap = IntArray(verseCount)
        val noteCountMap = IntArray(verseCount)
        val highlightColorMap: Array<Highlights.Info?> = arrayOfNulls(verseCount)

        if (db.countMarkersForBookChapter(ari_bc) > 0) {
            db.putAttributes(ari_bc, bookmarkCountMap, noteCountMap, highlightColorMap)
        }

        val ariMin = ari_bc and 0xffff00
        val ariMax = ari_bc or 0x0000ff

        // 2/3: Progress marks
        val progressMarkBitsMap = IntArray(verseCount)
        for (progressMark in db.listAllProgressMarks()) {
            val ari = progressMark.ari
            if (ari < ariMin || ari >= ariMax) {
                continue
            }

            val mapOffset = Ari.toVerse(ari) - 1
            if (mapOffset >= progressMarkBitsMap.size) {
                AppLog.e(TAG, "(for progressMarkBitsMap:) mapOffset out of bounds: " + mapOffset + " happened on ari 0x" + Integer.toHexString(ari))
            } else {
                progressMarkBitsMap[mapOffset] = progressMarkBitsMap[mapOffset] or (1 shl progressMark.preset_id + AttributeView.PROGRESS_MARK_BITS_START)
            }
        }

        // 3/3: Location indicators
        // Look up for maps locations.
        // If the app is installed, query its content provider to see which verses has locations on the map.
        val hasMapsMap = BooleanArray(verseCount)
        contentResolver.query(Uri.parse("content://palki.maps/exists?ari=$ari_bc"), null, null, null, null)?.use { c ->
            if (c.moveToNext()) {
                val col_aris = c.getColumnIndexOrThrow("aris")
                val aris_json = c.getString(col_aris)
                val aris = App.getDefaultGson().fromJson(aris_json, IntArray::class.java)

                if (aris != null) {
                    for (ari in aris) {
                        val mapOffset = Ari.toVerse(ari) - 1
                        if (mapOffset >= hasMapsMap.size) {
                            AppLog.e(TAG, "(for hasMapsMap:) mapOffset out of bounds: " + mapOffset + " happened on ari 0x" + Integer.toHexString(ari))
                        } else {
                            hasMapsMap[mapOffset] = true
                        }
                    }
                }
            }
        }

        // Finish calculating
        return VersesAttributes(
            bookmarkCountMap_ = bookmarkCountMap,
            noteCountMap_ = noteCountMap,
            highlightInfoMap_ = highlightColorMap,
            progressMarkBitsMap_ = progressMarkBitsMap,
            hasMapsMap_ = hasMapsMap
        )
    }
}
