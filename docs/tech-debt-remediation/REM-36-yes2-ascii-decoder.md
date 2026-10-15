# REM-36: Fix YES2 ASCII Search Decoder + Round-Trip Coverage

**Source:** 2026-07 code audit
**Module:** AlkitabYes2 (`Yes2VerseTextDecoder.java`)
**BRICE:** B=3 R=3 I=5 C=5 E=3 → **3.8**
**Phase:** 1, Quick Wins & Safety Fixes (2026-07 audit)

**Status:** Completed on 2026-10-15.

**Problem:** `Yes2VerseTextDecoder.Ascii.separateIntoVerses` used the verse index when lowercasing bytes. For `.yes` versions with `textEncoding == 1`, including the ASCII fallback for unknown encodings, uppercase letters remained in search text and an unrelated byte could be modified. Display text was unaffected.

**Completed work:**

- Lowercase the current byte using the byte index in `Yes2VerseTextDecoder.Ascii.separateIntoVerses`.
- Extend `Yes2RoundTripTest` with ASCII and unknown-encoding fixtures covering compressed and uncompressed files, separate verses and joined chapter text, uppercase and mixed-case text, punctuation, empty and short verses, and unchanged display text after search decoding.
- Inspect the sibling UTF-8 decoder loops in `Yes2VerseTextDecoder.Utf8` and `Utf8Decoder`. No equivalent verse-index/byte-index mix-up was found.

**Validation:** Both new regression tests failed against the original decoder before the fix. All 19 YES2 debug unit tests and `assemblePlainDebug` pass with the fix. The library does not expose a release unit-test task.

**Remaining work:** None within this scope. Other decoding concerns remain tracked separately in REM-39 and REM-48.

---

[← Back to remediation index](../tech-debt-remediation.md)
