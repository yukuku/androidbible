# Text Rendering Pipeline

## Overview

Bible verse text goes through a multi-stage pipeline from binary storage to rendered `SpannableStringBuilder` displayed in a `RecyclerView`.

## Pipeline

```
YES2 binary file
  → Yes2Reader.loadChapterText()
    → SingleChapterVerses (array of raw verse strings with formatting codes)
      → VersesDataModel (merges verses + pericope headers into display list)
        → VersesControllerImpl (RecyclerView adapter)
          → VerseRenderer.render() (applies formatting codes as spans)
            → VerseItem (custom RelativeLayout with background drawing)
```

## Formatting Codes

Verse text uses inline formatting codes prefixed with `@`:

| Code      | Meaning                                          |
|-----------|--------------------------------------------------|
| `@@`      | Marks verse as having formatting (must be first) |
| `@0`      | Paragraph level 0 (no indent)                    |
| `@1`–`@4` | Paragraph indent levels 1–4                      |
| `@^`      | Continuation indent                              |
| `@6`      | Red letter start (Jesus' words)                  |
| `@5`      | Red letter end                                   |
| `@9`      | Italic start                                     |
| `@7`      | Italic end                                       |
| `@8`      | Line break / blank line                          |
| `@<tag@>` | Start of special inline element (xref, footnote, ruby) |
| `@/`      | End of special inline element                    |

Special inline elements:

| Element                 | Meaning                                                        |
|-------------------------|----------------------------------------------------------------|
| `@<f1@>@/`              | Footnote link, field 1 (empty content)                          |
| `@<x1@>@/`              | Cross-reference link, field 1 (empty content)                   |
| `@<r=reading@>base@/`   | Ruby: `reading` is drawn above `base`. A one-letter kind may follow `r` (`@<rf=…@>` furigana, `@<rp=…@>` pinyin, `@<rs=…@>` Strong's, …); it is reported on `RubyRange.kind` and does not change rendering |

## VerseRenderer

`VerseRenderer.kt` processes formatting codes and produces a `SpannableStringBuilder`:

1. Detects `@@` prefix to determine if verse has formatting
2. Iterates through characters, building spans for each formatting region
3. Applies `ForegroundColorSpan` for red letters, `StyleSpan(ITALIC)` for italics
4. Handles indentation via `LeadingMarginSpan`
5. Processes `@<tag@>` blocks for cross-references and footnotes
6. Verse numbers are prepended as superscript spans

## FormattedTextRenderer

`FormattedTextRenderer.kt` is a lightweight production renderer — "a much simpler version of `VerseRenderer`" per its own doc comment — used where only a subset of the formatting codes matters (italics `@9…@7`, line break `@8`, `@<tag@>…@/` inline elements). It builds a `SpannableStringBuilder` directly, without the verse-number/paragraph machinery of the full pipeline.

A Compose port of verse rendering also exists (`VerseRendererCompose.kt`) alongside the View-based `VerseRenderer.kt`.

## Ruby (Compose only)

`@<r=reading@>base@/` keeps `base` inline in the `AnnotatedString` and records a `VerseRendererCompose.RubyRange` for it, so highlight offsets, dictionary links and TalkBack text are unaffected by the annotation. The verse composable then draws the reading above the base:

- `computeLineMetrics` reserves a band above every line (`LineMetrics.rubyBandPx`) when the verse has ruby, and the text is laid out with `LineHeightStyle.Alignment.Bottom` so the whole surplus sits on top of the glyphs.
- `widenRubyBases` adds letter spacing to a base run that is narrower than its reading. A reading may overhang a neighbouring character without ruby by one ruby em (`RUBY_OVERHANG_RATIO`) and keeps a gap from a neighbouring reading (`RUBY_SIDE_GAP_RATIO`); `rubySideSlackPx` decides which applies.
- `Modifier.rubyOverlay` paints each reading centered over its base using the `TextLayoutResult`; a base run broken across lines gets a proportional slice of the reading on each line (`rubySliceFor`). The reading takes the color of the innermost colored span under its centre character (`rubyColorAt`), so red-letter and highlighted, selected runs stay readable.
- Reading size is `RUBY_FONT_SIZE_RATIO` (0.5) of the verse size.

The View-based `VerseRenderer` ignores the `r` tag and shows only the base text, and `FormattedVerseText.removeSpecialCodes` strips the reading, so search, copy and share operate on the base text. `VerseRubySnapshotTest` renders sample sheets (basics, poetry, highlights, inline styles, typography variants) to `Alkitab/build/snapshots/verse-ruby/` for visual review. See `docs/features/ruby/design.md` for the data format, sources and open items.

## Plain Text Conversion

`FormattedVerseText.removeSpecialCodes()` strips all `@`-codes to produce plain text for:
- Clipboard copy operations
- Search indexing
- Share text generation

This is important — search and copy must use the stripped text, not raw formatted text.

## Pericope Rendering

Pericopes (section headers like "The Sermon on the Mount") are rendered as distinct items in the RecyclerView, interleaved with verses. The `VersesDataModel.itemPointer` array maps display positions:
- Negative values → pericope index (bitwise NOT)
- Non-negative values → verse index (0-based)

## VerseItem Layout

`VerseItem.kt` is a custom `RelativeLayout` that handles:
- Checked/selected state visual feedback (colored background)
- "Attention" animation (pulse highlight when navigating to a verse)
- Drag-and-drop for progress mark pins
- Accessibility (TalkBack support with verse number and text)
- Highlight color painting on the background canvas

### Text color in a selected verse

A checked verse paints the selection color over the page at `TextColorUtil.CHECKED_VERSE_OVERLAY_ALPHA`. The host then forces the text to black or white via `TextColorUtil.getForCheckedVerse`, based on the selection color alone.

A highlight band is drawn on top of that overlay. So both renderers give a highlighted run its own color through `TextColorUtil.getForCheckedVerseHighlight`, which picks whichever of the reading color or the forced color has better contrast against what the band actually paints. Words of Jesus lose their red in a checked verse and follow the same per-run color.

Dictionary links (`DictionaryLinkSpan`, and the Compose `addDictionaryLinks`) only underline. They never set their own color, so they follow the run they sit in.

`VerseTextColorSnapshotTest` renders every theme, selection color, highlight color and selection state through both pipelines into `Alkitab/build/snapshots/verse-text-color/` for visual review.

## Text Sizing

Font size is controlled by:
1. Base `textSize` preference (user setting)
2. Per-version `textSizeMult` multiplier (from `PerVersion` settings)
3. `CalculatedDimensions` in `S.applied()` precomputes final sizes

Two-finger pinch gesture in `IsiActivity` adjusts the base text size in real time.
