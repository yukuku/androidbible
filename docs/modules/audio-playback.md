# Audio playback

The app supports Bible chapter recordings and song accompaniments. Both support
background playback with Android media controls. Starting one stops the other;
one `AudioService` owns the active playback mode and media session.

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

Opening a targeted passage while Bible audio is active pauses playback and
prepares the destination chapter until Play is pressed. Ordinary chapter browsing
retains autoplay. Opening a Bible passage does not interrupt song playback.

The reader's audio bar provides playback controls, recording selection, speed,
and load diagnostics. Its log helps investigate slow or failed loads. User-visible
service messages must follow the app's selected language.
Diagnostics also appear after five seconds when playback stalls after seeking
to audio that has not loaded, including while offline.

## Song accompaniments

Song audio supports MP3 and MIDI through the shared audio service. Playback state
is shared with the viewer and can outlive the visible activity. Audio attachment
availability comes from the song's metadata; see [Songs](songs.md).

## Ownership and contracts

The [Bible audio package](../../Alkitab/src/main/java/yuku/alkitab/base/audio)
owns recording discovery, chapter resolution, timing, and Bible playback. The
[songs package](../../Alkitab/src/main/java/yuku/alkitab/songs) owns song playback.
Both screens bind to `AudioService`, which exposes Bible and song playback
components. A mode switch stops outgoing playback, cancels its work, installs the
new player in the existing session, then releases the outgoing player. Commands
and callbacks from an inactive component cannot affect the active mode.

The [backend contract](../features/audio-bible/backend-contract.md) specifies
paths, indexing, and response shapes. Earlier PRDs and implementation plans are
historical design records; consult the service and repository tests for current
state transitions and failure behavior.

`AudioService` owns binding, session and notification routing, mode switching,
and Android lifecycle handling. `BibleAudioPlayback` and `SongAudioPlayback`
retain their feature-specific state and player configuration. `AudioPlayback`
provides component coroutine lifetime, diagnostic scheduling and localized logs.
Requested playback that is buffering survives task removal; paused playback
allows the service to stop.
