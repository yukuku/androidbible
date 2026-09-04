# Offline Local Theme Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a fully offline Indonesian/English theme-search mode that uses Granite embeddings plus local lexical ranking to return real verses from the active installed Bible version.

**Architecture:** The base APK carries a public-domain WEB int8 verse matrix and deterministic metadata. An optional, checksum-verified pack downloads IBM's quantized ONNX model and exact tokenizer into app-private storage; `ThemeSearchEngine` fuses semantic, BM25, and synonym ranks via reciprocal-rank fusion and returns ARIs to the existing search result UI.

**Tech Stack:** Kotlin, ONNX Runtime Android 1.24.3, WorkManager KTX, OkHttp, kotlinx.serialization, memory-mapped `ByteBuffer`, Python 3 build tooling (`onnxruntime`, `tokenizers`, `numpy`), JUnit, Robolectric.

**Spec:** `docs/superpowers/specs/2026-09-05-offline-audio-tts-theme-search-design.md`

## Global Constraints

- Search is retrieval only: it never generates, rewrites, summarizes, or completes Bible text.
- Queries and active-version text never leave the device.
- Exact `SearchEngine.searchByGrep` remains the default and is not modified internally.
- Model revision is pinned to `835ad14087e140460703cf0fae09f97d469d65c2`.
- Model file is `onnx/model_quint8_avx2.onnx`, length `98,247,878`, SHA-256 `a6022dd8220ea6f6595562a1328ee216f4a94faa55362f2f4747c80f1e78772e`.
- Tokenizer file is `tokenizer.json`, length `25,301,672`, SHA-256 `4f2842d568e2724370aec203652a42ac783c7937f8347a1a2cc7506d71f1582f`.
- Android inference uses CLS pooling and L2 normalization exactly as the IBM model card specifies.
- The semantic index is derived only from public-domain WEB and contains no protected Indonesian verse text.
- Warm search target on the S21+ is below one second; no model work runs on the main thread.
- Every production behavior follows a red-green-refactor cycle.

---

### Task 1: Pin ONNX Runtime and the model-pack manifest

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `Alkitab/build.gradle.kts`
- Modify: `Alkitab/proguard-rules.pro`
- Create: `Alkitab/src/main/assets/offline_search/pack_manifest.json`
- Create: `Alkitab/src/main/java/yuku/alkitab/base/search/theme/pack/ModelPackManifest.kt`
- Create: `Alkitab/src/test/java/yuku/alkitab/base/search/theme/pack/ModelPackManifestTest.kt`
- Modify: `docs/content-licenses.md`

**Interfaces:**
- Produces: `ModelPackManifest.load(context): ModelPackManifest`
- Adds dependency: `com.microsoft.onnxruntime:onnxruntime-android:1.24.3`

- [ ] **Step 1: Write failing manifest tests**

```kotlin
@RunWith(RobolectricTestRunner::class)
class ModelPackManifestTest {
    @Test fun `manifest pins immutable official artifacts`() {
        val manifest = ModelPackManifest.load(ApplicationProvider.getApplicationContext())
        assertEquals("835ad14087e140460703cf0fae09f97d469d65c2", manifest.revision)
        assertEquals(98247878L, manifest.model.length)
        assertEquals("a6022dd8220ea6f6595562a1328ee216f4a94faa55362f2f4747c80f1e78772e", manifest.model.sha256)
        assertEquals(25301672L, manifest.tokenizer.length)
        assertTrue(manifest.model.url.startsWith("https://huggingface.co/ibm-granite/"))
    }
}
```

- [ ] **Step 2: Verify RED**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.search.theme.pack.ModelPackManifestTest"
```

- [ ] **Step 3: Add dependency, manifest, parser, and notices**

Use immutable URLs:

```text
https://huggingface.co/ibm-granite/granite-embedding-97m-multilingual-r2/resolve/835ad14087e140460703cf0fae09f97d469d65c2/onnx/model_quint8_avx2.onnx
https://huggingface.co/ibm-granite/granite-embedding-97m-multilingual-r2/resolve/835ad14087e140460703cf0fae09f97d469d65c2/tokenizer.json
```

Add the ONNX Runtime R8 keep rule from its official Android documentation and record Apache-2.0/MIT attributions in `docs/content-licenses.md`.

- [ ] **Step 4: Verify GREEN and dependency resolution**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.search.theme.pack.ModelPackManifestTest" :Alkitab:dependencies --configuration plainDebugRuntimeClasspath
```

