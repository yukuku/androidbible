Bible for Android
=================

**100% Free, Open-Source, Quick and Friendly Bible App.**

Get the apps on Google Play: [Alkitab](https://play.google.com/store/apps/details?id=yuku.alkitab) (Indonesian version) or [Quick Bible](https://play.google.com/store/apps/details?id=yuku.alkitab.kjv) (non-Indonesian version). You can also download them from the [Releases](https://github.com/yukuku/androidbible/releases) page.

- [Official Website](https://alkitab.app)
- [Changelog](https://alkitab.app/changelog) and [Development Blog](https://blog.bibleforandroid.com)
- [Discussion Group](https://groups.google.com/group/bibleforandroid)

Alkitab is the Indonesian word for the Bible.

Bible translations/versions
---------------------------

This app natively uses *.yes* files for the Bible text. You can create a *.yes* file easily by preparing a plain text file. See [this page](https://alkitab.app/developer) for instructions.

You can also convert PalmBible+ PDB files using the built-in converter in the app for your own use.

Tools for converting PalmBible+ PDB files to *.yet* files, and from *.yet* files to *.yes* files and internal app files, are available in [this folder](https://drive.google.com/drive/folders/0B0mZXH9nEuQ0dGdxbUI5T1lyeUU?resourcekey=0-V_emMiw0Q1APka5ddsS2rA&usp=sharing).

Customizing and Integrating
---------------------------

Bible for Android is not only open-source, but also designed in a flexible manner. You can:

- Create and distribute your own Bible translation
- Create and make publicly available your favorite Song Book
- Create and publish a Reading Plan
- Open specific verses from your app
- Make your own app that gives further insight regarding specific verses callable by Alkitab / Quick Bible

See the [Developer page](https://alkitab.app/developer) for more information.

Building
--------

Requirements: JDK 17 (Zulu recommended) and the Android SDK, including the NDK — the `Snappy` module contains native C++. The SDK and NDK versions are pinned in `gradle/libs.versions.toml`.

The main app module is `:Alkitab`. For local development, the supported open-source build is the `plain` flavor.

Build a debug APK:

    ./gradlew assemblePlainDebug

Build a debug App Bundle:

    ./gradlew bundlePlainDebug

Run the unit tests used in CI:

    ./gradlew testPlainDebugUnitTest testPlainReleaseUnitTest

Developer Documentation
-----------------------

For working with the codebase, see [CLAUDE.md](CLAUDE.md) (architecture overview, module structure, ARI encoding, code conventions, and build instructions) and the `docs/` folder:

**Architecture & infrastructure**
- [Architecture Deep Dive](docs/architecture.md) — singleton patterns, data flow, module dependencies
- [Build System](docs/build-system.md) — flavors, signing, CI/CD, release process
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

**Tech debt**
- [Tech Debt & Known Issues](docs/tech-debt.md) — known problems with file/line references
- [Tech Debt Remediation Plan](docs/tech-debt-remediation.md) — prioritized fixes with BRICE scores

Notes
-----

- The repository contains placeholder Bible data in `Alkitab/src/plain/assets/internal` and a placeholder `Alkitab/google-services.json`, so the open-source `plainDebug` build works out of the box once the Android SDK and NDK are installed (Firebase features won't actually function with the placeholder, but the build and the rest of the app do).
- Release packaging is pure Gradle. Production flavors expect the signing env vars (`SIGN_KEYSTORE`, `SIGN_ALIAS`, `SIGN_PASSWORD`) and `ALKITAB_PROPRIETARY_DIR`, which must contain `overlay/<applicationId>/text_raw/` (Bible text) and `google-services.json` (real Firebase config covering every production applicationId). With those set, `./gradlew assembleYuku_alkitabRelease` (or any other production flavor) builds and signs the APK directly. Set `BUILD_DIST` to override the `dev` suffix in the output filename.
- Product flavors currently include `plain`, `yuku_alkitab`, `yuku_quick_bible`, and `sabda_alkitab`.

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
