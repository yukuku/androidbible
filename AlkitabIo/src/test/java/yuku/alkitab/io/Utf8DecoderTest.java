package yuku.alkitab.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.Test;
import yuku.bintex.BintexReader;
import yuku.bintex.BintexWriter;

public class Utf8DecoderTest {
    @Test
    public void everyUnicodeScalarDecodesAndLowercasesWithoutExpansion() {
        for (int start = 0; start <= Character.MAX_CODE_POINT; start += 4096) {
            StringBuilder text = new StringBuilder();
            StringBuilder lowercase = new StringBuilder();
            for (int cp = start; cp < start + 4096 && cp <= Character.MAX_CODE_POINT; cp++) {
                if (cp >= 0xd800 && cp <= 0xdfff) continue;
                text.appendCodePoint(cp);
                lowercase.appendCodePoint(Character.toLowerCase(cp));
            }
            byte[] input = text.toString().getBytes(StandardCharsets.UTF_8);
            assertEquals(text.toString(), Utf8Decoder.toString(input));
            assertEquals(lowercase.toString(), Utf8Decoder.toStringLowerCase(input));
            assertEquals(text.length(), lowercase.length());
        }
    }

    @Test
    public void supplementarySequencesRespectSliceBoundaries() {
        byte[] input = "X𐐀Y".getBytes(StandardCharsets.UTF_8);
        assertEquals("𐐀", Utf8Decoder.toString(input, 1, 4));
        assertEquals("𐐨", Utf8Decoder.toStringLowerCase(input, 1, 4));
        for (int length = 1; length < 4; length++) {
            final int sliceLength = length;
            assertThrows(IllegalArgumentException.class, () -> Utf8Decoder.toString(input, 1, sliceLength));
            assertThrows(IllegalArgumentException.class, () -> Utf8Decoder.toStringLowerCase(input, 1, sliceLength));
        }
        assertEquals("", Utf8Decoder.toString(input, input.length, 0));
        assertEquals("", Utf8Decoder.toStringLowerCase(new byte[0]));
    }

    @Test
    public void invalidSequencesCannotEmitSurrogatesOrConsumeFollowingAscii() {
        int[][] malformed = {
            {0xc0, 0xaf}, {0xe0, 0x80, 0xaf}, {0xed, 0xa0, 0x80},
            {0xf0, 0x80, 0x80, 0xaf}, {0xf4, 0x90, 0x80, 0x80},
            {0xf5, 0x80, 0x80, 0x80}, {0xff},
        };
        for (int[] sequence : malformed) {
            byte[] input = new byte[sequence.length + 1];
            for (int i = 0; i < sequence.length; i++) input[i] = (byte) sequence[i];
            input[sequence.length] = 'A';
            assertEquals("\ufffd".repeat(sequence.length) + "A", Utf8Decoder.toString(input));
            assertEquals("\ufffd".repeat(sequence.length) + "a", Utf8Decoder.toStringLowerCase(input));
        }
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void slicesOutsideTheInputAreRejected() {
        Utf8Decoder.toString(new byte[4], 3, 2);
    }

    @Test
    public void lengthPrefixedVersesHandleEmojiCasingAndBufferGrowth() throws Exception {
        String text = "İ ΟΣ 𐐀 👩🏽‍💻 𠮷 CAFÉ";
        String lowercase = "i οσ 𐐨 👩🏽‍💻 𠮷 café";
        List<String> verses = Arrays.asList(text.repeat(1000), "", text);
        byte[] encoded = encodeVerses(verses);
        assertEquals(String.join("\n", verses) + "\n", decodeVerses(encoded, 3, false));
        assertEquals(lowercase.repeat(1000) + "\n\n" + lowercase + "\n", decodeVerses(encoded, 3, true));
        assertEquals("Z", Utf8Decoder.toString(new byte[]{'Z'}));
        assertEquals("", decodeVerses(new byte[0], 0, false));
    }

    @Test
    public void truncatedLengthPrefixedVerseCannotReadStaleBufferBytes() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BintexWriter writer = new BintexWriter(output);
        byte[] valid = "𐐀".getBytes(StandardCharsets.UTF_8);
        writer.writeVarUint(valid.length);
        output.write(valid);
        writer.writeVarUint(2);
        output.write(valid, 0, 2);
        writer.writeVarUint(1);
        output.write('A');
        assertThrows(IllegalArgumentException.class, () -> decodeVerses(output.toByteArray(), 3, false));
        assertThrows(IllegalArgumentException.class, () -> decodeVerses(output.toByteArray(), 3, true));
    }

    @Test
    public void concurrentReadersKeepTheirScratchBuffersIndependent() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Void>> tasks = Arrays.asList(
                decodeTask("ABC𐐀", "abc𐐨"), decodeTask("日本語👩🏽‍💻", "日本語👩🏽‍💻"),
                decodeTask("ÉΣ𠮷", "éσ𠮷"), decodeTask("XYZ🌿", "xyz🌿")
            );
            for (Future<Void> result : executor.invokeAll(tasks)) result.get();
        } finally {
            executor.shutdownNow();
        }
    }

    private static Callable<Void> decodeTask(String sample, String lowercase) {
        return () -> {
            String text = sample.repeat(2000);
            String expected = lowercase.repeat(2000);
            byte[] input = text.getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < 20; i++) {
                assertEquals(text, Utf8Decoder.toString(input));
                assertEquals(expected, Utf8Decoder.toStringLowerCase(input));
            }
            return null;
        };
    }

    private static byte[] encodeVerses(List<String> verses) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BintexWriter writer = new BintexWriter(output);
        for (String verse : verses) {
            byte[] bytes = verse.getBytes(StandardCharsets.UTF_8);
            writer.writeVarUint(bytes.length);
            output.write(bytes);
        }
        return output.toByteArray();
    }

    private static String decodeVerses(byte[] input, int count, boolean isLowercased) throws Exception {
        return Utf8Decoder.toStringFromVersesWithPrependedLengths(
            new BintexReader(new ByteArrayInputStream(input)), count, isLowercased);
    }
}
