# Offline Audio, TTS, TalkBack, and Theme Search — Design

**Status:** Approved design
**Date:** 2026-09-05
**Base:** Yuku Android Bible / Quick Bible at `c2f24b5f`
**Target device:** Samsung Galaxy S21+ (SM-G996B), Android 15

## Outcome

Yuku remains the application base. The open-source `plainDebug` build gains four connected capabilities:

1. a legally reusable narrative recording of the public-domain World English Bible (WEB), with streaming and explicit offline chapter downloads;
2. on-demand reading of the active Bible text and search results through Android `TextToSpeech`;
3. TalkBack-compatible labels, focus order, states, and actions for every new control;
4. an optional on-device semantic-search pack that accepts natural Indonesian or English themes, ranks real Bible verses without a network connection, and can pass those verses to TTS.

TB2 is explicitly excluded from this phase. No TB2 text, index, derived artifact, or unverified download is added. Existing authorized TB, public-domain KJV, WEB, and ASV modules remain usable.

## Product boundaries

- Search returns verses from an installed Bible version. It does not generate theology, commentary, summaries, or synthetic verse text.
- The feature is named **Pencarian tema offline**, not “AI answer,” so users understand that it ranks source verses rather than answering as an oracle.
- Exact text search remains unchanged and remains the default. Theme search is a separate mode in the existing search screen.
- Recorded audio is labeled **WEB — David Williams (public domain)** and is offered only while the active text is the `en-web` preset. It is never represented as TB, KJV, or another translation.
- TTS reads the active version's actual text and is strictly subordinate to recorded human narration. The UI calls it **Bacakan dengan Google TTS** and offers it only when no recorded set covers the passage or recorded playback has failed.
- Recorded audio can stream without downloading. A downloaded chapter must remain playable after Wi-Fi and mobile data are disabled.
- The local-search model is optional. Exact search, Bible reading, recorded audio, and TTS remain usable if the model pack is absent or deleted.

## Legal and attribution boundary

### Application code

The upstream Yuku repository is GPL-3.0. Modified distributed binaries therefore ship with corresponding source and GPL notices. Monetization is allowed by GPL-3.0, but recipients retain the GPL freedoms; this is not a proprietary-code conversion.

### Bible text and recorded audio

- WEB text comes from the retained eBible USFM archive and is public domain. “World English Bible” is a trademark; the text is not modified and keeps its name.
- The David Williams WEB recording comes from AudioTreasure. Its publisher states that the recording is released into the public domain without restriction.
- The in-app attribution screen records the work, narrator, source URL, public-domain statement, and local manifest checksum.
- The recording is not mixed with or labeled as Indonesian audio. Existing private/dev audio endpoints are not evidence of redistribution permission and are not used by the new built-in catalog.
- The validated catalog covers 1,189 unique Protestant-canon chapters. AudioTreasure's HTML contains a broken Ratapan/Lamentations 5 URL; the live `25_Lam5.mp3` path is used. Zechariah 14 is absent from the HTML listing but its live URL is included.

### Model and runtime

- `ibm-granite/granite-embedding-97m-multilingual-r2` is Apache-2.0 licensed. The app records the model revision, model/tokenizer SHA-256 values, and Apache-2.0 notice.
- ONNX Runtime Android is MIT licensed and its notice is included.
- The semantic verse matrix is derived only from the public-domain WEB text. It contains numeric embeddings and ARI coordinates, not TB/TB2 text.

## Architecture

The implementation is split into three independently testable subsystems and one device-verification pass. They share only small interfaces:

```text
Existing Bible Version
  ├── exact SearchEngine (unchanged)
  ├── ThemeSearchEngine ──> ranked ARIs ──> existing SearchAdapter
  └── ListeningSourceResolver
        ├── recorded narration (first choice)
        └── BibleSpeechController (Google TTS fallback only)

WEB preset
  └── BuiltInAudioCatalog ──> existing BibleAudioService/Media3
                                └── AudioDownloadStore (optional local file)

TalkBack
  └── semantics on reader, search-mode, result, speech, download, and audio controls
```

