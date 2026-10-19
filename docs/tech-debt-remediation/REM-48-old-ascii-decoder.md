# REM-48: Harden OldVerseTextDecoder.Ascii

**Source:** Found while converting `OldVerseTextDecoder` to Kotlin ([REM-16](REM-16-java-to-kotlin.md)), 2026-09-23
**Module:** Alkitab (`yuku.alkitab.base.storage.OldVerseTextDecoder.Ascii`, used by `Yes1Reader` for YES1 files that are not marked UTF-8)
**BRICE:** B=2 R=2 I=4 C=4 E=3 → **3.0**
**Phase:** 2 (Architecture Improvements)

**Status:** Complete (2026-10-15).

**Implementation:**
- Both YES1 ASCII entry points decode ISO-8859-1 consistently. Verse splitting uses string slices without a fixed-size character buffer.
- Lowercasing changes only ASCII A through Z in the decoded text and leaves caller-owned bytes untouched. `Yes1Reader` creates a fresh chapter byte array and does not depend on input mutation.
- Regression coverage preserves newline and empty-verse behavior, verifies Latin-1 decoding, and checks long verses and input immutability for both entry points.

---

[← Back to remediation index](../tech-debt-remediation.md)
