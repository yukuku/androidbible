# Backend Communication

## HTTP Client

All network requests use OkHttp3 configured in `Connections.kt`:
- Custom user agent string
- 50MB disk cache
- Long-timeout variant for large downloads
- Singleton `OkHttpClient` instance

## API Endpoints

### Base URL
- Release: `https://api.alkitab.app`
- Debug: Same base, but FCM functions use `http://10.0.3.2:5001/pulau-ribka/us-central1/` (emulator localhost)

### Bible Version Downloads

Version files are downloaded by `VersionDownloadWorker` (a `CoroutineWorker` using the shared OkHttp client). The worker reports progress via `setProgress(Data)`; `DownloadMapper` observes `WorkManager.getWorkInfoByIdFlow(uuid)` on a `MainScope` coroutine and mirrors `WorkInfo.State` into `DownloadManager.STATUS_*` constants for the version-list UI. Resume support: the worker checks the destination file's existing length and, if non-zero, sends `Range: bytes=N-` (the worker explicitly sends `Accept-Encoding: identity` so OkHttp does not transparently strip Content-Length on a gzip response). The download URL and metadata come from `VersionConfig`. The previous `PRDownloader`-based implementation (and the `PrDownloaderFixed` module) were removed in REM-19.

### Sync API

```
POST /sync/api/sync
```
Request body contains `simpleToken`, `installation_id`, `fcm_token`, and per-entity deltas. Response contains server-side deltas to apply locally. See [Sync Module](modules/sync.md).

### Devotion API

```
GET /devotion/get?name={kind}&date={yyyymmdd}
```
Returns devotional article text. Downloaded by `DevotionDownloader` (REM-05: a single-thread `ExecutorService` with a `LinkedBlockingDeque` queue and a clean `shutdown()` path).

### Song Book Downloads

Song books are fetched as serialized Java objects (gzip-compressed). Download URL is constructed from the server host + song book identifier.

### Version Config Updates

`VersionConfigUpdaterService` periodically fetches an updated version catalog JSON from the server. The response is cached at `files/version_config.json`, falling back to the bundled `assets/version_config.json`.

### Ribka Functions (Firebase Cloud Functions)

Firebase Cloud Functions hosted at `us-central1-pulau-ribka.cloudfunctions.net`:
- FCM token registration
- Push notification dispatch for sync
- Error/crash reporting via `RibkaReportActivity`

## Firebase Integration

- **Cloud Messaging (FCM)**: Push notifications to trigger sync on other devices
- **Crashlytics**: Crash reporting (release builds only)
- FCM token managed by `Fcm.java`, registered with backend on app start. A failed registration sets `Prefkey.fcm_registration_pending = true`; `Sync.sendFcmRegistrationId` retries up to three times on a daemon `ScheduledExecutorService` (1 min / 5 min / 30 min), and `App.staticInit()` re-enters `retryPendingFcmRegistrationIfNeeded` on next launch when the flag is set (REM-04).
- A placeholder `Alkitab/google-services.json` is committed so `plainDebug` builds out of the box. The real `google-services.json` (covering all production applicationIds) lives at `$ALKITAB_PROPRIETARY_DIR/google-services.json` and is copied per-flavor into the gitignored `Alkitab/src/<flavor>/google-services.json` at build time.

## File Downloads

Bible-version downloads run inside `VersionDownloadWorker` (see "Bible Version Downloads" above). Song-book downloads use raw OkHttp via `Connections.downloadCall(...)`. There is no shared "downloader" abstraction — each path uses the appropriate primitive.

## Content Provider (Outgoing)

The app exposes a read-only `ContentProvider` (`yuku.alkitab.base.cp.Provider`, ported to Kotlin in REM-16) for other apps to query Bible verses. This is outgoing communication — other apps query Alkitab, not the reverse.

See `AlkitabIntegration` module for the client-side API that other apps use.

## Share URLs

`ShareUrl` generates shareable verse links for copy/share operations. The URL format encodes the verse reference for web viewing.
