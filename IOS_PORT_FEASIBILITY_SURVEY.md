# iOS Port Feasibility Survey

A detailed assessment of the effort and risks involved in porting the Alkitab / Quick Bible Android app to native iOS as a near 1:1 port of its current feature set.

> **TL;DR** — A true *line-by-line* 1:1 port is **not feasible** because 40–50% of the codebase consists of Android-framework-specific UI (Activities, Fragments, RecyclerView, custom Views, AppWidget, ContentProvider). A faithful *functional* 1:1 port that preserves behavior, data formats, database schema, sync protocol, and feature set **is feasible** with no technical show-stoppers. Estimated effort: **~1,850 engineer-hours** (roughly 6–7 months for a 2-person team, or 3–4 months for a 3–4 person team) — see §15 for the per-area breakdown. The Android data layer, binary formats, sync protocol, and core models are highly portable; the UI has to be rewritten. Recommended UI stack is a **SwiftUI shell with a UIKit reader** — SwiftUI for the lists/forms/widget, UIKit for the verse rendering and split-view reader where attributed text, gesture composition, and scroll coupling push SwiftUI past its comfort zone. See §9 for the breakdown.

---

## 1. Scope of "1:1 port"

Before evaluating feasibility we have to define what a "1:1 port" can reasonably mean for a cross-platform rewrite:

| Interpretation | Feasible? | Notes |
|---|---|---|
| Literal source-level translation (Java/Kotlin → Swift line by line) | **No** | Tied to Android frameworks (Activity lifecycle, RecyclerView adapters, SharedPreferences, Parcelable, ContentProvider, AppWidget, Android View class hierarchy). There is no useful Swift equivalent for 1:1 translation of these constructs. |
| Feature parity, identical on-disk data formats, same sync protocol, visual design adapted to iOS conventions | **Yes** | Core logic is platform-neutral. UIKit/SwiftUI equivalents exist for all runtime UI needs. |
| Bit-for-bit compatibility of Bible files (YES2), reading plans (RPB), and cloud-sync payloads | **Yes** | All binary formats are little-endian, stream-based, and free of Java serialization. See §5. |
| Exact replica of AppWidget behavior on the home screen | **Partially** | iOS WidgetKit has a different model (static timeline snapshots, no event-driven updates); feature parity is possible but the UX will differ. See §12. |

The rest of this document assumes "**faithful functional port**" — same features, same data, same sync — and estimates the effort accordingly.

---

## 2. Codebase size & language mix

| | Files | LOC | Share |
|---|---|---|---|
| Java | 326 | 36,684 | 70% |
| Kotlin | 124 | 15,602 | 30% |
| C++ (JNI) | 1 | 54 | <1% |
| **Total** | **451** | **52,340** | |

### Per-module LOC

| Module | LOC | Role | iOS-relevant? |
|---|---|---|---|
| `Alkitab` (main app) | 35,488 | Activities, fragments, UI, widgets, sync, DB | Most of the porting work is here |
| `AlkitabYes2` | 1,454 | YES2 binary Bible format | Portable (see §5) |
| `BiblePlus` | 955 | PalmBible+ PDB import | Portable |
| `Snappy` | 852 (+54 C++) | Snappy compression JNI wrapper | Replaceable (see §4) |
| `AlkitabModel` | 586 | Core models (`Ari`, `Version`, `Book`, …) | Portable |
| `BintexReader`/`Writer` | 951 | Binary record format | Portable |
| `AlkitabIo` | 432 | `BibleReader`, gzip, UTF-8 | Portable |
| `AmbilWarna` | 387 | Color picker dialog | Needs rewrite (iOS has `UIColorPickerViewController` since iOS 14) |
| `AlkitabIntegration` | 317 | Inter-app ContentProvider API | Rewrite as iOS URL scheme / Universal Links |
| `Afw` | 304 | Preferences wrapper, adapter base | Rewrite against `UserDefaults` |
| Others (`FlowLayout`, `KpriModel`, `PrDownloaderFixed`, `ImportedDesktopVerseUtil`) | ~2,800 | Mixed utilities | Mixed — see per-feature sections |

### Where the mass is

