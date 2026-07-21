# REM-42: Bounded Eviction for the Version Cache

**Addresses:** TD-02 (adjacent) — version loading
**Module:** Versions (`MVersionDb`, `VersionImpl`)
**BRICE:** B=2 R=1 I=3 C=3 E=3 → **2.4**
**Phase:** 4 — Long-term / Minor

**Problem:** `MVersionDb` caches `VersionImpl` with `SoftReference` in a `ConcurrentHashMap`. Under memory pressure the GC reclaims every cached version at once, so a burst of chapter reads afterward each re-open and re-parse their `.yes` file. There is no LRU bound, no eviction policy, and no instrumentation to see how often it happens.

**Steps:**
1. Replace the `SoftReference` map with a small `LruCache` (a handful of entries is plenty — only the active + split versions are hot).
2. Keep a `SoftReference` second tier only if profiling shows re-parse cost matters for cold versions.
3. Add a debug log/metric on cache miss so the reload rate is observable.

**Difficulty:** Easy. Low priority — the current behavior is a performance nuisance, not a correctness bug.

---

[← Back to remediation index](../tech-debt-remediation.md)
