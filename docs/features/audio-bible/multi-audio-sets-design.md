# Multiple Audio Sets per Bible Version — Client Design

**Status:** Design, ready for implementation
**Scope:** `Alkitab` app. Backend counterpart: `yukuku/alkitab-host` →
`AUDIO_MULTI_SET.md`.

Supersedes the catalog sections of [prd.md](prd.md) §5.3–§5.5 and
[client-plan.md](client-plan.md). Everything else in the PRD — the foreground
`MediaSessionService`, the Compose audio bar, verse highlighting, split-view
single-source behavior — is unaffected and stands as written.

The audio feature has not shipped, so this is a **clean break**. No migration,
no compatibility shim, no reading of the old cache format.

---

## 1. What changes, and why

A Bible version currently maps to exactly one recording. It needs to map to
several: SABDA publishes multiple recordings per version, differing in narrator,
book coverage, and whether verse timing exists. `preset/in-tb` alone has four.

Two structural changes follow.

**Audio sets become a first-class dimension.** Every address that is
`(versionId, bookId, chapter_1)` today becomes
`(versionId, audioId, bookId, chapter_1)`. The user picks which recording to
hear, and that choice sticks per version.

**The global catalog is deleted.** `AudioCatalogRepository` downloads a document
describing *every* audio-capable version, cached to `files/audio_catalog.json`
with a bundled `assets/audio_catalog.json` fallback. That does not scale: there
are 94 upstream presets, 44 of which match real app versions, each with a
growing number of sets and a per-set book-coverage list. The app only ever needs
the version in front of the user.

It is replaced by a per-version query — `GET /audio/sets/<preset>` — resolved
lazily and cached per version.

## 2. Backend contract

Full specification in `alkitab-host` → `AUDIO_MULTI_SET.md` §3. The client's
view of it:

| Endpoint | Purpose |
|---|---|
| `GET /audio/sets/<preset>` | Which recordings exist for one version, with per-set book coverage |
| `GET /audio/file/<preset>/<audioId>/<book_1>/<chapter_1>.mp3` | Chapter audio, proxied and edge-cached |
| `GET /audio/timing/<preset>/<audioId>/<book_1>/<chapter_1>.json` | Normalized verse timing |

The app knows nothing about the upstream host. It receives URL *templates* from
`/audio/sets/*` and expands them; it never assembles a media path from parts.

**Book and chapter are 1-based in every audio URL**, matching the backend and
its upstream so a URL can be read against its origin by eye. The app's `Ari`
`bookId` is 0-based, so the conversion is `book_1 = bookId + 1`, done once in
`BibleAudioRepository` and nowhere else. The `_1` suffix is the codebase's
existing marker for 1-based values (`chapter_1`, `verse_1`).

Response shape:

```json
{
  "schema": 2,
  "preset": "in-tb",
  "sets": [
    { "audioId": "alkitabsuara", "title": "Alkitab Suara", "hasTiming": true,
      "books_1": [1, 2, "…", 66],
      "mp3UrlTemplate": "/audio/file/in-tb/alkitabsuara/{book_1}/{chapter_1}.mp3",
      "timingUrlTemplate": "/audio/timing/in-tb/alkitabsuara/{book_1}/{chapter_1}.json" }
  ]
}
```

`sets` is ordered; `sets[0]` is the default. An empty `sets` array means "no
audio for this version" — a normal `200`, not an error.

## 3. Models

Replacing `AudioCatalog` and `AudioVersion` in
`yuku/alkitab/base/audio/model/`:

```kotlin
@Serializable
data class AudioSets(
    val schema: Int = 2,
    val preset: String = "",
    val sets: List<AudioSet> = emptyList(),
)

@Serializable
data class AudioSet(
    val audioId: String,
    val title: String,
    val hasTiming: Boolean = false,
    val books_1: List<Int> = emptyList(),
    val mp3UrlTemplate: String,
    val timingUrlTemplate: String? = null,
) {
    fun coversBook(bookId: Int): Boolean = (bookId + 1) in books_1
}
```

`ChapterTiming` keeps its shape but is re-keyed on the new address: `versionId`
and `bookId` give way to `preset`, `audioId`, `book_1`, `chapter_1`.
`VerseTiming` is unchanged.

`books_1` is a `List<Int>` on the wire; the repository converts it to a
`Set<Int>` once at parse time so `coversBook` is O(1) — it is consulted on every
menu preparation.

## 4. Repository layer

`AudioCatalogRepository` becomes **`AudioSetsRepository`**, and the change is
more than a rename: the unit of work goes from "one global document" to "one
version, resolved on demand".

