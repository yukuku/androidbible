# REM-24: Refactor S.kt Service Locator — Steps 24a–24d complete 2026-05-12

**Addresses:** TD-15
**Module:** Cross-cutting (50 files)
**BRICE:** B=4 R=2 I=2 C=3 E=5 → **3.2**
**Phase:** 2 — Architecture Improvements

**Status:** Steps 24a (interface extraction), 24b (UI dialog removal), 24c (thread-safety fix), and 24d (AppServices container + caller migration) all complete as of 2026-05-12. ~216 direct `S.xxx` references across 44 files migrated to `App.services.*`. The `S` adapter properties (`S.storage`, `S.versions`, `S.uiDimensions`) and the legacy `@JvmStatic` accessors are retained so any new caller written against `S.foo` keeps compiling; new code should prefer `App.services`. 24e (Hilt) is still optional/deferred.

**24d caller-migration follow-up shipped 2026-05-12:**
- `App.services` is now initialized eagerly at the static field declaration in `App.java` rather than only inside `staticInit()`. This keeps `App.services.*` non-null for callers that exercise migrated code in tests that deliberately bypass `App.onCreate()` / `App.staticInit()` (e.g. `ProviderTest`, `VerseRendererTest`'s setup). The adapter objects on `S` only touch context/preferences when their methods are invoked, so eager init is safe before `App.context` is set.
- `AppServices`'s `storage` / `versions` / `uiDimensions` fields are annotated `@JvmField` so Java callers can write `App.services.versions.activeVersion()` (matching Kotlin syntax) instead of `App.services.getVersions().activeVersion()`.
- One deliberately un-migrated call site remains: the `S.db.listAllVersions().isEmpty()` check inside `IsiActivity.openVersionsDialog`. The rest of that method already uses `App.services.versions`; the single `S.db` line is left for the next person touching that method to migrate, to keep diffs cohesive.

**What shipped on 2026-05-12:**
- New `yuku.alkitab.base.services` package with `StorageProvider`, `VersionManager`, `UiDimensionsProvider`, and `AppServices` (see `Alkitab/src/main/java/yuku/alkitab/base/services/`).
- `S` exposes three adapter properties (`S.storage`, `S.versions`, `S.uiDimensions`) that implement the new interfaces. `@JvmStatic` is retained on the original `S.db`, `S.applied()`, `S.activeVersion()` etc. so existing Java call sites keep compiling — these are the migration target for the rest of 24d.
- `openVersionsDialog` / `openVersionsDialogWithNone` moved off `S` into `yuku.alkitab.base.util.VersionDialogHelper`. Callers in `IsiActivity.kt` and `SearchActivity.kt` were updated.
- `recalculateAppliedValuesBasedOnPreferences()` renamed to `recalculate()` to match the interface (single call site in `IsiActivity.kt`).
- Active-version state collapsed from three mutable nullable fields to a single `@Volatile var state: ActiveVersionState` data-class reference so all readers see a consistent `(mVersion, version, versionId)` triple.
- `App.staticInit()` now constructs `App.services = new AppServices(S.storage, S.versions, S.uiDimensions)` before any other init step.
- `AppServicesTest` (4 cases) demonstrates the new interfaces are fake-implementable in pure JUnit — no Robolectric or Android context required.

**Current state:** `S.kt` (313 lines) is a Kotlin `object` singleton mixing database access (`db`, `songDb`), active version state, and UI dimensions (`CalculatedDimensions`). Imported by 50 files with 161+ call sites. Untestable without a full Android environment.

**Recommended approach: Incremental interface extraction, then manual DI**

Hilt/Dagger adds significant complexity (annotation processing, code generation) for a codebase that doesn't yet use any DI. A lighter approach is to extract interfaces first, then provide them via a simple app-level container.

**Steps:**

**Step 24a: Extract interfaces (no DI yet, no callers change)**
1. Create `StorageProvider` interface:
   ```kotlin
   interface StorageProvider {
       val db: InternalDb
       val songDb: SongDb
   }
   ```
2. Create `VersionManager` interface:
   ```kotlin
   interface VersionManager {
       fun activeVersion(): Version
       fun activeMVersion(): MVersion
       fun activeVersionId(): String
       fun setActiveVersion(mv: MVersion)
       fun getVersionFromVersionId(versionId: String?): MVersion?
       fun getAvailableVersions(): List<MVersion>
       fun getMVersionInternal(): MVersionInternal
   }
   ```
3. Create `UiDimensionsProvider` interface:
   ```kotlin
   interface UiDimensionsProvider {
       fun applied(): CalculatedDimensions
       fun recalculate()
   }
   ```
4. Make `S` implement all three interfaces, delegating to its existing internal holders. This is a no-op refactor — all existing `S.db` / `S.applied()` / `S.activeVersion()` calls keep working.

**Step 24b: Move UI dialogs out of S**
1. Move `openVersionsDialog()` and `openVersionsDialogWithNone()` (lines 271-320) into a standalone `VersionDialogHelper` object or extension function. These are UI operations that don't belong in a service locator.

**Step 24c: Fix thread safety**
1. Make `activeVersion()` / `activeMVersion()` / `activeVersionId()` getters `@Synchronized` to match the setter
2. Or better: replace the three mutable fields with a single `AtomicReference<ActiveVersionState>` data class to ensure atomic reads

**Step 24d: Introduce app-level service container**
1. Create `AppServices` class initialized in `App.onCreate()`:
   ```kotlin
   class AppServices(
       val storage: StorageProvider,
       val versions: VersionManager,
       val uiDimensions: UiDimensionsProvider,
   )
   ```
2. Initialize in `App`: `val services = AppServices(S, S, S)` — initially delegates back to `S`
3. New code uses `App.services.storage.db` instead of `S.db`
4. Gradually migrate existing callers (50 files, can be done file-by-file)
5. In tests, provide fake implementations of the interfaces

**Step 24e: (Optional, later) Migrate to Hilt**
If the project adopts Hilt for other reasons (e.g., ViewModel injection in [REM-09](REM-09-isiactivity-viewmodel.md)), the interfaces from Step 24a become `@Provides` targets naturally.

**Difficulty:** Medium-Hard (2-3 days for steps 24a-24c, then ongoing migration for 24d). Step 24a is the critical enabler — once interfaces exist, the rest is incremental.

---

[← Back to remediation index](../tech-debt-remediation.md)