Each subsystem must preserve existing remote audio, exact search, verse selection, and navigation behavior.

## 1. Public-domain WEB narrative audio

### Catalog

`BuiltInAudioCatalog` exposes one `AudioSet` only for preset `en-web`:

- `audioId`: `audiotreasure-web-david-williams`
- title: `WEB — David Williams (public domain)`
- coverage: books 1–66
- timing: absent
- media locator: `builtin:audiotreasure-web`

`AudioSetsRepository` returns this built-in set immediately for `en-web`. Other presets continue through the existing backend repository unchanged. `BibleAudioRepository` recognizes the built-in locator and delegates chapter resolution to a checked-in compact manifest. Every manifest row contains `bookId`, `chapter`, and absolute HTTPS URL. Unknown coordinates return `null`; they never fall back to a guessed URL.

Because the source has no verse timing, the existing player disables verse highlighting and verse-skip for this set. Chapter play/pause, seek, speed, previous/next chapter, background playback, lock-screen controls, audio focus, and notification behavior continue through the existing Media3 service.

### Offline downloads

`AudioDownloadStore` owns files under the app-private `files/audio/audiotreasure-web/` directory. The current chapter can be downloaded from the audio sheet. Downloads write to a temporary sibling, verify that the result is a non-empty MPEG audio response, then atomically rename it. Cancellation or failure deletes only the temporary file. A missing local file while offline produces a recorded-playback failure and therefore makes the explicit Google TTS fallback available.

When a verified local file exists, the repository returns its `file:` URI; otherwise it returns the HTTPS URL. Users can remove the current chapter download. The UI reports the four durable states: not downloaded, downloading with byte progress, downloaded, and failed/retry. No broad media permission is requested because files remain app-private.

The first implementation supports current-chapter download and removal. Whole-Bible bulk download is deliberately excluded: the source is roughly 1.2 GB and an unbounded bulk action would conflict with the lightweight-app objective.

## 2. On-demand TTS and TalkBack

### Speech controller

`ListeningSourceResolver` is the single policy boundary for every reader and search “Dengarkan” action. It chooses recorded human narration whenever an audio set covers the passage. It chooses Google Text-to-Speech only when no recorded set covers the passage. If a recorded source exists but fails to load or is unreachable, the audio error surface offers an explicit Google TTS fallback; TTS never takes over silently.

`BibleSpeechController` wraps the Google Android `TextToSpeech` engine (`com.google.android.tts`) behind a small `SpeechEngine` interface so queueing and state are unit-testable without synthesizing sound in tests. It accepts immutable `SpeechPassage` values containing a stable utterance ID, display reference, language tag, and plain text. Samsung TTS or another installed engine is not selected automatically; if Google Speech Services is absent or disabled, the app explains how to install or enable it.

The controller:

- strips Yuku formatting codes before speaking;
- chooses Indonesian for `in-*`, English for `en-*`, and otherwise uses the version locale;
- checks `isLanguageAvailable` and exposes a clear unavailable-language error;
- stops recorded Bible or hymn audio through the existing `AudioPlaybackCoordinator` before speaking;
- supports play, pause/stop, next, and previous across a passage queue;
- releases the engine during application shutdown/tests;
- never claims offline capability merely because the Google engine exists: the UI explains that the matching Google offline voice must be installed in Android settings.

Reader and search actions first pass through `ListeningSourceResolver`. A selected verse with timed recorded audio starts that recording at the verse; an untimed recording starts its chapter and explains that verse-level seeking is unavailable. Only the no-recording or explicit playback-failure path offers TTS for the current chapter, selected verses, one result, or the displayed ranked result list. TTS reads the active version text, not the WEB semantic-index text.

### TalkBack contract

All new interactive views have a concise localized label, role, state, and action. Important examples:

- search mode announces “Pencarian tepat” or “Pencarian tema offline”;
- model-pack action announces absent/downloading/ready/error plus progress;
- each theme result exposes one combined focus target: reference, verse text, relevance order, and “ketuk dua kali untuk membuka”; 
- the listen action announces whether it will use recorded narration or Google TTS fallback, its stopped/playing state, and the current reference;
- the audio download action announces download state and does not rely on color/icon alone.

Focus stays on the initiating control when a download or search state updates. Results announce their count once through an accessibility live region. Existing verse-row content descriptions remain intact.

Device verification enables Samsung TalkBack only for the accessibility pass, records the original setting, and restores it afterward.

## 3. Offline local theme search

### Model pack

The optional pack consists of:

- Granite 97M multilingual R2 quantized ONNX model;
- its exact tokenizer data;
- a checked-in versioned manifest containing URLs, lengths, SHA-256 values, licenses, and semantic-index schema compatibility.

The model and tokenizer are downloaded from the official IBM Granite Hugging Face repository into app-private storage. Downloads are resumable through WorkManager, use a temporary file, verify byte length and SHA-256, and publish atomically. A checksum or compatibility failure leaves the previous valid pack untouched. Users can remove the pack.

The public-domain WEB semantic index is a checked-in int8 matrix keyed by ARI plus one scale value per vector. It has 31,102 verse slots and is approximately 12 MiB. The index-build script is checked in and deterministic: it reads the retained WEB module, applies the Granite tokenizer, CLS pooling, L2 normalization, and per-vector symmetric int8 quantization. The script emits its source/module/model hashes into index metadata.

### Tokenization and inference

The Android tokenizer implements the model's byte-level BPE contract, including UTF-8 byte-to-Unicode mapping, pre-tokenization, merge ranks, special tokens, attention mask, truncation, and padding. Golden tests compare Indonesian punctuation, English, numbers, apostrophes, and non-ASCII inputs against token IDs produced by the official Hugging Face tokenizer.

ONNX Runtime receives `input_ids` and `attention_mask`. The engine takes the CLS vector, normalizes it, and computes cosine scores against the int8 WEB matrix. Model loading and inference happen off the main thread. Only one session is retained; low-memory cleanup closes it.

### Hybrid ranking

Theme search combines three deterministic signals:

1. semantic cosine similarity against the WEB embedding matrix;
2. BM25 lexical relevance built from the active installed version's plain verse text;
3. a small checked-in Indonesian/English synonym map for high-value concepts such as anxiety, forgiveness, courage, wisdom, love, creation, grief, hope, and strength.

The lexical index is created locally from the active version on first theme search and cached by version ID plus version modification fingerprint. It stores normalized tokens and ARIs, not a second copy of copyrighted verse text. Changing or updating a version invalidates that cache.

Score fusion uses reciprocal-rank fusion rather than incomparable raw scores. Book filters from the current search screen apply before the final top 30 results. Missing verses or versification differences are skipped. Results always load reference and text from the selected active version.

Initial acceptance themes in Indonesian are:

- `kecemasan dan kekhawatiran`
- `kekuatan saat lemah`
- `kasih yang sabar`
- `meminta hikmat`
- `keberanian menghadapi ketakutan`
- `mengampuni orang lain`
- `penciptaan dunia`

The quality gate is relevance, not one hard-coded proof text: at least four of the top five results for each theme must be independently judged directly relevant, and the agreed canonical verse set must appear within the top 50. The benchmark fixture stores expected references, not copyrighted verse bodies.

### Search UI and failure behavior

The existing `SearchActivity` gains an accessible two-option mode selector. Exact mode uses `SearchEngine.searchByGrep` unchanged. Theme mode:

- shows an install action if the model pack is absent;
- shows progress while downloading or locally preparing;
- performs no HTTP request once the pack is ready;
- shows ranked results in the existing list and preserves open/copy/select behavior;
- offers “Dengarkan hasil”; the shared source resolver uses recorded narration where available and exposes Google TTS only for no-recording/failure cases.

If the pack is missing, corrupt, incompatible, or cannot load, the app reports the specific recovery action and exact search remains available. There is no silent network fallback and no cloud inference fallback.

