# Compose controls and native rollback

[Open the current comparison](index.html). It contains 45 before/after screen or state pairs, with raw captures and font annotations. This revision follows the user’s latest scope: keep shared typography styles and Compose refinements, revert direct native XML/View changes, and remove the synthetic keypad cursor.

The previous [proposal](../comparison/README.md), [Medium revision](../accessibility-revision/README.md), and [sentence-case revision](../sentence-case/README.md) are historical snapshots. Their native overrides and drawn cursor no longer describe the current implementation.

## Current implementation

- The hex label is “Warna Hex” in Indonesian. The other UI locales retain their translated hex/color wording with sRGB and the literal # removed. A separate # sits directly before the input, whose text is centered.
- Version details use the production Compose bottom-sheet host. The title is 20sp Medium; short name 16sp Medium; long name and metadata values 16sp Regular; labels/actions 14sp Medium; description 14sp Regular. Information scrolls independently of the action row. A removable installed version offers the trash-icon text action at start and primary share-icon action at end. Preset downloads, available updates, and built-in version protections retain their existing eligibility.
- Compose Bible/song number fields use solid active borders and dashed inactive borders. Replacement-ready values remain quieter than appended input. The drawn cursor is removed. Replacement, append, backspace, validation, suffix and navigation behavior remain unchanged.
- 55 native files match the current develop baseline exactly. This includes the XML layouts, native Goto, native song controls, and programmatic text styling. The native number-field and date-picker helpers are removed. Shared themes, text appearances, keypad styles, and the inflater that supports their line heights remain. Shared translated resources and the owned WebView styles also remain.
- The original native handle interface is preserved. A Compose-only handle adds the replacement-state information needed by the Compose song keypad.

## Evidence and limits

Android CLI installs/launches the app and inspects its UI on the dedicated Typography_Revision_API35 AVD. Instrumentation captures the actual Android windows. Display settings are 720 × 1280, 320dpi, Indonesian app UI and 100% system font size. Public BIMK and KJ content is installed through the app. Marker fixtures are local. No account, feedback, verse-report, deletion or sharing action is submitted.

The baseline is the preceding review build with the develop integration already included. Raw screenshots are untouched. Native font metadata records the base font of actual visible TextViews; inline spans can override individual runs. It covers views in the process windows, including underlying activity text behind modal surfaces. Compose usage annotations use the production typography definitions. The PDB conversion options are an explicitly identified component fixture. The new-note editor is an empty layout state. Two baseline images retain debug-only leak-analysis overlays, and the external verse-popup baseline has a system permission prompt behind it. Per-screen notes identify these; the affected after frames were recaptured cleanly.

After display stalls, the extended capture pass uses the same AVD and data with headless software rendering and guest Vulkan disabled. The 200% layout work remains outside scope. These checks do not claim human low-vision usability testing.

- [Exact native rollback inventory](native-rollback.json)
- [Screen definitions and font notes](screens.json)
- [Capture provenance](manifest.json)
- [Focused tests](unit-tests.json)
- [Lint results](lint.json)
- [Browser checks](validation.json)

## Validation

App and test APK builds, Android lint and 20 focused test executions passed. Shared-style tests run on API 26 and 34. The three sheet tests cover independent remove/share/update/download callbacks, eligibility for installed/preset/built-in versions, start/end action placement, and footer visibility with a long description in a constrained viewport. The callback tests use counters and do not remove or share a file.

To reproduce, use the dedicated QA AVD, build with `./gradlew assemblePlainDebug assemblePlainDebugAndroidTest 2>&1 | tail -60`, install with Android CLI, then run TypographyCaptureInstrumentation with phase, folder and cases arguments. Regenerate HTML with `python3 docs/audits/typography-2026-11-01/controls-revision/build-report.py`. The runner changes local presentation preferences and seeds marker fixtures; use an isolated device.

Report layout verification checks every screenshot against its intrinsic aspect ratio at viewport widths of 1440, 768, 390 and 320 pixels. All 90 current images load without distortion or horizontal overflow. The earlier sentence-case and accessibility reports also pass at desktop and phone widths. The 45 annotated cards were regenerated from the corrected layout. Raw Android captures and their provenance digests remain unchanged.
