# Devotions Module

## Overview

Provides daily devotional reading content from multiple sources (primarily Indonesian devotional publishers). Articles are downloaded on demand and cached locally in the database.

## Key Files

- `Alkitab/src/main/java/yuku/alkitab/base/ac/DevotionActivity.java` — Devotion reader UI
- `Alkitab/src/main/java/yuku/alkitab/base/devotion/DevotionDownloader.kt` — Background downloader (ported to Kotlin in REM-16; single-thread `ExecutorService` + `LinkedBlockingDeque` queue with clean shutdown, REM-05)
- `Alkitab/src/main/java/yuku/alkitab/base/devotion/DevotionArticle.java` — Abstract base class
- Article implementations: `ArticleMorningEveningEnglish`, `ArticleFromSabda`, `ArticleMeidA`, `ArticleRoc`, `ArticleRenunganHarian`, `ArticleSantapanHarian`

## Download System

`DevotionDownloader` runs work on a single-thread `ExecutorService`, fed by a `LinkedBlockingDeque` (so a `take()` provides natural backpressure):
- **Endpoint**: `GET /devotion/get?name={kind}&date={yyyymmdd}` via `Connections.downloadString(url)`
- Queue supports both LIFO (front) and FIFO (back) insertion for prioritization
- `shutdown()` sets a `volatile` flag and calls `executor.shutdownNow()`; the loop handles `InterruptedException` by re-interrupting and breaking
- Articles cached in the `Devotion` database table
- `touchTime` tracks access for cache management
- On completion the downloader emits an `AppEvents` `SharedFlow` event (replaced the `LocalBroadcastManager` broadcast in REM-03)

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
- `touchTime` — last access time
- `dataFormatVersion` — schema version for migration
