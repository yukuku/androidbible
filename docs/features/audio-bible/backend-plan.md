# Backend Implementation Plan — Bible Audio Playback

**Repo:** `yukuku/alkitab-host` (backend for `api.alkitab.app`)
**Client counterpart:** see [client-plan.md](client-plan.md) and [prd.md](prd.md).
**Estimated effort:** ~1 sprint-week for one engineer (v1 endpoints + caching + monitoring).

## 0. Stack (confirmed against `alkitab-host`)

The backend is **Python 3.12 / Flask on Google App Engine Standard**, deployed as the `web-py3` service (see `alkitab-host/CLAUDE.md`, `alkitab-host/web/flask_app.py`, `alkitab-host/dispatch.yaml`).

- **Routing.** `dispatch.yaml` sends `*/sync/*`, `*/cloud*`, `*/deferred_yuku/*`, `*/sync_cleanup/*` to the `sync-py3` service; everything else (including the new `/audio/*` paths) falls through to the `*/*` catch-all on `web-py3`. **No `dispatch.yaml` change needed.**
- **Path namespace.** `/addon/audio/*` is already taken by **song** audio (`alkitab-host/addon/flask_app.py:161-187`, used by Kidung MP3/MID files keyed on song books like KJ/KPRI). New Bible audio endpoints **must not collide with that prefix** — use top-level `/audio/*`.
- **Module shape.** Each feature is a Flask blueprint under its own top-level package (`addon/`, `versions/`, `devotion/`, `rp/`, `v/`, `announce/`). Blueprints are registered in `web/flask_app.py`. We will follow the same pattern: a new `audio/` package with `audio/__init__.py` and `audio/flask_app.py`.
- **Storage.** The backend uses **Cloud Datastore via `google-cloud-ndb`** for persistent records (see `addon.AddonDownloadCounter`, `versions.AndroidBibleVersionDownloadCounter`, dozens of `Sync*` kinds). For per-instance hot caches, `web/cache.py` is a small TTL-bound LRU. **No Postgres, no Firestore, no GCS for app data** — the timing cache will be one new NDB kind plus the in-memory LRU.
- **Admin auth.** Admin routes use the `@require_staff` decorator (Google OAuth2 staff login, `web/auth.py`), e.g. `addon.addon_admin_counter_stats`. **Not** a token. We follow the same convention for `/audio/admin/reload`.
- **Upstream fetches.** Synchronous `requests` calls with a timeout, caching via `web.cache.set/get` (TTL-bound, per-instance). `addon.audio_file_exists` is the canonical example.
- **Outbound CDN handoffs.** Existing addon endpoints redirect (`Flask.redirect`, 302) to either `https://boafiles.kejut.com/addon/...` or version-specific hosts. Bible audio will redirect to `https://media.sabda.org/...`.

This plan is now grounded in the actual codebase. Implementation specifics below reference real files.

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
| `GET` | `/audio/timing?versionId={versionId}&bookId={bookId}&chapter_1={chapter_1}` | none | Verse-timing JSON, normalized |
| `GET` | `/audio/chapter?versionId={versionId}&bookId={bookId}&chapter_1={chapter_1}` | none | 302 redirect to the chapter MP3 on the SABDA CDN |
| `POST` | `/audio/admin/reload` | `@require_staff` | Invalidate in-memory catalog + timing LRU; for manual rollouts |

`versionId` is the exact string the client's `MVersion.getVersionId()` returns (e.g. `preset/in-tb`). It is carried as a **query parameter** rather than a path segment so the `/` it contains doesn't need to be URL-encoded into a path component, which many routers (Nginx, Spring MVC's default mapping, Go's `http.ServeMux`) reject or pre-decode in surprising ways. Keeping `versionId` identical on client and backend means no translation layer on either side — the catalog entry's identifier, the `matchVersionId` the client looks up locally, and the query param sent to `/audio/timing`/`/audio/chapter` are all the same string.

