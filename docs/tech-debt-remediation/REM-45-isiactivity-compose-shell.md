# REM-45: Migrate the IsiActivity Shell to Compose (Staged)

**Status:** Partial, reviewed on 2026-10-15. The verse list and normal reader toolbar default to Compose, with legacy alternatives behind experimental settings. Selection actions use a separate View-based toolbar. Retiring the legacy verse pipeline gates the split-container migration, not every remaining shell component.
**Addresses:** General modernization; completes the reader's Compose migration beyond the verse content view
**Module:** UI (reader)
**BRICE:** B=2 R=1 I=1 C=2 E=5 → **2.2**
**Phase:** 4 — Long-term / Major Refactors

## Background

`VersesComposeControllerImpl` + `VersesComposeView` implement the whole
`VersesController` contract over a `LazyColumn` and are the default for both
reader panes and the lists in `VersesDialog`/`XrefDialog`. The legacy
RecyclerView implementation is an experimental opt-out selected through the
shared `createVersesController` factory.

The normal reader toolbar uses `ReaderToolbar` in a `ComposeView` by default,
with its own legacy-toolbar opt-out. `ReaderSelectionActionMode` drives a
separate View-based `FloatingSelectionToolbar`; it is not a Compose selection
surface. The split container and handle, gestures, back/forward panel, floater,
and drawer also remain View-based, connected through the
interfaces extracted in REM-06/07/08 (`ReaderGestureHost`, `VerseActionModeHost`,
`SplitViewHost`/`SplitViewActions`).

View/Compose interop is a supported migration strategy. Maintaining parallel
implementations adds regression-testing work, but replacing every View is not
itself a correctness fix. The remaining migration has structural constraints:

- **Dual verse pipelines force the container to stay a ViewGroup.** While the
  RecyclerView verse list is still reachable as an opt-out,
  `TwofingerLinearLayout` must be able to host either pane type. Making the
  container Compose would push that path into `AndroidView` wrappers. The
  container can only be composified once the RecyclerView verse pipeline is
  retired outright.
- **The split trio is tightly coupled.** `SplitViewManager`,
  `LabeledSplitHandleButton`, and `TwofingerLinearLayout` share view-level pixel
  math (explicit `LayoutParams` sizing during handle drags, orientation
  switching, a global-layout listener) and View touch-interception semantics
  (one-finger chapter swipe, two-finger scale/drag over the scrolling panes).
  A Compose port is a redesign (fraction-based layout + custom gesture
  arbitration over two `LazyColumn`s), not a translation.
- **Normal and selection toolbars are separate surfaces.** `ReaderToolbar`
  can coexist with the View-based selection toolbar, whose callbacks still
  use `VerseActionModeController` through `ReaderSelectionActionMode`.
  Completing selection UI migration requires its own interaction and layout
  checks; the normal toolbar port does not complete that work.

## Staged plan

1. **Stage 0: bake the Compose verse list (prerequisite for retirement).**
   The Compose verse list is the default, so this stage is about the feedback
   that arrives on it: split-scroll sync feel, fling behavior, TalkBack,
   drag-and-drop across devices. Exit criterion for moving on: no reason left
   to keep the legacy opt-out around.
2. **Stage 1: retire the legacy verse implementation.** Remove the legacy
   branch in `createVersesController` once the opt-out is safe to retire.
   Reader panes and `VersesDialog`/`XrefDialog` share this factory and already
   default to Compose; they do not need separate initial ports. Retiring the
   reader's legacy path unlocks stage 2.
3. **Stage 2: split container, handle, and reader gestures as one project.**
   Replace `TwofingerLinearLayout` + `LabeledSplitHandleButton` with a Compose
   split layout; rework `SplitViewManager` to a fraction-based model; port
   `ReaderGestureHandler`'s one-finger swipe and two-finger scale/drag into
   Compose pointer input with explicit arbitration against the pane scrolling.
   Highest-risk stage; needs its own test plan (gesture + split proportion
   persistence + inset behavior).
4. **Stage 3: small overlays, opportunistically.** The back/forward panel
   (`BackForwardListController`'s two buttons) is a trivial conversion; the
   `Floater` (custom-drawn drag target fed by `ReaderGestureHandler`) is a
   moderate one. Either can also be folded into stage 2 if convenient.
5. **Stage 4: finish toolbar and selection migration (partial).** The normal
   `ReaderToolbar` port is complete and covered by `ReaderToolbarComposeTest`
   and toolbar layout tests. Remaining work is the selection surface driven
   by `ReaderSelectionActionMode` / `VerseActionModeController`, plus retiring
   the legacy normal toolbar after its opt-out is no longer needed. Validate
   selection actions, preferred toolbar edge, fullscreen text clearance, and
   popup positioning. This work can proceed independently of stage 2.
6. **Stage 5: left drawer.** `LeftDrawer` works fine via interop today
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

**Difficulty:** Hard (multi-sprint). Stages 0/1 gate stage 2; independent
toolbar and overlay work can proceed separately. Each stage should be
independently shippable.

---

[← Back to remediation index](../tech-debt-remediation.md)
