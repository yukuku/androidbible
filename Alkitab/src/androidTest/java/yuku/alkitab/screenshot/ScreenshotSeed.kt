package yuku.alkitab.screenshot

import androidx.test.platform.app.InstrumentationRegistry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.S
import yuku.alkitab.base.ac.DevotionActivity
import yuku.alkitab.base.devotion.ArticleMeidA
import yuku.alkitab.base.devotion.ArticleMorningEveningEnglish
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.util.Highlights
import yuku.alkitab.base.util.ReadingPlanManager
import yuku.alkitab.debug.BuildConfig
import yuku.alkitab.model.Marker
import yuku.alkitab.songs.SongBookUtil
import yuku.alkitab.songs.newdoc.Line
import yuku.alkitab.songs.newdoc.LyricBlock
import yuku.alkitab.songs.newdoc.Meta
import yuku.alkitab.songs.newdoc.PBlock
import yuku.alkitab.songs.newdoc.SongDocument
import yuku.alkitab.songs.newdoc.Verse
import yuku.alkitab.songs.newdoc.VerseKind
import yuku.alkitab.songs.newdoc.VerseLine
import yuku.alkitab.util.Ari

/**
 * Fills the app with the content a store screenshot needs. Devotions, song
 * books and reading plans are normally downloaded, so without this the screens
 * that show them are empty download prompts.
 *
 * Psalm 23 is the anchor for the markers: it exists in every bundled version,
 * reads well in every translation, and is short enough that the seeded
 * highlights and the chapter opening are on screen together.
 */
object ScreenshotSeed {
    private const val PSALMS = 18
    private const val JOHN = 42

    private const val HIGHLIGHT_GREEN = 0xa5d6a7
    private const val HIGHLIGHT_YELLOW = 0xfff59d
    private const val HIGHLIGHT_BLUE = 0x90caf9

    private const val READING_PLAN = "esv_mcheyne"
    private const val SONG_BOOK = "Hymns"

    /** The internal version's preset is the flavor's language in practice. */
    private val indonesian: Boolean
        get() = BuildConfig.INTERNAL_VERSION_PRESET_NAME.startsWith("in-")

    fun apply() {
        seedMarkers()
        seedDevotion()
        seedReadingPlan()
        seedSongs()
    }

    private fun seedMarkers() {
        val db = App.services.storage.db
        // Seeding is not additive: a device that ran the capture before would
        // otherwise accumulate duplicates on every run.
        db.markerDao.listAll().forEach { db.markerDao.deleteById(it._id) }

        val now = Date()
        db.insertMarker(Ari.encode(PSALMS, 23, 1), Marker.Kind.highlight, Highlights.encode(HIGHLIGHT_GREEN), 2, now, now)
        db.insertMarker(Ari.encode(PSALMS, 23, 4), Marker.Kind.highlight, Highlights.encode(HIGHLIGHT_YELLOW), 1, now, now)
        db.insertMarker(Ari.encode(JOHN, 3, 16), Marker.Kind.highlight, Highlights.encode(HIGHLIGHT_BLUE), 1, now, now)

        db.insertMarker(Ari.encode(PSALMS, 23, 1), Marker.Kind.bookmark, "Psalm 23", 1, now, now)
        db.insertMarker(Ari.encode(JOHN, 3, 16), Marker.Kind.bookmark, "John 3:16", 1, now, now)
        db.insertMarker(Ari.encode(PSALMS, 121, 1), Marker.Kind.bookmark, "Psalm 121", 1, now, now)

        db.insertMarker(
            Ari.encode(PSALMS, 23, 4),
            Marker.Kind.note,
            "Even the darkest valley is a place we walk through, not a place we stay.",
            1, now, now,
        )
        db.insertMarker(
            Ari.encode(JOHN, 3, 16),
            Marker.Kind.note,
            "See also Rom 5:8 and 1 John 4:9.",
            1, now, now,
        )
    }

    /**
     * The article has to carry today's date, because `DevotionActivity` opens
     * on today and would start a download for any other day.
     */
    private fun seedDevotion() {
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val kind = if (indonesian) DevotionActivity.DevotionKind.MEID_A else DevotionActivity.DevotionKind.ME_EN
        val article = if (indonesian) {
            ArticleMeidA(today, DEVOTION_BODY_INDONESIAN, true)
        } else {
            ArticleMorningEveningEnglish(today, DEVOTION_BODY_ENGLISH, true)
        }
        App.services.storage.db.storeArticleToDevotions(article)
        Preferences.setString(Prefkey.devotion_last_kind_name, kind.name)
    }

    private fun seedReadingPlan() {
        val db = App.services.storage.db
        db.listAllReadingPlanInfo().forEach { db.deleteReadingPlanById(it.id) }

        // The fixture lives in the test APK's assets, so it is read from the
        // instrumentation context rather than the app's.
        val data = InstrumentationRegistry.getInstrumentation().context.assets
            .open("$READING_PLAN.rpb")
            .use { it.readBytes() }
        ReadingPlanManager.insertReadingPlanToDb(data, READING_PLAN)

        // A plan with nothing ticked looks unused; ticking the first few days
        // gives the progress bar something to show.
        for (day in 0 until 5) {
            for (sequence in 0 until 4) {
                ReadingPlanManager.updateReadingPlanProgress(READING_PLAN, day, sequence, true)
            }
        }
    }

