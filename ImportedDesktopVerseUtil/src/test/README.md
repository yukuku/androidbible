# DesktopVerseParser compatibility tests

## Golden compatibility baseline

`resources/desktop-verse-parser-golden.tsv` records the outcomes of the original Java `verseStringToAri` implementation, restored before any reading-guide parser changes. The fixture was captured and verified against that restored implementation on 2026-10-06, before the Kotlin port. The same fixture then passed against the Kotlin port, and again after complete-reference parsing was added. Explicitly approved bug fixes subsequently changed 83 expected outcomes: 6 chapter-list cases and 77 oversized-number cases; all other expectations remain unchanged.

Each tab-separated row contains a case category, locale language tag, escaped input, and expected outcome. Inputs escape tabs, carriage returns, newlines, and backslashes. Outcomes distinguish `null`, an empty list, hexadecimal start/end ARI pairs, and the exception class. The test reads the fixture; it never generates or updates expected values. The test JVM starts in US English. Each golden case explicitly selects the locale stored in its row and resets to `Locale.US` afterward; no test reads the host default locale.

The corpus covers all 66 book entries and their listed aliases, uppercase and period variants, surrounding prose, repeated and nonstandard whitespace, English/Indonesian/Turkish locale behavior, single-chapter books, inherited chapters, ranges and separators, malformed suffixes, reversed ranges, zero values, byte boundaries, and integer overflow. Some aliases in the original lookup table are not recognized by its extraction regex; their actual outcomes are preserved too.

The following corrections are intentional:

- `Gen 1,3,5` produces three whole chapters. Bare numbers inherit a chapter only after a verse address, as in `Gen 1:2,3,5`.
- A number that exceeds the ARI component limit of 255 or cannot be represented as an integer rejects the reference with `null`. No byte wrapping, exception, or valid prefix is returned for the covered oversized-number cases.
- Single-chapter shorthand such as `Jude 1,3,5` continues to denote verses.

Other compatibility observations remain:

- `Gen 1:2,,4` skips the empty component and returns both verses.
- `Gen 1:2–4` extracts only verse 2 because the legacy regex does not consume the Unicode dash.
- Book lookup during prose extraction uses the default locale.

Do not regenerate the fixture from the Kotlin implementation to make a failure disappear. Any intentional legacy behavior change needs an explicit decision and a review of the affected expected outcomes.

`DesktopVerseParserRegressionTest` covers the approved chapter-list and numeric-bound fixes with explicit expected values, including prose boundaries, chapter/verse transitions, single-chapter shorthand, range endpoints, and oversized numbers after valid list entries.

## Complete-reference features

`DesktopVerseParserReferenceTest` checks the strict `parseReference` API with explicit expected ARIs and rejection cases. It covers whole books and chapters, chapter lists, inherited verses, cross-chapter ranges, case-insensitive `dan`, Unicode dashes, normalized book names, locale-independent lookup, and rejection of malformed or overflowing addresses.

`DesktopVerseParserAliasTest` checks every frozen alias against its book index through the strict API. It obtains alias inputs and book indices from the fixture, not from production lookup data.

The strict API shares book data and numeric regex patterns with the legacy API, while retaining separate validation and range semantics. `ReadingPassageTest` in the app module verifies version-specific chapter/verse limits and guide boundaries. The parser itself has no Bible-version verse counts.

## Run

```sh
set -o pipefail
./gradlew :ImportedDesktopVerseUtil:testDebugUnitTest 2>&1 | tail -60
./gradlew :Alkitab:testPlainDebugUnitTest --tests '*ReadingPassageTest' 2>&1 | tail -60
```
