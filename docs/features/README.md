# Feature designs and research

Use the [feature guides](../README.md#feature-guides) for current capabilities
and ownership. This directory holds contracts, design rationale, research, and
preview artifacts; a proposal is not evidence that a feature has shipped.

## Contracts and implemented designs

| Document | Role |
| --- | --- |
| [Bible-audio contract](audio-bible/backend-contract.md) | HTTP paths, response shapes, indexing, and error semantics |
| [Bible-audio decisions](audio-bible/design.md) | Playback ownership, recording identity, timing, and follow-up proposals |
| [Portable songs](portable-songs/design.md) | Song document format, legacy compatibility, and separately scoped authoring/backend proposals |
| [Ruby text](ruby/design.md) | Encoding, implemented Compose rendering, source research, and open work |
| [Reader toolbar](toolbar/compose-implementation.md) | Implemented Compose toolbar and its supporting visual artifacts |

The song editor and toolbar playground are preview tools, not a guarantee that
all of their behavior matches the current Android implementation.

## Historical records and research

- [Toolbar audit](toolbar/audit.md): dated measurements and design alternatives;
  use the implementation guide for the resulting toolbar.
- [iOS feasibility survey](ios-port/feasibility-survey.md): historical assessment,
  not a current source inventory or validated delivery estimate.
- [Navigation port decision](../superpowers/specs/2026-05-06-goto-activity-compose-port-design.md):
  compatibility constraints for the implemented Compose navigation screen.
- [Song Room migration decision](../superpowers/specs/2026-05-15-rem-32-room-song-db-design.md):
  database isolation and migration invariants; delivery is recorded in REM-32.

Completed implementation checklists and code sketches have been consolidated
into these guides and decision records. Original plans remain available in Git
history. The [tech-debt index](../tech-debt-remediation.md) owns remaining REM
statuses.
