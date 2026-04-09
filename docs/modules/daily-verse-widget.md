# Daily Verse Widget

## Overview

A home screen app widget displaying daily Bible verses in a stack/card view. Configurable appearance with user-selectable version, text size, colors, and background transparency.

## Key Files

- `Alkitab/src/main/java/yuku/alkitab/base/appwidget/DailyVerseAppWidgetService.java` — RemoteViewsService
- `Alkitab/src/main/java/yuku/alkitab/base/appwidget/DailyVerseFactory.java` — RemoteViewsFactory implementation
- `Alkitab/src/main/java/yuku/alkitab/base/appwidget/DailyVerseData.java` — Widget settings and verse selection
- `Alkitab/src/main/java/yuku/alkitab/base/appwidget/DailyVerseAppWidgetConfigurationActivity.java` — Widget setup UI
- `Alkitab/src/main/java/yuku/alkitab/base/br/DailyVerseAppWidgetReceiver.java` — Broadcast receiver for widget updates

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

Settings are persisted in SharedPreferences keyed by widget ID.

## Navigation

Users can tap previous/next to cycle through verses. Each tap increments a `clickCount` stored per widget, which changes the seed and selects a different verse.
