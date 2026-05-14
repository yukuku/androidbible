# ~~REM-19: Replace PRDownloaderFixed with WorkManager Downloads~~ ✅ COMPLETED (2026-05-13)

**Addresses:** TD-12
**Module:** Downloads
**BRICE:** B=2 R=2 I=2 C=3 E=5 → **2.8**
**Phase:** 3 — Modernization

**Outcome:** Bible-version downloads now run inside [VersionDownloadWorker](../../Alkitab/src/main/java/yuku/alkitab/base/util/VersionDownloadWorker.kt), a `CoroutineWorker` that uses the shared `Connections.okHttp` client and reports progress via `setProgress(Data)`. [DownloadMapper](../../Alkitab/src/main/java/yuku/alkitab/base/util/DownloadMapper.kt) was rewritten from a Java enum singleton into a Kotlin `class` with a companion `instance`, internally observing `WorkManager.getWorkInfoByIdFlow(uuid)` on a `MainScope` coroutine and mirroring `WorkInfo.State` into `DownloadManager.STATUS_*` constants so the existing `VersionListFragment` polling UI (`getStatus(downloadKey)` / `getDownloadProgress(downloadKey)`) keeps working unchanged. On terminal `SUCCEEDED`, the observer hands off to the existing [VersionDownloadCompleteReceiver.onReceive](../../Alkitab/src/main/java/yuku/alkitab/base/br/VersionDownloadCompleteReceiver.java); on `FAILED`, it shows the existing network / server error dialog and calls `remove()`.

Resume support is implemented as documented in the original step 3: the worker checks the destination file's existing length and, if non-zero, sends `Range: bytes=N-`. A 206 Partial Content response appends to the file; a 200 response wipes and restarts. To keep on-disk byte counts in sync with wire byte counts (and so progress reporting and Range offsets remain accurate), the worker explicitly sends `Accept-Encoding: identity` — without this OkHttp would transparently decode any `Content-Encoding: gzip` response and strip Content-Length, breaking both progress reporting and Range-based resume. The `.yes` file itself is still gzip-precompressed at the application layer; `OptionalGzipInputStream` in `VersionDownloadCompleteReceiver` continues to handle that on the receiver side.

Song-book downloads in [SongBookUtil.downloadSongBook](../../Alkitab/src/main/java/yuku/alkitab/songs/SongBookUtil.kt) were never routed through PRDownloader — they already use raw OkHttp via `Connections.downloadCall(...)` — so the original step 5 of the plan was a no-op. The full module deletion took the rest of the steps with it: `:PrDownloaderFixed` removed from `settings.gradle.kts`, `implementation(project(":PrDownloaderFixed"))` removed from `Alkitab/build.gradle.kts`, `PRDownloader.initialize(...)` removed from `App.java`, the `PRDownloaderOkHttpClient` adapter deleted, and the entire `PrDownloaderFixed/` directory deleted.

**Verification:** `./gradlew assemblePlainDebug testPlainDebugUnitTest testPlainReleaseUnitTest` all pass. End-to-end smoke test on an Android 15 emulator: BBE (Bible in Basic English) preset downloaded successfully from `api.alkitab.app`, was inserted into the version DB, and the reader correctly switched to it and rendered Revelation 9 in English.

---

[← Back to remediation index](../tech-debt-remediation.md)
