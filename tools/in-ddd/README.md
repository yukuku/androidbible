# DDD rendering gallery

`in-ddd.yet` is the source for the synthetic Bible bundled with the `plain`
build. Open **FirstBookWithName 1**, chapters 3 through 8. These examples are
rendering fixtures, not a Bible translation. Chapters 1 and 2 retain their
original text, headings, and references. The other chapters and books retain
their navigation fixtures, including the second book's 255 chapters.

| First-book location | Coverage |
| --- | --- |
| 3:1–10 | Generic ruby, furigana, pinyin, Strong's identifiers, gloss, morphology, transliteration, lemma, and unknown-kind fallback |
| 3:11–24 | Character, word, and phrase readings; Latin and RTL bases; combining marks and emoji; leading, trailing, standalone, and adjacent runs |
| 4:1–16 | Footnotes and cross-references at different positions, adjacent markers, ten footnotes, ruby, styles, indentation, and paragraphs |
| 4:17–26 | Parallel headings, rich notes, web links, OSIS and numeric targets, ranges and disjoint targets, Unicode notes, RTL links, and wrap boundaries |
| 5:1–12 | Short and wide readings/bases, adjacent annotation collisions, punctuation, edge positions, oversized readings, and wrapping bases |
| 5:13–24 | Ruby with red/italic styles, styled substrings, all paragraph/poetry levels, plain lines, hard breaks, and blank lines |
| 5:25–32 | Empty-base anchors, empty readings, grapheme clusters, dense CJK and Hebrew annotation, and combined styles and links |
| 6:1–22 | Short and long rows, paragraph/poetry flow, hard breaks, red/italic text, ruby, RTL, links, and selection around end-side attributes |
| 7:1–18 | Guide, plain and formatted text, flush and first-line paragraphs, all four poetry indent levels, italic, red, and overlapping styles |
| 7:19–24 | Leading/trailing breaks, blank lines, styles across breaks and paragraphs |
| 8:1–22 | Accents, punctuation, Japanese, Chinese, Korean, Greek, Hebrew, Arabic, Indic and Thai text; mixed direction, emoji, nonbreaking spaces, long tokens, and supplementary characters |

## Try the gallery

- Use the Compose verse renderer to see ruby readings above the base text.
  Each ruby run has one reading; stacked readings and ruby interaction are not
  implemented. Glyph coverage depends on the selected font and device.
- In chapter 6, add bookmarks, notes, highlights, and reading progress pins,
  individually and together. Text below the end-side icons should use the freed
  width. These attributes are user data and are not created by the sample.
- Compare light/dark themes, large text, different fonts, hidden verse numbers,
  narrow split panes, selection, copying, and search snippets.
- Tap the footnotes, inline references, and chapter 4 parallel headings. Ruby
  readings themselves are not links.

## Regenerate the bundled assets

Build or obtain `YetToInternal.jar` as described in [Tools](../../docs/tools.md),
then run from the repository root:

```sh
java -jar /path/to/YetToInternal.jar --prefix ddd \
  tools/in-ddd/in-ddd.yet Alkitab/src/plain/assets/internal
```

Commit the YET source and the generated `ddd_*` files together. Do not use the
converter's `for.open.source` obfuscation option: the sample's readable text and
annotation tags are intentional. Warnings about the second book's chapters
beyond the KJV chapter count are expected for its navigation fixture.

Verify the source, packaged chapter offsets, headings, note/reference payloads,
and ruby parsing with:

```sh
./gradlew testPlainDebugUnitTest \
  --tests 'yuku.alkitab.base.storage.BuiltInGalleryTest'
```
