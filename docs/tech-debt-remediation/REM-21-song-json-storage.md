# REM-21: Migrate Song Storage from Parcelable to JSON

**Addresses:** TD-04 (long-term part)
**Module:** Songs
**BRICE:** B=3 R=3 I=1 C=2 E=5 → **2.8**
**Phase:** 4 — Long-term / Major Refactors

**Status:** App-side done (2026-07-13). Backend (`alkitab-host` redirect branching) and authoring (`kidung-data` `@doc` txt mode / `OutputJson`) are tracked separately and are **not** part of this app-side change — see `docs/features/portable-songs/design.md` §7/§8 and its "Out of scope" note.

**Shipped, app-side (`androidbible`):**
- `Alkitab/src/main/java/yuku/alkitab/songs/newdoc/` — the canonical `SongDocument` model (`kotlinx.serialization`, custom `KSerializer`s for the `Line`/`VerseLine`/`Block` JSON-shape unions), `LegacyParcelDecoder` (pure-JVM, no `android.os.Parcel`, replicates the marshalled wire format including Android-13 length-prefix auto-detection), `LegacySongConverter`, `SongDocumentRenderer` (WebView HTML), `SongDocumentText` (plain-text copy/share), `SongDocumentSearch`.
- `SongDb.java` — `readDocument`/`writeDocument` replace `marshallSong`/`unmarshallSong`; JSON is `dataFormatVersion = 5`; legacy Parcelable rows are decoded, converted, and written back as JSON lazily on first read (at most once per row) via `SongRoomDao.writeBackJsonSongData`. `getSong`/`getFirstSongFromBook`/`getAnySong`/`storeSongs` now return/accept `SongDocument`.
- `SongBookUtil.kt` — `deserializeSongs` parses the gzipped JSON song-book wrapper (`{ v, book, songs }`); `SafeObjectInputStream`/`ObjectInputStream` deleted outright; `isSupportedDataFormatVersion` accepts `5`.
- `SongFilter.java` — added `match(SongDocument, CompiledFilter)`, scanning the same fields (`code`, title/title_original, authors, tune, verse lines — not captions/musical/scripture) as the legacy `match(Song, CompiledFilter)`.
- `SongFragment.kt` / `SongViewActivity.kt` — hold a `SongDocument` (passed across the fragment `Bundle` as JSON text, since the type isn't `Parcelable`); render via `SongDocumentRenderer`/`SongDocumentText`. `templates/song.html`/`song.css` merged the `keySignature`/`timeSignature` divs into one `musical` div to match the new single merged block.
- Verified with `PortableSongsBridgeTest` (`Alkitab/src/test/java/yuku/alkitab/songs/newdoc/`) — drives a fixture corpus through both the pre-migration reference algorithms (kept in the test only, for comparison) and the new `SongDocument` path: Parcel/Java-serialization round-trips, decode→convert→JSON round-trip, render/plain-text equivalence, Android-13-vs-legacy golden parcels (via a test-only `AospParcelWriter`) plus negative-tag/class-name cases, search equivalence, legacy-vs-hand-authored-JSON convergence, and the download-wrapper parse — plus `SongDbTest`/`SongBookUtilTest` updated for the new types.

**Not done (explicit external gates, not unit-testable in this environment):**
- Real-device-captured BLOB validation across Android 9/12/13/14 (design §4.4/§12) — the golden-parcel tests validate against a faithful re-implementation of the AOSP wire format, not real device output.
- Backend `get_songs` redirect branching on `dataFormatVersion` and `kidung-data`'s `OutputJson`/`@doc` txt mode (design §7/§8) — separate repos, out of scope here.

---

[← Back to remediation index](../tech-debt-remediation.md)
