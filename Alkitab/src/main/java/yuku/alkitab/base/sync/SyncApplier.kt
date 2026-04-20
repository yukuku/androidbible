package yuku.alkitab.base.sync

import yuku.afw.storage.Preferences
import yuku.alkitab.base.model.ReadingPlan
import yuku.alkitab.base.model.SyncShadow
import yuku.alkitab.base.storage.InternalDb
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.util.Sqlitil
import yuku.alkitab.model.Marker
import yuku.alkitab.model.Marker_Label
import yuku.alkitab.model.Label
import yuku.alkitab.model.ProgressMark

/**
 * Applies server "append delta" payloads to the local database and updates the
 * corresponding [SyncShadow] row in the same transaction.
 *
 * Each `applyXxxAppendDelta` is a single transaction. They reject the delta if
 * the current entities diverge from what the server thought they were
 * ([Sync.ApplyAppendDeltaResult.dirty_entities]) or if the sync account has
 * changed since the request started ([Sync.ApplyAppendDeltaResult.dirty_sync_account]).
 *
 * Extracted from `InternalDb` — sync-application logic is a distinct
 * responsibility from SQLite access. All DB mutations go through [InternalDb]
 * delegators or DAO primitives, so the transactional and sync-notify semantics
 * are preserved.
 */
class SyncApplier(private val db: InternalDb) {

    fun applyMabelAppendDelta(
        finalRevno: Int,
        shadowEntities: List<Sync.Entity<Sync_Mabel.Content>>,
        clientState: Sync.ClientState<Sync_Mabel.Content>,
        appendDelta: Sync.Delta<Sync_Mabel.Content>,
        entitiesBeforeSync: List<Sync.Entity<Sync_Mabel.Content>>,
        simpleTokenBeforeSync: String,
    ): Sync.ApplyAppendDeltaResult {
        val sqliteDb = db.helper.writableDatabase
        sqliteDb.beginTransactionNonExclusive()
        Sync.notifySyncUpdatesOngoing(SyncShadow.SYNC_SET_MABEL, true)
        try {
            // if the current entities are not the same as the ones had when contacting server, reject this append delta.
            val currentEntities = Sync_Mabel.getEntitiesFromCurrent()
            if (!Sync.entitiesEqual(currentEntities, entitiesBeforeSync)) {
                return Sync.ApplyAppendDeltaResult.dirty_entities
            }

            // if the current simpleToken has changed (sync user logged off or changed), reject this append delta
            val simpleToken = Preferences.getString(Prefkey.sync_simpleToken)
            if (simpleTokenBeforeSync != simpleToken) {
                return Sync.ApplyAppendDeltaResult.dirty_sync_account
            }

            // apply changes, which is server append delta, to current entities
            for (o in appendDelta.operations) {
                when (o.opkind) {
                    Sync.Opkind.del -> when (o.kind) {
                        Sync.Entity.KIND_MARKER -> db.deleteMarkerByGid(o.gid)
                        Sync.Entity.KIND_LABEL -> db.deleteLabelByGid(o.gid)
                        Sync.Entity.KIND_MARKER_LABEL -> db.deleteMarker_LabelByGid(o.gid)
                        else -> return Sync.ApplyAppendDeltaResult.unknown_kind
                    }
                    Sync.Opkind.add, Sync.Opkind.mod -> when (o.kind) {
                        Sync.Entity.KIND_MARKER -> {
                            val marker: Marker? = db.getMarkerByGid(o.gid)
                            val newMarker = Sync_Mabel.updateMarkerWithEntityContent(marker, o.gid, o.content)
                            db.insertOrUpdateMarker(newMarker)
                        }
                        Sync.Entity.KIND_LABEL -> {
                            val label: Label? = db.getLabelByGid(o.gid)
                            val newLabel = Sync_Mabel.updateLabelWithEntityContent(label, o.gid, o.content)
                            db.insertOrUpdateLabel(newLabel)
                        }
                        Sync.Entity.KIND_MARKER_LABEL -> {
                            val markerLabel: Marker_Label? = db.getMarker_LabelByGid(o.gid)
                            val newMarkerLabel = Sync_Mabel.updateMarker_LabelWithEntityContent(markerLabel, o.gid, o.content)
                            db.insertOrUpdateMarker_Label(newMarkerLabel)
                        }
                        else -> return Sync.ApplyAppendDeltaResult.unknown_kind
                    }
                }
            }

            // apply changes, which are client delta, and server append delta, to shadow entities
            val shadowEntitiesPatched1 = SyncAdapter.patchNoConflict(shadowEntities, clientState.delta.operations)
            val shadowEntitiesPatched2 = SyncAdapter.patchNoConflict(shadowEntitiesPatched1, appendDelta.operations)

            val ss = Sync_Mabel.shadowFromEntities(shadowEntitiesPatched2, finalRevno)
            db.insertOrUpdateSyncShadowBySyncSetName(ss)

            sqliteDb.setTransactionSuccessful()
            return Sync.ApplyAppendDeltaResult.ok
        } finally {
            Sync.notifySyncUpdatesOngoing(SyncShadow.SYNC_SET_MABEL, false)
            sqliteDb.endTransaction()
        }
    }