- [ ] **Step 5: Commit pack metadata**

```bash
git add gradle/libs.versions.toml Alkitab/build.gradle.kts Alkitab/proguard-rules.pro Alkitab/src/main/assets/offline_search/pack_manifest.json Alkitab/src/main/java/yuku/alkitab/base/search/theme/pack Alkitab/src/test/java/yuku/alkitab/base/search/theme/pack docs/content-licenses.md
git commit -m "build: pin Granite offline search runtime"
```

---

### Task 2: Add atomic, resumable model-pack storage

**Files:**
- Create: `Alkitab/src/main/java/yuku/alkitab/base/search/theme/pack/ModelPackStore.kt`
- Create: `Alkitab/src/main/java/yuku/alkitab/base/search/theme/pack/ModelPackDownloadWorker.kt`
- Create: `Alkitab/src/main/java/yuku/alkitab/base/search/theme/pack/ModelPackRepository.kt`
- Create: `Alkitab/src/test/java/yuku/alkitab/base/search/theme/pack/ModelPackStoreTest.kt`
- Create: `Alkitab/src/test/java/yuku/alkitab/base/search/theme/pack/ModelPackDownloadWorkerTest.kt`

**Interfaces:**
- Produces: `ModelPackState.Absent`, `Downloading(bytes,total)`, `Ready(model,tokenizer)`, `Failed(reason)`
- Produces: `install()`, `remove()`, and `state: Flow<ModelPackState>`

- [ ] **Step 1: Write failing store/worker tests**

```kotlin
@Test fun `valid artifacts publish as one compatible pack`() {
    store.stageForTest("model", validModelBytes)
    store.stageForTest("tokenizer", validTokenizerBytes)
    store.publish(manifest)
    assertIs<ModelPackState.Ready>(store.inspect(manifest))
}

@Test fun `hash mismatch preserves previous ready pack`() = runTest {
    store.installKnownGoodForTest(manifest)
    server.enqueue(MockResponse().setBody("corrupt"))
    assertEquals(Result.failure(), runWorker())
    assertIs<ModelPackState.Ready>(store.inspect(manifest))
}
```

- [ ] **Step 2: Verify RED**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.search.theme.pack.ModelPackStoreTest" --tests "yuku.alkitab.base.search.theme.pack.ModelPackDownloadWorkerTest"
```

- [ ] **Step 3: Implement versioned staging and verified publication**

Store under `files/offline-search/packs/<revision>/`; write `.part` files, resume with HTTP `Range`, update WorkManager progress, verify exact length and streaming SHA-256, then atomically rename both artifacts and a final `READY.json` marker. Reject final non-HTTPS URLs and hosts outside `huggingface.co`/its official CDN redirect hosts.

```kotlin
if (digest.hex() != artifact.sha256 || file.length() != artifact.length) {
    file.delete()
    return Result.failure(workDataOf(ERROR to "checksum"))
}
```

- [ ] **Step 4: Run pack tests**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.search.theme.pack.*"
```

- [ ] **Step 5: Commit model-pack management**

```bash
git add Alkitab/src/main/java/yuku/alkitab/base/search/theme/pack Alkitab/src/test/java/yuku/alkitab/base/search/theme/pack
git commit -m "feat: download verified offline search pack"
```

---

### Task 3: Implement the exact byte-level BPE tokenizer

**Files:**
- Create: `Alkitab/src/main/java/yuku/alkitab/base/search/theme/model/ByteLevelBpeTokenizer.kt`
- Create: `Alkitab/src/main/java/yuku/alkitab/base/search/theme/model/TokenizerData.kt`
- Create: `Alkitab/src/test/java/yuku/alkitab/base/search/theme/model/ByteLevelBpeTokenizerTest.kt`

**Interfaces:**
- Produces: `encode(text: String, maxLength: Int): TokenizedInput`
- Produces: `TokenizedInput(inputIds: LongArray, attentionMask: LongArray)`

- [ ] **Step 1: Write failing golden tests from the official tokenizer**

