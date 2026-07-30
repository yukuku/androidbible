# Tech Debt Remediation Plan

This document is an index over the prioritized remediation plan for each tech debt item identified in [tech-debt.md](tech-debt.md). Each task has its own file under [`tech-debt-remediation/`](tech-debt-remediation/), scoped to a specific module or subsystem and evaluated using the BRICE framework.

## BRICE Evaluation Framework

| Dimension | Description | Scale |
|-----------|-------------|-------|
| **B** — Business Impact | How much does fixing this benefit users, stability, or maintainability? | 1 (low) – 5 (critical) |
| **R** — Risk of Inaction | What happens if we don't fix this? Data loss? Security? Developer churn? | 1 (low) – 5 (critical) |
| **I** — Implementation Cost | Developer time and effort (inverse: 5 = trivial, 1 = massive rewrite) | 1 (months) – 5 (hours) |
| **C** — Confidence | How confident are we that the fix works and doesn't introduce regressions? | 1 (risky) – 5 (certain) |
| **E** — Ecosystem Alignment | Does this align with modern Android best practices and library support? | 1 (niche) – 5 (standard) |

**BRICE Score** = (B + R + I + C + E) / 5. Higher = do first.

---

## Phase 1: Quick Wins & Safety Fixes (BRICE ≥ 4.0, plus urgent user-data safety fixes from audits)

- [REM-01: Fix SongBookUtil Resource Leak & Unsafe Deserialization](tech-debt-remediation/REM-01-songbookutil-deserialization.md) ✅ **4.6**
- [REM-02: Fix Preferences hold()/unhold() Safety](tech-debt-remediation/REM-02-preferences-hold-unhold.md) ✅ **4.2**
- [REM-03: Replace LocalBroadcastManager](tech-debt-remediation/REM-03-localbroadcastmanager.md) ✅ **3.8**
- [REM-04: Fix FCM Token Re-registration Retry](tech-debt-remediation/REM-04-fcm-token-retry.md) ✅ **4.2**
- [REM-05: Refactor DevotionDownloader threading](tech-debt-remediation/REM-05-devotion-downloader-threading.md) ✅ **4.0**

### From the 2026-07 code audit

- [REM-33: Make Data-Transfer Import Safe](tech-debt-remediation/REM-33-import-safety.md) — **3.8** (import dry-run destroys reading history, failed-import transaction leak, round-trip gaps)
- [REM-35: Fix "Update Song Book" dataFormatVersion Handling](tech-debt-remediation/REM-35-songbook-update-dfv.md) — **3.8** (mixed dataFormatVersion → duplicate songs + crash-on-read)
- [REM-36: Fix YES2 ASCII Search Decoder](tech-debt-remediation/REM-36-yes2-ascii-decoder.md) — **3.8** (one-character fix; search broken for ASCII-encoded versions)
- [REM-34: Fix Sync Request Delivery and Shared-State Races](tech-debt-remediation/REM-34-sync-delivery.md) — **3.4** (dropped sync requests, shared-state races)

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
- [REM-37: Bible Audio — MediaSession State & Split-View Wiring](tech-debt-remediation/REM-37-bible-audio-state.md) — **3.8** (2026-07 audit)
- [REM-38: Reader Lifecycle Fixes](tech-debt-remediation/REM-38-reader-lifecycle.md) — **3.4** (2026-07 audit)
- [REM-39: Text Decoding Correctness](tech-debt-remediation/REM-39-text-decoding.md) — **3.4** (2026-07 audit)
- [REM-41: Defensive Data Guards](tech-debt-remediation/REM-41-defensive-guards.md) — **3.4** (2026-07 audit)
- [REM-40: Version Download Robustness](tech-debt-remediation/REM-40-download-robustness.md) — **3.4** (2026-07 audit)

## Phase 3: Modernization (BRICE 2.5–3.0)

- [REM-15: Introduce Kotlin Coroutines](tech-debt-remediation/REM-15-coroutines.md) — **3.2**
- [REM-16: Convert Core Java Files to Kotlin](tech-debt-remediation/REM-16-java-to-kotlin.md) — **3.0** (9 of 11 files ported)
- [REM-17: Migrate Build to Kotlin DSL & Version Catalogs](tech-debt-remediation/REM-17-kotlin-dsl-build.md) ✅ **3.0**
- [REM-19: Replace PRDownloaderFixed with WorkManager Downloads](tech-debt-remediation/REM-19-prdownloader-replacement.md) ✅ **2.8**
- [REM-20: Replace AmbilWarna with Material Color Picker](tech-debt-remediation/REM-20-ambilwarna-replacement.md) ✅ **3.0**
- [REM-25: Decompose VerseRenderer.render()](tech-debt-remediation/REM-25-verserenderer-decompose.md) ✅ **3.4**
- [REM-26: Port VerseRenderer to Kotlin](tech-debt-remediation/REM-26-verserenderer-kotlin.md) ✅ **3.8**

