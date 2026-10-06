# Bible versions: YET file format

A YET file is a plain-text source for a Bible translation or version. It
contains version information, book names, and verse text. It can also include
section headings (pericopes), parallel passages, footnotes, cross-references,
and text formatting.

Convert `.yet` source into a `.yes` binary file before opening it in
Alkitab / Quick Bible. The app reads YES files; it does not import YET source
directly. See [Binary Formats](../binary-formats.md) for the runtime format.

## Prepare a text file

Use a plain-text editor such as [Visual Studio Code](https://code.visualstudio.com/),
[Sublime Text](https://www.sublimetext.com/), or Notepad++. A word-processing
document such as `.docx` is not a YET file. You need the Bible text you want
to process and permission to distribute it if you plan to share the result.

You can also use tools with Quick Bible YET export support, such as
[BibleMultiConverter](https://github.com/schierlm/BibleMultiConverter/) or
[Bibledit](https://bibledit.org/).

Save the file with:

- UTF-8 encoding without a byte order mark (BOM).
- LF line endings (`\n`, byte `0x0a`), rather than CRLF (`\r\n`).
- One tab character (`\t`, byte `0x09`) between fields.
- The `.yet` extension, rather than `.txt`.

If you prefer a spreadsheet, copy its cells into a plain-text editor. Check
that the pasted columns are separated by tabs and that the editor saves
UTF-8 with LF line endings.

The record types are:

| Record | Required? | Contents |
| --- | --- | --- |
| `info` | Yes | Version metadata |
| `book_name` | Yes | Names of the books present in this version |
| `verse` | Yes | One record per verse |
| `pericope` | No | Section heading at the beginning of a verse |
| `parallel` | No | Parallel passage for the preceding heading |
| `footnote` | No | Footnote addressed from a verse |
| `xref` | No | Cross-reference addressed from a verse |

The examples in this guide use actual tabs in record lines. Angle-bracketed
fields in syntax diagrams, such as `<book>`, are placeholders.

Sample YET files:

- [KJV with red letters](https://drive.google.com/file/d/0B0mZXH9nEuQ0SEtKM1poR2lFZlU/view).
- [KJV without red letters](https://drive.google.com/file/d/0B0mZXH9nEuQ0YmhkYkphOG9Pdms/view).
- [Indonesian TSI with footnotes and cross-references](https://drive.google.com/file/d/1Dez7jFhZQTMXEwivJlKFgv7UYc9BtwyP/view).
- [The repository's dummy Bible](../../tools/in-ddd/in-ddd.yet), which exercises
  formatting without distributing a real translation.

## Convert YET to YES

Install a Java runtime, then obtain `YetToYes2.jar` from the
[prebuilt converter folder](https://drive.google.com/drive/folders/0B0mZXH9nEuQ0dGdxbUI5T1lyeUU?resourcekey=0-V_emMiw0Q1APka5ddsS2rA&usp=sharing).
Open a terminal in the directory containing the jar and run:

```sh
java -jar YetToYes2.jar ABCV.yet ABCV.yes
```

The second argument specifies the output path. If it is omitted, the tool
writes a `.yes` file next to the input `.yet` file:

```sh
java -jar YetToYes2.jar ABCV.yet
java -jar YetToYes2.jar --help
```

After a successful conversion, copy the YES file to the device and open it
from the app's Versions screen. See [Tools](../tools.md) for converter options
and the desktop source project.

## Convert YET to the built-in format

A custom app build also needs a default Bible version. Use `YetToInternal.jar`
from the same converter folder to produce its internal assets:

```sh
java -jar YetToInternal.jar ABCV.yet
java -jar YetToInternal.jar --help
java -jar YetToInternal.jar --prefix xyz ABCV.yet
```

By default, this creates a directory named `ABCV` beside `ABCV.yet`. Without
`--prefix` (or `-p`), filenames use the prefix `ddd`.

For the open-source `plain` flavor, place the generated files in
`Alkitab/src/plain/assets/internal/`. If you change the prefix or version
metadata, update the `<internal>` declaration in
[app_config.xml](../../Alkitab/src/main/res/xml/app_config.xml) accordingly.
Production flavors use the proprietary asset overlay described in
[Build System](../build-system.md); their text does not come from the plain
flavor's assets.

## Version info

Put the version metadata at the beginning of the file:

```text
info	<key>	<value>
```

| Key | Required? | Meaning |
| --- | --- | --- |
| `longName` | Yes | Full translation name, such as `Terjemahan Baru` or `English Standard Version` |
| `shortName` | No | Abbreviation, usually uppercase, such as `TB`, `ESV`, or `KJV` |
| `description` | No | Longer description, which can include copyright information |
| `locale` | No | Two-letter language code, or a three-letter code where needed |

Use `in` for Indonesian rather than `id` in the `locale` field.

```text
info	shortName	KJV
info	longName	King James
info	description	King James Version (Authorized Version 1611)
info	locale	en
```

## Book names

Name every book included in the version. Omit books that are unavailable:

```text
book_name	<book_id>	<book_name>
```

The standard book numbers run from `1` (Genesis) to `66` (Revelation). Use
concise names, such as `1 Corinthians` rather than `First Letter of Paul to
the Corinthians`.

```text
book_name	1	Genesis
book_name	2	Exodus
```

You can add an optional abbreviation, used in grid navigation and typed
references. Aim for no more than three single-width characters (such as
Latin letters), or two double-width characters (such as Chinese characters):

```text
book_name	<book_id>	<book_name>	<book_abbreviation>
```

```text
book_name	1	创世纪	创
book_name	2	出埃及记	出
book_name	9	撒母耳记上	撒上
book_name	10	撒母耳记下	撒下
```

For additional books, use the [book-number table](../../publication/doc/book%20numbers.txt).
For a book not listed there, the source format reserves numbers `201` through
`255`. These are one-based YET numbers. In ARI references, subtract one to
obtain the book ID.

## Bible text

Each verse occupies one physical line:

```text
verse	<book>	<chapter>	<verse>	<verse_text>
```

Use the book numbers above. Chapter and verse numbers start at `1` and have
a maximum of `255`. Keep chapters and verses in order. Start each included
book at chapter 1, verse 1, and do not skip or duplicate verse numbers.
Represent a missing verse with an empty final field, retaining its preceding
tab. The app skips blank verse text.

Use formatting tags for line breaks within a verse; a physical newline
starts a new record. If the translation needs no headings, footnotes,
cross-references, or formatting, the required records above are sufficient.

## Pericope headings and parallel passages

A pericope is a section heading addressed by book, chapter, and verse. It
appears before that verse; headings in the middle of a verse are not supported.

```text
pericope	<book>	<chapter>	<verse>	<title>
parallel	<parallel_spec>
```

Use the same numbering as the `verse` records. A title may contain italic
tags (`@9` and `@7`), but no other formatting tags. Put each optional
`parallel` record after the heading it belongs to. Several headings, each
with their parallel passages, can precede the same verse.

A parallel specification consists of a reference and its display text:

| Form | Example | Notes |
| --- | --- | --- |
| Plain display text | `Luk. 3:1` | The app guesses the reference using the current version's book names |
| OSIS | `@o:Luke.3.1 Luk. 3:1` | Uses standard OSIS book names |
| ARI | `@a:257 Gen. 1:1` | Uses a zero-based book ID packed with chapter and verse |
| KJV LID | `@lid:1 Gen. 1:1` | Uses KJV sequential verse numbering, from 1 to 31102 |

Explicit references are more reliable than guessed display text. Ranges are
also supported:

```text
parallel	@o:John.3.16-John.3.18 John 3:16-18
parallel	@a:0x000101-0x000115 Gen. 1:1-21
parallel	@lid:1-21 Gen. 1:1-21
```

Example heading with a parallel passage:

```text
pericope	10	3	2	Para putrane kakung … Dawud
parallel	@o:1Chr.3.1-1Chr.3.4 1Bb 3:1-4
```

See [Opening verses](opening-verses.md#specify-the-target) for ARI and LID
and [Reading plans](reading-plans.md#osis-book-names) for standard OSIS names.

## Footnotes

Store each footnote separately from the verse text:

```text
footnote	<book>	<chapter>	<verse>	<index>	<content>
```

The address identifies the verse containing the footnote marker. Within
each verse, number footnotes consecutively from `1`; indices fit in the range
`1` through `255`. Content can include italics (`@9` / `@7`), verse links
such as `@<to:Gen.2.1@>Going to Gen 2:1@/`, and URLs beginning with `http://`
or `https://`.

```text
footnote	40	3	7	1	@@@9Pharisees@7 is a Jewish group that...
footnote	40	3	7	2	@@@9Sadducees@7 is a leader of the Jewish religion...
```

Insert `@<f1@>@/`, `@<f2@>@/`, and so on into the verse to refer to the
entries. Begin the verse text with `@@` so the renderer processes the tags:

```text
verse	40	3	7	@@But when he saw many of the Pharisees@<f1@>@/ and Sadducees@<f2@>@/ coming to where he was baptizing, he said to them: "You brood of vipers! Who warned you to flee from the coming wrath?
```

The app displays clickable footnote markers after “Pharisees” and “Sadducees”.
Every marker must have a corresponding footnote record.

## Cross-references (xrefs)

A cross-reference links a verse to a related passage, for example an Old
Testament passage quoted in the New Testament:

```text
xref	<book>	<chapter>	<verse>	<index>	<content>
```

Number references consecutively from `1` within each verse, separately from
the footnote indices. Indices range from `1` through `255`. Use italic tags
and the verse-link tags below in the content.

For Acts 1:20, the first reference points to Psalm 69:25 and the second to
Psalm 109:8:

```text
verse	44	1	20	@@@^Peter continued, "This was written in the book of Psalms, where it says, @1'Let his home become desolate, @2with no one living in it.'@<x1@>@/@0It also says, @1'Let someone else take his position.'@<x2@>@/
xref	44	1	20	1	@<ta:1197337@>Ps. 69:25@/
xref	44	1	20	2	Taken from @<ta:0x126d08@>Ps. 109:8@/
```

This renders as indented quotations with a clickable cross-reference marker
at each `@<xN@>@/` location. Every marker needs a matching `xref` record.

### Verse links

The link syntax is `@<t<target>@><display_text>@/`, where the target uses
`a:`, `o:`, or `lid:`. For ARI, book IDs are zero-based:

```text
1197337 = 18 * 65536 + 69 * 256 + 25 = Psalm 69:25
0x126d08 = 18 * 65536 + 109 * 256 + 8 = Psalm 109:8
```

You can use decimal or `0x` hexadecimal ARI values, standard OSIS identifiers,
or KJV LIDs:

```text
xref	44	1	20	1	@<to:Ps.69.25@>Ps. 69:25@/
xref	44	1	20	2	Taken from @<tlid:15764@>Ps. 109:8@/
```

Hyphens and commas specify ranges and separate passages:

```text
xref	58	4	7	1	@<ta:1203975-1203976@>Ps. 95:7-8@/; @<ta:3736327-3736328,3736333,3736577@>Heb. 3:7-8, 13; 4:1@/
```

## Text formatting

Plain verse text needs no tags. For formatted verse text, start with `@@`.
Tags use `@` followed by a character, or an inline element of the form
`@<...@>...@/`. Formatting resets at the beginning of every verse.

### Paragraph tags

| Tag | Effect |
| --- | --- |
| `@0` | Start a new line with no indentation |
| `@1` | Start a new line with one indentation level |
| `@2` | Start a new line with two indentation levels |
| `@3` | Start a new line with three indentation levels |
| `@4` | Start a new line with four indentation levels |
| `@^` | Start a new paragraph |

Each verse begins at indentation level zero, so an initial `@0` is
unnecessary. Use it to return to zero after an indented line. Reserve the
deeper levels for text that needs them, such as poetry.

Use `@^` at the start of a verse when it begins a new paragraph, including
verse 1 of a chapter, or in the middle of a verse for a paragraph break.

```text
@@@1Lihatlah laut itu, besar dan luas wilayahnya, @2di situ bergerak, tidak terbilang banyaknya, @2binatang-binatang yang kecil dan besar.
```

This displays the first line at one indentation level and the next two
lines at two levels:

```text
    Lihatlah laut itu, besar dan luas wilayahnya,
        di situ bergerak, tidak terbilang banyaknya,
        binatang-binatang yang kecil dan besar.
```

```text
@@@^Teofilus yang mulia, @^Banyak orang telah berusaha menyusun suatu berita tentang peristiwa-peristiwa yang telah terjadi di antara kita,
```

This separates the greeting and the following sentence into two paragraphs.

### Character tags

| Start | End | Effect |
| --- | --- | --- |
| `@6` | `@5` | Words of Jesus, usually displayed in red |
| `@9` | `@7` | Italics, for example words added by the translator |
| `@8` | None | Force a line break |

Restart red-letter or italic formatting in each verse that needs it; a
previous verse's formatting does not carry over.

Example verse text with poetry and line breaks:

```text
@@@1Apabila Engkau mengirim roh-Mu, mereka tercipta, @2dan Engkau membaharui muka bumi. @8
@@@1Biarlah habis orang-orang berdosa dari bumi, @2dan biarlah orang-orang fasik tidak ada lagi! @8@1Pujilah TUHAN, hai jiwaku! @2Haleluya!
```

Rendered layout:

```text
    Apabila Engkau mengirim roh-Mu, mereka tercipta,
        dan Engkau membaharui muka bumi.

    Biarlah habis orang-orang berdosa dari bumi,
        dan biarlah orang-orang fasik tidak ada lagi!

    Pujilah TUHAN, hai jiwaku!
        Haleluya!
```

Example with red-letter text in consecutive verses:

```text
@@Dan kata-Nya lagi: @6"Aku berkata kepadamu, sesungguhnya tidak ada nabi yang dihargai di tempat asalnya.@5
@@@6Dan Aku berkata kepadamu, dan kata-Ku ini benar: Pada zaman Elia terdapat banyak perempuan janda di Israel ketika langit tertutup selama tiga tahun dan enam bulan dan ketika bahaya kelaparan yang hebat menimpa seluruh negeri.@5
```

Each verse starts its own red-letter span. The formatting tags are hidden in
the rendered text. See [Text Rendering](../text-rendering.md) for the rendering
pipeline and the [ruby design](../features/ruby/design.md) for additional
annotations supported by the Compose renderer.

## OSIS book names

Old Testament:

```text
Gen Exod Lev Num Deut Josh Judg Ruth 1Sam 2Sam 1Kgs 2Kgs 1Chr 2Chr Ezra Neh
Esth Job Ps Prov Eccl Song Isa Jer Lam Ezek Dan Hos Joel Amos Obad Jonah Mic
Nah Hab Zeph Hag Zech Mal
```

New Testament:

```text
Matt Mark Luke John Acts Rom 1Cor 2Cor Gal Eph Phil Col 1Thess 2Thess 1Tim
2Tim Titus Phlm Heb Jas 1Pet 2Pet 1John 2John 3John Jude Rev
```

## Contact

Send format questions or translations you are permitted to distribute to
[help@alkitab.app](mailto:help@alkitab.app).
