# REM-16: Convert Core Java Files to Kotlin

**Addresses:** TD-11
**Module:** Various
**BRICE:** B=3 R=1 I=3 C=3 E=5 → **3.0**
**Phase:** 3 — Modernization

**Status:** Substantial progress — 9 of the 11 originally-listed files ported. Remaining: `Sync.java` and `InternalDb.java` (high-risk).

**Steps — ordered by value and risk (lowest risk first):**

| Priority | File | Risk | Notes |
|----------|------|------|-------|
| 1 | ~~`Highlights.java`~~ | ~~Low~~ | ✅ ported to `Highlights.kt` (2026-05-12). `object` with `@JvmStatic` methods and `@JvmField` properties to preserve Java call sites; 5 Kotlin callers got `!!` on `Info.partial` (now properly nullable). All 31 `HighlightsTest` cases + full unit-test suite pass. |
| 2 | ~~`TargetDecoder.java`~~ | ~~Low~~ | ✅ ported to `TargetDecoder.kt` (2026-05-13). `object` with `@JvmStatic decode()`; returns non-nullable `IntArrayList` (empty on error) to preserve call sites. |
| 3 | ~~`Jumper.java`~~ | ~~Low~~ | ✅ ported to `Jumper.kt` on 2026-05-12; behavior preserved, JumperTest + full unit suite pass |
| 4 | ~~`QueryTokenizer.java`~~ | ~~Low~~ | ✅ ported to `QueryTokenizer.kt` (2026-05-13). `object` with `@JvmStatic` on all public methods; `tokenize()` accepts `String?` for backwards-compatible null handling. |
| 5 | ~~`DevotionDownloader.java`~~ | ~~Low~~ | ✅ ported to `DevotionDownloader.kt` (2026-05-13). Regular Kotlin class with `@Synchronized`/`@Volatile` annotations, `::downloadLoop` method reference. |
| 6 | ~~`Provider.java`~~ | ~~Medium~~ | ✅ ported to `Provider.kt` (2026-05-13). `open class` (needed for test anonymous subclass); `companion object` for constants and static `uriMatcher`; `when` replaces `switch`. |
| 7 | ~~`SongBookUtil.java`~~ | ~~Medium~~ | ✅ ported to `SongBookUtil.kt` (2026-05-13). `object` with nested interfaces/classes; `@JvmStatic` on all public methods; `@JvmField` on `SongBookInfo` fields. |
| 8 | ~~`VerseRenderer.java`~~ | ~~Medium~~ | ✅ ported ([REM-26](REM-26-verserenderer-kotlin.md)) |
| 9 | ~~`SearchEngine.java`~~ | ~~Medium~~ | ✅ ported to `SearchEngine.kt` (2026-05-13). `object` with nested `ReadyTokens` class; `@JvmStatic` on public methods; `searchByGrep()` returns non-nullable `IntArrayList`; `sortWith` replaces `Arrays.sort`. |
| 10 | `Sync.java` | High | Threading + network, many call sites |
| 11 | `InternalDb.java` | High | Core database (already roughly halved by the DAO extraction) |

Not on the original list but still Java: `SyncAdapter.java` and the devotion article parsers (`DevotionArticle.java` + the six `Article*.java` implementations).

Use Android Studio's "Convert Java File to Kotlin" as a starting point, then manually clean up:
- Replace `@Nullable`/`@NonNull` with Kotlin nullability
- Replace `static` methods with top-level or companion object
- Replace `for` loops with `forEach`/`map`
- Replace `switch` with `when`
- Use data classes where appropriate

**Difficulty:** Varies (1 hour to 1 day per file depending on size). Do alongside other changes to avoid merge conflicts.

---

[← Back to remediation index](../tech-debt-remediation.md)
