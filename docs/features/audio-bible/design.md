# Bible audio: design decisions

This consolidates the initial audio PRD, client plan, multiple-recording design,
and shared-playback implementation plan. Their delivered architecture is
summarized here; task-by-task code sketches and dependency setup belong in Git
history. See [Audio playback](../../modules/audio-playback.md) for capabilities
and ownership and the [HTTP contract](backend-contract.md) for wire details.

## Playback ownership

A foreground media-session service owns each player so playback survives reader
navigation and backgrounding and participates in lock-screen and Bluetooth
controls. Activities observe playback and send commands; they do not own the
player's lifetime.

Bible and song services share `AudioPlaybackCoordinator`. Acquiring playback
stops the previously active logical session. Explicit coordination is necessary
because Android audio focus alone does not enforce the app's one-session rule.
Song MP3 and MIDI use the same service/player path.

## Recording identity and availability

A Bible version can have multiple recordings. A recording has an opaque audio
ID, display title, book coverage, and optional verse timing. Availability is
queried per version so the client only requests the content it needs. There is
no bundled recording catalog; a backend failure can hide the entry point until
availability is retried.

Version identity, recording identity, and chapter address are separate. Store
recording choices per version and include the recording ID in any future audio
cache key. The backend resolves upstream URLs and normalizes timing, keeping
origin-specific layouts out of the Android client.

The built-in Bible obtains its preset identity from flavor configuration. That
identity enables the same recording lookup used for downloaded versions. A
user-imported version without a preset identity does not acquire audio merely
because its language or title resembles a catalog version.

## Timing and navigation

Chapter playback works without verse timing. Verse-specific starting positions,
verse following, and verse controls require timing for the chosen recording and
coverage for the current book. UI availability must reflect those capabilities.
System next/previous transport commands navigate chapters, including across
book boundaries.

Parallel reading has one audio source at a time. Interleaving verse recordings
from two versions was excluded because it interrupts continuous listening.
Reader panes observe the chosen playback rather than starting competing players.

## Failure and appearance boundaries

Recording discovery, timing retrieval, player loading, and visible reader state
can fail independently. Distinguish an empty recording list from an unsuccessful
request internally, and permit recovery after transient failure. Diagnostic logs
help locate a failed load; they do not establish that playback succeeded.

Audio UI follows the applied reader appearance. Service-produced text must use
the in-app language rather than the device locale. Playback observation and
split-view wiring fixes are recorded in
[REM-37](../../tech-debt-remediation/REM-37-bible-audio-state.md).

## Follow-up ideas from the original plans

These are retained proposals, not a delivery commitment or evidence of support:

- Explicit offline downloads with progress, cancellation, size reporting, and
  deletion. Key audio and timing by version, recording, book, and chapter; select
  a maintained download mechanism when implementing it.
- Reading-plan playback, Android Auto/Wear integration, and casting, each needing
  its own behavior and device validation.
- More visible narrator/publisher attribution in the recording picker when the
  backend provides suitable metadata.

Changes need device checks for background playback, media controls, interruption,
chapter boundaries, recording selection, timing-free audio, and failed-network
recovery, alongside repository and playback-state tests.
