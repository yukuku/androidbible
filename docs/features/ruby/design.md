# Ruby text for Bible verses

Ruby is small annotation text set above a run of base text: furigana over kanji, pinyin over hanzi, a Strong's number over an English word. This document collects what the feature can be used for, where open data comes from, how the annotation is encoded in the `.yet` source format and in verse text, and how the Compose verse row renders it. Status: prototype, rendered by the Compose verse pipeline only, so it is invisible under the "Verse (legacy views)" experimental setting.

## 1. Uses for the same rendering

Reading aids for scripts with a pronunciation problem:

- Japanese furigana (hiragana over kanji). The 口語訳 prototype ships with this.
- Chinese pinyin, zhuyin (bopomofo, for Taiwan) and Cantonese jyutping over hanzi. The CUV prototypes ship pinyin.
- Korean hangul over hanja in hanja-mixed editions.
- Romanisation for readers who cannot read a script yet: Latin over Javanese, Balinese, Batak, Bugis/Lontara or Sundanese script editions, or the reverse (aksara over the Latin text, for script revival), Latin over Arabic-script Jawi/Pegon Malay, Latin over Thai, Burmese, Tamil, Amharic.
- Pronunciation of proper names in any language ("Mephibosheth" with a syllable guide), a long-standing print feature of "self-pronouncing" Bibles.

Study aids over a translation:

- Strong's numbers, morphology codes (parsing) or lemmas over the words of a translation.
- Interlinear glosses: the original Hebrew/Greek word, its transliteration, or a literal gloss over the translated word. Read the other way, a translation gloss over an original-language text such as the SBLGNT already in the sources repo.
- Glosses for archaic or dialect words in older translations (Terjemahan Lama, KJV "wist"), or a modern spelling over an old orthography (Van Ophuijsen, Soewandi era Malay).
- Alternative readings, textual variants or a second translation's wording over a phrase.
- Numbers: Arabic numerals over spelled-out numbers, or a modern unit over "cubit".

Non-reading uses that reuse the layout:

- Song books: chord names over lyrics are ruby by construction. The songs module renders chords today with its own logic; a shared ruby primitive could replace it.
- Dictionary or word-study mode: after a lookup, show the gloss above the word instead of a link.
- Reading plans or memorisation: mask base words and leave only the ruby cue.

## 2. Open data sources

Research date: 2026-09-13. Licenses were checked on the source pages where possible.

### Furigana

| Source | License | Format | Notes |
|---|---|---|---|
| 口語訳 XML with word-level furigana, Salt Terrae (`bible.salterrae.net/kougo/xml/`, mirrors `pebutty.net/kougo/`, `jpn.bible/kougo/`) | Public domain in Japan (JBS copyright expired 2005); site says "No rights reserved" | XML per book, `<w s="reading">漢字</w>` | The files under `alkitab-sources/step1/ja-kougo/` are this data. Proofread furigana, OT and NT. Used for the prototype. |
| 大正改訳新約聖書 (ルビ付) and 明治元訳旧約聖書 (ルビ付), Wikisource | Public domain text; Wikisource wrapper CC BY-SA 4.0 | MediaWiki `{{ruby}}` templates | Classical Japanese, full Bible across the two. Needs a wikitext parser. |
| CrossWire SWORD JapKougo, JapBungo, JapMeiji, JapRaguet, JapDenmo | Per module, texts public domain | OSIS with ruby as glosses | Alternative aligned source for the same texts. |
| 電網聖書 (cozoh.org/denmo) | Public domain | Plain text | NT draft, no furigana; needs auto-generation. |
| Auto-generation: SudachiPy + SudachiDict (Apache-2.0), fugashi + unidic-lite (MIT + BSD), pykakasi (GPL-3.0) | | | Sudachi has the cleanest license for a build step. MeCab readings come out as katakana and okurigana boundaries need post-processing. |

### Pinyin, zhuyin, jyutping

| Source | License | Format | Notes |
|---|---|---|---|
| Chinese Union Version (simplified `cmn-cu89s` and traditional), eBible.org, and `step2/zh-cuvmps.yet`, `zh-cuvt.yet` in the sources repo | Public domain | USFM/USX, yet | Base text. No pinyin. |
| theWord "Pinyin" module, already in `step2/zh-pinyin.yet` | Unknown (theword.net) | Verse-level romanised words, tone marks | Word tokens, not character aligned; the prototype aligns it by syllable count. Contains occasional typos and nonstandard tone placement. |
| pypinyin (MIT) with phrase-pinyin-data (MIT) | MIT | Library | Per-character pinyin with phrase-based heteronym handling, `Style.BOPOMOFO` for zhuyin. Used as the fallback for verses the theWord text does not align with. A Bible name dictionary would improve heteronyms (大衛, 差遣). |
| OpenCC (Apache-2.0) | | | Simplified/traditional conversion. |
| rime-cantonese (CC BY 4.0, ODbL for one table), pycantonese (MIT), ToJyutping | | | Jyutping generation for Cantonese readers. |

