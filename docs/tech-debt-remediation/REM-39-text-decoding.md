# REM-39: Text Decoding Correctness (Shared Static Buffer, Locale Lowercasing)

**Source:** 2026-07 code audit
**Module:** AlkitabIo (`Utf8Decoder`) + search (`QueryTokenizer`, `SearchEngine`)
**BRICE:** B=3 R=3 I=4 C=4 E=3 → **3.4**
**Phase:** 2 — Architecture Improvements (2026-07 audit)

**Status:** Complete (2026-10-15).

**Implementation:**
- `Utf8Decoder` keeps its reusable output buffer in a `ThreadLocal`, matching its other scratch buffers. Invalid ranges and truncated sequences fail explicitly instead of returning partial text.
- `QueryTokenizer` uses locale-independent `Character.toLowerCase` for each character, matching UTF-8 search decoding, including dotted I and Greek sigma.
- Regression coverage exercises simultaneous decoding, buffer growth, bounded slices, and query normalization under a Turkish default locale.

---

[← Back to remediation index](../tech-debt-remediation.md)
