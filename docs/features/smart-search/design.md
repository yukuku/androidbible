# Smart search (prototype)

Status: prototype. On by default behind the "Smart search (Indonesian)" experimental setting, with
its diagnostics also on by default so testers can see what it does.

## The problem

Search matches letters. That fails in both directions for Indonesian, where words are built with
affixes and some prefixes swallow the first letter of the root:

- **It misses.** `kasih` + `meN-` + `-i` is spelled `mengasihi`; the `k` is gone, so a search for
  `kasih` can never find it. A reader who types `sembuhkan sakit` gets 6 verses in Terjemahan Baru,
  because `menyembuhkan` and `penyakit` do not contain the typed letters.
- **It finds too much.** The letters of `berkat` sit inside `berkata`, so 3,059 verses come back
  and fewer than 300 are about blessing. `iman` hides in `bagaimana`, `alam` in `dalam`.

## How it works

`SearchActivity` hands the query to `SmartSearchRunner`, which:

1. **Selects a word list** for the translation (`LexiconRepository.select`), building the
   translation's vocabulary on first use (`VocabularyCache`, one read of the whole text).
2. **Plans** each query term (`SmartSearchPlanner`), deciding how it is matched and recording why.
3. **Searches** every term on its own across the selected books and intersects the results
   (`SearchEngine.searchByPlan`). Searching terms independently, instead of only inside the
   previous term's hits, is what lets the diagnostics report each term's own count.
4. With diagnostics on, **runs the classic letter search** for the same query so the two can be
   compared verse by verse.

### How a term is resolved

Checked in this order; the first that applies wins.

| Term | Resolution | Matched as |
|---|---|---|
| `+word`, `"a phrase"` | explicit | exactly as before |
| contains digits or punctuation | not a word | letters, as before |
| a form or root in the word list | in the word list | every form of its family, as whole words |
| occurs in the text but belongs to no family | its own family | that exact word only |
| peeling affixes reaches a listed form | affixes peeled | that form's family, plus the typed word |
| anything else | letter match | letters, as before |

"Its own family" matters: without it, a word that occurs in the text but has no relatives could be
peeled into an unrelated root. And the last row means an unknown word, or a fragment like `yerus`,
is never worse off than with the classic search.

### Affix peeling

`AffixPeeler` ports `query.py` from the lexicon builder in `alkitab-sources`. It peels suffixes
(`-lah`, `-kan`, `-an`, `-i`, clitics) and prefixes breadth first, so the shallowest landing wins
and `amukan` reaches `amuk` rather than stopping at `amu` + `-kan`. Nasal prefixes restore the
letter they swallowed, decided by the letter that follows: `meng-` before a vowel offers the root
with and without `k`, `meny-` restores `s`, `mem-` restores `p` or `m` before a vowel and nothing
before a consonant (`membawa`), and `men-` likewise with `t` or `n`. A stem is never shorter than
three letters.

### Matching words

`WordScanner` splits text the way the lexicon builder does: runs of letters or digits, with a hyphen
between two such runs keeping them one word (`kasih-Nya`, `orang-orang`). Formatting codes are
skipped, so `@6kasih` is the word `kasih`. `FamilyMatcher` checks a hyphenated word whole first,
since the word list assigns compounds to their real family (`mereka-rekakan` belongs to `reka`);
only a compound the list has never seen is split so its parts can match.

A formatting code in the middle of a word (`ber@9kata@7`) splits it in two. The classic search has
the same limitation for whole-word matches; it has not been seen to matter in practice.

## Word lists

