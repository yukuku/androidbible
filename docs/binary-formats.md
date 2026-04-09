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

| Section | Content |
|---------|---------|
| `versionInfo` | Metadata: shortName, longName, description, locale, buildTime, book_count, hasPericopes, textEncoding |
| `booksInfo` | Per-book metadata (names, chapter counts, verse counts) |
| `text` | Verse text data, may be Snappy-compressed. Organized by book/chapter with offset tables for random access |
| `xrefs` | Cross-reference entries |
| `footnotes` | Footnote entries |
| `pericopies` | Section headers (pericopes) with ARI positions |

### Text Encoding

- ASCII (encoding = 1) or UTF-8 (encoding = 2)
- Section attributes use Bintex encoding
- Section content is raw binary (optionally Snappy-compressed)

### Version Detection

`YesReaderFactory` checks the first 8 bytes:
- YES2 header → `Yes2Reader`
- YES1 header (legacy) → `Yes1Reader`

## Bintex — Binary Serialization Format

A compact binary encoding used within YES2 sections and RPB files.

### Types

| Type | Encoding |
|------|----------|
| `int` | 4 bytes, big-endian |
| `uint8` | 1 byte |
| `string` | length-prefixed (int32 length, then UTF-8 bytes) |
| `int[]` | int32 count, then count × int32 values |
| `valueString` | Like string but with variable-length encoding |
| `simpleMap` | Key-value pairs with type tags |

### Variable-Length Integers

Bintex uses a variable-length encoding for some integer types:
- Small values use fewer bytes
- Supports both 8-bit and 16-bit string character encoding
- Thread-local buffers for read performance

### Files

- `BintexReader/` module — reading
- `BintexWriter/` module — writing

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
