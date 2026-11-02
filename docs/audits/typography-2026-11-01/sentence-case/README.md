# Sentence-case UI and selected number-field design

The user selected Option A, the outlined number field. The [public sentence-case comparison](https://androidbible-typography-review.itikku.chatgpt.site/sentence-case/) shows the subsequent casing changes. [Earlier Medium-weight evidence and the selected design](../accessibility-revision/README.md) remain available as an identified snapshot.

## Implementation

- Shared text appearances disable uppercase transformations. All app themes use the shared action text appearance for platform and AppCompat controls. Default, colored, and outlined button styles explicitly disable uppercase behavior. Existing colors and backgrounds are retained. Generic alerts and reading-plan/devotion date pickers use the app confirmation label, avoiding the framework English OK resource. Date-picker text is set after native control creation without replacing the platform click handlers.
- Reading-plan date headings and detail actions retain their supplied casing. The legacy feedback finish appearance also disables capitals.
- 175 localized labels or label segments in 26 locale files were changed to sentence case, including confirmation actions, title/label headings, All songbook filters, reference prompts, and color-channel names. Languages without uppercase/lowercase distinctions retain their translations.
- Version-detail field names retain their source casing. Developer section/action labels and the comparison error placeholder no longer use styling capitals.
- Modern song tune and copyright rendering preserve source casing and inline styles. The legacy song stylesheet removes uppercase transforms. Plain-text tune export also retains its source casing.
- The existing exact-origin/path catalog adapter overrides uppercase CSS and normalizes uppercase tab/action labels in the in-app reading-plan and songbook catalogs. Technical terms such as ID and JSON are preserved, as are child elements and click handlers. This requires no server change and does not affect external pages.

Names and technical identifiers retain meaningful casing: Bible/version/songbook names, song suffix letters, testament abbreviations, hex values, diagnostic codes, and catalog identifiers. Reading content is not recased. The 200% text-scale layout work remains excluded.

## Evidence

[Open the comparison report](index.html). It includes nominal font annotations and links to untouched Android PNGs. The captures use the dedicated Android 15 audit AVD at 720 × 1280, 320dpi and 100% system font size, with Indonesian UI. Android CLI installs/launches the APKs; device-specific Instrumentation provides repeatable screenshots.

Eleven baseline states were captured directly from the preceding Medium-weight app before updating it. Additional baselines reuse prior typography snapshots, as labeled in the report. The app database follows versionCode, so the existing installation was not downgraded. The dedicated AVD was cold-restarted after a device stall; its data and display configuration were preserved. Generic-alert and date-picker confirmation wording is checked on API 26/34. The final confirmation-label device recapture could not complete because the emulator timed out during process startup, so that final-label state is not presented as a visual comparison.

The mixed-case song sample is a production-component fixture with fixed source text. It verifies casing in tune, paragraph copyright, and footer copyright without depending on a song publisher's existing letter case. It does not demonstrate a downloaded-song workflow. No sign-in, feedback, verse report, catalog upload/download, or destructive debug action was performed.

- [Screen definitions and font notes](screens.json)
- [Localized label changes](localized-changes.json)
- [Capture provenance](manifest.json)
- [Test results](unit-tests.json)
- [Lint results](lint.json)
- [Browser validation](validation.json)

## Integration with develop

The PR also integrates the current develop branch, retaining its darker Compose container palette and dialog/Note layout fixes. The Note layout uses the current develop hierarchy with the shared typography appearance on its two text fields. Screenshots in this report predate that integration and therefore do not illustrate those inherited changes. [Post-integration checks](integration-validation.json) passed: app/test APK builds, lint, and 46 focused test executions, including two Note checks.

## Regression checks

The shared typography tests check platform, AppCompat, Material, colored, outlined, and dialog action casing on API 26 and API 34 across the app's dark, light, and legacy feedback themes. Resource checks cover all 31 UI locales. Song tests verify that tune/copyright casing and inline italic spans survive rendering, and that copy/share tune text keeps its original case. Existing navigation and song parser/render/export tests remain included in the focused run.

## Reproduce

Build with `./gradlew assemblePlainDebug assemblePlainDebugAndroidTest 2>&1 | tail -60`, then install with Android CLI. Run the test-only `TypographyCaptureInstrumentation` with `-e phase before` or `after`, `-e folder sentence-case-before` or `sentence-case-after`, and the case names in `screens.json`. Pull the corresponding folder from `/sdcard/Android/data/yuku.alkitab.debug/files/typography/`. Use the dedicated QA device because the runner changes presentation preferences and seeds local fixtures.

Regenerate the HTML from the repository root with `python3 docs/audits/typography-2026-11-01/sentence-case/build-report.py`. The separate catalog script check runs the actual injected JavaScript in Chrome and verifies normalized labels, retained identifiers, child icons, and click handlers.
