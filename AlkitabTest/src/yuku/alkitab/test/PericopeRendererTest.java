package yuku.alkitab.test;

import android.test.AndroidTestCase;
import android.text.SpannedString;
import android.widget.TextView;
import yuku.alkitab.base.widget.PericopeRenderer;

public class PericopeRendererTest extends AndroidTestCase {
	public void testPericopeRenderer() {
		TextView textView = new TextView(getContext());

		// test robustness
		PericopeRenderer.render(textView, "123");
		assertEquals("123", textView.getText().toString());
		PericopeRenderer.render(textView, "@123");
		assertEquals("@123", textView.getText().toString());
		PericopeRenderer.render(textView, "@@123");
		assertEquals("123", textView.getText().toString());

		// test italic
		PericopeRenderer.render(textView, "@@@9ab@7");
		assertEquals("ab", textView.getText().toString());
		PericopeRenderer.render(textView, "@@@9ab@7cd");
		assertEquals("abcd", textView.getText().toString());
		PericopeRenderer.render(textView, "@@ab@9cd@7");
		assertEquals("abcd", textView.getText().toString());

		PericopeRenderer.render(textView, "@@ab@9cd@7ef@9gh@7ij");
		SpannedString text = (SpannedString) textView.getText();
		final Object[] spans = text.getSpans(0, text.length(), Object.class);
		assertEquals(2, spans.length);
		assertEquals(2, text.getSpanStart(spans[0]));
		assertEquals(4, text.getSpanEnd(spans[0]));
		assertEquals(6, text.getSpanStart(spans[1]));
		assertEquals(8, text.getSpanEnd(spans[1]));

		PericopeRenderer.render(textView, "@@rr@@ab@9cd@9@@9@9@@&@&7&77@@7@7@7");
		assertTrue(textView.getText().toString().startsWith("rrabcd"));
	}
}
