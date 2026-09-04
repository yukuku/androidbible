# WEB Narrative Audio and Offline Downloads Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the public-domain AudioTreasure WEB recording to Yuku's existing Media3 player and allow the current chapter to be downloaded, played offline, and removed.

**Architecture:** A strict built-in catalog supplies one audio set for the `en-web` preset and resolves every chapter through a validated JSON manifest. `AudioChapterDownloadStore` owns app-private files, while a `CoroutineWorker` performs atomic HTTPS downloads; the existing audio controller projects WorkManager state into the Compose audio bar.

**Tech Stack:** Kotlin, kotlinx.serialization, AndroidX Media3, WorkManager KTX, OkHttp, Compose Material 3, JUnit, Robolectric.

**Spec:** `docs/superpowers/specs/2026-09-05-offline-audio-tts-theme-search-design.md`

## Global Constraints

- Only preset `en-web` receives the built-in recording.
- The display title is `WEB — David Williams (public domain)` and must never imply TB/KJV audio.
- The catalog contains exactly 1,189 unique `(bookId, chapter)` entries covering 66 books.
- Recorded human narration is always the preferred listening source; this plan exposes deterministic availability/failure state for the later Google-TTS fallback plan.
- Local audio stays under app-private storage and requires no media permission.
- Existing remote audio behavior and exact search must remain unchanged.
- Kotlin is used for new production code; no default parameter values are introduced.
- Every production behavior follows a red-green-refactor cycle.

---

### Task 1: Check in the validated catalog and parser

**Files:**
- Create: `Alkitab/src/main/assets/audio/audiotreasure_web_manifest.json`
- Create: `Alkitab/src/main/java/yuku/alkitab/base/audio/builtin/BuiltInAudioCatalog.kt`
- Create: `Alkitab/src/test/java/yuku/alkitab/base/audio/builtin/BuiltInAudioCatalogTest.kt`
- Modify: `docs/content-licenses.md`

**Interfaces:**
- Produces: `BuiltInAudioCatalog.audioSetForPreset(presetName: String): AudioSet?`
- Produces: `BuiltInAudioCatalog.chapterUrl(audioId: String, bookId: Int, chapter1: Int): String?`

- [ ] **Step 1: Write the failing catalog tests**

```kotlin
@RunWith(RobolectricTestRunner::class)
class BuiltInAudioCatalogTest {
    @Test fun `WEB exposes the public-domain David Williams set`() {
        val set = BuiltInAudioCatalog(ApplicationProvider.getApplicationContext()).audioSetForPreset("en-web")
        assertEquals("audiotreasure-web-david-williams", set?.audioId)
        assertEquals((1..66).toSet(), set?.books_1)
        assertFalse(set!!.hasTiming)
    }

    @Test fun `non WEB presets have no built-in recording`() {
        assertNull(BuiltInAudioCatalog(ApplicationProvider.getApplicationContext()).audioSetForPreset("in-tb"))
    }

    @Test fun `manifest covers the full canon and known anomalies`() {
        val catalog = BuiltInAudioCatalog(ApplicationProvider.getApplicationContext())
        assertEquals(1189, catalog.chapterCountForTest())
        assertTrue(catalog.chapterUrl(AUDIO_ID, 24, 5)!!.endsWith("/25_Lam5.mp3"))
        assertTrue(catalog.chapterUrl(AUDIO_ID, 37, 14)!!.endsWith("/38_Zechariah_14.mp3"))
        assertNull(catalog.chapterUrl(AUDIO_ID, 66, 1))
    }
}
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.audio.builtin.BuiltInAudioCatalogTest"
```

Expected: compilation fails because `BuiltInAudioCatalog` does not exist.

- [ ] **Step 3: Add the manifest and minimal parser**

```kotlin
@Serializable
private data class Manifest(val chapterCount: Int, val tracks: List<Track>)

@Serializable
private data class Track(val bookId: Int, val chapter: Int, val url: String)

class BuiltInAudioCatalog(context: Context) {
    private val byAri = context.assets.open(MANIFEST_ASSET).use { input ->
        Json.decodeFromString<Manifest>(input.bufferedReader().readText()).tracks
            .associateBy { Ari.encode(it.bookId, it.chapter, 0) }
    }

    fun audioSetForPreset(presetName: String): AudioSet? = if (presetName == "en-web") {
        AudioSet(AUDIO_ID, TITLE, false, (1..66).toSet(), LOCATOR, null)
    } else null

    fun chapterUrl(audioId: String, bookId: Int, chapter1: Int): String? =
        if (audioId == AUDIO_ID) byAri[Ari.encode(bookId, chapter1, 0)]?.url else null
}
```

