package yuku.alkitab.base.model

import org.json.JSONObject

data class MTiming(
    val startTime: Long,
    val endTime: Long,
    val verseNumber: Int
) {
    companion object {
        fun fromJson(json: JSONObject): MTiming {
            val startTime = (json.getString("time_start").toFloat() * 1000).toLong()
            val duration = (json.getString("duration").toFloat() * 1000).toLong()
            val endTime = startTime + duration
            val verseNumber = json.getInt("verse")

            return MTiming(startTime, endTime, verseNumber)
        }

        fun fromJsonArray(jsonText: String): List<Triple<Long, Long, Int>> {
            val timestampsArray = JSONObject(jsonText).getJSONArray("timestamps")
            return List(timestampsArray.length()) { i ->
                val timing = fromJson(timestampsArray.getJSONObject(i))
                Triple(timing.startTime, timing.endTime, timing.verseNumber)
            }
        }
    }
}
