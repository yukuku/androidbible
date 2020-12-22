package yuku.alkitab.datatransfer.process

import kotlinx.serialization.json.Json

object Serializer {
    val exportJson = Json {
        encodeDefaults = false
        prettyPrint = true
    }

    val importJson = Json {
        classDiscriminator = "kind"
        ignoreUnknownKeys = true
    }
}
