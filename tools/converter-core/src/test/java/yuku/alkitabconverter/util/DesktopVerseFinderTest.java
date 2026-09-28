package yuku.alkitabconverter.util;

import static org.junit.Assert.*;
import org.junit.Test;

public class DesktopVerseFinderTest {

    @Test
    public void findInText() {
        test("don't have");
        test("The first verse is Gen 1:1", "Gen 1:1");
        test("The first verse is Gen 1:1 and the second is Gen  1 : 2", "Gen 1:1", "Gen  1 : 2");
        test("Ranges 1 John 2 : 3- 4 and 3 John 4 -7", "1 John 2 : 3- 4", "3 John 4 -7");
        test("verses Dan 111:222 dan 333", "Dan 111:222 dan 333");
        // across chapters
        test("verses Gen 1:2-3:4", "Gen 1:2-3:4");
        // across chapters with endash and emdash
        test("verses Gen 1:2—3:4 1Pe 5:6–7", "Gen 1:2—3:4", "1Pe 5:6–7");
    }

    private void test(String input, String... detectedStrings) {
        final int[] lastEnd = {0};
        final int[] checkPos = {0};
        final boolean[] onNoMoreDetectedCalled = {false};

        DesktopVerseFinder.findInText(input, new DesktopVerseFinder.DetectorListener() {
            @Override
            public boolean onVerseDetected(final int start, final int end, final String verse) {
                assertTrue("start must be greater or equal last end", start >= lastEnd[0]);
                lastEnd[0] = end;
                assertEquals(input.substring(start, end), detectedStrings[checkPos[0]]);
                checkPos[0]++;

                return true;
            }

            @Override
            public void onNoMoreDetected() {
                if (checkPos[0] != detectedStrings.length) {
                    fail("onMoreDetected was called too early.");
                }
                onNoMoreDetectedCalled[0] = true;
            }
        });

        assertTrue("onNoMoreDetected was not called", onNoMoreDetectedCalled[0]);
    }
}
