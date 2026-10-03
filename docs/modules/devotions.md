# Devotions Module

## Overview

Provides daily devotional reading content from multiple sources (primarily Indonesian devotional publishers). Articles are downloaded on demand and cached locally in the database.

## Key Files

- `Alkitab/src/main/java/yuku/alkitab/base/ac/DevotionActivity.java` — Devotion reader UI
- `Alkitab/src/main/java/yuku/alkitab/base/devotion/DevotionDownloader.kt`: keyed foreground/prefetch scheduler and retained loading state
- `Alkitab/src/main/java/yuku/alkitab/base/devotion/DevotionDownloadBackend.kt`: cancellable HTTP requests and cache persistence
- `Alkitab/src/main/java/yuku/alkitab/base/devotion/DevotionArticle.java` — Abstract base class
- Article implementations: `ArticleMorningEveningEnglish`, `ArticleFromSabda`, `ArticleMeidA`, `ArticleRoc`, `ArticleRenunganHarian`, `ArticleSantapanHarian`

## Download System

`DevotionActivity.display()` reads the local cache first. Ready articles remain readable offline; non-ready rows represent the server's `NG` response and show an unavailable message with Retry. Selecting an uncached reading schedules a download. Opening the screen or switching sources also prefetches from today forward: 15 days for most sources, 3 for Renungan Harian. Prefetch captures the source at scheduling time, skips existing cache rows, and prunes rows with `touchTime` older than 180 days.

`DevotionDownloader` uses two single-thread executors: one for the selected reading and one for prefetch. All queued and active requests share a registry keyed by `(source name, yyyyMMdd date)`, independent of article object equality. A queued selected reading is promoted to the foreground executor. A selected reading already active in prefetch reuses that request. Changing the selection cancels an obsolete foreground OkHttp call; its worker finishes before starting the latest selected request. Prefetch cannot occupy the foreground executor. Cancellation does not mark an obsolete selection as failed, and returning to a cancelling request restarts it after cancellation completes.

The backend rechecks the database before a normal download, so work queued before another cache update does not redownload it. It requests `GET /devotion/get?name={kind}&date={yyyymmdd}` using a client derived from `Connections.okHttp`, retaining the shared HTTP cache, user agent, and connect/read/write timeouts. A 30-second total call timeout bounds connection attempts and complete response-body reading. Responses close after reading. HTTP, network, parsing, and persistence exceptions end in `FAILED`; they never persist a partial reading. `NG` ends in `UNAVAILABLE` and is stored as a non-ready row. A failed or unavailable refresh preserves any ready cached reading.

The scheduler retains `QUEUED`, `DOWNLOADING`, `READY`, `FAILED`, and `UNAVAILABLE` states per key and publishes a replaying `StateFlow` revision. The screen observes it through `AppEvents.observeWhileStarted`, rereads only its current source/date, and restores current cache and status on start. The success-only `AppEvents.devotionDownloaded` bus remains available, but screen recovery does not depend on receiving that event. Terminal status history is bounded, with active and selected keys retained.

The loading panel uses the app's Material circular indicator and outlined button. Queued and downloading requests show distinct messages and a spinner; failures and unavailable readings show distinct messages and Retry. The drawer's Reload action also retries the selected reading. Retry bypasses the database and asks the HTTP cache to revalidate, while deduplicating any active request. Failed/unavailable states require explicit retry rather than restarting whenever the screen renders. Cached content remains visible during refresh, and Copy/Share are enabled only when a reading is rendered. Source/date are saved across activity recreation, status strings resolve through the activity's in-app language context, and reader colors apply to the panel in dark mode. Updates to other readings cannot replace the selected content or reset its scroll position.

## Verification

`DevotionDownloaderTest` controls executors and requests to cover queue promotion, slow prefetch isolation, queued/in-flight deduplication, source isolation, rapid selections, cancellation return races, Retry, and shutdown. `DevotionDownloadBackendTest` uses scripted HTTP responses and a localhost server for HTTP/network failures, `NG`, offline cache, refresh preservation, persistence failure, total timeout, and cancellation of a stalled response body. `DevotionActivityTest` uses Robolectric with controlled downloads and a local database to cover status, Retry, date/source switching, stopped/destroyed/recreated screens, in-app language, and reader colors. `DevotionDaoTest` verifies cache readiness for every source, including unavailable Morning & Evening rows. These tests do not call the production server.

## Article Parsing

Each article type has its own parser that:
1. Extracts the HTML/text body from the server response
2. Converts internal verse references (URLs) to verse callback spans
3. Handles links to other devotional articles
4. Separates web links from internal verse navigation links

## Available Devotion Types

Configured per-flavor in `AppConfig` via `R.xml.app_config`. The Indonesian version (`yuku_alkitab`) includes multiple devotion sources; the English version (`yuku_quick_bible`) includes Morning & Evening.

## Database

The `Devotion` table stores:
- `name` — devotion kind identifier
- `date` — article date (yyyymmdd)
- `body` — cached article content
- `readyToUse` — whether download is complete
- `touchTime`: last storage time
- `dataFormatVersion` — schema version for migration
