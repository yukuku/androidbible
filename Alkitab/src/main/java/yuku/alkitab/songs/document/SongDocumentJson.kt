package yuku.alkitab.songs.document

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Custom serializer for [Line] that reads/writes either a plain string
 * or a JSON array of [Span] objects.
 */
object LineSerializer : KSerializer<Line> {
    private val spanListSerializer = ListSerializer(Span.serializer())

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Line") {
        element("styled", spanListSerializer.descriptor)
    }

    override fun serialize(encoder: Encoder, value: Line) {
        if (value.size == 1 && value[0].style.isNullOrEmpty()) {
            // Plain text: serialize as single string
            encoder.encodeString(value[0].text)
        } else {
            // Styled text: serialize as array of spans
            encoder.encodeSerializableValue(spanListSerializer, value)
        }
    }

    override fun deserialize(decoder: Decoder): Line {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("LineSerializer requires JsonDecoder")

        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> {
                if (element.isString) {
                    listOf(Span(element.content))
                } else {
                    throw SerializationException("Expected string or array for Line, got primitive: $element")
                }
            }
            is JsonArray -> {
                jsonDecoder.json.decodeFromJsonElement(spanListSerializer, element)
            }
            else -> throw SerializationException("Expected string or array for Line, got: $element")
        }
    }
}

/**
 * Custom serializer for [VerseLine] that reads/writes either a plain [Line]
 * (string or Span[]) or an object `{ "size": 1.3, "align": "center", "content": Line }`.
 */
object VerseLineSerializer : KSerializer<VerseLine> {
    private val lineSerializer = LineSerializer

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("VerseLine") {
        element("content", lineSerializer.descriptor)
    }

    override fun serialize(encoder: Encoder, value: VerseLine) {
        if (value.size == null && value.align == null) {
            // Just a plain line
            encoder.encodeSerializableValue(lineSerializer, value.content)
        } else {
            // Object with size, align, content
            val jsonEncoder = encoder as? JsonEncoder
                ?: throw SerializationException("VerseLineSerializer requires JsonEncoder for object encoding")

            val contentElement = if (value.content.size == 1 && value.content[0].style.isNullOrEmpty()) {
                JsonPrimitive(value.content[0].text)
            } else {
                jsonEncoder.json.encodeToJsonElement(ListSerializer(Span.serializer()), value.content)
            }

            val jsonObject = buildJsonObject {
                value.size?.let { put("size", JsonPrimitive(it)) }
                value.align?.let { put("align", JsonPrimitive(it)) }
                put("content", contentElement)
            }
            jsonEncoder.encodeJsonElement(jsonObject)
        }
    }

    override fun deserialize(decoder: Decoder): VerseLine {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("VerseLineSerializer requires JsonDecoder")

        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> {
                if (element.isString) {
                    VerseLine(content = listOf(Span(element.content)))
                } else {
                    throw SerializationException("Expected string, array, or object for VerseLine, got primitive: $element")
                }
            }
            is JsonArray -> {
                VerseLine(content = jsonDecoder.json.decodeFromJsonElement(lineSerializer, element))
            }
            is JsonObject -> {
                val size = element["size"]?.jsonPrimitive?.content?.toFloatOrNull()
                val align = element["align"]?.jsonPrimitive?.content
                val content = element["content"]?.let {
                    jsonDecoder.json.decodeFromJsonElement(lineSerializer, it)
                } ?: throw SerializationException("VerseLine object missing 'content' field")
                VerseLine(size = size, align = align, content = content)
            }
        }
    }
}

/**
 * Custom serializer for [Block] that dispatches to the appropriate subclass
 * serializer based on the `type` field. Unknown block types are deserialized
 * as [UnknownBlock] for forward compatibility.
 */
