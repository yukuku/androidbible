# Markers Module (Bookmarks, Notes, Highlights)

## Overview

"Markers" is the unified system for bookmarks, notes, and highlights. All three share the same `Marker` database table, distinguished by a `kind` column. Markers can be organized with labels (categories) and synced across devices.

## Key Files

- `Alkitab/src/main/java/yuku/alkitab/base/ac/MarkerListActivity.kt` — Filtered list of markers with search and sorting
- `Alkitab/src/main/java/yuku/alkitab/base/ac/MarkersActivity.java` — Label management (create, rename, delete, reorder via `ItemTouchHelper` drag)
- `Alkitab/src/main/java/yuku/alkitab/base/ac/NoteActivity.java` — Note editor with verse link detection
- `Alkitab/src/main/java/yuku/alkitab/base/util/Highlights.kt` — JSON-based highlight encoding
- `Alkitab/src/main/java/yuku/alkitab/base/widget/AttributeView.java` — Icon drawing for bookmark/note/highlight indicators
- `Alkitab/src/main/java/yuku/alkitab/base/verses/VersesAttributes.kt` — Per-verse attribute maps
- `Alkitab/src/main/java/yuku/alkitab/base/verses/VerseAttributeLoader.kt` — Loads attributes from DB and content providers
- `Alkitab/src/main/java/yuku/alkitab/base/storage/MarkerDao.kt` / `LabelDao.kt` / `Marker_LabelDao.kt` — Per-table DAOs issuing raw SQL against the legacy `AlkitabDb` (`InternalDbHelper`)

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

Storage: the `Marker`, `Label`, and `Marker_Label` tables live in the legacy `AlkitabDb` SQLite file managed by `InternalDbHelper`. The `MarkerDao` / `LabelDao` / `Marker_LabelDao` classes expose a `Marker` / `Label` / `Marker_Label` model surface for callers and issue raw SQL internally.

## Labels

Labels are user-created categories for organizing bookmarks. Each label has a `title`, `ordering`, and `backgroundColor`. The `marker_label` junction table creates many-to-many relationships. Labels also have GIDs for sync.

## Highlights

Highlights use a JSON encoding in the `caption` field supporting:
- Full-verse highlights (solid color)
- Partial highlights (character range within a verse)
- Hash-based verification to detect when verse text has changed

The `Highlights` utility handles encoding/decoding and color management. `Highlights.blendOver()` masks the input to 24-bit RGB and interpolates opacity between fixed `0xA0` at adjustment 0 and background-adaptive opacity at adjustment 1. The adaptive result is cached independently of the adjustment.

Display settings offers an unchecked-by-default “Use original highlight colors” checkbox and a customizer with a 0–1 slider. The checkbox selects 0 when checked and 1 when cleared. Intermediate values are labeled custom. `Prefkey.highlightColorAdjustment` stores the amount, defaulting to 1; it affects rendering without modifying saved markers. Both verse renderers, marker snippets, and the highlight picker use the applied adjustment. The customizer previews verses from the active Bible version through the reader’s Compose renderer and row layout, preserving the active words-of-Jesus and verse-number colors, italics, paragraph formatting, and ruby annotations. Sample background, normal text, and highlight colors can be adjusted independently; changing normal text color preserves the words-of-Jesus color. Only confirming the customizer saves the adjustment; preview colors do not change the reading theme.

Color selection in `TypeHighlightDialog` and the label color editor uses the iOS-style Compose color picker at `yuku.alkitab.base.compose.colorpicker.IosColorPicker` / `ColorPickerDialog`, hosted inside a `ModalBottomSheet`.

## Attribute Display

`VerseAttributeLoader` aggregates bookmark count, note count, highlight color, progress mark presence, and map availability for each verse in a chapter. These are displayed as small icons in `AttributeView` next to each verse.

## Sorting Options

MarkerListActivity supports sorting by:
- `createTime` — when the marker was first created
- `modifyTime` — when last edited
- `ari` — Bible order (by verse reference)
- `caption` — alphabetical by content

## Verse Link Detection

`NoteActivity` uses `DesktopVerseFinder` in the app’s `yuku.alkitabconverter.util` package to detect verse references in note text and convert them to clickable links that navigate to the referenced verse.