## Phase 4: Long-term / Major Refactors (BRICE < 2.5)

- [REM-21: Migrate Song Storage from Parcelable to JSON](tech-debt-remediation/REM-21-song-json-storage.md) ✅ **2.8** — app-side done; backend redirect branching and `kidung-data` authoring are out of scope (separate repos).
- [REM-22: Introduce Jetpack Compose for New Screens](tech-debt-remediation/REM-22-jetpack-compose.md) — **2.6** (kicked off; the Goto Compose port is currently gated behind an experimental flag with the legacy screen as default)
- [REM-44: Make the Daily-Verse Widget's Version Fallback Explicit](tech-debt-remediation/REM-44-widget-version-fallback.md) — **2.6** (2026-07 audit)
- [REM-42: Bounded Eviction for the Version Cache](tech-debt-remediation/REM-42-version-cache-eviction.md) — **2.4** (2026-07 audit)
- [REM-43: Surface or Resolve Cross-Device Sync Conflicts](tech-debt-remediation/REM-43-sync-conflict-handling.md) — **2.4** (long-standing last-write-wins limitation)

---

## Priority Summary (Sorted by BRICE Score)

| ID | Task | BRICE | Phase |
|----|------|-------|-------|
| [REM-01](tech-debt-remediation/REM-01-songbookutil-deserialization.md) | ~~Fix SongBookUtil deserialization safety~~ ✅ | **4.6** | 1 |
| [REM-02](tech-debt-remediation/REM-02-preferences-hold-unhold.md) | ~~Fix Preferences hold/unhold safety~~ ✅ | **4.2** | 1 |
| [REM-04](tech-debt-remediation/REM-04-fcm-token-retry.md) | ~~Fix FCM token retry~~ ✅ | **4.2** | 1 |
| [REM-05](tech-debt-remediation/REM-05-devotion-downloader-threading.md) | ~~Fix DevotionDownloader threading~~ ✅ | **4.0** | 1 |
| [REM-03](tech-debt-remediation/REM-03-localbroadcastmanager.md) | ~~Replace LocalBroadcastManager~~ ✅ | **3.8** | 1 |
| [REM-23](tech-debt-remediation/REM-23-ybuild-gradle.md) | ~~Port ybuild.sh to Gradle~~ ✅ | **3.8** | 2 |
| [REM-26](tech-debt-remediation/REM-26-verserenderer-kotlin.md) | ~~Port VerseRenderer to Kotlin~~ ✅ | **3.8** | 3 |
| [REM-33](tech-debt-remediation/REM-33-import-safety.md) | Make data-transfer import safe (2026-07 audit) | **3.8** | 1 |
| [REM-35](tech-debt-remediation/REM-35-songbook-update-dfv.md) | Fix song book update dataFormatVersion (2026-07 audit) | **3.8** | 1 |
| [REM-36](tech-debt-remediation/REM-36-yes2-ascii-decoder.md) | Fix YES2 ASCII search decoder (2026-07 audit) | **3.8** | 1 |
| [REM-37](tech-debt-remediation/REM-37-bible-audio-state.md) | Bible audio MediaSession state + split wiring (2026-07 audit) | **3.8** | 2 |
| [REM-06](tech-debt-remediation/REM-06-isiactivity-gestures.md) | ~~Extract IsiActivity gestures~~ ✅ | **3.4** | 2 |
| [REM-07](tech-debt-remediation/REM-07-isiactivity-action-mode.md) | ~~Extract IsiActivity action mode~~ ✅ | **3.4** | 2 |
| [REM-09](tech-debt-remediation/REM-09-isiactivity-viewmodel.md) | Introduce ViewModel | **3.4** | 2 |
| [REM-12](tech-debt-remediation/REM-12-itemtouchhelper.md) | ~~Replace DragSortListView~~ ✅ | **3.4** | 2 |
| [REM-14](tech-debt-remediation/REM-14-material-dialogs.md) | ~~Replace material-dialogs~~ ✅ | **3.4** | 2 |
| [REM-18](tech-debt-remediation/REM-18-test-coverage.md) | ~~Add test coverage~~ ✅ | **3.4** | 2 |
| [REM-34](tech-debt-remediation/REM-34-sync-delivery.md) | Fix sync delivery + shared-state races (2026-07 audit) | **3.4** | 1 |
| [REM-38](tech-debt-remediation/REM-38-reader-lifecycle.md) | Reader lifecycle fixes (2026-07 audit) | **3.4** | 2 |
| [REM-39](tech-debt-remediation/REM-39-text-decoding.md) | Text decoding correctness (2026-07 audit) | **3.4** | 2 |
| [REM-40](tech-debt-remediation/REM-40-download-robustness.md) | Version download robustness (2026-07 audit) | **3.4** | 2 |
| [REM-41](tech-debt-remediation/REM-41-defensive-guards.md) | Defensive data guards (2026-07 audit) | **3.4** | 2 |
| [REM-25](tech-debt-remediation/REM-25-verserenderer-decompose.md) | ~~Decompose VerseRenderer.render~~ ✅ | **3.4** | 3 |
| [REM-24](tech-debt-remediation/REM-24-s-service-locator.md) | ~~Refactor S.kt service locator (steps 24a–24d complete)~~ ✅ | **3.2** | 2 |
| [REM-08](tech-debt-remediation/REM-08-isiactivity-split-view.md) | ~~Extract split view manager~~ ✅ | **3.2** | 2 |
| [REM-32](tech-debt-remediation/REM-32-room-song-db.md) | ~~Room migration (SongDb)~~ ✅ | **3.2** | 2 |
| [REM-15](tech-debt-remediation/REM-15-coroutines.md) | Introduce coroutines | **3.2** | 3 |
| [REM-16](tech-debt-remediation/REM-16-java-to-kotlin.md) | Java→Kotlin conversion (9/11 done) | **3.0** | 3 |
| [REM-17](tech-debt-remediation/REM-17-kotlin-dsl-build.md) | ~~Kotlin DSL build migration~~ ✅ | **3.0** | 3 |
| [REM-20](tech-debt-remediation/REM-20-ambilwarna-replacement.md) | ~~Replace AmbilWarna~~ ✅ | **3.0** | 3 |
| [REM-19](tech-debt-remediation/REM-19-prdownloader-replacement.md) | ~~Replace PRDownloader~~ ✅ | **2.8** | 3 |
| [REM-21](tech-debt-remediation/REM-21-song-json-storage.md) | ~~Song storage migration~~ ✅ (app-side) | **2.8** | 4 |
| [REM-22](tech-debt-remediation/REM-22-jetpack-compose.md) | Jetpack Compose adoption (kicked off) | **2.6** | 4 |
| [REM-44](tech-debt-remediation/REM-44-widget-version-fallback.md) | Explicit widget version fallback (2026-07 audit) | **2.6** | 4 |
| [REM-42](tech-debt-remediation/REM-42-version-cache-eviction.md) | Bounded version-cache eviction (2026-07 audit) | **2.4** | 4 |
| [REM-43](tech-debt-remediation/REM-43-sync-conflict-handling.md) | Surface/resolve sync conflicts | **2.4** | 4 |

## Suggested Execution Order

**Sprint 1 (1 week):** ~~REM-01~~✅, ~~REM-02~~✅, ~~REM-04~~✅, ~~REM-05~~✅ — quick safety fixes (all done)
**Sprint 2 (1 week):** ~~REM-03~~✅ — LocalBroadcastManager removal (done)
**Sprint 3 (2 weeks):** ~~REM-07~~✅, ~~REM-06~~✅, ~~REM-08~~✅ — IsiActivity decomposition (all done)
**Sprint 4 (1 week):** ~~REM-12~~✅, ~~REM-14~~✅ — deprecated library replacements (done)
**Sprint 5 (2 weeks):** ~~REM-32~~✅ — Room migration for SongDb (`AlkitabSongRoomDb`). `InternalDbHelper` is the active source of truth for every Bible-related table; `SongDbHelper` is a rollback safety net only.
**Sprint 6 (2 weeks):** REM-09, ~~REM-18a-f~~✅ — ViewModel + test coverage (REM-18a/b/c/d/e/f done)
**Ongoing:** REM-15, REM-16, ~~REM-17~~✅ — modernization work mixed into feature sprints (REM-17 done)

### 2026-07 audit follow-up (suggested)

**Sprint 7 (1 week) — user-data safety first:** REM-36 (1-line search fix), REM-35 (song book update), REM-33 (import safety), plus the REM-41 widget one-liner
**Sprint 8 (1 week):** REM-34 (sync delivery/races), rest of REM-41 (defensive guards)
**Sprint 9 (1 week):** REM-37 (Bible audio state), REM-38 (reader lifecycle), REM-39 (text decoding), REM-40 (download robustness)