```kotlin
@Test fun `Indonesian and Unicode match official token IDs`() {
    assertContentEquals(longArrayOf(179934, 32242, 2374, 4120, 6815, 237, 179938), tokenizer.encode("kasih yang sabar", 32).unpaddedIds())
    assertContentEquals(longArrayOf(179934, 74, 962, 307, 18089, 783, 16189, 8519, 7078, 179938), tokenizer.encode("kecemasan & pengharapan", 32).unpaddedIds())
    assertContentEquals(longArrayOf(179934, 33397, 758, 2989, 179938), tokenizer.encode("God’s love", 32).unpaddedIds())
    assertContentEquals(longArrayOf(179934, 56, 2252, 27327, 180, 18, 25, 1078, 179938), tokenizer.encode("Yohanes 3:16", 32).unpaddedIds())
    assertContentEquals(longArrayOf(179934, 165798, 1466, 6391, 25681, 22618, 179938), tokenizer.encode("mengampuni orang lain", 32).unpaddedIds())
}

@Test fun `truncation preserves start and return tokens`() {
    val encoded = tokenizer.encode("satu dua tiga empat lima enam", 5)
    assertEquals(5, encoded.inputIds.size)
    assertEquals(179934, encoded.inputIds.first())
    assertEquals(179938, encoded.inputIds.last())
}
```

- [ ] **Step 2: Verify RED**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.search.theme.model.ByteLevelBpeTokenizerTest"
```

- [ ] **Step 3: Implement tokenizer parsing and BPE**

Parse only `model.vocab`, `model.merges`, `pre_tokenizer`, and special-token IDs from `tokenizer.json` using `JsonReader` so the 25 MiB file is not duplicated as a giant object graph. Implement GPT-style bytes-to-Unicode, the tokenizer's stored regex splitting, cached pair merges, start token `179934`, return token `179938`, and end/pad token from the file. Padding sets attention-mask zero.

- [ ] **Step 4: Verify all golden and edge tests GREEN**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.search.theme.model.ByteLevelBpeTokenizerTest"
```

- [ ] **Step 5: Commit tokenizer**

```bash
git add Alkitab/src/main/java/yuku/alkitab/base/search/theme/model Alkitab/src/test/java/yuku/alkitab/base/search/theme/model
git commit -m "feat: implement Granite byte-level tokenizer"
```

---

### Task 4: Build and check in the public-domain WEB int8 index

**Files:**
- Create: `tools/offline_search/build_web_index.py`
- Create: `tools/offline_search/requirements.txt`
- Create: `tools/offline_search/test_build_web_index.py`
- Create: `Alkitab/src/main/assets/offline_search/web_granite97m_r2.int8`
- Create: `Alkitab/src/main/assets/offline_search/web_granite97m_r2.metadata.json`
- Create: `Alkitab/src/test/java/yuku/alkitab/base/search/theme/index/SemanticIndexReaderTest.kt`
- Create: `Alkitab/src/main/java/yuku/alkitab/base/search/theme/index/SemanticIndexReader.kt`

**Interfaces:**
- Python emits: header magic/version/dim/count, `(ari:int32, scale:float32, vector:int8[384])` rows
- Kotlin produces: `topK(query: FloatArray, allowedBooks: BooleanArray, k: Int): List<RankedAri>`

- [ ] **Step 1: Write failing Python quantization/determinism tests**

```python
def test_quantization_preserves_nearest_neighbors():
    vectors = normalized_fixture_vectors()
    encoded = quantize(vectors)
    assert top_k(vectors[0], vectors, 3) == top_k_int8(vectors[0], encoded, 3)

def test_writer_is_byte_deterministic(tmp_path):
    first = build_fixture_index(tmp_path / "a.int8")
    second = build_fixture_index(tmp_path / "b.int8")
    assert first.read_bytes() == second.read_bytes()
```

- [ ] **Step 2: Verify RED**

```bash
python3 -m unittest tools.offline_search.test_build_web_index -v
```

- [ ] **Step 3: Implement build script and generate the real index**

Read `content-sources/english/engwebp_usfm.zip`, normalize verses in canonical Protestant order, assert 66 books/1,189 chapters/31,102 verse slots, tokenize with the pinned official tokenizer, run the pinned quantized ONNX model, CLS-pool, L2-normalize, and symmetrically quantize each vector. Metadata includes all source/model/tokenizer/script hashes.

```bash
python3 tools/offline_search/build_web_index.py \
  --usfm content-sources/english/engwebp_usfm.zip \
  --model /tmp/granite-embedding-97m-r2-quint8.onnx \
  --tokenizer /tmp/granite-embedding-97m-r2-tokenizer.json \
  --output Alkitab/src/main/assets/offline_search/web_granite97m_r2.int8
```

- [ ] **Step 4: Write failing Kotlin reader tests, implement memory-mapped scoring, and verify**