Copy the already validated `/tmp/audiotreasure-web-manifest.json` into the asset path, removing repeated attribution fields from each row while retaining top-level source/license metadata and the 1,189 coordinate/URL records. Record checksum `90d9847c22acc226eb2655198ab97dd027b69ab40aaa551ad313e810958f7dc1` for the source manifest in `docs/content-licenses.md`.

- [ ] **Step 4: Run the focused tests and verify GREEN**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.audio.builtin.BuiltInAudioCatalogTest"
```

- [ ] **Step 5: Commit the catalog slice**

```bash
git add Alkitab/src/main/assets/audio Alkitab/src/main/java/yuku/alkitab/base/audio/builtin Alkitab/src/test/java/yuku/alkitab/base/audio/builtin docs/content-licenses.md
git commit -m "feat: add public-domain WEB audio catalog"
```

---

### Task 2: Merge the built-in set into existing audio resolution

**Files:**
- Modify: `Alkitab/src/main/java/yuku/alkitab/base/audio/AudioSetsRepository.kt`
- Modify: `Alkitab/src/main/java/yuku/alkitab/base/audio/BibleAudioRepository.kt`
- Modify: `Alkitab/src/test/java/yuku/alkitab/base/audio/AudioSetsRepositoryTest.kt`
- Modify: `Alkitab/src/test/java/yuku/alkitab/base/audio/BibleAudioRepositoryTest.kt`

**Interfaces:**
- Consumes: `BuiltInAudioCatalog.audioSetForPreset` and `chapterUrl`
- Preserves: `AudioSetsRepository.setsFor(versionId: String): AudioSets`
- Preserves: `BibleAudioRepository.buildChapterUrl(versionId, audioId, bookId, chapter_1)`

- [ ] **Step 1: Add failing repository tests**

```kotlin
@Test fun `WEB built-in set resolves without HTTP`() = runTest {
    AudioSetsRepository.presetNameResolver = PresetNameResolver { "en-web" }
    AudioSetsRepository.http = AudioHttp { error("HTTP must not be called") }
    assertEquals(BuiltInAudioCatalog.AUDIO_ID, AudioSetsRepository.setsFor("web").sets.single().audioId)
}

@Test fun `built-in locator resolves through catalog`() = runTest {
    val url = BibleAudioRepository.buildChapterUrl("web", BuiltInAudioCatalog.AUDIO_ID, 39, 1)
    assertEquals("https://audiotreasure.com/content/WEBD_AT/40_Matt_01.mp3", url)
}
```

- [ ] **Step 2: Verify RED with the two existing test classes**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.audio.AudioSetsRepositoryTest" --tests "yuku.alkitab.base.audio.BibleAudioRepositoryTest"
```

Expected: built-in WEB resolves empty and the locator cannot become an HTTP URL.

- [ ] **Step 3: Implement the minimal built-in branch**

In `AudioSetsRepository.resolveEntry`, resolve the preset, consult the injected/default built-in catalog, and return `AudioSets(schema = 1, preset = presetName, sets = listOf(set))` before any HTTP call. In `BibleAudioRepository.buildChapterUrl`, resolve the set and branch only when `mp3UrlTemplate == BuiltInAudioCatalog.LOCATOR`; all remote templates retain the existing path.

```kotlin
val builtIn = builtInCatalog.audioSetForPreset(presetName)
if (builtIn != null) return Entry(AudioSets(1, presetName, listOf(builtIn)), null)
```

- [ ] **Step 4: Run focused tests and the existing audio suite**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.audio.*"
```

- [ ] **Step 5: Commit repository integration**

```bash
git add Alkitab/src/main/java/yuku/alkitab/base/audio Alkitab/src/test/java/yuku/alkitab/base/audio
git commit -m "feat: resolve built-in WEB recording"
```

---

### Task 3: Add the atomic app-private chapter store

**Files:**
- Create: `Alkitab/src/main/java/yuku/alkitab/base/audio/download/AudioChapterDownloadStore.kt`
- Create: `Alkitab/src/test/java/yuku/alkitab/base/audio/download/AudioChapterDownloadStoreTest.kt`

**Interfaces:**
- Produces: `localUri(audioId: String, bookId: Int, chapter1: Int): Uri?`
- Produces: `tempFile(audioId: String, bookId: Int, chapter1: Int): File`
- Produces: `publishTemp(audioId: String, bookId: Int, chapter1: Int, temp: File)`
- Produces: `remove(audioId: String, bookId: Int, chapter1: Int): Boolean`
- Produces: `isDownloaded(audioId: String, bookId: Int, chapter1: Int): Boolean`

- [ ] **Step 1: Write failing filesystem tests**

```kotlin
@Test fun `publish atomically replaces temp with final file`() {
    val temp = store.tempFile(AUDIO_ID, 0, 1).apply { writeBytes(byteArrayOf(0x49, 0x44, 0x33, 1)) }
    store.publishTemp(AUDIO_ID, 0, 1, temp)
    assertFalse(temp.exists())
    assertEquals(4L, store.localFile(AUDIO_ID, 0, 1).length())
}

