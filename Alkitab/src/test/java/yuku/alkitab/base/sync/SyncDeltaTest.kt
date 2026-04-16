package yuku.alkitab.base.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import yuku.alkitab.base.sync.Sync.Delta
import yuku.alkitab.base.sync.Sync.Entity
import yuku.alkitab.base.sync.Sync.Operation
import yuku.alkitab.base.sync.Sync.Opkind

/**
 * Unit tests for sync delta generation and application utilities:
 * - [SyncAdapter.patchNoConflict]
 * - [Sync.entitiesEqual]
 * - [SyncUtils.findEntity]
 * - [SyncUtils.isSameContent]
 *
 * These cover the core of the sync protocol: computing a delta from two sets
 * of entities, and applying a delta to reach a new state.
 */
class SyncDeltaTest {
    /** Helper to build a typed `del` op (Kotlin can't infer `Nothing?` → `Content?`). */
    private fun delOp(kind: String, gid: String): Operation<Sync_Mabel.Content> =
        Operation<Sync_Mabel.Content>(Opkind.del, kind, gid, null)

    private fun marker(gid: String, ari: Int, caption: String? = null): Entity<Sync_Mabel.Content> {
        val c = Sync_Mabel.Content()
        c.ari = ari
        c.caption = caption
        c.kind = 1
        return Entity(Entity.KIND_MARKER, gid, c)
    }

    private fun label(gid: String, title: String, ordering: Int): Entity<Sync_Mabel.Content> {
        val c = Sync_Mabel.Content()
        c.title = title
        c.ordering = ordering
        return Entity(Entity.KIND_LABEL, gid, c)
    }

    //region patchNoConflict

    @Test
    fun patchNoConflict_emptyEntitiesEmptyOps_returnsEmpty() {
        val out = SyncAdapter.patchNoConflict(emptyList<Entity<Sync_Mabel.Content>>(), emptyList())
        assertEquals(0, out.size)
    }

    @Test
    fun patchNoConflict_addOp_insertsNewEntity() {
        val entities = listOf(marker("g1", 1))
        val ops = listOf(Operation(Opkind.add, Entity.KIND_MARKER, "g2", markerContent(ari = 2)))

        val out = SyncAdapter.patchNoConflict(entities, ops)

        assertEquals(2, out.size)
        val byGid = out.associateBy { it.gid }
        assertNotNull(byGid["g1"])
        assertNotNull(byGid["g2"])
        assertEquals(2, byGid["g2"]!!.content.ari)
    }

    @Test
    fun patchNoConflict_modOp_overwritesExistingEntity() {
        val entities = listOf(marker("g1", 1, "old caption"))
        val newContent = markerContent(ari = 99, caption = "new caption")
        val ops = listOf(Operation(Opkind.mod, Entity.KIND_MARKER, "g1", newContent))

        val out = SyncAdapter.patchNoConflict(entities, ops)

        assertEquals(1, out.size)
        assertEquals("g1", out[0].gid)
        assertEquals(99, out[0].content.ari)
        assertEquals("new caption", out[0].content.caption)
    }

    @Test
    fun patchNoConflict_delOp_removesEntity() {
        val entities = listOf(marker("g1", 1), marker("g2", 2))
        val ops = listOf(delOp(Entity.KIND_MARKER, "g1"))

        val out = SyncAdapter.patchNoConflict(entities, ops)

        assertEquals(1, out.size)
        assertEquals("g2", out[0].gid)
    }

    @Test
    fun patchNoConflict_delMissingGid_isNoOp() {
        // Server asks us to delete something we no longer have. Should be tolerated.
        val entities = listOf(marker("g1", 1))
        val ops = listOf(delOp(Entity.KIND_MARKER, "missing"))

        val out = SyncAdapter.patchNoConflict(entities, ops)

        assertEquals(1, out.size)
        assertEquals("g1", out[0].gid)
    }

    @Test
    fun patchNoConflict_addExistingGid_overwrites() {
        // When server sends `add` for something we already have (shouldn't normally happen,
        // but protocol tolerates it), add is treated identically to mod.
        val entities = listOf(marker("g1", 1, "existing"))
        val ops = listOf(Operation(Opkind.add, Entity.KIND_MARKER, "g1", markerContent(ari = 42, caption = "overwritten")))

        val out = SyncAdapter.patchNoConflict(entities, ops)

        assertEquals(1, out.size)
        assertEquals(42, out[0].content.ari)
        assertEquals("overwritten", out[0].content.caption)
    }