All responses include `Cache-Control` with `public, max-age=<ttl>` and ETag support. `appIdentifier` query params may be accepted for analytics but must not affect the response body in v1. We deliberately do **not** include `stale-while-revalidate` — the Android client uses OkHttp's standard `Cache`, which ignores that directive (`Connections.kt` configures a 50 MB disk cache with no SWR interceptor). Graceful-degradation when the backend is unreachable is handled at two other layers: (a) the client ships a bundled `assets/audio_catalog.json` for catalog fetches, and (b) the backend itself holds a hot timing cache (§4.3) so the sabda.org upstream being down does not translate to backend unavailability. If we later decide to serve stale responses on network failure, we'll add a dedicated client-side `Interceptor` rather than relying on header directives OkHttp ignores.

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
      "chapterUrlTemplate": "/audio/chapter?versionId=preset%2Fin-tb&bookId={bookId}&chapter_1={chapter_1}",
      "timingUrlTemplate": "/audio/timing?versionId=preset%2Fin-tb&bookId={bookId}&chapter_1={chapter_1}",
      "copyrightNotice": "© LAI, via SABDA",
      "license": "Non-commercial",
      "hasDeuterocanon": false
    },
    {
      "versionId": "preset/in-ayt",
      "shortName": "AYT",
      "displayLocaleHint": "in",
      "chapterUrlTemplate": "/audio/chapter?versionId=preset%2Fin-ayt&bookId={bookId}&chapter_1={chapter_1}",
      "timingUrlTemplate": "/audio/timing?versionId=preset%2Fin-ayt&bookId={bookId}&chapter_1={chapter_1}",
      "copyrightNotice": "© AYT, via SABDA",
      "license": "Non-commercial",
      "hasDeuterocanon": false
    },
    {
      "versionId": "preset/in-avb",
      "shortName": "AVB",
      "displayLocaleHint": "ms",
      "chapterUrlTemplate": "/audio/chapter?versionId=preset%2Fin-avb&bookId={bookId}&chapter_1={chapter_1}",
      "timingUrlTemplate": "/audio/timing?versionId=preset%2Fin-avb&bookId={bookId}&chapter_1={chapter_1}",
      "copyrightNotice": "© BSM, via SABDA",
      "license": "Non-commercial",
      "hasDeuterocanon": false
    },
    {
      "versionId": "preset/en-kjv",
      "shortName": "KJV",
      "displayLocaleHint": "en",
      "chapterUrlTemplate": "/audio/chapter?versionId=preset%2Fen-kjv&bookId={bookId}&chapter_1={chapter_1}",
      "timingUrlTemplate": "/audio/timing?versionId=preset%2Fen-kjv&bookId={bookId}&chapter_1={chapter_1}",
      "copyrightNotice": "Public Domain",
      "license": "PD",
      "hasDeuterocanon": false
    }
  ]
}
```

Field roles:

- `versionId` — the **exact** string the client's `MVersion.getVersionId()` returns for the version this audio entry covers. See `Alkitab/src/main/java/yuku/alkitab/base/model/MVersionPreset.java:18-20`: preset versions return `"preset/<preset_name>"`. The client uses this field to decide whether the toolbar icon should appear and which entry to use for the visible version.
- `chapterUrlTemplate` / `timingUrlTemplate` — URL templates with `{bookId}` and `{chapter_1}` placeholders (client performs literal string replace). The `versionId` is **already embedded and URL-encoded** in the template, so the client never has to encode it itself. Keeping these as full paths instead of just a filename lets us move the CDN without client work.

Headers:

```
ETag: "abc123"
Cache-Control: public, max-age=86400
Content-Type: application/json
```

`304 Not Modified` when `If-None-Match` matches; empty body.

### 3.3 Data source

The catalog is a Python module-level dict in `audio/flask_app.py`, in the same style as `BOOKS_WITH_MP3` in `addon/flask_app.py`. Hand-edited; one deploy = one update. No database, no JSON file on disk to keep in sync.

```python
# audio/flask_app.py
CATALOG = {
    "preset/in-tb":  {"shortName": "TB",  "displayLocaleHint": "in", "copyrightNotice": "© LAI, via SABDA",  "license": "Non-commercial", "hasDeuterocanon": False},
    "preset/in-ayt": {"shortName": "AYT", "displayLocaleHint": "in", "copyrightNotice": "© AYT, via SABDA", "license": "Non-commercial", "hasDeuterocanon": False},
    "preset/in-avb": {"shortName": "AVB", "displayLocaleHint": "ms", "copyrightNotice": "© BSM, via SABDA", "license": "Non-commercial", "hasDeuterocanon": False},
    "preset/en-kjv": {"shortName": "KJV", "displayLocaleHint": "en", "copyrightNotice": "Public Domain",     "license": "PD",              "hasDeuterocanon": False},
}

