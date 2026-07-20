# REM-43: Surface or Resolve Cross-Device Sync Conflicts

**Addresses:** TD-08 (adjacent) — sync protocol
**Module:** Sync (`SyncAdapter.patchNoConflict`, `SyncShadow`)
**BRICE:** B=3 R=3 I=1 C=2 E=3 → **2.4**
**Phase:** 4 — Long-term / Major Refactors

**Problem:** `SyncAdapter.patchNoConflict` is last-write-wins for every Mabel entity and for progress pins, so a highlight color or a progress-pin position edited on two devices silently discards one side. Note and bookmark caption text may be merged server-side before deltas are emitted, but the client unconditionally overwrites whatever arrives. `SyncShadow` computes the local delta; it does not detect or surface cross-device conflicts to the user.

**Steps:**
1. Decide the target behavior per entity type — silent LWW is acceptable for some (e.g. reading position) but not for user-authored content (notes).
2. For entities that warrant it, use the `SyncShadow` base revision to detect a three-way conflict (base ≠ local ≠ remote) instead of blindly overwriting.
3. On conflict, either merge deterministically (server-side, where caption text already merges) or record both sides and prompt the user.
4. Add tests driving two diverging clients through a shared base revision.

**Difficulty:** Hard — genuine conflict resolution is a protocol change spanning client and server, and the two evolve in separate repos. Scope carefully before starting.

---

[← Back to remediation index](../tech-debt-remediation.md)