object BlockSerializer : KSerializer<Block> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Block") {}

    override fun serialize(encoder: Encoder, value: Block) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw SerializationException("BlockSerializer requires JsonEncoder")

        val jsonObject = when (value) {
            is PBlock -> {
                val base = jsonEncoder.json.encodeToJsonElement(PBlock.serializer(), value).jsonObject
                JsonObject(base + ("type" to JsonPrimitive("p")))
            }
            is RowBlock -> {
                val base = jsonEncoder.json.encodeToJsonElement(RowBlock.serializer(), value).jsonObject
                JsonObject(base + ("type" to JsonPrimitive("row")))
            }
            is LyricBlock -> {
                val base = jsonEncoder.json.encodeToJsonElement(LyricBlock.serializer(), value).jsonObject
                JsonObject(base + ("type" to JsonPrimitive("lyric")))
            }
            is ScriptureBlock -> {
                val base = jsonEncoder.json.encodeToJsonElement(ScriptureBlock.serializer(), value).jsonObject
                JsonObject(base + ("type" to JsonPrimitive("scripture")))
            }
            is YoutubeBlock -> {
                val base = jsonEncoder.json.encodeToJsonElement(YoutubeBlock.serializer(), value).jsonObject
                JsonObject(base + ("type" to JsonPrimitive("youtube")))
            }
            is UnknownBlock -> {
                jsonEncoder.json.encodeToJsonElement(UnknownBlock.serializer(), value).jsonObject
            }
        }
        jsonEncoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): Block {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("BlockSerializer requires JsonDecoder")

        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject
        val type = jsonObject["type"]?.jsonPrimitive?.content
            ?: throw SerializationException("Block missing 'type' field")

        return when (type) {
            "p" -> jsonDecoder.json.decodeFromJsonElement(PBlock.serializer(), jsonObject)
            "row" -> jsonDecoder.json.decodeFromJsonElement(RowBlock.serializer(), jsonObject)
            "lyric" -> jsonDecoder.json.decodeFromJsonElement(LyricBlock.serializer(), jsonObject)
            "scripture" -> jsonDecoder.json.decodeFromJsonElement(ScriptureBlock.serializer(), jsonObject)
            "youtube" -> jsonDecoder.json.decodeFromJsonElement(YoutubeBlock.serializer(), jsonObject)
            else -> UnknownBlock(type, jsonObject)
        }
    }
}

/**
 * Custom serializer for [UnknownBlock] that preserves the raw JSON object.
 */
object UnknownBlockSerializer : KSerializer<UnknownBlock> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("UnknownBlock") {}

    override fun serialize(encoder: Encoder, value: UnknownBlock) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw SerializationException("UnknownBlockSerializer requires JsonEncoder")
        jsonEncoder.encodeJsonElement(value.rawJson)
    }

    override fun deserialize(decoder: Decoder): UnknownBlock {
        throw SerializationException("UnknownBlock should not be deserialized directly; use BlockSerializer")
    }
}

/**
 * Custom serializer for `List<Block>` that preserves all block types,
 * including [UnknownBlock], for forward compatibility.
 */
object BlockListSerializer : KSerializer<List<Block>> {
    private val delegate = ListSerializer(Block.serializer())

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: List<Block>) {
        delegate.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): List<Block> {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("BlockListSerializer requires JsonDecoder")

        val element = jsonDecoder.decodeJsonElement()
        val array = when (element) {
            is JsonArray -> element
            else -> throw SerializationException("Expected JsonArray for List<Block>")
        }
        val json = jsonDecoder.json

        return array.map { item ->
            json.decodeFromJsonElement(Block.serializer(), item)
        }
    }
}

/**
 * Download wrapper for a song book containing multiple [SongDocument]s.
 *
 * @param v Schema version. Defaults to 1.
 * @param book Metadata about the song book.
 * @param songs Ordered list of songs in this book.
 */
@Serializable
data class SongBookDocument(
    val v: Int = 1,
    val book: SongBookMeta,
    val songs: List<SongDocument>
)

/**
 * Metadata for a song book.
 *
 * @param name Machine-readable book identifier (e.g. "kidung").
 * @param title Human-readable book title (e.g. "Kidung Jemaat").
 * @param copyright Optional copyright notice.
 */
@Serializable
data class SongBookMeta(
    val name: String,
    val title: String? = null,
    val copyright: String? = null
)

/**
 * Public API for JSON serialization and deserialization of [SongDocument]
 * and [SongBookDocument] using kotlinx.serialization.
 */
object SongDocumentJson {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        prettyPrint = false
    }

    fun encodeToString(doc: SongDocument): String = json.encodeToString(SongDocument.serializer(), doc)
    fun decodeFromString(str: String): SongDocument = json.decodeFromString(SongDocument.serializer(), str)
    fun encodeToByteArray(doc: SongDocument): ByteArray = encodeToString(doc).toByteArray(Charsets.UTF_8)

    fun encodeToString(doc: SongBookDocument): String = json.encodeToString(SongBookDocument.serializer(), doc)
    fun decodeFromStringSongBook(str: String): SongBookDocument = json.decodeFromString(SongBookDocument.serializer(), str)
}
