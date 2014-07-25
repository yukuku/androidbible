package yuku.alkitab.base.widget;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;

public class FormattedTextRenderer {
	/**
	 * Renders a simple formatted text. This is a much simpler version of {@link VerseRenderer},
	 * only some tags are supported. The supported tags are:
	 * <pre>
	 *     @9...@7 for italics
	 *     @8 for line break
	 * </pre>
	 *
	 * @param text String with formatting tags. Optionally it can start with "@@".
	 */
	public static SpannableStringBuilder render(final String text) {
		return render(text, null);
	}

	/**
	 * Renders a simple formatted text. This is a much simpler version of {@link yuku.alkitab.base.widget.VerseRenderer},
	 * only some tags are supported. The supported tags are:
	 * <pre>
	 *     @9...@7 for italics
	 *     @8 for line break
	 * </pre>
	 *
	 * @param text String with formatting tags. Optionally it can start with "@@".
	 * @param appendToThis If not null, the results are appended to this string instead of newly created. Note that
	 * the contents of this parameter will be modified.
	 */
	public static SpannableStringBuilder render(final String text, final SpannableStringBuilder appendToThis) {
		return render(text, false, appendToThis);
	}

	/**
	 * Renders a simple formatted text. This is a much simpler version of {@link VerseRenderer},
	 * only some tags are supported. The supported tags are:
	 * <pre>
	 *     @9...@7 for italics
	 *     @8 for line break
	 * </pre>
	 *
	 * @param text String with formatting tags
	 * @param mustHaveFormattedHeader when true, the text must start with "@@" to enable formatting, otherwise, the text
	 * is treated as plain.
	 */
	public static SpannableStringBuilder render(final String text, final boolean mustHaveFormattedHeader) {
		return render(text, mustHaveFormattedHeader, null);
	}

	/**
	 * Renders a simple formatted text. This is a much simpler version of {@link yuku.alkitab.base.widget.VerseRenderer},
	 * only some tags are supported. The supported tags are:
	 * <pre>
	 *     @9...@7 for italics
	 *     @8 for line break
	 * </pre>
	 *
	 * @param text String with formatting tags
	 * @param mustHaveFormattedHeader when true, the text must start with "@@" to enable formatting, otherwise, the text
	 * @param appendToThis If not null, the results are appended to this string instead of newly created. Note that
	 * the contents of this parameter will be modified.
	 */
	public static SpannableStringBuilder render(final String text, final boolean mustHaveFormattedHeader, final SpannableStringBuilder appendToThis) {
		final int text_len = text.length();
		if (mustHaveFormattedHeader) {
			if (text_len < 2 || text.charAt(0) != '@' || text.charAt(1) != '@') {
				if (appendToThis == null) {
					return new SpannableStringBuilder(text);
				} else {
					appendToThis.append(text);
					return appendToThis;
				}
			}
		}

		int pos = 0;
		if (text_len >= 2 && text.charAt(0) == '@' && text.charAt(1) == '@') { // absorb "@@" at the beginning
			pos = 2;
		}

		int startItalic = -1;
		final SpannableStringBuilder sb = appendToThis != null? appendToThis: new SpannableStringBuilder();
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

			final char marker = text.charAt(pos);
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
				case '8':
					sb.append("\n");
					break;
				case '0':
				case '1':
				case '2':
				case '3':
				case '4':
				case '5':
				case '6':
				case '^':
					// known tags but not supported, so let's just be silent
					break;
				default:
					// just print out as-is
					sb.append("@").append(marker);
					break;
			}
			pos++;
		}

		return sb;
	}
}
