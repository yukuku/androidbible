import com.android.build.gradle.api.ApkVariantOutput
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.time.Instant
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.firebase.crashlytics.gradle)
    alias(libs.plugins.google.services)
}

/**
 * Mirrors the contents of {@code text_raw/} from a proprietary overlay directory
 * into {@code <outputDir>/internal/}, so that the result can be wired into a
 * product flavor's assets via AGP's {@code addGeneratedSourceDirectory}. The
 * Sync semantics ensure that removed files in the source disappear from the
 * output, and the typed {@code DirectoryProperty} output lets every downstream
 * AGP task (mergeAssets, lint vital, etc.) automatically depend on this task.
 */
abstract class CopyProprietaryAssetsTask @Inject constructor(
    private val fs: FileSystemOperations,
) : DefaultTask() {
    // Optional so Gradle's property validation doesn't preempt our friendly
    // "ALKITAB_PROPRIETARY_DIR is not set" error in doFirst when the env var
    // is missing. The doFirst check runs before copyAssets() executes, so we
    // never reach copyAssets() with an unset sourceDir.
    @get:Optional
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copyAssets() {
        fs.sync {
            from(sourceDir)
            into(outputDir.dir("internal"))
        }
    }
}

// Validates that the given google-services.json file contains a non-placeholder
// Firebase API key for `applicationId`. Returns null if usable, or a human-
// readable reason string if not. Called at task execution time so it sees the
// file produced by copyProprietaryGoogleServices<Flavor> (when applicable).
@Suppress("UNCHECKED_CAST")
fun firebaseApiKeyProblem(jsonFile: File?, applicationId: String): String? {
    if (jsonFile == null || !jsonFile.isFile) {
        return "google-services.json not found at $jsonFile"
    }
    val parsed = groovy.json.JsonSlurper().parse(jsonFile) as Map<String, Any>
    val clients = parsed["client"] as? List<Map<String, Any>> ?: emptyList()
    val client = clients.find { c ->
        val clientInfo = c["client_info"] as? Map<String, Any>
        val androidInfo = clientInfo?.get("android_client_info") as? Map<String, Any>
        androidInfo?.get("package_name") == applicationId
    } ?: return "no client entry for package '$applicationId' in $jsonFile"
    val apiKeys = client["api_key"] as? List<Map<String, Any>> ?: emptyList()
    val apiKey = apiKeys.mapNotNull { it["current_key"] as? String }.firstOrNull()
    return if (apiKey == null || apiKey.trim().isEmpty() || apiKey.trim().matches(Regex("0+"))) {
        "Firebase API key for '$applicationId' is missing or a placeholder in $jsonFile"
    } else null
}

// Last git commit hash, baked into BuildConfig.LAST_COMMIT_HASH and the APK filename.
// Falls back to "0000000" outside of a git checkout (e.g. some CI source archives).
val gitCommitHash: String = try {
    val hash = providers.exec {
        commandLine("git", "log", "-1", "--format=format:%h")
    }.standardOutput.asText.get().trim()
    hash.ifEmpty { "0000000" }
} catch (_: Exception) {
    "0000000"
}

// Version code: (2_000_000 + minutes since 2026-01-01 UTC) * 10
val buildVersionCode: Int = run {
    val epoch = Instant.parse("2026-01-01T00:00:00Z").epochSecond
    val minutesSinceEpoch = (Instant.now().epochSecond - epoch) / 60
    ((2_000_000 + minutesSinceEpoch) * 10).toInt()
}

// Map of production (non-plain) flavor name -> overlay subdirectory name under
// $ALKITAB_PROPRIETARY_DIR/overlay/. The overlay subdirectory matches the
// applicationId of the corresponding flavor.
val proprietaryFlavors = mapOf(
    "yuku_alkitab" to "yuku.alkitab",
    "yuku_quick_bible" to "yuku.alkitab.kjv",
    "sabda_alkitab" to "org.sabda.alkitab",
)
val proprietaryDir: String? = providers.environmentVariable("ALKITAB_PROPRIETARY_DIR").orNull

