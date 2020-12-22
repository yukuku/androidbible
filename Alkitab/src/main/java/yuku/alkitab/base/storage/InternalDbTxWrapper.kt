package yuku.alkitab.base.storage

object InternalDbTxWrapper {
    fun interface TxHandle {
        fun commit()
    }

    fun transact(internal: InternalDb, action: (TxHandle) -> Unit) {
        val db = internal.helper.writableDatabase
        db.beginTransactionNonExclusive()
        var committed = false
        action { committed = true }
        if (committed) {
            db.setTransactionSuccessful()
        }
        db.endTransaction()
    }
}