@Test fun `coordinates cannot escape the fixed directory`() {
    assertFailsWith<IllegalArgumentException> { store.localFile("../outside", 0, 1) }
    assertFailsWith<IllegalArgumentException> { store.localFile(AUDIO_ID, -1, 1) }
}
```

- [ ] **Step 2: Verify RED**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.audio.download.AudioChapterDownloadStoreTest"
```

- [ ] **Step 3: Implement fixed-path validation and atomic publication**

```kotlin
private fun checkedFile(audioId: String, bookId: Int, chapter1: Int, suffix: String): File {
    require(audioId == BuiltInAudioCatalog.AUDIO_ID)
    require(bookId in 0..65 && chapter1 in 1..150)
    return File(root, "%02d/%03d.mp3%s".format(bookId + 1, chapter1, suffix))
}

fun publishTemp(audioId: String, bookId: Int, chapter1: Int, temp: File) {
    val final = checkedFile(audioId, bookId, chapter1, "")
    require(temp.length() > 3L)
    final.parentFile!!.mkdirs()
    check(temp.renameTo(final))
}
```

- [ ] **Step 4: Verify GREEN**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.audio.download.AudioChapterDownloadStoreTest"
```

- [ ] **Step 5: Commit the store**

```bash
git add Alkitab/src/main/java/yuku/alkitab/base/audio/download Alkitab/src/test/java/yuku/alkitab/base/audio/download
git commit -m "feat: add private audio chapter store"
```

---

### Task 4: Download chapters with WorkManager

**Files:**
- Create: `Alkitab/src/main/java/yuku/alkitab/base/audio/download/AudioChapterDownloadWorker.kt`
- Create: `Alkitab/src/main/java/yuku/alkitab/base/audio/download/AudioChapterDownloads.kt`
- Create: `Alkitab/src/test/java/yuku/alkitab/base/audio/download/AudioChapterDownloadWorkerTest.kt`
- Modify: `Alkitab/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: `AudioChapterDownloads.enqueue(audioId, bookId, chapter1, url)`
- Produces: `AudioChapterDownloads.workFlow(audioId, bookId, chapter1): Flow<DownloadState>`
- Produces: `DownloadState.NotDownloaded`, `Downloading(bytes, total)`, `Downloaded`, `Failed`

- [ ] **Step 1: Write failing worker tests with a local MockWebServer**

```kotlin
@Test fun `successful MPEG response is published`() = runTest {
    server.enqueue(MockResponse().setHeader("Content-Type", "audio/mpeg").setBody("ID3audio"))
    val result = runWorker(server.url("/chapter.mp3").toString())
    assertEquals(ListenableWorker.Result.success(), result)
    assertTrue(store.isDownloaded(AUDIO_ID, 0, 1))
}

@Test fun `HTML response fails and removes temp`() = runTest {
    server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody("not audio"))
    assertEquals(ListenableWorker.Result.failure(), runWorker(server.url("/chapter.mp3").toString()))
    assertFalse(store.tempFile(AUDIO_ID, 0, 1).exists())
}
```

- [ ] **Step 2: Verify RED**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.audio.download.AudioChapterDownloadWorkerTest"
```

- [ ] **Step 3: Implement unique, constrained, atomic work**

Use unique work name `audio:<audioId>:<bookId>:<chapter1>`, `NetworkType.CONNECTED`, and input data with the fixed coordinate and catalog URL. Reject non-HTTPS final responses and hosts other than `audiotreasure.com`.

```kotlin
response.use {
    if (!it.isSuccessful || it.request.url.isHttps.not() || it.request.url.host != ALLOWED_HOST) return Result.failure()
    if (it.body?.contentType()?.type != "audio") return Result.failure()
    temp.outputStream().use { sink -> it.body!!.byteStream().copyTo(sink) }
    store.publishTemp(audioId, bookId, chapter1, temp)
}
return Result.success()
```

- [ ] **Step 4: Run worker and store tests**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.audio.download.*"
```