A word list is part of the Bible version's own data, like its cross-references and footnotes:
the `lexicon` section of a yes file, or `{prefix}_lexicon_bt.bt` for the internal version. It
reaches both from `lexicon` lines in the version's `.yet`, which list each form spelled out.
`YetToYes2` and `YetToInternal` compress them: `LexiconCompiler` finds rewrite rules for the start
and the end of roots in the data, and stores each form as tokens (the root, a rewritten root, a
shared text piece, or a literal). The format is in
[`docs/binary-formats.md`](../../binary-formats.md#lexicon-file-and-section).

`Version.loadLexicon()` reads it (`Yes2Reader` and `InternalReader` implement it; other readers
have none), and `LexiconRepository` caches it per version. The lists are produced offline; a
version without one simply has no `lexicon` section.

For Terjemahan Baru the list holds 2,942 families and 15,373 forms: 78 KiB as the internal file
and 49 KB as the yes file's Snappy-compressed section.

### Rules-only word families

With no word list in an Indonesian version, `RulesLexiconBuilder` derives families on the
device from the version's own vocabulary: each word joins the deepest stem, reachable by
peeling, that also occurs in the text. It takes well under a second on Terjemahan Baru. It is
knowingly weaker, because nothing says which words are roots: `kepada` joins
`pada`, and `kasihan` joins `kasih`. The Search Lab can force either source so the two can be
compared.

## Diagnostics

Everything below is visible to the user while "Smart search diagnostics" is on.

- **Search screen panel** (`SmartSearchPanel`), above the results. The summary line gives the
  verse count and how many verses were gained and dropped against the letter search. Chips list
  **All**, only the **New** verses, or the **Dropped** ones (shown with the letter search's
  highlighting). Expanded, it shows each term's resolution, the peel trail, every form searched
  (with occurrence counts, forms the typed letters could never reach highlighted, and forms absent
  from this translation faded), each term's own verse count and time, the word list in use, the
  vocabulary size, and timings for planning, smart search and letter search.
- **Result rows** carry a `NEW` badge when the letter search would have missed the verse, and a
  `via …` line naming the family forms that matched. Dropped rows carry `DROPPED`.
- **Search Lab** (`SearchLabActivity`, from the search screen's menu or the panel): the switches
  and the word-list mode (automatic, built-in only, rules only), the selected list and vocabulary
  for any version, a "try a word" box that plans the query with both the built-in list and rules
  only and counts verses, and a self-check over known-tricky queries.
- **Tips**: the empty search screen shows syntax tips matching what the search will do; the smart
  version is shown only for Indonesian translations.

## Measured on Terjemahan Baru

Verse counts, letter search against smart search with the version's own word list, from
`TerjemahanBaruSmartSearchTest`:

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

## Open questions

- **`kasihan` is its own family** in the word list, so the 145 verses `kasih` drops are mostly
  *belas kasihan* ("compassion"). Whether a search for `kasih` should reach them is a product
  decision for the word list, not the app.
- **Cost of the first search.** Learning a translation's vocabulary reads the whole text once per
  process, like one classic search. The diagnostics report the time.
- **Other screens.** The marker list filter still matches letters; `ReadyTokens` accepts family
  matchers, so it could adopt the same planner.
- **Translations of the new strings** exist for English and Indonesian only.

## Tests

- `WordScannerTest`, `AffixPeelerTest`, `SearchLexiconTest`: the building blocks.
- `SmartSearchEngineTest`: planning, searching, highlighting, the gained/dropped report and the
  rules-only builder, on an invented text.
- `TerjemahanBaruSmartSearchTest`: the table above against the real text and the word list in
  its `.yet`, and checks that the yes file's `lexicon` section and the internal
  `tb_lexicon_bt.bt` decode to the same families. Skipped unless `ALKITAB_TB_YET` is set;
  `ALKITAB_TB_YES` and `ALKITAB_PROPRIETARY_DIR` enable the two format checks.
- `LexiconSectionTest` (in `AlkitabYes2`): the rewrite rules, the rules the compiler finds for
  Indonesian and English samples, the token encoding, and a round trip.
- `SmartSearchSnapshotTest`: renders the panel and the Search Lab, in English and Indonesian, to
  `Alkitab/build/snapshots/smart-search/` for review without a device.
