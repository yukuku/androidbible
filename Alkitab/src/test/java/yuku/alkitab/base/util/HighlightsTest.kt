package yuku.alkitab.base.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [Highlights] — encoding/decoding of highlight state and the
 * `shouldRenderAsPartialForVerseText` guard that keeps stale partial-highlight
 * offsets from being applied after the underlying verse text changes.
 *
 * Failure paths in [Highlights.decode] call [AppLog.e], which has an Android /
 * Firebase dependency. Minimal test-scope shadows of `android.util.Log` and
 * `com.google.firebase.crashlytics.FirebaseCrashlytics` (under `src/test/java/…`)
 * make those paths runnable without pulling in Robolectric.
 */
class HighlightsTest {

    // region encode → decode round-trip (full-verse)

    @Test
    fun `encoding a full-verse highlight round-trips through decode and preserves the color`() {
        val encoded = Highlights.encode(0xff0000)
        val info = Highlights.decode(encoded)

        assertNotNull(info)
        assertEquals(0xff0000, info!!.colorRgb)
        assertNull(info.partial)
    }

    @Test
    fun `encoding a full-verse highlight with zero color round-trips correctly`() {
        val encoded = Highlights.encode(0x000000)
        val info = Highlights.decode(encoded)

        assertNotNull(info)
        assertEquals(0x000000, info!!.colorRgb)
        assertNull(info.partial)
    }

    @Test
    fun `a full-verse highlight is encoded as a JSON object so decode can dispatch off the leading brace`() {
        val encoded = Highlights.encode(0xabcdef)
        // decode() picks between legacy ('c' + 6 hex) and JSON format by checking for a leading '{'.
        assertTrue("encoded must start with '{' but was: $encoded", encoded.startsWith("{"))
    }

    // endregion

    // region encode → decode round-trip (partial)

    @Test
    fun `encoding a partial highlight round-trips color and partial offsets through decode`() {
        val color = 0x00ff00
        val hash = 123456789
        val start = 3
        val end = 15

        val encoded = Highlights.encode(color, hash, start, end)
        val info = Highlights.decode(encoded)

        assertNotNull(info)
        assertEquals(color, info!!.colorRgb)
        assertNotNull(info.partial)
        assertEquals(hash, info.partial!!.hashCode)
        assertEquals(start, info.partial!!.startOffset)
        assertEquals(end, info.partial!!.endOffset)
    }

    @Test
    fun `a partial highlight with all-zero fields still round-trips (partial object is not collapsed)`() {
        val encoded = Highlights.encode(0x112233, 0, 0, 0)
        val info = Highlights.decode(encoded)

        assertNotNull(info)
        assertEquals(0x112233, info!!.colorRgb)
        assertNotNull(info.partial)
        assertEquals(0, info.partial!!.hashCode)
        assertEquals(0, info.partial!!.startOffset)
        assertEquals(0, info.partial!!.endOffset)
    }

    @Test
    fun `a partial highlight preserves a negative hash code (String hashCode frequently overflows)`() {
        val encoded = Highlights.encode(0x654321, Int.MIN_VALUE, 1, 5)
        val info = Highlights.decode(encoded)

        assertNotNull(info)
        assertNotNull(info!!.partial)
        assertEquals(Int.MIN_VALUE, info.partial!!.hashCode)
    }

    // endregion

    // region ThreadLocal Info reuse

    @Test
    fun `encoding a full-verse highlight after a partial one does not leak the previous partial state`() {
        // encode() reuses a ThreadLocal Info; make sure the partial field is nulled on the full-verse path.
        Highlights.encode(0xaaaaaa, 999, 1, 2)

        val encoded = Highlights.encode(0xbbbbbb)
        val info = Highlights.decode(encoded)

        assertNotNull(info)
        assertEquals(0xbbbbbb, info!!.colorRgb)
        assertNull("partial must be cleared when encoding a full-verse highlight", info.partial)
    }

    // endregion

    // region decode: legacy 'c' + 6 hex format

