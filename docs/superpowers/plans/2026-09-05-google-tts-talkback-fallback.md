# Google TTS Fallback and TalkBack Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Route every “Dengarkan” request to recorded human narration first and expose Google Text-to-Speech only when no recording covers the passage or recorded playback has failed, with complete TalkBack semantics.

**Architecture:** `ListeningSourceResolver` owns source precedence and consumes recorded-audio availability from the audio subsystem. `BibleSpeechController` is an application-scoped state machine over a narrow `SpeechEngine`; the Android adapter binds explicitly to `com.google.android.tts`, while reader/search surfaces only construct passages and render state.

**Tech Stack:** Kotlin, Android `TextToSpeech`, coroutines `StateFlow`, existing `AudioPlaybackCoordinator`, AppCompat action modes, RecyclerView, Compose semantics where applicable, JUnit, Robolectric.

**Spec:** `docs/superpowers/specs/2026-09-05-offline-audio-tts-theme-search-design.md`

## Global Constraints

- Recorded human narration always wins when a covering set is available and has not terminally failed.
- Google TTS package `com.google.android.tts` is the only synthesis engine selected by this feature.
- TTS never starts silently after recorded audio fails; the user explicitly activates the offered fallback.
- Samsung TTS and other engines are not automatic substitutes.
- TTS reads the installed active-version text after Yuku formatting codes are removed.
- No network synthesis is requested; device acceptance requires the Google Indonesian/English offline voice.
- Every new control has a localized TalkBack label, state, role, and deterministic focus behavior.
- Every production behavior follows a red-green-refactor cycle.

---

### Task 1: Encode and test listening-source precedence

**Files:**
- Create: `Alkitab/src/main/java/yuku/alkitab/base/speech/ListeningSourceResolver.kt`
- Create: `Alkitab/src/test/java/yuku/alkitab/base/speech/ListeningSourceResolverTest.kt`

**Interfaces:**
- Consumes: `RecordedAudioAvailability`
- Produces: `ListeningSource.Recorded(audioId)`, `GoogleTts`, or `Unavailable(reason)`
- Produces: `resolve(availability, explicitTtsAfterFailure: Boolean): ListeningSource`

- [ ] **Step 1: Write the failing precedence tests**

```kotlin
class ListeningSourceResolverTest {
    @Test fun `covering recording always wins`() {
        assertEquals(ListeningSource.Recorded("human"), resolver.resolve(RecordedAudioAvailability.Available("human"), false))
    }

    @Test fun `no recording selects Google TTS`() {
        assertEquals(ListeningSource.GoogleTts, resolver.resolve(RecordedAudioAvailability.Unavailable, false))
    }

    @Test fun `recording failure requires explicit fallback`() {
        assertEquals(ListeningSource.Unavailable(Reason.RECORDED_FAILED), resolver.resolve(RecordedAudioAvailability.Failed("human"), false))
        assertEquals(ListeningSource.GoogleTts, resolver.resolve(RecordedAudioAvailability.Failed("human"), true))
    }
}
```

- [ ] **Step 2: Verify RED**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.speech.ListeningSourceResolverTest"
```

- [ ] **Step 3: Implement the exhaustive resolver**

```kotlin
fun resolve(availability: RecordedAudioAvailability, explicitTtsAfterFailure: Boolean): ListeningSource = when (availability) {
    is RecordedAudioAvailability.Available -> ListeningSource.Recorded(availability.audioId)
    RecordedAudioAvailability.Unavailable -> ListeningSource.GoogleTts
    is RecordedAudioAvailability.Failed -> if (explicitTtsAfterFailure) ListeningSource.GoogleTts else ListeningSource.Unavailable(Reason.RECORDED_FAILED)
}
```

- [ ] **Step 4: Verify GREEN**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.speech.ListeningSourceResolverTest"
```

- [ ] **Step 5: Commit the precedence policy**

```bash
git add Alkitab/src/main/java/yuku/alkitab/base/speech Alkitab/src/test/java/yuku/alkitab/base/speech
git commit -m "feat: prioritize recorded Bible narration"
```

---

### Task 2: Build the testable speech state machine

**Files:**
- Create: `Alkitab/src/main/java/yuku/alkitab/base/speech/SpeechPassage.kt`
- Create: `Alkitab/src/main/java/yuku/alkitab/base/speech/SpeechEngine.kt`
- Create: `Alkitab/src/main/java/yuku/alkitab/base/speech/SpeechState.kt`
- Create: `Alkitab/src/main/java/yuku/alkitab/base/speech/BibleSpeechController.kt`
- Create: `Alkitab/src/test/java/yuku/alkitab/base/speech/BibleSpeechControllerTest.kt`

