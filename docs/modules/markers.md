# Markers Module (Bookmarks, Notes, Highlights)

## Overview

"Markers" is the unified system for bookmarks, notes, and highlights. All three share the same `Marker` database table, distinguished by a `kind` column. Markers can be organized with labels (categories) and synced across devices.

## Key Files

- `Alkitab/src/main/java/yuku/alkitab/base/ac/MarkerListActivity.kt` — Filtered list of markers with search and sorting
- `Alkitab/src/main/java/yuku/alkitab/base/ac/MarkersActivity.java` — Label management (create, rename, delete, reorder via drag-sort)
- `Alkitab/src/main/java/yuku/alkitab/base/ac/NoteActivity.java` — Note editor with verse link detection
- `Alkitab/src/main/java/yuku/alkitab/base/util/Highlights.java` — JSON-based highlight encoding
- `Alkitab/src/main/java/yuku/alkitab/base/widget/AttributeView.java` — Icon drawing for bookmark/note/highlight indicators
- `Alkitab/src/main/java/yuku/alkitab/base/verses/VersesAttributes.kt` — Per-verse attribute maps
- `Alkitab/src/main/java/yuku/alkitab/base/verses/VerseAttributeLoader.kt` — Loads attributes from DB and content providers

## Marker Model

```
Marker {
  _id: long           — local database ID
  gid: String         — globally unique ID for sync
  ari: int            — verse reference (ARI encoding)
  kind: int           — 0=bookmark, 1=note, 2=highlight
  caption: String     — display text (bookmark title / note content / highlight color info)
  verseCount: int     — number of verses covered
  createTime: Date
  modifyTime: Date
}
```

## Labels

Labels are user-created categories for organizing bookmarks. Each label has a `title`, `ordering`, and `backgroundColor`. The `Marker_Label` junction table creates many-to-many relationships. Labels also have GIDs for sync.

## Highlights

Highlights use a JSON encoding in the `caption` field supporting:
- Full-verse highlights (solid color)
- Partial highlights (character range within a verse)
- Hash-based verification to detect when verse text has changed

The `Highlights` utility class handles encoding/decoding and color management.

## Attribute Display

`VerseAttributeLoader` aggregates bookmark count, note count, highlight color, progress mark presence, and map availability for each verse in a chapter. These are displayed as small icons in `AttributeView` next to each verse.

## Sorting Options

MarkerListActivity supports sorting by:
- `createTime` — when the marker was first created
- `modifyTime` — when last edited
- `ari` — Bible order (by verse reference)
- `caption` — alphabetical by content

## Verse Link Detection

`NoteActivity` uses `DesktopVerseFinder` from the `ImportedDesktopVerseUtil` module to detect verse references in note text and convert them to clickable links that navigate to the referenced verse.
