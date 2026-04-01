package yuku.alkitab.base.model

import org.json.JSONObject

/**
 * Timing information for a single verse within a chapter audio file.
 *
 * @param startMs start time in milliseconds
 * @param endMs end time in milliseconds
 * @param verseNumber 1-based verse number
 */
data class MTiming(
    val startMs: Long,
    val endMs: Long,
    val verseNumber: Int,
) {
    companion object {
        private fun fromJson(json: JSONObject): MTiming {
            val startMs = (json.getDouble("time_start") * 1000).toLong()
            val durationMs = (json.getDouble("duration") * 1000).toLong()
            return MTiming(
                startMs = startMs,
                endMs = startMs + durationMs,
                verseNumber = json.getInt("verse"),
            )
        }

        /**
         * Parse a full timing JSON response into a list of [MTiming].
         * Returns an empty list if parsing fails.
         */
        fun parseList(jsonText: String): List<MTiming> {
            return try {
                val arr = JSONObject(jsonText).getJSONArray("timestamps")
                List(arr.length()) { i -> fromJson(arr.getJSONObject(i)) }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