**Interfaces:**
- Produces: `SpeechPassage(id: String, reference: String, languageTag: String, text: String)`
- Produces: `BibleSpeechController.state: StateFlow<SpeechState>`
- Produces: `speak(passages)`, `stop()`, `next()`, `previous()`, `onDone(id)`, `close()`

- [ ] **Step 1: Write failing state-machine tests using a fake engine**

```kotlin
@Test fun `speak cleans text selects locale and queues passages`() {
    controller.speak(listOf(passage("one", "id-ID", "@@@0Kasih@8itu sabar")))
    assertEquals("Kasih\nitu sabar", engine.spoken.single().text)
    assertEquals("id-ID", engine.languageTag)
    assertEquals(SpeechState.Speaking("one", 0, 1), controller.state.value)
}

@Test fun `starting TTS stops the previous audio owner`() {
    AudioPlaybackCoordinator.acquire(recordedSession)
    controller.speak(listOf(passage("one", "id-ID", "teks")))
    assertEquals(1, recordedSession.stopCount)
}

@Test fun `unsupported locale reports error without speaking`() {
    engine.languageAvailable = false
    controller.speak(listOf(passage("one", "id-ID", "teks")))
    assertEquals(SpeechState.Error(SpeechError.LANGUAGE_UNAVAILABLE), controller.state.value)
    assertTrue(engine.spoken.isEmpty())
}
```

- [ ] **Step 2: Verify RED**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.speech.BibleSpeechControllerTest"
```

- [ ] **Step 3: Implement the minimal state machine**

Implement `AudioPlaybackCoordinator.Session`; acquire immediately before speaking and release on stop, final completion, error, or close. Queue one utterance at a time so next/previous and stable state are deterministic. Use `FormattedVerseText.removeSpecialCodes` plus conversion of `@8` to a sentence break before stripping.

```kotlin
fun speak(passages: List<SpeechPassage>) {
    if (passages.isEmpty()) return
    queue = passages
    index = 0
    AudioPlaybackCoordinator.acquire(this)
    speakCurrent()
}

override fun stopPlayback() = stop()
```

- [ ] **Step 4: Run the speech tests and existing coordinator tests**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.speech.*" --tests "yuku.alkitab.base.audio.AudioPlaybackCoordinatorTest"
```

- [ ] **Step 5: Commit the controller**

```bash
git add Alkitab/src/main/java/yuku/alkitab/base/speech Alkitab/src/test/java/yuku/alkitab/base/speech
git commit -m "feat: add Bible speech queue controller"
```

---

### Task 3: Bind explicitly to Google Speech Services

**Files:**
- Create: `Alkitab/src/main/java/yuku/alkitab/base/speech/GoogleTextToSpeechEngine.kt`
- Create: `Alkitab/src/test/java/yuku/alkitab/base/speech/GoogleTextToSpeechEngineTest.kt`
- Modify: `Alkitab/src/main/AndroidManifest.xml`

**Interfaces:**
- Implements: `SpeechEngine`
- Produces: `GoogleTextToSpeechEngine.isGoogleEngineInstalled(context): Boolean`

- [ ] **Step 1: Write failing Robolectric contract tests**

```kotlin
@Test fun `engine package is fixed to Google`() {
    assertEquals("com.google.android.tts", GoogleTextToSpeechEngine.ENGINE_PACKAGE)
}

@Test fun `missing Google package is reported before initialization`() {
    packageManager.removePackage(GoogleTextToSpeechEngine.ENGINE_PACKAGE)
    assertFalse(GoogleTextToSpeechEngine.isGoogleEngineInstalled(context))
}
```

- [ ] **Step 2: Verify RED**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.speech.GoogleTextToSpeechEngineTest"
```

- [ ] **Step 3: Implement the Android adapter**

Initialize with `TextToSpeech(context, listener, ENGINE_PACKAGE)`, translate Android success/error/language constants into `SpeechEngine` results, and register an `UtteranceProgressListener`. Add the Android 11+ `<queries>` package entry for `com.google.android.tts` and the TTS service intent.

```kotlin
private val tts = TextToSpeech(context.applicationContext, ::onInit, ENGINE_PACKAGE)

override fun speak(id: String, text: String): Boolean =
    tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle.EMPTY, id) == TextToSpeech.SUCCESS
