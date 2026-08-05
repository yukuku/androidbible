# Backend HTTP Contract — Bible Audio

**Scope:** the `/audio/*` endpoints this app calls, and nothing else. Backend
implementation — caching tiers, crawling, admin tooling, hosting — is out of
scope here and lives with the backend.

**Client counterparts:** [prd.md](prd.md), [client-plan.md](client-plan.md),
[multi-audio-sets-design.md](multi-audio-sets-design.md).

Base URL is `BuildConfig.SERVER_HOST` (`https://api.alkitab.app`). All requests
are unauthenticated `GET`s issued through `Connections.okHttp`, so OkHttp's
50 MB disk cache handles revalidation from the response headers — the app does
no manual `If-None-Match` bookkeeping.

---

## Conventions

**Book and chapter are 1-based** in every audio path (`book_1` = 1 for Genesis
through 66 for Revelation, `chapter_1` = 1 for the first chapter). `Ari`'s
`bookId` is 0-based, so `book_1 = bookId + 1`. The conversion happens once, in
`BibleAudioRepository`. The `_1` suffix is this codebase's existing marker for
1-based values, as in `chapter_1` and `verse_1`.

**`<preset>` is a version's `preset_name`** — `in-tb`, `en-kjv`, `ms-avb` — not
its `versionId`. `MVersionPreset` and `MVersionDb` carry it directly;
`MVersionInternal` gets it from `BuildConfig.INTERNAL_VERSION_PRESET_NAME` (see
PRD §5.4.1). A `file/…` version has no preset and makes no audio request.

**The app never constructs a media path.** `/audio/sets` hands back URL
templates; the app expands `{book_1}` and `{chapter_1}` into them and requests
the result. Origin host, folder layout, and filename scheme are entirely the
backend's business — `grep -ri "sabda" Alkitab/src/main` must return nothing.

## Endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/audio/sets/<preset>` | Which recordings exist for one version |
| `GET` | `/audio/file/<preset>/<audioId>/<book_1>/<chapter_1>.mp3` | Chapter audio |
| `GET` | `/audio/timing/<preset>/<audioId>/<book_1>/<chapter_1>.json` | Verse timing |

There is no global catalog endpoint. Availability is queried one version at a
time — see [multi-audio-sets-design.md](multi-audio-sets-design.md) §1 for why.

---

## `GET /audio/sets/<preset>`

```
GET /audio/sets/in-tb
```

`200 OK`

```json
{
  "schema": 2,
  "preset": "in-tb",
  "generatedAt": 1754357000000,
  "sets": [
    {
      "audioId": "alkitabsuara",
      "title": "Alkitab Suara",
      "hasTiming": true,
      "books_1": [1, 2, 3, "…", 66],
      "mp3UrlTemplate": "/audio/file/in-tb/alkitabsuara/{book_1}/{chapter_1}.mp3",
      "timingUrlTemplate": "/audio/timing/in-tb/alkitabsuara/{book_1}/{chapter_1}.json"
    },
    {
      "audioId": "davar",
      "title": "davar",
      "hasTiming": false,
      "books_1": [1, "…", 10, 15, "…", 66],
      "mp3UrlTemplate": "/audio/file/in-tb/davar/{book_1}/{chapter_1}.mp3",
      "timingUrlTemplate": null
    }
  ]
}
```

| Field | Meaning |
|---|---|
| `sets` | Ordered. `sets[0]` is the default recording for this version. |
| `audioId` | Opaque recording identifier; stable, used as the persisted selection key. |
| `title` | Display name. Falls back to the raw `audioId` when the backend has no mapping, so an unlabelled set reads as its slug rather than disappearing. |
| `hasTiming` | `false` → the recording plays but has no verse timing. Known up front, so highlight and verse-skip render disabled from the start. |
| `books_1` | 1-based book coverage. Ragged — a recording may omit whole books. |
| `mp3UrlTemplate` | Expand `{book_1}` and `{chapter_1}`; resolve against `SERVER_HOST`. |
| `timingUrlTemplate` | `null` exactly when `hasTiming` is false. |

**Guarantees the client relies on:**

- Every field is always present. `sets` is `[]` rather than absent when a
  version has no audio, and `timingUrlTemplate` is explicitly `null` rather than
  omitted. This is why the Kotlin models declare no default parameter values — a
  contract change fails loudly at parse time instead of yielding a
  silently-empty model.
- An unknown or unavailable preset returns `200` with `"sets": []`, **not** a
  `404`. "This version has no audio" is a normal answer.
- `503` with `Retry-After` means the backend couldn't resolve availability in
  time. Treat as "no audio for now" and hide the entry point; do not retry in a
  loop.

Caching: `ETag` + `Cache-Control: public, max-age=86400`.

## `GET /audio/file/<preset>/<audioId>/<book_1>/<chapter_1>.mp3`

Chapter audio, served directly by the backend. **Not a redirect** — the response
body is the MP3.

- `Accept-Ranges: bytes`, and `Range` requests are honored, so ExoPlayer can
  seek. Expect `206` with `Content-Range` for a ranged request.
- `Content-Type: audio/mpeg`, plus `ETag` / `Last-Modified`.
- `Cache-Control: public, max-age=2592000` (30 days).
- `404` — this chapter isn't in this recording. Surfaces the error state on the
  bar's play button (PRD §4.6). Rare within a book listed in `books_1`.
- `502` with `Retry-After` — upstream trouble. Error state on the play button;
  the retry is user-initiated, not automatic.

## `GET /audio/timing/<preset>/<audioId>/<book_1>/<chapter_1>.json`

`200 OK`

```json
{
  "schema": 2,
  "preset": "in-tb",
  "audioId": "alkitabsuara",
  "book_1": 1,
  "chapter_1": 1,
  "durationMs": 1893000,
  "verses": [
    { "verse_1": 1, "startMs": 0,     "endMs": 20560 },
    { "verse_1": 2, "startMs": 20560, "endMs": 31520 }
  ]
}
```

Timings are integer **milliseconds**, already normalized and sorted by
`startMs`; `durationMs` is the maximum `endMs`. The app does no unit conversion.

- `verses: []` with `200` means "audio exists, timing does not" — play without
  highlight. This is a normal state, not an error, so it is not a `404`.
- Skip the request entirely when the set's `hasTiming` is false.

Caching: `ETag` + `Cache-Control: public, max-age=604800`.

## Error handling summary

| Response | Client behavior |
|---|---|
| `200` with `sets: []` | No audio for this version; hide the toolbar icon |
| `200` with `verses: []` | Play without verse highlight; disable verse-skip |
| `404` on `/audio/file/…` | Error icon on the bar's play button; tap retries |
| `502` / `503` with `Retry-After` | Treat as temporarily unavailable; no retry loop (only the play button's explicit tap-to-retry re-queries) |
| Network or parse failure | Empty set list; icon stays hidden rather than showing a dead button. A parse failure refetches once with `no-cache` first, in case the corrupt bytes were a cached entry |

The app ships **no bundled fallback**. Per-version availability can't be usefully
baked into the APK, so these endpoints must be live in production before the
client feature is testable end to end.
