# Portable Songs (REM-21) — Android Implementation Plan

Android-side implementation plan for the migration described in [`design.md`](./design.md): replace the two non-portable song payloads (on-device `Parcel.marshall()` BLOB, gzipped Java-serialized download) with the canonical JSON document model, rescue existing on-device data with a version-agnostic custom Parcelable decoder, and render from a new document model.

- **Status:** implemented — shipped app-side on 2026-07-13 (see [REM-21](../../tech-debt-remediation/REM-21-song-json-storage.md)); this doc is retained as the design/plan record
- **Scope of this doc:** the `androidbible` app only. The backend redirect branching and the `kidung-data` `@doc` txt authoring / `OutputJson` are tracked separately (design §7, §8, §10) and are **not** part of this plan.
- **Centerpiece:** §6 — a comprehensive bridge test that drives the *same* representative songs through both the current client code (legacy `Song` + `Parcel` + `songToHtml` + `SongFilter`) and the new JSON document path, and asserts equivalence.

---

## 1. Goal and success criteria

Transform the design into verifiable goals:

| Goal | Verify |
|------|--------|
| New downloads store & render as JSON | Download-path test parses a gzipped JSON wrapper into `SongDocument`s and `storeSongs` writes UTF-8 JSON rows |
| Existing on-device legacy BLOBs still load after an OS upgrade | Custom decoder produces identical models from the pre-13 **and** Android-13 golden parcels (§6.4) |
| No data loss migrating a legacy song to the document model | Every field of a representative legacy `Song` survives decode → convert → JSON → parse (§6.2) |
| Rendering is equivalent | Converted-legacy `SongDocument` renders the same lyric-body semantics as the current `SongFragment.songToHtml` (§6.3) |
| Search still works | `SongFilter` matches the same songs against a `SongDocument` as it does against the legacy `Song` (§6.5) |
| Legacy and `@doc` authoring converge | Converted-legacy KRI 25 and hand-authored canonical JSON KRI 25 parse to structurally identical `SongDocument`s (§6.6, design §8.3) |

The whole change is shippable only when the §6 bridge test is green **and** the decoder has been validated against a handful of real device-captured BLOBs (design §4.4 / §12 — an external, pre-ship gate, not a unit test).

---

## 2. Components — new, changed, unchanged

**New (all under `Alkitab/src/main/java/yuku/alkitab/songs/newdoc/`):**

| File | Responsibility |
|------|----------------|
| `SongDocument.kt` | The document model: `SongDocument`, `Block` (sealed), `Verse`, `VerseLine`, `Line`, `Span`, `VerseKind` — design §3. `@Serializable` via `kotlinx.serialization` (already a dependency). |
| `SongDocumentJson.kt` | The configured `Json` instance + custom serializers for the union types (`Line`, `VerseLine`) and the forward-compatible `Block` discriminator. `encode`/`decode` + the download-wrapper (`SongBook` / §3.7). |
| `LegacyParcelDecoder.kt` | Pure-Kotlin/JVM decoder (design §4). Reads a marshalled `song_info.data` byte buffer **without** `android.os.Parcel`, into a legacy `yuku.kpri.model.Song`. |
| `LegacySongConverter.kt` | Maps a legacy `Song` → `SongDocument` in canonical block order (design §5). |
| `SongDocumentRenderer.kt` | Walks `blocks` → WebView HTML (design §9). Replaces `SongFragment.songToHtml`. |
| `SongDocumentText.kt` | Walks `blocks` → plain text for copy/share. Replaces `SongViewActivity.convertSongToText`. |

**Changed:**

| File | Change |
|------|--------|
| `SongDb.java` | `marshallSong`/`unmarshallSong` become a single JSON read/write helper with lazy on-read conversion (design §6). Write path always emits JSON at the new `dataFormatVersion`; read path dispatches JSON-vs-legacy and rewrites legacy rows as JSON. |
| `SongBookUtil.kt` | `deserializeSongs` → JSON parser; delete `SafeObjectInputStream`; update `isSupportedDataFormatVersion`; keep `OptionalGzipInputStream`. |
| `SongFilter.java` | Add a `match(SongDocument, CompiledFilter)` overload that scans `meta` + all block text. |
| `SongFragment.kt` / `SongViewActivity.kt` | Hold a `SongDocument` instead of a `Song`; render via `SongDocumentRenderer`; copy/share via `SongDocumentText`; scripture OSIS localization unchanged. |

