@file:OptIn(kotlinx.serialization.InternalSerializationApi::class, kotlinx.serialization.ExperimentalSerializationApi::class)

package yuku.alkitab.songs.newdoc

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * These serializers all operate directly on the JSON element tree
 * ([JsonEncoder] / [JsonDecoder]) rather than the usual structural
 * encode/decode calls, because [Line] / [VerseLine] / [Block] are shaped by
 * *JSON value kind* (string vs array vs object), not by a fixed field
 * layout. This is the standard kotlinx.serialization pattern for
 * "one of several JSON shapes" unions; see design §3.4 / §3.2.
 */

private fun jsonEncoderOf(encoder: Encoder): JsonEncoder =
    encoder as? JsonEncoder ?: error("${encoder::class.simpleName} only supports JSON encoding")

private fun jsonDecoderOf(decoder: Decoder): JsonDecoder =
    decoder as? JsonDecoder ?: error("${decoder::class.simpleName} only supports JSON decoding")

object LineSerializer : KSerializer<Line> {
    override val descriptor: SerialDescriptor = buildSerialDescriptor("yuku.alkitab.songs.newdoc.Line", SerialKind.CONTEXTUAL)

    override fun serialize(encoder: Encoder, value: Line) {
        val e = jsonEncoderOf(encoder)
        val element: JsonElement = when (value) {
            is Line.Plain -> JsonPrimitive(value.text)
            is Line.Styled -> JsonArray(value.spans.map { e.json.encodeToJsonElement(Span.serializer(), it) })
        }
        e.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): Line {
        val d = jsonDecoderOf(decoder)
        return when (val element = d.decodeJsonElement()) {
            is JsonArray -> Line.Styled(element.map { d.json.decodeFromJsonElement(Span.serializer(), it) })
            is JsonPrimitive -> Line.Plain(element.content)
            else -> error("Invalid Line JSON: $element")
        }
    }
}

object VerseLineSerializer : KSerializer<VerseLine> {
    override val descriptor: SerialDescriptor = buildSerialDescriptor("yuku.alkitab.songs.newdoc.VerseLine", SerialKind.CONTEXTUAL)

    override fun serialize(encoder: Encoder, value: VerseLine) {
        val e = jsonEncoderOf(encoder)
        val element: JsonElement = when (value) {
            is VerseLine.Simple -> e.json.encodeToJsonElement(LineSerializer, value.line)
            is VerseLine.Wrapped -> buildJsonObject {
                value.size?.let { put("size", it) }
                value.align?.let { put("align", it) }
                put("content", e.json.encodeToJsonElement(LineSerializer, value.content))
            }
        }
        e.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): VerseLine {
        val d = jsonDecoderOf(decoder)
        return when (val element = d.decodeJsonElement()) {
            is JsonObject -> VerseLine.Wrapped(
                size = element["size"]?.jsonPrimitive?.floatOrNull,
                align = element["align"]?.jsonPrimitive?.contentOrNull,
                content = d.json.decodeFromJsonElement(LineSerializer, element["content"] ?: error("VerseLine object missing 'content'")),
            )
            else -> VerseLine.Simple(d.json.decodeFromJsonElement(LineSerializer, element))
        }
    }
}

object BlockSerializer : KSerializer<Block> {
    override val descriptor: SerialDescriptor = buildSerialDescriptor("yuku.alkitab.songs.newdoc.Block", SerialKind.CONTEXTUAL)

