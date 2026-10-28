# Songs

Users can install song books, browse or search their songs, read lyrics and
musical information, open scripture and media links, and play available audio.
Search can include lyrics as well as titles, with a song-book filter.

## Boundaries

The [songs package](../../Alkitab/src/main/java/yuku/alkitab/songs) owns browsing,
search, display, and song-book downloads. Song books arrive as gzipped JSON
containing portable song documents. Installed books and documents are owned by
the song database; [Storage](../storage.md) describes persistence and legacy
migration.

A song document is an ordered set of layout blocks with metadata, lyric stanzas,
refrains, and optional inline styling or links. Both display paths consume that
model: Compose is the default, with a WebView alternative available through the
legacy-song experimental setting. Appearance follows the reader's applied
settings. Copy/share and search derive text from the document rather than its
rendered HTML.

Song search opens over the song viewer and retains its search context while
users inspect songs. Playback can continue in the background; starting Bible
audio stops song audio and vice versa. See [Audio playback](audio-playback.md).

## Content and compatibility

The [song-book authoring guide](../developer/songs.md) explains content submission.
The [portable-song design](../features/portable-songs/design.md) records the JSON
format and legacy conversion rationale. Backend publishing and authoring tools
live in separate repositories; Android implementation status does not establish
that those external tools have shipped.

## Song media

Opening a song requests `/songs/media` from the backend using its book name and
code. The toolbar's Audio & video action opens a media sheet with available
audio controls and approved YouTube videos. The sheet is at most 360dp wide and
leaves a gap below the status bar. Audio supports play/pause, seeking and repeat;
playback continues through the existing song audio service after dismissal.
Video items open through an external YouTube URL intent, pausing song audio.

The server supplies video titles, channels, thumbnails, view counts with exact
or approximate precision, and ISO 8601 publication dates. Android does not call
the YouTube API. The server owns the shuffled seven-day media snapshot and CDN
cache policy. Missing counts remain absent. Loading, retry, partial-result and
empty states are shown within the sheet. Media requests are scoped to the
current song so an earlier response cannot replace a later selection.
