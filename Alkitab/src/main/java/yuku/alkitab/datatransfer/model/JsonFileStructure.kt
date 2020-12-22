package yuku.alkitab.datatransfer.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class Root(
    val success: Boolean,
    val snapshots: Snapshots,
)

@Serializable
data class Snapshots(
    val history: Snapshot<HistoryEntity>,
    val mabel: Snapshot<MabelEntity>,
    val pins: Snapshot<PinsEntity>,
    val rp: Snapshot<RppEntity>,
)

@Serializable
data class Snapshot<E>(
    val revno: Int = 0,
    val entities: List<E>,
)

@Serializable
sealed class Entity {
    abstract val gid: String
    abstract val kind: String
    abstract val creator_id: String
}

@Serializable
data class HistoryEntity(
    override val gid: String,
    override val kind: String,
    override val creator_id: String,
    val content: HistoryContent,
) : Entity()

@Serializable
data class HistoryContent(
    val ari: Int,
    val timestamp: Long,
)

@Serializable
sealed class MabelEntity: Entity()

@Serializable
@SerialName("Marker")
data class MarkerEntity(
    override val gid: String,
    override val kind: String,
    override val creator_id: String,
    val content: MarkerContent,
) : MabelEntity()

@Serializable
data class MarkerContent(
    val kind: Int,
    val ari: Int,
    val verseCount: Int,
    val createTime: Int,
    val modifyTime: Int,
    val caption: String,
)

@Serializable
@SerialName("Label")
data class LabelEntity(
    override val gid: String,
    override val kind: String,
    override val creator_id: String,
    val content: LabelContent,
) : MabelEntity()

@Serializable
data class LabelContent(
    val title: String,
    val ordering: Int,
    val backgroundColor: String? = null,
)

@Serializable
@SerialName("Marker_Label")
data class MarkerLabelEntity(
    override val gid: String,
    override val kind: String,
    override val creator_id: String,
    val content: MarkerLabelContent,
) : MabelEntity()

@Serializable
data class MarkerLabelContent(
    val marker_gid: String,
    val label_gid: String,
)

@Serializable
data class PinsEntity(
    override val gid: String,
    override val kind: String,
    override val creator_id: String,
    val content: PinsContent,
) : Entity()

@Serializable
data class PinsContent(
    val pins: List<Pin>
)

@Serializable
data class Pin(
    val ari: Int,
    val modifyTime: Int,
    val preset_id: Int,
    val caption: String? = null,
)

@Serializable
data class RppEntity(
    override val gid: String,
    override val kind: String,
    override val creator_id: String,
    val content: RppContent,
) : Entity()

@Serializable
data class RppContent(
    val startTime: Long,
    val done: List<Int>,
)