    @Test
    fun patchNoConflict_modNonExistentGid_insertsEntity() {
        // Port of server behavior: add and mod are the same operation - overwrite.
        // So mod on a missing gid inserts it.
        val entities = emptyList<Entity<Sync_Mabel.Content>>()
        val ops = listOf(Operation(Opkind.mod, Entity.KIND_MARKER, "new-gid", markerContent(ari = 7)))

        val out = SyncAdapter.patchNoConflict(entities, ops)

        assertEquals(1, out.size)
        assertEquals("new-gid", out[0].gid)
        assertEquals(7, out[0].content.ari)
    }

    @Test
    fun patchNoConflict_sameGidDifferentKind_treatedSeparately() {
        // Marker "g1" and Label "g1" are different entities (keyed by gid+kind).
        val entities = listOf(marker("g1", 1, "marker caption"))
        val ops = listOf(Operation(Opkind.add, Entity.KIND_LABEL, "g1", labelContent("my label")))

        val out = SyncAdapter.patchNoConflict(entities, ops)

        assertEquals(2, out.size)
        val marker = out.first { it.kind == Entity.KIND_MARKER }
        val label = out.first { it.kind == Entity.KIND_LABEL }
        assertEquals("marker caption", marker.content.caption)
        assertEquals("my label", label.content.title)
    }

    @Test
    fun patchNoConflict_delForWrongKind_doesNotRemove() {
        // A del op with the same gid but wrong kind must not remove the other entity.
        val entities = listOf(marker("g1", 1))
        val ops = listOf(delOp(Entity.KIND_LABEL, "g1"))

        val out = SyncAdapter.patchNoConflict(entities, ops)

        assertEquals(1, out.size)
        assertEquals(Entity.KIND_MARKER, out[0].kind)
    }

    @Test
    fun patchNoConflict_multipleModsSameGid_lastWins() {
        // Concurrent edits / replayed ops: multiple mods to same gid - last one wins.
        val entities = listOf(marker("g1", 1, "original"))
        val ops = listOf(
            Operation(Opkind.mod, Entity.KIND_MARKER, "g1", markerContent(caption = "edit1")),
            Operation(Opkind.mod, Entity.KIND_MARKER, "g1", markerContent(caption = "edit2")),
            Operation(Opkind.mod, Entity.KIND_MARKER, "g1", markerContent(caption = "final")),
        )

        val out = SyncAdapter.patchNoConflict(entities, ops)

        assertEquals(1, out.size)
        assertEquals("final", out[0].content.caption)
    }

    @Test
    fun patchNoConflict_addThenDel_removesEntity() {
        val entities = emptyList<Entity<Sync_Mabel.Content>>()
        val ops = listOf(
            Operation(Opkind.add, Entity.KIND_MARKER, "g1", markerContent(ari = 5)),
            delOp(Entity.KIND_MARKER, "g1"),
        )

        val out = SyncAdapter.patchNoConflict(entities, ops)

        assertEquals(0, out.size)
    }

    @Test
    fun patchNoConflict_delThenAdd_restoresEntity() {
        val entities = listOf(marker("g1", 1, "original"))
        val ops = listOf(
            delOp(Entity.KIND_MARKER, "g1"),
            Operation(Opkind.add, Entity.KIND_MARKER, "g1", markerContent(ari = 99, caption = "restored")),
        )

        val out = SyncAdapter.patchNoConflict(entities, ops)

        assertEquals(1, out.size)
        assertEquals(99, out[0].content.ari)
        assertEquals("restored", out[0].content.caption)
    }

    @Test
    fun patchNoConflict_mixedOperations() {
        val entities = listOf(
            marker("g1", 1, "one"),
            marker("g2", 2, "two"),
            marker("g3", 3, "three"),
        )
        val ops = listOf(
            delOp(Entity.KIND_MARKER, "g2"),
            Operation(Opkind.mod, Entity.KIND_MARKER, "g3", markerContent(ari = 33, caption = "threeV2")),
            Operation(Opkind.add, Entity.KIND_MARKER, "g4", markerContent(ari = 4, caption = "four")),
        )

        val out = SyncAdapter.patchNoConflict(entities, ops)

        val byGid = out.associateBy { it.gid }
        assertEquals(3, out.size)
        assertNotNull(byGid["g1"])
        assertNull(byGid["g2"])
        assertEquals(33, byGid["g3"]!!.content.ari)
        assertEquals("threeV2", byGid["g3"]!!.content.caption)
        assertEquals(4, byGid["g4"]!!.content.ari)
    }