    /**
     * Applies a pins (progress-mark) append delta. The entire pins set is a
     * single sync entity, so `del` and `add` are unsupported — only `mod` with
     * the full replacement pins list is valid.
     */
    fun applyPinsAppendDelta(
        finalRevno: Int,
        appendDelta: Sync.Delta<Sync_Pins.Content>,
        entitiesBeforeSync: List<Sync.Entity<Sync_Pins.Content>>,
        simpleTokenBeforeSync: String,
    ): Sync.ApplyAppendDeltaResult {
        val sqliteDb = db.helper.writableDatabase
        sqliteDb.beginTransactionNonExclusive()
        Sync.notifySyncUpdatesOngoing(SyncShadow.SYNC_SET_PINS, true)
        try {
            val currentEntities = Sync_Pins.getEntitiesFromCurrent()
            if (!Sync.entitiesEqual(currentEntities, entitiesBeforeSync)) {
                return Sync.ApplyAppendDeltaResult.dirty_entities
            }

            val simpleToken = Preferences.getString(Prefkey.sync_simpleToken)
            if (simpleTokenBeforeSync != simpleToken) {
                return Sync.ApplyAppendDeltaResult.dirty_sync_account
            }

            for (o in appendDelta.operations) {
                when (o.opkind) {
                    Sync.Opkind.del, Sync.Opkind.add ->
                        return Sync.ApplyAppendDeltaResult.unsupported_operation
                    Sync.Opkind.mod -> {
                        if (Sync.Entity.KIND_PINS != o.kind) {
                            return Sync.ApplyAppendDeltaResult.unknown_kind
                        }
                        // the whole logic to update all pins with the ones received from server (all pins in one entity)
                        val content = o.content ?: return Sync.ApplyAppendDeltaResult.unknown_kind
                        for (pin in content.pins ?: emptyList()) {
                            val pm = db.getProgressMarkByPresetId(pin.preset_id) ?: ProgressMark().apply {
                                preset_id = pin.preset_id
                            }
                            pm.ari = pin.ari
                            pm.caption = pin.caption
                            pm.modifyTime = Sqlitil.toDate(pin.modifyTime)
                            db.insertOrUpdateProgressMark(pm)
                        }
                    }
                }
            }

            val ss = Sync_Pins.shadowFromEntities(Sync_Pins.getEntitiesFromCurrent(), finalRevno)
            db.insertOrUpdateSyncShadowBySyncSetName(ss)

            sqliteDb.setTransactionSuccessful()
            return Sync.ApplyAppendDeltaResult.ok
        } finally {
            Sync.notifySyncUpdatesOngoing(SyncShadow.SYNC_SET_PINS, false)
            sqliteDb.endTransaction()
        }
    }

    fun applyRpAppendDelta(
        finalRevno: Int,
        appendDelta: Sync.Delta<Sync_Rp.Content>,
        entitiesBeforeSync: List<Sync.Entity<Sync_Rp.Content>>,
        simpleTokenBeforeSync: String,
    ): Sync.ApplyAppendDeltaResult {
        val sqliteDb = db.helper.writableDatabase
        sqliteDb.beginTransactionNonExclusive()
        Sync.notifySyncUpdatesOngoing(SyncShadow.SYNC_SET_RP, true)
        try {
            val currentEntities = Sync_Rp.getEntitiesFromCurrent()
            if (!Sync.entitiesEqual(currentEntities, entitiesBeforeSync)) {
                return Sync.ApplyAppendDeltaResult.dirty_entities
            }

            val simpleToken = Preferences.getString(Prefkey.sync_simpleToken)
            if (simpleTokenBeforeSync != simpleToken) {
                return Sync.ApplyAppendDeltaResult.dirty_sync_account
            }

            for (o in appendDelta.operations) {
                if (Sync.Entity.KIND_RP_PROGRESS != o.kind) {
                    return Sync.ApplyAppendDeltaResult.unknown_kind
                }

                when (o.opkind) {
                    Sync.Opkind.del -> db.readingPlanDao.deleteAllProgressForGid(o.gid)
                    Sync.Opkind.add, Sync.Opkind.mod -> {
                        val content = o.content ?: return Sync.ApplyAppendDeltaResult.unknown_kind
                        val readingCodes = db.readingPlanDao.getAllReadingCodesByProgressGid(o.gid)
                        val src = HashSet<Int>(readingCodes.size()).apply {
                            for (i in 0 until readingCodes.size()) add(readingCodes[i])
                        }
                        val dst = HashSet<Int>(content.done ?: emptyList())

                        // deletions
                        val toDel = HashSet(src).apply { removeAll(dst) }
                        for (value in toDel) {
                            db.readingPlanDao.deleteProgress(o.gid, value)
                        }

                        // additions — preserve single checkTime for the whole batch
                        val toAdd = HashSet(dst).apply { removeAll(src) }
                        val checkTime = System.currentTimeMillis()
                        for (value in toAdd) {
                            db.readingPlanDao.insertOrUpdateProgress(o.gid, value, checkTime)
                        }

                        // update startTime if it changed
                        val newStartTime = content.startTime
                        if (newStartTime != null) {
                            for (info in db.listAllReadingPlanInfo()) {
                                if (ReadingPlan.gidFromName(info.name) == o.gid) {
                                    if (info.startTime != newStartTime) {
                                        db.readingPlanDao.updateStartDate(info.id, newStartTime)
                                    }
                                    break
                                }
                            }
                        }
                    }
                }
            }

            val ss = Sync_Rp.shadowFromEntities(Sync_Rp.getEntitiesFromCurrent(), finalRevno)
            db.insertOrUpdateSyncShadowBySyncSetName(ss)

            sqliteDb.setTransactionSuccessful()
            return Sync.ApplyAppendDeltaResult.ok
        } finally {
            Sync.notifySyncUpdatesOngoing(SyncShadow.SYNC_SET_RP, false)
            sqliteDb.endTransaction()
        }
    }
}
