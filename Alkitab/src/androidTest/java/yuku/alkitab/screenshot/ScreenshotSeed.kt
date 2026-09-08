package yuku.alkitab.screenshot

import java.util.Date
import yuku.alkitab.base.App
import yuku.alkitab.base.util.Highlights
import yuku.alkitab.model.Marker
import yuku.alkitab.util.Ari

/**
 * Populates the database with the markers a store screenshot needs to look
 * like a used app rather than a fresh install.
 *
 * Psalm 23 is the anchor: it exists in every bundled version, reads well in
 * every translation, and is short enough that the seeded highlights and the
 * chapter opening are on screen together.
 */
object ScreenshotSeed {
    private const val PSALMS = 18
    private const val JOHN = 42

    private val HIGHLIGHT_GREEN = 0xa5d6a7
    private val HIGHLIGHT_YELLOW = 0xfff59d
    private val HIGHLIGHT_BLUE = 0x90caf9

    fun apply() {
        val db = App.services.storage.db
        val now = Date()

        clearMarkers()

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
     * Seeding is not additive: a device that ran the capture before would
     * otherwise accumulate duplicate markers on every run.
     */
    private fun clearMarkers() {
        val db = App.services.storage.db
        db.markerDao.listAll().forEach { db.markerDao.deleteById(it._id) }
    }
}
