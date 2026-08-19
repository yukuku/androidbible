# Binary Formats

## YES2 (.yes) — Bible Version Format

The primary Bible text file format. Each `.yes` file contains one complete Bible version.

### File Structure

```
[Header]     8 bytes: 0x98 0x58 0x0d 0x0a 0x00 0x5d 0xe0 0x02
[Section Index]
  sectionIndex.size    (int32)
  sectionIndexVersion  (uint8, must be 1)
  section_count        (int32)
  For each section:
    sectionName          (length-prefixed string)
    offset               (int32)
    attributes_size      (int32)
    content_size         (int32)
    reserved             (4 bytes)
[Sections...]
[Footer]     1 byte: 0x00
```

### Sections

| Section       | Content                                                                                                   |
|---------------|-----------------------------------------------------------------------------------------------------------|
| `versionInfo` | Metadata: shortName, longName, description, locale, buildTime, book_count, hasPericopes, textEncoding     |
| `booksInfo`   | Per-book metadata (names, chapter counts, verse counts)                                                   |
| `text`        | Verse text data, may be Snappy-compressed. Organized by book/chapter with offset tables for random access |
| `xrefs`       | Cross-reference entries                                                                                   |
| `footnotes`   | Footnote entries                                                                                          |
| `pericopies`  | Section headers (pericopes) with ARI positions                                                            |

### Text Encoding

- ASCII (encoding = 1) or UTF-8 (encoding = 2)
- Section attributes use Bintex encoding
- Section content is raw binary (optionally Snappy-compressed)

### Version Detection

`YesReaderFactory` checks the first 8 bytes:
- YES2 header → `Yes2Reader`
- YES1 header (legacy) → `Yes1Reader`

## Internal Bible Format — Built-in Bible Version

The internal (built-in) Bible version is stored in `assets/internal/` as a set of plain text and Bintex binary files. This is the fallback version that ships with the APK. In the open-source `plain` build, the dummy `ddd_*` files come from `Alkitab/src/plain/assets/internal/`; production flavors get real Bible text via `CopyProprietaryAssetsTask` (see [build-system.md](build-system.md)), which copies from `$ALKITAB_PROPRIETARY_DIR/overlay/<applicationId>/text_raw/` into a generated assets directory.

### Configuration

The internal version is declared in `Alkitab/src/main/res/xml/app_config.xml`:
```xml
<internal locale="in" shortName="DDD" longName="Dummy Debug Data" prefix="ddd" preset_name='in-ddd' />
```

The `prefix` determines all filenames. Version ID is always `"internal"` (from `MVersionInternal.getVersionInternalId()`). The version is always active and always has data files.

### File Inventory

| File                                    | Format     | Purpose                                                      |
|-----------------------------------------|------------|--------------------------------------------------------------|
| `{prefix}_index_bt.bt`                  | Bintex     | Book/chapter/verse index with byte offsets into text files   |
| `{prefix}_k01.txt` – `{prefix}_k66.txt` | UTF-8 text | Verse text, one file per book (66 books), one verse per line |
| `{prefix}_pericope_index_bt.bt`         | Bintex     | ARI → offset mapping for pericope blocks                     |
| `{prefix}_pericope_blocks_bt.bt`        | Bintex     | Pericope titles and parallel passage references              |
| `{prefix}_footnotes_bt.bt`              | Bintex     | Footnote entries indexed by ARIF                             |
| `{prefix}_xrefs_bt.bt`                  | Bintex     | Cross-reference entries indexed by ARIF                      |

### Reader: `InternalReader.java`

Loaded via `VersionImpl.getInternalVersion()` → `InternalReader`. Key methods:
- `loadBooks()` — reads `{prefix}_index_bt.bt`, returns `InternalBook[]`
- `loadVerseText(book, chapter)` — seeks into `{prefix}_kNN.txt` using chapter offsets, splits on `\n`
- `loadPericope(bookId, chapter)` — reads pericope index + blocks for the chapter
- `getFootnoteEntry(arif)` / `getXrefEntry(arif)` — lazy-loaded from `.bt` files

### Index File (`{prefix}_index_bt.bt`)

```
uint8  version              // must be 3
uint8  book_count           // number of books (typically 66)

For each book:
  uint8              bookId           // 0-65
  valueString        shortName        // e.g., "Kej" (Kejadian)
  valueString        abbreviation     // e.g., "Kej"
  valueString        resName          // e.g., "ddd_k01" (filename without .txt)
  uint8              chapter_count
  uint8[chapter_count]       verse_counts      // verses per chapter
  varuint[chapter_count+1]   chapter_offsets   // byte offsets into the .txt file
```

