# Shared UI scale: implementation and comparison

This is the initial proposal snapshot. See the [sentence-case revision](../sentence-case/README.md) for the latest casing changes. See the [Medium-weight accessibility revision and number-field options](../accessibility-revision/README.md) for the subsequent feedback changes.

[Open the complete before/after report](index.html). It embeds every screenshot, so the HTML can be downloaded and opened offline. The report contains 75 screen/state pairs, font annotations, and source evidence. [Annotated PNG exports](annotated/) provide the same comparisons as individual images.

The original app and updated app were captured on the same dedicated Android 15 emulator, at 720 × 1280 pixels, 320dpi, and 100% system font size. App language is Indonesian. The selected-verse before image is reused from the original audit on the same emulator and font settings; application source was unchanged between that audit and the comparison baseline. The other 74 before images were recaptured from the retained original APK. Both passes use the same installed BIMK and KJ content and local bookmark, note, highlight, label, and progress-mark fixtures. The audio and cross-reference cases use the bundled DDD debug version. No account was signed in and no report, feedback, or exported file was sent.

## Implementation

The eight roles use system sans, script fallback, zero tracking, six sizes, and two weights:

| Role | Size | Weight | Line height |
| --- | ---: | ---: | ---: |
| Title | 20sp | 500 | 26sp |
| Primary | 16sp | 400 | 24sp |
| Emphasis | 16sp | 500 | 24sp |
| Supporting | 14sp | 400 | 20sp |
| Label | 14sp | 500 | 20sp |
| Caption | 12sp | 400 | 16sp |
| Number | 24sp | 500 | 32sp |
| Keypad | 28sp | 400 | 36sp |

The XML dimension resources are the shared source for Compose typography and TextAppearance styles. A text-view inflater applies MaterialTextView line-height support within the existing bridge themes without replacing buttons or other widgets. Programmatically styled UI uses the same roles and preserves existing color state lists. Text appearances resolve primary/secondary colors from the current theme, and tabs retain the primary/secondary selection colors. Search-result references and marker titles/dates use UI roles; their reading excerpts retain reader typography.

The two owned download catalogs receive a scoped CSS style from the app. The adapter checks the exact server scheme, authority, and catalog path. It uses the same nominal role dimensions as CSS pixels and leaves unrelated help pages alone. This keeps the Android implementation reviewable in this repository without a server deployment.

## Coverage

Every user-facing screen family affected by the proposal has a visual pair. Shared components are shown in their main states rather than repeating every possible content item, validation error, language, or scroll position.

| Family | Captured screens and states |
| --- | --- |
| Reader | Modern and legacy toolbars/readers, drawer, appearance panel and font menu, verse popup, cross-reference popup, highlight sheet, selected-verse toolbar |
| Bible navigation | Dialer, typed reference, book grid, book menu; legacy dialer and grid |
| Versions | Manager and selection sheet |
| Search | Empty input/tips, results, filters |
| Settings | Categories, display, usage, copy/share, data transfer, experimental features |
| Account | Sign-in, create account, reset password, legacy sign-in, sync log |
| Bookmarks | Categories, populated bookmarks, notes, and highlights, note editor, bookmark editor, label chooser, populated progress mark list |
| Devotions | Article and source/date selector |
| Songs | Modern and legacy content, keypad, songbook menu, search, media sheet, drawer, download catalog |
| Reading plans | Active day, all days, date/progress view, drawer, download catalog |
| Appearance | Color grid, spectrum, sliders, day/night color settings, font manager |
| Audio | Player bar, recording selector, speed selector, source picker and load log component fixtures |
| Other | About, development feedback sheet, correction instructions/editor, verse report, generic alert, daily verse widget configuration, export progress |
| Legacy/support | PDB conversion options component fixture; developer sync diagnostics |

The audio source picker and audio log use the production Compose components with fixed local state. These are component fixtures, not full playback/backend scenarios. The PDB pair inflates the production dialog XML with fixed sample text and encoding choices; it does not prove a real PDB import. The generic alert uses the production activity with fixture title/message. The daily verse pair covers the configuration screen. Launcher RemoteViews are unchanged. The separate legacy feedback wizard has its own theme and is unchanged.

Reader-controlled verse text, section headings, song/article semantics, note content, inline parsed-reference and match emphasis, diagnostics in monospace, and font previews and custom-drawn icon/badge labels remain specialized. The UI scale does not remove meaningful content formatting. The 200% text-scale layout issues from the original audit are deliberately outside this implementation.

## Verification

The final focused run passed 77 tests across shared typography, native color inheritance, light/dark themes, reader contrast, verse rendering, devotions, and widget behavior. Typography inflation is checked on API 26 and API 34, including version rows, legacy sign-in fields, toolbar titles/subtitles, and tab selection colors. See [unit-test results](unit-tests.json).

The default reader content crop is pixel-identical in the before and after screenshots: 0 changed pixels out of 771,840. This verifies the captured default state; it does not claim exhaustive visual coverage of every reader font and setting. See [reader preservation evidence](reader-preservation.json).

## Evidence

- [Screen definitions, font annotations, and source locations](screens.json)
- [Scale definitions](scale.json)
- [Capture provenance and APK hashes](manifest.json)
- [Report validation](validation.json)
- [Final Android CLI reader layout](reader-layout.json)
- [Before screenshots](before/) and [after screenshots](after/)

Screenshots are unretouched emulator output. Status-bar clocks, build metadata, generated logs, and asynchronous media may differ. Before annotations name size and weight; line height was inherited unless the original source specified it. After annotations include the shared nominal line height. Reading sizes use dp, catalog sizes use CSS px, and all other annotations use sp.

## Reproduction

Use the dedicated QA emulator, not a personal installation: the test-only runner creates local fixtures and changes presentation preferences. It lives in `Alkitab/src/androidTest/java/yuku/alkitab/qa/TypographyCaptureInstrumentation.java` and is not packaged into the production app.

Capture all baseline cases before installing the updated app. The app database version follows versionCode, so reinstalling an older APK after the update cannot reopen that database.

1. Build and retain the original APK before changing production sources. Install it with Android CLI.
2. Build the test APK using `./gradlew assemblePlainDebugAndroidTest 2>&1 | tail -60`.
3. Install the test APK with `adb -s DEVICE install -r Alkitab/build/outputs/apk/androidTest/plain/debug/Alkitab-plain-debug-androidTest.apk`.
4. Run `adb -s DEVICE shell am instrument -w -e phase before yuku.alkitab.debug.test/yuku.alkitab.qa.TypographyCaptureInstrumentation`.
5. Build and install the updated app with Android CLI, then run the same instrumentation command with `-e phase after`.
6. Pull each folder from `/sdcard/Android/data/yuku.alkitab.debug/files/typography/`. The optional instrumentation argument `-e cases name1,name2` repeats selected cases.
7. Run `node build-report.cjs --render` from this comparison folder with Playwright and Chrome available. Set `NODE_PATH` when using an external Node runtime.

Android CLI supplies app installation, launch, and interactive layout inspection. The repeatable screenshot pass uses Android Instrumentation's UiAutomation through adb; this provides an explicit device and exercises the real app UI. The installed Android CLI screenshot command cannot select a device when multiple emulators are online. No emulator root access is required.