- `Alkitab/src/main/java/yuku/alkitab/base/IsiActivity.kt` is **~2,321 lines** — the monolithic main reader activity (gestures, split view, action mode, navigation history, font zoom, volume-key nav). CLAUDE.md flags it at ~2,900 lines; either way it dominates the UI port.
- The next largest units are `VerseRenderer`, `FormattedTextRenderer`, `VersesControllerImpl`, and `InternalDbHelper`.
- Kotlin is concentrated in the main app (42% of main-app LOC); library modules remain mostly Java.

---

## 3. Android API surface

This is the single biggest factor in porting cost. The app relies heavily on framework-level Android APIs:

| API | Use | Severity | iOS equivalent |
|---|---|---|---|
| `android.view.View` / custom View subclasses | `VerseItem`, `Floater`, `AttributeView`, `FlowLayout`, `AmbilWarnaPrefWidgetView` | HIGH | `UIView` subclasses; custom drawing via Core Graphics / `CALayer` |
| `RecyclerView` | 91 references; primary list rendering for verses, markers, search, songs, reading plans | HIGH | `UITableView`, `UICollectionView` |
| `TextView` + `Spannable` | Inline styling of verse text (red-letter, italics, indentation, footnote links) | HIGH | `NSAttributedString` with attributes; `UITextView` for rich text; `UILabel` for simpler cases |
| `SharedPreferences` | 353 references; `Prefkey` enum with 100+ keys | HIGH | `UserDefaults` |
| `Parcelable` | 43 references; `KpriModel.Song` stores `Parcelable` bytes *in the database* | MEDIUM | Must be replaced; use `Codable` + JSON/Data. Storage format must be migrated or re-serialized on port. |
| `ContentProvider` | `yuku.alkitab.base.cp.Provider` (read-only verse lookup for other apps) | MEDIUM | iOS has no ContentProvider; replace with URL schemes, Universal Links, or App Group shared container |
| `BroadcastReceiver` | 27 references; daily widget, FCM, package changes | MEDIUM | `NotificationCenter` + BGTaskScheduler |
| `WorkManager` | 9 references; background sync jobs | MEDIUM | `BGAppRefreshTask`, `BGProcessingTask`, or `URLSession` background configuration |
| `Service` | 6 services in manifest (widget service, FCM, etc.) | MEDIUM | No direct equivalent; iOS background execution is heavily restricted — replace with Background Tasks / push-triggered fetches |
| `AppWidget` | Daily verse home-screen widget | MEDIUM | WidgetKit (see §12) |
| `PendingIntent` | Widget updates, notifications | LOW | `UNNotificationAction`, URL schemes |
| `SQLiteOpenHelper` | `InternalDbHelper` with many migrations | HIGH | GRDB or SQLite.swift (recommended) / Core Data |
| Firebase (FCM, Crashlytics, Analytics, RemoteConfig) | 31 references | HIGH | Firebase iOS SDK (1:1 API parity) |
| `ExoPlayer` (Media3 1.8.0) | Song MP3 playback | MEDIUM | `AVPlayer` / `AVAudioPlayer` |
| Android MIDI | MIDI song playback | MEDIUM | `AVMIDIPlayer` (iOS has native MIDI support) |
| `Intent` / custom action / `.yes`/`.pdb` URI filters | Opening Bible files from other apps | MEDIUM | Document types + `UIDocumentPickerViewController`, or URL scheme handlers |

**Rough proportions** of the main app:

- ~40–50% touches Android framework APIs directly (will be rewritten against UIKit/Foundation).
- ~20–30% is business logic — verse rendering, sync protocol, Bible format handling — portable with minor adaptation.
- ~20–30% is XML UI resources (layouts, styles, drawables) — replaced with Auto Layout / SwiftUI / asset catalogs.

---

## 4. Native / JNI code

The only C++ in the tree is `Snappy/jni/yuku_snappy_codec_SnappyImplNative.cpp` (54 LOC) — a thin JNI wrapper around Google's [snappy](https://github.com/google/snappy) with `nativeCompress` / `nativeDecompress` entry points. It exists because the YES2 text section is Snappy-compressed.

Options for iOS:

