# Daily Verse Widget

The daily verse widget brings Bible passages to the Android home screen. Users can browse verses, open a passage in the app, and choose the Bible version and appearance that suit their home screen.

## Configuration and navigation

Each widget is configured independently. Supported launchers let users revisit its settings after placement. Saving appearance changes preserves verse navigation; canceling leaves the saved configuration unchanged.

Verse selection draws from a curated collection rather than following Bible order. Different widget instances can show different passages. The widget handles unavailable Bible content so that a missing or unreadable version does not leave it unusable.

## Launcher integration and localization

The launcher controls how the widget is presented, sized, and reconfigured, so available interactions vary across devices and launchers.

The widget picker shows a localized sample rather than the widget’s current verse. Its language follows the launcher’s resource context, which may differ from the language selected inside the app. Preview samples do not affect live verse selection or the user’s chosen Bible version.

Preview text comes from published Bible translations. The [preview source records](daily-verse-preview-sources.json) document the editions, references, source URLs, and text normalization. Reference numbering can differ between editions, and publisher-specific reuse terms apply.
