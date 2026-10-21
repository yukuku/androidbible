# Bible versions

Users can download Bible versions from the catalog, enable and reorder installed
versions, and import their own files. The built-in version remains available
without a download. Parallel reading can use a different version in each pane.

## Content lifecycle

The [version-manager package](../../Alkitab/src/main/java/yuku/alkitab/versionmanager)
owns the catalog, installed-version UI, and file imports. A bundled Bible-version
catalog supplies a fallback; server updates are cached locally. Catalog entries
describe available content, while installed metadata identifies usable local
files.

Downloads run through WorkManager with progress and resume support. Completion
installs the content and registers it for use. Local imports accept YES and
PalmBible+ PDB files, including gzip-compressed variants; PDB content is converted
to YES2. Readers support legacy YES files as well as YES2.

Version identity is shared by preferences, navigation, and other features:

- `internal` identifies the built-in Bible.
- `preset/<name>` identifies a catalog version.
- `file/<path>` identifies user-imported content.

Per-version appearance adjustments are separate from the Bible text. Installed
metadata and reader caches must handle unavailable or replaced files; a cached
reader does not guarantee that the underlying file remains readable.

## Related guides

See [Storage](../storage.md) for persistence, [Binary formats](../binary-formats.md)
for runtime encoding, and the [YET authoring guide](../developer/yet.md) and
[Tools](../tools.md) for creating Bible files. Audio recording availability is a
separate backend lookup described in [Audio playback](audio-playback.md).
