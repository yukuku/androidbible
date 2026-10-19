package name.fraser.neil.plaintext

import java.util.LinkedList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiffMatchPatchEncodingTest {
    private val engine = diff_match_patch()
    private val text = "+猫 café\t100%\n"
    private val encodedText = "+%E7%8C%AB caf%C3%A9%09100%25%0A"

    @Test
    fun `delta encoding preserves Unicode plus signs spaces and escaped characters`() {
        val diffs = LinkedList<diff_match_patch.Diff>().apply {
            add(diff_match_patch.Diff(diff_match_patch.Operation.INSERT, text))
        }

        assertEquals("+$encodedText", engine.diff_toDelta(diffs))
        assertEquals(diffs, engine.diff_fromDelta("", "+$encodedText"))
    }

    @Test
    fun `patch encoding preserves the wire format and applies after decoding`() {
        val patches = engine.patch_make("", text)
        val serialized = "@@ -0,0 +1,${text.length} @@\n+$encodedText\n"

        assertEquals(serialized, engine.patch_toText(patches))
        val decoded = LinkedList(engine.patch_fromText(serialized))
        assertEquals(serialized, engine.patch_toText(decoded))
        val applied = engine.patch_apply(decoded, "")
        assertEquals(text, applied[0])
        assertTrue((applied[1] as BooleanArray).all { it })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `delta decoding rejects malformed percent escapes`() {
        engine.diff_fromDelta("", "+%ZZ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `patch decoding rejects malformed percent escapes`() {
        engine.patch_fromText("@@ -0,0 +1 @@\n+%ZZ\n")
    }
}
