Bible for Android
=================

**100% Free, Open-Source, Quick and Friendly Bible App.**

Get the apps on Google Play: [Alkitab](https://play.google.com/store/apps/details?id=yuku.alkitab) (Indonesian version) or [Quick Bible](https://play.google.com/store/apps/details?id=yuku.alkitab.kjv) (non-Indonesian version).

- [Official Website](https://alkitab.app)
- [Developer Page](https://alkitab.app/developer) — creating your own Bible versions, song books, reading plans, and integrations
- [Changelog](CHANGELOG.md)
- [Development Blog](https://blog.alkitab.app)
- [Discussion Group](https://groups.google.com/group/bibleforandroid)

Alkitab is the Indonesian word for the Bible.

Building
--------

Requirements: JDK 21 (Zulu recommended) and the Android SDK, including the NDK, because the `Snappy` module contains native C++. The compile SDK, minimum SDK, and NDK versions are pinned in `gradle/libs.versions.toml`.

The main app module is `:Alkitab`. For local development, the supported open-source build is the `plain` flavor, which works out of the box with no extra setup: placeholder Bible data ships in `Alkitab/src/plain/assets/internal`, and a placeholder `Alkitab/google-services.json` is checked in (Firebase features will not actually function with it, but the build and the rest of the app do).

Build a debug APK:

    ./gradlew assemblePlainDebug

Build a debug App Bundle:

    ./gradlew bundlePlainDebug

Run the unit tests used in CI:

    ./gradlew testPlainDebugUnitTest testPlainReleaseUnitTest

The four product flavors are `plain` (open source), plus `yuku_alkitab`, `yuku_quick_bible`, and `sabda_alkitab` (the published apps). The production flavors need the proprietary Bible text and signing keys described under [Release builds](#release-builds) below.

Contributing
------------

`develop` is the integration branch. Base your work on `develop` and target your pull request at `develop`, never at `master`. The repository follows git-flow: `master` only receives merges from `release/*` branches when a version ships.

Every pull request against `develop` runs the unit tests and a `plainDebug` build. Pull requests from branches inside this repository also get signed release APKs for all production flavors, uploaded to a preview URL and linked in a comment on the pull request. Pull requests from forks skip that job, since it needs secrets that forks cannot read.

Before working on the codebase, read [CLAUDE.md](CLAUDE.md) for the architecture overview, module structure, ARI verse addressing, and code conventions.

Bible translations/versions
---------------------------

This app natively uses *.yes* files for the Bible text. You can create a *.yes* file easily by preparing a plain text file (a *.yet* file). See [the developer page](https://alkitab.app/developer) for instructions, and [docs/binary-formats.md](docs/binary-formats.md) for the format specifications.

To add a version to the app, open it from the Versions screen. The app accepts *.yes* files directly, and converts PalmBible+ *.pdb* files to *.yes* with its built-in converter for your own use. Gzipped files (*.yes.gz*, *.pdb.gz*) are decompressed transparently.

For converting in bulk or from another Bible format, see [docs/tools.md](docs/tools.md), which covers the desktop converters in `tools/` and where to get the prebuilt jars.

Customizing and Integrating
---------------------------

Bible for Android is not only open-source, but also designed in a flexible manner. You can:

- Create and distribute your own Bible translation
- Create and make publicly available your favorite Song Book
- Create and publish a Reading Plan
- Open specific verses from your app
- Make your own app that gives further insight regarding specific verses callable by Alkitab / Quick Bible

The inter-app API lives in the `AlkitabIntegration` module, and `extensions/example-imagesharer` is a working example of an extension app. See the [Developer page](https://alkitab.app/developer) for more information.

Developer Documentation
-----------------------

Start with [CLAUDE.md](CLAUDE.md) (architecture overview, module structure, ARI encoding, code conventions, and build instructions), then the `docs/` folder:

**Architecture & infrastructure**
- [Architecture Deep Dive](docs/architecture.md) — singleton patterns, data flow, module dependencies
- [Build System](docs/build-system.md) — flavors, signing, CI/CD, release process
- [Google Play Publishing](docs/play-publishing.md) — store uploads via `tools/play/publish.py`
- [Tools](docs/tools.md) — the desktop converters in `tools/` for Bible versions and reading plans
- [Storage & Database](docs/storage.md) — SQLite schema, preferences, file storage
- [Backend Communication](docs/backend-communication.md) — API endpoints, download flows
- [Text Rendering](docs/text-rendering.md) — verse formatting pipeline and codes
- [Binary Formats](docs/binary-formats.md) — YES2, Bintex, RPB file format specs

**Feature modules**
- [Songs](docs/modules/songs.md) — song book browsing, search, audio playback
- [Reading Plans](docs/modules/reading-plans.md) — RPB binary format, daily progress tracking
- [Versions](docs/modules/versions.md) — Bible version management, download, YES2 format
- [Markers](docs/modules/markers.md) — bookmarks, notes, highlights system
- [Sync](docs/modules/sync.md) — cloud sync protocol and FCM push
- [Devotions](docs/modules/devotions.md) — daily devotional articles
- [Audio Playback](docs/modules/audio-playback.md) — ExoPlayer/MIDI controllers
- [Search](docs/modules/search.md) — full-text verse search engine
- [Daily Verse Widget](docs/modules/daily-verse-widget.md) — home screen widget
- [Data Transfer](docs/modules/data-transfer.md) — JSON export/import of user data

Design documents for in-flight features are under `docs/features/`.

**Tech debt**
- [Tech Debt & Known Issues](docs/tech-debt.md) — known problems with file/line references
- [Tech Debt Remediation Plan](docs/tech-debt-remediation.md) — prioritized fixes with BRICE scores

Release builds
--------------

The [Releases](https://github.com/yukuku/androidbible/releases) page holds pre-release APKs built automatically from every push to `develop`. They are signed with the production key and installable, but they are development builds, not the stable versions on Google Play.

`versionName` comes from `version.properties` combined with a build stage: `dev` by default, `beta` with `-PversionStage=beta`, and `release` with `-PversionStage=release`. Never hardcode a version in `Alkitab/build.gradle.kts`.

Release packaging is pure Gradle. Production flavors expect the signing env vars (`SIGN_KEYSTORE`, `SIGN_ALIAS`, `SIGN_PASSWORD`, plus `SIGN_SABDA_KEYSTORE`, `SIGN_SABDA_ALIAS`, `SIGN_SABDA_PASSWORD` for the `sabda_alkitab` flavor, which has its own upload key) and `ALKITAB_PROPRIETARY_DIR`, which must contain `overlay/<applicationId>/text_raw/` (Bible text) and `google-services.json` (real Firebase config covering every production applicationId). With those set, `./gradlew assembleYuku_alkitabRelease` (or any other production flavor) builds and signs the APK directly. Set `BUILD_DIST` to override the `dev` suffix in the output filename.

Translations are integrated with `integrate_translations.sh`.

License
--------

    Copyright 2009-present The Alkitab App Authors.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
