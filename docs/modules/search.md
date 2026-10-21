# Bible search

Search finds verses in a selected Bible version, with filters for a book or
testament. Results show matching text and open the passage in the reader.
Recent queries are available as suggestions.

## Query behavior

- Space-separated terms use AND matching: all terms must occur in the verse.
- Ordinary terms match substrings, so `love` can match `beloved`.
- A leading `+` requests whole-word matching, as in `+love`.
- Quoted phrases and consecutive plus-prefixed words match a word sequence.
  Phrase matching tolerates punctuation and supported inline formatting between
  words; it does not span verses.

Matching is case-insensitive with locale-independent normalization. Query and
Bible-text normalization must agree, including for legacy file encodings.
Visible text and inline formatting are handled separately so rendering tags do
not become words in a phrase.

## Ownership

`SearchActivity` owns the UI. `QueryTokenizer` and `SearchEngine` in the
[utilities package](../../Alkitab/src/main/java/yuku/alkitab/base/util) define
query interpretation and verse matching. Their tests are the detailed behavior
contract; this guide does not prescribe token ordering or scan optimizations.

Song search uses song documents and belongs to the [Songs](songs.md) feature.
Verse snippets follow the [shared rendering pipeline](../text-rendering.md).
