# Daily Verse Widget

## Overview

A home screen app widget displaying daily Bible verses in a stack/card view. Configurable appearance with user-selectable version, text size, colors, and background transparency.

## Key Files

- `Alkitab/src/main/java/yuku/alkitab/base/appwidget/DailyVerseAppWidgetService.kt`: RemoteViewsService
- `Alkitab/src/main/java/yuku/alkitab/base/appwidget/DailyVerseFactory.kt`: RemoteViewsFactory implementation
- `Alkitab/src/main/java/yuku/alkitab/base/appwidget/DailyVerseData.kt`: Widget settings and verse selection
- `Alkitab/src/main/java/yuku/alkitab/base/appwidget/DailyVerseAppWidgetConfigurationActivity.kt`: Widget setup lifecycle and saved configuration
- `Alkitab/src/main/java/yuku/alkitab/base/br/DailyVerseAppWidgetReceiver.kt` — Broadcast receiver for widget updates

- `Alkitab/src/main/java/yuku/alkitab/base/appwidget/DailyVerseConfigurationScreen.kt`: Compose configuration UI

## Verse Selection Algorithm

Uses a predefined verse list from `R.raw.daily_verses_bt`:
1. Generate seed from: `widgetId + year + dayOfYear + clickCount`
2. Select verse from predefined list using seed
3. Verify verse exists in the selected Bible version
4. Falls back to internal version if the selected version can't load the verse

## Configuration

Each widget instance stores its own preferences:
- Bible version to use
- Text size
- Text color
- Background transparency (alpha)
- Whether to show the app icon

The configuration screen uses Material 3 cards for Bible versions and appearance controls, with the app’s existing colors and toolbar-position preference. The form scrolls as one unit while Cancel and OK remain visible. The always-visible transparency slider directly controls the background, from solid at 0% to fully transparent at 100%, with labels at opposite ends; there is no separate transparency checkbox. Opaque legacy settings open at 0%. Sliders have no tick or endpoint dots. New configuration labels are localized across all supported app languages. Unsaved appearance and version edits survive activity recreation.

Settings are persisted in SharedPreferences keyed by widget ID. Launchers that support widget reconfiguration can reopen the setup screen with the saved settings selected. Saving appearance changes preserves verse navigation; canceling leaves the widget unchanged.

On Android 12 and newer, the widget declares a 4 × 2 cell target size and a layout-based preview. Older launchers can use the preview image and minimum dimensions.

## Navigation

Users can tap previous/next to cycle through verses. Each tap increments a `clickCount` stored per widget, which changes the seed and selects a different verse.
