# Tools

`tools/` holds the desktop-side programs used to produce the data files the
app consumes: Bible versions, reading plans, and the placeholder Bible that
ships with the open-source build. It also holds the release and CI helpers.

The converters are plain JVM modules in the root Gradle build, so opening the
repository in Android Studio or IntelliJ IDEA picks them up alongside the app,
and CI compiles and tests them on every pull request:

| Module | Purpose |
|---|---|
| `:tools:converter-core` | Shared converter code plus the app's own format modules, compiled for the desktop JVM |
| `:tools:cli` | The `alkitab-tools` command line: `yet2yes`, `yet2internal`, `rpa2rpb`, `rpbdump` |
| `:tools:importers` | One-off source-format importers, one package per Bible version |

If you only want to convert a file, the prebuilt jars are easier than
building anything. See [Prebuilt jars](#prebuilt-jars) below.

## Running the CLI

Build a self-contained jar and run it:

```bash
./gradlew :tools:cli:fatJar
java -jar tools/cli/build/libs/alkitab-tools.jar --help
```

or install a launcher script with `./gradlew :tools:cli:installDist` and run
`tools/cli/build/install/alkitab-tools/bin/alkitab-tools`. Every command
accepts `--help`.

```
alkitab-tools yet2yes [--no-compress] [--ignore-skipped-verses] <yet-file> [<yes-file>]
alkitab-tools yet2internal [--prefix <prefix>] <yet-file> [<internal-files-dir>]
alkitab-tools rpa2rpb [--name <name>] <rpa-file> [<rpb-file>]
alkitab-tools rpbdump [--verbose] <rpb-file>
```

The converters exit with a non-zero status on failure, so they can be chained
in scripts.

## Bible version converters

The pipeline is `source format` → `.yet` → `.yes` (or the internal format).
`.yet` is a plain-text intermediate, meant to be readable and editable by
hand, and is the format described on the
[developer page](https://alkitab.app/developer). `.yes` is the binary format
the app reads at runtime. See [binary-formats.md](binary-formats.md) for both
specifications.

| Command | Class | Purpose |
|---|---|---|
| `yet2yes` | `yuku.alkitabconverter.yet.YetToYes2` | `.yet` to `.yes`, the format users install |
| `yet2internal` | `yuku.alkitabconverter.yet.YetToInternal` | `.yet` to the built-in version format that ships inside the APK |

`yet2yes` writes `foo.yes` next to `foo.yet` when no output path is given.
Snappy compression is on by default; `--no-compress` turns it off.
`--ignore-skipped-verses` permits gaps in verse numbering, though chapters
must still be consecutive and each book must start at chapter 1 verse 1.

`yet2internal` writes into a directory named after the `.yet` file when no
output directory is given, and prefixes every file with `--prefix` (`ddd` by
default).

### Source-format importers

`:tools:importers` holds one package per imported Bible version, each
converting some upstream format into `.yet`. They read as one-off scripts
rather than a general-purpose tool: each is a `main` method with its input
and output paths written into the code. Expect to adapt one instead of
running it unchanged, and run it from the IDE.

Two are generic batch converters: `unboundbatch` (Unbound Bible archives) and
`thewordbatch` (theWord modules). The rest are per-version, and their package
names follow `<language>_<version>`, for example `in_tb_usfm` (Indonesian
Terjemahan Baru from USFM), `en_web` (World English Bible), `ja_kougo`
(Japanese Kougo), `ro_cornilescu` (Romanian Cornilescu), and `zh_ckjv`
(Chinese KJV).

The USFM importers (`in_tb_usfm`, `in_tsi_usfm`, `in_ayt`, `ury_orya`) first
convert USFM to USFX through `Usfm2Usfx`, which runs
`prog/wordsend/usfm2usfx.exe` under `mono`. So `mono` has to be installed,
and because that path is resolved against the working directory, these
importers need to run with `tools/` as the working directory. The same holds
for the one-off `main` methods in `:tools:converter-core`
(`RpaConverter`, `DailyVerseProses`) that build their paths from
`user.dir`.

### The placeholder Bible

`tools/in-ddd/in-ddd.yet` is the source for the `ddd_*` files in
`Alkitab/src/plain/assets/internal/`, the dummy Bible text the open-source
`plain` flavor ships so it builds and runs without the proprietary overlay.
It is not real Bible text: the verses are placeholder strings that also
exercise the verse formatting codes, so the reader has something to render.
Regenerate the assets with:

```bash
java -jar tools/cli/build/libs/alkitab-tools.jar yet2internal tools/in-ddd/in-ddd.yet Alkitab/src/plain/assets/internal
```

## Reading plan converters

| Command | Purpose |
|---|---|
| `rpa2rpb` | Converts an `.rpa` reading plan source into the binary `.rpb` the app reads |
| `rpbdump` | Prints an `.rpb` file's info and how many times each verse is read, so you can check what a conversion produced |

`.rpa` is the plain-text authoring format: tab-separated `info <key> <value>`
lines (`title`, `description` and `duration` are required) and one
`plan <range-count> <start-ari> <end-ari>...` line per day. The `name` stored
in the `.rpb` defaults to the `.rpa` file name; the app itself ignores it and
names a plan after the file it was installed from.

`tools/reading-plans/` holds the bundled plans as `.rpa`/`.rpb` pairs, and is
where new ones belong. Its `sources/` directory holds the raw reading lists
that `RpaConverter` turned into `.rpa` files, and `archive/` holds older
`.rpb` builds with the coverage summaries produced from them.

The `.rpb` format is specified in [binary-formats.md](binary-formats.md), and
the app side is covered in [modules/reading-plans.md](modules/reading-plans.md).

## How the modules share the app's code

`:tools:converter-core` compiles the source folders of the app's `AlkitabIo`,
`AlkitabModel`, `AlkitabYes2`, `BintexReader`, `BintexWriter`, and `Snappy`
modules directly, next to its own converter code (`TextDb`, `Rec`, `XrefDb`,
`FootnoteDb`, USFM handling, and verse-reference parsing). The converters
therefore write `.yes` files using the same code the app reads them with.

Those modules are Android libraries, so a JVM module cannot depend on them as
projects. `src/stubs/java` supplies stand-ins for the few Android classes they
reference (`android.util.Log`, `android.os.Parcel`, `android.os.Parcelable`);
`androidx.annotation` is an ordinary JVM dependency. `Snappy` falls back to
its pure-Java implementation because the native library is absent on the
desktop.

## Golden tests

`:tools:cli` has tests that run the converters over the inputs checked into
the repository and compare the results with the outputs checked in next to
them:

- `yet2internal` over `tools/in-ddd/in-ddd.yet` must reproduce
  `Alkitab/src/plain/assets/internal/` byte for byte.
- `yet2yes` over the same file, compressed and uncompressed, must read back
  verse for verse through the app's `Yes2Reader`.
- `rpa2rpb` over every `.rpa` in `tools/reading-plans/` must reproduce the
  `.rpb` of the same name byte for byte.

A change to the shared format code that alters what the converters write
fails these tests. If the change is intended, regenerate the checked-in
outputs with the CLI in the same pull request. Run them with:

```bash
./gradlew :tools:converter-core:test :tools:cli:test
```

## Release and CI helpers

These share the directory but are unrelated to the converters.

- `tools/play/publish.py` uploads builds to Google Play. See
  [play-publishing.md](play-publishing.md).
- `tools/cloudflare/pr-preview/` builds and deploys the per-pull-request APK
  preview site. See the "PR APK previews" section of
  [build-system.md](build-system.md).
- `tools/prog/` holds two third-party helpers. `wordsend` is a USFM toolset,
  bundled with the SIL fonts, whose `usfm2usfx.exe` the USFM importers
  invoke. `bdb_to_res_raw.php` is a standalone script.

## Prebuilt jars

Ready-to-run jars, including a desktop PalmBible+ `.pdb` to `.yet` converter
that has no counterpart in this repository, are in
[this Google Drive folder](https://drive.google.com/drive/folders/0B0mZXH9nEuQ0dGdxbUI5T1lyeUU?resourcekey=0-V_emMiw0Q1APka5ddsS2rA&usp=sharing).
They predate the `alkitab-tools` CLI and are invoked as separate jars
(`java -jar YetToYes2.jar ...`), with the same arguments as the matching
command.

For a one-off `.pdb` conversion you may not need any of this: the app itself
converts PalmBible+ files when you open one from the Versions screen.
