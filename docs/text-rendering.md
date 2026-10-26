# Text rendering

Bible readers supply raw verse text and section headings. The presentation layer
combines them with verse identity, selection, markers, reading guides, and audio
highlights. Compose is the default rendering path; legacy Android Views remain
available through the verse experimental setting.

## Shared presentation

Verse lists use a common controller interface so the reader, parallel panes,
and verse dialogs can work with either renderer. Compose builds annotated text
and native rows; legacy views build spans and custom verse rows. Snippets outside
verse lists use a shared text slot to follow the reader's appearance.

The [verses package](../Alkitab/src/main/java/yuku/alkitab/base/verses) owns these
controllers and renderers. Both paths must preserve verse addresses, visible
text, selection behavior, and highlight ranges. Section headings are separate
items interleaved with verses, rather than part of verse text.

## Formatting and plain text

Verse text can contain inline formatting for paragraphs, italics, red letters,
line breaks, footnotes, cross-references, and ruby annotations. The
[YET guide](developer/yet.md#text-formatting) documents the authoring syntax;
[Binary formats](binary-formats.md) documents file encoding.

Keep formatting codes out of copied/shared text. `FormattedVerseText` provides
plain-text conversion. Search uses its own formatting-aware matching path;
changes must keep query normalization and decoded verse text consistent. See
[Search](modules/search.md).

`FormattedTextRenderer` supports a smaller span-based formatting surface for
callers that do not need complete verse rows. It is distinct from the full
verse-list presentation.

## Ruby annotations

Compose renders ruby readings above their base text. The base remains the text
used for highlights, dictionary links, accessibility, copy, and search. Layout
reserves vertical and horizontal room for readings and handles wrapping and RTL
text. The legacy verse renderer shows the base without the ruby reading.

The [ruby design](features/ruby/design.md) owns encoding, malformed-input rules,
data-source research, and open work. Renderer code and tests own the placement
algorithm. `VerseRubySnapshotTest` generates sheets for visual review under
`Alkitab/build/snapshots/verse-ruby/`.

## Appearance and contrast

Applied reading appearance combines base preferences and per-version size
adjustments. Pinch zoom updates the reader's base size. Consumers should use
the shared appearance rather than duplicate preference calculations.

Highlight bands use a fixed opacity and preserve saved colors. Both renderers
resolve text contrast against the page, selection overlay, and highlight band
in painting order. They retain readable text colors and adjust insufficient
contrast toward darker or lighter shades. Red-letter text retains its own color
in selected verses as well as ordinary reading. Dictionary links underline text
while inheriting its color.

Display settings provide one text color adjustment slider. Its independent
preference defaults to full correction; lower values blend toward the original
text colors. The setting does not change saved markers or highlight opacity.
See [Markers](modules/markers.md).

`VerseTextColorSnapshotTest` renders theme, selection, and highlight combinations
through both pipelines under `Alkitab/build/snapshots/verse-text-color/`.
