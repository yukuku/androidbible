package yuku.alkitab.datatransfer.process

import kotlinx.serialization.json.Json

object Serializer {
    val json = Json {
        classDiscriminator = "kind"
        encodeDefaults = false
        ignoreUnknownKeys = true
        prettyPrint = true
    }
}
