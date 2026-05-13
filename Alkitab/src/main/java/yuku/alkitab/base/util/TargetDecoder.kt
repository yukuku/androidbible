package yuku.alkitab.base.util

import yuku.alkitab.util.Ari
import yuku.alkitab.util.IntArrayList
import java.util.regex.Pattern

object TargetDecoder {
    private val TAG = TargetDecoder::class.java.simpleName

    private val rangeSplitter = Pattern.compile(",")
    private val startEndSplitter = Pattern.compile("-")

    /**
     * Returns ari ranges for encoded target. Targets can be encoded using any of the following (using examples):
     * a:[ari start]-[ari end],[ari single verse]
     * ari:[ari start]-[ari end],[ari single verse] (alternative format)
     * o:[osis start]-[osis end],[osis single verse]
     * lid:[lid start]-[lid end],[lid single verse]
     *
     * @return [start, end, ..., start, end] or null if can't decode.
     */
    @JvmStatic
    fun decode(encoded: String): IntArrayList {
        val (type, rangesJoined) = when {
            encoded.startsWith("o:") -> 1 to encoded.substring(2)
            encoded.startsWith("a:") -> 2 to encoded.substring(2)
            encoded.startsWith("ari:") -> 2 to encoded.substring(4)
            encoded.startsWith("lid:") -> 3 to encoded.substring(4)
            else -> {
                AppLog.e(TAG, "Unknown target format: $encoded")
                return IntArrayList()
            }
        }

        val ranges = rangeSplitter.split(rangesJoined, -1)
        val res = IntArrayList(ranges.size * 2)
        for (range in ranges) {
            val startEnd = startEndSplitter.split(range, 2)
            if (startEnd.size == 1) {
                val ari = decodeSingle(type, startEnd[0])
                if (ari != 0) {
                    res.add(ari)
                    res.add(ari)
                }
            } else {
                val ariStart = decodeSingle(type, startEnd[0])
                val ariEnd = decodeSingle(type, startEnd[1])
                if (ariStart != 0 && ariEnd != 0) {
                    res.add(ariStart)
                    res.add(ariEnd)
                }
            }
        }

        return res
    }


    private fun decodeSingle(type: Int, single: String): Int = when (type) {
        1 -> OsisBookNames.osisToAri(single)
        2 -> Ari.parseInt(single, 0)
        3 -> LidToAri.lidToAri(Ari.parseInt(single, 0))
        else -> 0
    }
}
