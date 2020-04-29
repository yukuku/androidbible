package yuku.alkitab.base.util

import androidx.annotation.Keep
import yuku.afw.storage.Preferences
import yuku.alkitab.base.App
import yuku.alkitab.base.model.SyncShadow
import yuku.alkitab.base.storage.Prefkey
import yuku.alkitab.base.sync.Sync
import yuku.alkitab.base.util.InstallationUtil.getInstallationId
import yuku.alkitab.model.util.Gid
import java.util.ArrayList

private const val MAX_HISTORY_ENTRIES = 20
private const val TAG = "History"

object History {
    @Keep
    data class Entry(
        @JvmField
        val gid: String,

        @JvmField
        val ari: Int,

        @JvmField
        val timestamp: Long,

        /**
         * The installationId of the device that creates this history entry.
         */
        @JvmField
        val creator_id: String,

        /**
         * Whether this entry is created as a result of a jumpback operation,
         * i.e. the last verse viewed before navigating away.
         */
        @JvmField
        val jumpback: Boolean
    )

    @Keep
    data class HistoryJson(
        val entries: List<Entry>
    )

    val entries = mutableListOf<Entry>().apply {
        Preferences.getString(Prefkey.history)?.let { s ->
            this.addAll(App.getDefaultGson().fromJson(s, HistoryJson::class.java).entries)
        }
    }

    @Synchronized
    fun save() {
        val obj = HistoryJson(entries)
        val new_json = App.getDefaultGson().toJson(obj)
        val old_json = Preferences.getString(Prefkey.history)
        if (old_json != new_json) {
            Preferences.setString(Prefkey.history, new_json)
            Sync.notifySyncNeeded(SyncShadow.SYNC_SET_HISTORY)
        } else {
            AppLog.d(TAG, "History not changed.")
        }
    }

    @Synchronized
    fun add(ari: Int, jumpback: Boolean) {
        // check: do we have this previously?
        for (i in entries.indices.reversed()) {
            val entry = entries[i]
            if (entry.ari == ari) {
                // YES. Remove this.
                entries.removeAt(i)
            }
        }

        // Add it to the front
        entries.add(0, Entry(Gid.newGid(), ari, System.currentTimeMillis(), getInstallationId(), jumpback))

        // and remove if overflow
        while (entries.size > MAX_HISTORY_ENTRIES) {
            entries.removeAt(MAX_HISTORY_ENTRIES)
        }
    }

    @Synchronized
    fun getEntry(position: Int): Entry {
        return entries[position]
    }

    @get:Synchronized
    val size
        get() = entries.size

    fun listAllEntries(): List<Entry> {
        return ArrayList(entries)
    }
}
