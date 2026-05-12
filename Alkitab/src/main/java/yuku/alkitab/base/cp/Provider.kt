package yuku.alkitab.base.cp

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import yuku.alkitab.base.App
import yuku.alkitab.base.config.AppConfig
import yuku.alkitab.base.model.MVersionDb
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.base.util.FormattedVerseText
import yuku.alkitab.base.util.LidToAri
import yuku.alkitab.model.Book
import yuku.alkitab.model.SingleChapterVerses
import yuku.alkitab.util.Ari
import yuku.alkitab.util.IntArrayList
import yuku.alkitabintegration.provider.VerseProvider
import java.util.Arrays
import java.util.Locale

open class Provider : ContentProvider() {
    companion object {
        private val TAG = Provider::class.java.simpleName

        private const val PATHID_bible_verses_single_by_lid = 1
        private const val PATHID_bible_verses_single_by_ari = 2
        private const val PATHID_bible_verses_range_by_lid = 3
        private const val PATHID_bible_verses_range_by_ari = 4
        private const val PATHID_bible_versions = 5

        private var uriMatcher: UriMatcher? = null
    }

    override fun attachInfo(context: Context, info: ProviderInfo) {
        super.attachInfo(context, info)

        val authority = info.authority

        if (uriMatcher == null) {
            uriMatcher = UriMatcher(UriMatcher.NO_MATCH).also { m ->
                m.addURI(authority, VerseProvider.PATH_bible_verses_single_by_lid + "#", PATHID_bible_verses_single_by_lid)
                m.addURI(authority, VerseProvider.PATH_bible_verses_single_by_ari + "#", PATHID_bible_verses_single_by_ari)
                m.addURI(authority, VerseProvider.PATH_bible_verses_range_by_lid + "*", PATHID_bible_verses_range_by_lid)
                m.addURI(authority, VerseProvider.PATH_bible_verses_range_by_ari + "*", PATHID_bible_verses_range_by_ari)
                m.addURI(authority, "bible/versions", PATHID_bible_versions)
            }
        }
    }

    override fun onCreate(): Boolean {
        yuku.afw.App.initWithAppContext(context!!.applicationContext)
        yuku.alkitab.base.App.staticInit()
        return true
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        AppLog.d(TAG, "@@query uri=$uri projection=${Arrays.toString(projection)} selection=$selection args=${Arrays.toString(selectionArgs)} sortOrder=$sortOrder")

        val uriMatch = uriMatcher!!.match(uri)
        AppLog.d(TAG, "uriMatch=$uriMatch")

        val formattingS = uri.getQueryParameter("formatting")
        val formatting = parseBoolean(formattingS)

        return when (uriMatch) {
            PATHID_bible_verses_single_by_lid -> getCursorForSingleVerseLid(Ari.parseInt(uri.lastPathSegment, Integer.MIN_VALUE), formatting)
            PATHID_bible_verses_single_by_ari -> getCursorForSingleVerseAri(Ari.parseInt(uri.lastPathSegment, Integer.MIN_VALUE), formatting)
            PATHID_bible_verses_range_by_lid -> getCursorForRangeVerseLid(decodeLidRange(uri.lastPathSegment!!), formatting)
            PATHID_bible_verses_range_by_ari -> getCursorForRangeVerseAri(decodeAriRange(uri.lastPathSegment!!), formatting)
            PATHID_bible_versions -> getCursorForBibleVersions()
            else -> null
        }.also { res ->
            AppLog.d(TAG, "returning " + (if (res == null) "null" else "cursor with ${res.count} rows"))
        }
    }