1. **Pure-Swift Snappy port** — a few implementations exist (unofficial); small footprint.
2. **Link the original C library** via a Swift Package Manager target (`cSettings`) — lowest risk; code reuse identical to Android.
3. **Apple `Compression` framework** — does **not** support Snappy (only zlib, lz4, lzfse, lzma). Would require re-encoding YES2 payloads, which breaks binary compatibility. Not recommended.

**Recommended:** option (2) — link the official C snappy as a SwiftPM C target. Trivially reuses the same decompression behavior with no format conversion.

Effort: ~40 hours including build-system plumbing.

---

## 5. Binary data formats

| Format | Readers/writers | LOC | Portability |
|---|---|---|---|
| **YES2** (Bible text) | `Yes2Reader`, `Yes2Writer` | 1,454 | **HIGH**. Header magic + Bintex-indexed sections (`versionInfo`, `booksInfo`, `text`, `xrefs`, `footnotes`, `pericopies`). Text section is Snappy-compressed. |
| **Bintex** | `BintexReader`, `BintexWriter` | 951 | **HIGH**. Pure-logic tagged-binary record format. Used by YES2 and RPB. |
| **RPB** (reading plans) | `ReadingPlan`, `RpbWriter` | ~300 | **HIGH**. Same family as Bintex. |
| **PDB** (PalmBible+) | `BiblePlus/*` | 955 | **HIGH**. Import-only. |
| **YES1** (legacy) | `Yes1Reader` | ~400 | **MEDIUM**. Legacy; rarely used. |

Characteristics that make them portable:

- **Little-endian** throughout — matches iOS/ARM64 native order, no byte swapping needed for scalars.
- **Stream abstraction (`RandomInputStream`)** is trivially reimplemented over `FileHandle` / `Data`.
- **No Java serialization** — no `ObjectInputStream`, no reflection, no `Serializable`.
- **No Android dependencies** — these are JVM-only libraries.

Porting to Swift is mostly mechanical: `DataInputStream` → `Data` slicing + `withUnsafeBytes`, `byte[]` → `Data`, `InputStream` → `InputStream` (Foundation also has one, with a subset of the API). Estimated ~150 hours across all formats, including tests to validate round-trip against fixture files generated on Android.

---

## 6. ARI addressing

`AlkitabModel/src/main/java/yuku/alkitab/util/Ari.java` (82 LOC) encodes `book:chapter:verse` into a 24-bit integer:

```
bits 23–16: bookId (0–65)
bits 15–8:  chapter (1-based; 0 = whole book)
bits 7–0:   verse   (1-based; 0 = whole chapter)
```

Pure bitwise arithmetic plus a few string parsers. **Trivially portable** (~10 lines of Swift). Used universally as the canonical verse reference — **do not** split it back into separate fields on iOS; preserve the exact encoding so sync payloads, URL schemes, and file formats stay compatible.

---

## 7. Database schema

`InternalDbHelper.java` owns ~10 core tables + migrations going back to schema version 50 (the current version is in the 17,000,000+ range). No triggers, no views, no explicit foreign keys — the app enforces relational integrity in code.

| Table | Role |
|---|---|
| `Marker` | Bookmarks, notes, highlights (`kind` column), each with `gid` for sync |
| `Label` | Bookmark categories with custom colors |
| `Marker_Label` | M:N junction |
| `ProgressMark` | 5 reading-progress pins |
| `ProgressMarkHistory` | Historical pin moves |
| `ReadingPlan` / `ReadingPlanProgress` | Plans + daily completion |
| `Version` | Installed Bible version metadata |
| `SyncShadow` / `SyncLog` | Sync state and audit |
| `Devotion` | Cached devotional articles |
| `PerVersion` | Per-version user settings |

