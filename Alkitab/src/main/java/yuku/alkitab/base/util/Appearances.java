package yuku.alkitab.base.util;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.UnderlineSpan;
import android.util.TypedValue;
import android.widget.TextView;

import yuku.alkitab.base.S;

public class Appearances {
	public static final String TAG = Appearances.class.getSimpleName();

	public static void applyMarkerSnippetContentAndAppearance(TextView t, String reference, CharSequence verseText) {
		SpannableStringBuilder sb = new SpannableStringBuilder(reference);
		sb.setSpan(new UnderlineSpan(), 0, reference.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		sb.append(' ').append(verseText);
		t.setText(sb);
		applyTextAppearance(t);
	}

	public static void applyTextAppearance(TextView t) {
		t.setTypeface(S.applied.fontFace, S.applied.fontBold);
		t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, S.applied.fontSize2dp);
		t.setIncludeFontPadding(false);
		t.setTextColor(S.applied.fontColor);
		t.setLinkTextColor(S.applied.fontColor);
		t.setLineSpacing(0.f, S.applied.lineSpacingMult);
	}

	public static void applyPericopeTitleAppearance(TextView t) {
		t.setTypeface(S.applied.fontFace, Typeface.BOLD);
		t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, S.applied.fontSize2dp);
		t.setTextColor(S.applied.fontColor);
		t.setLineSpacing(0.f, S.applied.lineSpacingMult);
	}

	public static void applyPericopeParallelTextAppearance(TextView t) {
		t.setTypeface(S.applied.fontFace);
		t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, S.applied.fontSize2dp * (14.f / 17.f));
		t.setMovementMethod(LinkMovementMethod.getInstance());
		t.setTextColor(S.applied.fontColor);
		t.setLinkTextColor(S.applied.fontColor);
		t.setLineSpacing(0.f, S.applied.lineSpacingMult);
	}
	
	public static void applySearchResultReferenceAppearance(TextView t, SpannableStringBuilder sb) {
		applyMarkerTitleTextAppearance(t);
		sb.setSpan(new UnderlineSpan(), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		t.setText(sb);
		t.setLineSpacing(0.f, S.applied.lineSpacingMult);
	}

	public static void applyMarkerTitleTextAppearance(TextView t) {
		t.setTypeface(S.applied.fontFace, S.applied.fontBold);
		t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, S.applied.fontSize2dp * 1.2f);
		t.setTextColor(S.applied.fontColor);
	}

	public static void applyMarkerDateTextAppearance(TextView t) {
		t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, S.applied.fontSize2dp * 0.8f);
		t.setTextColor(S.applied.fontColor);
	}

	public static void applyVerseNumberAppearance(TextView t) {
		t.setTypeface(S.applied.fontFace, S.applied.fontBold);
		t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, S.applied.fontSize2dp * 0.7f);
		t.setIncludeFontPadding(false);
		t.setTextColor(S.applied.verseNumberColor);
	}
}