    @Test
    fun `decode accepts the legacy c-prefixed 6-hex-digit format and returns a full-verse highlight`() {
        val info = Highlights.decode("c123456")

        assertNotNull(info)
        assertEquals(0x123456, info!!.colorRgb)
        assertNull(info.partial)
    }

    @Test
    fun `decode accepts lowercase hex digits in the legacy format`() {
        val info = Highlights.decode("cabcdef")

        assertNotNull(info)
        assertEquals(0xabcdef, info!!.colorRgb)
    }

    @Test
    fun `decode accepts uppercase hex digits in the legacy format`() {
        val info = Highlights.decode("cABCDEF")

        assertNotNull(info)
        assertEquals(0xABCDEF, info!!.colorRgb)
    }

    @Test
    fun `decode parses all-zero color in the legacy format`() {
        val info = Highlights.decode("c000000")

        assertNotNull(info)
        assertEquals(0, info!!.colorRgb)
    }

    @Test
    fun `decode tolerates trailing characters after the legacy 7-char header (legacy forward-compat)`() {
        // Only positions 1..7 are parsed; anything after is ignored. Intentional forward-compat.
        val info = Highlights.decode("c123456extra")

        assertNotNull(info)
        assertEquals(0x123456, info!!.colorRgb)
    }

    @Test
    fun `decode returns null when the legacy format contains non-hex digits`() {
        assertNull(Highlights.decode("cZZZZZZ"))
    }

    // endregion

    // region decode: null / empty / malformed

    @Test
    fun `decode returns null for a null input`() {
        assertNull(Highlights.decode(null))
    }

    @Test
    fun `decode returns null for an empty string`() {
        assertNull(Highlights.decode(""))
    }

    @Test
    fun `decode returns null when the input is too short for legacy format and not JSON`() {
        assertNull(Highlights.decode("abc"))
        // 6 chars starting with 'c' is one short of the legacy length (>= 7).
        assertNull(Highlights.decode("c12345"))
    }

    @Test
    fun `decode returns null for arbitrary non-highlight text`() {
        assertNull(Highlights.decode("not a highlight string"))
    }

    @Test
    fun `decode returns null for malformed JSON`() {
        assertNull(Highlights.decode("{not valid json"))
    }

    @Test
    fun `decode returns a default Info for an empty JSON object`() {
        val info = Highlights.decode("{}")

        assertNotNull(info)
        assertEquals(0, info!!.colorRgb)
        assertNull(info.partial)
    }

    // endregion

    // region alphaMix

    @Test
    fun `alphaMix sets the alpha channel to 0xA0 and preserves the input RGB bits`() {
        assertEquals(0xa0000000.toInt(), Highlights.alphaMix(0x000000))
        assertEquals(0xa0ff0000.toInt(), Highlights.alphaMix(0xff0000))
        assertEquals(0xa000ff00.toInt(), Highlights.alphaMix(0x00ff00))
        assertEquals(0xa00000ff.toInt(), Highlights.alphaMix(0x0000ff))
        assertEquals(0xa0123456.toInt(), Highlights.alphaMix(0x123456))
        assertEquals(0xa0ffffff.toInt(), Highlights.alphaMix(0xffffff))
    }

    @Test
    fun `alphaMix ignores any alpha bits present on the input and always produces an 0xA0 alpha`() {
        // If callers accidentally pass an ARGB value, the high byte must not bleed through —
        // the output alpha must always be 0xA0.
        assertEquals(0xa0123456.toInt(), Highlights.alphaMix(0xff123456.toInt()))
        assertEquals(0xa0000000.toInt(), Highlights.alphaMix(0xff000000.toInt()))
        assertEquals(0xa0ffffff.toInt(), Highlights.alphaMix(0xffffffff.toInt()))
    }

    // endregion

    // region hashCode

    @Test
    fun `Highlights hashCode delegates to String hashCode`() {
        assertEquals("abc".hashCode(), Highlights.hashCode("abc"))
        assertEquals("".hashCode(), Highlights.hashCode(""))
        val verse = "In the beginning God created the heavens and the earth."
        assertEquals(verse.hashCode(), Highlights.hashCode(verse))
    }

