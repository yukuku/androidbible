# ~~REM-18: Add Test Coverage for Core Modules~~ ✅ COMPLETED (2026-05-11)

**Addresses:** TD-13
**Module:** Cross-cutting
**BRICE:** B=4 R=3 I=2 C=3 E=5 → **3.4**
**Phase:** 2 — Architecture Improvements

**Outcome:** All six sub-steps shipped — see per-step entries below. Cumulatively this added ~170 unit tests across `HighlightsTest`, the four `Sync*Test` files, `InternalDbTest`, `SearchEngineTest`, `Yes2RoundTripTest` + `SnappyStreamRoundTripTest`, and `ProviderTest`, plus latent-bug fixes in `Highlights.alphaMix`, `Sync_Pins.Content.equals/hashCode`, and a flagged `SnappyInputStream` EOF edge. Robolectric is now a `testImplementation` dependency on `Alkitab`, and test-scope shadows for `android.util.Log` / `FirebaseCrashlytics` / `android.util.Pair` unblock further pure-JUnit work on Android-touching code.

**Steps — prioritized by risk coverage:**

**Step 18a: Highlight encoding tests** ✅ COMPLETED
**Completed in:** `513961b9` / `1ca73821` (2026-04-16)
1. ✅ Added 31 unit tests in `HighlightsTest.kt`: encode/decode round-trip, hash verification, partial-highlight guard, alphaMix with ARGB input
2. ✅ Fixed `Highlights.alphaMix()` ARGB leak: changed `0xa0000000 | colorRgb` to `0xa0000000 | (colorRgb & 0x00ffffff)` so the output alpha is always `0xA0` regardless of what the caller passes
3. ✅ Added test-scope no-op shadows for `android.util.Log` and `com.google.firebase.crashlytics.FirebaseCrashlytics` — these unblock all future Alkitab module unit tests that touch `AppLog`

**Step 18b: Sync protocol tests** ✅ COMPLETED
**Completed in:** `b4bce934` (2026-04-16)
1. ✅ Added 101 unit tests across `SyncDeltaTest.kt`, `Sync_MabelTest.kt`, `Sync_PinsTest.kt`, `Sync_RpTest.kt` — covers `SyncAdapter.patchNoConflict` delta application (add/mod/del, missing GIDs, conflict, idempotency), `Sync.entitiesEqual`, `SyncUtils.findEntity/isSameContent`, `Sync_Mabel.updateMarker/Label/Marker_Label`, and `Content equals/hashCode/toString` for all three sync sets
2. ✅ Fixed `Sync_Pins.Content.equals()`: was sorting copies but comparing the original unsorted lists — now compares sorted copies. Fixed `hashCode()` to match order-insensitive equals (violations would cause misbehavior inside `HashMap`/`HashSet`)
3. ✅ Added test-scope stub for `android.util.Pair` so `patchNoConflict` runs without Robolectric

**Step 18c: InternalDb tests (or Room DAO tests)** ✅ COMPLETED
1. ✅ Added Robolectric as a `testImplementation` dependency and enabled `testOptions.unitTests.includeAndroidResources` in `Alkitab/build.gradle` — Robolectric is required because `InternalDbHelper` extends Android's `SQLiteOpenHelper`
2. ✅ Added `InternalDbTest.kt` under `Alkitab/src/test/java/yuku/alkitab/base/storage/` using `RobolectricTestRunner` with `@Config(application = Application::class)` so `yuku.alkitab.base.App.onCreate` (Firebase / PRDownloader / FCM) doesn't run. Reuses the existing test-scope shadows of `android.util.Log` and `com.google.firebase.crashlytics.FirebaseCrashlytics` (added in REM-18a) so `AppLog`'s static initializer loads without bootstrapping Firebase. `yuku.afw.App.context` is set manually in `@Before`; because no `sync_simpleToken` preference is present, `Sync.notifySyncNeeded` early-returns and no background work fires
3. ✅ Test names follow the Kotlin backtick-sentence convention from CLAUDE.md. Covers marker CRUD (`insertMarker`, `insertOrUpdateMarker`, `getMarkerById/Gid`, `listMarkersForAriKind`, `listAllMarkers`, `deleteMarkerById` with cascade to `Marker_Label`, `countMarkersForBookChapter`), label ordering (`insertLabel`, `getLabelMaxOrdering`, `reorderLabels` up/down, `sortLabelsAlphabetically`, `listLabelsByMarker` ordering), highlight storage (`updateOrInsertHighlights` insert/update/delete, `updateOrInsertPartialHighlight` including dedup of sync-duplicates, `getHighlightColorRgb` single/multi-verse), and attribute loading (`putAttributes` bookmarks, notes, multi-verse highlight spread, ordering by `modifyTime`, book-chapter filtering)