    /**
     * @return [start, end, start, end, ...]
     */
    private fun decodeLidRange(range: String): IntArrayList {
        val res = IntArrayList()

        for (split in range.split(",")) {
            val start: Int
            val end: Int
            if (split.contains('-')) {
                val startEnd = split.split("-", limit = 2)
                start = Ari.parseInt(startEnd[0], Integer.MIN_VALUE)
                end = Ari.parseInt(startEnd[1], Integer.MIN_VALUE)
            } else {
                start = Ari.parseInt(split, Integer.MIN_VALUE)
                end = start
            }

            if (start != Integer.MIN_VALUE && end != Integer.MIN_VALUE) {
                res.add(start)
                res.add(end)
            }
        }

        return res
    }

    /**
     * Also supports verse 0 for the whole chapter (0xbbcc00)
     *
     * @return [start, end, start, end, ...]
     */
    private fun decodeAriRange(range: String): IntArrayList {
        val res = IntArrayList()

        for (split in range.split(",")) {
            var start: Int
            var end: Int
            if (split.contains('-')) {
                val startEnd = split.split("-", limit = 2)
                start = Ari.parseInt(startEnd[0], Integer.MIN_VALUE)
                end = Ari.parseInt(startEnd[1], Integer.MIN_VALUE)
            } else {
                start = Ari.parseInt(split, Integer.MIN_VALUE)
                end = start
            }

            if (start != Integer.MIN_VALUE && end != Integer.MIN_VALUE) {
                start = start and 0xffffff
                end = end and 0xffffff

                if (start == end && Ari.toVerse(start) == 0) {
                    // case: 0xXXYY00 - 0xXXYY00 (whole single chapter)
                    res.add(start or 0x01)
                    res.add(start or 0xff)
                } else if (end >= start) {
                    if (Ari.toVerse(start) == 0) start = start or 0x01
                    if (Ari.toVerse(end) == 0) end = end or 0xff
                    res.add(start)
                    res.add(end)
                }
            }
        }

        return res
    }

    private fun getCursorForSingleVerseLid(lid: Int, formatting: Boolean): Cursor {
        return getCursorForSingleVerseAri(LidToAri.lidToAri(lid), formatting)
    }

    private fun getCursorForSingleVerseAri(ari: Int, formatting: Boolean): Cursor {
        val res = MatrixCursor(arrayOf("_id", VerseProvider.COLUMN_ari, VerseProvider.COLUMN_bookName, VerseProvider.COLUMN_text))

        AppLog.d(TAG, "getting ari 0x" + Integer.toHexString(ari))

        if (ari != Integer.MIN_VALUE && ari != 0) {
            val book = App.services.versions.activeVersion().getBook(Ari.toBook(ari))
            if (book != null) {
                var text = App.services.versions.activeVersion().loadVerseText(ari)
                if (text != null) {
                    if (!formatting) {
                        text = FormattedVerseText.removeSpecialCodes(text)
                    }
                    res.addRow(arrayOf<Any?>(1, ari, book.shortName, text))
                }
            }
        }

        return res
    }

    private fun getCursorForRangeVerseLid(lids: IntArrayList, formatting: Boolean): Cursor {
        val aris = IntArrayList(lids.size())
        var i = 0
        val len = lids.size()
        while (i < len) {
            val ariStart = LidToAri.lidToAri(lids.get(i))
            val ariEnd = LidToAri.lidToAri(lids.get(i + 1))
            aris.add(ariStart)
            aris.add(ariEnd)
            i += 2
        }

        return getCursorForRangeVerseAri(aris, formatting)
    }

