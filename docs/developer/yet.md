# YET file format specification

A YET file is a plain text file for a translation or version of the Bible. It will always contain the version information, book names, and the verse text. Optionally, it can also include pericope headings, footnotes, and cross references.

A YET file can be converted into a YES binary file with the .yes file extension that [Alkitab / Quick Bible](https://www.alkitab.app) app can open natively.

## About editing text files

You need a good plain text editor, such as [Visual Studio Code](https://code.visualstudio.com/), [Sublime Text](http://www.sublimetext.com/) or Notepad++. Do not use Microsoft Word or Wordpad. You will need access to the raw text of the Bible version you want to process.

Alternatively, you may want to use tools that support converting and exporting to Quick Bible YET format:

- [BibleMultiConverter](https://github.com/schierlm/BibleMultiConverter/) command-line tool

- Cloud editor on [bibledit.org](http://bibledit.org/)

When saving a YET file, please note the following:

- Save in **UTF-8 encoding without BOM** (byte order marker).

- The EOL (end-of-line) character must be **LF only ("\\n")** (single byte 0x0a, also known as **UNIX format**), not CRLF ("\\r\\n") (0x0d 0x0a also known as Windows or DOS format).

- Fields in a line must be separated with tab characters (**\\t**, 0x09).

  - Tip: You can also use Excel or similar apps to create a .yet file if you are more comfortable with it. After creating the spreadsheet, copy the cells and paste it to a text editor, which will result in columns separated by tabs.

- Use the file extension **.yet** when saving (not .txt)

The YET file contains the following sections:

- `info`: required

- `book_name`: required

- `verse`: required

- `pericope` (and `parallel`): optional

- `footnote`: optional

- `xref`: optional

Please refer to the YET file sections section for details.

**Sample files** — Here are some examples of YET files. Use a text editor to open them.

- [KJV with red letter](https://drive.google.com/file/d/0B0mZXH9nEuQ0SEtKM1poR2lFZlU/edit?usp=sharing)

- [KJV without red letter](https://drive.google.com/file/d/0B0mZXH9nEuQ0YmhkYkphOG9Pdms/edit?usp=sharing)

- [Indonesian TSI with footnotes and cross-references](https://drive.google.com/open?id=1Dez7jFhZQTMXEwivJlKFgv7UYc9BtwyP)

## Converting YET to YES files

YET files are the text files you can read, but in order to be read by the Alkitab / Quick Bible apps, they have to be converted to YES files.

### How to convert YET to YES file, the easy way

You will need to have [Java Runtime](https://www.azul.com/downloads/?package=jdk#zulu) installed on your computer. Download [**YetToYes2.jar**](http://goo.gl/eV83IS). Open the command-line prompt or Terminal, go to the same directory as **YetToYes2.jar** file and run:

```text
java -jar YetToYes2.jar input_file output_file
```

For example:

```text
java -jar YetToYes2.jar ABCV.yet ABCV.yes
```

If the conversion is successful, you will get the YES file in the same directory as the YET file.

### How to convert YET to internal format

If you want to build your own version of the Quick Bible app from the [sources](https://github.com/yukuku/androidbible), there will be a default translation that you have to include in the app.

You will need to create a YET file and then convert it to an internal format by using the [**YetToInternal.jar**](https://goo.gl/f2HtLC) tool. This tool generates a folder containing the internal files that you should copy to `Alkitab/src/main/assets/internal` folder.

How to run it is similar to how to run YetToYes2.jar above. For example:

```text
java -jar YetToInternal.jar ABCV.yet
java -jar YetToInternal.jar --help  (to access help)
java -jar YetToInternal.jar ABCV.yet -p xyz (to change default prefix ddd to xyz prefix)
```

A folder called `ABCV` will be created and it contains the internal files.

## YET file sections

### Version info

Information about the current bible version is specified at the beginning of the file.

The format of the version info line is as follows:

```text
info	<key>	<value>
```

Key is one of the predefined strings:

- `longName` (required): The non-abbreviated name of the translation. “Terjemahan Baru”, “English Standard Version”, “American Standard”.

- `shortName` (optional): All-capital abbreviated name. “TB”, “ESV”, “KJV”.

- `description` (optional): Longer description of the version, might include copyright info as well.

- `locale` (optional): 2 letter (preferred) or 3 letter language code. For historical reasons, use “in” for Indonesian, not “id”.

Example:

```text
info	shortName	KJV
info	longName	King James
info	description	King James Version (Authorized Version 1611)
info	locale	en
```

### Book names

All available books must be named. Unavailable books (e.g. OT books) need not be named.

The format of the book name line is as follows:

```text
book_name	<book_id>	<book_name>
```

book\_id is a number from 1-66: 1 for Genesis, 66 for Revelation.

book\_name is the name of the book. Please use short names (“1 Corinthians” instead of “First letter of Paul to the Corinthians”)

Example:

```text
book_name	1	Genesis
book_name	2	Exodus
```

Optionally, you may include abbreviations for the book names. Please keep it to 3 characters for single-width characters (Latin etc.) or 2 characters for double-width characters (Chinese etc.) This is particularly useful for Non-English translations. Abbreviations will be shown in the grid navigation, and users will be able to navigate that Bible version by manually typing in the abbreviations.

```text
book_name	<book_id>	<book_name>	<book_abbreviation>
```

Example:

```text
book_name	1	创世纪	创
book_name	2	出埃及记	出
…
book_name	9	撒母耳记上	撒上
book_name	10	撒母耳记下	撒下
```

If you add more books e.g. Apocryphal books, please number them according to this [book number scheme](https://github.com/yukuku/androidbible/blob/develop/publication/doc/book%20numbers.txt). If the book you want to add is not listed there, you can use any other number starting from 201 up to 255.

### Bible text

Each verse is on its own line. (New-lines in a verse are specified using a tag, more below.)

The format of the verse line is as follows:

```text
verse	<book>	<chapter>	<verse>	<verse_text>
```

Book is a number from 1 to 66: 1 for Genesis, 66 for Revelation.

(Other books follow this [book number scheme](https://github.com/yukuku/androidbible/blob/develop/publication/doc/book%20numbers.txt).)

Chapter starts from 1, max 255

Verse starts from 1, max 255

No duplicated or skipped verse numbers are allowed. Use a blank verse\_text for any skipped verses. In the app, any blank verses will be skipped.

If your Bible version does not have any special text formatting, pericope titles or parallel verses, then you are done! The rest of the document will describe how to write those extra content.

### Pericope headings

Because pericope headings are addressed using book-chapter-verse, they are only available at the beginning of verses. Currently, a new pericope heading at the middle of a verse is not supported.

It may also contain one or more parallel verse ranges for the pericope.

The format of the pericope heading is as follows:

```text
pericope	<book>	<chapter>	<verse>	<title>
```

Book, chapter, and verse follows the numbering in verse text

Title is the title of the pericope. It may not contain any formatting tags except italics `@9` and `@7`.

then, optionally followed by one or more parallel lines:

```text
parallel	<parallel_spec>
```

parallel\_spec can be one of the following:

- A plain text like `Luk. 3:1` or `John 3:16-18` The actual verse reference will be guessed based on the book names of the current version. This is the simplest format, but it’s not 100% guaranteed that the correct verse will be linked.

- OSIS ID (book.chapter.verse or book.chapter.verse-book.chapter.verse) specified before the displayed text. The book names are standardized. This must be written as `@o:Luke.3.1 Luk. 3:1` or `@o:John.3.16-John.3.18 John 3:16-18`. See below for list of OSIS book names.

- BCV integer with (book number that starts from 0 (Genesis) to 65 (Revelation)) \* 65536 + (chapter number starting from 1) \* 256 + (verse number starting from 1). This must be written as `@a:257 Gen. 1:1` or `@a:0x000101-0x000115 Gen. 1:1-21`

- LID (1-31102 following KJV versification) specified before the displayed text. This must be written as `@lid:1 Gen. 1:1` or `@lid:1-21 Gen. 1:1-21`

Example:

```text
pericope	10	3	2	Para putrane kakung … Dawud
parallel	@o:1Chr.3.1-1Chr.3.4 1Bb 3:1-4
```

Note: You can have more than one pericope header (and its "parallel" lines) per verse. They will be displayed sequentially above the verse.

### Footnotes

Footnotes are used to add more information about a word or a phrase, from explaining the meaning of it to clarifying translation decisions. Footnotes are stored not inline within the text, but on a separate “table”. Each footnote entry has its own line. The format of the footnote line is:

```text
footnote	<book>	<chapter>	<verse>	<index>	<content>
```

Book, chapter, and verse is where the footnote is referred from.

The index is a number starting from 1 for each verse, that increases sequentially if there is more than one footnote on a verse.

The content may contain formatting tags as follows:

- Italics (`@9` and `@7`).

- Links to other verses, e.g. `@<to:Gen.2.1@>Going to Gen 2:1@/`

- URLs that have to start with http:// or https://.

For example, a footnote on Matt. 3:7:

```text
footnote	40	3	7	1	@@@9Pharisees@7 is a Jewish group that...
footnote	40	3	7	2	@@@9Sadducees@7 is a leader of the Jewish religion...
```

Then, to refer to that footnote, a special tag is inserted inline on the verse\_text as `@<fx@>@/` where x is the index of the footnote as above. Remember to have `@@` at the beginning of the verse\_text if that verse contains a footnote reference.

For example, on the verse text section of Matt. 3:7:

```text
verse	40	3	7	@@But when he saw many of the Pharisees@<f1@>@/ and Sadducees@<f2@>@/ coming to where he was baptizing, he said to them: "You brood of vipers! Who warned you to flee from the coming wrath?
```

It will be rendered as:

```text
But when he saw many of the Pharisees1 and Sadducees2 coming to where he was baptizing, he said to them: "You brood of vipers! Who warned you to flee from the coming wrath?
```

### Cross references (xrefs)

Cross references are used to provide a link to another verse that is relate to the current verse. The most common use is providing a link to an Old Testament verse on a New Testament verse where the Old Testament verse is quoted.

Each cross-reference entry has its own line. The format of the cross-reference line is:

```text
xref	<book>	<chapter>	<verse>	<index>	<content>
```

Book, chapter, and verse is where the cross-reference entry is referred from.

The index is a number starting from 1 for each verse that increases sequentially if there is more than one cross-reference on a verse.

The content may contain formatting tags, but currently it’s limited to italics `@9` and `@7` only. To make a link to a certain verse, use the special tag as described below.

An example from Acts 1:20:

```text
Peter continued, "This was written in the book of Psalms, where it says,
    'Let his home become desolate,
        with no one living in it.'※
It also says,
    'Let someone else take his position.'※
```

The first cross-reference mark points to Ps. 69:25 and the second one to Ps. 109:8.

The verse is written in the .yet file as:

```text
verse	44	1	20	@@@^Peter continued, "This was written in the book of Psalms, where it says, @1'Let his home become desolate, @2with no one living in it.'@<x1@>@/@0It also says, @1'Let someone else take his position.'@<x2@>@/
```

And its corresponding xref lines:

```text
xref	44	1	20	1	@<ta:1197337@>Ps. 69:25@/
xref	44	1	20	2	Taken from @<ta:0x126d08@>Ps. 109:8@/
```

#### Verse links

Note that there is a special tag that refers to a verse. You can see the number 1197337 and 0x126d08 on the content above. 1197337 is 18 \* **65536** + 69 \* **256** + 25 which refers to Book 18 (Psalms, with book 0 as Genesis and 65 as Revelation), chapter 69, verse 25. The numbers 65536 and 256 are fixed. The numbers can also be written as lower-case hexadecimal prefixed with 0x. (Other books follow this [book number table](https://github.com/yukuku/androidbible/blob/develop/publication/doc/book%20numbers.txt), minus one since the book number here starts from zero)

Other than `a:`, it also supports `o:` for OSIS IDs (do not use your translation's book names) and `lid:` for KJV 1-31102 versification. You can choose the one you prefer. Example:

```text
xref	44	1	20	1	@<to:Ps.69.25@>Ps. 69:25@/
xref	44	1	20	2	Taken from @<tlid:15764@>Ps. 109:8@/
```

It also supports verse ranges separated by `-` and commas, e.g.:

```text
xref	58	4	7	1	@<ta:1203975-1203976@>Ps. 95:7-8@/; @<ta:3736327-3736328,3736333,3736577@>Heb. 3:7-8, 13; 4:1@/
```

## Text formatting

For verses without any formatting, just write the text as is. Note: *the formatting tags below are entirely optional*. Please ignore this documentation section if your Bible text is a plain text.

For verses with formatting, use the formatting tags below to format the verse text.

All formatting tags consist of the `@` character, followed by another character. Formatting tags are divided into 2 kinds: paragraph tags and character tags.

### Paragraph tags

`@0` starts a new line with indentation level set to zero

`@1` starts a new line with indentation level to single (“left side” in poems)

`@2` starts a new line with indentation level to double (“right side” in poems)

`@3` starts a new line with indentation level to triple (don’t use this if not needed)

`@4` starts a new line with indentation level to 4x (don’t use this if not needed)

Each verse starts as if it began with `@0`, and each verse is independent of the previous verses. `@0` is not needed at the beginning of the verse text. `@0` is used just to return the indentation to zero when the indentation has been adjusted to non-zero previously.

`@^` marks the beginning of the paragraph. This is used for:

- A verse that is of a different paragraph from the previous verse (so, put the `@^` at the beginning of the verse)

- A verse at the beginning of a chapter (verse 1) that starts a new paragraph (so, put the `@^` at the beginning of the verse)

- A new paragraph in the middle of a verse (so, put the `@^` at the middle of the verse)

Example verse\_text:

```text
@@@1Lihatlah laut itu, besar dan luas wilayahnya, @2di situ bergerak, tidak terbilang banyaknya, @2binatang-binatang yang kecil dan besar.
```

Rendered as:

```text
    Lihatlah laut itu, besar dan luas wilayahnya,               |
        di situ bergerak, tidak terbilang banyaknya,            |
        binatang-binatang yang kecil dan besar.                 |
```

Example verse\_text:

```text
@@@^Teofilus yang mulia, @^Banyak orang telah berusaha menyusun suatu berita tentang peristiwa-peristiwa yang telah terjadi di antara kita,
```

Rendered as:

```text
    Teofilus yang mulia,                       |
                                               |
    Banyak orang telah berusaha menyusun suatu |
berita tentang peristiwa-peristiwa yang telah  |
terjadi di antara kita,                        |
```

### Character tags

`@6` starts, and `@5` ends words of Jesus (usually red text)

`@9` starts, and `@7` ends italicized text (e.g. for translator's additional words inserted into the text)

`@8` forces a line break

Remember that formatting resets on every verse. So if you want to continue having red text or italic, start the verse with the appropriate tags.

Example verse\_text:

```text
@@@1Apabila Engkau mengirim roh-Mu, mereka tercipta, @2dan Engkau membaharui muka bumi. @8
@@@1Biarlah habis orang-orang berdosa dari bumi, @2dan biarlah orang-orang fasik tidak ada lagi! @8@1Pujilah TUHAN, hai jiwaku! @2Haleluya!
```

Rendered as:

```text
    Apabila Engkau mengirim roh-Mu, mereka tercipta,
        dan Engkau membaharui muka bumi.

    Biarlah habis orang-orang berdosa dari bumi,
        dan biarlah orang-orang fasik tidak ada lagi!

    Pujilah TUHAN, hai jiwaku!
        Haleluya!
```

Example verse\_text:

```text
@@Dan kata-Nya lagi: @6"Aku berkata kepadamu, sesungguhnya tidak ada nabi yang dihargai di tempat asalnya.@5
@@@6Dan Aku berkata kepadamu, dan kata-Ku ini benar: Pada zaman Elia terdapat banyak perempuan janda di Israel ketika langit tertutup selama tiga tahun dan enam bulan dan ketika bahaya kelaparan yang hebat menimpa seluruh negeri.@5
```

Rendered as:

```text
Dan kata-Nya lagi: "Aku berkata kepadamu, sesungguhnya tidak ada nabi yang dihargai di tempat asalnya.
Dan Aku berkata kepadamu, dan kata-Ku ini benar: Pada zaman Elia terdapat banyak perempuan janda di Israel ketika langit tertutup selama tiga tahun dan enam bulan dan ketika bahaya kelaparan yang hebat menimpa seluruh negeri.
```

### OSIS book names

**Old Testament books**

Gen Exod Lev Num Deut Josh Judg Ruth 1Sam 2Sam 1Kgs 2Kgs 1Chr 2Chr Ezra Neh Esth Job Ps Prov Eccl Song Isa Jer Lam Ezek Dan Hos Joel Amos Obad Jonah Mic Nah Hab Zeph Hag Zech Mal

**New Testament books**

Matt Mark Luke John Acts Rom 1Cor 2Cor Gal Eph Phil Col 1Thess 2Thess 1Tim 2Tim Titus Phlm Heb Jas 1Pet 2Pet 1John 2John 3John Jude Rev

## Contact

Questions about the file format and submitting your Bible translations can be addressed to help@alkitab.app.