No pre-aligned, openly licensed character-level pinyin Bible was found; generation is the practical path.

### Strong's numbers and interlinear data

| Source | License | Coverage | Notes |
|---|---|---|---|
| Berean Standard Bible tables (`bereanbible.com/bsb_tables.tsv`, berean.bible/downloads) | Public domain (since 2023-04-30) | Full | One row per BSB word group with Hebrew/Greek word, transliteration, parsing, Strong's and the BSB gloss. Best single file for both Strong's ruby and interlinear ruby. `step1/bsb` already holds the BSB text. |
| CrossWire KJV 2006 (`gitlab.com/crosswire-bible-society/kjv`, eBible `eng-kjv2006`) | Public domain | Full | OSIS `<w lemma="strong:H1234">` / USFM `\w ...|strong=""\w*` on English word groups. |
| STEP Bible TAHOT / TAGNT (`github.com/STEPBible/STEPBible-Data`) | CC BY 4.0 | Full | Per original-language word: Strong's, morphology, context-sensitive English gloss. |
| Open Scriptures Hebrew Bible (`github.com/openscriptures/morphhb`) | Text public domain, lemma/morph CC BY 4.0 | OT | Word-level Strong's and morphology on Hebrew. |
| unfoldingWord UHB / UGNT and ULT (`git.door43.org/unfoldingWord`) | CC BY-SA 4.0 | Full | USFM 3 `\w ...|strong=""` on original text; ULT carries `\zaln` alignment milestones from English to original words. Share-alike applies to bundled data. |
| OpenGNT (`github.com/eliranwong/OpenGNT`) | CC BY-SA 4.0 | NT | Strong's, morphology, transliteration, English and Chinese glosses. |
| MorphGNT / SBLGNT | Morphology CC BY-SA; SBLGNT text under the SBL EULA | NT | Avoid redistributing the SBLGNT text. |

Not usable: Lexham interlinears, Bible Hub pages, Japanese Living Bible, 新共同訳 and 聖書協会共同訳 (all copyrighted).

### USFM 3 ruby markup

USFM 3.0 encodes ruby as a single character marker with a gloss attribute: `\rb 漢字|gloss="かん:じ"\rb*` (a colon splits the gloss per base character, an empty piece means no gloss). USX 3 has `<char style="rb" gloss="...">`. No published USFM/USX Bible on eBible.org, DBL or scripture.api.bible was found that actually uses it, so treat `\rb` as an interchange format to emit, not a source to harvest. The `@<r=...@>` code below maps to it one to one.

### Regional scripts of Indonesia

Javanese (Wikisource `Jv/Alkitab`: Brückner 1822, Gericke 1840, Jansz 1888 are public domain), Balinese, Batak, Bugis and Sundanese Bibles exist digitally only in Latin script; the LAI editions are copyrighted. Script-form ruby would need a transliteration pipeline (`bennylin/transliterasi` covers Jawa, Bali, Bugis, Batak, Sunda; license to confirm) over the public-domain Latin texts, plus the Noto fonts (OFL 1.1) for those scripts.

### Recommended next prototypes

1. Strong's: Berean `bsb_tables.tsv` over `step1/bsb`, or the CrossWire KJV OSIS.
2. Interlinear: the same Berean table (transliteration or gloss as the ruby).
3. Zhuyin and jyutping: rerun the CUV pipeline with pypinyin `Style.BOPOMOFO` and ToJyutping.

## 3. Encoding

### Verse text

```
@<r=reading@>base@/
```