- [ ] **Step 5: Commit the worker slice**

```bash
git add Alkitab/src/main/java/yuku/alkitab/base/audio/download Alkitab/src/test/java/yuku/alkitab/base/audio/download Alkitab/src/main/AndroidManifest.xml
git commit -m "feat: download WEB chapters for offline playback"
```

---

### Task 5: Prefer local media and expose download controls

**Files:**
- Modify: `Alkitab/src/main/java/yuku/alkitab/base/audio/BibleAudioRepository.kt`
- Modify: `Alkitab/src/main/java/yuku/alkitab/base/audio/AudioBarController.kt`
- Modify: `Alkitab/src/main/java/yuku/alkitab/base/audio/ui/AudioBar.kt`
- Modify: `Alkitab/src/main/res/values/strings.xml`
- Modify: `Alkitab/src/main/res/values-in/strings.xml`
- Modify: `Alkitab/src/test/java/yuku/alkitab/base/audio/BibleAudioRepositoryTest.kt`
- Create: `Alkitab/src/test/java/yuku/alkitab/base/audio/AudioBarControllerTest.kt`

**Interfaces:**
- Adds to `AudioBarUiState`: `downloadState: DownloadState?`
- Produces: `RecordedAudioAvailability.Available`, `Unavailable`, or `Failed`
- Adds commands: `DownloadChapter` and `RemoveDownloadedChapter`

- [ ] **Step 1: Add failing local-preference and command-projection tests**

```kotlin
@Test fun `downloaded chapter URI wins over network catalog URL`() = runTest {
    store.writeForTest(AUDIO_ID, 0, 1, "ID3audio".toByteArray())
    assertTrue(repository.buildChapterUrl("web", AUDIO_ID, 0, 1)!!.startsWith("file:"))
}

@Test fun `download command enqueues current built-in chapter`() {
    controller.dispatchForTest(AudioBarCommand.DownloadChapter)
    assertEquals(DownloadKey(AUDIO_ID, 0, 1), downloads.lastEnqueued)
}
```

- [ ] **Step 2: Verify RED**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.audio.BibleAudioRepositoryTest" --tests "yuku.alkitab.base.audio.AudioBarControllerTest"
```

- [ ] **Step 3: Implement local preference and Compose controls**

The download button appears only for the built-in WEB set. Use one semantic button whose label changes between `Unduh pasal untuk offline`, progress percentage, `Tersimpan offline`, and `Hapus unduhan pasal`. `AudioBarController` collects the current chapter's WorkManager flow and refreshes the media source after a successful download without auto-starting playback. It also exposes whether a covering recorded set exists and whether its most recent load reached a terminal failure; the later listening-source resolver uses this state and never guesses from connectivity alone.

```kotlin
val local = downloadStore.localUri(audioId, bookId, chapter_1)
return local?.toString() ?: builtInCatalog.chapterUrl(audioId, bookId, chapter_1)
```

- [ ] **Step 4: Run audio tests and debug build**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.audio.*" :Alkitab:assemblePlainDebug
```

- [ ] **Step 5: Commit the user-visible audio feature**

```bash
git add Alkitab/src/main/java/yuku/alkitab/base/audio Alkitab/src/main/res/values Alkitab/src/main/res/values-in Alkitab/src/test/java/yuku/alkitab/base/audio
git commit -m "feat: expose offline WEB chapter audio"
```

---

### Task 6: Audio regression gate

**Files:**
- Modify only files required by failures exposed in this task.

- [ ] **Step 1: Run all debug and release unit tests**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest :Alkitab:testPlainReleaseUnitTest
```

- [ ] **Step 2: Run lint for changed audio/resources**

```bash
./gradlew :Alkitab:lintPlainDebug
```

- [ ] **Step 3: Build the installable APK**

```bash
./gradlew :Alkitab:assemblePlainDebug
```

- [ ] **Step 4: Inspect packaged catalog and APK size**

```bash
unzip -l Alkitab/build/outputs/apk/plain/debug/*.apk | rg "audiotreasure_web_manifest|lib/.*/libonnxruntime"
du -h Alkitab/build/outputs/apk/plain/debug/*.apk
```

- [ ] **Step 5: Commit only a necessary regression fix; otherwise record the green command outputs in the final device plan**

```bash
git status --short
```
