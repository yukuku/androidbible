# Backend Implementation Plan — Bible Audio Playback

**Repo:** `yukuku/alkitab-host` (backend for `api.alkitab.app`)
**Client counterpart:** see [client-plan.md](client-plan.md) and [prd.md](prd.md).
**Estimated effort:** ~1 sprint-week for one engineer (v1 endpoints + caching + monitoring).

> ⚠️ I was not able to read `alkitab-host` from this environment (the GitHub MCP scope is restricted to `yukuku/androidbible`). This plan is therefore framed against the conventions visible from the client: a RESTful JSON API at `https://api.alkitab.app`, existing endpoints such as `/devotion/get`, `/sync/api/sync`, `/versions/get_yes`, `/addon/audio/exists`, and the Firebase-adjacent hosting described in `docs/backend-communication.md`. Before implementation, confirm the stack and adjust the handler language/framework sections accordingly.

---

## 1. Goals

1. Serve a versioned audio catalog that the client can cache (ETag-aware).
2. Serve normalized per-chapter verse-timing JSON (schema owned by us, not by sabda.org).
3. Serve chapter MP3s via redirect to the current canonical CDN (today: media.sabda.org), so the client has exactly one URL shape regardless of where the file lives.
4. Degrade gracefully when sabda.org is slow or down (we cache what we've seen).
5. Make it cheap to add, remove, or re-price audio versions without a client release.

## 2. Endpoint summary

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/audio/catalog` | none | List of versions with audio + URL templates |
| `GET` | `/audio/timing/{versionId}/{bookId}/{chapter_1}` | none | Verse-timing JSON, normalized |
| `GET` | `/audio/chapter/{versionId}/{bookId}/{chapter_1}` | none | 302 redirect to the chapter MP3 on the CDN |
| `POST` | `/audio/admin/reload` | admin token | Invalidate in-memory catalog; for manual rollouts |

All responses include `Cache-Control` headers (`public, max-age=<ttl>, stale-while-revalidate=<ttl*2>`) and ETag support. `appIdentifier` query params may be accepted for analytics but must not affect the response body in v1.

## 3. `GET /audio/catalog`

### 3.1 Request

```
GET /audio/catalog
If-None-Match: "abc123"   (optional)
Accept: application/json
```

### 3.2 Response

`200 OK`

```json
{
  "generatedAt": 1713456000000,
  "entries": [
    {
      "versionId": "preset/in-tb",
      "shortName": "TB",
      "displayLocaleHint": "in",
      "chapterUrlTemplate": "/audio/chapter/preset%2Fin-tb/{bookId}/{chapter_1}",
      "timingUrlTemplate": "/audio/timing/preset%2Fin-tb/{bookId}/{chapter_1}",
      "copyrightNotice": "© LAI, via SABDA",
      "license": "Non-commercial",
      "hasDeuterocanon": false
    },
    {
      "versionId": "preset/in-ayt",
      "shortName": "AYT",
      "displayLocaleHint": "in",
      "chapterUrlTemplate": "/audio/chapter/preset%2Fin-ayt/{bookId}/{chapter_1}",
      "timingUrlTemplate": "/audio/timing/preset%2Fin-ayt/{bookId}/{chapter_1}",
      "copyrightNotice": "© AYT, via SABDA",
      "license": "Non-commercial",
      "hasDeuterocanon": false
    },
    {
      "versionId": "preset/in-avb",
      "shortName": "AVB",
      "displayLocaleHint": "ms",
      "chapterUrlTemplate": "/audio/chapter/preset%2Fin-avb/{bookId}/{chapter_1}",
      "timingUrlTemplate": "/audio/timing/preset%2Fin-avb/{bookId}/{chapter_1}",
      "copyrightNotice": "© BSM, via SABDA",
      "license": "Non-commercial",
      "hasDeuterocanon": false
    },
    {
      "versionId": "preset/en-kjv",
      "shortName": "KJV",
      "displayLocaleHint": "en",
      "chapterUrlTemplate": "/audio/chapter/preset%2Fen-kjv/{bookId}/{chapter_1}",
      "timingUrlTemplate": "/audio/timing/preset%2Fen-kjv/{bookId}/{chapter_1}",
      "copyrightNotice": "Public Domain",
      "license": "PD",
      "hasDeuterocanon": false
    }
  ]
}
```

Headers:

```
ETag: "abc123"
Cache-Control: public, max-age=86400, stale-while-revalidate=172800
Content-Type: application/json
```

`304 Not Modified` when `If-None-Match` matches; empty body.

### 3.3 Data source

The catalog is a small hand-maintained config file (JSON or TOML) in the backend repo — effectively the same shape as `version_config.json` today, but narrower. No database; loaded once at boot, reloadable via `/audio/admin/reload`. Each deploy can change the list.

### 3.4 Notes

- `versionId` must exactly match the client's `MVersion.getVersionId()` format (`"preset/<preset_name>"`). See `Alkitab/src/main/java/yuku/alkitab/base/model/MVersionPreset.java:18-20`. Getting this wrong means the toolbar icon never shows up.
- `chapterUrlTemplate` uses `{bookId}` and `{chapter_1}` placeholders (client simple substitution). Keeping them as full paths, not just the filename, lets us move the CDN later without client work.
- `hasDeuterocanon` is advisory so the client can disable Next-chapter at the Protestant canon boundary for those who want it; v1 can ignore.

## 4. `GET /audio/timing/{versionId}/{bookId}/{chapter_1}`

### 4.1 Request

```
GET /audio/timing/preset%2Fin-tb/41/3
Accept: application/json
```

`versionId` is URL-encoded because it contains `/`. `bookId` is the 0-based book ID used throughout the client (`AlkitabModel/Ari.java`). `chapter_1` is 1-based.

### 4.2 Response (schema v1)

```json
{
  "schema": 1,
  "versionId": "preset/in-tb",
  "bookId": 41,
  "chapter_1": 3,
  "durationMs": 195000,
  "verses": [
    { "verse_1": 1,  "startMs": 0,     "endMs": 5820  },
    { "verse_1": 2,  "startMs": 5820,  "endMs": 11960 },
    { "verse_1": 3,  "startMs": 11960, "endMs": 18100 }
  ],
  "generatedAt": 1713456000000
}
```

Validation:

- `verses` is sorted by `startMs`.
- Each `endMs > startMs`.
- `verse_1` matches the canonical verse count of the chapter (we check against the bundled YES2 metadata).
- Unknown versions → `404`; version known but chapter out of range → `404`; version known, chapter in range, but upstream has no timing → `200` with an empty `verses` array (the client displays audio without highlight).

Headers:

```
Cache-Control: public, max-age=604800, stale-while-revalidate=2592000
ETag: "<sha1>"
```

### 4.3 Implementation

Backend maintains a storage bucket (Firestore, GCS, or a Postgres table — whichever is already in alkitab-host) with the normalized timing for every (versionId, bookId, chapter_1) that a client has ever requested. On request:

1. Cache hit → serve from storage (microseconds).
2. Cache miss → fetch from `karaoke.sabda.org/api/timming.php?book=<timingName>&chapter=<chapter_1>&version=<timingVersionParam>`, normalize, store, serve.
3. Upstream failure on cache miss → `503` with `Retry-After: 30`. Never serve stale data we've never verified.
4. Upstream failure on cache hit → serve the stored copy anyway (that's why `stale-while-revalidate` is long).

The SABDA-specific translation tables (version ID → `timingVersionParam`, book ID → `timingName`) live here, not in the client. Shape:

```
AUDIO_ADAPTERS = {
  "preset/in-tb": {
    sabda_timing_version: "tbsuara",
    sabda_audio_folder: "tb_alkitabsuara",
  },
  "preset/in-ayt": {
    sabda_timing_version: "ayt",
    sabda_audio_folder: "ayt-ai-v2",
  },
  ...
}

BOOK_META = [
  { bookId: 0, sabda_folder: "kejadian", sabda_abbr: "kej", sabda_timing_name: "Kejadian" },
  ...
]
```

(Lift these tables verbatim from `BibleAudioRepository.kt` in PR #127 — they're the result of real experimentation against sabda.org.)

### 4.4 Why normalize instead of proxy passthrough?

The sabda.org karaoke API returns `{ timestamps: [{ time_start, duration, verse }] }`. If a client just hit sabda directly:

- Schema changes break every installed version.
- The SABDA site is occasionally slow (seconds); we want to serve from hot cache.
- We can't add fields (e.g. `durationMs`) without an upstream PR.

## 5. `GET /audio/chapter/{versionId}/{bookId}/{chapter_1}`

### 5.1 Behavior

- `302 Found` with `Location:` pointing to the SABDA MP3 URL.
- `Cache-Control: public, max-age=86400` on the redirect (so the client's OkHttp cache remembers the redirect itself).
- On unknown version or out-of-range chapter: `404`.
- On SABDA-side 404 (known missing chapter): `404` with a JSON body `{ "error": "chapter_not_available" }` — the client snackbars "Audio not available for this chapter."

### 5.2 Why a redirect, not a proxy?

- MP3 files are ~1–10 MB each. Proxying would bill us for tens of TB of egress with no value-add.
- SABDA already serves with reasonable cache headers.
- Clients benefit from CDN edge caching on the sabda side.

If SABDA's policy later forbids cross-origin redirects or demands we terminate the stream, we can flip the handler to proxy — the client's URL shape doesn't change.

### 5.3 URL construction

Use the same algorithm as PR #127's `BibleAudioRepository.buildUrl` (server-side this time):

```
base   = "https://media.sabda.org/alkitab_audio"
folder = AUDIO_ADAPTERS[versionId].sabda_audio_folder
subdir = if bookId < 39 then "pl/mp3/cd" else "pb/mp3/cd"
prefix = zeroPad(bookId + 1, 2)
shortDir = BOOK_META[bookId].sabda_folder
abbr = BOOK_META[bookId].sabda_abbr
chapterStr = zeroPad(chapter_1, if chapterCount(bookId) >= 100 then 3 else 2)
url = "$base/$folder/$subdir/${prefix}_${shortDir}/${prefix}_${abbr}${chapterStr}.mp3"
```

## 6. `POST /audio/admin/reload`

Simple admin endpoint protected by the same mechanism that today protects the existing `/versions/get_yes` etc. backoffice (reuse, don't invent). Body: empty. Response: `{ "reloadedAt": <ts>, "catalogEntries": <count> }`. Bumps the catalog's ETag so all clients refetch within a day.

## 7. Monitoring

- **Per-endpoint latency & error rate** (existing monitoring stack).
- **Cache hit ratio on `/audio/timing/*`** — if this drops, sabda is probably hot-reloading; alert.
- **SABDA error rate** — if upstream 5xx crosses 5% over 10 min, page on-call.
- **Catalog fetch rate** — baseline after rollout; spikes often mean client cache invalidation bugs.

## 8. Rollout

1. Implement the three read endpoints (catalog, timing, chapter) and `/audio/admin/reload`. Deploy to production — no flag, the endpoints are either live or they aren't.
2. Pre-warm the timing cache for the whole Bible (~1188 chapters × 4 versions ≈ 4750 fetches) by running a one-off script against the deployed instance. Takes a few minutes and guarantees the first user never hits a cold cache.
3. Verify the client (shipped with a bundled `assets/audio_catalog.json` mirroring the same four versions) continues to work if the backend is temporarily unreachable, so there is no hard coupling between app and backend releases.

## 9. Test plan

- [ ] Unit tests for `AUDIO_ADAPTERS` / `BOOK_META` lookups (boundary: bookId 38 → "pl", bookId 39 → "pb").
- [ ] Unit tests for URL construction (Psalms has 150 chapters → 3-digit formatting; every other book → 2-digit).
- [ ] Integration test against a mock sabda server with injected failures (5xx, timeout, malformed JSON).
- [ ] Contract test matching the JSON schema in §4 against the shapes the Android client expects (can be a shared JSON-schema file in both repos).
- [ ] Load test: catalog endpoint at 1000 rps with ETag → mostly 304s.

## 10. Security & abuse

- No auth on read endpoints (by design — devotion and version endpoints are the same shape).
- Rate-limit per client IP: 100 req/min per endpoint is plenty (a phone will make one catalog + ~10 timing requests per session).
- Deny any path-traversal in `{versionId}` (reject anything not in the catalog's key set).

## 11. Coordination with client

- Client lands its code with a bundled `audio_catalog.json` describing the four SABDA versions. If the backend is absent, the app still works — it just won't see new versions until the next app release.
- The client's first HTTP call on app start (after the existing version-config refresh) is `GET /audio/catalog`. Keep this fast and cached hard — slow catalog fetches won't break the feature (falls back to bundled), but they cost mobile data.

## 12. Future work

- **Analytics events** (when SABDA allows us to attribute usage): forward per-chapter play counts.
- **Alternative audio sources** (e.g. user-contributed readings or AI-generated voices) — the catalog's `versionId` + `chapterUrlTemplate` shape supports this already.
- **Timing regeneration** when a version updates — add a `timingGeneration` field to force client refresh per version.
- **HLS / DASH** for per-verse byte-range requests if pre-download or fine-grained scrubbing becomes a priority.

## 13. Open questions (for the backend owner)

1. Which datastore does `alkitab-host` already use? (Timing cache should ride on an existing Postgres/Firestore/KV, not introduce a new one.)
2. Is there already a scheduled job framework for pre-warming? (If not, a one-off script is fine.)
3. What's the current admin-auth scheme for existing backoffice endpoints? Reuse it for `/audio/admin/reload`.
4. Licensing: SABDA permits free distribution of audio, but do we need a formal attribution line beyond the `copyrightNotice` field in the catalog?
