package yuku.alkitab.test;

import android.text.Spannable;
import android.text.SpannableStringBuilder;
import androidx.annotation.NonNull;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import junit.framework.TestCase;
import yuku.alkitab.base.widget.FormattedTextRenderer;

public class FormattedTextRendererTest extends TestCase {
    SpannableStringBuilder mockSb() {
        return new SpannableStringBuilder() {
            String chars = "";
            List<Object[]> spans = new ArrayList<>();

            @NonNull
            @Override
            public SpannableStringBuilder append(final CharSequence text) {
                chars += text;
                return this;
            }

            @NonNull
            @Override
            public SpannableStringBuilder append(final CharSequence text, final int start, final int end) {
                chars += text.subSequence(start, end);
                return this;
            }

            @NonNull
            @Override
            public SpannableStringBuilder append(final char text) {
                chars += text;
                return this;
            }

            @NonNull
            @Override
            public String toString() {
                return chars;
            }

            @Override
            public int length() {
                return chars.length();
            }

            @Override
            public void setSpan(final Object what, final int start, final int end, final int flags) {
                spans.add(new Object[]{what, start, end, flags});
            }

            @Override
            public <T> T[] getSpans(final int queryStart, final int queryEnd, final Class<T> kind) {
                final List<T> list = new ArrayList<>();
                for (final Object[] span : spans) {
                    if (kind.isInstance(span[0])) {
                        //noinspection unchecked
                        list.add((T) span[0]);
                    }
                }

                @SuppressWarnings("unchecked") final T[] res = (T[]) Array.newInstance(kind, list.size());
                for (int i = 0; i < res.length; i++) {
                    res[i] = list.get(i);
                }
                return res;
            }

            @Override
            public int getSpanStart(final Object what) {
                for (final Object[] span : spans) {
                    if (span[0].equals(what)) {
                        return (int) span[1];
                    }
                }
                return -1;
            }

            @Override
            public int getSpanEnd(final Object what) {
                for (final Object[] span : spans) {
                    if (span[0].equals(what)) {
                        return (int) span[2];
                    }
                }
                return -1;
            }
        };
    }

    @SuppressWarnings("SameParameterValue")
    SpannableStringBuilder mockSb(String prefix) {
        final SpannableStringBuilder res = mockSb();
        res.append(prefix);
        return res;
    }

    public void testPericopeRenderer() {
        // test robustness
        assertEquals("123", FormattedTextRenderer.render("123", false, mockSb()).toString());
        assertEquals("23", FormattedTextRenderer.render("@123", false, mockSb()).toString());
        assertEquals("123", FormattedTextRenderer.render("@@123", false, mockSb()).toString());

        // test italic
        assertEquals("ab", FormattedTextRenderer.render("@@@9ab@7", false, mockSb()).toString());
        assertEquals("abcd", FormattedTextRenderer.render("@@@9ab@7cd", false, mockSb()).toString());
        assertEquals("abcd", FormattedTextRenderer.render("@@ab@9cd@7", false, mockSb()).toString());

        // required vs optional formatting header
        assertEquals("ab", FormattedTextRenderer.render("@9ab@7", false, mockSb()).toString());
        assertEquals("@9ab@7", FormattedTextRenderer.render("@9ab@7", true, mockSb()).toString());
        assertEquals("ab", FormattedTextRenderer.render("@@@9ab@7", true, mockSb()).toString());

        // appending test
        final SpannableStringBuilder prefix = mockSb("prefix");
        assertEquals("prefixab", FormattedTextRenderer.render("@9ab@7", false, prefix).toString());
        assertEquals("prefixab@9ab@7", FormattedTextRenderer.render("@9ab@7", true, prefix).toString());
        assertEquals("prefixab@9ab@7ab", FormattedTextRenderer.render("@@@9ab@7", true, prefix).toString());

        final SpannableStringBuilder text = FormattedTextRenderer.render("@@ab@9cd@7ef@9gh@7ij", false, mockSb());
        final Object[] spans = text.getSpans(0, text.length(), Object.class);
        assertEquals(2, spans.length);
        assertEquals(2, text.getSpanStart(spans[0]));
        assertEquals(4, text.getSpanEnd(spans[0]));
        assertEquals(6, text.getSpanStart(spans[1]));
        assertEquals(8, text.getSpanEnd(spans[1]));

        assertTrue(FormattedTextRenderer.render("@@rr@@ab@9cd@9@@9@9@@&@&7&77@@7@7@7", false, mockSb()).toString().startsWith("rr@@abcd"));
    }