```kotlin
@Test fun `reader rejects incompatible dimensions`() {
    assertFailsWith<IndexFormatException> { SemanticIndexReader(openFixture(dimensions = 383)) }
}

@Test fun `reader ranks allowed books only`() {
    val result = reader.topK(query, booleanArrayOf(true, false), 2)
    assertTrue(result.all { Ari.toBook(it.ari) == 0 })
}
```

Run:

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.search.theme.index.SemanticIndexReaderTest"
```

- [ ] **Step 5: Commit build tooling and index**

```bash
git add tools/offline_search Alkitab/src/main/assets/offline_search/web_granite97m_r2.* Alkitab/src/main/java/yuku/alkitab/base/search/theme/index Alkitab/src/test/java/yuku/alkitab/base/search/theme/index
git commit -m "feat: add public-domain WEB semantic index"
```

---

### Task 5: Run Granite query inference on Android

**Files:**
- Create: `Alkitab/src/main/java/yuku/alkitab/base/search/theme/model/OnnxEmbeddingSession.kt`
- Create: `Alkitab/src/main/java/yuku/alkitab/base/search/theme/model/GraniteEmbeddingEngine.kt`
- Create: `Alkitab/src/test/java/yuku/alkitab/base/search/theme/model/GraniteEmbeddingEngineTest.kt`

**Interfaces:**
- Produces: `embed(text: String): FloatArray` of length 384
- Produces: `close()`

- [ ] **Step 1: Write failing inference-boundary tests with a fake ONNX session**

```kotlin
@Test fun `embedding passes exact tensors selects CLS and normalizes`() {
    session.hiddenState = arrayOf(
        arrayOf(
            floatArrayOf(3f, 4f) + FloatArray(382),
            FloatArray(384) { 99f },
        )
    )
    val vector = engine.embed("kasih yang sabar")
    assertEquals(384, vector.size)
    assertEquals(1.0f, sqrt(vector.sumOf { (it * it).toDouble() }).toFloat(), 1e-4f)
    assertArrayEquals(floatArrayOf(0.6f, 0.8f), vector.copyOf(2), 1e-6f)
    assertArrayEquals(longArrayOf(179934, 32242, 2374, 4120, 6815, 237, 179938), session.lastInputIds)
    assertArrayEquals(LongArray(7) { 1L }, session.lastAttentionMask)
}

@Test fun `close releases the retained ONNX session exactly once`() {
    engine.close()
    engine.close()
    assertEquals(1, session.closeCount)
}
```

- [ ] **Step 2: Verify RED**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.search.theme.model.GraniteEmbeddingEngineTest"
```

- [ ] **Step 3: Implement ONNX inputs, CLS pooling, normalization, and close**

```kotlin
val result = session.run(mapOf("input_ids" to idsTensor, "attention_mask" to maskTensor))
val hidden = result[0].value as Array<Array<FloatArray>>
return l2Normalize(hidden[0][0])
```

`OnnxEmbeddingSession` is the narrow seam that converts `LongArray` inputs into ORT tensors and returns the first output as a hidden-state array. Its production implementation creates and closes tensors/results with `use`. Retain one session per ready pack and dispatch model open/inference on `Dispatchers.Default`. The real-model numeric reference for the S21+ acceptance query `kasih yang sabar` begins `[-0.024145354, -0.017853657, -0.017045664, 0.015889600, 0.042929146, -0.014993993, 0.020726370, -0.016147053]` after CLS pooling and normalization; verify that on-device in the acceptance plan without bundling the 94 MiB model as a JVM test fixture.