**Unchanged / retained:** `yuku.kpri.model.{Song,Lyric,Verse,VerseKind}` remain as the decoder's target type and class-name dispatch keys (design §10). `SongInfoEntity` keeps `data: ByteArray?` (now UTF-8 JSON bytes). `SongRoomDao`, `SongRoomDatabase`, `SongDbDataMigration` (legacy SQLite → Room byte-for-byte copy) are untouched — rows it imports are simply legacy-format and get converted on first read.

---

## 3. Document model (design §3) in kotlinx.serialization

The two source-of-truth subtleties, because they drive both the model and the custom serializers:

- **`Line = String | Span[]`** — a line with no inline styling serializes as a bare JSON string; a styled line serializes as an array of `Span`. Needs a custom `KSerializer<Line>` that branches on `JsonElement` shape (primitive vs array).
- **`VerseLine = Line | { size?, align?, content: Line }`** — a lyric line optionally wrapped for its own relative size/align. Custom serializer branches on primitive/array (→ plain `Line`) vs object (→ wrapped).
- **`Block`** is discriminated by `type` (`p`/`row`/`lyric`/`scripture`/`youtube`/`gap`). Use a sealed interface with `classDiscriminator = "type"`. **Forward-compatibility (design §3.2):** an unknown `type` must not throw — configure the polymorphic deserializer with a default that yields an `UnknownBlock` rendered as a plain paragraph. `Json { ignoreUnknownKeys = true }` so unknown `role`/fields are dropped, not fatal.

```kotlin
@Serializable
data class SongDocument(
    val code: String,
    val meta: Meta,
    val blocks: List<Block>,
)

@Serializable
data class Meta(val title: String?, val title_original: String?)

sealed interface Block { val size: Float? }
// PBlock(role,size,align,content: Line) | RowBlock(size,items: List<PBlock>) |
// LyricBlock(role,size,caption: Line?,verses: List<Verse>) |
// ScriptureBlock(role,size,osis: String) | YoutubeBlock(role,size,videoId: String) |
// GapBlock(size) | UnknownBlock(raw: JsonElement)   // forward-compat sink

@Serializable
data class Verse(val kind: VerseKind, val marker: String?, val lines: List<VerseLine>)
```

`meta` is **derived, never hand-authored** (design §3.1). It exists only so listing / search prefilter / DB never parse the document body. Derivation happens once, at the point something *authors* a `SongDocument` — `LegacySongConverter` builds it directly from the legacy `Song.title`/`Song.title_original` fields it's already converting from; an external tool (`kidung-data`'s `OutputJson`) is the trusted producer for downloaded/hand-authored JSON. `blocks` is flowing document content, not something the app re-parses on every load: `SongDocumentJson.encode`/`decode` pass `meta` through as-is, they do **not** recompute it. (Convergence between a converted-legacy document and hand-authored `@doc` JSON — §6.6 — is a property of both producers deriving `meta` the same way, not of the app re-deriving on read.)

`dataFormatVersion` for the JSON payload is bumped to **5** (design §6/§7). `SongBookUtil.isSupportedDataFormatVersion` accepts 5; the alkitab-uri download path (`SongViewActivity`) requests `dataFormatVersion=5&type=json` for new installs.

---

## 4. Custom Parcelable decoder (design §4) — the load-bearing piece

`LegacyParcelDecoder.decode(buf: ByteArray, dataFormatVersion: Int): Song`, pure JVM, no `android.os.Parcel`. It replicates exactly the wire facts in design §4.1:

- little-endian, 4-byte alignment; `readInt` = 4 bytes LE.
- `readString` = `writeString16`: int length in **UTF-16 code units**, `-1` = null, else that many UTF-16 chars **plus a NUL terminator char**, padded to 4 bytes.
- `readStringList` = int count (`-1` null) then N `readString`.
- typed list reader understanding **only** `VAL_NULL (-1)` and `VAL_PARCELABLE (4)`; any other tag or unexpected class name → `throw IllegalStateException` (design §4.2).
- **Android-13 detection (design §4.3):** at the first `VAL_PARCELABLE`, peek the int after the tag; if it is `21` and the next 21 UTF-16 chars are a known class name (`yuku.kpri.model.Lyric` / `…Verse`) → `LEGACY`; else → `ANDROID13` (the int was the inserted length prefix). Lock the format for the whole buffer. Both class names are exactly `0x15` chars and a real length prefix is far larger, so no collision.

