# Portable Songs (REM-21): Parcelable → JSON

Design document for migrating song storage and distribution from non-portable binary formats (Android `Parcelable` on disk, Java serialization over the wire) to a portable, document-based JSON format.

- **Status:** design / feasibility
- **Date:** 2026-05-28
- **Related preview artifact** (open in a browser): [`song-editor.html`](./song-editor.html) — live 3-pane editor (txt → canonical JSON → app-faithful render) that parses **both** the legacy and the new `@doc` txt formats. Its sample dropdown includes "KRI 25 — legacy", "KRI 25 — @doc", and a refrain/scripture/row/size demo.

---

## 1. Motivation

Songs are currently stored and distributed in two opaque, non-portable binary formats:

1. **On-device storage** — each song is an Android `Parcel.marshall()` byte buffer in the Room `song_info.data` BLOB column (`dataFormatVersion = 3`).
2. **Download** — a song book is a gzipped Java `ObjectOutputStream` of `List<Song>`.

Both are tied to a specific runtime (Android `Parcel`, the JVM `Song`/`Lyric`/`Verse` class layout) and cannot be authored, inspected, validated, or consumed by any other system. Two concrete problems drive this work:

- **The Android 13 Parcel break.** The marshalled `Parcel` byte layout changed in Android 13: a 4-byte length prefix is now written after the `VAL_PARCELABLE` type tag, before the class-name string (see <https://medium.com/@yukuku/androids-parcel-serialized-format-was-changed-in-android-13-a7017c25a246>). A device that downloaded songs on Android < 13 and later upgraded to Android ≥ 13 can **fail to `Parcel.unmarshall()` its own stored BLOBs**. The platform `Parcel` is therefore unsafe for persistence across OS upgrades.
- **No portability / no rich layout.** The field-based `Song` model cannot express document layout (titles, prose paragraphs, side-by-side attribution, embedded scripture, media), and authors cannot control how a song is laid out when rendered.

This design replaces both payloads with one canonical JSON document model, adds a version-agnostic custom Parcelable decoder to rescue existing on-device data, and extends the song authoring `.txt` format to produce the new model.

---

## 2. Scope

In scope for this design:

- **On-device storage**: `song_info.data` becomes UTF-8 JSON; a custom decoder converts existing legacy Parcelable BLOBs on the fly.
- **Download wire format**: gzipped JSON replaces the gzipped Java-serialized `List<Song>`.
- **txt authoring** (`kidung-data`): the source `.txt` format is extended to express the new document model; existing legacy `.txt` files keep working unchanged.

Explicitly **out of scope** (unchanged by this design):

- **Web HTML rendering** (`alkitab-host`): the website keeps rendering from its existing pickled-dict pipeline. The canonical JSON is designed so the web can adopt it later without a schema change, but that migration is not part of this work.

Decisions taken (with rationale) at the end of §11.

---

## 3. Canonical JSON document model

One canonical JSON is used everywhere this design touches (on-disk storage, download, and the output of the txt generator). It is **document-based**: a song is an ordered list of layout `blocks` that the author controls, not a fixed set of fields.

### 3.1 Song object

```jsonc
{
  "code": "25",                 // identity / lookup key (required)
  "meta": {                     // generator-derived, denormalized for index/search
    "title": "Malam Kudus",
    "title_original": "Silent Night"
  },
  "blocks": [ /* Block[] */ ]
}
```

- `code` — **required**, the per-book lookup key (replaces the indexed `song_info.code` column).
- `meta` — **derived, never hand-authored**. `meta.title` is the text of the first block with role `title`; `meta.title_original` is the first block with role `title_original` (omitted if none). It exists so the song list, search prefilter, notifications, and DB indexes never have to parse the document. Single source of truth is `blocks`. Derivation happens once, at the point something *authors* the document (the generator emitting this JSON, or the app's on-device legacy→JSON converter) — a reader never re-derives `meta` from `blocks`, it trusts whatever `meta` the document carries.
- No per-song schema-version field (`v`, etc.) — a single song's document doesn't need one. Payload-level versioning lives one level up, on the container that carries it: the on-device `song_info.dataFormatVersion` column, and the download wrapper's `dataFormatVersion` (§3.7).

### 3.2 Blocks

A block is discriminated by `type`. **Every** block may carry an optional `size` (float; `1` = normal relative font size). `p` blocks additionally accept an optional line-level `align`.

| `type`      | Shape | Purpose |
|-------------|-------|---------|
| `p`         | `{ type:"p", role?, size?, align?, content: Line }` | A single line of text. `role` selects default formatting; `align` is line-level. |
| `row`       | `{ type:"row", size?, items: PBlock[] }` | Horizontal container; items are spread start → end (first = start/left, last = end/right). Used for "lyricist left / composer right". |
| `lyric`     | `{ type:"lyric", role?, size?, caption?: Line, verses: Verse[] }` | A lyric group (stanza set). |
| `scripture` | `{ type:"scripture", role?, size?, osis: string }` | Scripture reference(s) as an OSIS string, e.g. `"John.3.16; Rom.5.8"`. |
| `youtube`   | `{ type:"youtube", role?, size?, videoId: string }` | An embedded YouTube reference (single required `videoId`). |
| `gap`       | `{ type:"gap", size? }` | A blank vertical-space element (default height `1em`, scaled by `size`). No `content`/`role`/`align`. |

Notes:

- `p` holds **one** line (`content`), because in the `@doc` txt format each source line maps to one block. Multi-line sung/spoken text lives in `lyric` verses, not in a `p`.
- `align` values are **`start` / `center` / `end`** (writing-direction relative, not `left`/`right`).
- An unknown `type` or unknown `role` renders as a plain paragraph and is otherwise ignored — the format is **forward-compatible** (this removes the legacy "fatal on unknown header" limitation).

### 3.3 Verse

```jsonc
{ "kind": "normal" | "refrain" | "text", "marker"?: string, "lines": VerseLine[] }
```

- `kind` maps from the legacy `VerseKind` (`NORMAL`/`REFRAIN`/`TEXT`).
  - `normal` → numbered (positional within the group) and indented.
  - `refrain` → "Ref.:" label, **no number**, italic/indented.
  - `text` → spoken/instruction; **no number**, plain.
- `marker` — optional explicit label that overrides the positional number.
- The legacy `Verse.ordering` field is **dropped**: it was never used for display (numbering has always been positional).

### 3.4 Lines and inline styling

A `VerseLine` (an entry of `Verse.lines`) is **either** a `Line` **or** an object carrying line-level formatting:

```jsonc
// plain line:
"Malam Kudus, sunyi senyap,"
// line with inline styling:
[ {"text":"Sia","style":["u"]}, {"text":"pa yang b'lum lelap;"} ]
// line with line-level size/align:
{ "size": 1.3, "content": [ {"text":"Come to the "}, {"text":"river","style":["u"]} , {"text":","} ] }
```

- `Line = string | Span[]`. A line with no styling is emitted as a plain string; a line containing styled runs is a `Span[]`.
- `Span = { text: string, style?: ("u" | "b" | "i")[] }` — underline, bold, italic only. (No red-letter; songs do not use it.)
- A `VerseLine` may be wrapped as `{ size?, align?, content: Line }` to give one lyric line its own relative size / alignment.

`p.content` is a `Line` (it does not take the `{size,align,content}` wrapper — size/align live on the `p` block itself).

### 3.5 Roles and default formatting

`role` is an open vocabulary. Roles carry **default formatting** in the renderer, so authors set `size`/`align` only to override. Known roles:

| Role | Default rendering (from `song.css`) |
|------|-------------------------------------|
| `title` | bold, ~125%, centered |
| `title_original` | ~87.5%, centered |
| `tune` | sans, uppercase, floated to the end (right) |
| `musical` | sans, ~87.5% (the "1=Bes 6/8" key + time line) |
| `authors_lyric` | sans, ~87.5% (lyricist) |
| `authors_music` | sans, ~87.5% (composer) |
| `note` | spoken/rubric text |
| `copyright` | small, uppercase, dimmed (footer) |
| `body` (or absent) | default paragraph |

### 3.6 Scripture references

`scripture.osis` stores **stable OSIS identifiers** (`Book.Chapter[.Verse]`, ranges with `-`, multiple separated by `;`). Book names are **not** stored; they are localized at render time against the user's active Bible version (as today via `OsisBookNames` + `activeVersion().getBook(...).reference(...)`). Scripture can now appear as a positioned block anywhere in the document, not only as one song-global field.

### 3.7 Song-book download wrapper

A song book is downloaded as **one gzipped JSON document**: a `dataFormatVersion` marker plus the list of songs. This replaces the gzipped Java-serialized `List<Song>`.

```jsonc
{
  "dataFormatVersion": 5,
  "songs": [ /* Song[] (§3.1) */ ]
}
```

- `dataFormatVersion` — **required**, always `5` for this JSON payload shape. Same meaning, and same field name, as the `dataFormatVersion` the app already sends as a request query parameter to `get_songs` and stores per-row in `song_info.dataFormatVersion` (§6) — it's the version of *this specific payload's* format, not a per-song thing. A client that only understands `dataFormatVersion` values it doesn't recognize should refuse the payload rather than guess.
- `songs` — the song-book's songs, each a full song object (§3.1). Order is the book's display order.
- **No `book` property.** Book identity/metadata (`name`, `title`, `copyright`) is not repeated inside the payload — the client already has it before requesting the download (it's what selected *which* book to download in the first place: a `SongBookInfo` parsed from the `alkitab://…&name=…&title=…&copyright=…` download link, or looked up from the already-installed book being refreshed). Duplicating it inside the gzipped body would just be another place for it to drift from what the client displays.
- The whole wrapper is what gets gzip-compressed for transport (`Content-Encoding`-style, via `OptionalGzipInputStream` on the read side) — not each song individually.

On-device this is consumed by `SongBookUtil.deserializeSongs`, which gunzips, JSON-decodes into `SongDocumentJson.SongBookWrapper`, and returns `.songs` for `SongDb.storeSongs` to persist (each song JSON-encoded individually per row, §6 — the wrapper itself is not stored, only unwrapped).

### 3.8 Worked example — KRI 25 ("Malam Kudus / Silent Night")

Canonical block order mirrors the app `song.html` template (see §9): title, title_original, tune, authors `row`, scripture, musical, lyric groups.

```jsonc
{
  "code": "25",
  "meta": { "title": "Malam Kudus", "title_original": "Silent Night" },
  "blocks": [
    { "type": "p", "role": "title",          "content": "Malam Kudus" },
    { "type": "p", "role": "title_original",  "content": "Silent Night" },
    { "type": "p", "role": "tune",           "content": "STILLE NACHT" },
    { "type": "row", "items": [
      { "type": "p", "role": "authors_lyric", "content": "Joseph Mohr" },
      { "type": "p", "role": "authors_music", "content": "Franz X. Gruber" }
    ] },
    { "type": "p", "role": "musical",        "content": "1=Bes 6/8" },
    { "type": "lyric", "verses": [
      { "kind": "normal", "lines": [
        "Malam Kudus, sunyi senyap,",
        [ {"text":"Sia","style":["u"]}, {"text":"pa yang b'lum lelap;"} ],
        "ayah bunda yang tinggallah t'rus,",
        "jaga Anak yang Maha Kudus;",
        "Anak tidur tenang,",
        "Anak tidur tenang."
      ] }
      /* … verses 2, 3 … */
    ] },
    { "type": "lyric", "verses": [ /* English stanzas 1–4 … */ ] }
  ]
}
```

Load the "KRI 25 — legacy" or "KRI 25 — @doc" sample in [`song-editor.html`](./song-editor.html) to see the full document and its render.

---

## 4. Custom version-agnostic Parcelable decoder

A pure-Kotlin/JVM decoder (**no `android.os.Parcel`**) reads the marshalled `song_info.data` byte buffer directly. This is required so that post-OS-upgrade installs can read their pre-upgrade BLOBs — exactly the failure mode the Android 13 change introduces. It runs **on-device** (it does not need to run off-device for this design; the corpus is regenerated as JSON by `kidung-data`).

### 4.1 On-disk byte format

`SongDb.marshallSong` calls `song.writeToParcelCompat(dataFormatVersion, p, 0)` then `p.marshall()`. The `Song` is written field-by-field (so the top-level `Song` has **no** type tags); only the nested lists carry tags. The byte stream is (assuming `dataFormatVersion ≥ 2`, the only case in production — `dataFormatVersion = 3` is hard-coded for downloads):

```
writeString(code)
writeString(title)
writeString(title_original)
writeStringList(authors_lyric)        // int count (-1 if null) then N strings
writeStringList(authors_music)
writeString(tune)
writeString(keySignature)
writeString(timeSignature)
writeInt(lyrics.size or -1)
  for each Lyric (via writeValue):
    writeInt(4)                       // VAL_PARCELABLE
    [Android 13+: writeInt(byteLength)]   // <-- the only format divergence
    writeString("yuku.kpri.model.Lyric")
    writeString(caption)
    writeInt(verses.size or -1)
      for each Verse (via writeValue):
        writeInt(4)                   // VAL_PARCELABLE
        [Android 13+: writeInt(byteLength)]
        writeString("yuku.kpri.model.Verse")
        writeInt(ordering)            // present in bytes; decoder discards it
        writeInt(kind.value)          // 0=NORMAL, 1=REFRAIN, 2=TEXT
        writeStringList(lines)
writeString(scriptureReferences)      // only if dataFormatVersion >= 2
```

Platform wire facts the decoder must replicate:

- **Endianness / alignment.** Little-endian (all Android devices); everything is padded to a 4-byte boundary. (Marshalled parcels are not endianness-portable, but every supported Android device is LE.)
- **`writeInt`** — 4 bytes LE.
- **`writeString`** (UTF-16 / `writeString16`) — `int` length = number of UTF-16 code units; **null is length `-1`**; otherwise that many UTF-16 chars **plus a NUL terminator char**, then pad to 4 bytes.
- **`writeStringList`** — `int` count (`-1` if the list is null), then each element via `writeString`.
- **`writeList`** (the nested `Parcelable` lists) — `int` size (`-1` if null), then per element a `writeValue`: an `int` type tag followed by the value.

### 4.2 Supported value types (and only these)

The decoder implements `readInt`, `readString`, `readStringList`, and a typed list reader that understands exactly two `writeValue` tags:

- `VAL_NULL` (`-1`) → a null element.
- `VAL_PARCELABLE` (`4`) → a `Lyric` or `Verse`, dispatched by the embedded class-name string.

Any other tag, or an unexpected class name, is a hard error (`throw IllegalStateException`). The decoder deliberately does not implement the full `Parcel` value protocol — it only decodes what songs use.

### 4.3 Android-13 detection

The only divergence between formats is the length prefix Android ≥ 13 inserts after the `VAL_PARCELABLE` tag, before the class-name string. Type tags first appear at the first `Lyric` element of the `lyrics` list. Detect once, then lock the format for the whole buffer (a single device/OS wrote it all):

```
At the first VAL_PARCELABLE element, after reading the tag (4), peek the next int A:
  if A == 21 and the next 21 UTF-16 chars are a known class name
        ("yuku.kpri.model.Lyric" | "yuku.kpri.model.Verse"):
     format = LEGACY        // A was the class-name length
  else:
     format = ANDROID13     // A is the length prefix; the class-name string follows
```

Both class names are exactly 21 (`0x15`) chars; a real length prefix (the byte size of a serialized `Lyric`/`Verse`) is far larger, so collision is impossible. For subsequent elements the locked format determines whether an extra `int` length prefix precedes each class name. The length prefix is read and ignored (fields are decoded directly); it can optionally be used to validate or skip.

### 4.4 Test fixtures (cs.android.com)

We cannot capture real on-device BLOBs in this environment, so the decoder is unit-tested against **reference parcels generated from AOSP source**:

- Take `Parcel.java` / `Parcel.cpp` for a pre-13 platform and for 13+ from <https://cs.android.com>, and use them (or a faithful re-implementation of `writeValue` / `writeString16` / `writeList` for those versions) to produce golden byte buffers for representative songs in **both** layouts.
- Assert the decoder produces identical models from the pre-13 and 13+ buffers, including null fields, empty lists, multi-group lyrics, and refrain/text verses.
- **Required before shipping:** validate against a handful of buffers captured from real devices on Android 9 / 12 / 13 / 14, to confirm the UTF-16 + length prefix assumptions hold in practice.

### 4.5 Fallback

If the custom decoder throws, the app may fall back to the platform `Parcel.unmarshall()` path (which still works when the OS has not changed since download). A decode failure is logged with the row's `code`/`bookName` for diagnosis.

---

## 5. Legacy → document converter

The decoder yields the legacy field values; a converter maps them to canonical blocks in an order that reproduces the current app layout (see §9):

1. `title` → `p` role `title`
2. `title_original` → `p` role `title_original` (if present)
3. `tune` → `p` role `tune` (if present)
4. authors → a `row` with `p` role `authors_lyric` (lyricist, joined `"; "`) as the first item and `p` role `authors_music` (composer) as the last item; include only those present
5. `scriptureReferences` → `scripture` block (if present)
6. `keySignature` + `timeSignature` → `p` role `musical`, content = `[keySignature, timeSignature].filter(present).join(" ")` (if either present)
7. each legacy `Lyric` → a `lyric` block: `caption` preserved; each `Verse` becomes `{ kind, lines }` with `VerseKind` mapped and inline `<u>/<b>/<i>` parsed into spans (other raw text escaped); `Verse.ordering` dropped.

`code` becomes top-level `code`; `meta` is derived. This same mapping is what [`song-editor.html`](./song-editor.html) implements for legacy input, and what the on-device migration uses after decoding.

---

## 6. On-device migration

**Lazy, on-read** — no bulk migration pass.

- The Room `song_info` payload becomes UTF-8 JSON in the existing `data` BLOB. `dataFormatVersion` is bumped (e.g. to `5`) to mark the new payload, so the read path can distinguish JSON rows from legacy Parcelable rows and the conversion is idempotent.
- The single song-read helper (used by **both** the song viewer and on-device song search — recall search deserializes every song to filter it in `listSongInfosByBookNameAndDeepFilter`) does:
  1. If the row's `dataFormatVersion` marks JSON → parse JSON.
  2. Else (legacy Parcelable) → custom-decode (§4) → convert (§5) → use; then **write the JSON back** to the row with the bumped `dataFormatVersion`, so each legacy song is converted at most once.
- New downloads are JSON from the start. `SongDbDataMigration` (the legacy SQLite → Room copy) keeps copying the `data` BLOB byte-for-byte; rows it imports are simply legacy-format and get converted on first read like any other.

This keeps the storage-engine swap (Room) and the payload swap independent, as the existing `SongDb` doc comment anticipates.

---

## 7. Download path

- **App.** `SongBookUtil.deserializeSongs` (Java `ObjectInputStream` guarded by `SafeObjectInputStream`) is replaced by a JSON parser (Moshi / kotlinx). This removes the Java-deserialization gadget surface entirely — a security win. `OptionalGzipInputStream` stays (the payload is still gzipped). The supported `dataFormatVersion` check (`SongBookUtil.isSupportedDataFormatVersion`) is updated to accept the new JSON version.
- **Backend** (`alkitab-host`). `get_songs` keeps issuing a 302 redirect, but branches on the requested `dataFormatVersion`: new clients → `…/songs/v1/data/<book>-5.json.gz`; old clients → the existing `<book>-4.ser.gz`. Old installs keep working; the web (pickle/HTML) path is untouched.
- **`kidung-data`.** A new `OutputJson` (alongside the existing `OutputSer`) emits `<book>-5.json.gz` (the §3.7 wrapper) plus a JSON `song_book_infos`. Static artifacts are published to `boafiles.kejut.com` as today.

---

## 8. txt authoring format (`kidung-data`)

The legacy `.txt` format is **unchanged and still builds** — there is no plan to rewrite existing `.txt` files. Document features are opt-in per song.

### 8.1 Legacy format (unchanged)

`InputTxt.java` semantics are preserved exactly: header keywords (`no`/`code`, `judul`/`title`, `judul_asli`/`title_original`, `tune`, `lirik`/`authors_lyric`, `musik`/`authors_music`, `ayat`/`scriptureReferences`, `tempo` discarded); bare key-signature / time-signature lines; `*N`, `*reff`/`*ref`[N], `*text`[N], `*versi`/`*version <caption>` markers; auto-grouping when a normal verse number decreases; `//` comments; `===` song separator; blank lines do **not** end a verse.

### 8.2 New `@doc` document mode (opt-in)

A song enters document mode with a `code <CODE>` line (required; `no` also accepted) followed by a line that is exactly `@doc`. Below `@doc`, each line maps to a block **in written order**:

- **Default**: a non-empty line with no leading `@` or `*` → a `p` block (no role).
- **Role / formatting tags**: a line may start with one or more whitespace-separated `@`-tags, then the text. Tags combine, e.g. `@size=2.0 Big text` or `@size=0.5 @musical Do=C`.
  - role tags: `@title`, `@title_original`, `@tune`, `@musical`, `@authors_lyric`, `@authors_music`, `@note`, `@copyright`, `@body`, … → `p.role`.
  - formatting tags: `@size=<float>` → `p.size`; `@align=<start|center|end>` → `p.align`.
- `@scripture <osis>` → a `scripture` block.
- `@youtube <videoId>` → a `youtube` block.
- `@gap` → a `gap` block. Combines with `@size=<float>` like other tags, e.g. `@size=2 @gap` for a double-height blank line.
- `@row` … `@/row` → a `row` block; each line between the markers is parsed as a `p` item (role/size/align tags apply per item), giving e.g. lyricist-left / composer-right.
- **Lyric markers** `*N` / `*ref`[N] / `*reff`[N] / `*text`[N] / `*versi`/`*version <caption>` behave as in legacy, including auto-grouping (a normal verse number ≤ the last one starts a new `lyric` group). Subsequent non-marker lines are appended to the current verse.
  - **Verse lines** support leading **`@size=` / `@align=`** line-level tags, producing the `{ size?, align?, content }` verse-line form (role tags are not meaningful on a lyric line and stay literal).
- A **blank line ends the current verse** (returns to paragraph mode). A following `*` marker reopens lyric mode.
- **Forward-compatible**: an unknown `@directive` is warned and skipped, not fatal.

Inline `<u>` / `<b>` / `<i>` in any content line → spans.

The generator derives `meta` from the `title` / `title_original` role blocks and emits the canonical JSON (§3).

### 8.3 KRI 25 in `@doc` form

```
code 25
@doc
@title Malam Kudus
@title_original Silent Night
@tune STILLE NACHT
@row
@authors_lyric Joseph Mohr
@authors_music Franz X. Gruber
@/row
@musical 1=Bes 6/8

*1
Malam Kudus, sunyi senyap,
<u>Sia</u>pa yang b'lum lelap;
…
*1
Silent night, holy night,
…
```

The legacy KRI 25 and this `@doc` KRI 25 produce structurally identical canonical JSON (verified in the editor harness).

---

## 9. Rendering

The renderer walks `blocks` top-to-bottom and styles each by `type` + `role`, using the app's **default day theme** (from `SongViewActivity` template vars and `song.css`): background `#f0f0f0`, text `#212121`, font-size 18px, verse-number `#828282`, line-spacing 1.15, default system font, body padding `16px 8px`.

Block order and float handling mirror the app `song.html` template, which places a clearing break (`clear: both`) after each floated section:

```
code (float start) + title (centered)
title_original (centered)
tune (float end)            → break
authors_lyric / authors_music (row: start / end)  → break
scripture
keySignature + timeSignature (musical)            → break
lyric groups (caption "Versi N" fallback when >1 group and no caption;
              normal = numbered + indented, refrain = "Ref.:" + unnumbered,
              text = plain + unnumbered)
copyright (footer)
```

The reference renderer is in [`song-editor.html`](./song-editor.html). On device this becomes a new `SongDocument` model + renderer that produces the WebView HTML, replacing the legacy `SongFragment.songToHtml` / `SongViewActivity.convertSongToText` paths. Scripture OSIS is localized at render against the active version (unchanged behavior).

---

## 10. Affected components

**App (`androidbible`)**

- `KpriModel` — legacy `Song`/`Lyric`/`Verse`/`VerseKind` retained only as the decode target / for the custom decoder’s class-name dispatch; new in-memory model is `SongDocument` (blocks).
- New: custom Parcelable decoder, legacy→document converter, `SongDocument` model + JSON (de)serialization, `SongDocument` WebView renderer.
- `SongDb.marshallSong`/`unmarshallSong` → JSON read/write helper with lazy on-read conversion (§6); `dataFormatVersion` bump; `SongInfoEntity` unchanged (still `data: ByteArray?`, now holding UTF-8 JSON).
- `SongBookUtil.deserializeSongs` → JSON parser; drop `SafeObjectInputStream`; update `isSupportedDataFormatVersion`.
- Rendering: `SongFragment` / `SongViewActivity` switch to the document renderer.

**Backend (`alkitab-host`)**

- `get_songs` redirect branches on `dataFormatVersion` (serve `-5.json.gz` to new clients, `-4.ser.gz` to old). No web-render change.

**Authoring (`kidung-data`)**

- `InputTxt` extended with `@doc` document mode (legacy path unchanged).
- New `OutputJson` emitting `<book>-5.json.gz` + JSON `song_book_infos`.

---

## 11. Decisions and rationale

- **Scope = storage + download + txt; web out of scope.** Keeps the change shippable; the canonical JSON is web-ready for a later, separate adoption.
- **Clean break + lazy on-read conversion** (not dual-model, not a bulk pass). Existing on-device data is rescued by the custom decoder on first read and rewritten as JSON; the app then has a single document renderer.
- **Custom decoder runs on-device, pure Kotlin/JVM, supporting only song types.** Directly addresses the Android 13 persistence break; throwing on unexpected types keeps it small and auditable.
- **Forward redesign with role-tagged blocks** (not a strict 1:1 round-trip of the old model). Drops dead quirks (`Verse.ordering`, implicit lyric-splitting becomes explicit groups) and enables author-controlled layout.
- **`bump dataFormatVersion`** as the payload marker (vs a separate column).
- **`row` block for side-by-side** (vs real multi-column or block-level align): the only real need is lyricist/composer on one line.
- **Inline styles limited to `u`/`b`/`i`**; **`align` is line-level** with `start`/`center`/`end`.

---

## 12. Risks and validation

- **Parcel byte assumptions.** The UTF-16 (`writeString16`) encoding and the Android-13 length-prefix placement must be validated against real device-captured BLOBs (Android 9 / 12 / 13 / 14) in addition to the AOSP-derived golden fixtures (§4.4). The platform-`Parcel` fallback (§4.5) mitigates residual risk.
- **Legacy `.txt` fidelity.** The `@doc` extension must not regress the legacy parser; the existing corpus (kpri/kj/kri/kppk/… ) must build byte-identically on the legacy path.
- **Lossless conversion.** Every legacy song must convert to JSON and render equivalently; spot-check across books with differing conventions (English vs Indonesian keywords, `*ref` vs `*reff`, `*versi`, `*text`, `ayat`, multi-group).
- **Dirty source data.** Some existing key/time signatures and OSIS strings are malformed; the editor surfaces these as non-fatal warnings — the generator should do likewise rather than hard-fail.

---

## 13. Out of scope / future work

- Web (`alkitab-host`) adopting the canonical JSON for HTML rendering.
- Swapping the (already document-based) JSON further, e.g. richer inline spans (red-letter), audio links, or per-verse scripture.
- An off-device corpus-conversion tool (not needed: `kidung-data` regenerates the corpus as JSON).