    @Test
    fun `Highlights hashCode handles Unicode verse text`() {
        val s = "Ku bersyukur pada-Mu \u2014 \u8d5e\u7f8e\u8bd7"
        assertEquals(s.hashCode(), Highlights.hashCode(s))
    }

    // endregion

    // region Info.shouldRenderAsPartialForVerseText

    @Test
    fun `shouldRenderAsPartialForVerseText returns false when the Info has no partial data`() {
        val info = Highlights.Info().apply {
            colorRgb = 0xff0000
            partial = null
        }

        assertFalse(info.shouldRenderAsPartialForVerseText("some verse text"))
    }

    @Test
    fun `shouldRenderAsPartialForVerseText returns true when the hash matches and offsets are within text length`() {
        val verseText = "Hello world, this is a verse."

        val info = Highlights.Info().apply {
            colorRgb = 0xff0000
            partial = Highlights.Info.Partial().apply {
                hashCode = Highlights.hashCode(verseText)
                startOffset = 0
                endOffset = 5
            }
        }

        assertTrue(info.shouldRenderAsPartialForVerseText(verseText))
    }

    @Test
    fun `shouldRenderAsPartialForVerseText returns false when the stored hash does not match the verse text`() {
        val verseText = "Hello world"

        val info = Highlights.Info().apply {
            partial = Highlights.Info.Partial().apply {
                hashCode = Highlights.hashCode("different text")
                startOffset = 0
                endOffset = 5
            }
        }

        assertFalse(info.shouldRenderAsPartialForVerseText(verseText))
    }

    @Test
    fun `shouldRenderAsPartialForVerseText allows offsets that sit exactly at the end of the verse text`() {
        // The production check is `<=` verseText.length, so offsets equal to the text length are allowed.
        val verseText = "abcde"

        val info = Highlights.Info().apply {
            partial = Highlights.Info.Partial().apply {
                hashCode = Highlights.hashCode(verseText)
                startOffset = 5
                endOffset = 5
            }
        }

        assertTrue(info.shouldRenderAsPartialForVerseText(verseText))
    }

    @Test
    fun `shouldRenderAsPartialForVerseText returns false when startOffset is past the end of the verse text`() {
        val verseText = "abcde"

        val info = Highlights.Info().apply {
            partial = Highlights.Info.Partial().apply {
                hashCode = Highlights.hashCode(verseText)
                startOffset = 6
                endOffset = 7
            }
        }

        assertFalse(info.shouldRenderAsPartialForVerseText(verseText))
    }

    @Test
    fun `shouldRenderAsPartialForVerseText returns false when endOffset is past the end of the verse text`() {
        val verseText = "abcde"

        val info = Highlights.Info().apply {
            partial = Highlights.Info.Partial().apply {
                hashCode = Highlights.hashCode(verseText)
                startOffset = 0
                endOffset = 6
            }
        }

        assertFalse(info.shouldRenderAsPartialForVerseText(verseText))
    }

    @Test
    fun `a partial highlight round-tripped through encode and decode can still be rendered for its original verse`() {
        val verseText = "In the beginning God created the heavens and the earth."
        val hash = Highlights.hashCode(verseText)

        val encoded = Highlights.encode(0xff9900, hash, 0, 3)
        val decoded = Highlights.decode(encoded)

        assertNotNull(decoded)
        assertTrue(decoded!!.shouldRenderAsPartialForVerseText(verseText))
    }

    @Test
    fun `shouldRenderAsPartialForVerseText returns false when the version text has changed since the highlight was saved`() {
        // Simulates a Bible-version update that changes the verse text underneath a saved partial highlight.
        val original = "In the beginning God created the heavens and the earth."
        val changed = "In the beginning God created heaven and earth."

        val info = Highlights.Info().apply {
            partial = Highlights.Info.Partial().apply {
                hashCode = Highlights.hashCode(original)
                startOffset = 0
                endOffset = 3
            }
        }

        assertFalse(info.shouldRenderAsPartialForVerseText(changed))
    }

    // endregion
}
