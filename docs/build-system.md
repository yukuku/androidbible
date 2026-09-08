# Build System

## Gradle Configuration

- **AGP / Kotlin / library versions**: pinned in the version catalog, `gradle/libs.versions.toml`
- **Compile SDK**: 36, **Min SDK**: 26, **Target SDK**: 35
- **JVM Toolchain**: 21 (all modules)
- **NDK**: required for Snappy native code; exact version pinned in the build config
- **Build files**: Kotlin DSL (`build.gradle.kts`, `settings.gradle.kts`) with a version catalog in `gradle/libs.versions.toml`

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

## Versioning

`versionName` is assembled at build time from `version.properties` at the repo
root plus a *stage*, so an artifact's name says which channel produced it:

| Stage | Selected by | Example | Used for |
|-------|-------------|---------|----------|
| `dev` | the default | `5.0.0-dev.42` | every ordinary build, local or CI |
| `beta` | `-PversionStage=beta` or `VERSION_STAGE=beta` | `5.0.0-beta.1` | the Play open-testing track |
| `release` | `-PversionStage=release` or `VERSION_STAGE=release` | `5.0.0` | the Play production track |

`version.properties` holds only `versionBase` (the marketing version) and
`betaNumber`. The dev counter is not stored: it is the number of commits since
`versionBase` last changed, so it advances once per commit on its own, is the
same for anyone building that commit, and restarts near zero for each release
line rather than counting the whole repo's history. A `betaNumber` bump does not
reset it, so a dev version name is never reused within one `versionBase`.

Finding that starting point needs real history, so CI checks out with
`fetch-depth: 0`. On a shallow clone the lookup finds nothing and the count
falls back to however many commits the clone happens to have.

Beta numbers are deliberately manual, because a number that moved on its own
could not identify the build a tester is reporting against:

```bash
./gradlew bumpBetaNumber   # betaNumber 1 -> 2
git commit -am "Bump to 5.0.0-beta.2"
```

When a release ships, bump `versionBase` and reset `betaNumber` to 1 in the same
commit.

`versionCode` is independent of all of this: it is
`(2_000_000 + minutes since 2026-01-01 UTC) * 10`, so any later build outranks
any earlier one regardless of branch or stage, and Play never sees a number go
backwards. The `* 10` reserves nine spare codes per minute in case per-ABI
splits are ever needed. Because it is derived from the clock, two builds of the
same commit normally get different codes; set `VERSION_CODE` to pin it, which is
what the release workflow does so all three flavors in a run share one code.

Two consequences worth knowing:

- A dev build published from `develop` gets a *higher* `versionCode` than a beta
  built yesterday. That is only a problem if both reach the same Play track, so
  keep dev builds off Play entirely (they live on GitHub pre-releases).
- If a production hotfix is ever built after a beta, its `versionCode` will
  exceed the beta's, and Play will serve that hotfix to testers as an upgrade.
  Rebuild and re-upload the beta afterwards to put testers back ahead.

### Release helper tasks

```bash
./gradlew printVersion              # versionStage / versionName / versionCode
./gradlew bumpBetaNumber            # increment betaNumber in version.properties
./gradlew bundleProductionRelease   # AAB for all three production flavors
./gradlew assembleProductionRelease # APK for all three production flavors
```

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
- Triggers on push/PR to `develop` and `release/**` branches
- Ubuntu latest, JDK 21 (Zulu)
- `plain-debug` job: runs `testPlainDebugUnitTest`, `testPlainReleaseUnitTest`, `assemblePlainDebug`, `bundlePlainDebug`
- `signed-release` job (pushes to `develop` and same-repo PRs): builds and signs all production flavors using the proprietary overlay repo, uploads per-flavor artifacts, and on `develop` pushes publishes a GitHub pre-release. On PRs it also uploads a `pr-preview-apks` artifact (APKs and metadata only — no AABs or mapping files)
- `pr-apk-preview` job (same-repo PRs only): publishes those signed release APKs to a Cloudflare Worker with static assets and comments immutable `*.workers.dev` download links on the PR

A second workflow, `.github/workflows/release.yml`, is manual
(`workflow_dispatch`) and builds the artifacts that actually go to Google Play.
See "Release workflow" below.

### PR APK previews

Each same-repo PR gets its signed release APKs published to the `alkitab-pr` Cloudflare Worker, and a comment linking to a download page. Fork PRs skip it — they have neither the signing key nor the Cloudflare token.

`wrangler versions upload` creates a new worker *version* per build, each with its own immutable URL (`https://<8-hex>-alkitab-pr.<subdomain>.workers.dev`), so builds never overwrite each other. `tools/cloudflare/pr-preview/make_dist.py` stages the APKs plus a generated `index.html`; `render_comment.py` renders the PR comment.

Configuration is two repository secrets — `CLOUDFLARE_API_TOKEN` (created from the "Edit Cloudflare Workers" token template) and `CLOUDFLARE_ACCOUNT_ID`. Without them the job skips cleanly, so CI stays green. The worker needs no manual creation: the first run bootstraps it via `wrangler deploy`, then retries the version upload.

Two caveats: the APKs are production-signed and share application IDs with the Play Store builds, so installing one replaces the installed app; and preview URLs are public with no documented expiry, so a build stays reachable until its version is deleted (Cloudflare Access can gate them if that is not acceptable).

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
- `SIGN_KEYSTORE`, `SIGN_ALIAS`, `SIGN_PASSWORD` — required to sign release builds (any flavor). The signing config in `Alkitab/build.gradle.kts` reads them at config time.
- `BUILD_DIST` — distribution channel identifier embedded in the APK filename. Defaults to `dev` when unset.
- `VERSION_STAGE` — `dev` (default), `beta`, or `release`; picks how `versionName` is assembled. `-PversionStage=` does the same and wins if both are given. See "Versioning" above.
- `VERSION_CODE` — pins `versionCode` instead of deriving it from the clock, so a rebuild of the same commit produces the same number. The release workflow sets it once per run.

