# Bookmarks, notes, and highlights

Markers attach user content to Bible passages. Bookmarks save places, notes
hold text with clickable verse references, and highlights mark whole verses or
text ranges. Users can browse, search, and sort their saved markers; labels
organize bookmarks.

## Identity and storage

Marker kinds share a common storage model, with a verse address, range, content,
and timestamps. Local database IDs serve device-local access; globally unique
IDs preserve identity across [sync](sync.md) and [backup/import](data-transfer.md).
Labels and marker-label assignments have their own sync identities. See
[Storage](../storage.md) for database ownership.

Highlights preserve saved colors and, for partial highlights, text ranges with
verification information. Highlights use a fixed opacity. Display-time text
color adjustment keeps highlighted or selected text readable against its
background without rewriting saved markers. Unaffected text retains the
configured reading colors. Changes to text normalization must
preserve partial-range compatibility.

## Reader integration

The reader combines markers with progress pins and other verse attributes.
Compose and legacy rendering must present the same saved content, selection,
and highlight appearance with readable text. Note links use the shared verse-reference
parser; its [compatibility tests](../../Alkitab/src/test/desktop-verse-parser.md)
document the parsing contract.

The [storage package](../../Alkitab/src/main/java/yuku/alkitab/base/storage)
owns marker persistence; reader attribute loading and presentation live in the
[verses package](../../Alkitab/src/main/java/yuku/alkitab/base/verses).
