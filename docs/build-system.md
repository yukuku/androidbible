# Build System

## Gradle Configuration

- **AGP**: 8.13.0
- **Kotlin**: 2.2.0
- **Compile SDK**: 36, **Min SDK**: 26, **Target SDK**: 35
- **JVM Toolchain**: 17 (all modules)
- **NDK**: 28.2.13676358 (required for Snappy native code)
- **Build files**: Groovy DSL (`build.gradle`, not `.kts`)

## Product Flavors

Flavor dimension: `playStoreApplicationId`

| Flavor | Package | Purpose |
|--------|---------|---------|
| `plain` | (default) | Open-source development build |
| `yuku_alkitab` | `yuku.alkitab` | Production Indonesian version |
| `yuku_quick_bible` | `yuku.alkitab.kjv` | Production English version |
| `sabda_alkitab` | `org.sabda.alkitab` | SABDA partner version |

Each flavor can override resources in `src/{flavor}/res/` and Java/Kotlin sources in `src/{flavor}/java/`.

## Build Variants

- **plainDebug** — local development (works out of the box)
- **plainRelease** — release build of open-source version
- **yuku_alkitabDebug/Release** — Indonesian production
- **yuku_quick_bibleDebug/Release** — English production
- **sabda_alkitabDebug/Release** — SABDA partner

## Common Build Commands

```bash
# Development
./gradlew assemblePlainDebug          # Build debug APK
./gradlew bundlePlainDebug            # Build debug AAB
./gradlew installPlainDebug           # Build and install on device

# Testing
./gradlew testPlainDebugUnitTest      # Debug unit tests
./gradlew testPlainReleaseUnitTest    # Release unit tests (ProGuard applied)
./gradlew testPlainDebugUnitTest --tests "fully.qualified.TestClass"
./gradlew testPlainDebugUnitTest --tests "*.TestClass.testMethod"

# Lint
./gradlew lintPlainDebug
```

## CI/CD

GitHub Actions workflow (`.github/workflows/android.yml`):
- Triggers on push/PR to `develop` branch
- Ubuntu latest, JDK 17 (Zulu)
- Runs: `testPlainDebugUnitTest`, `testPlainReleaseUnitTest`, `assemblePlainDebug`, `bundlePlainDebug`

## Release Build

Production release builds are pure Gradle:

```bash
ALKITAB_PROPRIETARY_DIR=/path/to/proprietary \
SIGN_KEYSTORE=/path/to/keystore \
SIGN_ALIAS=mykey \
SIGN_PASSWORD=secret \
BUILD_DIST=market \
./gradlew assembleYuku_alkitabRelease
```

Required `$ALKITAB_PROPRIETARY_DIR` layout:
```
$ALKITAB_PROPRIETARY_DIR/
├── google-services.json                      # one file with client entries for every production applicationId
└── overlay/
    ├── yuku.alkitab/text_raw/                # real Bible text for yuku_alkitab
    ├── yuku.alkitab.kjv/text_raw/            # real Bible text for yuku_quick_bible
    └── org.sabda.alkitab/text_raw/           # real Bible text for sabda_alkitab (a symlink to yuku.alkitab is fine if both ship the same Bible)
```

Environment variables:
- `ALKITAB_PROPRIETARY_DIR` — directory matching the layout above. Required for `yuku_alkitab`, `yuku_quick_bible`, `sabda_alkitab`. Not used by `plain`.
- `SIGN_KEYSTORE`, `SIGN_ALIAS`, `SIGN_PASSWORD` — required to sign release builds (any flavor). The signing config in `Alkitab/build.gradle` reads them at config time.
- `BUILD_DIST` — distribution channel identifier embedded in the APK filename. Defaults to `dev` when unset.

What the Gradle build does:
1. `CopyProprietaryAssetsTask` (per production flavor) copies `$ALKITAB_PROPRIETARY_DIR/overlay/<applicationId>/text_raw/*` into `Alkitab/build/generated/proprietaryAssets/<flavor>/internal/`. Wired into AGP via `androidComponents { onVariants { ... addGeneratedSourceDirectory(...) } }` so every consumer (mergeAssets, lint vital, etc.) automatically depends on it. Fails fast if the env var is unset or the overlay is missing.
2. `copyProprietaryGoogleServices<Flavor>` (per production flavor) copies `$ALKITAB_PROPRIETARY_DIR/google-services.json` into `Alkitab/src/<flavor>/google-services.json`, where the GMS plugin's source-set lookup picks it up. Those destinations are matched by the existing `google-services.json` line in `.gitignore`, so they're never committed — they behave like build artifacts that just happen to live under `src/`. The plain flavor falls back to the committed placeholder at `Alkitab/google-services.json`.
3. The git commit hash is read at config time and exposed as `BuildConfig.LAST_COMMIT_HASH` (consumed by `AboutActivity` and `InstallationUtil`).
4. The release APK is named `Alkitab-{versionCode}-{versionName}-{commitHash}-{applicationId}-{BUILD_DIST}.apk`.
5. For non-plain release builds, `validate<Variant>FirebaseConfig` reads the post-copy `Alkitab/src/<flavor>/google-services.json` and aborts the build if the API key is missing or a placeholder.

The `plain` flavor keeps its placeholder `ddd_*` Bible files in `Alkitab/src/plain/assets/internal/` and uses the placeholder `Alkitab/google-services.json`. It needs none of the proprietary env vars.

## ProGuard

Release builds use ProGuard with:
- `minifyEnabled true` and `shrinkResources true`
- **No obfuscation** (`-dontobfuscate` in `proguard-rules.pro`)
- Preserves: Serializable classes, OkHttp3, Gson, Kotlin Serialization, datatransfer models

## Server Configuration (Build Config)

Defined in root `build.gradle`:
- `SERVER_HOST`: `https://api.alkitab.app`
- `RIBKA_FUNCTIONS_HOST`: `https://us-central1-pulau-ribka.cloudfunctions.net/` (release)
- `RIBKA_FUNCTIONS_HOST_DEBUG`: `http://10.0.3.2:5001/pulau-ribka/us-central1/` (debug, emulator localhost)

## Firebase

- A placeholder `Alkitab/google-services.json` is committed so `plainDebug` works out of the box. The real `google-services.json` (covering all production applicationIds) lives at `$ALKITAB_PROPRIETARY_DIR/google-services.json` and is copied per-flavor into gitignored `Alkitab/src/<flavor>/google-services.json` at build time — see "Release Build" above.
- FCM registration is skipped in debug builds
- Firebase BOM 29.0.3 (Messaging + Crashlytics)
- Debug builds use `RIBKA_FUNCTIONS_HOST_DEBUG` for FCM functions

## Supported Locales

37 locales configured in `resConfigs`: af, am, bg, cs, de, el, en, es, et, fa, fi, fr, hu, in, it, iw, ja, jv, ko, lt, lv, mk, ms, my, nl, no, pl, pt, ro, ru, sk, sv, th, uk, vi, zh-CN, zh-TW.
