# ~~REM-03: Replace LocalBroadcastManager~~ ✅ COMPLETED (2026-04-22)

**Addresses:** TD-03 (LocalBroadcastManager)
**Module:** Cross-cutting
**BRICE:** B=3 R=3 I=3 C=4 E=5 → **3.8**
**Phase:** 1 — Quick Wins & Safety Fixes

**Outcome:** All 24 files that used `App.getLbm()` now talk through a single Kotlin event-bus object at `yuku.alkitab.base.events.AppEvents`. Each former `ACTION_*` broadcast became a named `MutableSharedFlow` (most `<Unit>`, one `<Boolean>` for the version-list refreshing spinner, and one `<DevotionDownloadedEvent>` data-class-typed bus carrying the kind name + `yyyyMMdd` date). Buses are configured with `extraBufferCapacity = 16, onBufferOverflow = DROP_OLDEST` — `tryEmit` is guaranteed to succeed regardless of buffer pressure, and when bursts exceed the buffer the oldest queued "reload" signal is dropped rather than the newest (which is what a collector catching up actually needs). Preserves LBM's fire-and-forget, in-process, no-replay semantics.

**What was done:**

1. **Senders** — every `App.getLbm().sendBroadcast(new Intent(ACTION_*))` replaced with a `@JvmStatic` emit helper on `AppEvents` (e.g. `AppEvents.emitAttributeMapChanged()`). Touched: `IsiActivity`, `SyncAdapter` (9 call sites), `MarkerListActivity` (4 call sites), `SecretSyncDebugActivity`, `VersionListFragment` (5 call sites), `VersionsActivity`, `VersionConfigUpdaterService`, `VersionDownloadCompleteReceiver`, `DownloadMapper`, `DevotionDownloader`, `ProgressMarkRenameDialog`, `DataTransferFragment`, `DisplayFragment`, `CurrentReading`.

2. **Receivers (Kotlin activities/fragments)** — replaced `BroadcastReceiver` + `registerReceiver`/`unregisterReceiver` with `lifecycleScope.launch { AppEvents.foo.collect { ... } }` in `IsiActivity`, `MarkerListActivity`, `VersionListFragment`. The associated `onDestroy` overrides (only doing `unregisterReceiver`) became no-ops and were deleted.

3. **Receivers (Java activities/fragments)** — added two Java-friendly helpers on `AppEvents`: `observe(LifecycleOwner, Flow<*>, Runnable)` collects until `DESTROYED`, and `observeWhileStarted` (+ typed `observeWhileStartedWithValue`) uses `repeatOnLifecycle(STARTED)` for onStart/onStop-scoped flows. Migrated `MarkersActivity`, `ReadingPlanActivity`, `SyncSettingsActivity.SyncSettingsFragment`, `DailyVerseAppWidgetConfigurationActivity` (lifecycle = DESTROYED) and `DevotionActivity` (lifecycle = STARTED, typed value consumer).

4. **Receivers (Java custom views)** — `LabeledSplitHandleButton.init()` and `LeftDrawer.Text.onFinishInflate` call a third helper, `AppEvents.observeOnView(view, flow, runnable)`. The helper installs an `OnAttachStateChangeListener`: collection starts on attach (launching a fresh `MainScope` job each time) and is cancelled on detach, so a view that's inflated but never attached does not leak a live collector, and a re-attached view automatically resubscribes. Contract: call once per view instance — calling from `onAttachedToWindow` would accumulate a listener per attach cycle.

5. **Stale constants removed** — `IsiActivity.ACTION_ATTRIBUTE_MAP_CHANGED` / `ACTION_ACTIVE_VERSION_CHANGED` / `ACTION_NIGHT_MODE_CHANGED` / `ACTION_NEEDS_RESTART`, `MarkersActivity.ACTION_RELOAD`, `MarkerListActivity.ACTION_RELOAD`, `ReadingPlanActivity.ACTION_READING_PLAN_PROGRESS_CHANGED`, `SyncSettingsActivity.ACTION_RELOAD`, `CurrentReading.ACTION_CURRENT_READING_CHANGED`, `DevotionDownloader.ACTION_DOWNLOADED`, `VersionListFragment.ACTION_RELOAD` / `ACTION_UPDATE_REFRESHING_STATUS` / `EXTRA_refreshing`, plus the private `DevotionDownloader.broadcastDownloaded` helper.

6. **Dependency removed** — `androidx-localbroadcastmanager` deleted from `gradle/libs.versions.toml` and `Alkitab/build.gradle.kts`. `App.getLbm()` helper and the `LocalBroadcastManager` import removed from `App.java`.

Coroutines and `lifecycleScope` were already on the classpath transitively through `androidx.fragment:fragment-ktx:1.8.9` (pulling `kotlinx-coroutines-android:1.9.0` and `lifecycle-runtime-ktx:2.7.0`); no new dependency was needed.

**Verified:** `./gradlew :Alkitab:assemblePlainDebug` plus `testPlainDebugUnitTest testPlainReleaseUnitTest` — all 313 unit tests pass.

---

[← Back to remediation index](../tech-debt-remediation.md)
