# Architecture

Bible for Android is a multi-module Android app. Feature guides describe user
capabilities and subsystem boundaries; implementation details belong in source
and tests. Start with the [documentation index](README.md) for other guides.

## Build modules

| Module | Responsibility |
| --- | --- |
| `Alkitab` | Android application, feature UI, storage, networking, and background work |
| `AlkitabModel` | Bible models and verse addressing |
| `AlkitabIo` | Bible-reader interface and shared I/O |
| `AlkitabYes2` | YES2 Bible reader/writer |
| `AlkitabIntegration` | Public inter-app integration API |
| `Bintex` | Binary serialization primitives |
| `Snappy` | Native compression through JNI |

[settings.gradle.kts](../settings.gradle.kts) defines build membership. Desktop
converters reuse library sources in a separate IntelliJ project; see [Tools](tools.md).
Feature areas under `Alkitab` are packages, not independent Gradle modules.

## Application services

`App.services` exposes storage, Bible-version management, and reading appearance
through narrow interfaces in the
[services package](../Alkitab/src/main/java/yuku/alkitab/base/services).
Prefer these interfaces over the legacy `S` service locator for new callers.
Application initialization also configures Firebase and other process services;
entry points such as the content provider can run before an activity exists.

## Reader and UI

Activities and fragments host both Compose and Android Views. Compose is the
default for verse lists, verse snippets, navigation, the normal reader toolbar,
song lyrics, and sync login. Experimental settings retain legacy alternatives
for several surfaces; [ExperimentalFlags](../Alkitab/src/main/java/yuku/alkitab/base/settings/ExperimentalFlags.kt)
and its preference defaults define the available switches.

`IsiActivity` coordinates the reader. Gesture handling, verse actions, and
parallel-version panes have separate collaborators. Each pane has its own Bible
version and verse controller; navigation and selection are coordinated between
panes. A common controller interface lets Compose and legacy verse lists serve
the same reader interactions. See [Text rendering](text-rendering.md).

## Verse identity and data flow

ARI (Alkitab Resource Identifier) is the shared verse address used in storage,
intents, sync, and file formats. It packs a zero-based book ID into bits 23–16,
a chapter into bits 15–8, and a verse into bits 7–0. Chapters and verses are
one-based; zero denotes a whole book or chapter. Use
[Ari](../AlkitabModel/src/main/java/yuku/alkitab/util/Ari.java) to construct and
interpret addresses.

Bible-version metadata resolves to a reader that supplies chapter text and
section headings. Presentation combines those with markers, reading guides,
and playback state. Keep raw formatting, visible text, and verse identity
distinct: decoration must preserve the address and text offsets needed for
selection and highlights.

## Persistence and background work

[Storage](storage.md) owns database boundaries, migrations, preferences, and
file lifetime. [Backend communication](backend-communication.md) describes
network responsibilities. WorkManager handles persistent sync and version
downloads; other feature operations use their own lifecycle and cancellation
policies. Do not infer successful synchronization from worker completion alone.

Bible and song playback run in foreground media services and share an
exclusive-playback coordinator; see [Audio playback](modules/audio-playback.md).

## Language and appearance

The in-app language can differ from the device language. Activity resources
are wrapped for that choice. Services, receivers, workers, and application
contexts need `ConfigurationWrapper.localizedContext` when resolving visible
strings. Refresh cached localized resources when the language changes.

Reading appearance is derived from preferences and per-version adjustments.
Renderers should consume the applied appearance rather than independently
reconstructing colors, typography, or spacing.

## Ongoing work

The [tech-debt backlog](tech-debt-remediation.md) tracks unfinished architecture
work and links completed remediation records. Dated plans describe their design
baseline; they do not define the current implementation.