# At request time, the handler synthesises chapterUrlTemplate / timingUrlTemplate
# from the dict's keys plus the static path shape — see the JSON in §3.2.
```

The ETag is derived from a `sha1` of `json.dumps(CATALOG, sort_keys=True)`. It changes only when the dict changes (i.e. when a deploy ships a new entry). `/audio/admin/reload` is a no-op for the catalog itself today (the dict reloads when the App Engine instance restarts), but we keep the endpoint to bust the timing LRU and as a hook for any future dynamic catalog source.

### 3.4 Notes

- `versionId` must exactly match the client's `MVersion.getVersionId()` format (`"preset/<preset_name>"`). See `Alkitab/src/main/java/yuku/alkitab/base/model/MVersionPreset.java:18-20`. Getting this wrong means the toolbar icon never shows up.
- `hasDeuterocanon` is advisory so the client can disable Next-chapter at the Protestant canon boundary for those who want it; v1 can ignore.

## 4. `GET /audio/timing?versionId=…&bookId=…&chapter_1=…`

### 4.1 Request

```
GET /audio/timing?versionId=preset%2Fin-tb&bookId=41&chapter_1=3
Accept: application/json
```

`versionId` is URL-encoded (`preset/in-tb` → `preset%2Fin-tb`). `bookId` is the 0-based book ID used throughout the client (`AlkitabModel/Ari.java`). `chapter_1` is 1-based.

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

`durationMs` is **informational** — it's what we measured upstream at normalization time. The client treats `ExoPlayer.duration` (after the MP3 has prepared) as the source of truth for the scrubber max, because the actual file length may differ from what sabda.org's timing API reports. Include `durationMs` when we have a confident measurement; omit the field (or set it to `0`) when we don't. The client must not fail if it's missing or mismatched.

Validation:

- `verses` is sorted by `startMs`.
- Each `endMs > startMs`.
- `verse_1` matches the canonical verse count of the chapter (we check against the bundled YES2 metadata).
- Unknown versions → `404`; version known but chapter out of range → `404`; version known, chapter in range, but upstream has no timing → `200` with an empty `verses` array (the client displays audio without highlight).

Headers:

```
Cache-Control: public, max-age=604800
ETag: "<sha1>"
```

### 4.3 Implementation

Two-tier cache: per-instance in-memory LRU (microsecond hits, evicted on instance restart) backed by a single new NDB kind for cross-instance persistence.

**NDB model** (defined in `audio/flask_app.py`, alongside the existing counter-style models in `addon/` and `versions/`):

```python
class BibleAudioTimingCache(ndb.Model):
    # key name format: "<versionId>|<bookId>|<chapter_1>", e.g. "preset/in-tb|41|3"
    payload   = ndb.JsonProperty(required=False)  # the normalized response body, or None for negative-cached entries
    not_found = ndb.BooleanProperty(default=False)  # True if upstream returned 404 / known-missing chapter
    fetched_at = ndb.IntegerProperty(required=True)  # epoch seconds, for TTL re-validation
