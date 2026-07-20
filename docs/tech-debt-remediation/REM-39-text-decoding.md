# REM-39: Text Decoding Correctness (Shared Static Buffer, Locale Lowercasing)

**Source:** 2026-07 code audit
**Module:** AlkitabIo (`Utf8Decoder`) + search (`QueryTokenizer`, `SearchEngine`)
**BRICE:** B=3 R=3 I=4 C=4 E=3 → **3.4**
**Phase:** 2 — Architecture Improvements (2026-07 audit)

**Status:** Not started.

**Steps:**
1. Make `Utf8Decoder.buf` `ThreadLocal` like the sibling `byte_buf_`/`char_buf_` fields (or allocate per call — measure first; the ThreadLocal pattern is already established in the same file). Remove the swallowed `ArrayIndexOutOfBoundsException` (`// biarin`) once the race is gone, or at least log it.
2. Lowercase queries and haystack with the same rules. Simplest consistent choice: locale-invariant lowercasing on both sides (`Locale.ROOT` in `QueryTokenizer` to match the decoders' `Character.toLowerCase`). Document the choice where both sides can see it.
3. Tests: concurrent decode of two versions (repeat-run to catch interleaving), and a Turkish-locale search test (`Locale.setDefault(new Locale("tr"))`, query "KIRK").

**Difficulty:** Easy (~half a day).

---

[← Back to remediation index](../tech-debt-remediation.md)
