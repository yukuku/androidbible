package yuku.alkitab.base.widget;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.widget.TextView;

public class PericopeRenderer {
	public static void render(final TextView textView, final String text) {
		final int text_len = text.length();
		if (text_len < 2 || text.charAt(0) != '@' || text.charAt(1) != '@') {
			textView.setText(text);
			return;
		}

		SpannableStringBuilder sb = new SpannableStringBuilder();
		int pos = 2;
		int startItalic = -1;
		while (true) {
			if (pos >= text_len) {
				break;
			}
			int nextAt = text.indexOf('@', pos);

			if (nextAt == -1) { // no more, just append till the end of everything and exit
				sb.append(text, pos, text_len);
				break;
			}

			// insert all text until the nextAt
			if (nextAt != pos) /* optimization */ {
				sb.append(text, pos, nextAt);
				pos = nextAt;
			}

			pos++;
			// just in case
			if (pos >= text_len) {
				break;
			}

			char marker = text.charAt(pos);
			switch (marker) {
				case '9':
					startItalic = sb.length();
					break;
				case '7':
					if (startItalic != -1) {
						sb.setSpan(new StyleSpan(Typeface.ITALIC), startItalic, sb.length(), 0);
						startItalic = -1;
					}
					break;
			}
			pos++;
		}
		textView.setText(sb);
	}
}