```

Why NDB and not GCS:
- Total dataset is ~5000 chapters × ~5 KB = ~25 MB. Datastore handles this easily; entity size limit is 1 MB so a chapter's timing fits with room to spare.
- Datastore reads are cheap (~$0.06 per 100k reads) and most chapters won't get more than a handful of cold-instance fetches per day.
- Operationally the project already lives in NDB; introducing GCS would add another auth surface and another deploy step.

Why not just `web/cache.py`:
- That LRU is per-instance and per-warm-window. With F1 instance class auto-scaling 0-3, each scale-up serves a cold cache. NDB makes the cache survive scale events.

**Request flow** (`GET /audio/timing`):

1. **In-memory hit** (`web.cache.get(key)`) → serve.
2. **NDB hit** with `not_found=False` and `fetched_at` within TTL → populate in-memory cache, serve.
3. **NDB hit** with `not_found=True` and within negative TTL → serve `200` with `verses: []` (clients render audio without highlight). We do **not** propagate 404 here because the chapter exists in the Bible — it just lacks timing.
4. **Cold miss** → `requests.get(karaoke.sabda.org/api/timming.php?book=<timingName>&chapter=<chapter_1>&version=<timingVersionParam>, timeout=15)`.
   - Successful parse → normalize, store with `not_found=False`, populate LRU, serve.
   - Upstream `404` → store with `not_found=True` (TTL ~7 days), serve `200` with empty `verses`.
   - Upstream `5xx` / timeout / parse error → return `503 Retry-After: 30`. **Do not** poison the cache.
5. **Stale NDB hit** (older than TTL) → kick off a refresh in the background (Cloud Tasks queue or an `ndb.tasklets.toplevel`-wrapped fire-and-forget) and serve the stored copy synchronously. Keeps p99 latency flat.

**TTLs:**
- Positive cache: 30 days (timing data is essentially immutable for a given recording).
- Negative cache: **1 day** (short window so SABDA gap-fixes propagate quickly; the cost of the occasional re-fetch storm is negligible vs. user surprise).
- In-memory LRU: 1 hour bound (matches the `audio_file_exists` precedent in `addon/flask_app.py:105`).

**No pre-warm.** The cache fills lazily on the first user request per (version, book, chapter). The first hit on each chapter is slower (one upstream call to SABDA), but the upper bound is acceptable: ~200–500 ms added on a cold chapter. With a 30-day positive TTL, the second user — even days later — sees a hot hit.

**Adapter tables** live in `audio/adapters.py` and are lifted verbatim from PR #127's `BibleAudioRepository.kt` (the values below are the actual constants from `Alkitab/src/main/java/yuku/alkitab/base/util/BibleAudioRepository.kt` in that PR — they are the result of real experimentation against sabda.org and we should not reinvent them):

```python
# audio/adapters.py
AUDIO_ADAPTERS = {
    "preset/in-tb":  {"timing_version": "tbsuara", "audio_folder": "tb_alkitabsuara"},
    "preset/in-ayt": {"timing_version": "ayt",     "audio_folder": "ayt-ai-v2"},
    "preset/in-avb": {"timing_version": "avb",     "audio_folder": "avb"},
    "preset/en-kjv": {"timing_version": "kjv",     "audio_folder": "kjv"},
}