    @Test
    fun patchNoConflict_doesNotMutateInputList() {
        // The algorithm should return a new list, not mutate the caller's.
        val entities = mutableListOf(marker("g1", 1))
        val originalSize = entities.size
        val ops = listOf(Operation(Opkind.add, Entity.KIND_MARKER, "g2", markerContent(ari = 2)))

        SyncAdapter.patchNoConflict(entities, ops)

        assertEquals(originalSize, entities.size)
    }

    //endregion

    //region entitiesEqual

    @Test
    fun entitiesEqual_bothEmpty_true() {
        assertTrue(Sync.entitiesEqual(emptyList<Entity<Sync_Mabel.Content>>(), emptyList()))
    }

    @Test
    fun entitiesEqual_sameOrder_true() {
        val a = listOf(marker("g1", 1), marker("g2", 2))
        val b = listOf(marker("g1", 1), marker("g2", 2))
        assertTrue(Sync.entitiesEqual(a, b))
    }

    @Test
    fun entitiesEqual_differentOrder_true() {
        // The whole point: order doesn't matter.
        val a = listOf(marker("g1", 1), marker("g2", 2), marker("g3", 3))
        val b = listOf(marker("g3", 3), marker("g1", 1), marker("g2", 2))
        assertTrue(Sync.entitiesEqual(a, b))
    }

    @Test
    fun entitiesEqual_differentSizes_false() {
        val a = listOf(marker("g1", 1), marker("g2", 2))
        val b = listOf(marker("g1", 1))
        assertFalse(Sync.entitiesEqual(a, b))
    }

    @Test
    fun entitiesEqual_differentContent_false() {
        val a = listOf(marker("g1", 1, "original"))
        val b = listOf(marker("g1", 1, "different"))
        assertFalse(Sync.entitiesEqual(a, b))
    }

    @Test
    fun entitiesEqual_differentGids_false() {
        val a = listOf(marker("g1", 1))
        val b = listOf(marker("g2", 1))
        assertFalse(Sync.entitiesEqual(a, b))
    }

    //endregion

    //region SyncUtils

    @Test
    fun findEntity_present_returnsMatch() {
        val list = listOf(marker("g1", 1), label("g2", "my label", 0), marker("g3", 3))
        val found = SyncUtils.findEntity(list, "g2", Entity.KIND_LABEL)
        assertNotNull(found)
        assertEquals("g2", found!!.gid)
    }

    @Test
    fun findEntity_missingGid_returnsNull() {
        val list = listOf(marker("g1", 1))
        assertNull(SyncUtils.findEntity(list, "nope", Entity.KIND_MARKER))
    }

    @Test
    fun findEntity_sameGidWrongKind_returnsNull() {
        // Same gid but different kind must NOT match. Gid+kind together form the key.
        val list = listOf(marker("g1", 1))
        assertNull(SyncUtils.findEntity(list, "g1", Entity.KIND_LABEL))
    }

    @Test
    fun findEntity_emptyList_returnsNull() {
        assertNull(SyncUtils.findEntity(emptyList<Entity<Sync_Mabel.Content>>(), "g1", Entity.KIND_MARKER))
    }

    @Test
    fun isSameContent_sameGidKindContent_true() {
        val a = marker("g1", 5, "hi")
        val b = marker("g1", 5, "hi")
        assertTrue(SyncUtils.isSameContent(a, b))
    }

    @Test
    fun isSameContent_differentContent_false() {
        val a = marker("g1", 5, "hi")
        val b = marker("g1", 5, "bye")
        assertFalse(SyncUtils.isSameContent(a, b))
    }

    @Test
    fun isSameContent_differentGid_false() {
        val a = marker("g1", 5, "hi")
        val b = marker("g2", 5, "hi")
        assertFalse(SyncUtils.isSameContent(a, b))
    }

    @Test
    fun isSameContent_differentKind_false() {
        val m = marker("g1", 5, "hi")
        val l = label("g1", "hi", 0).also { it.content.ari = 5 }
        assertFalse(SyncUtils.isSameContent(m, l))
    }

    //endregion

    //region Delta computation + application round-trip

