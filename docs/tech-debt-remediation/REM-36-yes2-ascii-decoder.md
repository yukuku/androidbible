# REM-36: Fix YES2 ASCII Search Decoder + Round-Trip Coverage

**Source:** 2026-07 code audit
**Module:** AlkitabYes2 (`Yes2VerseTextDecoder.java`)
**BRICE:** B=3 R=3 I=5 C=5 E=3 → **3.8**
**Phase:** 1 — Quick Wins & Safety Fixes (2026-07 audit)

**Status:** Not started.

**Problem:** `Yes2VerseTextDecoder.Ascii.separateIntoVerses` lowercases with the wrong index — `verseBuf[i] |= 0x20` (verse counter) instead of `verseBuf[j]` (byte counter). For `.yes` versions with `textEncoding == 1` (also the fallback for unknown encodings), search misses matches spanning uppercase letters and corrupts one byte per verse in the search buffer. Display is unaffected; the lowercased path is search-only.

**Steps:**
1. One-character fix: `verseBuf[i]` → `verseBuf[j]`.
2. Extend the YES2 round-trip test (currently UTF-8-only) with an ASCII-encoded fixture asserting `separateIntoVerses(..., lowercased = true)` returns fully lowercased text and `lowercased = false` returns the original.
3. Grep for other `[i]`/`[j]` mix-ups in the sibling decoders while in there.

**Difficulty:** Trivial (~1 hour).

---

[← Back to remediation index](../tech-debt-remediation.md)
