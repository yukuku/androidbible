# Store Screenshots

Play Store screenshots are generated, not taken by hand. `StoreScreenshotTest`
drives the app on Gradle managed devices and `tools/screenshots/capture.py`
files the results into `Alkitab/src/<flavor>/play/listings/<language>/graphics/`,
where [Google Play Publishing](play-publishing.md) picks them up.

## Running it

```bash
ALKITAB_PROPRIETARY_DIR=/path/to/proprietary \
  python3 tools/screenshots/capture.py --flavor yuku_quick_bible
```

The overlay is required: store screenshots have to show the real Bible text, so
the capture runs on a production flavor rather than `plain`. Nothing else is
needed, because the debug build type signs itself.

Narrow the run while iterating:

```bash
--devices storePhone          # skip the tablet
--languages en-US,de-DE       # a couple of listings instead of all eight
```

Expect a few minutes per device. The first run also downloads an emulator
system image, and each managed device needs about 7 GB free for its userdata
partition, so a nearly full disk fails with `Not enough space to create
userdata partition`.

## How the screens are reached

Each screenshot launches its activity directly through an intent. Tapping
through the drawer would mean matching on-screen text, which changes in every
language the capture runs in.

## Seeded content

`ScreenshotSeed` fills the app before anything is captured, because most of
these screens are empty on a fresh install:

| Screen | Seeded with |
|--------|-------------|
| Reader, highlights, notes | Markers on Psalm 23 and John 3:16 |
| Devotions | Today's article, Renungan Pagi for Indonesian flavors and Morning & Evening otherwise |
| Reading plan | `esv_mcheyne.rpb` from the test assets, with the first five days ticked |
| Songs | Four public-domain hymns in a book named "Hymns" |

The devotion has to carry *today's* date, because the activity opens on today
and would start a download for any other day. Seeding is not additive: markers,
reading plans and the song book are cleared first, so repeated runs on the same
device do not accumulate duplicates.

The audio bar is the one screen not driven by seeding. It needs audio sets
fetched from the server, so that shot taps the reader's audio button, matching
on its view id rather than its label so it works in every language, and waits
for playback to start. With no network the button is absent, the tap is skipped
and the frame falls back to the plain reader rather than failing the run.

Languages come from one device, not one device per language: the app's language
is its own `pref_language` setting, independent of the device locale, so the
test writes that preference and launches the next activity. That is what makes
covering every listing language affordable.

To add a screen, add a `Shot` to `StoreScreenshotTest.SHOTS`, then add its id to
`SHOT_ORDER` in `capture.py` if it should be filed. A shot that is captured but
not listed in `SHOT_ORDER` is simply not copied.

## Devices

| Device | Profile | Pixels | Files into |
|--------|---------|--------|------------|
| `storePhone` | Nexus 5 | 1080x1920 | `phone-screenshots` |
| `storeTablet` | Nexus 9 | 2048x1536 | `tablet-screenshots`, `large-tablet-screenshots` |

Those two profiles are chosen for Play's rules rather than for being current
hardware. Play rejects a screenshot whose long edge is more than twice its
short edge, which rules out the 20:9 profiles modern phones use, and wants at
least 1080px on the short edge with at least four screenshots per bucket.

The system image is plain `aosp`. The smaller `aosp-atd` images have no
rendering stack, so every screenshot taken on one is solid black. Full images
in turn refuse AGP's default `-gpu auto-no-window`, which is why
`gradle.properties` sets `android.testoptions.manageddevices.emulator.gpu`.

## Known rough edges

- AGP leaves a run's files in `build/intermediates/...` or
  `build/outputs/...` depending on the run, and names the directory after a
  fixed managed device whichever device actually ran. `capture.py` wipes both
  locations before each run and searches both afterwards, which is why the
  device a screenshot belongs to is known from *when* it appeared rather than
  from where AGP put it. The same quirk is why the Gradle task is invoked with
  `--rerun`: a second capture would otherwise be up to date and write nothing.
- The tablet captures include the emulator's launcher taskbar along the bottom
  edge. Cropping belongs with the framing and caption step, which is not built
  yet.
- The reading plan fixture is an English-titled ESV plan, so the plan name reads
  in English even in a localized capture. The daily readings inside it are book
  names, which do come out in the capture language.
- Split-screen version comparison is not captured. It needs a second version
  installed, which the bundled internal version alone cannot provide.