```kotlin
object AudioSetsRepository {
    suspend fun setsFor(versionId: String): AudioSets      // network + cache
    fun cachedSetsFor(versionId: String): AudioSets?       // non-blocking peek
}
```

- **Version → preset.** `preset/in-tb` → `in-tb`. `internal` resolves through
  `BuildConfig.INTERNAL_VERSION_AUDIO_ID` exactly as it does today; that
  per-flavor mapping is the only reason the bundled TB/KJV versions can play
  audio at all, and it is unchanged. A `file/…` versionId has no preset and
  short-circuits to empty.
- **In-memory cache** keyed by `versionId`, holding negative results too — a
  version with no audio must not re-query on every chapter turn.
- **Disk cache** is the existing 50 MB OkHttp cache on `Connections.okHttp`,
  honoring the backend's `Cache-Control`. No new file, no bundled asset, no
  hand-rolled ETag bookkeeping.
- **`Prefkey.audioCatalog_etag` is deleted.** It exists only to drive the
  manual `If-None-Match` on the catalog fetch; OkHttp does conditional revalidation
  itself.
- **`VersionConfigUpdaterService` no longer refreshes audio.** There is nothing
  global left to prefetch. Its `refreshBlocking()` call and the import go away.

`BibleAudioRepository` gains `audioId` on both methods and expands templates
against the resolved set rather than the catalog entry.

Deleted outright: `AudioCatalog.kt`, `AudioVersion.kt`,
`Alkitab/src/main/assets/audio_catalog.json`, `Prefkey.audioCatalog_etag`, and
`AudioCatalogRepositoryTest`'s catalog-file fixtures.

## 5. Selection and persistence

Which recording plays is a per-version user choice.

- **Preference:** a new `Prefkey.audioSelectedSets` holding a JSON object,
  `{"preset/in-tb": "davar"}`. One key rather than a synthesized key per version
  — `Prefkey` is an enum, so per-version keys are not expressible, and the map
  stays small (one entry per version the user has actually played).
- **Default:** `sets[0]`, i.e. backend order. The backend orders by the admin
  table, so editorial control over "which recording does a new user hear" lives
  server-side and needs no app release.
- **Fallback:** if the remembered `audioId` is absent from a later response —
  upstream dropped it, or an admin disabled it — fall back to `sets[0]` and
  overwrite the stored choice. A stale preference must never leave the user with
  a dead entry point.
- **Coverage-aware fallback:** if the remembered set does not cover the current
  book, the bar still shows it as selected but the entry point is hidden for
  that book (§6). It does **not** silently switch recordings — an unannounced
  narrator change mid-book is worse than a temporarily absent button.

## 6. UI

### 6.1 Toolbar entry point

The audio icon's visibility currently comes from a synchronous
`AudioCatalogRepository.isAudioAvailable(versionId)` inside
`onPrepareOptionsMenu`. With per-version lookup, the first answer is not
available synchronously.

Resolution:

1. `onPrepareOptionsMenu` reads `cachedSetsFor(versionId)` — non-blocking.
2. `null` (not yet resolved) → icon hidden, and a coroutine kicks off
   `setsFor(versionId)`.
3. On completion, `invalidateOptionsMenu()` re-prepares the menu with the real
   answer.

The fetch is triggered on version change and on activity start, so in practice
the icon appears on first composition. A brief absence on a genuinely cold first
open is preferable to a blocking network call on the main thread, and far
preferable to an icon that is present but dead.

Visibility is now `sets.isNotEmpty() && selectedSet.coversBook(currentBookId)`.
Book coverage is ragged upstream — `in-tb/davar` has no books 11–14 — so a
version-level check alone would leave a button that only 404s.

### 6.2 Set picker

When a version has more than one set, the audio bar shows the current set's
`title` as a compact button next to the speed control. Tapping opens a
`ModalBottomSheet` with a single-selection list of `title`s — the same pattern
and placement as `SpeedBottomSheet`, which already establishes this interaction
in the bar.

- Single-set versions show no button at all. Most versions have one set; they
  should not pay for a control that offers no choice.
- Sets not covering the current book are listed but disabled, with the reason
  shown ("not available for this book"). Hiding them would make the list appear
  to change size as the user moves through the Bible.
- Switching sets reloads the current chapter in the new recording, seeking to
  the start of the verse that was playing when timing exists on both sides, and
  to the chapter start otherwise. Timing differs per recording, so a
  millisecond-preserving switch would land in an arbitrary place.

`AudioSourceOption` — currently `(versionId, shortName)` for the split-view
picker — gains `audioId` and `title`. Split-source selection and set selection
remain distinct choices: which *version* drives audio, then which *recording* of
it.

