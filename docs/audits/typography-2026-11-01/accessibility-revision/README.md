# Medium controls and distinct number input

[Open the interactive review and annotated Android evidence](index.html). The [public review page](https://androidbible-typography-review.itikku.chatgpt.site/accessibility/) contains the same options and captures.

This revision follows the shared typography proposal. It applies Medium (500) to book selectors and dropdown items, book/chapter/verse grid buttons, Bible and song keypad digits and suffixes, and version short names or primary titles in both the selector and version manager. Version descriptions remain 14sp Regular to preserve the hierarchy.

Book and version names use 16sp Medium, grid cells use 16sp Medium, keypad digits use 28sp Medium, and numeric entry uses 24sp Medium. The native song drawer previously inherited a 22sp button size; it now shares the 28sp keypad role. Secondary songbook names use 14sp Medium in Compose. On the stock Android 15 capture device, the native popup uses a nominal 16sp menu size and retains its 0.7× secondary span (11.2sp), while applying Medium to both.

## Number entry

Option A is implemented as the review candidate. It uses a rounded rectangular field, a blue outline for the active target, and a darker blue-gray fill that separates the value from the plain keypad buttons.

- A value which the next digit replaces uses a quieter foreground and a dashed border.
- A value which the next digit continues uses brighter digits and a solid border. The active field also shows a caret.
- Switching chapter and verse preserves each field's own editing state.
- A song code with four characters is replacement-ready because the next digit starts a new code.
- Empty fields are replacement-ready. Existing keypad validation, replacement, appending, backspace, and navigation logic is preserved.

The quieter digits have 6.01:1 contrast against their field, while entered digits have 10.69:1 contrast. Border style and the caret provide cues in addition to color. Screen readers receive localized descriptions of whether the next digit replaces or continues the value.

Options B (filled capsule) and C (light value tile) are interactive browser prototypes for review. They use the browser's system font and are labeled as prototypes. They are not Android screenshots or alternative implementations in the APK. The final shape and color choice remains open for review.

## Evidence

The report contains ten before/after pairs and thirteen additional states. The before frames are the retained after frames from the initial proposal. They therefore compare this feedback revision with the initial shared scale, rather than with the original app before the typography project. The original 75-pair comparison remains in [comparison](../comparison/README.md).

The legacy book picker baseline contains bundled DDD book names; the revision uses BIMK. These pairs compare the control structure and font treatment. The displayed book text differs. Additional states without retained baseline frames are clearly labeled and contain only the revised screenshot.

All new frames come from the real app on the dedicated Android 15 emulator, 720 × 1280 pixels, 320dpi, 100% system font size, with Indonesian UI and public BIMK/KJ content. Android CLI installed and launched the builds. Device-specific Instrumentation UiAutomation screenshots were collected through adb. Orange annotation boxes are HTML overlays; the linked raw PNGs remain unretouched.

- [Screen definitions and nominal font annotations](screens.json)
- [Capture provenance and image hashes](manifest.json)
- [Focused unit-test results](unit-tests.json)
- [Lint result](lint.json)
- [Browser report validation](validation.json)
- [Raw before frames](before/) and [raw revised frames](after/)
- [Annotated comparison exports](annotated/)

## Implementation and verification

`UiTypography` and `typography.xml` share the Medium keypad role across native and Compose controls. The relevant book and version rows use the existing Emphasis role. `NumericEntryValue` supplies the Compose field; `NumericEntryAppearance` supplies the equivalent native treatment. The legacy dialer and both song handles receive the actual replacement/appending state rather than inferring it from the displayed number.

The final app and test APK builds passed. Fifteen focused test executions passed, covering shared typography and native color inheritance on API 26/34, readable replacement values, cursor clearing, and chapter/verse input-state transitions. Android lint passed with no errors; existing warnings and hints are recorded in the lint result. The real-device evidence covers the modern and legacy navigation controls, song sheet and native drawer, and both version-manager tabs.

The 200% text-scale layout issues remain outside this revision. This report does not claim human low-vision usability testing; it provides concrete options for that review.

## Reproduce

Use the dedicated QA emulator. The test-only runner seeds local fixtures and changes presentation preferences.

1. Build with `./gradlew assemblePlainDebug assemblePlainDebugAndroidTest 2>&1 | tail -60` and install the app with Android CLI.
2. Install the test APK with `adb -s DEVICE install -r Alkitab/build/outputs/apk/androidTest/plain/debug/Alkitab-plain-debug-androidTest.apk`.
3. Run `adb -s DEVICE shell am instrument -w -e phase after -e folder accessibility-revision -e cases CASES yuku.alkitab.debug.test/yuku.alkitab.qa.TypographyCaptureInstrumentation`, using case names from `screens.json`.
4. Pull `/sdcard/Android/data/yuku.alkitab.debug/files/typography/accessibility-revision/` into `after/`.
5. Run `node build-report.cjs`. The page keeps the design prototypes and rebuilds the annotated evidence section from `screens.json`.

The app database version follows versionCode, so capture baselines before installing a newer APK. Reuse the retained frames rather than downgrading an installation with newer data.
