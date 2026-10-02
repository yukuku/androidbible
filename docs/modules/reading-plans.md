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
Info (Bintex VALUE Simple Map):
  - title (string)
  - description (string)
  - duration (int, number of days)
Days (duration entries):
  - ARI count (uint8, two entries per reading range)
  - ARI values (Bintex INT values, ordered as start/end pairs)
Footer: 0x00
```

The plan name is supplied separately when the app imports or reads the plan.
Each day can hold up to 127 ranges. See [Binary Formats](../binary-formats.md#rpb-reading-plan-format)
for the runtime encoding and the [RPA authoring guide](../developer/reading-plans.md)
for creating and publishing a plan.

## Progress Tracking

Progress is stored in the `ReadingPlanProgress` database table. Each reading within a day is identified by a reading code: `(dayNumber << 8) | sequenceIndex`. This allows tracking completion of individual verse ranges within a single day.

## Sync

Reading plan progress syncs via `Sync_Rp`. The sync protocol handles merging progress from multiple devices — since progress is additive (readings can only be marked complete), conflict resolution is straightforward union merge.

## Database Tables

- **ReadingPlan** — plan metadata (name, title, description, startDate, duration, data blob)
- **ReadingPlanProgress** — per-reading completion records (readingPlanId, readingCode, checkTime)