    private fun getCursorForRangeVerseAri(ariRanges: IntArrayList, formatting: Boolean): Cursor {
        val res = MatrixCursor(arrayOf("_id", VerseProvider.COLUMN_ari, VerseProvider.COLUMN_bookName, VerseProvider.COLUMN_text))

        var c = 0
        var i = 0
        val len = ariRanges.size()
        while (i < len) {
            val ariStart = ariRanges.get(i)
            val ariEnd = ariRanges.get(i + 1)

            if (ariStart == 0 || ariEnd == 0) {
                i += 2
                continue
            }

            if (ariStart == ariEnd) {
                // case: single verse
                val ari = ariStart
                val book = App.services.versions.activeVersion().getBook(Ari.toBook(ari))
                if (book != null) {
                    var text = App.services.versions.activeVersion().loadVerseText(ari)
                    if (!formatting) {
                        text = FormattedVerseText.removeSpecialCodes(text)
                    }
                    res.addRow(arrayOf<Any?>(++c, ari, book.shortName, text))
                }
            } else {
                val ariStartBc = Ari.toBookChapter(ariStart)
                val ariEndBc = Ari.toBookChapter(ariEnd)

                if (ariStartBc == ariEndBc) {
                    // case: multiple verses in the same chapter
                    val book = App.services.versions.activeVersion().getBook(Ari.toBook(ariStart))
                    if (book != null) {
                        c += resultForOneChapter(res, book, c, ariStartBc, Ari.toVerse(ariStart), Ari.toVerse(ariEnd), formatting)
                    }
                } else {
                    // case: multiple verses in different chapters
                    var ariBc = ariStartBc
                    while (ariBc <= ariEndBc) {
                        val book = App.services.versions.activeVersion().getBook(Ari.toBook(ariBc))
                        val chapter1 = Ari.toChapter(ariBc)
                        if (book != null && chapter1 > 0 && chapter1 <= book.chapter_count) {
                            c += when (ariBc) {
                                ariStartBc -> resultForOneChapter(res, book, c, ariBc, Ari.toVerse(ariStart), 0xff, formatting)
                                ariEndBc -> resultForOneChapter(res, book, c, ariBc, 0x01, Ari.toVerse(ariEnd), formatting)
                                else -> resultForOneChapter(res, book, c, ariBc, 0x01, 0xff, formatting)
                            }
                        }
                        ariBc += 0x0100
                    }
                }
            }

            i += 2
        }

        return res
    }

    /**
     * @return number of verses put into the cursor
     */
    private fun resultForOneChapter(cursor: MatrixCursor, book: Book, lastC: Int, ariBc: Int, v1Start: Int, v1End: Int, formatting: Boolean): Int {
        val verses: SingleChapterVerses = App.services.versions.activeVersion().loadChapterText(book, Ari.toChapter(ariBc)) ?: return 0

        var count = 0
        for (v1 in v1Start..v1End) {
            val v0 = v1 - 1
            if (v0 < verses.verseCount) {
                val ari = ariBc or v1
                var text: String? = verses.getVerse(v0)
                if (!formatting) {
                    text = FormattedVerseText.removeSpecialCodes(text)
                }
                count++
                cursor.addRow(arrayOf<Any?>(lastC + count, ari, book.shortName, text))
            } else {
                break
            }
        }
        return count
    }

    private fun getCursorForBibleVersions(): Cursor {
        val res = MatrixCursor(arrayOf("_id", "type", "available", "shortName", "longName", "description"))

        val ac = AppConfig.get()
        var id = 0L
        res.addRow(arrayOf(++id, "internal", 1, ac.internalShortName, ac.internalLongName, ac.internalLongName))

        for (mvDb in App.services.storage.db.listAllVersions()) {
            res.addRow(arrayOf(++id, "yes", if (mvDb.hasDataFile()) 1 else 0, mvDb.shortName ?: mvDb.longName, mvDb.longName, mvDb.description))
        }

        return res
    }

    private fun parseBoolean(s: String?): Boolean {
        if (s == null) return false
        if (s == "0") return false
        if (s == "1") return true
        if (s == "false") return false
        if (s == "true") return true
        val lower = s.lowercase(Locale.US)
        if (lower == "false") return false
        if (lower == "true") return true
        if (lower == "no") return false
        if (lower == "yes") return true
        val n = Ari.parseInt(s, Integer.MIN_VALUE)
        if (n == 0) return false
        return n != Integer.MIN_VALUE
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException()

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = throw UnsupportedOperationException()

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = throw UnsupportedOperationException()
}
