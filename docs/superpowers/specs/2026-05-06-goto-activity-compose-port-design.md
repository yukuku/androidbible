# Navigation Compose port: decision record

Original design: 2026-05-06. The Compose navigation screen is implemented;
[REM-22](../../tech-debt-remediation/REM-22-jetpack-compose.md) tracks broader
Compose adoption. This record preserves the port's constraints. Current code
and tests define behavior; the original task-by-task plan remains in Git history.

## Purpose and scope

Port the book/chapter/verse picker while preserving its activity result API,
saved tab identity, reference interpretation, and three navigation styles:
Dialer, Direct, and Grid. This establishes shared Compose hosting and theming
without requiring the whole reader to migrate at once.

The screen keeps the legacy navigation alternative through the experimental
setting. Do not infer removal of the legacy fragments or layouts from the
original port plan.

## Compatibility constraints

- Preserve `GotoActivity.createIntent`, `obtainResult`, and result address
  semantics for callers.
- Preserve the saved tab identifiers and the “ask for verse” preference across
  both implementations.
- Keep the active Bible's book list, optional alphabetical ordering, numeric
  bounds, single-chapter book shortcuts, and direct-reference matching.
- Keep tab input and navigation state stable through ordinary configuration
  changes. Avoid leaking preference observers or keyboard/focus ownership.
- Resolve visible strings in the app's selected language and use the shared
  app theme. The early dynamic-color theme sketch is superseded by the app's
  branded theme.

## Ownership and validation

[GotoActivity](../../../Alkitab/src/main/java/yuku/alkitab/base/ac/GotoActivity.kt)
hosts the alternatives. The
[Compose navigation package](../../../Alkitab/src/main/java/yuku/alkitab/base/compose/goto)
owns Compose tabs, candidate matching, and state. Dependency versions belong in
[the catalog](../../../gradle/libs.versions.toml).

Review navigation results, tab restoration, keyboard behavior, book sorting,
invalid/oversized references, and both chapter-only and verse selection when
changing this area. The port record does not assert that every such scenario
has automated UI coverage.
