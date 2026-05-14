package yuku.alkitab.base.storage.room

import android.app.Application
import android.content.ContentValues
import androidx.room.Room
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import yuku.alkitab.base.storage.Db
import yuku.alkitab.base.storage.InternalDbHelper

/**
 * Load test for the REM-10 / REM-11 migrations. Simulates a 2025-era app
 * upgrading to the current build: the legacy `AlkitabDb` already has the
 * `Version` / `Marker` / `Label` / `Marker_Label` tables populated, and the
 * new `AlkitabRoomDb` does not exist yet. On first launch Room creates the
 * fresh database at the latest schema version, then
 * [VersionDataMigration] and [MarkerDataMigration] each do a one-time copy.
 *
 * The harness reuses exactly the same code path the real upgrade hits — there
 * is no Android-version-specific behaviour in either migration, so an
 * in-Robolectric run is a faithful proxy for what a v1 device sees.
 *
 * Dataset:
 *  - 100 versions
 *  - 100 labels
 *  - 50,000 markers, each with a 2,048-byte caption (≈100 MB of caption
 *    bytes — exercises the read-everything-into-an-ArrayList shape of
 *    [MarkerDataMigration.readLegacyMarkers] under memory pressure)
 *  - 60,000 marker_label associations
 *
 * Verifiable pattern: every field on every entity is a pure function of the
 * row's integer index `i` (or `j` for associations). The seed and the
 * verifier both call the same `*Pattern(i)` helpers, so a regression in the
 * migration is the only thing that can make verification disagree with the
 * seed.
 *
 * Association layout: for `j` in `0..49,999` the j-th association links
 * marker `j` to label `j % 100`, so every marker has at least one label and
 * every label has 500 markers from this first pass. For `j` in
 * `50,000..59,999` the j-th association links marker `j - 50,000` to label
 * `(j + 31) % 100`. Because `gcd(31, 100) = 1` and `31 mod 100 != 0`, the
 * offset always lands on a *different* label than the first-pass label for
 * that marker — so markers 0..9,999 each end up with exactly two distinct
 * labels and markers 10,000..49,999 each end up with one. The test asserts
 * this invariant on marker 0 (two labels) and marker 30,000 (one label) to
 * prove the join survives the migration.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class MarkerDataMigrationLoadTest {
    private lateinit var legacy: InternalDbHelper
    private lateinit var room: AppDatabase

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        yuku.afw.App.context = app
        legacy = InternalDbHelper(app)
        room = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        room.close()
        legacy.close()
    }

    @Test
    fun `migrating 100 versions + 50000 markers + 100 labels + 60000 marker_labels round-trips every field`() {
        // -- 1. Seed the legacy DB to simulate a 2025-era install --------
        val seedStart = System.currentTimeMillis()
        seedVersions()
        seedLabels()
        seedMarkers()
        seedMarkerLabels()
        val seedMs = System.currentTimeMillis() - seedStart
        log("seeded legacy DB in ${seedMs}ms — " +
            "$N_VERSIONS versions, $N_LABELS labels, $N_MARKERS markers " +
            "(${CAPTION_BYTES} bytes each), $N_MARKER_LABELS marker_labels")

        // -- 2. Run the migrations (this is what first-launch does) ------
        val migrateStart = System.currentTimeMillis()
        VersionDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        MarkerDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        val migrateMs = System.currentTimeMillis() - migrateStart
        log("ran VersionDataMigration + MarkerDataMigration in ${migrateMs}ms")

        // -- 3. Counts match exactly -------------------------------------
        assertEquals("version count", N_VERSIONS, room.versionDao().count())
        assertEquals("label count", N_LABELS, room.labelDao().count())
        assertEquals("marker count", N_MARKERS, room.markerDao().count())
        assertEquals("marker_label count", N_MARKER_LABELS, room.markerLabelDao().count())

        // -- 4. Sampled deep field-by-field round-trip -------------------
        verifyVersionsByPattern()
        verifyLabelsByPattern()
        verifyMarkersByPattern()
        verifyMarkerLabelsByPattern()

        // -- 5. Idempotency: re-running the migration is a no-op --------
        VersionDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        MarkerDataMigration.copyFromLegacyDbIfNeeded(room, legacy)
        assertEquals("version count after re-run", N_VERSIONS, room.versionDao().count())
        assertEquals("label count after re-run", N_LABELS, room.labelDao().count())
        assertEquals("marker count after re-run", N_MARKERS, room.markerDao().count())
        assertEquals(
            "marker_label count after re-run",
            N_MARKER_LABELS,
            room.markerLabelDao().count(),
        )

        log("verification passed; total wall time ${System.currentTimeMillis() - seedStart}ms")
    }

    // ---- Seed helpers --------------------------------------------------
    //
    // All four seed helpers wrap their inserts in a single legacy SQLite
    // transaction. SQLite's default per-statement journal flush would make
    // 50,000 individual inserts take O(minutes); batching them keeps the
    // seed phase to a few seconds.

    private fun seedVersions() {
        val db = legacy.writableDatabase
        db.beginTransaction()
        try {
            val cv = ContentValues()
            for (i in 0 until N_VERSIONS) {
                cv.clear()
                cv.put(Db.Version.locale, versionLocale(i))
                cv.put(Db.Version.shortName, versionShortName(i))
                cv.put(Db.Version.longName, versionLongName(i))
                cv.put(Db.Version.description, versionDescription(i))
                cv.put(Db.Version.filename, versionFilename(i))
                cv.put(Db.Version.preset_name, versionPresetName(i))
                cv.put(Db.Version.modifyTime, versionModifyTime(i))
                cv.put(Db.Version.active, versionActive(i))
                cv.put(Db.Version.ordering, versionOrdering(i))
                db.insert(Db.TABLE_Version, null, cv)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun seedLabels() {
        val db = legacy.writableDatabase
        db.beginTransaction()
        try {
            val cv = ContentValues()
            for (i in 0 until N_LABELS) {
                cv.clear()
                cv.put(Db.Label.gid, labelGid(i))
                cv.put(Db.Label.title, labelTitle(i))
                cv.put(Db.Label.ordering, labelOrdering(i))
                cv.put(Db.Label.backgroundColor, labelBgColor(i))
                db.insert(Db.TABLE_Label, null, cv)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun seedMarkers() {
        val db = legacy.writableDatabase
        db.beginTransaction()
        try {
            val cv = ContentValues()
            for (i in 0 until N_MARKERS) {
                cv.clear()
                cv.put(Db.Marker.gid, markerGid(i))
                cv.put(Db.Marker.ari, markerAri(i))
                cv.put(Db.Marker.kind, markerKind(i))
                cv.put(Db.Marker.caption, markerCaption(i))
                cv.put(Db.Marker.verseCount, markerVerseCount(i))
                cv.put(Db.Marker.createTime, markerCreateTime(i))
                cv.put(Db.Marker.modifyTime, markerModifyTime(i))
                db.insert(Db.TABLE_Marker, null, cv)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun seedMarkerLabels() {
        val db = legacy.writableDatabase
        db.beginTransaction()
        try {
            val cv = ContentValues()
            for (j in 0 until N_MARKER_LABELS) {
                cv.clear()
                cv.put(Db.Marker_Label.gid, mlGid(j))
                cv.put(Db.Marker_Label.marker_gid, mlMarkerGid(j))
                cv.put(Db.Marker_Label.label_gid, mlLabelGid(j))
                db.insert(Db.TABLE_Marker_Label, null, cv)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ---- Verify helpers ------------------------------------------------

    private fun verifyVersionsByPattern() {
        // versionDao().listAll() returns rows ordered by `ordering ASC`,
        // and versionOrdering(i) = ORDERING_BASE + i is strictly increasing
        // in i, so position-in-list maps directly to seed index.
        val all = room.versionDao().listAll()
        assertEquals(N_VERSIONS, all.size)
        for (i in versionSampleIndices()) {
            val row = all[i]
            assertEquals("version $i filename", versionFilename(i), row.filename)
            assertEquals("version $i preset_name", versionPresetName(i), row.preset_name)
            assertEquals("version $i locale", versionLocale(i), row.locale)
            assertEquals("version $i shortName", versionShortName(i), row.shortName)
            assertEquals("version $i longName", versionLongName(i), row.longName)
            assertEquals("version $i description", versionDescription(i), row.description)
            assertEquals("version $i modifyTime", versionModifyTime(i), row.modifyTime)
            assertEquals("version $i active", versionActive(i), row.active)
            assertEquals("version $i ordering", versionOrdering(i), row.ordering)
        }
    }

    private fun verifyLabelsByPattern() {
        val byGid = room.labelDao().listAll().associateBy { it.gid }
        assertEquals(N_LABELS, byGid.size)
        for (i in labelSampleIndices()) {
            val gid = labelGid(i)
            val row = byGid[gid] ?: error("label gid $gid missing from Room after migration")
            assertEquals("label $i title", labelTitle(i), row.title)
            assertEquals("label $i ordering", labelOrdering(i), row.ordering)
            assertEquals("label $i backgroundColor", labelBgColor(i), row.backgroundColor)
        }
    }

    private fun verifyMarkersByPattern() {
        for (i in markerSampleIndices()) {
            val gid = markerGid(i)
            val row = room.markerDao().findByGid(gid)
            assertNotNull("marker gid $gid missing from Room after migration", row)
            row!!
            assertEquals("marker $i ari", markerAri(i), row.ari)
            assertEquals("marker $i kind", markerKind(i), row.kind)
            // Caption length-then-content lets a length mismatch fail fast
            // instead of dumping the full 2KB diff on assertion failure.
            assertEquals(
                "marker $i caption length",
                CAPTION_BYTES,
                row.caption!!.length,
            )
            assertEquals("marker $i caption", markerCaption(i), row.caption)
            assertEquals("marker $i verseCount", markerVerseCount(i), row.verseCount)
            assertEquals("marker $i createTime", markerCreateTime(i), row.createTime)
            assertEquals("marker $i modifyTime", markerModifyTime(i), row.modifyTime)
        }
    }

    private fun verifyMarkerLabelsByPattern() {
        for (j in mlSampleIndices()) {
            val gid = mlGid(j)
            val row = room.markerLabelDao().findByGid(gid)
            assertNotNull("marker_label gid $gid missing from Room after migration", row)
            row!!
            assertEquals("ml $j marker_gid", mlMarkerGid(j), row.marker_gid)
            assertEquals("ml $j label_gid", mlLabelGid(j), row.label_gid)
        }

        // The "first 10,000 markers have 2 labels, the rest have 1" invariant
        // is what makes the marker_label sampling above non-trivial: if Room
        // miscounted or deduped silently, these joins would return the wrong
        // size.
        assertEquals(
            "marker 0 should appear in exactly 2 marker_label rows",
            2,
            room.markerLabelDao().listByMarkerGid(markerGid(0)).size,
        )
        assertEquals(
            "marker 9,999 should appear in exactly 2 marker_label rows",
            2,
            room.markerLabelDao().listByMarkerGid(markerGid(9_999)).size,
        )
        assertEquals(
            "marker 10,000 should appear in exactly 1 marker_label row",
            1,
            room.markerLabelDao().listByMarkerGid(markerGid(10_000)).size,
        )
        assertEquals(
            "marker 30,000 should appear in exactly 1 marker_label row",
            1,
            room.markerLabelDao().listByMarkerGid(markerGid(30_000)).size,
        )
        assertEquals(
            "marker 49,999 should appear in exactly 1 marker_label row",
            1,
            room.markerLabelDao().listByMarkerGid(markerGid(49_999)).size,
        )

        // Two-label markers must have two *distinct* labels (the +31 offset
        // is the whole point of the dataset layout).
        val twoLabels = room.markerLabelDao()
            .listByMarkerGid(markerGid(0))
            .map { it.label_gid }
            .toSet()
        assertEquals("marker 0's two label_gids must be distinct", 2, twoLabels.size)
    }

    // ---- Sample-index sets --------------------------------------------
    //
    // First/last/middle plus a handful of "random-looking" indices to
    // catch off-by-one and stride bugs without iterating the full 50K.

    private fun versionSampleIndices() =
        listOf(0, 1, N_VERSIONS / 2, N_VERSIONS - 1, 7, 42, 73, 99)

    private fun labelSampleIndices() =
        listOf(0, 1, N_LABELS / 2, N_LABELS - 1, 7, 31, 88)

    private fun markerSampleIndices() = listOf(
        0, 1, 2,
        1_234,
        N_MARKERS / 4,
        N_MARKERS / 2,
        N_MARKERS * 3 / 4,
        25_000,
        33_333,
        N_MARKERS - 1,
    )

    private fun mlSampleIndices() = listOf(
        0, 1, 2,
        N_MARKERS - 1,         // last "one label per marker" association
        N_MARKERS,             // first "second label" association
        N_MARKERS + 5_000,
        N_MARKER_LABELS - 1,
        N_MARKER_LABELS / 2,
    )

    // ---- Pattern functions (single source of truth: seed AND verify
    //      derive every field from these, so they cannot disagree) -----

    // Version pattern ---------------------------------------------------
    private fun versionFilename(i: Int) = fmt("/data/version-%03d.yes", i)
    private fun versionPresetName(i: Int): String? =
        if (i % 7 == 0) null else fmt("preset-%03d", i)

    private fun versionLocale(i: Int): String? =
        if (i % 11 == 0) null else fmt("en-%d", i % 10)

    private fun versionShortName(i: Int) = fmt("V%03d", i)
    private fun versionLongName(i: Int) = fmt("Version Long Name %03d", i)
    private fun versionDescription(i: Int) = fmt("Description for version %03d", i)
    private fun versionModifyTime(i: Int) = 1_700_000_000 + i
    private fun versionActive(i: Int) = i % 2
    private fun versionOrdering(i: Int) = ORDERING_BASE + i

    // Label pattern -----------------------------------------------------
    private fun labelGid(i: Int) = fmt("L%05d", i)
    private fun labelTitle(i: Int) = fmt("Label %d", i)
    private fun labelOrdering(i: Int) = i + 1
    private fun labelBgColor(i: Int): String? =
        if (i % 4 == 0) null else fmt("#%06x", i * 0x10101 and 0xffffff)

    // Marker pattern ----------------------------------------------------
    private fun markerGid(i: Int) = fmt("M%08d", i)
    private fun markerAri(i: Int): Int {
        // Spread across books 0..65, chapters 1..100, verses 1..50 — every
        // ARI is a distinct, deterministic int. Values don't need to point
        // at real Bible verses for the migration test.
        val book = i % 66
        val chapter = 1 + (i / 66) % 100
        val verse = 1 + (i / 6_600) % 50
        return (book shl 16) or (chapter shl 8) or verse
    }

    private fun markerKind(i: Int) = 1 + (i % 3) // 1 = bookmark, 2 = note, 3 = highlight
    private fun markerCaption(i: Int): String {
        val header = fmt("M%08d|", i)
        val footer = fmt("|END%08d", i)
        val filler = "ABCDEFGHIJ"[i % 10].toString()
        val midLength = CAPTION_BYTES - header.length - footer.length
        return header + filler.repeat(midLength) + footer
    }

    private fun markerVerseCount(i: Int) = 1 + (i % 5)
    private fun markerCreateTime(i: Int) = 1_700_000_000 + i
    private fun markerModifyTime(i: Int) = 1_700_000_000 + i * 2

    // Marker_Label pattern ---------------------------------------------
    private fun mlGid(j: Int) = fmt("ML%08d", j)
    private fun mlMarkerGid(j: Int) = markerGid(j % N_MARKERS)
    private fun mlLabelGid(j: Int): String {
        // First pass: j in 0..N_MARKERS-1 → label = j % N_LABELS. Every
        // marker is associated with its index-mod-100 label.
        //
        // Second pass: j in N_MARKERS..N_MARKER_LABELS-1 → label =
        // (j + 31) % N_LABELS. The +31 offset is coprime to 100 and
        // non-zero mod 100, so the second-pass label is guaranteed
        // distinct from the first-pass label for the same marker. This
        // gives markers 0..9,999 two distinct labels each, exercising the
        // many-to-many junction.
        val labelIdx = if (j < N_MARKERS) j % N_LABELS else (j + 31) % N_LABELS
        return labelGid(labelIdx)
    }

    private fun fmt(template: String, vararg args: Any?): String =
        String.format(Locale.US, template, *args)

    private fun log(message: String) {
        println("[MarkerDataMigrationLoadTest] $message")
    }

    private companion object {
        const val N_VERSIONS = 100
        const val N_LABELS = 100
        const val N_MARKERS = 50_000
        const val N_MARKER_LABELS = 60_000

        // Each marker caption is exactly this many UTF-16 code units. With
        // the legacy SQLite schema's `caption text` column the bytes-on-disk
        // value depends on encoding, but the round-trip we care about is
        // String → ContentValues → SQLite → cursor.getString() →
        // MarkerEntity.caption being exactly the same String we put in.
        const val CAPTION_BYTES = 2_048

        // Versions are ordered by `ordering ASC` after migration; keeping
        // the base high makes the test resilient to a future change that
        // pre-inserts a few rows (e.g. presets) ahead of the seeded set.
        const val ORDERING_BASE = 100
    }
}