`chapter_offsets[i]` is the byte position where chapter `i+1` begins. `chapter_offsets[chapter_count]` is the end-of-file offset (used to compute the last chapter's length).

### Text Files (`{prefix}_kNN.txt`)

UTF-8 plain text. One verse per line, separated by `\n` (0x0a). Lines may be empty for missing/skipped verses. Lines may contain inline formatting codes (see [Text Rendering](text-rendering.md)):

```
Normal text
@@@8@8Formatted verse with line breaks@8
@@@6Red letter text@5 normal text
@<f1@>footnote marker@/ and @<x2@>xref marker@/
```

Verse text is read by seeking to the chapter offset and reading `chapter_offsets[c+1] - chapter_offsets[c]` bytes, then splitting on `\n` via `OldVerseTextDecoder.Utf8.separateIntoVerses()`.

### Pericope Files

**Index (`{prefix}_pericope_index_bt.bt`):**
```
int  entry_count

For each entry:
  int  ari       // ARI of the verse this pericope precedes
  int  offset    // byte offset into the blocks file
```

Entries are sorted by ARI. Loading uses binary search to find all pericopes for a given chapter range.

**Blocks (`{prefix}_pericope_blocks_bt.bt`):**
Each block at the offset specified by the index:
```
uint8       version          // 3 (current) or 1-2 (legacy)
autostring  title            // section heading text (e.g., "The Creation")
uint8       parallel_count   // number of parallel passage references
autostring[parallel_count]   parallels   // e.g., "Matt 3:1-17"
```

Read by `Yes1PericopeBlock.read()`. `autostring` encoding is described in the Bintex section below.

### Footnote and Cross-Reference Files

Both `{prefix}_footnotes_bt.bt` and `{prefix}_xrefs_bt.bt` share the same format:
```
uint8   data_format_version   // must be 1
int     entry_count

int[entry_count]            arif       // ARIF keys (sorted)
int[entry_count]            offsets    // byte offsets into content area
valueString[entry_count]    contents   // entry text
```

**ARIF encoding:** Packs an ARI and a field index into a single int:
- Bits 31-8: ARI (book/chapter/verse)
- Bits 7-0: field index within that verse (1-based), allowing multiple entries per verse

Read by `XrefsSection.Reader` and `FootnotesSection.Reader`. Lookup uses unsigned binary search on the sorted ARIF array.

### Differences from YES2

| Aspect          | Internal                                 | YES2                                         |
|-----------------|------------------------------------------|----------------------------------------------|
| Verse text      | Plain UTF-8 text, one file per book      | Binary section, optionally Snappy-compressed |
| Index           | Bintex with chapter byte offsets         | Bintex section index with complex structure  |
| Reader class    | `InternalReader`                         | `Yes2Reader`                                 |
| Location        | `assets/internal/` (bundled in APK)      | App data directory (downloaded)              |
| Registration    | Hardcoded singleton via `app_config.xml` | `Version` table in SQLite                    |
| Pericope format | `Yes1PericopeIndex` (v2/v3)              | `Yes2PericopeIndex` (different encoding)     |

### Creation Tool

`tools/YetToInternal/` contains `YetToInternal.java` → `InternalCommon.createInternalFiles()`, which converts from YET (Yet Another Translation) source format to the internal file set.

---

## Bintex — Binary Serialization Format

A compact binary encoding used within YES2 sections, internal Bible files, and RPB reading plan files. All multi-byte integers are big-endian. Thread-local buffers (2048 bytes / 1024 chars) are used for read performance.

### Raw Types (No Type Tag)

| Function                         | Bytes | Encoding                               |
|----------------------------------|-------|----------------------------------------|
| `readInt()` / `writeInt()`       | 4     | 32-bit big-endian signed integer       |
| `readUint8()` / `writeUint8()`   | 1     | 8-bit unsigned                         |
| `readUint16()` / `writeUint16()` | 2     | 16-bit big-endian unsigned             |
| `readChar()` / `writeChar()`     | 2     | 16-bit big-endian unsigned (Java char) |
| `readFloat()` / `writeFloat()`   | 4     | IEEE 754 single-precision, big-endian  |

### Variable-Length Unsigned Integer (VarUint)

Encodes non-negative integers with 1-5 bytes. High bits of the first byte indicate the encoding length:

| Range                       | First Byte Pattern   | Total Bytes |
|-----------------------------|----------------------|-------------|
| 0 – 127                     | `0xxxxxxx`           | 1           |
| 128 – 16,383                | `10xxxxxx` + 1 byte  | 2           |
| 16,384 – 2,097,151          | `110xxxxx` + 2 bytes | 3           |
| 2,097,152 – 268,435,455     | `1110xxxx` + 3 bytes | 4           |
| 268,435,456 – 2,147,483,647 | `11110000` + 4 bytes | 5           |

### String Types (Raw, No Type Tag)

| Function                                 | Format                                                                                                                                                                                                                                                                    |
|------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `readShortString()`                      | LEN (1 byte) + LEN × 16-bit chars. Max 255 chars.                                                                                                                                                                                                                         |
| `writeLongString()`                      | LEN (4 bytes) + LEN × 16-bit chars.                                                                                                                                                                                                                                       |
| `readAutoString()` / `writeAutoString()` | KIND (1 byte) + LEN + data. KIND: `0x01` = 8-bit short (LEN=1 byte), `0x02` = 16-bit short (LEN=1 byte), `0x11` = 8-bit long (LEN=4 bytes), `0x12` = 16-bit long (LEN=4 bytes). 8-bit strings store 1 byte/char (ISO-8859-1); 16-bit strings store 2 bytes/char (UTF-16). |

### VALUE Types (Self-Describing with Type Tag)

Each value is prefixed by a type tag byte that indicates the encoding.

**VALUE Integer:**

| Tag              | Encoding                                          |
|------------------|---------------------------------------------------|
| `0x01`–`0x07`    | Immediate: the tag byte itself is the value (1–7) |
| `0x0e`           | Value 0                                           |
| `0x0f`           | Value -1                                          |
| `0x10` + 1 byte  | Unsigned 8-bit                                    |
| `0x11` + 1 byte  | Negative 8-bit (bitwise NOT of stored byte)       |
| `0x20` + 2 bytes | Unsigned 16-bit (big-endian)                      |
| `0x21` + 2 bytes | Negative 16-bit                                   |
| `0x30` + 3 bytes | Unsigned 24-bit                                   |
| `0x31` + 3 bytes | Negative 24-bit                                   |
| `0x40` + 4 bytes | Unsigned 32-bit                                   |
| `0x41` + 4 bytes | Negative 32-bit                                   |

**VALUE String:**

| Tag                 | Encoding                                                              |
|---------------------|-----------------------------------------------------------------------|
| `0x0c`              | null                                                                  |
| `0x0d`              | Empty string                                                          |
| `0x51`–`0x5f`       | 8-bit string, length = tag & 0x0f (1–15), followed by that many bytes |
| `0x61`–`0x6f`       | 16-bit string, length = tag & 0x0f (1–15), followed by length×2 bytes |
| `0x70` + 1-byte LEN | 8-bit string, length < 256                                            |
| `0x71` + 1-byte LEN | 16-bit string, length < 256                                           |
| `0x72` + 4-byte LEN | 8-bit string, any length                                              |
| `0x73` + 4-byte LEN | 16-bit string, any length                                             |

Writer automatically chooses 8-bit encoding if all chars are ≤ 0xFF, 16-bit otherwise.

**VALUE Int Array:**

| Tag                 | Encoding                     |
|---------------------|------------------------------|
| `0xc0` + 1-byte LEN | uint8 array, ≤ 255 elements  |
| `0xc1` + 1-byte LEN | uint16 array, ≤ 255 elements |
| `0xc4` + 1-byte LEN | int32 array, ≤ 255 elements  |
| `0xc8` + 4-byte LEN | uint8 array, any length      |
| `0xc9` + 4-byte LEN | uint16 array, any length     |
| `0xcc` + 4-byte LEN | int32 array, any length      |

Writer automatically picks the smallest element size that fits all values.

**VALUE Simple Map:**

| Tag                   | Encoding                                                                                                                                                          |
|-----------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `0x90`                | Empty map                                                                                                                                                         |
| `0x91` + 1-byte COUNT | Map with COUNT entries. Each entry: 1-byte key length + key bytes (8-bit chars) + VALUE (int, string, array, or nested map). Max 255 entries, keys max 255 chars. |

### Files

- `BintexReader/` module — `BintexReader.java`
- `BintexWriter/` module — `BintexWriter.java`

## RPB — Reading Plan Format

Binary format for reading plan files.

### Structure

```
[Header]  7 bytes: 0x52 0x8a 0x61 0x34 0x00 0xe0 0xea
[Version] 1 byte: 0x01
[Body]    Bintex-encoded:
  name         (string)
  title        (string)
  description  (string)
  duration     (int, number of days)
  url          (string, optional)
  For each day (duration times):
    verse_count  (int)
    ari_values   (int array, verse_count entries)
```

Each ARI value identifies a verse or verse range to read for that day.

## Snappy Compression

The `Snappy` module provides JNI bindings to the Snappy compression library (C++ native code). Used for compressing the `text` section of YES2 files. Compression is per-section, not per-verse — the entire text section is compressed as one block.

NDK is required to build this module (`jni/Android.mk` and `jni/Application.mk`).

## PDB — PalmBible+ Format (Import Only)

Legacy format from PalmBible+ for Palm OS. The `BiblePlus` module reads PDB files and converts them to YES2 format during import. Converted files are saved as `pdb-{name}.yes`.
