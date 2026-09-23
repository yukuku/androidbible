package yuku.alkitab.base.storage

import android.app.Application
import java.io.File
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import yuku.alkitab.yes1.Yes1Reader
import yuku.alkitab.yes2.Yes2Reader

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class YesReaderFactoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val magic = intArrayOf(0x98, 0x58, 0x0d, 0x0a, 0x00, 0x5d, 0xe0)

    private fun yesFile(vararg header: Int): String {
        val f = File(tmp.root, "test-${System.nanoTime()}.yes")
        f.writeBytes(ByteArray(header.size) { header[it].toByte() } + ByteArray(32))
        return f.absolutePath
    }

    @Test
    fun `a version 1 header opens a Yes1Reader`() {
        assertTrue(YesReaderFactory.createYesReader(yesFile(*magic, 0x01)) is Yes1Reader)
    }

    @Test
    fun `a version 2 header opens a Yes2Reader`() {
        assertTrue(YesReaderFactory.createYesReader(yesFile(*magic, 0x02)) is Yes2Reader)
    }

    @Test
    fun `an unsupported version returns null`() {
        assertNull(YesReaderFactory.createYesReader(yesFile(*magic, 0x03)))
    }

    @Test
    fun `a wrong magic byte returns null`() {
        val header = magic.copyOf()
        header[6] = 0xe1
        assertNull(YesReaderFactory.createYesReader(yesFile(*header, 0x02)))
    }

    @Test
    fun `a missing file returns null`() {
        assertNull(YesReaderFactory.createYesReader(File(tmp.root, "missing.yes").absolutePath))
    }

    @Test
    fun `a file shorter than the header returns null`() {
        val f = File(tmp.root, "short.yes")
        f.writeBytes(ByteArray(magic.size) { magic[it].toByte() })
        assertNull(YesReaderFactory.createYesReader(f.absolutePath))
    }
}
