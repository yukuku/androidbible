# Ruby text for Bible verses

Ruby is small annotation text set above a run of base text: furigana over kanji, pinyin over hanzi, a Strong's number over an English word. This document collects what the feature can be used for, where open data comes from, how the annotation is encoded in the `.yet` source format and in verse text, and how the Compose verse row renders it. Status: prototype, rendered by the Compose pipeline only (the "Verse (Compose)" experimental setting).

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
- Horizontal room comes from letter spacing on the base run when the reading is wider. A reading may hang one ruby em over a neighbouring character that carries no ruby (so three kana over one kanji do not push the kanji away from its okurigana), and must keep a small gap from a neighbouring reading. For CJK bases the spacing is the conventional look; for Latin bases it spreads the letters of the word, which is acceptable for a study display but a proper implementation would pad the run instead.
- A base run broken across lines gets a proportional slice of its reading on each line. A word joiner between base characters would prevent the break but change offsets, so it is not used.
- Reading size is half the verse size, drawn in the color of the base character under the reading's centre, so a highlight or red-letter boundary inside a base run picks whichever side holds most of the reading.

## 5. Open items

- Settings: a per-version toggle to hide ruby, a size ratio, and a choice between ruby and inline parentheses for accessibility.
- View pipeline: `VerseRenderer` needs an equivalent (a `ReplacementSpan` or a custom `LineHeightSpan` plus overlay) or the reader must be Compose-only before ruby presets go to production.
- Selection and copy: copy uses `removeSpecialCodes`, so the reading is dropped; an option to copy "base(reading)" would help language learners.
- Search: readings are not indexed. Indexing them would let users search by pinyin or kana.
- Performance: `widenRubyBases` measures two strings per ruby range on every bind. A verse with 40 rubies costs 80 measurements; cache per (text, style) if this shows up in profiles.
- Data quality: the theWord pinyin has typos and nonstandard tone placement (normalised by the aligner); roughly 4% of verses fall back to pypinyin. A Bible name dictionary for pypinyin would fix most heteronym errors.
