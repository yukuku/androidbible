# Tech Debt Backlog

This is the single index for technical debt and remediation work. Each REM record under [`tech-debt-remediation/`](tech-debt-remediation/) owns its problem description, current status, completed work, and remaining steps. REM records can be open, partial, or complete; keep related findings in the same record and link to other REMs for distinct work. Existing IDs remain stable.

Scored work uses the BRICE framework below. Unscored items remain visible in their own section until their scope and priority are assessed. Refer to code by symbol name and dependency versions through `gradle/libs.versions.toml`.

Summary reconciled with the implementation, test sources, and task records on 2026-10-15. A check mark means the stated scope is complete; partial tasks retain their remaining steps in the linked record. This documentation review does not replace runtime validation of open findings.

## BRICE Evaluation Framework

| Dimension                   | Description                                                                | Scale                    |
|-----------------------------|----------------------------------------------------------------------------|--------------------------|
| **B** — Business Impact     | How much does fixing this benefit users, stability, or maintainability?    | 1 (low) – 5 (critical)   |
| **R** — Risk of Inaction    | What happens if we don't fix this? Data loss? Security? Developer churn?   | 1 (low) – 5 (critical)   |
| **I** — Implementation Cost | Developer time and effort (inverse: 5 = trivial, 1 = massive rewrite)      | 1 (months) – 5 (hours)   |
| **C** — Confidence          | How confident are we that the fix works and doesn't introduce regressions? | 1 (risky) – 5 (certain)  |
| **E** — Ecosystem Alignment | Does this align with modern Android best practices and library support?    | 1 (niche) – 5 (standard) |

**BRICE Score** = (B + R + I + C + E) / 5. Higher = do first.

---

## Phase 1: Quick Wins & Safety Fixes (BRICE ≥ 4.0, plus urgent user-data safety fixes from audits)

- [REM-01: Fix SongBookUtil Resource Leak & Unsafe Deserialization](tech-debt-remediation/REM-01-songbookutil-deserialization.md) ✅ **4.6**
- [REM-02: Fix Preferences hold()/unhold() Safety](tech-debt-remediation/REM-02-preferences-hold-unhold.md) ✅ **4.2**
- [REM-03: Replace LocalBroadcastManager](tech-debt-remediation/REM-03-localbroadcastmanager.md) ✅ **3.8**
- [REM-04: Fix FCM Token Re-registration Retry](tech-debt-remediation/REM-04-fcm-token-retry.md) ✅ **4.2**
- [REM-05: Refactor DevotionDownloader threading](tech-debt-remediation/REM-05-devotion-downloader-threading.md) ✅ **4.0**

### From the 2026-07 code audit

- [REM-33: Make Data-Transfer Import Safe](tech-debt-remediation/REM-33-import-safety.md) ✅ **3.8** (completed 2026-10-15; safe previews, transaction cleanup, reference validation, deleted-plan progress backups)
- [REM-35: Fix "Update Song Book" dataFormatVersion Handling](tech-debt-remediation/REM-35-songbook-update-dfv.md) ✅ **3.8** (completed 2026-07-20; mixed-version books are replaced by book name and stored as current JSON)
- [REM-36: Fix YES2 ASCII Search Decoder](tech-debt-remediation/REM-36-yes2-ascii-decoder.md) ✅ **3.8** (completed 2026-10-15; ASCII search decoding and round-trip coverage)
- [REM-34: Fix Sync Request Delivery and Shared-State Races](tech-debt-remediation/REM-34-sync-delivery.md) ✅ **3.4** (completed 2026-10-15; persisted delivery, shared-state guards)

## Phase 2: Architecture Improvements (BRICE 3.0–3.9)

- [REM-06: Extract IsiActivity Gesture Handling](tech-debt-remediation/REM-06-isiactivity-gestures.md) ✅ **3.4**
- [REM-07: Extract IsiActivity Action Mode](tech-debt-remediation/REM-07-isiactivity-action-mode.md) ✅ **3.4**
- [REM-08: Extract IsiActivity Split View Manager](tech-debt-remediation/REM-08-isiactivity-split-view.md) ✅ **3.2**
- [REM-09: Introduce ViewModel for IsiActivity](tech-debt-remediation/REM-09-isiactivity-viewmodel.md) — **3.4**
- [REM-32: Migrate SongDb to Room](tech-debt-remediation/REM-32-room-song-db.md) ✅ **3.2** — kept. Songs is module-isolated (own SQLite file, no FKs/transactions with Bible data) and song books are re-downloadable, so the migration risk is bounded. REM-21 (Phase 4) composed on top by swapping the Parcelable `data` BLOB to JSON.
- [REM-12: Replace DragSortListView with ItemTouchHelper](tech-debt-remediation/REM-12-itemtouchhelper.md) ✅ **3.4**
- [REM-14: Replace material-dialogs with Material 3](tech-debt-remediation/REM-14-material-dialogs.md) ✅ **3.4**
- [REM-18: Add Test Coverage for Core Modules](tech-debt-remediation/REM-18-test-coverage.md) ✅ **3.4**
- [REM-23: Port ybuild.sh to Gradle](tech-debt-remediation/REM-23-ybuild-gradle.md) ✅ **3.8**
- [REM-24: Refactor S.kt Service Locator](tech-debt-remediation/REM-24-s-service-locator.md) ✅ (24a–24d done) **3.2**
- [REM-37: Bible Audio: MediaSession State & Split-View Wiring](tech-debt-remediation/REM-37-bible-audio-state.md): ✅ **3.8** (completed 2026-10-15; playback observation and appearance refresh)
- [REM-38: Reader Lifecycle Fixes](tech-debt-remediation/REM-38-reader-lifecycle.md): ✅ **3.4** (completed 2026-10-15; restoration, intent parsing, dialog lifecycle)
- [REM-39: Text Decoding Correctness](tech-debt-remediation/REM-39-text-decoding.md): ✅ Complete on 2026-10-15
- [REM-41: Defensive Data Guards](tech-debt-remediation/REM-41-defensive-guards.md): ✅ Complete on 2026-10-15
- [REM-40: Version Download Robustness](tech-debt-remediation/REM-40-download-robustness.md): ✅ Complete on 2026-10-15

