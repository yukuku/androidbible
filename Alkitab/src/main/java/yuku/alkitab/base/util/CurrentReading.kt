package yuku.alkitab.base.util

import yuku.afw.storage.Preferences
import yuku.alkitab.base.events.AppEvents
import yuku.alkitab.base.storage.Prefkey

/**
 * Manages current reading (selected from reading plan) with persistence.
 */
object CurrentReading {
    @JvmStatic
    fun set(ariStart: Int, ariEnd: Int) {
        Preferences.withTransaction {
            Preferences.setInt(Prefkey.current_reading_ari_start, ariStart)
            Preferences.setInt(Prefkey.current_reading_ari_end, ariEnd)
        }

        AppEvents.emitCurrentReadingChanged()
    }

    @JvmStatic
    fun clear() {
        Preferences.withTransaction {
            Preferences.remove(Prefkey.current_reading_ari_start)
            Preferences.remove(Prefkey.current_reading_ari_end)
        }

        AppEvents.emitCurrentReadingChanged()
    }

    /**
     * @return null if no current reading
     */
    @JvmStatic
    fun get(): IntArray? {
        if (!Preferences.contains(Prefkey.current_reading_ari_start)) {
            return null
        }

        return intArrayOf(
            Preferences.getInt(Prefkey.current_reading_ari_start, 0),
            Preferences.getInt(Prefkey.current_reading_ari_end, 0),
        )
    }
}
