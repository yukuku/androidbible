# REM-48: Harden OldVerseTextDecoder.Ascii

**Source:** Found while converting `OldVerseTextDecoder` to Kotlin ([REM-16](REM-16-java-to-kotlin.md)), 2026-09-23
**Module:** Alkitab (`yuku.alkitab.base.storage.OldVerseTextDecoder.Ascii`, used by `Yes1Reader` for YES1 files that are not marked UTF-8)
**BRICE:** B=2 R=2 I=4 C=4 E=3 → **3.0**
**Phase:** 2 (Architecture Improvements)

**Status:** Not started.

**Problem:** The ASCII decoder for YES1 files has three problems:
1. `separateIntoVerses` copies each verse into a fixed 4,000-char buffer, so a verse longer than 4,000 bytes throws `ArrayIndexOutOfBoundsException` while the chapter loads.
2. Bytes above 0x7F decode differently in the two methods. `separateIntoVerses` sign-extends each byte, so 0xE9 becomes U+FFE9, while `makeIntoSingleString` decodes ISO-8859-1, so 0xE9 becomes U+00E9. The code carries a warning that only ASCII input is supported, and `OldVerseTextDecoderTest` pins the sign-extension today.
3. With `lowercased = true`, both methods lowercase the caller's byte array in place instead of a copy.

**Steps:**
1. Decode each verse with `String(ba, from, length, Charsets.ISO_8859_1)` between newlines, the same way `Utf8.separateIntoVerses` slices the buffer. This removes the fixed buffer and makes both methods agree on Latin-1. Update the sign-extension test to expect U+00E9.
2. Lowercase a copy of the bytes, or lowercase A to Z in the decoded string, instead of the input array. Check first that `Yes1Reader` does not rely on the mutation.
3. Add tests: a verse longer than 4,000 bytes, non-ASCII bytes decoding the same in both methods, and the input array unchanged after a lowercased call.

**Difficulty:** Easy.

---

[← Back to remediation index](../tech-debt-remediation.md)
