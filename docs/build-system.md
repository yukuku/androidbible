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

## Release Build (`ybuild.sh`)

Production release script for maintainers:

1. Creates 1GB RAM disk at `/Volumes/ART` for fast builds
2. Requires environment variables:
   - `ALKITAB_PROPRIETARY_DIR` — path to proprietary resources overlay
   - `SIGN_KEYSTORE`, `SIGN_ALIAS`, `SIGN_PASSWORD` — signing config
   - `FLAVOR` — which flavor to build
   - `BUILD_PACKAGE_NAME` — final package name
   - `BUILD_DIST` — distribution channel identifier
3. Overlays proprietary resources from `ALKITAB_PROPRIETARY_DIR`
4. Writes Git commit hash to `R.string.git_commit_hash`
5. Outputs signed APK named `{package}-{versionCode}-{versionName}-{commitHash}.apk`

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

- `google-services.json` is gitignored — each build environment must provide its own
- FCM registration is skipped in debug builds
- Firebase BOM 29.0.3 (Messaging + Crashlytics)
- Debug builds use `RIBKA_FUNCTIONS_HOST_DEBUG` for FCM functions

## Supported Locales

37 locales configured in `resConfigs`: af, am, bg, cs, de, el, en, es, et, fa, fi, fr, hu, in, it, iw, ja, jv, ko, lt, lv, mk, ms, my, nl, no, pl, pt, ro, ru, sk, sv, th, uk, vi, zh-CN, zh-TW.
