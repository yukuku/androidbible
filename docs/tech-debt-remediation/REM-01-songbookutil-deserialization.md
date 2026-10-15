# REM-01: Fix SongBookUtil Resource Leak & Unsafe Deserialization ✅ COMPLETED

**Module:** Songs
**BRICE:** B=4 R=5 I=4 C=5 E=5 → **4.6**
**Phase:** 1 — Quick Wins & Safety Fixes

**Completed in:** `1b9b74d9` (2026-04-11)

**What was done (steps 1-3):**
1. ✅ Wrapped `Response` and streams in try-with-resources to prevent leaks
2. ✅ Added response body size validation (rejects >50MB)
3. ✅ Introduced `SafeObjectInputStream` with class whitelist (`java.util.*`, `java.lang.*`, and Song model classes only) to prevent deserialization attacks. Also added `instanceof` check before casting.
4. ✅ App-side download and storage payloads use JSON as of 2026-07-13 in [REM-21](REM-21-song-json-storage.md). `SongBookUtil.deserializeSongs` parses a gzipped JSON wrapper; Java object deserialization and its allow-list stream are removed. On-device legacy Parcelable rows are converted lazily through `LegacyParcelDecoder`; the legacy `yuku.kpri.model` types remain for that decoder. External backend/authoring gates are listed in REM-21.

**Tests added:** 7 unit tests in `SongBookUtilTest.java` covering single/multiple songs, empty list, gzip, Unicode, multiple lyrics, and null fields.

---

[← Back to remediation index](../tech-debt-remediation.md)