The decoder discards `Verse.ordering` (design §3.3) at read time but must still consume its 4 bytes. It reads (and ignores) the Android-13 length prefix on subsequent elements.

**Fallback (design §4.5):** if `decode` throws, `SongDb` may retry via the platform `Parcel.unmarshall()` path (works when the OS hasn't changed since the row was written) and logs the failure with the row's `code`/`bookName`.

---

## 5. Read/write path in `SongDb` (design §6)

Replace `marshallSong`/`unmarshallSong` with:

```
readDocument(row: {data, dataFormatVersion, _id, code, bookName}): SongDocument
  if dataFormatVersion marks JSON (== 5):
      return SongDocumentJson.decode(data)          // UTF-8 JSON
  else:                                             // legacy Parcelable
      song = LegacyParcelDecoder.decode(data, dataFormatVersion)   // (fallback → Parcel.unmarshall)
      doc  = LegacySongConverter.convert(song)
      writeBackJson(row._id, doc)                    // idempotent: bump dataFormatVersion to 5
      return doc

writeDocument(doc): ByteArray = SongDocumentJson.encode(doc).toByteArray(UTF_8)
```

Every read call site routes through `readDocument`: `getSong`, `getFirstSongFromBook`, `getAnySong`, and the streaming deep-filter cursor loop in `listSongInfosByBookNameAndDeepFilter`. The write-back needs the row `_id`, so add a DAO `@Query("UPDATE song_info SET data = :data, dataFormatVersion = 5 WHERE _id = :id")`. Write-back on the read path is a lazy, at-most-once conversion (design §6); a read during a search scan of a legacy book converts each row it touches.

`storeSongs` marshals with `writeDocument` at version 5. Callers (`SongBookUtil.downloadSongBook`) already pass `dataFormatVersion` through — it becomes 5 for JSON downloads.

Note: today `getSong`/`getAnySong`/etc. return a legacy `Song`. Their callers (`SongViewActivity`, `SongFragment`) switch to `SongDocument` (§2). Keep the public method names; change the return type.

---

## 6. The comprehensive bridge test

One test class, `PortableSongsBridgeTest` (Robolectric where a `Parcel` control is exercised; plain JUnit for the pure-JVM decoder/converter/JSON assertions). It drives a shared corpus of representative legacy `Song` fixtures through **both** paths and asserts equivalence. This is the test the design's "exercise both the current client code and the new song format" requirement maps to.

### 6.0 Shared fixture corpus

A `songs()` factory returning legacy `yuku.kpri.model.Song` objects covering every edge the design flags (§4.4, §12):

- KRI 25 "Malam Kudus / Silent Night" — the design §3.8 worked example (multi-group lyrics, inline `<u>`, title_original, tune, two authors, musical key/time, scripture).
- A song with **null** `title_original`, `tune`, `keySignature`, `timeSignature`, `scriptureReferences`, and **empty** `authors_lyric`/`authors_music` lists.
- A song with `REFRAIN` and `TEXT` verses interleaved with `NORMAL`, and a multi-group lyric with explicit captions.
- A song whose lines contain `<u>`/`<b>`/`<i>` and raw `&`/`<` needing escaping.

### 6.1 Current-code control (Robolectric)

For each fixture, exercise the **existing** client code as a control:

- `Song.writeToParcelCompat(3, p, 0)` → `p.marshall()` → `Parcel.unmarshall` → `Song.createFromParcelCompat(3, …)`; assert field-equal to the original (the current storage round-trip still holds).
- `SongFragment.songToHtml(song, false)` → capture the legacy HTML.
- `ObjectOutputStream(List<Song>)` → gzip → `SongBookUtil.deserializeSongs`; assert field-equal (the current download round-trip still holds).

> **Robolectric caveat (drives §6.4).** Robolectric's `ShadowParcel.marshall()` uses its own serialization, **not** the native Android binary layout. So bytes it produces are self-consistent but are **not** what a real device writes to `song_info.data`, and they cannot validate `LegacyParcelDecoder`. The control above proves current code still works; the decoder is validated separately against faithful AOSP-layout goldens (§6.4).

### 6.2 Decode → convert → JSON round-trip (no data loss)

For each fixture: build its **faithful** legacy-layout bytes with the test-only `AospParcelWriter` (§6.4), then:

```
song  = LegacyParcelDecoder.decode(bytes, 3)
assertSongFieldsEqual(fixture, song)                 // decoder reconstructs the legacy model
doc   = LegacySongConverter.convert(song)
json  = SongDocumentJson.encode(doc)
doc2  = SongDocumentJson.decode(json)
assertDocumentsEqual(doc, doc2)                      // JSON round-trip is lossless, incl. the meta LegacySongConverter computed
```

Assert the converter honored design §5 block order (title, title_original, tune, authors `row`, scripture, musical, lyric groups), dropped `Verse.ordering`, mapped `VerseKind`, and parsed inline `<u>/<b>/<i>` into `Span`s with other text escaped.

### 6.3 Render equivalence (old vs new)

For each fixture: `songToHtml(fixture)` (legacy, §6.1) vs `SongDocumentRenderer.renderLyrics(convert(fixture))`. Normalize whitespace/attribute order and assert the **lyric-body semantics** match: same verse numbering (positional; `NORMAL` numbered, `REFRAIN`/`TEXT` unnumbered), same "Versi N" caption fallback when >1 group and no caption, same lines in order, same inline styling. (Chrome/exact-markup differences are normalized out; the assertion is on structure, not byte-identical HTML.)

Also assert `SongDocumentText.render(doc)` reproduces `convertSongToText(fixture)` for copy/share.

### 6.4 Android-13 vs pre-13 golden parcels (design §4.3, §4.4)

A test-only `AospParcelWriter` re-implements `writeInt` / `writeString16` / `writeStringList` / `writeValue(VAL_PARCELABLE)` / `writeList` for **both** layouts, mirroring `Song.writeToParcelCompat` → `Lyric.writeToParcel` → `Verse.writeToParcel` field order. Its only divergence is the 4-byte length prefix inserted after each `VAL_PARCELABLE` tag in `ANDROID13` mode.

For each fixture:

```
legacyBytes  = AospParcelWriter(LEGACY).write(fixture, dfv=3)
a13Bytes     = AospParcelWriter(ANDROID13).write(fixture, dfv=3)
assertNotEquals(legacyBytes, a13Bytes)                 // the layouts genuinely differ
songL = LegacyParcelDecoder.decode(legacyBytes, 3)
songA = LegacyParcelDecoder.decode(a13Bytes, 3)
assertSongFieldsEqual(fixture, songL)
assertSongFieldsEqual(fixture, songA)                  // decoder auto-detects & both agree
```

Cover null fields, empty lists, multi-group lyrics, and refrain/text verses (design §4.4). Add negative cases: an unknown `writeValue` tag and an unexpected class name each `throw IllegalStateException` (design §4.2).

> `AospParcelWriter` is the wire-format ground truth for the unit tests. It is **not** proof against reality — design §4.4/§12 require validating the decoder against real BLOBs captured from Android 9 / 12 / 13 / 14 before shipping. That is an external gate; note it in the PR, don't fake it in a unit test.

### 6.5 Search equivalence

For each fixture and a set of query strings (code, title, title_original, an author, a lyric-body word, a non-match): assert `SongFilter.match(fixture, cf) == SongFilter.match(convert(fixture), cf)`. This pins the new `match(SongDocument, …)` overload to the legacy `match(Song, …)` semantics that the deep-filter scan relies on.

### 6.6 Legacy ≡ `@doc` convergence (design §8.3)

Load the canonical JSON for "KRI 25 — @doc" as a checked-in test resource (structurally the design §3.8 document, i.e. what `kidung-data`'s `@doc` generator would emit), parse it to `SongDocument`, and assert it is structurally identical to `convert(legacy KRI 25)`. This is the app-side proof of the design's claim that the legacy and `@doc` sources produce the same model — without depending on `kidung-data`.

### 6.7 Download-wrapper parse

Build a gzipped JSON song-book wrapper (design §3.7: `{dataFormatVersion, songs}` — no `book`; book metadata travels via the download request, not the payload) and feed it to the new `SongBookUtil.deserializeSongs`, asserting the `SongDocument`s come through. Assert the removed Java-deserialization path is gone (a gzipped, still-Java-serialized payload built the old way fails to parse), and that `isSupportedDataFormatVersion(5)` is true / `(3)`/`(4)` are false.

---

## 7. Phasing

Each phase is independently testable; land them in order.

1. **Model + JSON** (`SongDocument`, `SongDocumentJson`) → verify: §6.2 round-trip on hand-built documents; forward-compat (unknown `type`/`role` → `UnknownBlock`/dropped, not fatal).
2. **Decoder** (`LegacyParcelDecoder` + `AospParcelWriter` test helper) → verify: §6.4 goldens + negative cases.
3. **Converter** (`LegacySongConverter`) → verify: §6.2 field/block-order assertions, §6.6 convergence.
4. **Storage read/write** (`SongDb` lazy on-read conversion + write-back DAO query) → verify: Robolectric `SongDbTest` extended — store a legacy-format row, read it back as a `SongDocument`, assert the row's `dataFormatVersion` flipped to 5 and the bytes are now JSON (idempotent on second read).
5. **Search** (`SongFilter` overload) → verify: §6.5.
6. **Render** (`SongDocumentRenderer`, `SongDocumentText`; wire `SongFragment`/`SongViewActivity`) → verify: §6.3; manual smoke on device.
7. **Download** (`SongBookUtil` JSON parser, drop `SafeObjectInputStream`, `isSupportedDataFormatVersion`, alkitab-uri version) → verify: §6.7; existing `SongBookUtilTest` updated for the JSON wire format.

---

## 8. Verification commands

```bash
# The bridge test and the extended storage/download tests
./gradlew testPlainDebugUnitTest --tests "yuku.alkitab.songs.newdoc.PortableSongsBridgeTest" 2>&1 | tail -60
./gradlew testPlainDebugUnitTest --tests "yuku.alkitab.base.storage.SongDbTest" 2>&1 | tail -60
./gradlew testPlainDebugUnitTest --tests "yuku.alkitab.songs.SongBookUtilTest" 2>&1 | tail -60

# Full suite as CI runs it, plus a real build
./gradlew testPlainDebugUnitTest testPlainReleaseUnitTest 2>&1 | tail -60
./gradlew assemblePlainDebug 2>&1 | tail -60
```

Follow with a device smoke test: install a JSON song book, open a song, copy/share it, run a deep search that forces a legacy book's rows through the on-read converter, and confirm a previously-installed (legacy BLOB) book still opens.

---

## 9. Risks and open points

- **Robolectric `Parcel` is not the device wire format** (§6.1 caveat). The decoder's real-world correctness rests on `AospParcelWriter` faithfulness + the pre-ship device-capture validation (design §4.4/§12). Treat the device-capture step as a hard release gate.
- **`writeString16` UTF-16 + NUL + padding** and the **Android-13 length-prefix placement** are the two assumptions most likely to bite; the golden fixtures and the negative-tag tests bound them, real captures confirm them.
- **Lazy write-back during a search scan** issues an `UPDATE` per legacy row touched. Read the row out of the `Cursor` first, then fire the `UPDATE` on a background thread (`Background.run`) rather than synchronously on the scan thread — at-most-once per row, then permanently JSON, and doesn't block a main-thread single-song read or stall the search loop.
- **`meta` is computed once, by whichever code authors a `SongDocument`, and trusted thereafter** (§3): `LegacySongConverter` builds it directly from `Song.title`/`Song.title_original` when synthesizing a document from legacy data; `SongDocumentJson.encode`/`decode` pass `meta` through unchanged. Convergence between a converted-legacy document and hand-authored `@doc` JSON (§6.6) depends on both producers deriving `meta` the same way — not on the app re-deriving it on every read.
- **Backend / `kidung-data` are out of scope here** but must ship compatibly: old installs keep requesting the `.ser` payload (design §7). Don't remove the version-3 request path until telemetry says no old clients remain.
