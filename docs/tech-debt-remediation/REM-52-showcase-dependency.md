# REM-52: Review the FancyShowCaseView Dependency

**Module:** Reader onboarding and contextual tips
**Status:** Open; evaluate whether replacement is warranted.
**BRICE:** Not scored; confirm maintenance and compatibility needs first.

## Problem

The app depends on `FancyShowCaseView` for contextual reader tips. Low maintenance activity is a dependency concern, and the tips attach to the View hierarchy while the reader has both Compose and View surfaces. The version catalog in `gradle/libs.versions.toml` is the dependency-version source of truth.

## Remaining work

1. Review upstream maintenance and Android compatibility, then inventory the app's tip call sites.
2. Decide whether to retain the dependency, replace it, or implement the small required tip surface locally. Preserve dismissal/understood preferences, accessibility, and anchor positioning.
3. Coordinate any replacement with [REM-45](REM-45-isiactivity-compose-shell.md), including fullscreen, toolbar placement, and mixed View/Compose anchors.
4. If replacing it, remove the dependency after all callers migrate and verify first-run and already-dismissed behavior.

Other dependency replacements have their own completed records: [REM-03](REM-03-localbroadcastmanager.md), [REM-12](REM-12-itemtouchhelper.md), [REM-14](REM-14-material-dialogs.md), [REM-19](REM-19-prdownloader-replacement.md), and [REM-20](REM-20-ambilwarna-replacement.md). The percent-layout cleanup belongs to [REM-22](REM-22-jetpack-compose.md).

---

[← Back to remediation index](../tech-debt-remediation.md)
