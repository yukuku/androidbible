package yuku.alkitab.datatransfer.model

import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertNotNull
import org.junit.Test
import yuku.alkitab.datatransfer.process.Serializer

class JsonFileExportTest {
    @Test
    fun testParse() {
        val resourceInput = this::class.java.getResourceAsStream("/fixtures/download_all.json")
        assertNotNull(resourceInput)

        if (resourceInput != null) {
            val json = resourceInput.reader().use { it.readText() }
            val root = Serializer.importJson.decodeFromString(Root.serializer(), json)
            println(root)
        }
    }
}
