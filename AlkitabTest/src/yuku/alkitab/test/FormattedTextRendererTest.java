package yuku.alkitab.test;

import android.test.AndroidTestCase;
import android.text.SpannableStringBuilder;
import yuku.alkitab.base.widget.FormattedTextRenderer;

public class FormattedTextRendererTest extends AndroidTestCase {
	public void testPericopeRenderer() {
		// test robustness
		assertEquals("123", FormattedTextRenderer.render("123").toString());
		assertEquals("23", FormattedTextRenderer.render("@123").toString());
		assertEquals("123", FormattedTextRenderer.render("@@123").toString());

		// test italic
		assertEquals("ab", FormattedTextRenderer.render("@@@9ab@7").toString());
		assertEquals("abcd", FormattedTextRenderer.render("@@@9ab@7cd").toString());
		assertEquals("abcd", FormattedTextRenderer.render("@@ab@9cd@7").toString());

		// required vs optional formatting header
		assertEquals("ab", FormattedTextRenderer.render("@9ab@7", false).toString());
		assertEquals("@9ab@7", FormattedTextRenderer.render("@9ab@7", true).toString());
		assertEquals("ab", FormattedTextRenderer.render("@@@9ab@7", true).toString());

		final SpannableStringBuilder text = FormattedTextRenderer.render("@@ab@9cd@7ef@9gh@7ij");
		final Object[] spans = text.getSpans(0, text.length(), Object.class);
		assertEquals(2, spans.length);
		assertEquals(2, text.getSpanStart(spans[0]));
		assertEquals(4, text.getSpanEnd(spans[0]));
		assertEquals(6, text.getSpanStart(spans[1]));
		assertEquals(8, text.getSpanEnd(spans[1]));

		assertTrue(FormattedTextRenderer.render("@@rr@@ab@9cd@9@@9@9@@&@&7&77@@7@7@7").toString().startsWith("rr@@abcd"));
	}
}
