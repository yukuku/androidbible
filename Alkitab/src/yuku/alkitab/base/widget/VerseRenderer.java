package yuku.alkitab.base.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.LineHeightSpan;
import android.text.style.MetricAffectingSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.TextView;

import yuku.alkitab.R;
import yuku.alkitab.base.S;
import yuku.alkitab.base.util.Appearances;

public class VerseRenderer {
	public static final String TAG = VerseRenderer.class.getSimpleName();
	
	static class ParagraphSpacingBefore implements LineHeightSpan {
		private final int before;
		
		ParagraphSpacingBefore(int before) {
			this.before = before;
		}
		
		@Override public void chooseHeight(CharSequence text, int start, int end, int spanstartv, int v, FontMetricsInt fm) {
			if (spanstartv == v) {
				fm.top -= before;
				fm.ascent -= before;
			}
		}
	}

	/**
	 * This is used instead of {@link LeadingMarginSpan.Standard} to overcome
	 * a bug in CyanogenMod 7.x. If we don't support CM 7 anymore, we 
	 * can use that instead of this, which seemingly *a bit* more efficient. 
	 */
	static class LeadingMarginSpanFixed implements LeadingMarginSpan.LeadingMarginSpan2 {
		private final int first;
		private final int rest;
	
		@Override public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int baseline, int bottom, CharSequence text, int start, int end, boolean first, Layout layout) {}
		
		public LeadingMarginSpanFixed(int all) {
			this.first = all;
			this.rest = all;
		}
	
		public LeadingMarginSpanFixed(int first, int rest) {
			this.first = first;
			this.rest = rest;
		}
		
		@Override public int getLeadingMargin(boolean first) {
			return first? this.first: this.rest;
		}
	