What the Gradle build does:
1. `CopyProprietaryAssetsTask` (per production flavor) copies `$ALKITAB_PROPRIETARY_DIR/overlay/<applicationId>/text_raw/*` into `Alkitab/build/generated/proprietaryAssets/<flavor>/internal/`. Wired into AGP via `androidComponents { onVariants { ... addGeneratedSourceDirectory(...) } }` so every consumer (mergeAssets, lint vital, etc.) automatically depends on it. Fails fast if the env var is unset or the overlay is missing.
2. `copyProprietaryGoogleServices<Flavor>` (per production flavor) copies `$ALKITAB_PROPRIETARY_DIR/google-services.json` into `Alkitab/src/<flavor>/google-services.json`, where the GMS plugin's source-set lookup picks it up. Those destinations are matched by the existing `google-services.json` line in `.gitignore`, so they're never committed — they behave like build artifacts that just happen to live under `src/`. The plain flavor falls back to the committed placeholder at `Alkitab/google-services.json`.
3. The git commit hash is read at config time and exposed as `BuildConfig.LAST_COMMIT_HASH` (consumed by `AboutActivity` and `InstallationUtil`).
4. The release APK is named `Alkitab-{versionCode}-{versionName}-{commitHash}-{applicationId}-{BUILD_DIST}.apk`.
5. For non-plain release builds, `validate<Variant>FirebaseConfig` reads the post-copy `Alkitab/src/<flavor>/google-services.json` and aborts the build if the API key is missing or a placeholder.
6. `debugSymbolLevel = "SYMBOL_TABLE"` makes AGP emit `Alkitab/build/outputs/native-debug-symbols/<variant>/native-debug-symbols.zip` and embed the same symbols in the AAB, so the Play Console can symbolicate crashes in the Snappy JNI code.

The `plain` flavor keeps its placeholder `ddd_*` Bible files in `Alkitab/src/plain/assets/internal/` and uses the placeholder `Alkitab/google-services.json`. It needs none of the proprietary env vars.

### Release workflow

`.github/workflows/release.yml` is the manual (`workflow_dispatch`) counterpart
to the automatic `develop` builds. It takes a `stage` (`beta` or `release`) and
an optional `dry_run`, and it:

1. Resolves the version once via `./gradlew -q :Alkitab:printVersion`, then pins
   `VERSION_CODE` so all three flavors in the run share one code.
2. Fails immediately if the tag `v<versionName>` already exists, which is what
   catches a forgotten `bumpBetaNumber`.
3. Builds signed APKs and AABs for all three production flavors, then checks
   each `output-metadata.json` against the version it announced.
4. Uploads everything as a workflow artifact with 90-day retention (rather than
   the 14 days `android.yml` uses), because a mapping file stays useful for as
   long as the build it belongs to is installed anywhere.
5. Creates a GitHub Release tagged `v<versionName>`, marked as a pre-release for
   beta stages, carrying the AAB, APK, `mapping.txt` and native debug symbols
   for each flavor.

Its tags are version names (`v5.0.0-beta.2`), which keeps them distinct from the
`versionCode`-based tags (`v23605150`) that `android.yml` creates for dev
pre-releases.

Releasing a beta, end to end:

```bash
./gradlew bumpBetaNumber
git commit -am "Bump to 5.0.0-beta.2" && git push
# then run the Release workflow against that commit with stage=beta,
# download the AABs from the GitHub Release, and upload them to the
# Play open-testing track.
```

## ProGuard

Release builds use ProGuard with:
- `minifyEnabled true` and `shrinkResources true`
- **No obfuscation** (`-dontobfuscate` in `proguard-rules.pro`)
- Preserves: Serializable classes, OkHttp3, Gson, Kotlin Serialization, datatransfer models

## Server Configuration (Build Config)

Defined in `Alkitab/build.gradle.kts`:
- `SERVER_HOST`: `https://api.alkitab.app`
- `RIBKA_FUNCTIONS_HOST`: `https://us-central1-pulau-ribka.cloudfunctions.net/` (release)
- `RIBKA_FUNCTIONS_HOST_DEBUG`: `http://10.0.3.2:5001/pulau-ribka/us-central1/` (debug, emulator localhost)

## Firebase

- A placeholder `Alkitab/google-services.json` is committed so `plainDebug` works out of the box. The real `google-services.json` (covering all production applicationIds) lives at `$ALKITAB_PROPRIETARY_DIR/google-services.json` and is copied per-flavor into gitignored `Alkitab/src/<flavor>/google-services.json` at build time — see "Release Build" above.
- FCM registration is skipped in debug builds
- Firebase BOM (Messaging + Crashlytics); version pinned in `gradle/libs.versions.toml`
- Debug builds use `RIBKA_FUNCTIONS_HOST_DEBUG` for FCM functions

## Supported Locales

30 locales configured via `androidResources.localeFilters` in `Alkitab/build.gradle.kts`: af, bg, ceb, cs, da, de, el, es, fr, hu, in, it, ja, ko, lv, ms, my, nl, pl, pt-rBR, pt, ro, ru, th, tl, tr, uk, vi, zh-rCN, zh-rTW.
