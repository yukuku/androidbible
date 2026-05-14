# ~~REM-17: Migrate Build to Kotlin DSL & Version Catalogs~~ ✅ COMPLETED (2026-04-20)

**Addresses:** TD-12 (build system)
**Module:** Build
**BRICE:** B=2 R=1 I=3 C=4 E=5 → **3.0**
**Phase:** 3 — Modernization

**Outcome:**
- Created `gradle/libs.versions.toml` centralising all 30+ dependency versions, 20+ library coordinates, and 8 plugin IDs. Firebase BOM dependencies declared without version (BOM-managed). SDK integer versions (`compileSdk`, `minSdk`, `targetSdk`) stored as strings and accessed via `.get().toInt()` in build files.
- `settings.gradle` → `settings.gradle.kts`: added `pluginManagement` and `dependencyResolutionManagement` (with `FAIL_ON_PROJECT_REPOS`) so all repository declarations are centralised in one place. `jitpack.io` (needed for `FancyShowCaseView`) declared there. Foojay toolchain resolver kept inline (version catalog not yet available at settings `plugins {}` eval time).
- Root `build.gradle` → `build.gradle.kts`: replaced `buildscript` + `allprojects` with a lean `plugins { ... apply false }` block. The `ext {}` block is gone — all versions live in the catalog.
- `Alkitab/build.gradle` → `Alkitab/build.gradle.kts`: `CopyProprietaryAssetsTask` converted to Kotlin abstract class with `@Inject constructor(private val fs: FileSystemOperations)`. `firebaseApiKeyProblem` converted to a top-level function with `@Suppress("UNCHECKED_CAST")`. `String.capitalize()` (deprecated in Kotlin 2.x) replaced with `replaceFirstChar { it.uppercaseChar() }`. All `$androidxActivityVersion` ext references replaced with `libs.androidx.activity.ktx` catalog accessors. Server-host constants (`SERVER_HOST`, `RIBKA_FUNCTIONS_HOST`, `RIBKA_FUNCTIONS_HOST_DEBUG`) inlined as `val` in this file and duplicated in `AlkitabFeedback` (only two consumers; no ext lookup needed).
- All 15 library module `build.gradle` files converted to Kotlin DSL. `apply plugin:` replaced with `alias(libs.plugins.*)`. `compileSdkVersion`/`minSdkVersion`/`targetSdkVersion` methods replaced with `compileSdk`/`minSdk`/`targetSdk` property assignments. `minifyEnabled` → `isMinifyEnabled`, `shrinkResources` → `isShrinkResources`. Stale `repositories {}` blocks in `AlkitabYes2` and `AlkitabFeedback` removed (centralised in settings). `kotlin-android` / `kotlin-parcelize` plugin IDs replaced with their full `org.jetbrains.kotlin.*` IDs.
- `apply plugin: 'com.google.gms.google-services'` (legacy bottom-of-file pattern) moved to the `plugins {}` block at the top of `Alkitab/build.gradle.kts`.

**Difficulty:** Medium (1-2 days). Mostly mechanical but Groovy-specific constructs need manual translation.

---

[← Back to remediation index](../tech-debt-remediation.md)
