# Devotions Module

The devotional reader offers daily articles from sources configured for each app flavor, including Morning & Evening and Indonesian devotional publishers. Articles are downloaded on demand and cached for offline reading; nearby dates are prefetched in the background.

The selected source and date take priority over prefetching. Downloads are deduplicated, and switching readings cancels obsolete foreground work. The screen shows waiting, downloading, failure, and unavailable-content states, with Retry for failures or content that is not yet published. Refresh failures preserve cached readings.

The reader restores its selection and download status across activity lifecycle changes and follows the app's language and reading appearance settings. Source-specific article parsers handle formatting and links to Bible passages or other readings.

Key entry points:

- `DevotionActivity.kt`: reader, date/source navigation, loading UI, and lifecycle handling.
- `DevotionDownloader.kt`: coroutine scheduling, prioritization, deduplication, and retained status.
- `DevotionDownloadBackend.kt`: HTTP retrieval and cache updates.
- `DevotionArticle` and its source implementations: article parsing and links.
- `DevotionDao`: access to the devotional cache.

Downloader, backend, reader, and cache tests cover priority, retry, offline behavior, and lifecycle recovery using controlled requests and local storage, without the production server.