- `reading` is the ruby text. It must not contain `@`. It may contain spaces (a gloss of several words).
- `base` is ordinary verse text. It may contain other inline codes (`@6`/`@5`, `@9`/`@7`), but not another `@<..@>` element and not a paragraph or line-break code.
- A one-letter kind may follow `r`: `rf` furigana, `rp` pinyin, `rs` Strong's number, `rg` gloss, `rm` morphology, `rt` transliteration, `rl` lemma. Every kind renders the same way; the kind selects the tap behaviour (not built yet) and lets a version mix kinds. A renderer that does not know a letter treats it as plain `r`. The Kougo and CUV presets carry `rf` and `rp`.
- One reading per base run; nested or stacked rubies are not encoded. A second annotation layer (say Strong's on top of furigana) would need a second tag letter or a separator inside the reading, and is left open.
- Granularity is the producer's choice: one kanji, one word, or one phrase. The furigana prototype annotates per kanji group as the source does; the pinyin prototype annotates per character, which keeps the widening local when a reading is wider than its base.

Why this shape rather than a separate table:

- It is the existing `@<tag@>content@/` grammar, so the View renderer, `removeSpecialCodes`, search, copy, share, `YetToYes2` and the YES2 reader all pass the base text through unchanged today. Older app versions show plain text.
- Footnotes and cross-references are out of band (`@<f1@>` refers to a field) because their payload is large and shown on demand. A reading is short and always displayed with its base, so in-band is simpler and needs no new YES2 section.
- The `=` separator leaves `@<r1@>`-style numeric fields free for a future out-of-band variant.

### `.yet`

No new line type. `verse` lines carry the code inside the text, and the verse must start with `@@` as for any other formatted verse:

```
verse	1	1	1	@@はじめに@<r=かみ@>神@/は@<r=てん@>天@/と@<r=ち@>地@/とを@<r=そうぞう@>創造@/された。
verse	1	1	1	@@@<r=Qǐ@>起@/@<r=chū@>初@/，@<r=shén@>神@/@<r=chuàng@>创@/@<r=zào@>造@/@<r=tiān@>天@/@<r=dì@>地@/。
```

Producers should keep a ruby-free preset alongside (`ja-kougo` next to `ja-kougo-ruby`) until the View pipeline renders ruby too, since the View pipeline shows the base text only.

## 4. Rendering (Compose)

See the "Ruby" section of `docs/text-rendering.md` for the code path. Design choices:

- The base text stays inline in the `AnnotatedString`; readings are an overlay drawn from the `TextLayoutResult`. Offsets used by partial highlights, dictionary links, inline-link tap detection and TalkBack are unchanged. The alternative, `InlineTextContent` placeholders, would replace every base run by one placeholder character and break all of those.
- Vertical room comes from a taller `lineHeight` with `LineHeightStyle.Alignment.Bottom`, so every line of a ruby verse reserves the same band above the glyphs, including the first line. Verses without ruby keep proportional alignment.
- Horizontal room for a reading wider than its base is taken first from what is already beside the base. The whitespace between two words counts, split in half when the word beyond it carries a reading of its own so both can claim their share. A neighbouring character with no reading is worth one ruby em of overhang, so three kana over one kanji do not push the kanji away from its okurigana. A reading directly abutting the base costs a small gap instead.
- What that room does not cover is added to the space characters flanking the base, half to each side, which keeps the word's own shape and only grows the gaps around it. Two words sharing one space both add to it. Padding both sides equally is what lets the overlay centre the reading and still clear its neighbours. A base with no space beside the side that needs room, such as one Han character between two others, falls back to letter spacing inside the base, which is the conventional look for CJK anyway.
- A base run broken across lines gets a proportional slice of its reading on each line. A word joiner between base characters would prevent the break but change offsets, so it is not used.
- Reading size is half the verse size, drawn in the color of the base character under the reading's centre, so a highlight or red-letter boundary inside a base run picks whichever side holds most of the reading.

### Malformed and oversized input

`VerseRendererComposeRubyTest` and the "adversarial" sheet of `VerseRubySnapshotTest` cover these cases; none of them throws:

- Readings are positioned a line at a time from the laid-out geometry. Each wants to sit centred over its base; one that would collide with the reading before it slides right, and the line is pulled back from its right edge so the last one still fits. Sliding rather than shrinking is what keeps a reading whole when its base is short or starts a wrapped line, since a narrow neighbour lends the room its own base was not using. A line holding more than it can fit ellipsises what is left over, and positions still rise left to right there, so two readings never overlap.
- A reading wider than the row is ellipsised to the row width and clipped. Both the letter spacing on a base and the padding beside it draw on the same budget of `RUBY_MAX_LETTER_SPACING_EM` per base character, so garbage data can neither spread a word over several lines nor push its neighbours off one.
- A base run broken across lines never splits a surrogate pair in its reading.
- A base may span `@8` and paragraph codes; the reading is split per line.
- A base run with no characters, `@<r=reading@>@/`, is a reading for a word the translation does not have. It is drawn centred on the point in the text where it was written, so a bracket pair or any other marker the data puts around it reads as its anchor, and it takes its room from the text beside it the way any reading wider than its base does.
- A stray `@/`, an empty tag, a tag with an unknown letter, a nested ruby or an empty reading produce no ruby and leave the text as is. An inner tag replaces the outer one, so nested rubies annotate the inner base only. An unterminated `@<` at the end of the verse leaks its content as text, which matches the View renderer.
- Emoji, ZWJ sequences, combining marks and bidi controls are kept verbatim in both reading and base.

## 5. Open items

These are engineering follow-ups rather than features. The feature roadmap is section 6.

- View pipeline: `VerseRenderer` needs an equivalent (a `ReplacementSpan` or a custom `LineHeightSpan` plus overlay) or the reader must be Compose-only before ruby presets go to production. This gates everything in section 6.
- Performance: `widenRubyBases` measures two strings per ruby range on every bind. A verse with 40 rubies costs 80 measurements; cache per (text, style) if this shows up in profiles.
- Accessibility: a choice between ruby and inline parentheses, for readers who cannot make out text at half size.
- Data quality: the theWord pinyin has typos and nonstandard tone placement (normalised by the aligner); roughly 4% of verses fall back to pypinyin. A Bible name dictionary for pypinyin would fix most heteronym errors.

## 6. What to build next

The point of all of this is to help someone read the Bible and understand it better. Ruby annotations are a way of putting more information in front of a reader without making the text harder to read. On their own they are only labels. The features below are what turn them into something a person can learn from, ordered by how much work each one is.

### 6.1 Small things we can do next

These use machinery the app already has.

- **Make a reading tappable.** Every `RubyRange` already carries its kind letter (`rs`, `rf`, `rp` and the rest), and nothing in the app reads it yet. The tap handling written for footnotes and cross-references already knows how to turn a position on screen into a range in the text. Wiring a tap on a Strong's number to open that word's entry is the smallest change here with the largest effect, because it turns the numbers from decoration into the way a reader looks a word up.
- **Show readings in the verse dialogs.** The cross-reference and verse popups still use the older View renderer, so a verse opened from a cross-reference loses its annotations. Someone following a cross-reference is usually studying, which is exactly when the annotations matter.
- **Copy and share with the readings included.** Copying a verse strips the codes, so whatever the reader found is lost when they paste it into their notes. An option to copy `tahun (H8141)` would let people keep it.
- **Let readings be searched.** Readings are not indexed today. Searching for a Strong's number or a pinyin syllable should find the verses that carry it. For someone learning, that is the natural way to ask where else a word appears.
- **A per-version switch for readings.** Today a build either shows them or does not. A reader should be able to turn them off for ordinary reading and on for study, and choose which kind to show when a version carries more than one.

### 6.2 Things we should have

These need new data or new screens, but no new ideas.

- **A dictionary that is always there.** Tapping a Strong's number should give a definition offline, without a second app installed. At the moment the definition comes from a separate app's content provider, so most readers will tap and get nothing. The definition is the thing the reader actually wants; the number is only a key.
- **Word study: show every verse that uses this word.** From a Strong's number, list every other verse carrying it, with a count. Being able to follow one word across the whole Bible is what digging deeper means in practice, and the data needed to do it is already inside the verse text.
- **An interlinear view for one verse at a time.** A panel that expands under a verse and stacks the original word, its transliteration, its Strong's number, a short gloss and its grammar. The format already describes all of these as kinds. What is missing is support for more than one annotation over the same word, and a layout that stacks them. An expandable panel on a single verse is more useful than a whole separate reading mode, because it keeps the reader in the text.
- **The original text next to the translation.** Hebrew and Greek texts are available and already used in this repo's data work. Showing the original alongside the Indonesian, with its own readings, lets a reader see what the translators were working from rather than taking it on trust.
- **Explain the grammar in plain words.** A morphology code such as `V-Qal-Perf-3ms` means nothing to most readers. Expanding it into ordinary Indonesian is what makes grammar information useful to someone who has not studied Hebrew.

### 6.3 Bigger efforts

- **Aligning a whole translation to the original, word by word.** The annotations in use now are prepared verse by verse. Doing it for a complete Indonesian translation is the hard part, and every feature above gets better the more of it exists. This deserves to be treated as its own project with its own review process, because an alignment error does not just look wrong, it teaches the reader something untrue.
- **Word study that works across translations.** Someone comparing two translations should be able to follow a single original word through both. That needs alignment data for each translation and a way to map between them.
- **Reading plans that teach vocabulary.** Once word study data exists, a plan can bring a reader back to the same important word on purpose, spaced out over days. This is the point where the app stops being a text viewer and starts teaching.

### 6.4 One thing to be careful about

Annotations carry authority they have not earned. A reader who sees a Strong's number over a word will believe the original word means what the dictionary says, in that verse, in that sense. Wrong or careless data is worse than no data, because it is confidently wrong and the reader has no way to check it. Any new annotation data needs a review step before it ships, and the app should make it clear which version an annotation came from.
