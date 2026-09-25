# Smart search

This prototype expands Indonesian search terms to related words. Both smart search and its
diagnostics are enabled by default under Experimental settings.

For example, `kasih` finds `mengasihi`, while `berkat` no longer matches `berkata`.
`kasihan` and `kasih` are intentionally separate roots in the TB lexicon.

## Query handling

`SearchActivity` calls `SmartSearchRunner`. It loads the version's lexicon and vocabulary,
plans each term, then intersects the matching verses from the selected books.
With diagnostics enabled, it also runs the old substring search for comparison.
Each term is searched independently so diagnostics can show its own result count.

The planner uses the first matching rule:

| Input | Behavior |
|---|---|
| `+word` or a quoted phrase | Keep the existing exact-match behavior |
| Digits or punctuation | Use substring search |
| A root or form in the lexicon | Match the whole word family |
| A word in the text with no listed family | Match that word exactly |
| An unknown word whose affixes can be removed to reach a known form | Match that family and the typed word |
| Anything else | Use substring search |

The vocabulary check prevents a known word from being reduced to an unrelated root.
Unknown fragments such as `yerus` still use substring search.

`AffixPeeler` follows the builder's `query.py`. It tries prefixes and suffixes breadth first,
restoring initial letters where needed (`mengasihi` → `kasihi`). It accepts only known forms
and keeps at least three letters. Trying alternatives lets `amukan` reach `amuk` through `-an`
instead of committing to `amu` through `-kan`.

## Word boundaries

`WordScanner` treats letters and digits as words and keeps hyphenated forms together.
It skips verse formatting codes. `FamilyMatcher` looks up a hyphenated word as a whole;
only unknown compounds are split. This keeps `mereka-rekakan` in the `reka` family rather
than matching the pronoun `mereka`.

A formatting code inside a word (`ber@9kata@7`) splits it. The old whole-word search has
the same limitation.

## Lexicon files

The source `.yet` lists roots and their forms on `lexicon` lines. The converters produce:

- `YetToYes2`: a `lexicon` section in the downloadable `.yes` file.
- `YetToInternal`: `{prefix}_lexicon_bt.bt` for the bundled version.

`LexiconCompiler` compresses forms using root tokens, rewrite rules, shared text, and literals.
See the [binary format](../../binary-formats.md#lexicon-file-and-section).
`Version.loadLexicon()` loads the data through `Yes2Reader` or `InternalReader`;
`LexiconRepository` caches it per version.

TB contains 2,942 families and 15,373 forms: 78 KiB bundled, or 49 KB compressed in `.yes`.

Without a lexicon, Indonesian versions use `RulesLexiconBuilder`. It groups words under the
deepest reachable stem found in the text. This fallback is less accurate: it can group
`kepada` with `pada`, or `kasihan` with `kasih`. The separate-root decision applies to the
curated TB lexicon; the fallback cannot reliably make that distinction.

## Diagnostics

The search panel shows term resolution, affix-removal steps, matching forms, counts, and timings.
All / New / Dropped filters compare results with substring search. Rows show `NEW` or `DROPPED`
badges and a `via …` line for matching forms.

Search Lab lets testers select automatic, built-in-only, or rules-only mode, try queries,
and run known examples. Search tips follow the active language and search mode.

## TB results

Recorded by `TerjemahanBaruSmartSearchTest` using the version's lexicon:

| Query | Letter search | Smart search | Gained | Dropped |
|---|---:|---:|---:|---:|
| `berkat` | 3,059 | 279 | 0 | 2,780 |
| `kasih` | 853 | 828 | 120 | 145 |
| `sembuhkan sakit` | 6 | 48 | 42 | 0 |
| `beriman` | 14 | 225 | 211 | 0 |
| `mengasihi` | 140 | 828 | 688 | 0 |
| `menyembah` | 203 | 944 | 741 | 0 |
| `mengampuni` | 67 | 137 | 70 | 0 |
| `+kasih` | 481 | 481 | 0 | 0 |

## Limits

- The first search reads the whole translation to build its vocabulary. Run it off the main thread.
- Marker filtering still uses substring matching.
- New UI strings are available in English and Indonesian only.

## Tests

- `WordScannerTest`, `AffixPeelerTest`, `SearchLexiconTest`: parsing and word families.
- `SmartSearchEngineTest`: planning, results, highlighting, diagnostics, fallback rules,
  and the separation of `kasihan` from `kasih`.
- `TerjemahanBaruSmartSearchTest`: real TB results. Requires `ALKITAB_TB_YET`;
  `ALKITAB_TB_YES` and `ALKITAB_PROPRIETARY_DIR` enable binary-format comparisons.
  These checks are skipped when their inputs are missing.
- `LexiconSectionTest`: encoding, rewrite rules, and round trips.
- `SmartSearchSnapshotTest`: English and Indonesian panel and Lab renders, saved to
  `Alkitab/build/snapshots/smart-search/`.