// Server endpoints inlined here (and in AlkitabFeedback) — these are app-specific
// constants previously held in root build.gradle's ext block.
val serverHost = "https://api.alkitab.app"
val ribkaFunctionsHost = "https://us-central1-pulau-ribka.cloudfunctions.net/"
val ribkaFunctionsHostDebug = "http://10.0.3.2:5001/pulau-ribka/us-central1/"

android {
    signingConfigs {
        create("release") {
            keyAlias = System.getenv("SIGN_ALIAS")
            keyPassword = System.getenv("SIGN_PASSWORD")
            storeFile = file(System.getenv("SIGN_KEYSTORE") ?: "/dev/null")
            storePassword = System.getenv("SIGN_PASSWORD")
        }
    }
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        applicationId = "yuku.alkitab.debug"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = buildVersionCode
        versionName = "5.0.0-b0"
        multiDexEnabled = true
        // Keep this synced with integrate_translations.sh! Also update pref_language.xml and ConfigurationWrapper!
        resourceConfigurations += listOf("af", "bg", "ceb", "cs", "da", "de", "el", "es", "fr", "hu", "in", "it", "ja", "ko", "lv", "ms", "my", "nl", "pl", "pt-rBR", "pt", "ro", "ru", "th", "tl", "tr", "uk", "vi", "zh-rCN", "zh-rTW")
        buildConfigField("String", "SERVER_HOST", "\"$serverHost\"")
        buildConfigField("String", "RIBKA_FUNCTIONS_HOST", "\"$ribkaFunctionsHost\"")
        buildConfigField("String", "LAST_COMMIT_HASH", "\"$gitCommitHash\"")

        // Audio catalog identifier for the internal Bible version. The
        // bundled internal version reports `MVersion.getVersionId() == "internal"`,
        // which never matches a `preset/*` catalog entry; this field tells
        // AudioCatalogRepository what catalog row to use when the user is
        // reading the internal version. Empty string means "internal has no
        // audio for this flavor". Each productFlavor overrides this below.
        buildConfigField("String", "INTERNAL_VERSION_AUDIO_ID", "\"\"")
    }

    // Room schema export — JSON snapshots of each @Database version land here.
    // Checked into git so reviewers can see schema diffs. See REM-11 design doc.
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }
    sourceSets {
        // Room schema JSONs are exposed as assets so MigrationTestHelper can
        // load them at test time. AGP doesn't propagate test-only asset
        // srcDirs into the unit-test `apk_for_local_test` archive that
        // Robolectric reads, so the schemas have to live in the main source
        // set even though they're only used by tests. Cost: ~3 KB per schema
        // version shipped in the production APK — acceptable given the
        // alternative (an instrumented-test setup that needs an emulator in
        // CI).
        // See Alkitab/src/test/java/.../room/AppDatabaseMigrationTest.kt.
        getByName("main").assets.srcDir("$projectDir/schemas")
    }
    buildTypes {
        debug {
            buildConfigField("String", "RIBKA_FUNCTIONS_HOST", "\"$ribkaFunctionsHostDebug\"")
            buildConfigField("boolean", "SKIP_FCM_REGISTRATION", "true")
        }
        release {
            buildConfigField("boolean", "SKIP_FCM_REGISTRATION", "false")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    lint {
        abortOnError = false
    }

    flavorDimensions += "playStoreApplicationId"

    productFlavors {
        // Use this for development. The plain build's bundled `ddd_*` files are
        // Indonesian-language placeholders, so for dev convenience we map the
        // internal version to TB audio so the bottom sheet has something to play.
        create("plain") {
            buildConfigField("String", "INTERNAL_VERSION_AUDIO_ID", "\"preset/in-tb\"")
        }

        // The following flavors are for release
        create("yuku_alkitab") {
            applicationId = "yuku.alkitab"
            buildConfigField("String", "INTERNAL_VERSION_AUDIO_ID", "\"preset/in-tb\"")
        }
        create("yuku_quick_bible") {
            applicationId = "yuku.alkitab.kjv"
            buildConfigField("String", "INTERNAL_VERSION_AUDIO_ID", "\"preset/en-kjv\"")
        }
        create("sabda_alkitab") {
            applicationId = "org.sabda.alkitab"
            buildConfigField("String", "INTERNAL_VERSION_AUDIO_ID", "\"preset/in-tb\"")
        }
    }

    namespace = "yuku.alkitab.debug"

    buildFeatures {
        buildConfig = true
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // The REM-10/REM-11 migration load test in MarkerDataMigrationLoadTest
            // holds 50,000 × 2 KB marker captions live during the bulk Room insert
            // (≈100 MB just for caption strings, plus per-entity overhead).
            // Default 512 MB unit-test heap OOMs; 2 GB leaves comfortable headroom.
            all { it.maxHeapSize = "2g" }
        }
    }

    // We have to specify the ndk version twice, in here and in the Snappy library
    // https://issuetracker.google.com/issues/353554169
    ndkVersion = libs.versions.ndk.get()
}

