# REM-47: Close the Header File in YesReaderFactory on Read Failure

**Source:** Found while converting `YesReaderFactory` to Kotlin ([REM-16](REM-16-java-to-kotlin.md)), 2026-09-23
**Module:** Alkitab (`yuku.alkitab.base.storage.YesReaderFactory`)
**BRICE:** B=2 R=2 I=5 C=5 E=4 → **3.6**
**Phase:** 2 (Architecture Improvements)

**Status:** Not started.

**Problem:** `YesReaderFactory.createYesReader` opens a `RandomAccessFile` to read the 8-byte header and closes it only after `read` returns. If `read` throws an `IOException`, the catch block logs and returns null without closing the file, so a file descriptor leaks for every failed open. The return value of `read` is also ignored. A file shorter than 8 bytes leaves the rest of the header zeroed, which the magic-byte or version check then rejects, so that part is harmless today but only by accident.

**Steps:**
1. Read the header inside `RandomAccessFile(filename, "r").use { ... }` so the file is closed on every path.
2. Optionally switch to `readFully` and treat `EOFException` as "not a YES file", which makes the short-file case explicit.
3. `YesReaderFactoryTest` already covers the header outcomes, including a file shorter than the header. A failing `read` is hard to provoke in a unit test, and the change is mechanical.

**Difficulty:** Trivial.

---

[← Back to remediation index](../tech-debt-remediation.md)
