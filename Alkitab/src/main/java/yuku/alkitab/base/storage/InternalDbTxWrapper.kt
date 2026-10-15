package yuku.alkitab.base.storage

object InternalDbTxWrapper {
    fun interface TxHandle {
        fun commit()
    }

    fun transact(internal: InternalDb, action: (TxHandle) -> Unit) {
        val db = internal.helper.writableDatabase
        db.beginTransactionNonExclusive()
        try {
            var isCommitted = false
            action { isCommitted = true }
            if (isCommitted) {
                db.setTransactionSuccessful()
            }
        } finally {
            db.endTransaction()
        }
    }
}
