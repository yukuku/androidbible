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

## Phase 1: Quick Wins & Safety Fixes (BRICE ≥ 4.0)

- [REM-01: Fix SongBookUtil Resource Leak & Unsafe Deserialization](tech-debt-remediation/REM-01-songbookutil-deserialization.md) ✅ **4.6**
- [REM-02: Fix Preferences hold()/unhold() Safety](tech-debt-remediation/REM-02-preferences-hold-unhold.md) ✅ **4.2**
- [REM-03: Replace LocalBroadcastManager](tech-debt-remediation/REM-03-localbroadcastmanager.md) ✅ **3.8**
- [REM-04: Fix FCM Token Re-registration Retry](tech-debt-remediation/REM-04-fcm-token-retry.md) ✅ **4.2**
- [REM-05: Refactor DevotionDownloader threading](tech-debt-remediation/REM-05-devotion-downloader-threading.md) ✅ **4.0**

## Phase 2: Architecture Improvements (BRICE 3.0–3.9)

- [REM-06: Extract IsiActivity Gesture Handling](tech-debt-remediation/REM-06-isiactivity-gestures.md) ✅ **3.4**
- [REM-07: Extract IsiActivity Action Mode](tech-debt-remediation/REM-07-isiactivity-action-mode.md) ✅ **3.4**
- [REM-08: Extract IsiActivity Split View Manager](tech-debt-remediation/REM-08-isiactivity-split-view.md) ✅ **3.2**
- [REM-09: Introduce ViewModel for IsiActivity](tech-debt-remediation/REM-09-isiactivity-viewmodel.md) — **3.4**
- [REM-10: Migrate InternalDb to Room (Markers Table)](tech-debt-remediation/REM-10-room-markers.md) ✅ **3.4**
- [REM-11: Migrate InternalDb to Room (Version Table)](tech-debt-remediation/REM-11-room-version.md) ✅ **3.2**
- [REM-27: Migrate InternalDb to Room (Devotion Table)](tech-debt-remediation/REM-27-room-devotion.md) ✅ **3.2**
- [REM-28: Migrate InternalDb to Room (PerVersion Table)](tech-debt-remediation/REM-28-room-per-version.md) ✅ **3.2**
- [REM-29: Migrate InternalDb to Room (ProgressMark Tables)](tech-debt-remediation/REM-29-room-progress-mark.md) ✅ **3.2**
- [REM-30: Migrate InternalDb to Room (ReadingPlan Tables)](tech-debt-remediation/REM-30-room-reading-plan.md) ✅ **3.2**
- [REM-31: Migrate InternalDb to Room (SyncShadow + SyncLog Tables)](tech-debt-remediation/REM-31-room-sync-shadow.md) ✅ **3.2**
- [REM-12: Replace DragSortListView with ItemTouchHelper](tech-debt-remediation/REM-12-itemtouchhelper.md) ✅ **3.4**
- [REM-14: Replace material-dialogs with Material 3](tech-debt-remediation/REM-14-material-dialogs.md) ✅ **3.4**
- [REM-18: Add Test Coverage for Core Modules](tech-debt-remediation/REM-18-test-coverage.md) ✅ **3.4**
- [REM-23: Port ybuild.sh to Gradle](tech-debt-remediation/REM-23-ybuild-gradle.md) ✅ **3.8**
- [REM-24: Refactor S.kt Service Locator](tech-debt-remediation/REM-24-s-service-locator.md) ✅ (24a–24d done) **3.2**

## Phase 3: Modernization (BRICE 2.5–3.0)

- [REM-15: Introduce Kotlin Coroutines](tech-debt-remediation/REM-15-coroutines.md) — **3.2**
- [REM-16: Convert Core Java Files to Kotlin](tech-debt-remediation/REM-16-java-to-kotlin.md) — **3.0** (9 of 11 files ported)
- [REM-17: Migrate Build to Kotlin DSL & Version Catalogs](tech-debt-remediation/REM-17-kotlin-dsl-build.md) ✅ **3.0**
- [REM-19: Replace PRDownloaderFixed with WorkManager Downloads](tech-debt-remediation/REM-19-prdownloader-replacement.md) ✅ **2.8**
- [REM-20: Replace AmbilWarna with Material Color Picker](tech-debt-remediation/REM-20-ambilwarna-replacement.md) ✅ **3.0**
- [REM-25: Decompose VerseRenderer.render()](tech-debt-remediation/REM-25-verserenderer-decompose.md) ✅ **3.4**
- [REM-26: Port VerseRenderer to Kotlin](tech-debt-remediation/REM-26-verserenderer-kotlin.md) ✅ **3.8**

## Phase 4: Long-term / Major Refactors (BRICE < 2.5)

