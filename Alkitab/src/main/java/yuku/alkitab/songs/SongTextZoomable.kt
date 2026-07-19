package yuku.alkitab.songs

/**
 * A song-display fragment whose text size responds to the two-finger pinch
 * gesture handled by [yuku.alkitab.base.widget.TwofingerLinearLayout] in
 * [SongViewActivity]. Implemented by both the legacy WebView [SongFragment]
 * (mapping to the WebView text zoom) and the experimental Compose
 * [SongComposeFragment] (scaling the Compose font sizes).
 */
interface SongTextZoomable {
    /** Text zoom as a percentage; 100 is the unzoomed size. */
    var songTextZoomPercent: Int
}
