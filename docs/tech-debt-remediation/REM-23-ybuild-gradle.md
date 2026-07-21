# ~~REM-23: Port ybuild.sh to Gradle~~ ✅ COMPLETED (2026-04-16)

**Addresses:** TD-14
**Module:** Build
**BRICE:** B=4 R=3 I=3 C=4 E=5 → **3.8**
**Phase:** 2 — Architecture Improvements

**Outcome:** `ybuild.sh` deleted. Production builds are now `./gradlew assemble<Flavor>Release`, working on any platform Gradle supports. The placeholder `ddd_*` Bible files moved from `Alkitab/src/main/assets/internal/` to `Alkitab/src/plain/assets/internal/` so production flavors don't inherit them. A typed `CopyProprietaryAssetsTask` per production flavor copies `$ALKITAB_PROPRIETARY_DIR/overlay/<applicationId>/text_raw/*` into `Alkitab/build/generated/proprietaryAssets/<flavor>/internal/`, wired into AGP via `androidComponents { onVariants { ... addGeneratedSourceDirectory(...) } }` so every consumer (mergeAssets, lint vital, etc.) automatically depends on it. The git commit hash is now `BuildConfig.LAST_COMMIT_HASH` (the `R.string.last_commit_hash` resource was removed). APKs are still named `Alkitab-{versionCode}-{versionName}-{commitHash}-{applicationId}-{BUILD_DIST}.apk` (with `BUILD_DIST` defaulting to `dev`). All four flavors (plain debug, yuku_alkitab, yuku_quick_bible, sabda_alkitab) verified building end-to-end.

---

## Original analysis & steps (for reference)

Production release builds required running `ybuild.sh`, a 168-line macOS-only bash script that created a RAM disk, copied proprietary assets from an external directory, stamped the git commit hash, ran Gradle, and renamed the output APK. This could not run on Linux CI and prevented building with a simple `./gradlew assembleYuku_alkitabRelease`.

Gradle already handled signing (`signingConfigs.release`) and product flavors. What was missing were 4 operations that could all be expressed as Gradle tasks.

**Step 23a: Proprietary asset injection via Gradle**
1. Add an `ALKITAB_PROPRIETARY_DIR` environment variable check in `Alkitab/build.gradle` — only required for non-`plain` flavors
2. For each production flavor (`yuku_alkitab`, `yuku_quick_bible`, `sabda_alkitab`), define a mapping from flavor to its `BUILD_PACKAGE_NAME` overlay subdirectory (e.g., `yuku_alkitab` → `yuku`)
3. Register a `preBuild`-dependent task (e.g., `copyProprietaryAssets${flavorName}`) that:
   - Deletes `Alkitab/src/main/assets/internal/`
   - Copies files from `$ALKITAB_PROPRIETARY_DIR/overlay/$BUILD_PACKAGE_NAME/text_raw/*` into `Alkitab/src/main/assets/internal/`
4. Alternatively, use `sourceSets` to point each production flavor's `assets.srcDirs` to the proprietary directory directly, avoiding the copy:
   ```groovy
   yuku_alkitab {
       applicationId 'yuku.alkitab'
       assets.srcDirs = ['src/main/assets_without_internal', "$proprietaryDir/overlay/yuku"]
   }
   ```
   This would require restructuring `src/main/assets` so that `internal/` is in its own source set.

**Step 23b: Git commit hash injection via Gradle**
1. Add a task that reads the git commit hash:
   ```groovy
   def gitHash = providers.exec {
       commandLine 'git', 'log', '-1', '--format=format:%h'
   }.standardOutput.asText.get().trim()
   ```
2. Option A: Generate `last_commit.xml` as a build output (preferred — avoids modifying source):
   ```groovy
   android.applicationVariants.all { variant ->
       def task = tasks.register("generateLastCommit${variant.name.capitalize()}") {
           def outputDir = layout.buildDirectory.dir("generated/res/lastCommit/${variant.name}")
           outputs.dir(outputDir)
           doLast {
               def dir = outputDir.get().asFile
               new File(dir, "values/last_commit.xml").with {
                   parentFile.mkdirs()
                   text = """<?xml version="1.0" encoding="utf-8"?>
   <resources><string name="last_commit_hash">${gitHash}</string></resources>"""
               }
           }
       }
       variant.registerGeneratedResFolders(project.files(task.map { it.outputs.files.singleFile }))
   }
   ```
3. Option B (simpler): Use `buildConfigField` instead of a resource:
   ```groovy
   defaultConfig {
       buildConfigField 'String', 'LAST_COMMIT_HASH', "\"${gitHash}\""
   }
   ```
   Then update code that reads `R.string.last_commit_hash` to read `BuildConfig.LAST_COMMIT_HASH`. Grep for `last_commit_hash` to find all readers.

**Step 23c: Custom APK naming via Gradle**
1. Use the existing `applicationVariants.all` block in the Alkitab build script to set the output filename:
   ```groovy
   android.applicationVariants.all { variant ->
       variant.outputs.all { output ->
           def flavor = variant.flavorName
           def buildType = variant.buildType.name
           def dist = System.getenv("BUILD_DIST") ?: "dev"
           def pkgName = System.getenv("BUILD_PACKAGE_NAME") ?: "plain"
           outputFileName = "Alkitab-${variant.versionCode}-${variant.versionName}-${gitHash}-${pkgName}-${dist}.apk"
       }
   }
   ```

**Step 23d: Remove RAM disk dependency**
1. The RAM disk served two purposes: build isolation and speed. Neither is necessary:
   - **Isolation:** Gradle's `build/` directory already isolates outputs. `./gradlew clean` handles cleanup.
   - **Speed:** Modern SSDs make the speed benefit negligible. CI runners typically have fast storage.
2. No Gradle changes needed — just stop using the RAM disk.

**Step 23e: Validate environment and retire ybuild.sh**
1. Add a validation task that checks required env vars for production flavors:
   ```groovy
   tasks.register("validateProductionEnv") {
       doFirst {
           if (System.getenv("ALKITAB_PROPRIETARY_DIR") == null) {
               throw new GradleException("ALKITAB_PROPRIETARY_DIR not set")
           }
           // ... check SIGN_KEYSTORE, etc.
       }
   }
   // Wire it: only for non-plain release builds
   ```
2. After all steps are verified, delete `ybuild.sh` and update documentation
3. The new build command becomes:
   ```bash
   ALKITAB_PROPRIETARY_DIR=/path/to/proprietary \
   BUILD_PACKAGE_NAME=yuku \
   BUILD_DIST=playstore \
   SIGN_KEYSTORE=/path/to/keystore \
   SIGN_ALIAS=mykey \
   SIGN_PASSWORD=secret \
   ./gradlew assembleYuku_alkitabRelease
   ```

**Difficulty:** Medium (1-2 days). Step 23a (asset injection) is the trickiest part — need to decide between copy-on-build vs. sourceSets approach. Steps 23b-23d are straightforward Gradle configuration. Low risk since existing `ybuild.sh` can remain as fallback during transition.

---

[← Back to remediation index](../tech-debt-remediation.md)
