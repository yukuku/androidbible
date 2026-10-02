# Creating a reading plan (RPA)

A reading-plan source file is plain text with the `.rpa` extension. Like a
[YET file](yet.md), it contains records whose fields are separated by tabs.
Use UTF-8 encoding and LF (Unix) line endings. Use one tab between fields,
not spaces or several tabs.

A plan needs an `info` section and a `day` section. The uploader converts the
source into an `.rpb` binary file for the app. You do not install `.rpa` source
files directly in the app.

## Create a plan in your browser

Use the [reading-plan creator](https://alkitab.app/rp/create) to enter a title,
description, days, and readings without writing a tab-separated file by hand.
The page supports Indonesian and English. Choose **Preview** to review the
readings, then publish the plan through the upload page.

For custom tooling or an existing schedule in a text editor, use the RPA format
below and [upload the file](https://alkitab.app/rp/upload).

## A complete example

The separators in this example are actual tab characters:

```text
info	version	1
info	title	Two-day sample reading plan
info	description	Read Genesis and Matthew together
info	duration	2
day	1	Gen.1.1-Gen.1.10	Matt.1
day	2	Gen.1.11-Gen.1.20	Matt.2
```

Day 1 contains two readings: Genesis 1:1-10 and all of Matthew 1. Day 2
contains Genesis 1:11-20 and all of Matthew 2.

You can also examine [reading plans contributed by users](https://drive.google.com/drive/folders/1jVAM5-iVXStv_HbUjwC3UBA9qXIU45XA?usp=sharing).

## Info section

Each metadata record has this structure:

```text
info	<key>	<value>
```

All four fields below are required for conversion:

| Key | Example value | Meaning |
| --- | --- | --- |
| `version` | `1` | RPA file-format version; use `1` |
| `title` | `M'Cheyne Bible Reading Plan` | Title displayed in the app |
| `description` | `Daily Old Testament, New Testament, and Psalms or Gospels` | Description, contained on one line |
| `duration` | `365` | Number of days in the plan |

The duration must match the number of `day` records. A two-day example needs
`duration` set to `2`, even if it is adapted from a year-long plan.

## Day section

```text
day	<day_number>	<range>	<range>	...
```

Number days consecutively from `1` through `duration`. Put each reading range
in its own tab-separated field. Each range is either `start-end` or a single
reference. A single reference selects that verse, or that whole chapter if
its verse number is omitted or zero.

Each day can contain at most **127 reading ranges**. The binary format stores
two ARI values per range and uses one byte for the day's array length.

### Reference formats

| Format | Example | Meaning |
| --- | --- | --- |
| OSIS with a verse | `Gen.1.1` | Genesis 1:1 |
| OSIS without a verse | `Gen.1` | All of Genesis 1 |
| Decimal ARI | `256` | All of Genesis 1 (verse component is zero) |
| Hexadecimal ARI | `0x000100` | All of Genesis 1 |
| KJV LID | `lid:1` | Genesis 1:1 |

ARI uses a zero-based book ID packed with chapter and verse. See the
[ARI explanation](opening-verses.md#specify-the-target). Do not prefix ARI
references in an RPA file with `a:`; that prefix belongs to the verse-dialog
API. Use actual chapter and verse numbers supported by the Bible text. The
chapter must be at least `1`; chapter and verse components fit in one byte.

In a range, verse zero (or an omitted OSIS verse) means the first verse at
the start and the last verse at the end. For example, `Matt.1-Matt.3` selects
all three chapters.

### OSIS book names

Use these identifiers exactly, including capitalization. For example,
Exodus is `Exod`, not `Ex` or `Exo`.

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

For a one-chapter book, include chapter `1`. Write Jude's first verse as
`Jude.1.1` and its last verse as `Jude.1.25`. `Jude.25` denotes chapter 25,
so it does not mean verse 25. To select the whole book, use `Jude.1` or
`Jude.1.1-Jude.1.25`. The same rule applies to Obadiah, Philemon, 2 John,
and 3 John.

### Shortened range endings

Writing both endpoints in full is the clearest form:

```text
Gen.1.1-Gen.1.10
Matt.1-Matt.3
Eccl.1-Eccl.3
```

The current uploader also accepts these shortened endings when the start
is an OSIS reference:

| Range | Meaning |
| --- | --- |
| `Gen.1.1-10` | Genesis 1:1-10; a bare number is a verse in the starting chapter |
| `Gen.1.1-2.3` | Genesis 1:1 through 2:3; `chapter.verse` stays in the starting book |

`Matt.1-3` does **not** mean chapters 1 through 3: the bare `3` is interpreted
as verse 3 of chapter 1. Use `Matt.1-Matt.3` for a whole-chapter range.
These shortcuts are specific to the reading-plan uploader; use complete
OSIS endpoints for [verse-dialog targets](opening-verses.md#verse-ranges).

## Publish the plan

Upload your `.rpa` file at [alkitab.app/rp/upload](https://alkitab.app/rp/upload).
Preview the readings, check the references, and then publish the plan through
the form. See the [available plans](https://alkitab.app/rp) for existing
contributions.

To edit, replace, or delete a submitted plan, contact
[help@alkitab.app](mailto:help@alkitab.app).

## Common mistakes

- Use tabs between fields. In an editor with visible whitespace enabled,
  tabs and spaces should look different. The screenshot
  below shows tabs as horizontal lines.
- Match `duration` to the number of days, and keep day numbers consecutive.
- Include all four `info` keys and keep each metadata value on one line.
- Use `Zeph.3`, with a period, rather than `Zeph3`. The regular expression
  `[a-z]\d` can help locate a missing period.
- Review shortened range endings carefully. A search for `-\d+\b` finds
  them for inspection, but some are valid verse shortcuts.
- Use one tab-separated field per reading range, and stay within the
  127-range limit for a day.

![Visible tabs separating the fields of an RPA info section](images/reading-plans.png)

For the runtime format and progress tracking, see [Reading Plans](../modules/reading-plans.md)
and [Binary Formats](../binary-formats.md#rpb-reading-plan-format).