- [ ] **Step 4: Verify GREEN and release minification**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.search.theme.model.GraniteEmbeddingEngineTest" :Alkitab:assemblePlainRelease
```

- [ ] **Step 5: Commit inference engine**

```bash
git add Alkitab/src/main/java/yuku/alkitab/base/search/theme/model Alkitab/src/test/java/yuku/alkitab/base/search/theme/model
git commit -m "feat: run Granite embeddings on device"
```

---

### Task 6: Build active-version BM25 and synonym ranks

**Files:**
- Create: `Alkitab/src/main/assets/offline_search/theme_synonyms_id_en.json`
- Create: `Alkitab/src/main/java/yuku/alkitab/base/search/theme/rank/QueryNormalizer.kt`
- Create: `Alkitab/src/main/java/yuku/alkitab/base/search/theme/rank/Bm25Index.kt`
- Create: `Alkitab/src/main/java/yuku/alkitab/base/search/theme/rank/Bm25IndexCache.kt`
- Create: `Alkitab/src/main/java/yuku/alkitab/base/search/theme/rank/ReciprocalRankFusion.kt`
- Create: `Alkitab/src/test/java/yuku/alkitab/base/search/theme/rank/ThemeRankingTest.kt`

**Interfaces:**
- Produces: `Bm25Index.search(tokens, allowedBooks, k): List<RankedAri>`
- Produces: `ReciprocalRankFusion.fuse(rankings: List<List<RankedAri>>, k: Int, constant: Int): List<RankedAri>`

- [ ] **Step 1: Write failing normalization/BM25/RRF tests**

```kotlin
@Test fun `Indonesian synonym expansion is bounded and deterministic`() {
    assertEquals(listOf("cemas", "kecemasan", "khawatir", "kekhawatiran"), normalizer.expand("kecemasan"))
}

@Test fun `BM25 ranks matching rare term before common term`() {
    val index = Bm25Index.fromDocuments(fixtureDocuments)
    assertEquals(rareMatchAri, index.search(listOf("hikmat"), allBooks, 3).first().ari)
}

@Test fun `RRF uses rank not raw score`() {
    assertEquals(expectedAri, ReciprocalRankFusion.fuse(listOf(semanticRanks, lexicalRanks), 10, 60).first().ari)
}
```

- [ ] **Step 2: Verify RED**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.search.theme.rank.ThemeRankingTest"
```

- [ ] **Step 3: Implement normalization, sparse postings, cache fingerprint, and fusion**

Normalize with `Locale.ROOT`, Unicode NFKC, letters/digits only, and a checked-in bounded synonym map. Build postings from `FormattedVerseText.removeSpecialCodes(version.loadVerseText(ari))`. Cache a compact binary index under `cacheDir/offline-search/lexical/<fingerprint>.bin`, where the fingerprint includes version ID, `MVersion.modifyTime`, schema version, and synonym hash.

- [ ] **Step 4: Run ranking tests**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.search.theme.rank.*"
```

- [ ] **Step 5: Commit lexical ranking**

```bash
git add Alkitab/src/main/assets/offline_search/theme_synonyms_id_en.json Alkitab/src/main/java/yuku/alkitab/base/search/theme/rank Alkitab/src/test/java/yuku/alkitab/base/search/theme/rank
git commit -m "feat: add local Bible lexical ranking"
```

---

### Task 7: Compose the theme-search engine and benchmark gate

**Files:**
- Create: `Alkitab/src/main/java/yuku/alkitab/base/search/theme/ThemeSearchEngine.kt`
- Create: `Alkitab/src/main/java/yuku/alkitab/base/search/theme/ThemeSearchResult.kt`
- Create: `Alkitab/src/test/resources/offline_search/indonesian_theme_benchmark.json`
- Create: `Alkitab/src/test/java/yuku/alkitab/base/search/theme/ThemeSearchEngineTest.kt`

**Interfaces:**
- Produces: `search(version, versionId, query, bookIds, limit): ThemeSearchResponse`
- Response carries ranked ARIs, elapsed time, pack state, and non-sensitive error category.

- [ ] **Step 1: Write failing orchestration and quality tests**

```kotlin
@Test fun `search fuses semantic and lexical ranks and applies book filter`() = runTest {
    val response = engine.search(version, versionId, "meminta hikmat", newTestamentOnly, 30)
    assertTrue(response.results.all { Ari.toBook(it.ari) >= 39 })
    assertTrue(response.results.any { it.ari == james1_5 })
}

@Test fun `missing pack never falls back to network`() = runTest {
    pack.state = ModelPackState.Absent
    val response = engine.search(version, versionId, "kasih", allBooks, 30)
    assertEquals(ThemeSearchError.PACK_REQUIRED, response.error)
    assertEquals(0, network.calls)
}
```

- [ ] **Step 2: Verify RED**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.search.theme.ThemeSearchEngineTest"
```

- [ ] **Step 3: Implement orchestration and benchmark fixture**

Retrieve semantic top 200 and lexical top 200, fuse via RRF constant 60, filter missing/blank active-version verses, and return top 30. The benchmark fixture covers the seven approved Indonesian themes and stores canonical ARIs only.

