# Reading Plans Module

## Overview

The reading plan system lets users follow structured daily Bible reading schedules. Multiple plans can be active simultaneously. Progress is tracked per-day and synced via the cloud sync system.

## Key Files

- `Alkitab/src/main/java/yuku/alkitab/base/ac/ReadingPlanActivity.java` — Main reading plan UI with calendar navigation
- `Alkitab/src/main/java/yuku/alkitab/base/model/ReadingPlan.java` — Data model with daily verse arrays
- `Alkitab/src/main/java/yuku/alkitab/base/util/ReadingPlanManager.java` — File parsing, DB operations, progress management

## RPB Binary Format

Reading plans use a custom binary format (`.rpb`):

```
Header: 0x52 0x8a 0x61 0x34 0x00 0xe0 0xea (7 bytes)
Version: 1 (uint8)
Body (Bintex-encoded):
  - name (string)
  - title (string)
  - description (string)
  - duration (int, number of days)
  - url (string, optional)
  - For each day:
    - verse count (int)
    - ARI values (int array)
```

## Progress Tracking

Progress is stored in the `ReadingPlanProgress` database table. Each reading within a day is identified by a reading code: `(dayNumber << 8) | sequenceIndex`. This allows tracking completion of individual verse ranges within a single day.

Opening a passage activates every passage for that plan day in the reader's current reading section, while navigating to the passage that was tapped. The drawer lists each passage on a separate row with its own completion checkbox. Ticking or unticking a row changes only that passage's saved progress and keeps all current reading guides visible. The X beside the current reading header clears all active passages and guides without changing saved progress.

Display settings offer start/end labels, a thin line on the left, or a fixed reference listing the active passages. The rounded left-side line is inset 4 dp from the reading pane edge and is 2 dp wide. Marked items keep at least 4 dp between the line and their text area. Existing margins of 10 dp or more preserve text placement; smaller margins shift only marked items to a 10 dp text start. Unmarked items keep the user's chosen padding. In split view, each pane uses its own left edge. The line uses the reading text color at full opacity when its contrast against the page is at least 3:1; otherwise it uses whichever of black or white provides higher contrast. This also applies to custom day and night colors. The setting defaults to Off and applies to both reading-plan and devotional passages.

### Reading guide previews

Open `ReadingGuidePreviews.kt` in Android Studio's Design or Split view. The named `@Preview` entries include light and dark variants for every scenario. The `Reading line` groups cover padding (zero, reduced, minimum clearance, and wide), ranges (single verse, disjoint, adjacent, empty, another chapter, and displayed boundaries), opaque backgrounds, selection, audio highlighting, large reading text, narrow and split panes, sepia colors, low-contrast color fallback, and disabled guides. Start/end labels and the fixed reading indicator also have single- and multiple-passage previews in both themes.

The previews use the actual Compose guide and verse components with sample text. They do not require an active plan, database, or running audio player. The audio example shows the visual highlight, not playback controls. Rendering tests export sample images under `Alkitab/build/test-artifacts/reading-guide-previews/`.

## Sync

Reading plan progress syncs via `Sync_Rp`. Its delta protocol includes additions, changes, and deletions so marking or unmarking a passage can sync across devices.

## Database Tables

- **ReadingPlan** — plan metadata (name, title, description, startDate, duration, data blob)
- **ReadingPlanProgress** — per-reading completion records (readingPlanId, readingCode, checkTime)
