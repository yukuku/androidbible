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