### From the REM-16 Kotlin conversion (2026-09)

- [REM-46: Stop Debouncer From Leaking a Thread per Instance](tech-debt-remediation/REM-46-debouncer-thread-leak.md) ✅ **3.6** (completed 2026-10-15; coroutine delay and shared IO threads)
- [REM-47: Close the Header File in YesReaderFactory on Read Failure](tech-debt-remediation/REM-47-yesreaderfactory-file-leak.md) ✅ **3.6** (completed 2026-10-15; scoped header reads close on failure)
- [REM-48: Harden OldVerseTextDecoder.Ascii](tech-debt-remediation/REM-48-old-ascii-decoder.md): ✅ Complete on 2026-10-15

## Phase 3: Modernization (BRICE 2.5–3.0)

- [REM-15: Introduce Kotlin Coroutines](tech-debt-remediation/REM-15-coroutines.md): **3.2** (partial; devotion downloads complete, sync/search/version loading remain)
- [REM-16: Convert Core Java Files to Kotlin](tech-debt-remediation/REM-16-java-to-kotlin.md) — **3.0** (9 of 11 files ported)
- [REM-17: Migrate Build to Kotlin DSL & Version Catalogs](tech-debt-remediation/REM-17-kotlin-dsl-build.md) ✅ **3.0**
- [REM-19: Replace PRDownloaderFixed with WorkManager Downloads](tech-debt-remediation/REM-19-prdownloader-replacement.md) ✅ **2.8**
- [REM-20: Replace AmbilWarna with Material Color Picker](tech-debt-remediation/REM-20-ambilwarna-replacement.md) ✅ **3.0**
- [REM-25: Decompose VerseRenderer.render()](tech-debt-remediation/REM-25-verserenderer-decompose.md) ✅ **3.4**
- [REM-26: Port VerseRenderer to Kotlin](tech-debt-remediation/REM-26-verserenderer-kotlin.md) ✅ **3.8**

## Phase 4: Long-term / Major Refactors (BRICE < 2.5)

- [REM-21: Migrate Song Storage from Parcelable to JSON](tech-debt-remediation/REM-21-song-json-storage.md) ✅ **2.8** — app-side done; backend redirect branching and `kidung-data` authoring are out of scope (separate repos).
- [REM-22: Introduce Jetpack Compose for New Screens](tech-debt-remediation/REM-22-jetpack-compose.md): **2.6** (ongoing; navigation, verse lists/dialogs, reader toolbar, songs, and sync login default to Compose; search toolbar also ported)
- [REM-45: Migrate the IsiActivity Shell to Compose (Staged)](tech-debt-remediation/REM-45-isiactivity-compose-shell.md): **2.2** (partial; normal toolbar ported, selection toolbar and other shell elements remain; split-container migration depends on retiring the legacy verse pipeline)
- [REM-44: Make the Daily-Verse Widget's Version Fallback Explicit](tech-debt-remediation/REM-44-widget-version-fallback.md) ✅ **2.6** (completed 2026-10-20; localized fallback notice and per-widget reconfiguration)
- [REM-42: Bounded Eviction for the Version Cache](tech-debt-remediation/REM-42-version-cache-eviction.md) — **2.4** (2026-07 audit)
- [REM-43: Surface or Resolve Cross-Device Sync Conflicts](tech-debt-remediation/REM-43-sync-conflict-handling.md) — **2.4** (long-standing last-write-wins limitation)

## Open Work Awaiting Prioritization

- [REM-49: Simplify Bible Database SQL and Storage Plumbing](tech-debt-remediation/REM-49-bible-storage-plumbing.md): handwritten queries, duplicate reordering logic, and large SyncShadow chunk reads. Existing DAO and statement-lifetime fixes are recorded in the same item.
- [REM-50: Centralize Sync API Calls and Failure Handling](tech-debt-remediation/REM-50-sync-api-boundary.md): endpoint construction, error classification, and transient data-sync retries. Queue delivery is complete in REM-34; conflicts remain in REM-43.
- [REM-51: Make Preference Types and Cache Invalidation Explicit](tech-debt-remediation/REM-51-preferences-architecture.md): typed keys/defaults and cache ownership. Hold/unhold safety is complete in REM-02; editor synchronization is complete in REM-34.
- [REM-52: Review the FancyShowCaseView Dependency](tech-debt-remediation/REM-52-showcase-dependency.md): evaluate maintenance, Android compatibility, and tip anchoring alongside reader UI work.

## How to use this index

The entries above are the single status listing. Scores and phases record the
original prioritization; completed entries are retained for rationale and
verification history. Choose future work from open and partial entries, then
read the linked record for remaining scope. Completed records may describe
intermediate implementations that later work superseded.
