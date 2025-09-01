package yuku.alkitab.base.sync

object SyncUtils {

    @JvmStatic
    fun <C> isSameContent(a: Sync.Entity<C>, b: Sync.Entity<C>): Boolean {
        if (a.gid != b.gid) return false
        if (a.kind != b.kind) return false
        return a.content == b.content
    }

    @JvmStatic
    fun <C> findEntity(list: List<Sync.Entity<C>>, gid: String, kind: String): Sync.Entity<C>? {
        for (entity in list) {
            if (gid == entity.gid && kind == entity.kind) {
                return entity
            }
        }
        return null
    }

    interface ThrowEverythingRunnable {
        @Throws(Exception::class)
        fun run()
    }

    @JvmStatic
    fun wontThrow(r: ThrowEverythingRunnable) {
        try {
            r.run()
        } catch (e: Exception) {
            throw RuntimeException("ThrowEverythingRunnable is passed but caused exception: $r", e)
        }
    }
}