- [ ] **Step 4: Run engine and quality tests**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.search.theme.*"
```

Expected: every theme has at least four independently reviewed relevant verses in top five and every canonical set has a hit within top 50; update synonym data or ranking weights only from evidence, never hard-code query-to-verse answers.

- [ ] **Step 5: Commit the engine**

```bash
git add Alkitab/src/main/java/yuku/alkitab/base/search/theme Alkitab/src/test/java/yuku/alkitab/base/search/theme Alkitab/src/test/resources/offline_search
git commit -m "feat: add hybrid offline theme search"
```

---

### Task 8: Add the accessible theme-search UI

**Files:**
- Modify: `Alkitab/src/main/res/layout/activity_search.xml`
- Create: `Alkitab/src/main/res/layout/search_theme_pack_status.xml`
- Modify: `Alkitab/src/main/java/yuku/alkitab/base/ac/SearchActivity.kt`
- Create: `Alkitab/src/main/java/yuku/alkitab/base/search/theme/SearchMode.kt`
- Modify: `Alkitab/src/main/res/values/strings.xml`
- Modify: `Alkitab/src/main/res/values-in/strings.xml`
- Create: `Alkitab/src/test/java/yuku/alkitab/base/ac/SearchModeTest.kt`
- Create: `Alkitab/src/test/java/yuku/alkitab/base/ac/SearchActivityTest.kt`

**Interfaces:**
- Adds modes: `SearchMode.Exact` and `SearchMode.ThemeOffline`
- Exact mode continues to call only `SearchEngine.searchByGrep`.

- [ ] **Step 1: Write failing mode and UI-state tests**

```kotlin
@Test fun `exact remains default`() {
    assertEquals(SearchMode.Exact, SearchMode.restore(null))
}

@Test fun `theme submit invokes only local engine`() {
    activity.selectMode(SearchMode.ThemeOffline)
    activity.submit("keberanian menghadapi ketakutan")
    verify { themeEngine.search(any(), any(), any(), any(), 30) }
    verify(exactly = 0) { exactEngine.search(any(), any()) }
}
```

- [ ] **Step 2: Verify RED**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.ac.SearchModeTest" --tests "yuku.alkitab.base.ac.SearchActivityTest"
```

- [ ] **Step 3: Implement mode selector, pack states, ranked results, and listening handoff**

Use an accessible Material single-choice control above the search box. Theme mode shows install/progress/remove/retry states for the optional pack, disables submit only while pack preparation is active, sends results through the existing adapter, announces count once via a polite live region, and passes “Dengarkan hasil” to the narration-first resolver from the TTS plan.

- [ ] **Step 4: Run search/UI tests and debug build**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.ac.Search*" --tests "yuku.alkitab.base.search.theme.*" :Alkitab:assemblePlainDebug
```

- [ ] **Step 5: Commit UI integration**

```bash
git add Alkitab/src/main/res/layout/activity_search.xml Alkitab/src/main/res/layout/search_theme_pack_status.xml Alkitab/src/main/java/yuku/alkitab/base/ac/SearchActivity.kt Alkitab/src/main/java/yuku/alkitab/base/search/theme/SearchMode.kt Alkitab/src/main/res/values Alkitab/src/main/res/values-in Alkitab/src/test/java/yuku/alkitab/base/ac
git commit -m "feat: expose offline theme search mode"
```

---

### Task 9: Offline-search regression, packaging, and performance gate

**Files:**
- Modify only files required by failures exposed in this task.

- [ ] **Step 1: Run the complete theme-search suite**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.search.theme.*" --tests "yuku.alkitab.base.ac.Search*"
```

- [ ] **Step 2: Run all debug/release unit tests**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest :Alkitab:testPlainReleaseUnitTest
```

- [ ] **Step 3: Build debug and minified release APKs**

```bash
./gradlew :Alkitab:assemblePlainDebug :Alkitab:assemblePlainRelease
```

- [ ] **Step 4: Inspect ABI and size budgets**

```bash
unzip -l Alkitab/build/outputs/apk/plain/debug/*.apk | rg "web_granite97m_r2|libonnxruntime|pack_manifest"
du -h Alkitab/build/outputs/apk/plain/debug/*.apk Alkitab/build/outputs/apk/plain/release/*.apk
```

Expected: base index is present, model/tokenizer are absent from APK, arm64 runtime is present, base compressed-size increase excluding runtime is at most 15 MiB.

- [ ] **Step 5: Record green outputs and exact APK path for the device plan**

```bash
git status --short
find Alkitab/build/outputs/apk/plain/debug -name '*.apk' -print
```