```

- [ ] **Step 4: Verify GREEN and manifest merge**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.speech.GoogleTextToSpeechEngineTest" :Alkitab:processPlainDebugMainManifest
```

- [ ] **Step 5: Commit the Google adapter**

```bash
git add Alkitab/src/main/java/yuku/alkitab/base/speech Alkitab/src/test/java/yuku/alkitab/base/speech Alkitab/src/main/AndroidManifest.xml
git commit -m "feat: use Google TTS for narration fallback"
```

---

### Task 4: Construct passages from active Bible text

**Files:**
- Create: `Alkitab/src/main/java/yuku/alkitab/base/speech/BiblePassageFactory.kt`
- Create: `Alkitab/src/test/java/yuku/alkitab/base/speech/BiblePassageFactoryTest.kt`

**Interfaces:**
- Produces: `fromAris(version: Version, versionId: String, aris: List<Int>): List<SpeechPassage>`
- Produces: `fromChapter(version, versionId, book, chapter1, startVerse1): List<SpeechPassage>`

- [ ] **Step 1: Write failing passage tests**

```kotlin
@Test fun `Indonesian preset produces Indonesian passages in ARI order`() {
    val passages = factory.fromAris(version, "preset/in-tb", listOf(ari2, ari1))
    assertEquals(listOf(ari1.toString(), ari2.toString()), passages.map { it.id })
    assertTrue(passages.all { it.languageTag == "id-ID" })
}

@Test fun `missing or blank verses are skipped`() {
    assertEquals(listOf(validAri.toString()), factory.fromAris(versionWithBlank, "preset/en-web", listOf(blankAri, validAri)).map { it.id })
}
```

- [ ] **Step 2: Verify RED**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.speech.BiblePassageFactoryTest"
```

- [ ] **Step 3: Implement deterministic text/locale construction**

Use `Version.reference(ari)` and `Version.loadVerseText(ari)`, strip formatting in the controller, and map version IDs/presets starting with `in-` to `id-ID`, `en-` to `en-US`, otherwise use `Version.locale` with a stable fallback.

- [ ] **Step 4: Verify GREEN**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.speech.BiblePassageFactoryTest"
```

- [ ] **Step 5: Commit the passage factory**

```bash
git add Alkitab/src/main/java/yuku/alkitab/base/speech Alkitab/src/test/java/yuku/alkitab/base/speech
git commit -m "feat: build speech passages from Bible versions"
```

---

### Task 5: Add reader “Dengarkan” routing

**Files:**
- Modify: `Alkitab/src/main/res/menu/context_isi.xml`
- Modify: `Alkitab/src/main/java/yuku/alkitab/base/actionmode/VerseActionModeActions.kt`
- Modify: `Alkitab/src/main/java/yuku/alkitab/base/actionmode/VerseActionModeController.kt`
- Modify: `Alkitab/src/main/java/yuku/alkitab/base/IsiActivity.kt`
- Modify: `Alkitab/src/main/res/values/strings.xml`
- Modify: `Alkitab/src/main/res/values-in/strings.xml`
- Modify: `Alkitab/src/test/java/yuku/alkitab/base/actionmode/VerseActionModeControllerTest.kt`

**Interfaces:**
- Adds action: `listenToSelectedVerses(selectedVerses1: IntArrayList)`
- Uses: `ListeningSourceResolver` and existing `AudioBarController.showFromVerse`

- [ ] **Step 1: Add failing action-mode policy tests**

```kotlin
@Test fun `listen action routes to recorded audio when available`() {
    actions.recordedAvailability = RecordedAudioAvailability.Available("human")
    clickMenu(R.id.menuListen)
    verify { actions.playAudioFromVerse(3) }
    verify(exactly = 0) { actions.speakSelectedVerses(any()) }
}

@Test fun `listen action routes to Google TTS when recording is absent`() {
    actions.recordedAvailability = RecordedAudioAvailability.Unavailable
    clickMenu(R.id.menuListen)
    verify { actions.speakSelectedVerses(any()) }
}
```

- [ ] **Step 2: Verify RED**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.actionmode.VerseActionModeControllerTest"
```

- [ ] **Step 3: Implement one menu item and explicit failure fallback**

Replace the narrowly named `menuPlayAudioFromVerse` surface with `menuListen`. The action resolves source once. A `RecordedAudioAvailability.Failed` state opens a dialog whose positive action explicitly calls TTS; cancel does nothing. Do not expose a second always-visible TTS action.

- [ ] **Step 4: Run reader/action tests**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.actionmode.*" --tests "yuku.alkitab.base.speech.*"
```

