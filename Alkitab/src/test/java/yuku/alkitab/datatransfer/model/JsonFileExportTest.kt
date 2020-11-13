package yuku.alkitab.datatransfer.model

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import java.io.File
import org.junit.Test

class JsonFileExportTest {
    @Test
    fun testParse() {
        val moshi = Moshi.Builder().apply {
            PolymorphicJsonAdapterFactory.of(MabelContent::class.java, )
        }.build()
        val root = moshi.adapter(Root::class.java).fromJson(File("/tmp/download_all.json").readText())
        println(root)
    }
}