    override fun serialize(encoder: Encoder, value: Block) {
        val e = jsonEncoderOf(encoder)
        val element: JsonElement = when (value) {
            is PBlock -> e.json.encodeToJsonElement(PBlock.serializer(), value)
            is RowBlock -> e.json.encodeToJsonElement(RowBlock.serializer(), value)
            is LyricBlock -> e.json.encodeToJsonElement(LyricBlock.serializer(), value)
            is ScriptureBlock -> e.json.encodeToJsonElement(ScriptureBlock.serializer(), value)
            is YoutubeBlock -> e.json.encodeToJsonElement(YoutubeBlock.serializer(), value)
            is GapBlock -> e.json.encodeToJsonElement(GapBlock.serializer(), value)
            is UnknownBlock -> value.raw
        }
        e.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): Block {
        val d = jsonDecoderOf(decoder)
        val obj = d.decodeJsonElement() as? JsonObject ?: error("Block must be a JSON object")
        val type = obj["type"]?.jsonPrimitive?.contentOrNull
        return when (type) {
            "p" -> d.json.decodeFromJsonElement(PBlock.serializer(), obj)
            "row" -> d.json.decodeFromJsonElement(RowBlock.serializer(), obj)
            "lyric" -> d.json.decodeFromJsonElement(LyricBlock.serializer(), obj)
            "scripture" -> d.json.decodeFromJsonElement(ScriptureBlock.serializer(), obj)
            "youtube" -> d.json.decodeFromJsonElement(YoutubeBlock.serializer(), obj)
            "gap" -> d.json.decodeFromJsonElement(GapBlock.serializer(), obj)
            else -> UnknownBlock(type ?: "", obj)
        }
    }
}

object PBlockSerializer : KSerializer<PBlock> {
    override val descriptor: SerialDescriptor = buildSerialDescriptor("yuku.alkitab.songs.newdoc.PBlock", SerialKind.CONTEXTUAL)

    override fun serialize(encoder: Encoder, value: PBlock) {
        val e = jsonEncoderOf(encoder)
        e.encodeJsonElement(
            buildJsonObject {
                put("type", "p")
                value.role?.let { put("role", it) }
                value.size?.let { put("size", it) }
                value.align?.let { put("align", it) }
                put("content", e.json.encodeToJsonElement(LineSerializer, value.content))
            },
        )
    }

    override fun deserialize(decoder: Decoder): PBlock {
        val d = jsonDecoderOf(decoder)
        val obj = d.decodeJsonElement().jsonObject
        return PBlock(
            role = obj["role"]?.jsonPrimitive?.contentOrNull,
            size = obj["size"]?.jsonPrimitive?.floatOrNull,
            align = obj["align"]?.jsonPrimitive?.contentOrNull,
            content = d.json.decodeFromJsonElement(LineSerializer, obj["content"] ?: error("p block missing 'content'")),
        )
    }
}

object RowBlockSerializer : KSerializer<RowBlock> {
    override val descriptor: SerialDescriptor = buildSerialDescriptor("yuku.alkitab.songs.newdoc.RowBlock", SerialKind.CONTEXTUAL)

    override fun serialize(encoder: Encoder, value: RowBlock) {
        val e = jsonEncoderOf(encoder)
        e.encodeJsonElement(
            buildJsonObject {
                put("type", "row")
                value.size?.let { put("size", it) }
                put("items", JsonArray(value.items.map { e.json.encodeToJsonElement(PBlock.serializer(), it) }))
            },
        )
    }

    override fun deserialize(decoder: Decoder): RowBlock {
        val d = jsonDecoderOf(decoder)
        val obj = d.decodeJsonElement().jsonObject
        return RowBlock(
            size = obj["size"]?.jsonPrimitive?.floatOrNull,
            items = (obj["items"]?.jsonArray ?: JsonArray(emptyList())).map { d.json.decodeFromJsonElement(PBlock.serializer(), it) },
        )
    }
}

object LyricBlockSerializer : KSerializer<LyricBlock> {
    override val descriptor: SerialDescriptor = buildSerialDescriptor("yuku.alkitab.songs.newdoc.LyricBlock", SerialKind.CONTEXTUAL)