# Indexed by 0-based bookId. timing_name is Indonesian even for KJV — that's
# what karaoke.sabda.org/api/timming.php expects, regardless of audio version.
BOOK_META = [
    # ("folder",       "abbr", "timing_name")
    ("kejadian",       "kej",  "Kejadian"),         # 0  Genesis
    ("keluaran",       "kel",  "Keluaran"),         # 1  Exodus
    # … all 66 books …
    ("wahyu",          "wah",  "Wahyu"),            # 65 Revelation
]
```

The full table (~66 entries) is in PR #127's `BibleAudioRepository.kt` `BOOK_INFO` array. Copy it across into the Python module on backend implementation.

### 4.4 Why normalize instead of proxy passthrough?

The sabda.org karaoke API returns `{ timestamps: [{ time_start, duration, verse }] }`. If a client just hit sabda directly:

- Schema changes break every installed version.
- The SABDA site is occasionally slow (seconds); we want to serve from hot cache.
- We can't add fields (e.g. `durationMs`) without an upstream PR.

## 5. `GET /audio/chapter?versionId=…&bookId=…&chapter_1=…`

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

Protected by the project's standard `@require_staff` decorator (`alkitab-host/web/auth.py`) — the same mechanism that today guards `addon_admin_counter_stats`, `versions/admin/*`, etc.

Body empty. Clears the per-instance `web.cache` LRU for `audio/timing/*` keys (catalog included). Does **not** touch the NDB cache — that survives across reloads, since the timing data is essentially immutable. To force a re-fetch of NDB-cached entries, an admin can delete rows directly via the Datastore console; we don't expose a programmatic flush in v1 because we never expect to need one.

Response:

```json
{ "reloadedAt": 1713456000000, "evictedKeys": 184 }
```

## 7. Monitoring

- **Per-endpoint latency & error rate** (existing monitoring stack).
- **Cache hit ratio on `/audio/timing/*`** — if this drops, sabda is probably hot-reloading; alert.
- **SABDA error rate** — if upstream 5xx crosses 5% over 10 min, page on-call.
- **Catalog fetch rate** — baseline after rollout; spikes often mean client cache invalidation bugs.

## 8. Rollout

1. Land the new `audio/` package and register the blueprint in `web/flask_app.py`. Deploy via `cd deploy/web-py3 && bash deploy.sh`. No `dispatch.yaml` change is needed — the existing `*/*` catch-all routes `/audio/*` to `web-py3`.
2. **No pre-warm.** Cache fills on demand. First user on a given chapter will incur ~200–500 ms of additional latency; subsequent users hit the NDB cache for 30 days.
3. Verify the client (shipped with a bundled `assets/audio_catalog.json` mirroring the same four versions) continues to work if the backend is temporarily unreachable, so there is no hard coupling between app and backend releases.

## 9. Test plan

- [ ] Unit tests for `AUDIO_ADAPTERS` / `BOOK_META` lookups (boundary: bookId 38 → "pl", bookId 39 → "pb").
- [ ] Unit tests for URL construction (Psalms has 150 chapters → 3-digit formatting; every other book → 2-digit).
- [ ] Integration test against a mock sabda server with injected failures (5xx, timeout, malformed JSON).
- [ ] Contract test matching the JSON schema in §4 against the shapes the Android client expects (can be a shared JSON-schema file in both repos).
- [ ] Load test: catalog endpoint at 1000 rps with ETag → mostly 304s.

## 10. Security & abuse

- No auth on read endpoints (by design — `/devotion/get`, `/versions/*`, `/addon/audio/exists`, etc. are all the same shape).
- The existing endpoints have **no per-IP rate limiting** beyond App Engine's quotas. We will not add Flask-level rate limiting in v1 to stay consistent. A phone will issue one `/audio/catalog` per session plus ~10 `/audio/timing` and ~10 `/audio/chapter` per chapter played; load will be bounded by the install base. Revisit only if SABDA's CDN starts complaining.
- Reject any `versionId` not in `CATALOG.keys()` with `404` (prevents arbitrary strings from reaching the sabda.org upstream).
- Validate `bookId` is `0..65` and `chapter_1` is `1..150` at the handler boundary; reject otherwise with `400`.

## 11. Coordination with client

- Client lands its code with a bundled `audio_catalog.json` describing the four SABDA versions. If the backend is absent, the app still works — it just won't see new versions until the next app release.
- The client's first HTTP call on app start (after the existing version-config refresh) is `GET /audio/catalog`. Keep this fast and cached hard — slow catalog fetches won't break the feature (falls back to bundled), but they cost mobile data.

## 12. Future work

- **Analytics events** (when SABDA allows us to attribute usage): forward per-chapter play counts.
- **Alternative audio sources** (e.g. user-contributed readings or AI-generated voices) — the catalog's `versionId` + `chapterUrlTemplate` shape supports this already.
- **Timing regeneration** when a version updates — add a `timingGeneration` field to force client refresh per version.
- **HLS / DASH** for per-verse byte-range requests if pre-download or fine-grained scrubbing becomes a priority.

## 13. Open questions (resolved)

| Question | Answer found in the codebase |
|---|---|
| Datastore? | `google-cloud-ndb` (Cloud Datastore) — same as `addon`, `versions`, `sync`. New kind: `BibleAudioTimingCache`. |
| Scheduled jobs / pre-warming? | `cron.yaml` is currently empty. We add `POST /audio/admin/prewarm` as a one-shot admin endpoint instead of a cron entry; it can be re-hit manually after each deploy. |
| Admin auth? | `@require_staff` decorator from `web/auth.py` (Google OAuth2). Reused. |

The only question left for the **product** owner, not the engineering owner:

1. **Licensing attribution.** SABDA permits free distribution of audio, but should the in-app "About this audio" sheet display a longer attribution string than the per-version `copyrightNotice` field? If so, what text? (Default for v1: just the per-version line.)
