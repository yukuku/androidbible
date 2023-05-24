package yuku.alkitab.datatransfer.process

import kotlinx.serialization.json.Json

object Serializer {
    val exportJson = Json {
        classDiscriminator = "kind"
        encodeDefaults = false
    }

    val importJson = Json {
        classDiscriminator = "kind"
        ignoreUnknownKeys = true
    }
}
