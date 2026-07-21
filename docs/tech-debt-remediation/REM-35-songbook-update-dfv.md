# REM-35: Fix "Update Song Book" dataFormatVersion Handling

**Source:** 2026-07 code audit
**Module:** Songs (`SongViewActivity.updateSongBook`, `SongDb.storeSongs`, `SongRoomDao`)
**BRICE:** B=4 R=4 I=4 C=4 E=3 → **3.8**
**Phase:** 1 — Quick Wins & Safety Fixes (2026-07 audit)

**Status:** Done (2026-07-20). `SongViewActivity.updateSongBook` now always requests and stores at `SongDocumentJson.DATA_FORMAT_VERSION` (with the `isSupportedDataFormatVersion` guard the `alkitab://` path has); `SongDb.storeSongs` dropped its `dataFormatVersion` parameter and always stamps rows at that version (the payload is always JSON); the replace primitive is now `SongRoomDao.replaceSongsForBookName` (delete by `bookName` alone), and the version-scoped `replaceSongsForBookNameAndDataFormatVersion` / `deleteSongInfosByBookNameAndDataFormatVersion` / `getDataFormatVersionForSongs` / `findDataFormatVersionForBookName` methods were removed as their last callers went away. Tests cover updating a mixed-version book (no duplicates, all rows at version 5, every row readable).

**Problem:** After REM-21's lazy per-row conversion, a pre-REM-21 song book is mixed-`dataFormatVersion` (viewed rows at 5, unviewed rows still at 2/3/4). `updateSongBook()` feeds `getDataFormatVersionForSongs` (a `LIMIT 1` with no `ORDER BY` — nondeterministic for mixed books) into the re-download, and `storeSongs` deletes only rows matching that version while always writing JSON payloads. Result: duplicate songs, and/or JSON rows stamped with a legacy `dataFormatVersion` that crash on next read when dispatched to `LegacyParcelDecoder`.

**Steps:**
1. In `updateSongBook`, always request and store at `SongDocumentJson.DATA_FORMAT_VERSION` (the payload written is always JSON anyway). Add the `isSupportedDataFormatVersion` guard that the `alkitab://` download path already has.
2. Change the replace primitive to delete by `bookName` alone (`replaceSongsForBookName`), not `(bookName, dataFormatVersion)` — a book update should replace the whole book. Keep the old DAO method only if something still needs it.
3. Make `getDataFormatVersionForSongs` deterministic or delete it if step 1 removes its last caller.
4. Tests: update a mixed-version book → no duplicates, all rows at version 5, every row readable via `readDocument`.

**Difficulty:** Easy (~half a day with tests).

---

[← Back to remediation index](../tech-debt-remediation.md)
