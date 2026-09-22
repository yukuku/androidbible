# Tools

`tools/` holds the desktop-side programs used to produce the data files the
app consumes: Bible versions, reading plans, and the placeholder Bible that
ships with the open-source build. It also holds the release and CI helpers.

**`tools/` is not part of the Gradle build.** It is a separate IntelliJ
project with its own `.idea/` directory, module `.iml` files, and jar
artifacts. Opening the repository root in Android Studio will not build it;
open `tools/` itself. Nothing in the app module depends on it, and it is not
compiled by CI.

If you only want to convert a file and do not care about the source, use the
prebuilt jars instead. See [Prebuilt jars](#prebuilt-jars) below.

## Bible version converters

The pipeline is `source format` → `.yet` → `.yes` (or the internal format).
`.yet` is a plain-text intermediate: human-readable, hand-editable, and the
format described on the [developer page](https://alkitab.app/developer).
`.yes` is the binary format the app reads at runtime. See
[binary-formats.md](binary-formats.md) for both specifications.

| Module | Main class | Purpose |
|---|---|---|
| `YetToYes2` | `yuku.alkitabconverter.yet.YetToYes2` | `.yet` to `.yes`, the format users install |
| `YetToInternal` | `yuku.alkitabconverter.yet.YetToInternal` | `.yet` to the built-in version format that ships inside the APK |
| `AlkitabConverterProcesses` | one class per version | Source-format importers, described below |
| `AlkitabConverter` | library | Shared code for the above: `TextDb`, `Rec`, `XrefDb`, `FootnoteDb`, USFM and verse-reference parsing |

Both converters take their arguments with JCommander and accept `--help`.

```
java -jar YetToYes2.jar [--no-compress] [--ignore-skipped-verses] <yet-file> [<yes-file>]
java -jar YetToInternal.jar [--prefix <prefix>] <yet-file> [<internal-files-dir>]
```

`YetToYes2` writes `foo.yes` next to `foo.yet` when no output path is given.
Snappy compression is on by default; `--no-compress` turns it off.
`--ignore-skipped-verses` permits gaps in verse numbering, though chapters
must still be consecutive and each book must start at chapter 1 verse 1.

### Source-format importers

`AlkitabConverterProcesses` holds one package per Bible version that has been
imported, each converting some upstream format into `.yet`. They are
one-off scripts kept for reference rather than a general-purpose tool, so
expect to adapt one rather than run it unchanged.

Two are generic batch converters: `unboundbatch` (Unbound Bible archives) and
`thewordbatch` (theWord modules). The rest are per-version, and their package
names follow `<language>_<version>`, for example `in_tb_usfm` (Indonesian
Terjemahan Baru from USFM), `en_web` (World English Bible), `ja_kougo`
(Japanese Kougo), `ro_cornilescu` (Romanian Cornilescu), and `zh_ckjv`
(Chinese KJV).

### The placeholder Bible

`tools/in-ddd/in-ddd.yet` is the source for the `ddd_*` files in
`Alkitab/src/plain/assets/internal/`, the dummy Bible text the open-source
`plain` flavor ships so it builds and runs without the proprietary overlay.
Its verses are generated filler, not scripture. Regenerate the assets by
running `YetToInternal` over it.

## Reading plan converters

| Module | Purpose |
|---|---|
| `RpaToRpb` | Converts an `.rpa` reading plan source into the binary `.rpb` the app reads |
| `RpbTester` | Dumps an `.rpb` file so you can check what a conversion produced |

`.rpa` is the plain-text authoring format; anyone with one can publish a plan.
`tools/AlkitabConverter/file/` holds the sources for the bundled plans
(`bibleplan_*.txt`, `blueletter_*`, `esv_*`) as worked examples. The `.rpb`
format is specified in [binary-formats.md](binary-formats.md), and the app
side is covered in [modules/reading-plans.md](modules/reading-plans.md).

## Supporting modules

- `common-jvm` holds shared JVM-side code.
- `fakeandroid` provides stub implementations of the few Android classes the
  converters touch (`android.util.Log`, `android.os.Parcel`,
  `android.os.Parcelable`, the support annotations), so code shared with the
  app compiles against a plain JDK.
- `prog` holds third-party helper programs collected over the years, kept for
  reference.

## Release and CI helpers

These are unrelated to the converters and live here only because `tools/` is
where scripts go.

- `tools/play/publish.py` uploads builds to Google Play. See
  [play-publishing.md](play-publishing.md).
- `tools/cloudflare/pr-preview/` builds and deploys the per-pull-request APK
  preview site. See the "PR APK previews" section of
  [build-system.md](build-system.md).

## Prebuilt jars

Ready-to-run jars, including a desktop PalmBible+ `.pdb` to `.yet` converter
that has no counterpart in this repository, are in
[this Google Drive folder](https://drive.google.com/drive/folders/0B0mZXH9nEuQ0dGdxbUI5T1lyeUU?resourcekey=0-V_emMiw0Q1APka5ddsS2rA&usp=sharing).

For a one-off `.pdb` conversion you may not need any of this: the app itself
converts PalmBible+ files when you open one from the Versions screen.
