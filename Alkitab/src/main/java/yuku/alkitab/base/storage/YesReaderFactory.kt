package yuku.alkitab.base.storage

import java.io.IOException
import java.io.RandomAccessFile
import yuku.alkitab.base.util.AppLog
import yuku.alkitab.io.BibleReader
import yuku.alkitab.yes1.Yes1Reader
import yuku.alkitab.yes2.Yes2Reader
import yuku.alkitab.yes2.io.RandomAccessFileRandomInputStream

object YesReaderFactory {
    private val TAG: String = YesReaderFactory::class.java.simpleName

    private val MAGIC = byteArrayOf(0x98.toByte(), 0x58, 0x0d, 0x0a, 0x00, 0x5d, 0xe0.toByte())

    /**
     * @return A [Yes1Reader], [Yes2Reader], or null if there is any error.
     */
    @JvmStatic
    fun createYesReader(filename: String): BibleReader? {
        try {
            val header = ByteArray(8)

            val f = RandomAccessFile(filename, "r")
            f.read(header)
            f.close()

            if (!header.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
                AppLog.e(TAG, "Yes file '" + filename + "' has not a correct header. Header is: " + header.contentToString())
                return null
            }

            return when (header[7].toInt()) {
                0x01 -> Yes1Reader(filename) // VERSION 1 YES
                0x02 -> Yes2Reader(RandomAccessFileRandomInputStream(filename)) // VERSION 2 YES
                else -> {
                    AppLog.e(TAG, "Yes file version unsupported: " + header[7])
                    null
                }
            }
        } catch (e: IOException) {
            AppLog.e(TAG, "@@createYesReader io exception", e)
            return null
        }
    }
}