    private fun seedSongs() {
        val songDb = S.songDb
        songDb.deleteSongBook(SONG_BOOK)
        songDb.insertSongBookInfo(
            SongBookUtil.SongBookInfo().apply {
                name = SONG_BOOK
                title = SONG_BOOK
                copyright = "Public domain"
            }
        )
        songDb.storeSongs(SONG_BOOK, SONGS)
        Preferences.setString(Prefkey.song_last_bookName, SONG_BOOK)
        Preferences.setString(Prefkey.song_last_code, SONGS[0].code)
    }

    /**
     * One [LyricBlock] holds the whole song, so the verses render as a single
     * numbered run. A refrain is passed separately because it is a different
     * [VerseKind] and the renderer labels it rather than numbering it.
     */
    private fun hymn(
        code: String,
        title: String,
        author: String,
        verses: List<List<String>>,
        refrain: List<String>?,
    ) = SongDocument(
        code = code,
        meta = Meta(title = title),
        blocks = listOf(
            PBlock(role = "author", content = Line.of(author)),
            LyricBlock(
                verses = buildList {
                    verses.forEachIndexed { index, lines ->
                        add(
                            Verse(
                                kind = VerseKind.NORMAL,
                                marker = (index + 1).toString(),
                                lines = lines.map { VerseLine.Simple(Line.of(it)) },
                            )
                        )
                    }
                    if (refrain != null) {
                        add(
                            Verse(
                                kind = VerseKind.REFRAIN,
                                marker = null,
                                lines = refrain.map { VerseLine.Simple(Line.of(it)) },
                            )
                        )
                    }
                },
            ),
        ),
    )

    private val SONGS by lazy {
        listOf(
            hymn(
                "1", "Amazing Grace", "John Newton, 1779",
                listOf(
                    listOf(
                        "Amazing grace! how sweet the sound,",
                        "That saved a wretch like me!",
                        "I once was lost, but now am found,",
                        "Was blind, but now I see.",
                    ),
                    listOf(
                        "'Twas grace that taught my heart to fear,",
                        "And grace my fears relieved;",
                        "How precious did that grace appear",
                        "The hour I first believed!",
                    ),
                    listOf(
                        "Through many dangers, toils and snares,",
                        "I have already come;",
                        "'Tis grace hath brought me safe thus far,",
                        "And grace will lead me home.",
                    ),
                ),
                null,
            ),
            hymn(
                "2", "Be Thou My Vision", "Irish, 8th century",
                listOf(
                    listOf(
                        "Be Thou my vision, O Lord of my heart;",
                        "Naught be all else to me, save that Thou art.",
                        "Thou my best thought, by day or by night,",
                        "Waking or sleeping, Thy presence my light.",
                    ),
                ),
                null,
            ),
            hymn(
                "3", "Holy, Holy, Holy", "Reginald Heber, 1826",
                listOf(
                    listOf(
                        "Holy, holy, holy! Lord God Almighty!",
                        "Early in the morning our song shall rise to Thee.",
                        "Holy, holy, holy! merciful and mighty!",
                        "God in three Persons, blessed Trinity!",
                    ),
                ),
                null,
            ),
            hymn(
                "4", "It Is Well With My Soul", "Horatio Spafford, 1873",
                listOf(
                    listOf(
                        "When peace like a river attendeth my way,",
                        "When sorrows like sea billows roll;",
                        "Whatever my lot, Thou hast taught me to say,",
                        "It is well, it is well with my soul.",
                    ),
                ),
                listOf(
                    "It is well with my soul,",
                    "It is well, it is well with my soul.",
                ),
            ),
        )
    }

    private const val DEVOTION_BODY_ENGLISH = """
        <b>"He restoreth my soul." — Psalm 23:3</b>
        <p>The believer's soul is not always at the same pitch. There are
        seasons when the harp is unstrung, when the hand hangs down, and the
        song is silent. Yet the Shepherd does not leave the wandering sheep to
        perish on the mountain. He restores.</p>
        <p>Notice that He does not say the soul is replaced, but restored. The
        same soul that fainted is the soul that is revived. Grace does not
        discard what sin has damaged; it repairs it, and often the mended
        vessel carries more than the whole one ever did.</p>
        <p>Are you low today? Then here is your comfort: restoration is His
        work, not yours. Lie still in the green pastures and let the Shepherd
        do what only He can do.</p>
    """

    private const val DEVOTION_BODY_INDONESIAN = """
        <b>"Ia menyegarkan jiwaku." — Mazmur 23:3</b>
        <p>Jiwa orang percaya tidak selalu berada dalam keadaan yang sama. Ada
        masa ketika kecapi tidak berdawai, tangan terkulai, dan nyanyian
        terhenti. Namun Sang Gembala tidak membiarkan domba-Nya yang tersesat
        binasa di gunung. Ia menyegarkan.</p>
        <p>Perhatikanlah, Ia tidak berkata bahwa jiwa itu diganti, melainkan
        disegarkan. Jiwa yang tadinya lemah itu jugalah yang dipulihkan.
        Anugerah tidak membuang apa yang telah dirusak dosa; anugerah
        memperbaikinya.</p>
        <p>Apakah hari ini Anda sedang lemah? Inilah penghiburan bagi Anda:
        pemulihan adalah pekerjaan-Nya, bukan pekerjaan Anda. Berbaringlah
        dengan tenang di padang yang berumput hijau.</p>
    """
}
