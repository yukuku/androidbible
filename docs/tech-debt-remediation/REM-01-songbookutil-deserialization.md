# REM-01: Fix SongBookUtil Resource Leak & Unsafe Deserialization ✅ COMPLETED

**Addresses:** TD-04, PB-06
**Module:** Songs
**BRICE:** B=4 R=5 I=4 C=5 E=5 → **4.6**
**Phase:** 1 — Quick Wins & Safety Fixes

**Completed in:** `1b9b74d9` (2026-04-11)

**What was done (steps 1-3):**
1. ✅ Wrapped `Response` and streams in try-with-resources to prevent leaks
2. ✅ Added response body size validation (rejects >50MB)
3. ✅ Introduced `SafeObjectInputStream` with class whitelist (`java.util.*`, `java.lang.*`, and Song model classes only) to prevent deserialization attacks. Also added `instanceof` check before casting.
4. ⬜ Long-term: Migrate song download format from Java serialization to JSON (Kotlinx Serialization) — not yet started, requires server change (tracked separately as [REM-21](REM-21-song-json-storage.md))

**Tests added:** 7 unit tests in `SongBookUtilTest.java` covering single/multiple songs, empty list, gzip, Unicode, multiple lyrics, and null fields.

---

[← Back to remediation index](../tech-debt-remediation.md)