		@Override public int getLeadingMarginLineCount() {
			return 1;
		}
	}

	static class VerseNumberSpan extends MetricAffectingSpan {
		private final boolean applyColor;
	
		public VerseNumberSpan(boolean applyColor) {
			this.applyColor = applyColor;
		}
		
		@Override public void updateMeasureState(TextPaint tp) {
			tp.baselineShift += (int) (tp.ascent() * 0.3f + 0.5f);
			tp.setTextSize(tp.getTextSize() * 0.7f);
		}
	
		@Override public void updateDrawState(TextPaint tp) {
			tp.baselineShift += (int) (tp.ascent() * 0.3f + 0.5f);
			tp.setTextSize(tp.getTextSize() * 0.7f);
			if (applyColor) {
				tp.setColor(S.applied.verseNumberColor);
			}
		}
	}

	/** 0 undefined. 1 and 2 based on version. */
	private static int leadingMarginSpanVersion = 0;
	
	/** Creates a leading margin span based on version:
	 * - API 7 or 11 and above: LeadingMarginSpan.Standard
	 * - API 8..10: LeadingMarginSpanFixed, which is based on LeadingMarginSpan.LeadingMarginSpan2
	 */
	static Object createLeadingMarginSpan(int all) {
		return createLeadingMarginSpan(all, all);
	}
	
	/** Creates a leading margin span based on version:
	 * - API 7 or 11 and above: LeadingMarginSpan.Standard
	 * - API 8..10: LeadingMarginSpanFixed, which is based on LeadingMarginSpan.LeadingMarginSpan2
	 */
	static Object createLeadingMarginSpan(int first, int rest) {
		if (leadingMarginSpanVersion == 0) {
			int v = Build.VERSION.SDK_INT;
			leadingMarginSpanVersion = (v == 7 || v >= 11)? 1: 2; 
		}
		
		if (leadingMarginSpanVersion == 1) {
			return new LeadingMarginSpan.Standard(first, rest); 
		} else {
			return new VerseRenderer.LeadingMarginSpanFixed(first, rest);
		}
	}
	
	private static ThreadLocal<char[]> buf_char_ = new ThreadLocal<char[]>() {
		@Override protected char[] initialValue() {
			return new char[1024];
		}
	};
	
	private static final String[] VERSE_NUMBER_STRINGS = {
		"0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
		"10", "11", "12", "13", "14", "15", "16", "17", "18", "19",
		"20", "21", "22", "23", "24", "25", "26", "27", "28", "29",
		"30", "31", "32", "33", "34", "35", "36", "37", "38", "39",
		"40", "41", "42", "43", "44", "45", "46", "47", "48", "49",
		"50", "51", "52", "53", "54", "55", "56", "57", "58", "59",
		"60", "61", "62", "63", "64", "65", "66", "67", "68", "69",
		"70", "71", "72", "73", "74", "75", "76", "77", "78", "79",
	}; // up to [79]
	
	/**
	 * @param dontPutSpacingBefore this verse is right after a pericope title or on the 0th position
	 */
	public static void render(TextView lText, TextView lVerseNumber, int ari, int verse_1, String text, int highlightColor, boolean checked, boolean dontPutSpacingBefore, int withXref, VersesView.XrefListener xrefListener) {
		// @@ = start a verse containing paragraphs or formatting
		// @0 = start with indent 0 [paragraph]
		// @1 = start with indent 1 [paragraph]
		// @2 = start with indent 2 [paragraph]
		// @3 = start with indent 3 [paragraph]
		// @4 = start with indent 4 [paragraph]
		// @6 = start of red text [formatting]
		// @5 = end of red text   [formatting]
		// @9 = start of italic [formatting]
		// @7 = end of italic   [formatting]
		// @8 = put a blank line to the next verse [formatting]
		// @^ = start-of-paragraph marker
		// @< to @> = special tags
		// @/ = end of special tags (for those need to be closed)
		
		int text_len = text.length();
		
		// Determine if this verse text is a simple verse or formatted verse. 
		// Formatted verses start with "@@".
		// Second character must be '@' too, if not it's wrong, we will fallback to simple render.
		if (text_len < 2 || text.charAt(0) != '@' || text.charAt(1) != '@') {
			simpleRender(lText, lVerseNumber, ari, verse_1, text, highlightColor, checked, withXref, xrefListener);
			return;
		}

		// optimization, to prevent repeated calls to charAt()
		char[] text_c = buf_char_.get();
		if (text_c.length < text_len) {
			text_c = new char[text_len];
			buf_char_.set(text_c);
		}
		text.getChars(0, text_len, text_c, 0);
		
		/**
		 * '0'..'4', '^' indent 0..4 or new para
		 * -1 undefined
		 */
		int paraType = -1; 
		/**
		 * position of start of paragraph
		 */
		int startPara = 0;
		/**
		 * position of start red marker
		 */
		int startRed = -1;
		/**
		 * position of start italic marker
		 */
		int startItalic = -1;
		
		SpannableStringBuilder sb = new SpannableStringBuilder();
	
		// this has two uses
		// - to check whether a verse number has been written
		// - to check whether we need to put a new line when encountering a new para 
		int startPosAfterVerseNumber = 0;
		String verseNumber_s = verse_1 < 80? VERSE_NUMBER_STRINGS[verse_1]: Integer.toString(verse_1);
	
		int pos = 2; // we start after "@@"
	
		// write verse number inline only when no @[1234^] on the beginning of text
		if (text_len >= 4 && text_c[pos] == '@' && (text_c[pos+1] == '^' || (text_c[pos+1] >= '1' && text_c[pos+1] <= '4'))) {
			// don't write verse number now
		} else {
			sb.append(verseNumber_s);
			sb.setSpan(new VerseRenderer.VerseNumberSpan(!checked), 0, sb.length(), 0);
			sb.append("  ");
			startPosAfterVerseNumber = sb.length();
		}
	
		
		// initialize lVerseNumber to have no padding first
		lVerseNumber.setPadding(0, 0, 0, 0);
		
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
			
			char marker = text_c[pos];
			switch (marker) {
			case '0':
			case '1':
			case '2':
			case '3':
			case '4':
			case '^':
				// apply previous
				applyParaStyle(sb, paraType, startPara, verse_1, startPosAfterVerseNumber > 0, dontPutSpacingBefore && startPara <= startPosAfterVerseNumber, startPara <= startPosAfterVerseNumber, lVerseNumber);
				if (sb.length() > startPosAfterVerseNumber) {
					sb.append("\n");
				}
				// store current
				paraType = marker;
				startPara = sb.length();
				break;
			case '6':
				startRed = sb.length();
				break;
			case '9':
				startItalic = sb.length();
				break;
			case '5':
				if (startRed != -1) {
					if (!checked) {
						sb.setSpan(new ForegroundColorSpan(S.applied.fontRedColor), startRed, sb.length(), 0);
					}
					startRed = -1;
				}
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
			}
			
			pos++;
		}
		
		// apply unapplied
		applyParaStyle(sb, paraType, startPara, verse_1, startPosAfterVerseNumber > 0, dontPutSpacingBefore && startPara <= startPosAfterVerseNumber, startPara <= startPosAfterVerseNumber, lVerseNumber);
	
		if (highlightColor != 0) {
			sb.setSpan(new BackgroundColorSpan(highlightColor), startPosAfterVerseNumber == 0? 0: verseNumber_s.length() + 1, sb.length(), 0);
		}
		
		for (int i = 0; i < withXref; i++) {
			addXrefLink(lText.getContext(), sb, ari, i, xrefListener);
		}
	
		lText.setText(sb);
		
		// show verse on lVerseNumber if not shown in lText yet
		if (startPosAfterVerseNumber > 0) {
			lVerseNumber.setText(""); //$NON-NLS-1$
		} else {
			lVerseNumber.setText(verseNumber_s);
			Appearances.applyVerseNumberAppearance(lVerseNumber);
			if (checked) {
				lVerseNumber.setTextColor(0xff000000); // override with black!
			}
		}
	}
	
    /**
	 * @param paraType if -1, will apply the same thing as when paraType is 0 and firstLineWithVerseNumber is true.
	 * @param firstLineWithVerseNumber If this is formatting for the first paragraph of a verse and that paragraph contains a verse number, so we can apply more lefty first-line indent.
	 * This only applies if the paraType is 0.
	 * @param dontPutSpacingBefore if this paragraph is just after pericope title or on the 0th position, in this case we don't apply paragraph spacing before.
	 * @return whether we should put a top-spacing to the detached verse number too
	 */
	static void applyParaStyle(SpannableStringBuilder sb, int paraType, int startPara, int verse_1, boolean firstLineWithVerseNumber, boolean dontPutSpacingBefore, boolean firstParagraph, TextView lVerseNumber) {
		int len = sb.length();
		
		if (startPara == len) return;
		
		switch (paraType) {
		case -1:
			sb.setSpan(createLeadingMarginSpan(0, S.applied.indentParagraphRest), startPara, len, 0);
			break;
		case '0':
			if (firstLineWithVerseNumber) {
				sb.setSpan(createLeadingMarginSpan(0, S.applied.indentParagraphRest), startPara, len, 0);
			} else {
				sb.setSpan(createLeadingMarginSpan(S.applied.indentParagraphRest), startPara, len, 0);
			}
			break;
		case '1':
			sb.setSpan(createLeadingMarginSpan(S.applied.indentSpacing1 + (verse_1 >= 100 ? S.applied.indentSpacingExtra : 0)), startPara, len, 0);
			break;
		case '2':
			sb.setSpan(createLeadingMarginSpan(S.applied.indentSpacing2 + (verse_1 >= 100 ? S.applied.indentSpacingExtra : 0)), startPara, len, 0);
			break;
		case '3':
			sb.setSpan(createLeadingMarginSpan(S.applied.indentSpacing3 + (verse_1 >= 100 ? S.applied.indentSpacingExtra : 0)), startPara, len, 0);
			break;
		case '4':
			sb.setSpan(createLeadingMarginSpan(S.applied.indentSpacing4 + (verse_1 >= 100 ? S.applied.indentSpacingExtra : 0)), startPara, len, 0);
			break;
		case '^':
			if (!dontPutSpacingBefore) {
				sb.setSpan(new VerseRenderer.ParagraphSpacingBefore(S.applied.paragraphSpacingBefore), startPara, len, 0);
				if (firstParagraph) {
					lVerseNumber.setPadding(0, S.applied.paragraphSpacingBefore, 0, 0);
				}
			}
			sb.setSpan(createLeadingMarginSpan(S.applied.indentParagraphFirst, S.applied.indentParagraphRest), startPara, len, 0);
			break;
		}
	}

	public static void simpleRender(TextView lText, TextView lVerseNumber, int ari, int verse_1, String text, int highlightColor, boolean checked, int withXref, VersesView.XrefListener xrefListener) {
		// initialize lVerseNumber to have no padding first
		lVerseNumber.setPadding(0, 0, 0, 0);
		
		SpannableStringBuilder sb = new SpannableStringBuilder();
	
		// verse number
		String verseNumber_s = verse_1 < 80? VERSE_NUMBER_STRINGS[verse_1]: Integer.toString(verse_1);
		sb.append(verseNumber_s).append("  ").append(text);
		sb.setSpan(new VerseRenderer.VerseNumberSpan(!checked), 0, verseNumber_s.length(), 0);
	
		// verse text
		sb.setSpan(createLeadingMarginSpan(0, S.applied.indentParagraphRest), 0, sb.length(), 0);
	
		if (highlightColor != 0) {
			sb.setSpan(new BackgroundColorSpan(highlightColor), verseNumber_s.length() + 1, sb.length(), 0);
		}
		
		for (int i = 0; i < withXref; i++) {
			addXrefLink(lText.getContext(), sb, ari, i, xrefListener);
		}
	
		lText.setText(sb);
		lVerseNumber.setText("");
	}

	static void addXrefLink(final Context context, SpannableStringBuilder sb, final int ari, final int which, final VersesView.XrefListener xrefListener) {
		// if last char of this sb is newline, move back.
		int sb_start = sb.length();
		if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
			sb_start --;
		}
		
		sb.insert(sb_start, "\u2022 "); // append space after it to prevent false click detection
		int sb_end = sb_start+1;
		
		sb.setSpan(new ImageSpan(context, R.drawable.ic_btn_search, DynamicDrawableSpan.ALIGN_BASELINE), sb_start, sb_start+1, 0);
		sb.setSpan(new ClickableSpan() {
			@Override public void onClick(View widget) {
				if (xrefListener != null) {
					xrefListener.onXrefClick(ari, which);
				}
			}
		}, sb_start, sb_end, 0);
	}
}
