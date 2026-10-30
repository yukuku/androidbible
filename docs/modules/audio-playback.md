# Audio playback

The app supports Bible chapter recordings and song accompaniments. Both support
background playback with Android media controls. Starting one stops the other;
the shared playback coordinator enforces a single active audio session.

## Bible recordings

The reader offers recordings for supported Bible versions, including multiple
recordings with different narrators, book coverage, or verse timing. The chosen
recording is remembered per version. Availability comes from the backend's
per-version audio-set endpoint; there is no bundled audio catalog fallback.

Chapter audio can continue across chapters through the media service. When a
recording has verse timing, playback can highlight and follow the active verse
and start at a requested verse. Recordings without timing still support chapter
playback. Verse-based actions depend on timing and book coverage, rather than
merely on the presence of audio for that version.

The reader's audio bar provides playback controls, recording selection, speed,
and load diagnostics. Its log helps investigate slow or failed loads. User-visible
service messages must follow the app's selected language.

## Song accompaniments

Song audio supports MP3 and MIDI through the song media service. Playback state
is shared with the viewer and can outlive the visible activity. Audio attachment
availability comes from the song's metadata; see [Songs](songs.md).

## Ownership and contracts

The [Bible audio package](../../Alkitab/src/main/java/yuku/alkitab/base/audio)
owns recording discovery, chapter resolution, timing, and Bible playback. The
[songs package](../../Alkitab/src/main/java/yuku/alkitab/songs) owns song playback.
Both use Media3 players in foreground media-session services and participate in
`AudioPlaybackCoordinator`.

The Compose controls observe the bound service's player through Media3 Compose
UI progress state holders. Transport actions still go through the feature's
controller and service so playback coordination, verse previews, retry logs,
and song repeat behavior stay consistent. UI observation ends when the controls
are dismissed; background playback remains owned by the service.

The [backend contract](../features/audio-bible/backend-contract.md) specifies
paths, indexing, and response shapes. Earlier PRDs and implementation plans are
historical design records; consult the service and repository tests for current
state transitions and failure behavior.
