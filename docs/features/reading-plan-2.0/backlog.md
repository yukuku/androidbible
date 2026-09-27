# Reading Plan 2.0 backlog

Created: 2026-09-27. All priorities and behaviors below are proposals, not
shipped changes. See the [audit](audit.md) for evidence and the
[tracking conventions](README.md#tracking-conventions) for status updates.

See the [English and Indonesian mockups](mockups.md) for the visual direction.
Familiarity is a requirement: preserve recognizable navigation and reading
controls, and validate the transition with existing users in both languages.
Mockups do not change the Proposed status of these entries.

## Tracking summary

| ID | Priority | Status | Change | Dependency | Implementation PR |
| --- | --- | --- | --- | --- | --- |
| RP2-001 | P1 | Proposed | Truthful daily and overdue progress | None | Not started |
| RP2-002 | P1 | Proposed | Reader session and explicit completion | RP2-003 interaction contract | Not started |
| RP2-003 | P1 | Proposed | Consistent passage actions | None | Not started |
| RP2-004 | P1 | Proposed | Preserve browsing state | None | Not started |
| RP2-005 | P2 | Proposed | Reading-first screen hierarchy | RP2-001; coordinate with RP2-002 | Not started |
| RP2-006 | P2 | Proposed | Day and plan completion states | RP2-001 and RP2-002 | Not started |
| RP2-007 | P2 | Proposed | Dedicated All days browser | RP2-003 and RP2-004 | Not started |
| RP2-008 | P2 | Proposed | Clear plan management and recovery | RP2-001; coordinate with RP2-005 | Not started |
| RP2-009 | P2 | Proposed | Catalogue comparison and preview | Backend/content coordination | Not started |
| RP2-010 | P2 | Proposed | Localization and accessibility | Coordinate with RP2-005 and RP2-007 | Not started |
| RP2-011 | Validation | Proposed | Compatibility and uncertainty checks | Run alongside relevant items | Not started |
| RP2-012 | Deferred | Proposed | Undated mode and native catalogue | Separate product decisions | Not started |

P1 addresses the core reading loop and reproduced correctness problems. P2
improves navigation, comprehension, and access. Validation is a release gate
where applicable, not lower-priority optional testing. Deferred denotes scope
priority; the initial status remains Proposed until that disposition is accepted.

## RP2-001: Truthful daily and overdue progress

**Evidence:** A01 and source-only date/completion findings.
**Subsystem:** plan progress calculations and summary.
**Rationale:** a newly started plan should not immediately report missed work.

**Proposed behavior:** show completed/total readings for the selected day and a
compact overall assigned-reading count. Count unfinished assignments strictly
before today's scheduled day as overdue; today's unfinished assignments are
due today. Readings completed ahead do not cancel earlier missed assignments.
Label overall units as readings, not chapters, days, verses, or minutes.

**Acceptance criteria:**

- A fresh Gospel plan shows 0 of 3 daily readings complete without overdue
  feedback. Completing one updates both daily and overall counts accurately.
- A partially completed earlier day remains overdue even if enough future
  readings are checked to match the aggregate target.
- Browsing another date does not redefine today's overdue boundary.
- A future start has a not-started state and no overdue work. After the schedule
  ends, remaining work is identified without pretending its last date is today.
- Empty-reading days and zero denominators produce valid, understandable states.
  Check/uncheck operations remain reversible.

## RP2-002: Reader session and explicit completion

**Evidence:** A02. **Subsystem:** reader handoff, session persistence, progress.
**Rationale:** users currently have to remember the assignment and return to
record completion separately.

**Proposed behavior:** retain plan identity, day, and passage sequence alongside
the assigned range. Present a compact session control identifying the range and
position within the day. Offer explicit completion and continuation to the next
unfinished passage in the same day. After its last unfinished passage, show the
daily completion state. Leaving the session does not mark anything complete.

**Acceptance criteria:**

- Single- and multi-chapter assignments show the complete assigned range while
  reading; the session does not obscure normal Bible controls or verse content.
- Completing a passage updates exactly its originating plan entry once and
  offers the correct next unfinished passage. Opening, scrolling, and dismissing
  a session do not change completion.
- Identical ranges in different plans cannot update the wrong plan. Switching
  plans and removing the session's plan are handled without stale writes.
- Back preserves the selected plan day. Recreation restores the session identity;
  a stale or missing entry ends the session safely.
- Existing range-only saved state remains readable or is safely dismissed;
  it must not be guessed into a plan completion entry.

## RP2-003: Consistent passage actions

**Evidence:** A03. **Subsystem:** daily and full-schedule passage rows.
**Rationale:** tapping the same reference currently means reading in one place
and changing progress in another.

**Proposed behavior:** reference text opens the passage everywhere; a separate,
consistently positioned completion control toggles its state. Keep an explicit
bulk action with a scope-specific confirmation instead of relying on discovery
through long-press. Provide undo for bulk changes, restoring the previous marks.

**Acceptance criteria:**

- Daily and schedule reference taps open reading and never toggle completion.
- Completion controls update only their labeled passage and can be unchecked.
- Bulk confirmation names the selected endpoint. Cancel changes nothing, and
  undo restores the exact prior state rather than clearing previously read work.
- Touch and accessibility activation produce the same actions.

## RP2-004: Preserve browsing state

**Evidence:** A07. **Subsystem:** activity and navigation state restoration.
**Rationale:** rotation currently changes the user's selected task.

**Proposed behavior:** preserve selected plan/day, expanded schedule or successor
navigation destination, and scroll position through recreation. Restore against
the current plan data; if a plan is unavailable, show a safe available state.

**Acceptance criteria:**

- Day 2 with the schedule expanded stays on Day 2 and at the same position after
  rotating in both directions and after activity recreation.
- Font-size changes do not reset the selected day or destination.
- Process restoration handles valid saved state and a removed plan without a
  crash or misleading passage selection.

## RP2-005: Reading-first hierarchy and navigation

**Evidence:** A05 and A02. **Subsystem:** plan home screen and switcher.
**Rationale:** toolbar management and statistics crowd out plan identity and
continuation.

**Proposed behavior:** give the plan title room to wrap outside constrained
toolbar space; keep a clear switcher and secondary management menu. Present day
number/total, date, daily completion, a named Continue action, and passage rows.
Keep Today and All days visible; make overall progress secondary. Continue
selects the earliest unfinished assignment without silently rescheduling it.
When it differs from today, explain the distinction and retain access to today.

**Acceptance criteria:**

- Long titles remain available in full without opening the drawer.
- A returning reader can continue the first unfinished passage or open today's
  schedule directly, without using the date popup.
- Day identity, daily progress, and primary action precede overall statistics.
- Switching plans updates title, passages, counts, and actions consistently.
- Download and removal no longer consume two always-visible toolbar actions.
  Retain the familiar download wording in the overflow menu and keep the plan
  switcher immediately visible near the top. Preserve arrows and the date menu.

## RP2-006: Day and whole-plan completion states

**Evidence:** A04; whole-plan behavior is source-only and needs validation.
**Subsystem:** plan and reader completion presentation.
**Rationale:** aggregate on-schedule feedback does not clearly acknowledge the
completed task.

**Proposed behavior:** explicitly confirm when the day's assignments are complete,
with an optional next-day action. Do not automatically advance away. When no
assignments remain unread, show a completed-plan state instead of routing First
unread to the final day. Allow review and unchecking; no streak feature is required.

**Acceptance criteria:**

- Completing the last unfinished daily passage produces a daily completion state.
- Completing the entire plan produces a distinct state with no false unread action.
- Unchecking a passage restores appropriate incomplete state and navigation.
- Scheduled empty/rest days do not falsely signal completion of the whole plan.

## RP2-007: Dedicated All days browser

**Evidence:** A03. **Subsystem:** schedule browsing.
**Rationale:** appending the entire schedule below statistics duplicates content
and makes long plans harder to navigate.

**Proposed behavior:** replace inline expansion with an All days destination.
Rows show day/date and complete, partial, or unread status with counts. Opening a
day shows its assignments. Provide Today, First unread, and a day-number jump;
a calendar remains secondary navigation. Preserve position when returning.

**Acceptance criteria:**

- The daily-screen entry retains Tampilkan daftar baca in Indonesian and uses
  the clearer Show reading list in English. Back returns to the previous day
  and scroll position.
- A long plan can jump to a requested valid day without repeated arrows or
  scrolling from the beginning. Out-of-range input is explained.
- Status accurately represents partial days; passage actions follow RP2-003.
- Returning from a day restores the browser position; rotation follows RP2-004.
- A completed plan has no misleading First unread destination.

## RP2-008: Clear management and recovery

**Evidence:** A05 and source-only management semantics.
**Subsystem:** menus, confirmations, and schedule changes.
**Rationale:** rescheduling, restarting, and removing have different effects but
are scattered and insufficiently explained.

**Proposed behavior:** group About, Adjust schedule, Restart, and Remove downloaded
plan under plan management. Offer moving the first unfinished day to today with
a preview of the new schedule and finish date, stating that checked readings
remain checked. Explain that removal retains progress, while Restart clears it
and starts again. Use action-specific confirmation labels. Show descriptive plan
information outside the drawer; keep the internal ID secondary.

**Acceptance criteria:**

- Schedule adjustment preserves all marks and clearly previews date changes.
- During the transition, existing date-menu recovery and drawer Restart actions
  remain aliases to the same new dialogs. Later removal requires usability
  validation with existing users, rather than disappearing in the first revamp.
- Restart and removal explain their different consequences before confirmation;
  cancelling either changes nothing.
- Removing then downloading a plan preserves its existing completion behavior;
  restart deliberately clears completion.
- Completed plans do not offer a recovery action that invents an unread day.

## RP2-009: Catalogue comparison and plan preview

**Evidence:** A06. **Subsystem:** hosted catalogue, content, and Android handoff.
**Rationale:** large descriptions and IDs make comparison difficult, and a tap
starts a plan before a deliberate setup step.

**Proposed behavior:** use concise entries with title, duration, coverage, and
workload where supported by actual data. Keep full descriptions and IDs in details
and preserve ID search. Preview a sample day and choose the starting date before
an explicit Start plan action. Localize curated presentation where available;
do not claim user-authored descriptions are translated. Never invent time estimates.

**Dependencies:** coordinate with `yukuku/alkitab-host` for the hosted catalogue
and available metadata. Read that repository's instructions before subsequent
work there. This documentation PR neither changes it nor specifies an invented
API. A native catalogue rewrite is deferred.

**Acceptance criteria:**

- Selecting an entry shows a preview rather than immediately starting a plan.
- The user can inspect a sample day and start date before confirming.
- Missing workload/translation data has an honest fallback without guessed values.
- Existing ID links remain usable. Already-downloaded, offline, failed-download,
  and invalid-data paths preserve user progress and offer a clear next action.

## RP2-010: Localization, flexible text, and accessibility

**Evidence:** A08. **Subsystem:** date formatting, layout, and semantics.
**Rationale:** the observed date uses a different language format, and larger
text crowds the navigation area.

**Proposed behavior:** format dates using the selected app language. Give the
navigation area and passage rows flexible height, retaining usable touch targets.
Label every completion control with its passage and checked state. Convey status
with text as well as color; test real announcements and contrast before asserting
accessibility compliance.

**Acceptance criteria:**

- Indonesian and English app language choices produce corresponding dates even
  when the device language differs.
- At 100%, 150%, and 200% text sizes, day/date and long reference labels remain
  readable without clipping or overlapping controls, in portrait and landscape.
- TalkBack identifies each passage action and checkbox state, with coherent
  traversal. Keyboard navigation reaches all essential actions.
- New controls retain at least 48 dp touch targets. Rendered status remains
  understandable without relying on red/blue color distinctions.

## RP2-011: Validation and compatibility work

**Evidence:** audit limitations and unconfirmed/source-only findings.
**Subsystem:** test coverage across plan, reader, storage, and sync.
**Rationale:** the emulator session does not establish all edge cases or production
compatibility. This work accompanies the relevant implementation item.

**Acceptance checklist:**

- Revisit the suspected start-date mismatch with genuinely changed dates and
  non-today selections. File a defect only with reproducible evidence; preserve
  the audit's current not-reproduced conclusion until then.
- Test a clean-install empty state, cancelled catalogue navigation, offline
  installed plans, download failures, invalid data, and duplicate downloads.
- Cover future starts, ended schedules with gaps, complete plans, partial days,
  empty/rest days, clock/date changes, and out-of-order completion.
- Verify app-language/device-language differences, long titles/references,
  TalkBack, landscape, and process restoration.
- Verify progress survives upgrade, removal/re-download, schedule adjustment,
  and interrupted reading. Check/uncheck and restart must synchronize correctly
  across devices in a suitable production-configured test environment.
- Preserve compatibility with existing plan binaries and completion identities;
  any necessary migration requires an explicit design and migration test.
- Correct the module documentation's additive-only claim after checking actual
  sync behavior. Record dated results and link implementation PRs; do not turn
  untested expectations into observed findings.

## RP2-012: Deferred product expansion

**Subsystem:** schedule model and catalogue architecture.
**Rationale:** neither a new scheduling model nor a native rewrite is necessary
to fix the demonstrated problems.

Keep an undated, at-your-own-pace mode and a native catalogue outside the initial
revamp. Reconsider each separately with user need, compatibility implications,
and scope recorded before implementation. No backend contract, migration,
reminder system, or streak system is implicitly authorized by this backlog.

**Acceptance criterion:** initial delivery does not depend on either expansion;
any later adoption receives a separate accepted proposal and tracking entry.