    static class AssertingTagListener implements FormattedTextRenderer.TagListener {
        private final String[] expectedCalls;
        private int current;

        public AssertingTagListener(final String... expectedCalls) {
            this.expectedCalls = expectedCalls;
        }

        @Override
        public void onTag(@NonNull String tag, @NonNull Spannable buffer, int start, int end) {
            final String call = tag + ":" + start + ":" + end + ":" + buffer;
            final String exp = expectedCalls[current];
            if (!exp.equals(call)) {
                fail("actual call index " + current + ": " + call + " not equal expected " + exp);
            }
            current++;
        }

        public void end() {
            if (current != expectedCalls.length) {
                fail("was only called " + current + " times, expected " + expectedCalls.length);
            }
        }
    }


    public void testRecognizeSpecialTags() {
        // empty listener
        assertEquals("abc", FormattedTextRenderer.render("@<tag@>abc@/", false, mockSb()).toString());
        assertEquals("abc", FormattedTextRenderer.render("a@<tag@>b@/c", false, mockSb()).toString());
        assertEquals("prefixabcdef", FormattedTextRenderer.render("a@<tag@>b@/cdef", false, mockSb("prefix")).toString());
        assertEquals("prefixabcdef", FormattedTextRenderer.render("@9a@<tag@>b@/cdef@7", false, mockSb("prefix")).toString());
        assertEquals("prefixabcdef", FormattedTextRenderer.render("a@<tag@>b@9cd@7e@/f", false, mockSb("prefix")).toString());

        // tags must be called correctly
        {
            final AssertingTagListener listener = new AssertingTagListener("x:3:8:hi_there");
            FormattedTextRenderer.render("hi_@<x@>there@/", false, mockSb(), listener);
            listener.end();
        }
        // multiple tags
        {
            final AssertingTagListener listener = new AssertingTagListener("x:0:5:there", "y:9:14:there_hi_hello");
            FormattedTextRenderer.render("@<x@>there@/_hi_@<y@>hello@/_cool", false, mockSb(), listener);
            listener.end();
        }
        // empty tags
        {
            final AssertingTagListener listener = new AssertingTagListener(":0:0:", ":0:5:hello");
            FormattedTextRenderer.render("@<@>@/@<@>hello@/", false, mockSb(), listener);
            listener.end();
        }
        // nested tags
        {
            final AssertingTagListener listener = new AssertingTagListener("y:11:15:readyupuppypeak", "x:7:20:readyupuppypeakdowny", "w:5:20:readyupuppypeakdowny");
            FormattedTextRenderer.render("ready@<w@>up@<x@>uppy@<y@>peak@/downy@/@/", false, mockSb(), listener);
            listener.end();
        }

        // no end of tag
        {
            final AssertingTagListener listener = new AssertingTagListener();
            assertEquals("readywbzzzz", FormattedTextRenderer.render("ready@<wbzzzz", false, mockSb(), listener).toString());
            listener.end();
        }
        // no closing tag
        {
            final AssertingTagListener listener = new AssertingTagListener();
            assertEquals("readybzzzz", FormattedTextRenderer.render("ready@<w@>bzzzz", false, mockSb(), listener).toString());
            listener.end();
        }
        // extra end of or closing tag
        {
            final AssertingTagListener listener = new AssertingTagListener();
            assertEquals("readybzzzz", FormattedTextRenderer.render("re@/ady@<w@>bzz@>zz@>", false, mockSb(), listener).toString());
            listener.end();
        }
        // tag in special tag
        {
            final AssertingTagListener listener = new AssertingTagListener("bb@9cc@7dd:2:4:aaee");
            assertEquals("aaeeff", FormattedTextRenderer.render("aa@<bb@9cc@7dd@>ee@/ff", false, mockSb(), listener).toString());
            listener.end();
        }
    }
}
