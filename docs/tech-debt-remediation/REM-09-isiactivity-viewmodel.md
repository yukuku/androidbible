# REM-09: Introduce ViewModel for IsiActivity

**Addresses:** TD-07
**Module:** Main reader
**BRICE:** B=4 R=3 I=2 C=3 E=5 → **3.4**
**Phase:** 2 — Architecture Improvements

**Status:** Not started.

**Steps:**
1. Create `IsiViewModel : ViewModel()` with:
   - `activeVersion: StateFlow<MVersion>`
   - `currentAri: StateFlow<Int>`
   - `versesDataModel: StateFlow<VersesDataModel>`
   - `selectedVerses: StateFlow<Set<Int>>`
   - `splitVersion: StateFlow<MVersion?>`
2. Move version loading, chapter display logic, and verse selection state from `IsiActivity` into `IsiViewModel`
3. `IsiActivity` observes StateFlows and updates UI
4. Navigation history moves to ViewModel (survives rotation)

**Prerequisite:** [REM-06](REM-06-isiactivity-gestures.md) ✅, [REM-07](REM-07-isiactivity-action-mode.md) ✅, [REM-08](REM-08-isiactivity-split-view.md) ✅ should be done first to reduce IsiActivity size before extracting ViewModel. (All three are complete.)

**Difficulty:** Hard (2-3 days). Risk: extensive refactoring of the largest file in the codebase.

---

[← Back to remediation index](../tech-debt-remediation.md)
