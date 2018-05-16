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

	public static void applyMarkerSnippetContentAndAppearance(TextView t, String reference, CharSequence verseText, float textSizeMult) {
		final SpannableStringBuilder sb = new SpannableStringBuilder(reference);
		sb.setSpan(new UnderlineSpan(), 0, reference.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		sb.append(' ').append(verseText);
		t.setText(sb);
		applyTextAppearance(t, textSizeMult);
	}

	public static void applyTextAppearance(TextView t, float fontSizeMultiplier) {
		final S.CalculatedDimensions applied = S.applied();

		t.setTypeface(applied.fontFace, applied.fontBold);
		t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, applied.fontSize2dp * fontSizeMultiplier);
		t.setIncludeFontPadding(false);
		t.setTextColor(applied.fontColor);
		t.setLinkTextColor(applied.fontColor);
		t.setLineSpacing(0.f, applied.lineSpacingMult);
	}

	public static void applyPericopeTitleAppearance(TextView t, float fontSizeMultiplier) {
		final S.CalculatedDimensions applied = S.applied();

		t.setTypeface(applied.fontFace, Typeface.BOLD);
		t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, applied.fontSize2dp * fontSizeMultiplier);
		t.setTextColor(applied.fontColor);
		t.setLineSpacing(0.f, applied.lineSpacingMult);
	}

	public static void applyPericopeParallelTextAppearance(TextView t, float fontSizeMultiplier) {
		final S.CalculatedDimensions applied = S.applied();

		t.setTypeface(applied.fontFace);
		t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, applied.fontSize2dp * 0.8235294f * fontSizeMultiplier);
		t.setMovementMethod(LinkMovementMethod.getInstance());
		t.setTextColor(applied.fontColor);
		t.setLinkTextColor(applied.fontColor);
		t.setLineSpacing(0.f, applied.lineSpacingMult);
	}
	
	public static void applySearchResultReferenceAppearance(TextView t, SpannableStringBuilder sb, float textSizeMult) {
		applyMarkerTitleTextAppearance(t, textSizeMult);
		sb.setSpan(new UnderlineSpan(), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		t.setText(sb);
		t.setLineSpacing(0.f, S.applied().lineSpacingMult);
	}

	public static void applyMarkerTitleTextAppearance(TextView t, float textSizeMult) {
		final S.CalculatedDimensions applied = S.applied();

		t.setTypeface(applied.fontFace, applied.fontBold);
		t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, applied.fontSize2dp * 1.2f * textSizeMult);
		t.setTextColor(applied.fontColor);
	}

	public static void applyMarkerDateTextAppearance(TextView t, float textSizeMult) {
		final S.CalculatedDimensions applied = S.applied();

		t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, applied.fontSize2dp * 0.8f * textSizeMult);
		t.setTextColor(applied.fontColor);
	}

	public static void applyVerseNumberAppearance(TextView t, float textSizeMult) {
		final S.CalculatedDimensions applied = S.applied();

		t.setTypeface(applied.fontFace, applied.fontBold);
		t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, applied.fontSize2dp * 0.7f * textSizeMult);
		t.setIncludeFontPadding(false);
		t.setTextColor(applied.verseNumberColor);
	}
}
