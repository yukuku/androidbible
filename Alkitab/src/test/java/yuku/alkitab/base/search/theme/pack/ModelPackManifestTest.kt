package yuku.alkitab.base.search.theme.pack

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ModelPackManifestTest {
    @Test
    fun `manifest pins immutable official artifacts`() {
        val manifest = ModelPackManifest.load(ApplicationProvider.getApplicationContext())
        assertEquals("835ad14087e140460703cf0fae09f97d469d65c2", manifest.revision)
        assertEquals(98_247_878L, manifest.model.length)
        assertEquals("a6022dd8220ea6f6595562a1328ee216f4a94faa55362f2f4747c80f1e78772e", manifest.model.sha256)
        assertEquals(25_301_672L, manifest.tokenizer.length)
        assertEquals("4f2842d568e2724370aec203652a42ac783c7937f8347a1a2cc7506d71f1582f", manifest.tokenizer.sha256)
        assertTrue(manifest.model.url.startsWith("https://huggingface.co/ibm-granite/"))
    }
}
