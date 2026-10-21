Bible for Android
=================

**100% Free, Open-Source, Quick and Friendly Bible App.**

Get the apps on Google Play: [Alkitab](https://play.google.com/store/apps/details?id=yuku.alkitab) (Indonesian version) or [Quick Bible](https://play.google.com/store/apps/details?id=yuku.alkitab.kjv) (non-Indonesian version).

- [Official Website](https://alkitab.app)
- [Developer Page](https://alkitab.app/developer) for creating your own Bible versions, song books, reading plans, and integrations
- [Content and Integration Guides](docs/developer/README.md) for the developer documentation maintained in this repository
- [Changelog](CHANGELOG.md)
- [Development Blog](https://blog.alkitab.app)
- [Discussion Group](https://groups.google.com/group/bibleforandroid)

Alkitab is the Indonesian word for the Bible.

Building
--------

Requirements: JDK 21 (Zulu recommended) and the Android SDK, including the NDK, because the `Snappy` module contains native C++. The compile SDK, minimum SDK, and NDK versions are pinned in `gradle/libs.versions.toml`.

The main app module is `:Alkitab`. For local development, the supported open-source build is the `plain` flavor, which works out of the box: placeholder Bible data ships in `Alkitab/src/plain/assets/internal`, and a placeholder `Alkitab/google-services.json` is checked in (Firebase features will not actually function with it, but the build and the rest of the app do).

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

See the [documentation index](docs/README.md) for architecture, feature guides, and tooling, and [AGENTS.md](AGENTS.md) for contributor conventions.

Bible translations/versions
---------------------------

This app natively uses *.yes* files for the Bible text. You can create a *.yes* file by preparing a plain text file (a *.yet* file). See the [YET authoring guide](docs/developer/yet.md) for instructions and [docs/binary-formats.md](docs/binary-formats.md) for the binary format specifications.

To add a version to the app, open it from the Versions screen. The app accepts *.yes* files directly, and converts PalmBible+ *.pdb* files to *.yes* with its built-in converter for your own use. Gzipped files (*.yes.gz*, *.pdb.gz*) are decompressed transparently.

For converting in bulk or from another Bible format, see [docs/tools.md](docs/tools.md), which covers the desktop converters in `tools/` and where to get the prebuilt jars.

Customizing and Integrating
---------------------------

Bible for Android is not only open-source, but also designed in a flexible manner. You can:

- Create and distribute your own Bible translation
- Create and make publicly available your favorite Song Book
- Create and publish a Reading Plan
- Open specific verses from your app

The inter-app API lives in the `AlkitabIntegration` module. See the [content and integration guides](docs/developer/README.md) for authoring formats and opening verses.

Developer Documentation
-----------------------

The [documentation index](docs/README.md) links the architecture, infrastructure,
feature guides, contracts, and historical design records. Content authors and
integrators can start with the [developer guides](docs/developer/README.md).

Release builds
--------------

The [Releases](https://github.com/yukuku/androidbible/releases) page holds pre-release APKs built automatically from every push to `develop`. They are signed with the production key and installable, but they are development builds, not the stable versions on Google Play.

See [Build System](docs/build-system.md) for version stages, production overlays,
Firebase configuration, signing keys, and release artifacts. [Google Play
Publishing](docs/play-publishing.md) covers uploading those artifacts to the store.

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
