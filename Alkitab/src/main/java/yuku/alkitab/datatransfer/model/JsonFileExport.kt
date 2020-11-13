package yuku.alkitab.datatransfer.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class Root(
    val success: Boolean,
    val snapshots: Snapshots,
)

@JsonClass(generateAdapter = true)
class Snapshots(
    val history: Snapshot<HistoryEntity>,
    val mabel: Snapshot<MabelEntity>,
    val pins: Snapshot<PinsEntity>,
    val rp: Snapshot<RppEntity>,
)

@JsonClass(generateAdapter = true)
class Snapshot<E>(
    val revno: Int,
    val entities: List<E>,
)

abstract class Entity(
    val gid: String,
    val kind: String,
    val creator_id: String,
)

@JsonClass(generateAdapter = true)
class HistoryEntity(
    gid: String,
    kind: String,
    creator_id: String,
    val content: HistoryContent,
) : Entity(gid, kind, creator_id)

@JsonClass(generateAdapter = true)
class HistoryContent(
    val ari: Int,
    val timestamp: Long,
)

@JsonClass(generateAdapter = true)
class MabelEntity(
    gid: String,
    kind: String,
    creator_id: String,
    val content: MabelContent,
) : Entity(gid, kind, creator_id)

abstract class MabelContent

@JsonClass(generateAdapter = true)
class MarkerContent(
    val kind: Int,
    val ari: Int,
    val verseCount: Int,
    val createTime: Long,
    val modifyTime: Long,
    val caption: String,
) : MabelContent()

@JsonClass(generateAdapter = true)
class LabelContent(
    val title: String,
    val ordering: Int,
) : MabelContent()

@JsonClass(generateAdapter = true)
class MarkerLabelContent(
    val marker_gid: String,
    val label_gid: String,
) : MabelContent()

@JsonClass(generateAdapter = true)
class PinsEntity(
    gid: String,
    kind: String,
    creator_id: String,
    val content: PinsContent,
) : Entity(gid, kind, creator_id)

@JsonClass(generateAdapter = true)
class PinsContent(
    val pins: List<Pin>
)

@JsonClass(generateAdapter = true)
class Pin(
    val ari: Int,
    val modifyTime: Long,
    val preset_id: Int,
)

@JsonClass(generateAdapter = true)
class RppEntity(
    gid: String,
    kind: String,
    creator_id: String,
    val content: RppContent,
) : Entity(gid, kind, creator_id)

@JsonClass(generateAdapter = true)
class RppContent(
    val startTime: Long,
    val done: List<Int>,
)