**Recommended iOS stack: [GRDB](https://github.com/groue/GRDB.swift).** It is a mature SQLite wrapper that lets us reuse the schema verbatim, port the migration ladder step-by-step, and keep SQL parity with Android — which matters if users ever move data between platforms via export/import (see §13) or if we ever need to diff bugs across platforms.

Core Data is technically viable but would require re-modeling every entity, migrations become schema-mapping files, and SQL-level debugging is harder. Not recommended for a *faithful* port.

Special caveats:

- **`KpriModel.Song`** stores a `Parcelable`-serialized blob in its DB row (CLAUDE.md calls out this design as a known bad decision). On iOS that blob is unreadable — Android's `Parcel` format is a private, unstable runtime format, so a Swift reimplementation isn't viable. Options: (a) define a portable serialization format (JSON) and migrate Android to write it, which is REM-21 in the tech-debt plan; (b) ship iOS with fresh song DBs and let users re-download their song books. **Recommend (a)**, done on Android *before* the iOS port begins — it's a worthwhile cleanup independently of porting, and it's the only option that preserves existing song data cross-platform. This is also consistent with the §14 recommendation against KMP (no shared Kotlin parser to bridge through).

Migration effort: ~200 hours including the `Parcelable` song remediation.

---

## 8. Sync protocol

Sync lives at `yuku.alkitab.base.sync.*`:

- REST over HTTPS to `https://api.alkitab.app/sync/api/sync`.
- Delta operations (add/mod/del) per entity type, base-revision gated for conflict detection.
- Sync sets: `Sync_Mabel` (markers + labels + junctions), `Sync_Pins`, `Sync_Rp`, `Sync_History`.
- JSON payloads (Gson-serialized on Android).
- Simple `simpleToken` auth header.
- FCM push messages trigger sync pulls on other devices.

This is the **most portable** subsystem in the codebase. No Android APIs in the wire protocol. Swift implementation: `URLSession` + `Codable`, with the existing Gson DTOs converted 1:1 to Swift `Codable` structs. Firebase Cloud Messaging iOS SDK provides the push half with essentially the same API as Android.

Effort: ~100 hours.

---

## 9. UI complexity

This is where the port is largest and most judgment-heavy.

### UIKit vs. SwiftUI — a hybrid is the right answer

The rest of this section names specific UIKit APIs, but that is not a blanket "UIKit only" recommendation. The honest picture in 2026:

- **The reader must be UIKit.** Four things on one screen push it out of SwiftUI's comfort zone:
  1. **Rich verse text with tappable spans.** `VerseRenderer` produces a `SpannableStringBuilder` with per-span color, italic, paragraph indent levels (`@0`–`@4`), line breaks, and footnote / cross-reference spans that must be *independently tappable*. `NSAttributedString` + `UITextView` / a `UILabel` subclass maps 1:1. SwiftUI `AttributedString` is improving but still awkward for per-span tap targets and multi-level paragraph indentation inside a long scrollable list.
  2. **Variable-height cells with selection state, highlights, and long-press action mode.** `UITableView` / `UICollectionView` with diffable data sources give explicit control over cell recycling, size caching, and scroll position. `LazyVStack` / `List` work for simple cells but degrade with the combined load of attributed-text layout, selection, highlights, and split-view scroll coupling.
  3. **Gesture composition.** Pinch-to-zoom font, swipe chapter nav, long-press multi-select, simultaneous-gesture priority. `UIGestureRecognizer` is the direct analogue to Android's `GestureDetector`; SwiftUI gesture composition is still fiddly for this kind of priority resolution.
  4. **Split-view scroll coupling.** The two panes have to follow each other. `UIScrollViewDelegate` on two `UIScrollView`s is precise; SwiftUI scroll observation is improving but less direct.
- **Most other screens should be SwiftUI.** Markers list, labels, reading plans, devotions list, settings, about, onboarding, sign-in flows, version picker, bookmark editor — these are lists and forms. SwiftUI is faster to write, easier to maintain, and the iOS-native look is a bonus.
- **The daily verse widget must be SwiftUI.** WidgetKit is SwiftUI-only; there is no choice.
- **`WKWebView` screens** (devotion article, song lyric HTML template, help pages) are wrapped as `UIViewRepresentable` either way.

Recommended shape: **a SwiftUI app shell with a UIKit reader.** The reader lives inside a `UIViewControllerRepresentable` (or a UIKit-rooted scene), and the rest of the navigation graph is SwiftUI. This matches what Apple itself ships in many first-party apps and keeps the complex bits on the battle-tested API without paying for it on settings screens.

If the team is strongly SwiftUI-biased, the reader can be attempted in SwiftUI — but plan an escape hatch to drop back to UIKit for the verse list if `AttributedString` / `ScrollView` hit limits. If the team is strongly UIKit-biased, all-UIKit also works and is a safe default; you just write more boilerplate for the simple screens.

### The reader (`IsiActivity.kt`, ~2,321 lines)

Responsibilities: verse list rendering, split view (two parallel versions), pinch-to-zoom font size, swipe chapter navigation, long-press action mode (copy / share / bookmark / highlight), navigation history, volume-key paging, menu integration, dialog coordination.

On iOS this decomposes into a parent `UIViewController` hosting two child `UITableViewController` (or `UICollectionViewController`) instances in a `UISplitViewController`, with `UIPinchGestureRecognizer`, `UISwipeGestureRecognizer`, and `UIMenuController` / `UIEditMenuInteraction` for selection actions. `UISplitViewController` is a better native abstraction than the hand-rolled Android split view.

### Verse rendering pipeline

```
S.activeVersion() → Version.loadChapterText() → SingleChapterVerses
  → VersesDataModel (merges verses + pericopes via itemPointer array)
    → VersesControllerImpl (RecyclerView adapter)
      → VerseRenderer / FormattedTextRenderer (applies formatting codes)
        → VerseItem (custom RelativeLayout with highlight/selection drawing)
```

`VerseRenderer.java` and `FormattedTextRenderer.kt` (~500–700 LOC combined) process inline formatting codes:

- `@@` — marks verse as formatted
- `@0`–`@4`, `@^` — paragraph indentation levels
- `@6` / `@5` — red-letter (Jesus' words) start/end
- `@9` / `@7` — italic start/end
- `@8` — line break
- `@<tag@>…@/` — inline elements (cross-refs, footnotes)

On Android this is applied via `SpannableStringBuilder` + `LeadingMarginSpan`, `ForegroundColorSpan`, `StyleSpan`, `BackgroundColorSpan`. On iOS the same pipeline maps to `NSMutableAttributedString` with `NSParagraphStyle` (for indent / head-indent), `NSForegroundColorAttributeName`, etc. The *parser* is pure logic and ports as-is; the *span application* layer is small (~100–200 LOC) and maps to iOS attributes almost 1:1.

### Other UI modules

Unless noted, these are good candidates for **SwiftUI**:

- **Song module** — lyric rendering (HTML template via `WebView` on Android) → `WKWebView` wrapped as `UIViewRepresentable`; audio controllers → `AVPlayer` (MP3) and `AVMIDIPlayer` (MIDI). Song browse/search list: SwiftUI `List`.
- **Search** — SwiftUI `.searchable` + results list; drop to `UISearchController` only if result-row rendering needs attributed text with tappable spans.
- **Markers** — list/edit/filter UI; SwiftUI `List` with swipe actions.
- **Reading plans** — checklist UI; SwiftUI.
- **Devotions** — list in SwiftUI; article view is `WKWebView` (`UIViewRepresentable`).
- **Settings** — SwiftUI `Form` with `Section`s (matches existing Android behavior of an embedded preferences UI, not the system settings app).
- **Color picker** — SwiftUI `ColorPicker` (iOS 14+) or UIKit's `UIColorPickerViewController`; either way, do not port `AmbilWarna`.
- **Flow layout** — SwiftUI's `Layout` protocol (iOS 16+) or UIKit's `UICollectionViewCompositionalLayout`.
- **Daily verse widget** — **SwiftUI required** (WidgetKit).

### Custom drawing

Android uses canvas drawing in `VerseItem` for the highlight/selection background and in `AttributeView` / `Floater` for small overlays. Total custom-drawing surface is modest (~500–700 LOC). Core Graphics in `draw(_:)` overrides on iOS covers all of it without new research.

**Effort for the full UI layer: ~800 hours** (verse rendering 200 + main reader 250 + songs/markers/devotions/reading-plans/search UI 200 + custom drawing 150 — see §15).

---

## 10. Third-party dependencies

| Android dep | iOS path |
|---|---|
| `androidx.appcompat` / `fragment` / `core-ktx` | UIKit (built-in) |
| `androidx.recyclerview` | `UITableView` / `UICollectionView` |
| `androidx.preference` | `UserDefaults` + custom settings UI |
| `androidx.work:work-runtime` | `BGTaskScheduler` + `URLSession` background |
| `androidx.constraintlayout` | Auto Layout / SwiftUI |
| `androidx.media3:media3-exoplayer` | `AVPlayer` / `AVFoundation` |
| `com.google.android.material` | UIKit + bespoke components |
| `com.google.code.gson` | `Codable` / `JSONEncoder` |
| `com.squareup.okhttp3` | `URLSession` |
| `io.coil-kt:coil` | Kingfisher or SDWebImage |
| `com.google.firebase:firebase-messaging` | Firebase iOS SDK (FCM) |
| `com.google.firebase:firebase-crashlytics` | Firebase iOS SDK |
| `com.afollestad.material-dialogs:core` | `UIAlertController` + bespoke modal sheets |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | `Codable` |
| `com.github.faruktoptas:FancyShowCaseView` | Custom onboarding overlay (no close equivalent) |
| `PrDownloaderFixed` | `URLSession` download tasks (resumable) |
| Vendored: `AmbilWarna` color picker, `FlowLayout`, `Snappy` JNI | See §4, §9 |

~80% of Android dependencies have built-in or well-maintained iOS equivalents. No dependency presents a genuine blocker.

---

## 11. Multi-flavor build system

Gradle `productFlavors`:

- `plain` — open-source build with placeholder `ddd_*` Bible assets and placeholder `google-services.json`. Builds out of the box.
- `yuku_alkitab` — Indonesian "Alkitab" production build, `applicationId = yuku.alkitab`.
- `yuku_quick_bible` — English "Quick Bible" production build, `applicationId = yuku.alkitab.kjv`.
- `sabda_alkitab` — SABDA variant, `applicationId = org.sabda.alkitab`.

Production flavors pull proprietary Bible text + real Firebase config from `$ALKITAB_PROPRIETARY_DIR` at build time and copy them into the gitignored per-flavor source set.

### iOS mapping

Map each Android flavor to an **Xcode scheme + configuration + target** triplet. Two common patterns:

- **Single target, multiple schemes + `.xcconfig` files** — each scheme defines `PRODUCT_BUNDLE_IDENTIFIER`, display name, Firebase plist path, asset catalog. Cleanest for a small matrix.
- **Multiple targets** — needed only if each flavor links different code (here they do not).

Proprietary overlay: a build-phase script copies `$ALKITAB_PROPRIETARY_DIR/ios/<bundle-id>/…` into the target's resource bundle during the build, mirroring the current Gradle approach. `GoogleService-Info.plist` lives per-scheme.

Effort: ~60 hours including CI.

---

## 12. Inter-app integration, widgets, file handling

### ContentProvider (`yuku.alkitab.base.cp.Provider`)

Android apps currently query Alkitab for verses via a read-only `ContentProvider` at URIs like `content://<authority>/bible/verses/single/ari/<ARI>` and `/bible/versions`. Optional `formatting` query param.

**iOS has no ContentProvider equivalent.** Alternatives, in rough order of fidelity:

- **Custom URL scheme** (`alkitab://verses/ari/<ARI>`) opens the app with the requested verse. Not a query-and-return API — it's a "launch Alkitab and show this verse" API. Sufficient for the main inbound use case ("tap a verse reference in another app"), but breaks the headless query use case.
- **App Group shared container** — if the external apps are built by the same publisher, they can share a container and read directly.
- **Share extension** / **App Intents** (iOS 16+) — expose verse lookup as a system-level intent consumable by Siri, Shortcuts, and Spotlight. Closest thing iOS has to a ContentProvider for structured query.

If third-party Android apps actively consume the current ContentProvider, this is a **feature-parity gap** on iOS. Worth confirming with the publisher which integrations matter.

### Daily-verse AppWidget

Android's `AppWidget` supports dynamic updates and interactions. iOS **WidgetKit** (iOS 14+) is different in kind:

- Widgets render from a precomputed `TimelineProvider` snapshot.
- No custom view hierarchy at runtime — only a SwiftUI tree.
- Interactions are limited to deep-link `widgetURL` taps (plus buttons on iOS 17+).
- No background service; the system decides when to refresh.

Daily-verse widget maps well: the timeline can schedule daily entries and pre-cache verse text. Configuration (choosing a version, color scheme) maps to `IntentConfiguration`. Feature parity is **achievable**; the plumbing differs.

### `.yes` / `.pdb` file handling

Android handles `application/*.yes` and PDB MIME types via intent filters. iOS equivalent: register `CFBundleDocumentTypes` + UTIs in `Info.plist`, then handle the open URL in `SceneDelegate`. Straightforward.

---

## 13. Assets, bundled Bible text, localization

### Bundled Bibles

- Plain flavor ships placeholder `ddd_*` files under `Alkitab/src/plain/assets/internal/`.
- Production flavors copy real `tb_*`, `kjv_*` files from the proprietary overlay into `build/generated/proprietaryAssets/<flavor>/internal/` at build time.
- Format: YES2 binary (a few hundred KB to a few MB per translation).

On iOS these binaries are copied as-is into the app bundle (or Documents directory for downloads). **Zero format work** — the files are already platform-neutral.

### Downloaded versions

Users can download 100+ additional versions from the backend. The downloader just fetches files and drops them into a versions directory; port against `URLSession` download tasks. Already abstracted on Android behind `BibleReader` — reuse the abstraction.

### Data export/import

The app has JSON export/import for user data (markers, labels, reading plan progress, etc.). Implementing this on iOS with the same schema is essential if users will ever move between platforms. Pure `Codable` work once the DB layer is in place.

### Localization

String resources cover **34 languages + screen-size variants**. Plurals use Android's `<plurals>` XML; iOS equivalent is `.stringsdict`. No code-level i18n surprises — no hardcoded localizable strings in Java/Kotlin were observed during exploration.

Conversion of `values-*/strings.xml` → `Localizable.strings` (+ `.stringsdict` where plurals exist) is mechanical; scripts exist in the community. Effort: ~50 hours including plurals and QA against RTL / CJK / variable-width layouts.

---

## 14. Kotlin Multiplatform option

Current state:

- Mixed Java/Kotlin, **no `expect`/`actual` declarations**, no `commonMain`/`iosMain` source sets, `build.gradle` (Groovy) not `build.gradle.kts`.
- No existing KMP infrastructure to extend.

Kotlin concentration is in the main app module (42%); library modules remain largely Java.

**Theoretical KMP-shareable code:** ~3,500–4,000 LOC (~7% of the total).

- `AlkitabModel` — high portability (~600 LOC).
- Binary format readers (YES2, Bintex, BiblePlus) — high portability after isolating Android stream wrappers (~1,200 LOC effective).
- Sync classes — high portability (~500 LOC).
- Formatting code *parser* (separated from span application) — medium portability (~200–400 LOC).
- Everything else (activities, fragments, custom Views, XML layouts) is non-portable by construction.

**Assessment:** KMP is **not recommended** for this port. The sharable surface is small, most of the shareable code is already Java (not Kotlin) and would have to be rewritten in Kotlin before it could be shared, and the UI/storage layers — where most of the LOC lives — must be platform-specific anyway. The incremental maintenance tax of KMP tooling, `expect`/`actual` boilerplate, and cross-platform debugging outweighs the benefit at this scale.

**Alternative:** port the business logic (models, formats, sync, ARI) to pure Swift once, maintain parity with Android by keeping JSON DTOs, file formats, and test vectors in sync, and run a shared test fixture set on both platforms in CI. This gives you 80% of the behavioral safety of KMP with none of the tooling overhead.

---

## 15. Effort and risk summary

| Area | Difficulty | Hours | Blocking? |
|---|---|---|---|
| Core models (`Ari`, `Version`, `Book`) | Trivial | 20 | No |
| Binary formats (YES2, Bintex, RPB, PDB) | Low | 150 | No |
| Snappy (link C library) | Low | 40 | No |
| Database layer (GRDB port of schema + migrations) | Low–Medium | 200 | No |
| Sync protocol (REST + FCM) | Low | 100 | No |
| Settings / preferences | Trivial | 30 | No |
| Verse rendering (parser + attributed string builder) | Medium | 200 | No |
| Main reader UI (split view, gestures, action mode) | Medium | 250 | No |
| Songs / devotions / markers / reading plans / search UI | Medium | 200 | No |
| Custom drawing (VerseItem, Floater, AttributeView) | Medium | 150 | No |
| Firebase (FCM, Crashlytics, RemoteConfig, Analytics) | Low | 80 | No |
| AppWidget → WidgetKit | Medium | 80 | Minor feature-parity gap |
| ContentProvider → URL schemes / App Intents | Medium | 40 | Feature gap if third-party integrators exist |
| Multi-scheme build + proprietary overlay | Low | 60 | No |
| Localization (34 languages) | Trivial | 50 | No |
| QA, beta, test matrix | Medium | 200 | Ongoing |
| **Total** | | **~1,850** | |

A disciplined team will scope the MVP smaller — reader + markers + sync is probably ~1,000 hours. The numbers above assume feature-complete parity including songs, reading plans, devotions, widget, and all flavors.

### Top risks

1. **`IsiActivity` is a monolith.** Any missed behavior (volume-key nav, split-view scroll coupling, gesture priority) will be caught only by users. Budget time for regression hunting after the first beta.
2. **Formatting code parser edge cases.** The inline `@` code family is load-bearing and idiosyncratic. Build a golden-master test suite from Android first; port tests to Swift.
3. **`Parcelable` song blobs.** CLAUDE.md already flags this as a bad design; iOS cannot read them. Plan the migration (§7).
4. **ContentProvider integrations.** If other apps depend on the ContentProvider, iOS has no equivalent — confirm the dependency set before committing.
5. **AppWidget UX.** WidgetKit's timeline model is different; users expecting real-time updates may notice.
6. **Database migration tail.** The migration ladder stretches back to schema version 50. If any iOS user ever imports a very old Android backup, the full ladder needs to work in Swift too. Recommendation: port migrations from the latest backward-compatible floor (e.g. the earliest version still seen in the wild) and reject older backups explicitly.
7. **Firebase Auth / `simpleToken`.** Confirm the sign-in flow on iOS matches Android exactly; Google Sign-In and email auth are both supported but need per-platform configuration.

### Features with reduced fidelity on iOS

- **ContentProvider** — no true equivalent; use URL schemes + App Intents.
- **AppWidget real-time updates** — WidgetKit timelines only.
- **Long-running background sync** — iOS background execution is stricter than Android; rely on push-triggered foreground sync + `BGAppRefreshTask`.
- **File association with `.yes` / `.pdb`** — works but UX is more modal (document picker) than Android's ambient intent chooser.

---

## 16. Recommendation

**Green light, with caveats.**

There are no technical blockers. The core of the app — data formats, sync, database, business logic — is highly portable and will port cleanly. The bulk of the work is rewriting the UI; default to **SwiftUI for the app shell (navigation, lists, forms, settings, widget) and UIKit for the reader** (see §9). All-UIKit is a safe fallback if the team prefers it; all-SwiftUI is possible for teams willing to carry reader-side risk.

Suggested phasing:

1. **Phase 0 — Foundation (~6 weeks).** Port models, ARI, binary formats, Snappy, database schema (GRDB), and a headless test harness that validates byte-for-byte parity against Android fixture files.
2. **Phase 1 — Reader MVP (~10 weeks).** One version active at a time, single-pane, verse rendering with formatting codes, navigation, font zoom, share, copy, bookmark. Single flavor (open-source `plain` equivalent). Ship to TestFlight.
3. **Phase 2 — Sync & markers (~6 weeks).** Sync protocol, marker types (bookmarks, notes, highlights), labels, Firebase auth, FCM push.
4. **Phase 3 — Content features (~8 weeks).** Split view, songs, devotions, reading plans, search, data export/import.
5. **Phase 4 — Widgets, multi-flavor, localization, polish (~6 weeks).** WidgetKit daily verse, schemes for production flavors, proprietary overlay wiring, 34 locales, App Intents.
6. **Phase 5 — QA, beta, launch (~4 weeks).**

This sequencing lets Phase 1 ship to real users early, derisks the hardest UI surface first, and delays the ContentProvider and AppWidget feature-gap discussions until they are the only thing left.

Strong recommendation: **skip Kotlin Multiplatform**; keep iOS as a native Swift codebase and share only test fixtures and JSON/binary payloads across platforms.
