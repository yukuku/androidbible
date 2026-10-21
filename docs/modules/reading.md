# Reading aids

Reading plans, devotional articles, and the daily verse widget offer different
ways to return to Bible reading. Bible-version management, markers, and audio
have their own [feature guides](../README.md#feature-guides).

## Reading plans

Users can follow multiple daily reading schedules. Plans are distributed as RPB
files; [Binary formats](../binary-formats.md#rpb-reading-plan-format) specifies
the runtime encoding, and the [RPA guide](../developer/reading-plans.md) explains
authoring and submission.

Progress is saved per passage within a plan day and can synchronize across
devices. Opening a passage activates that day's passages in the reader, while
navigating to the tapped passage. Each drawer checkbox updates only its passage.
Clearing the current reading removes the active guides without changing saved
completion.

Optional reading guides identify active passages beside the text and follow
layout direction. Passage validation uses the shared verse-reference parser;
[parser compatibility tests](../../Alkitab/src/test/desktop-verse-parser.md)
describe its contract. Deleted-plan progress is retained by sync and backups.

`ReadingPlanManager` owns plan loading and progress operations; database ownership
is described in [Storage](../storage.md). Sync behavior belongs to [Sync](sync.md).

## Devotions

The devotional reader lets users browse daily articles by source and date.
Articles are downloaded as needed and cached for offline reading, with refresh
and retry support. Foreground requests and prefetch have separate cancellation
and deduplication behavior in `DevotionDownloader`.

The reader follows the app's language and reading appearance and supports links
to Bible passages. Pinch-to-zoom temporarily resizes article text without
changing saved font preferences. See [Backend communication](../backend-communication.md)
for article delivery and [Storage](../storage.md) for caching.

## Daily verse widget

The daily verse widget brings Bible passages to the Android home screen. Users can browse verses, open a passage in the app, and choose the Bible version and appearance that suit their home screen.

### Configuration and navigation

Each widget is configured independently. Supported launchers let users revisit its settings after placement. Saving appearance changes preserves verse navigation; canceling leaves the saved configuration unchanged.

Verse selection draws from a curated collection rather than following Bible order. Different widget instances can show different passages. The widget handles unavailable Bible content so that a missing or unreadable version does not leave it unusable. If its configured version cannot load, a notice names the internal Bible used as a fallback and opens that widget's settings when tapped. The saved selection is preserved until the user saves a replacement, and the notice disappears when that selection becomes readable again. An unset version also offers a choice; explicitly choosing the internal Bible shows no fallback notice.

### Launcher integration and localization

The launcher controls how the widget is presented, sized, and reconfigured, so available interactions vary across devices and launchers.

The widget picker shows a localized sample rather than the widget’s current verse. Its language follows the launcher’s resource context, which may differ from the language selected inside the app. Preview samples do not affect live verse selection or the user’s chosen Bible version.

Preview text comes from published Bible translations. The [preview source records](daily-verse-preview-sources.json) document the editions, references, source URLs, and text normalization. Reference numbering can differ between editions, and publisher-specific reuse terms apply.
