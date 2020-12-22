package yuku.alkitab.datatransfer.process

import kotlinx.serialization.json.Json

object Serializer {
    val exportJson = Json {
        classDiscriminator = "kind"
        encodeDefaults = false
        prettyPrint = true
        @Suppress("EXPERIMENTAL_API_USAGE")
        prettyPrintIndent = "  "
    }

    val importJson = Json {
        classDiscriminator = "kind"
        ignoreUnknownKeys = true
    }
}
