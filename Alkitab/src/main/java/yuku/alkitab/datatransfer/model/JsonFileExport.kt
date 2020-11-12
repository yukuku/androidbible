package yuku.alkitab.datatransfer.model

class Root(
    val success: Boolean,
    val snapshots: Snapshots,
)

class Snapshots(
    val history: Snapshot<HistoryEntity>,
    val mabel: Snapshot<MabelEntity>,
    val pins: Snapshot<PinsEntity>,
    val rp: Snapshot<RppEntity>,
)

class Snapshot<E>(
    val revno: Int,
    val entities: List<E>,
)

abstract class Entity(
    val gid: String,
    val kind: String,
    val creator_id: String,
)

class HistoryEntity(
    gid: String,
    kind: String,
    creator_id: String,
    val content: HistoryContent,
) : Entity(gid, kind, creator_id)

class HistoryContent(
    val ari: Int,
    val timestamp: Long,
)

class MabelEntity(
    gid: String,
    kind: String,
    creator_id: String,
    val content: MabelContent,
) : Entity(gid, kind, creator_id)

abstract class MabelContent

class MarkerContent(
    val kind: Int,
    val ari: Int,
    val verseCount: Int,
    val createTime: Long,
    val modifyTime: Long,
    val caption: String,
) : MabelContent()

class LabelContent(
    val title: String,
    val ordering: Int,
) : MabelContent()

class MarkerLabelContent(
    val marker_gid: String,
    val label_gid: String,
) : MabelContent()

class PinsEntity(
    gid: String,
    kind: String,
    creator_id: String,
    val content: PinsContent,
) : Entity(gid, kind, creator_id)

class PinsContent(
    val pins: List<Pin>
)

class Pin(
    val ari: Int,
    val modifyTime: Long,
    val preset_id: Int,
)

class RppEntity(
    gid: String,
    kind: String,
    creator_id: String,
    val content: RppContent,
) : Entity(gid, kind, creator_id)

class RppContent(
    val startTime: Long,
    val done: List<Int>,
)