## Resource and performance budgets

- Base APK increase excluding native ONNX Runtime: no more than 15 MiB compressed, primarily the int8 WEB index and metadata.
- Optional downloaded model/tokenizer: no more than 125 MiB combined.
- arm64 ONNX native runtime: expected approximately 26 MiB uncompressed; release packaging must contain only supported ABIs already selected by the project.
- Warm query-to-results target on the S21+: under 1 second for 31,102 verses.
- Cold model-open target on the S21+: under 5 seconds, with visible progress and no main-thread stall.
- Exact-search regression: no material behavior or latency change.
- Downloaded audio is accounted separately and removable chapter by chapter.

## Security and privacy

- Theme queries, verse text, embeddings, and Google TTS passages never leave the device; Android network synthesis is not requested, and acceptance requires an installed offline voice.
- Model/audio downloads use fixed HTTPS origins and reject redirects to non-HTTPS destinations.
- Model artifacts are accepted only after SHA-256 and length validation.
- Paths are fixed by catalog IDs and ARIs; user text never becomes a filesystem path or URL.
- Temporary files are scoped to the exact target and deleted on failure.
- Logs contain model/catalog IDs and error categories, never full user queries or Bible passages.

## Test strategy

### Automated unit and Robolectric tests

- built-in audio visibility only for `en-web`, complete coordinate lookup, anomaly paths, and unknown-coordinate rejection;
- local-file preference, atomic download success, checksum/content failure, cancellation cleanup, and removal;
- listening-source precedence, recorded-playback failure fallback, Google TTS text cleaning, locale choice, queue navigation, unavailable engine/language, state transitions, and audio mutual exclusion;
- byte-level BPE golden token IDs and truncation/padding;
- int8 cosine calculation, reciprocal-rank fusion, book filters, cache invalidation, corrupt/incompatible pack handling;
- search-mode state and preservation of existing exact-search behavior;
- accessibility labels/states and live-region behavior for new controls.

### Build and regression gate

Run both debug and release unit suites plus the debug APK build with JDK 21:

```bash
./gradlew :Alkitab:testPlainDebugUnitTest \
  :Alkitab:testPlainReleaseUnitTest \
  :Alkitab:assemblePlainDebug
```

### Samsung S21+ acceptance pass

1. Install the newly built `plainDebug` APK over the existing debug app without clearing user data.
2. Open WEB, stream Genesis 1, verify Media3 notification, pause/resume, speed, and next chapter.
3. Download one WEB chapter, disable Wi-Fi and mobile data, force-stop/reopen the app, and play that chapter fully from the local file.
4. Verify that a passage with working recorded narration does not offer or start TTS. Then use a passage without a recorded set (and separately simulate a recorded-load failure), invoke Google TTS, and verify start/stop plus selected-verse reading with the Indonesian offline voice.
5. Install/verify the local model pack, enable airplane-equivalent offline conditions, run all seven Indonesian benchmark themes, open results, and exercise “Dengarkan hasil”: recorded narration first where available, Google TTS only on the allowed fallback path.
6. Enable TalkBack, traverse every new control and one result flow without coordinate taps, confirm announcements and focus stability, then restore the original accessibility setting.
7. Confirm exact search still finds known TB and WEB phrases and existing recorded-audio behavior for other configured presets is unchanged.

Evidence consists of Gradle output, ADB package/version state, network-disabled checks, media-session state, UI hierarchy/accessibility output, and screenshots for the principal flows.

## Delivery sequence

The work is executed as separate plans in this order:

1. built-in WEB audio catalog and offline chapter downloads;
2. TTS controller plus reader/search actions and TalkBack semantics;
3. Granite model pack, tokenizer, WEB semantic index, hybrid theme search, and TTS handoff;
4. full regression build and S21+ online/offline/accessibility acceptance pass.

Each subsystem lands through test-first commits. A later failure cannot be hidden by narrowing the target: completion requires all four sequence items and the device evidence above.