- [REM-21: Migrate Song Storage from Parcelable to JSON](tech-debt-remediation/REM-21-song-json-storage.md) — **2.8**
- [REM-22: Introduce Jetpack Compose for New Screens](tech-debt-remediation/REM-22-jetpack-compose.md) — **2.6** (kicked off)

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
| [REM-06](tech-debt-remediation/REM-06-isiactivity-gestures.md) | ~~Extract IsiActivity gestures~~ ✅ | **3.4** | 2 |
| [REM-07](tech-debt-remediation/REM-07-isiactivity-action-mode.md) | ~~Extract IsiActivity action mode~~ ✅ | **3.4** | 2 |
| [REM-09](tech-debt-remediation/REM-09-isiactivity-viewmodel.md) | Introduce ViewModel | **3.4** | 2 |
| [REM-10](tech-debt-remediation/REM-10-room-markers.md) | ~~Room migration (Markers)~~ ✅ | **3.4** | 2 |
| [REM-12](tech-debt-remediation/REM-12-itemtouchhelper.md) | ~~Replace DragSortListView~~ ✅ | **3.4** | 2 |
| [REM-14](tech-debt-remediation/REM-14-material-dialogs.md) | ~~Replace material-dialogs~~ ✅ | **3.4** | 2 |
| [REM-18](tech-debt-remediation/REM-18-test-coverage.md) | ~~Add test coverage~~ ✅ | **3.4** | 2 |
| [REM-25](tech-debt-remediation/REM-25-verserenderer-decompose.md) | ~~Decompose VerseRenderer.render~~ ✅ | **3.4** | 3 |
| [REM-24](tech-debt-remediation/REM-24-s-service-locator.md) | ~~Refactor S.kt service locator (steps 24a–24d complete)~~ ✅ | **3.2** | 2 |
| [REM-08](tech-debt-remediation/REM-08-isiactivity-split-view.md) | ~~Extract split view manager~~ ✅ | **3.2** | 2 |
| [REM-11](tech-debt-remediation/REM-11-room-version.md) | ~~Room migration (Version)~~ ✅ | **3.2** | 2 |
| [REM-27](tech-debt-remediation/REM-27-room-devotion.md) | ~~Room migration (Devotion)~~ ✅ | **3.2** | 2 |
| [REM-28](tech-debt-remediation/REM-28-room-per-version.md) | ~~Room migration (PerVersion)~~ ✅ | **3.2** | 2 |
| [REM-29](tech-debt-remediation/REM-29-room-progress-mark.md) | ~~Room migration (ProgressMark)~~ ✅ | **3.2** | 2 |
| [REM-30](tech-debt-remediation/REM-30-room-reading-plan.md) | ~~Room migration (ReadingPlan)~~ ✅ | **3.2** | 2 |
| [REM-31](tech-debt-remediation/REM-31-room-sync-shadow.md) | ~~Room migration (SyncShadow + SyncLog)~~ ✅ | **3.2** | 2 |
| [REM-15](tech-debt-remediation/REM-15-coroutines.md) | Introduce coroutines | **3.2** | 3 |
| [REM-16](tech-debt-remediation/REM-16-java-to-kotlin.md) | Java→Kotlin conversion (9/11 done) | **3.0** | 3 |
| [REM-17](tech-debt-remediation/REM-17-kotlin-dsl-build.md) | ~~Kotlin DSL build migration~~ ✅ | **3.0** | 3 |
| [REM-20](tech-debt-remediation/REM-20-ambilwarna-replacement.md) | ~~Replace AmbilWarna~~ ✅ | **3.0** | 3 |
| [REM-19](tech-debt-remediation/REM-19-prdownloader-replacement.md) | ~~Replace PRDownloader~~ ✅ | **2.8** | 3 |
| [REM-21](tech-debt-remediation/REM-21-song-json-storage.md) | Song storage migration | **2.8** | 4 |
| [REM-22](tech-debt-remediation/REM-22-jetpack-compose.md) | Jetpack Compose adoption (kicked off) | **2.6** | 4 |

## Suggested Execution Order

**Sprint 1 (1 week):** ~~REM-01~~✅, ~~REM-02~~✅, ~~REM-04~~✅, ~~REM-05~~✅ — quick safety fixes (all done)
**Sprint 2 (1 week):** ~~REM-03~~✅ — LocalBroadcastManager removal (done)
**Sprint 3 (2 weeks):** ~~REM-07~~✅, ~~REM-06~~✅, ~~REM-08~~✅ — IsiActivity decomposition (all done)
**Sprint 4 (1 week):** ~~REM-12~~✅, ~~REM-14~~✅ — deprecated library replacements (done)
**Sprint 5 (2 weeks):** ~~REM-10~~✅, ~~REM-11~~✅, ~~REM-27~~✅, ~~REM-28~~✅, ~~REM-29~~✅, ~~REM-30~~✅, ~~REM-31~~✅ — Room migration for every InternalDb table (Marker / Version / Devotion / PerVersion / ProgressMark / ReadingPlan / SyncShadow / SyncLog all done; legacy `InternalDbHelper` is now only a rollback safety net)
**Sprint 6 (2 weeks):** REM-09, ~~REM-18a-f~~✅ — ViewModel + test coverage (REM-18a/b/c/d/e/f done)
**Ongoing:** REM-15, REM-16, ~~REM-17~~✅ — modernization work mixed into feature sprints (REM-17 done)