// Register one CopyProprietaryAssetsTask per production flavor. The task
// materialises proprietary internal/ Bible data into
// build/generated/proprietaryAssets/<flavor>/internal/ and is wired into the
// flavor's assets via androidComponents.onVariants below.
proprietaryFlavors.forEach { (flavorName, overlaySubdir) ->
    tasks.register<CopyProprietaryAssetsTask>("copyProprietaryAssets${flavorName.replaceFirstChar { it.uppercaseChar() }}") {
        description = "Copies proprietary internal/ Bible assets for the $flavorName flavor."
        outputDir.set(layout.buildDirectory.dir("generated/proprietaryAssets/$flavorName"))
        if (proprietaryDir != null) {
            sourceDir.set(file("$proprietaryDir/overlay/$overlaySubdir/text_raw"))
        }
        doFirst {
            if (proprietaryDir == null) {
                throw GradleException("ALKITAB_PROPRIETARY_DIR is not set; required to build the '$flavorName' flavor.")
            }
            val src = file("$proprietaryDir/overlay/$overlaySubdir/text_raw")
            if (!src.isDirectory) {
                throw GradleException("Proprietary overlay directory not found: $src")
            }
        }
    }
}

// Register one Copy task per production flavor that drops the proprietary
// google-services.json into the flavor's source set, where the
// com.google.gms.google-services plugin will pick it up via its standard
// source-set lookup. The destination paths (Alkitab/src/<flavor>/google-services.json)
// are gitignored so the worktree's tracked content never changes — they're
// effectively build artifacts that just happen to live under src/.
//
// The plain flavor falls back to Alkitab/google-services.json (the placeholder
// committed to the repo) since we never write to src/plain/google-services.json.
proprietaryFlavors.keys.forEach { flavorName ->
    tasks.register<Copy>("copyProprietaryGoogleServices${flavorName.replaceFirstChar { it.uppercaseChar() }}") {
        description = "Copies proprietary google-services.json into the $flavorName flavor's source set."
        if (proprietaryDir != null) {
            from(file("$proprietaryDir/google-services.json"))
        }
        into(file("src/$flavorName"))
        doFirst {
            if (proprietaryDir == null) {
                throw GradleException("ALKITAB_PROPRIETARY_DIR is not set; required to build the '$flavorName' flavor.")
            }
            val src = file("$proprietaryDir/google-services.json")
            if (!src.isFile) {
                throw GradleException("Proprietary google-services.json not found: $src")
            }
        }
    }
}

// Wire each production flavor's generated proprietary assets into AGP. Using
// addGeneratedSourceDirectory (rather than sourceSets.assets.srcDirs +=) lets
// AGP automatically declare the dependency on the copy task for every consumer
// (mergeAssets, lint vital, etc.).
androidComponents {
    proprietaryFlavors.keys.forEach { flavorName ->
        onVariants(selector().withFlavor("playStoreApplicationId" to flavorName)) { variant ->
            val copyTask = tasks.named<CopyProprietaryAssetsTask>(
                "copyProprietaryAssets${flavorName.replaceFirstChar { it.uppercaseChar() }}"
            )
            variant.sources.assets?.addGeneratedSourceDirectory(copyTask) { it.outputDir }
        }
    }
}

