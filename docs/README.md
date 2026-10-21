# Documentation

For app development, start with [Architecture](architecture.md),
[Build environment setup](build-environment.md), and [Build system](build-system.md).
[AGENTS.md](../AGENTS.md) contains contributor and coding-agent conventions.

To create Bible content or integrate another app, use the
[content and integration guides](developer/README.md).

## Technical guides

| Guide | Scope |
| --- | --- |
| [Architecture](architecture.md) | Build modules, application services, reader boundaries, and verse identity |
| [Storage](storage.md) | Persistence ownership, migration constraints, preferences, and files |
| [Backend communication](backend-communication.md) | Network responsibilities, origins, and Firebase |
| [Text rendering](text-rendering.md) | Compose and legacy presentation, formatting, and contrast |
| [Binary formats](binary-formats.md) | YES2, built-in Bible data, Bintex, and RPB encoding |
| [Build system](build-system.md) | Build variants, versioning, signing, CI, and artifact creation |
| [Build environment setup](build-environment.md) | Local toolchain and SDK setup |
| [Google Play publishing](play-publishing.md) | Store uploads, tracks, and listing metadata |
| [Tools](tools.md) | Desktop content converters and supporting scripts |

## Feature guides

These describe capabilities and ownership. Detailed algorithms, UI dimensions,
exact schemas, and default values belong in code and tests or the linked format
contracts.

- [Bible versions](modules/versions.md)
- [Bookmarks, notes, and highlights](modules/markers.md)
- [Bible search](modules/search.md)
- [Reading aids](modules/reading.md): reading plans, devotions, and the daily verse widget
- [Songs](modules/songs.md)
- [Audio playback](modules/audio-playback.md)
- [Sync and support logs](modules/sync.md)
- [User-data backup and import](modules/data-transfer.md)

## Designs and work records

The [tech-debt backlog](tech-debt-remediation.md) is the status index for open,
partial, and completed REM work. Completed records describe their delivery
scope, not necessarily today's exact implementation.

[Feature designs](features/README.md) separate current contracts and research
from historical plans and link earlier decisions retained from completed work.

## Keeping docs useful

Give each fact one home and link to it from other guides. Keep feature guides
at the capability and boundary level. Preserve precision for external file and
API contracts, where another implementation needs it. Record the date and scope
of historical research; do not present an old plan as current architecture.
Read dependency and SDK versions from the version catalog instead of copying
them into long-lived prose.
