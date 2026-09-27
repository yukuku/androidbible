# Reading Plan 2.0

Review date: 2026-09-27.

This folder records the Reading Plan UX audit and a proposed implementation
backlog. The goal is to make choosing a plan, continuing a reading, recording
completion, and returning after a break understandable and consistent.

- [Audit and evidence](audit.md): observed behavior, source findings,
  reproduction steps, screenshots, and limits of the review.
- [Prioritized backlog](backlog.md): stable tracking IDs, proposed changes,
  dependencies, and acceptance criteria.
- [Existing module documentation](../../modules/reading-plans.md): background on
  storage and the plan format. Its additive-only progress description does not
  match the implemented ability to uncheck readings; see the audit.

## Proposed delivery order

| Priority | Focus | Items |
| --- | --- | --- |
| P1 | Truthful progress, consistent reading actions, and state preservation | RP2-001 through RP2-004 |
| P2 | Navigation, completion feedback, management, discovery, and accessibility | RP2-005 through RP2-010 |
| Validation | Resolve uncertainties and protect compatibility | RP2-011 |
| Deferred | Optional product expansion | RP2-012 |

Priorities reflect user impact and confidence in the evidence, not effort
estimates or a committed release schedule. Start with RP2-001 and RP2-004,
which address directly reproduced problems. Define the shared passage behavior
in RP2-003 before integrating reader completion in RP2-002 and the full schedule
in RP2-007. Use RP2-011 throughout delivery, not only at the end.

## Tracking conventions

All entries start as **Proposed**. Publishing this documentation does not mean
that a recommendation is approved, implemented, or validated with users.

Keep each RP2 ID stable. Update the status in the backlog's summary table, link
the implementation PR, and record any acceptance evidence beside the entry.
Use **Accepted**, **In progress**, **Blocked**, **Done**, or **Deferred** as work
progresses. A blocked item must name its dependency. Mark an item Done only
when its acceptance criteria have been verified; record the completion date.
Do not renumber or erase entries when priorities change.

The audit distinguishes **Observed**, **Source-verified**, **Interpretation**,
and **Unconfirmed** evidence. Proposed behavior belongs in the backlog; do not
rewrite historical observations to make them describe the redesigned screen.
When new tests contradict an observation, add dated evidence and explain the
changed conclusion.

## Boundaries

This is documentation-only work. It changes no UI, public API, database schema,
reading-plan binary format, or sync protocol. The backlog preserves explicit
user-controlled completion and existing progress. Backend catalogue work is
identified separately and is not implemented in this repository by this PR.
An undated mode and native catalogue rewrite remain deferred proposals.
