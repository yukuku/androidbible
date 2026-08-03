# REM-45: Migrate the IsiActivity Shell to Compose (Staged)

**Status:** Planned — gated on the Verse (Compose) experimental verse list being promoted to default (see stage 0/1 below).
**Addresses:** General modernization; completes the reader's Compose migration beyond the verse content view
**Module:** UI (reader)
**BRICE:** B=2 R=1 I=1 C=2 E=5 → **2.2**
**Phase:** 4 — Long-term / Major Refactors

## Background

The verse content view already has a fully Compose-based implementation behind the
"Verse (Compose)" experimental setting (2026-08): `VersesComposeControllerImpl` +
`VersesComposeView` implement the whole `VersesController` contract over a
`LazyColumn`, swapped in place of the two `EmptyableRecyclerView` panes. The rest
of the reader — the split container and handle, gestures, toolbar/action mode,
back/forward panel, floater, drawer — is still View-based, connected through the
interfaces extracted in REM-06/07/08 (`ReaderGestureHost`, `VerseActionModeHost`,
`SplitViewHost`/`SplitViewActions`).

This mixed state is *not* itself debt: View/Compose interop is the supported
migration strategy, and the existing seams are exactly what makes the remaining
migration cheap. The reason to stage the rest carefully is structural:

- **Dual verse pipelines force the container to stay a ViewGroup.** While the
  RecyclerView verse list is the default and the Compose list is an experiment,
  `TwofingerLinearLayout` must be able to host either pane type. Making the
  container Compose first would push the *default* RecyclerView path into
  `AndroidView` wrappers — destabilizing production to serve an experiment.
  The container can only be composified after the Compose verse list becomes
  the default and the RecyclerView verse pipeline is retired.
- **The split trio is tightly coupled.** `SplitViewManager`,
  `LabeledSplitHandleButton`, and `TwofingerLinearLayout` share view-level pixel
  math (explicit `LayoutParams` sizing during handle drags, orientation
  switching, a global-layout listener) and View touch-interception semantics
  (one-finger chapter swipe, two-finger scale/drag over the scrolling panes).
  A Compose port is a redesign (fraction-based layout + custom gesture
  arbitration over two `LazyColumn`s), not a translation.
- **The toolbar drags the action mode with it.** The verse selection UI is an
  AppCompat `ActionMode` (`VerseActionModeController`) rendered into the
  toolbar chrome; a Compose toolbar means rebuilding that whole surface.

## Staged plan

1. **Stage 0 — bake the Compose verse list (prerequisite, not this task).**
   Gather feedback on the experimental setting; verify split-scroll sync feel,
   fling behavior, TalkBack, drag-and-drop across devices. Exit criterion for
   moving on: the Compose verse list is promoted to the default.
2. **Stage 1 — retire the RecyclerView verse pipeline in the reader.** Remove
   the `IsiActivity` flag branch and the RecyclerView-based reader panes
   (`VersesDialog`/`XrefDialog` keep `VersesControllerImpl` until ported
   separately). One verse pipeline in the reader unlocks every later stage.
3. **Stage 2 — split container, handle, and reader gestures as one project.**
   Replace `TwofingerLinearLayout` + `LabeledSplitHandleButton` with a Compose
   split layout; rework `SplitViewManager` to a fraction-based model; port
   `ReaderGestureHandler`'s one-finger swipe and two-finger scale/drag into
   Compose pointer input with explicit arbitration against the pane scrolling.
   Highest-risk stage; needs its own test plan (gesture + split proportion
   persistence + inset behavior).
4. **Stage 3 — small overlays, opportunistically.** The back/forward panel
   (`BackForwardListController`'s two buttons) is a trivial conversion; the
   `Floater` (custom-drawn drag target fed by `ReaderGestureHandler`) is a
   moderate one. Either can also be folded into stage 2 if convenient.
5. **Stage 4 — toolbar and verse action mode.** Replace the AppCompat toolbar
   (Goto button, prev/next chapter, version button, audio slot) and rebuild the
   selection action surface currently provided by `startSupportActionMode` /
   `VerseActionModeController`. Largest user-visible surface; last on purpose.
6. **Stage 5 — left drawer.** `LeftDrawer` works fine via interop today
   (including the progress-pin `startDragAndDrop` source feeding the Compose
   drop targets); port it only once the rest of the shell is Compose.

## Risks

- Stage 2 touches scroll-sync and gesture behavior that users feel immediately;
  regressions there are worse than keeping Views longer. Do not combine it with
  unrelated reader changes.
- Edge-to-edge inset handling in `IsiActivity.setupSafeAreaInsets` is
  View-listener-based and was tuned per-edge (toolbar location, audio bar,
  stacked split); each stage that absorbs a view must re-verify cutout/fullscreen
  behavior.
- `TextAppearancePanel` overlays and `FancyShowCaseView` tips attach to the View
  hierarchy; they need interop hosts (or their own ports) as the shell shrinks.

**Difficulty:** Hard (multi-sprint, sequential). Each stage should land
independently shippable, with the previous stage settled first.

---

[← Back to remediation index](../tech-debt-remediation.md)