- [ ] **Step 5: Commit reader integration**

```bash
git add Alkitab/src/main/res/menu/context_isi.xml Alkitab/src/main/java/yuku/alkitab/base/actionmode Alkitab/src/main/java/yuku/alkitab/base/IsiActivity.kt Alkitab/src/main/res/values Alkitab/src/main/res/values-in Alkitab/src/test/java/yuku/alkitab/base/actionmode
git commit -m "feat: route reader listening to narration first"
```

---

### Task 6: Add search listening and TalkBack semantics

**Files:**
- Modify: `Alkitab/src/main/res/menu/context_search.xml`
- Modify: `Alkitab/src/main/res/layout/item_search_result.xml`
- Modify: `Alkitab/src/main/java/yuku/alkitab/base/ac/SearchActivity.kt`
- Create: `Alkitab/src/main/java/yuku/alkitab/base/accessibility/ListeningAccessibilityText.kt`
- Create: `Alkitab/src/test/java/yuku/alkitab/base/accessibility/ListeningAccessibilityTextTest.kt`
- Modify: `Alkitab/src/main/res/values/strings.xml`
- Modify: `Alkitab/src/main/res/values-in/strings.xml`

**Interfaces:**
- Adds search action: `menuListenSelected`
- Produces combined result description: reference, verse body, result position, activation hint

- [ ] **Step 1: Write failing accessibility-text tests**

```kotlin
@Test fun `result description includes rank reference text and action`() {
    assertEquals(
        "Hasil 2 dari 30. Yohanes 3:16. Karena begitu besar kasih Allah. Ketuk dua kali untuk membuka.",
        text.resultDescription(2, 30, "Yohanes 3:16", "Karena begitu besar kasih Allah.")
    )
}

@Test fun `listen state names recorded source before TTS`() {
    assertEquals("Dengarkan dengan rekaman naratif", text.listenAction(RecordedAudioAvailability.Available("human")))
    assertEquals("Bacakan dengan Google TTS", text.listenAction(RecordedAudioAvailability.Unavailable))
}
```

- [ ] **Step 2: Verify RED**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.accessibility.ListeningAccessibilityTextTest"
```

- [ ] **Step 3: Implement search action and one-focus-target rows**

Set the row `contentDescription` to the combined localized description and mark child labels `importantForAccessibility="no"`. Add `android:accessibilityLiveRegion="polite"` to the result-count/status view. The search action groups selected ARIs by chapter: recorded audio opens the first relevant chapter; no-recording/failure follows the same policy as reader selection.

- [ ] **Step 4: Run search, speech, and accessibility tests**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.ac.SearchActivityTest" --tests "yuku.alkitab.base.speech.*" --tests "yuku.alkitab.base.accessibility.*"
```

- [ ] **Step 5: Commit search/TalkBack integration**

```bash
git add Alkitab/src/main/res/menu/context_search.xml Alkitab/src/main/res/layout/item_search_result.xml Alkitab/src/main/java/yuku/alkitab/base/ac/SearchActivity.kt Alkitab/src/main/java/yuku/alkitab/base/accessibility Alkitab/src/main/res/values Alkitab/src/main/res/values-in Alkitab/src/test
git commit -m "feat: add accessible narration-first listening actions"
```

---

### Task 7: TTS/TalkBack regression gate

**Files:**
- Modify only files required by failures exposed in this task.

- [ ] **Step 1: Run all focused tests**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest --tests "yuku.alkitab.base.speech.*" --tests "yuku.alkitab.base.actionmode.*" --tests "yuku.alkitab.base.accessibility.*"
```

- [ ] **Step 2: Run the complete debug/release unit suites**

```bash
./gradlew :Alkitab:testPlainDebugUnitTest :Alkitab:testPlainReleaseUnitTest
```

- [ ] **Step 3: Build the debug APK**

```bash
./gradlew :Alkitab:assemblePlainDebug
```

- [ ] **Step 4: Inspect manifest and strings**

```bash
rg -n "com.google.android.tts|android.intent.action.TTS_SERVICE" Alkitab/build/intermediates/merged_manifest/plainDebug/processPlainDebugMainManifest/AndroidManifest.xml
rg -n "Dengarkan|Google TTS|rekaman naratif" Alkitab/src/main/res/values-in/strings.xml
```

- [ ] **Step 5: Record green output for the device-verification plan**

```bash
git status --short
```