android.applicationVariants.all {
    val variant = this
    // Custom APK output filename, mirroring the legacy ybuild.sh naming:
    //   Alkitab-<versionCode>-<versionName>-<gitHash>-<applicationId>-<BUILD_DIST>.apk
    // BUILD_DIST defaults to "dev" so local builds get a recognisable name.
    val buildDist = providers.environmentVariable("BUILD_DIST").getOrElse("dev")
    outputs.all {
        (this as? ApkVariantOutput)?.outputFileName =
            "Alkitab-${variant.versionCode}-${variant.versionName}-$gitCommitHash-${variant.applicationId}-$buildDist.apk"
    }

    // Wire the proprietary google-services.json copy task into the GMS plugin
    // task that consumes it, for each production flavor variant. Using
    // tasks.matching { }.configureEach { } rather than tasks.named() so the
    // build doesn't break if the GMS plugin is ever removed or renames its task.
    if (proprietaryFlavors.containsKey(variant.flavorName)) {
        val copyTaskName = "copyProprietaryGoogleServices${variant.flavorName.replaceFirstChar { it.uppercaseChar() }}"
        val gmsTaskName = "process${variant.name.replaceFirstChar { it.uppercaseChar() }}GoogleServices"
        tasks.matching { it.name == gmsTaskName }.configureEach {
            dependsOn(copyTaskName)
        }
    }

    // Validate Firebase config for non-plain release builds. Reads from the
    // flavor source set (populated by copyProprietaryGoogleServices<Flavor>),
    // falling back to the root google-services.json the GMS plugin would
    // otherwise use.
    if (!variant.buildType.isDebuggable && variant.flavorName != "plain") {
        val applicationId = variant.applicationId
        val flavorName = variant.flavorName
        val validateTask = tasks.register("validate${variant.name.replaceFirstChar { it.uppercaseChar() }}FirebaseConfig") {
            if (proprietaryFlavors.containsKey(flavorName)) {
                dependsOn("copyProprietaryGoogleServices${flavorName.replaceFirstChar { it.uppercaseChar() }}")
            }
            doLast {
                val candidates = listOf(
                    file("src/$flavorName/google-services.json"),
                    file("google-services.json"),
                )
                val jsonFile = candidates.find { it.isFile }
                val reason = firebaseApiKeyProblem(jsonFile, applicationId)
                if (reason != null) {
                    throw GradleException(
                        "Release build '${variant.name}' has unusable Firebase config: $reason. " +
                            "For production flavors, set ALKITAB_PROPRIETARY_DIR to a directory containing " +
                            "a real google-services.json with a client entry for '$applicationId'."
                    )
                }
            }
        }

        tasks.named("pre${variant.name.replaceFirstChar { it.uppercaseChar() }}Build").configure {
            dependsOn(validateTask)
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":AlkitabFeedback"))
    implementation(project(":AlkitabIntegration"))
    implementation(project(":AlkitabIo"))
    implementation(project(":AlkitabModel"))
    implementation(project(":AlkitabYes2"))
    implementation(project(":BiblePlus"))
    implementation(project(":KpriModel"))
    implementation(project(":BintexReader"))
    implementation(project(":BintexWriter"))
    implementation(project(":Afw"))
    implementation(project(":FlowLayout"))
    implementation(project(":ImportedDesktopVerseUtil"))

    // Compose — single BOM-managed Compose surface (audio-bible bottom sheet + GotoActivity).
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // AndroidX
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.multidex)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.work.runtime.ktx)

    // Room — see docs/superpowers/specs/2026-05-13-rem-11-room-version-table-design.md
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.androidx.room.testing)

    // Google
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.session)
    implementation(libs.google.material)
    implementation(libs.gson)
    implementation(libs.fastscroll)

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    debugImplementation(libs.leakcanary.android)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.crashlytics)

    // Misc
    implementation(libs.okhttp)
    implementation(libs.coil)
    implementation(libs.kotlinx.serialization.json)

    // UI
    implementation(libs.fancyShowcaseView)
}
