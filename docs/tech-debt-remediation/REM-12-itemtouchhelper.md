# REM-12: Replace DragSortListView with ItemTouchHelper ✅ COMPLETED

**Addresses:** TD-12
**Module:** Markers (label management)
**BRICE:** B=2 R=2 I=4 C=4 E=5 → **3.4**
**Phase:** 2 — Architecture Improvements

**Completed changes:**
- `MarkersActivity.java` rewritten to use `RecyclerView` + custom `ItemTouchHelper.Callback` (drag-handle initiated via `startDrag`, divider guarded via `getMovementFlags` and `canDropOver`). Native context menu replaced with a `PopupMenu` shown on long-press.
- `VersionListFragment.kt` converted to `RecyclerView.Adapter<VersionItemHolder>` with `ItemTouchHelper` installed only when `downloadedOnly == true`.
- DB persistence still uses the existing pair-wise `reorderLabels` / `reorderVersions`. Instead of committing on every `onMove`, the adapter snapshots its item list at drag start and issues a single reorder call in `clearView` using `snapshot[startPos]` and `snapshot[endPos]`.
- Translucent-white drag overlay (`0x22ffffff`) preserved via `onSelectedChanged` / `clearView`.
- Three layouts (`activity_markers.xml`, `fragment_versions_all.xml`, `fragment_versions_downloaded.xml`) switched to `androidx.recyclerview.widget.RecyclerView`.
- `DragSortListView` module removed from `settings.gradle`, `Alkitab/build.gradle`, and the filesystem.

**Difficulty:** Easy-Medium (4-6 hours).

---

[← Back to remediation index](../tech-debt-remediation.md)
