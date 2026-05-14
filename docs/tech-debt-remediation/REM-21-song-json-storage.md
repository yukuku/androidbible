# REM-21: Migrate Song Storage from Parcelable to JSON

**Addresses:** TD-04 (long-term part)
**Module:** Songs
**BRICE:** B=3 R=3 I=1 C=2 E=5 → **2.8**
**Phase:** 4 — Long-term / Major Refactors

**Status:** Not started.

**Steps:**
1. Define `SongJsonModel` using Kotlinx Serialization with explicit field names. Note: `KpriModel/Song.java` (line 13) implements both `Serializable` and `Parcelable`, and line 10-11 has a comment acknowledging this is a "Bad decision".
2. Add database migration that reads all songs via old `Parcelable` format (currently stored as `Parcel.marshall()` byte arrays via `SongDb.marshallSong()` at line 29-35 and `unmarshallSong()` at line 37-44), re-serializes as JSON, and writes back
3. Update `SongDb.java` to read/write JSON instead of `Parcelable` blobs — the "data" column (line 81) currently stores marshalled byte arrays
4. Update server-side song book format to JSON (coordinate with backend team)
5. Support both formats during transition (detect format by first byte)
6. Remove `KpriModel.Song.writeToParcel()` / `createFromParcel()` (lines 31-38, 75-85) after migration period

**Difficulty:** Hard (3-5 days). Risk: data migration must handle all existing song books without data loss. Requires server-side changes.

---

[← Back to remediation index](../tech-debt-remediation.md)
