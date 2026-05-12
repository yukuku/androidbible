package yuku.alkitab.base.services

import yuku.alkitab.base.storage.InternalDb
import yuku.alkitab.base.storage.SongDb

/**
 * Provides access to app-wide SQLite databases. Splitting database access behind
 * an interface lets tests substitute in-memory or stub databases without pulling
 * in the rest of the [yuku.alkitab.base.S] service locator.
 */
interface StorageProvider {
    val db: InternalDb
    val songDb: SongDb
}