**Step 18d: SearchEngine tests** ✅ COMPLETED
1. ✅ Unit tests added in `SearchEngineTest.kt` — covers `ReadyTokens` construction, `satisfiesTokens`, and end-to-end `searchByGrep` against a small in-memory fake `Version`. Exercises single token, multi-token (AND) intersection, whole-word matching, quoted phrases (multiword), book-id filtering, duplicate-token de-duplication, and cross-verse-boundary rejection.
2. ✅ Runs under `RobolectricTestRunner` so `android.util.SparseBooleanArray` is a real implementation rather than the "not mocked" stub. `AppLog` is kept quiet via the existing test-scope shadows (`android.util.Log`, `FirebaseCrashlytics`) introduced for `HighlightsTest`.
3. Difficulty: Medium (4-6 hours)

**Step 18e: YES2 reader/writer round-trip tests** ✅ COMPLETED
1. ✅ Added `Yes2RoundTripTest.kt` (12 tests) and `SnappyStreamRoundTripTest.kt` (4 tests) under `AlkitabYes2/src/test/java/`. Round-trip covers: single-book uncompressed read-back of every verse; multi-book boundaries (Genesis / Exodus / Revelation) preserving book ordering and chapter offsets; BMP UTF-8 content (Indonesian, Greek, Hebrew, extended Latin) — limited to U+FFFF because `Utf8Decoder` is documented as "intentionally incomplete" and does not support 4-byte UTF-8 sequences; `dontSeparateVerses` newline-joined form; `lowercase` flag; out-of-range chapter returns null; pericope round-trip with parallels and ARI-keyed lookup; pericope empty-chapter and no-pericope-section cases; missing xref / footnote sections return null.
2. ✅ Snappy-compressed text section round-trip: writes multi-block compressed Bible text and verifies every verse decodes identically; separately verifies that a compressed file is strictly smaller than the uncompressed equivalent for highly-repetitive text.
3. ✅ Direct `SnappyOutputStream` / `SnappyInputStream` round-trip: single-block, multi-block (4 blocks with partial trailing block), mid-block `seek` crossing block boundaries, and compression-ratio check on highly-repetitive input (each 4 KB block compresses to under 400 bytes via the pure-Java codec).
4. ✅ Added a test-scope `android.util.Log` shadow at `AlkitabYes2/src/test/java/android/util/Log.java` — mirrors the one in the Alkitab module so direct `Log.e` calls in `Yes2Reader`, `SectionIndex`, and the xref/footnote sections don't trigger "Method not mocked" during plain JUnit.
5. The tests exposed a latent bug in `SnappyInputStream`: after the last byte of the last block is read, calling `read()` again increments `current_block_index` past the end and throws `ArrayIndexOutOfBoundsException` instead of returning -1. Production callers in `Yes2Reader.TextSectionReader.loadVerseText` always read exact-length ranges derived from verse `varuint` lengths, so they never hit EOF this way. Flagged in-file rather than silently worked around; see `SnappyStreamRoundTripTest.kt`.

**Step 18f: Content provider tests** ✅ COMPLETED (2026-05-11)
1. ✅ Added `ProviderTest.kt` (14 tests) under `Alkitab/src/test/java/yuku/alkitab/base/cp/` exercising every URI path the read-only [`Provider`](../../Alkitab/src/main/java/yuku/alkitab/base/cp/Provider.kt) supports: single-verse by ARI (with `_id`, `ari`, book short name, and verse-text assertions), single-verse by LID (resolved via `LidToAri`), range by ARI within a single chapter, range by ARI crossing a chapter boundary, range by ARI crossing a book boundary, range by LID, whole-chapter shorthand (`bbcc00-bbcc00` → all verses in that chapter), and the `bible/versions` listing query (asserts the internal version row using `AppConfig.get()` for `shortName` / `longName` / `description`).
2. ✅ Pinned the contract edges: `formatting=0` (default) strips inline formatting codes via `FormattedVerseText.removeSpecialCodes`, `formatting=1` returns the raw verse text including codes, out-of-range `bookId` (single or range) returns an empty `MatrixCursor` rather than null, an unknown URI path returns null, and `getType` returns null for every URI (production behaviour — `Provider.getType` is hardcoded to null).
3. ✅ Same Robolectric setup as steps 18c/d: `@Config(application = Application::class)` to skip `App.onCreate`; existing test-scope shadows of `android.util.Log` and `FirebaseCrashlytics` keep `AppLog` quiet; `yuku.afw.App.context` is wired in `@Before`. The fake `MVersion` returns an anonymous `Version` subclass with two books, three chapters, and one verse carrying real `@@` / `@9` / `@7` formatting codes; `S.setActiveVersion(fakeMv)` overwrites the active version after `ActiveVersionHolder`'s init naturally falls back to the placeholder DDD `MVersionInternal` (constructor-only, no asset I/O). The provider is constructed as an anonymous subclass that no-ops `onCreate` to skip `App.staticInit` (FCM / PRDownloader / FeedbackSender) while still running `attachInfo` to set up the static `UriMatcher`.

---

[← Back to remediation index](../tech-debt-remediation.md)
