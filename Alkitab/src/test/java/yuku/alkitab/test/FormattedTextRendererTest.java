package yuku.alkitab.test;

import android.support.annotation.NonNull;
import android.text.SpannableStringBuilder;
import junit.framework.TestCase;
import yuku.alkitab.base.widget.FormattedTextRenderer;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

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

	SpannableStringBuilder mockSb(String prefix) {
		final SpannableStringBuilder res = mockSb();
		res.append(prefix);
		return res;
	}

	public void testPericopeRenderer() {
		// test robustness
		assertEquals("123", FormattedTextRenderer.render("123", mockSb()).toString());
		assertEquals("23", FormattedTextRenderer.render("@123", mockSb()).toString());
		assertEquals("123", FormattedTextRenderer.render("@@123", mockSb()).toString());

		// test italic
		assertEquals("ab", FormattedTextRenderer.render("@@@9ab@7", mockSb()).toString());
		assertEquals("abcd", FormattedTextRenderer.render("@@@9ab@7cd", mockSb()).toString());
		assertEquals("abcd", FormattedTextRenderer.render("@@ab@9cd@7", mockSb()).toString());

		// required vs optional formatting header
		assertEquals("ab", FormattedTextRenderer.render("@9ab@7", false, mockSb()).toString());
		assertEquals("@9ab@7", FormattedTextRenderer.render("@9ab@7", true, mockSb()).toString());
		assertEquals("ab", FormattedTextRenderer.render("@@@9ab@7", true, mockSb()).toString());

		// appending test
		final SpannableStringBuilder prefix = mockSb("prefix");
		assertEquals("prefixab", FormattedTextRenderer.render("@9ab@7", false, prefix).toString());
		assertEquals("prefixab@9ab@7", FormattedTextRenderer.render("@9ab@7", true, prefix).toString());
		assertEquals("prefixab@9ab@7ab", FormattedTextRenderer.render("@@@9ab@7", true, prefix).toString());

		final SpannableStringBuilder text = FormattedTextRenderer.render("@@ab@9cd@7ef@9gh@7ij", mockSb());
		final Object[] spans = text.getSpans(0, text.length(), Object.class);
		assertEquals(2, spans.length);
		assertEquals(2, text.getSpanStart(spans[0]));
		assertEquals(4, text.getSpanEnd(spans[0]));
		assertEquals(6, text.getSpanStart(spans[1]));
		assertEquals(8, text.getSpanEnd(spans[1]));

		assertTrue(FormattedTextRenderer.render("@@rr@@ab@9cd@9@@9@9@@&@&7&77@@7@7@7", mockSb()).toString().startsWith("rr@@abcd"));
	}
}
