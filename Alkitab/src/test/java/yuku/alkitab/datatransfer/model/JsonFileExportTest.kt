package yuku.alkitab.datatransfer.model

import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Test

val format = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    classDiscriminator = "kind"
}

class JsonFileExportTest {
    @Test
    fun testParse() {
        val json = File("/tmp/download_all.json").readText()
        val root = format.decodeFromString<Root>(json)
        println(root)
    }
}