    /**
     * Mimics the delta-computation logic that lives inside each
     * `Sync_*.getClientStateAndCurrentEntities()` — kept here in test code so
     * we can verify round-trips without touching the DB-backed production path.
     */
    private fun <C> computeDelta(srcs: List<Entity<C>>, dsts: List<Entity<C>>): Delta<C> {
        val delta = Delta<C>()
        for (dst in dsts) {
            val existing = SyncUtils.findEntity(srcs, dst.gid, dst.kind)
            if (existing == null) {
                delta.operations.add(Operation(Opkind.add, dst.kind, dst.gid, dst.content))
            } else if (!SyncUtils.isSameContent(dst, existing)) {
                delta.operations.add(Operation(Opkind.mod, dst.kind, dst.gid, dst.content))
            }
        }
        for (src in srcs) {
            if (SyncUtils.findEntity(dsts, src.gid, src.kind) == null) {
                delta.operations.add(Operation<C>(Opkind.del, src.kind, src.gid, null))
            }
        }
        return delta
    }

    @Test
    fun roundTrip_unchangedState_producesEmptyDelta() {
        val state = listOf(marker("g1", 1), marker("g2", 2))
        val delta = computeDelta(state, state)
        assertEquals(0, delta.operations.size)
    }

    @Test
    fun roundTrip_purelyAdditive() {
        val shadow = listOf(marker("g1", 1))
        val current = listOf(marker("g1", 1), marker("g2", 2, "new"))

        val delta = computeDelta(shadow, current)
        val applied = SyncAdapter.patchNoConflict(shadow, delta.operations)

        assertTrue(Sync.entitiesEqual(applied, current))
        assertEquals(1, delta.operations.size)
        assertEquals(Opkind.add, delta.operations[0].opkind)
    }

    @Test
    fun roundTrip_modificationOnly() {
        val shadow = listOf(marker("g1", 1, "old"))
        val current = listOf(marker("g1", 1, "new"))

        val delta = computeDelta(shadow, current)
        val applied = SyncAdapter.patchNoConflict(shadow, delta.operations)

        assertTrue(Sync.entitiesEqual(applied, current))
        assertEquals(1, delta.operations.size)
        assertEquals(Opkind.mod, delta.operations[0].opkind)
    }

    @Test
    fun roundTrip_deletionOnly() {
        val shadow = listOf(marker("g1", 1), marker("g2", 2))
        val current = listOf(marker("g1", 1))

        val delta = computeDelta(shadow, current)
        val applied = SyncAdapter.patchNoConflict(shadow, delta.operations)

        assertTrue(Sync.entitiesEqual(applied, current))
        assertEquals(1, delta.operations.size)
        assertEquals(Opkind.del, delta.operations[0].opkind)
        assertEquals("g2", delta.operations[0].gid)
    }

    @Test
    fun roundTrip_mixedOperations_reconstructsCurrent() {
        // Realistic scenario: shadow is what server knows, current is what we have now.
        // The computed delta, applied to shadow, must reproduce current.
        val shadow = listOf(
            marker("g1", 10, "one"),
            marker("g2", 20, "two"),
            marker("g3", 30, "three"),
            label("lb1", "Favorites", 0),
        )
        val current = listOf(
            marker("g1", 10, "one"),           // unchanged
            marker("g2", 20, "TWO-renamed"),   // modified
            // g3 deleted
            marker("g4", 40, "four"),          // newly added
            label("lb1", "Favorites!", 1),     // modified label (title and ordering)
        )

        val delta = computeDelta(shadow, current)
        val applied = SyncAdapter.patchNoConflict(shadow, delta.operations)

        assertTrue(
            "Applying computed delta to shadow must reproduce current (order-insensitive)",
            Sync.entitiesEqual(applied, current),
        )
    }

    @Test
    fun roundTrip_conflict_serverAlsoDeletes_localStillConverges() {
        // Conflict: we deleted g2 locally AND server also sends a del for g2 in append_delta.
        // Applying the server's append_delta on top of our current state must be idempotent.
        val currentLocal = listOf(marker("g1", 1))
        val serverAppendDelta = listOf(delOp(Entity.KIND_MARKER, "g2"))

        val applied = SyncAdapter.patchNoConflict(currentLocal, serverAppendDelta)

        assertTrue(Sync.entitiesEqual(applied, currentLocal))
    }

    //endregion

    //region helpers

    private fun markerContent(ari: Int = 0, caption: String? = null, kind: Int = 1): Sync_Mabel.Content {
        val c = Sync_Mabel.Content()
        c.ari = ari
        c.caption = caption
        c.kind = kind
        return c
    }

    private fun labelContent(title: String, ordering: Int = 0): Sync_Mabel.Content {
        val c = Sync_Mabel.Content()
        c.title = title
        c.ordering = ordering
        return c
    }

    //endregion
}