### 6.3 Timing-less sets

`hasTiming: false` is common (`davar`, `wordproject`, `in-tb/hosanna`). The PRD
already specifies this state — audio plays, verse highlight and verse-skip are
disabled, no error is shown. What changes is that it is now knowable *before*
playback from `AudioSet.hasTiming`, so the controls can be rendered disabled
from the start rather than becoming inert once a timing fetch comes back empty.

## 7. Service layer

- `BibleAudioService.AudioRequest` gains `audioId`.
- `PlaybackState` gains `audioId`, so the bar can render the active recording
  and detect service-initiated changes after process death.
- Chapter auto-advance and prev/next respect the selected set's coverage: at a
  coverage edge the button is disabled, exactly as at a Bible boundary. Auto-advance
  stops rather than skipping the gap — jumping Job → Esther because Ezra is
  missing would be more confusing than stopping.
- The MediaStyle notification subtitle becomes `"<version short name> · <set
  title>"` when a version has more than one set, and stays the bare version name
  otherwise.

## 8. File-level impact

| File | Change |
|---|---|
| `audio/model/AudioCatalog.kt`, `AudioVersion.kt` | **Deleted** |
| `audio/model/AudioSets.kt`, `AudioSet.kt` | **New** |
| `audio/model/ChapterTiming.kt` | Re-keyed on `preset`/`audioId`/`book_1` |
| `audio/AudioCatalogRepository.kt` | **Replaced** by `AudioSetsRepository.kt` |
| `audio/BibleAudioRepository.kt` | `audioId` parameter; template expansion from the set |
| `audio/AudioBarController.kt` | Set selection, persistence, coverage checks |
| `audio/BibleAudioService.kt` | `audioId` in `AudioRequest`; coverage-aware neighbors |
| `audio/PlaybackState.kt` | `audioId` field |
| `audio/ui/AudioBar.kt` | Set button; `AudioSourceOption` gains `audioId`/`title` |
| `audio/ui/AudioSetBottomSheet.kt` | **New** |
| `IsiActivity.kt` | Async menu resolution + `invalidateOptionsMenu()` |
| `sv/VersionConfigUpdaterService.java` | Audio catalog refresh removed |
| `storage/Prefkey.kt` | `audioCatalog_etag` removed; `audioSelectedSets` added |
| `assets/audio_catalog.json` | **Deleted** |

## 9. Test plan

- **`AudioSetsRepository`** — version→preset mapping including `internal` via
  `INTERNAL_VERSION_AUDIO_ID` and the empty-override case; `file/…` versions
  short-circuit; negative results cached; concurrent `setsFor` for one version
  issues a single request.
- **`AudioSet.coversBook`** — 0-based to 1-based conversion at both ends
  (Genesis `bookId 0` → `books_1` `1`, Revelation `bookId 65` → `66`), and the
  ragged `davar` case (books 11–14 absent).
- **Selection** — default is `sets[0]`; a remembered `audioId` is honored;
  a vanished `audioId` falls back to `sets[0]` and rewrites the stored map;
  the map round-trips through preferences with multiple versions.
- **Coverage-driven UI** — entry point hidden for an uncovered book; neighbor
  chapter disabled at a coverage edge; auto-advance stops at a gap.
- **Timing** — normalized ms parse; `hasTiming: false` disables highlight and
  verse-skip from the start.

Existing `AudioCatalogRepositoryTest` is replaced by `AudioSetsRepositoryTest`.
`BibleAudioRepositoryTest` gains `audioId` cases. `HighlightTrackerTest`,
`BibleNeighborResolverTest`, and `AudioHighlightColorTest` are unaffected.

## 10. Sequencing

The backend must land first — the app has no bundled fallback any more, so
`/audio/sets/*` has to be live before the client change is useful.

1. Backend: `/audio/sets`, `/audio/file`, `/audio/timing`, admin page; deployed.
2. Client: models + `AudioSetsRepository`, catalog deleted. Single-set versions
   work end to end.
3. Client: selection, persistence, set picker, coverage gating.
4. Client: service-layer `audioId` plumbing and notification subtitle.

Steps 2–4 are one user-visible feature and ship together.

## 11. Open items

- **Offline pre-download** (PRD §2 v2 item) now has to key its cache directory
  on `audioId` as well: `files/audio/<versionId>/<audioId>/<bookId>/<chapter>.mp3`.
  Noted so the layout is not designed into a corner later.
- **Per-set attribution.** The backend can serve narrator/publisher text, but
  the bar has no room for it. If it becomes desirable, the set bottom sheet is
  the natural home.
