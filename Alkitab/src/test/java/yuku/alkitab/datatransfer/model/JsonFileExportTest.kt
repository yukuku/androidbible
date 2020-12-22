package yuku.alkitab.datatransfer.model

import java.io.File
import kotlinx.serialization.decodeFromString
import org.junit.Test
import yuku.alkitab.datatransfer.process.Serializer

class JsonFileExportTest {
    @Test
    fun testParse() {
        val json = File("/tmp/download_all.json").readText()
        val root = Serializer.importJson.decodeFromString<Root>(json)
        println(root)
    }
}
