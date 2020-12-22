package yuku.alkitab.datatransfer.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import yuku.alkitab.base.sync.Sync

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
    abstract val creator_id: String
}

@Serializable
sealed class EntityWithKind : Entity() {
    abstract val kind: String
}

@Serializable
data class HistoryEntity(
    override val gid: String,
    override val kind: String,
    override val creator_id: String,
    val content: HistoryContent,
) : EntityWithKind()

@Serializable
data class HistoryContent(
    val ari: Int,
    val timestamp: Long,
)

/**
 * This entity has "kind" property as type ddefined by the serializer.
 */
@Serializable
sealed class MabelEntity : Entity()

@Serializable
@SerialName(Sync.Entity.KIND_MARKER)
data class MarkerEntity(
    override val gid: String,
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
@SerialName(Sync.Entity.KIND_LABEL)
data class LabelEntity(
    override val gid: String,
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
@SerialName(Sync.Entity.KIND_MARKER_LABEL)
data class MarkerLabelEntity(
    override val gid: String,
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
) : EntityWithKind()

@Serializable
data class PinsContent(
    val pins: List<Pin>,
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
) : EntityWithKind()

@Serializable
data class RppContent(
    val startTime: Long,
    val done: List<Int>,
)