    override fun serialize(encoder: Encoder, value: LyricBlock) {
        val e = jsonEncoderOf(encoder)
        e.encodeJsonElement(
            buildJsonObject {
                put("type", "lyric")
                value.role?.let { put("role", it) }
                value.size?.let { put("size", it) }
                value.caption?.let { put("caption", e.json.encodeToJsonElement(LineSerializer, it)) }
                put("verses", e.json.encodeToJsonElement(ListSerializer(Verse.serializer()), value.verses))
            },
        )
    }

    override fun deserialize(decoder: Decoder): LyricBlock {
        val d = jsonDecoderOf(decoder)
        val obj = d.decodeJsonElement().jsonObject
        return LyricBlock(
            role = obj["role"]?.jsonPrimitive?.contentOrNull,
            size = obj["size"]?.jsonPrimitive?.floatOrNull,
            caption = obj["caption"]?.let { d.json.decodeFromJsonElement(LineSerializer, it) },
            verses = obj["verses"]?.jsonArray?.map { d.json.decodeFromJsonElement(Verse.serializer(), it) } ?: emptyList(),
        )
    }
}

object ScriptureBlockSerializer : KSerializer<ScriptureBlock> {
    override val descriptor: SerialDescriptor = buildSerialDescriptor("yuku.alkitab.songs.newdoc.ScriptureBlock", SerialKind.CONTEXTUAL)

    override fun serialize(encoder: Encoder, value: ScriptureBlock) {
        val e = jsonEncoderOf(encoder)
        e.encodeJsonElement(
            buildJsonObject {
                put("type", "scripture")
                value.role?.let { put("role", it) }
                value.size?.let { put("size", it) }
                put("osis", value.osis)
            },
        )
    }

    override fun deserialize(decoder: Decoder): ScriptureBlock {
        val d = jsonDecoderOf(decoder)
        val obj = d.decodeJsonElement().jsonObject
        return ScriptureBlock(
            role = obj["role"]?.jsonPrimitive?.contentOrNull,
            size = obj["size"]?.jsonPrimitive?.floatOrNull,
            osis = obj["osis"]?.jsonPrimitive?.contentOrNull ?: error("scripture block missing 'osis'"),
        )
    }
}

object YoutubeBlockSerializer : KSerializer<YoutubeBlock> {
    override val descriptor: SerialDescriptor = buildSerialDescriptor("yuku.alkitab.songs.newdoc.YoutubeBlock", SerialKind.CONTEXTUAL)

    override fun serialize(encoder: Encoder, value: YoutubeBlock) {
        val e = jsonEncoderOf(encoder)
        e.encodeJsonElement(
            buildJsonObject {
                put("type", "youtube")
                value.role?.let { put("role", it) }
                value.size?.let { put("size", it) }
                put("videoId", value.videoId)
            },
        )
    }

    override fun deserialize(decoder: Decoder): YoutubeBlock {
        val d = jsonDecoderOf(decoder)
        val obj = d.decodeJsonElement().jsonObject
        return YoutubeBlock(
            role = obj["role"]?.jsonPrimitive?.contentOrNull,
            size = obj["size"]?.jsonPrimitive?.floatOrNull,
            videoId = obj["videoId"]?.jsonPrimitive?.contentOrNull ?: error("youtube block missing 'videoId'"),
        )
    }
}

object GapBlockSerializer : KSerializer<GapBlock> {
    override val descriptor: SerialDescriptor = buildSerialDescriptor("yuku.alkitab.songs.newdoc.GapBlock", SerialKind.CONTEXTUAL)

    override fun serialize(encoder: Encoder, value: GapBlock) {
        val e = jsonEncoderOf(encoder)
        e.encodeJsonElement(
            buildJsonObject {
                put("type", "gap")
                value.size?.let { put("size", it) }
            },
        )
    }

    override fun deserialize(decoder: Decoder): GapBlock {
        val d = jsonDecoderOf(decoder)
        val obj = d.decodeJsonElement().jsonObject
        return GapBlock(size = obj["size"]?.jsonPrimitive?.floatOrNull)
    }
}
