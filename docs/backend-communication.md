# Backend Communication

## HTTP Client

All network requests use OkHttp3 (v5.1.0) configured in `Connections.kt`:
- Custom user agent string
- 50MB disk cache
- Long-timeout variant for large downloads
- Singleton `OkHttpClient` instance

## API Endpoints

### Base URL
- Release: `https://api.alkitab.app`
- Debug: Same base, but FCM functions use `http://10.0.3.2:5001/pulau-ribka/us-central1/` (emulator localhost)

### Bible Version Downloads

Version files are downloaded via `PRDownloader` (patched library in `PrDownloaderFixed` module). The download URL and metadata come from `VersionConfig`.

### Sync API

```
POST /sync/api/sync
```
Request body contains `simpleToken`, `installation_id`, `fcm_token`, and per-entity deltas. Response contains server-side deltas to apply locally. See [Sync Module](modules/sync.md).

### Devotion API

```
GET /devotion/get?name={kind}&date={yyyymmdd}
```
Returns devotional article text. Downloaded by `DevotionDownloader` on a background thread.

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
- FCM token managed by `Fcm.java`, registered with backend on app start
- `google-services.json` is gitignored — not available in open-source build

## File Downloads

`PRDownloader` (patched as `PrDownloaderFixed`) handles file downloads with:
- Progress callbacks
- Resume support
- Configured in `App.staticInit()`

## Content Provider (Outgoing)

The app exposes a read-only `ContentProvider` (`yuku.alkitab.base.cp.Provider`) for other apps to query Bible verses. This is outgoing communication — other apps query Alkitab, not the reverse.

See `AlkitabIntegration` module for the client-side API that other apps use.

## Share URLs

`ShareUrl` generates shareable verse links for copy/share operations. The URL format encodes the verse reference for web viewing.
