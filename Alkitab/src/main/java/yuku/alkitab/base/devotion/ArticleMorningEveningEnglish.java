package yuku.alkitab.base.devotion;

import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.style.URLSpan;

import yuku.alkitab.base.ac.DevotionActivity;
import yuku.alkitab.base.widget.CallbackSpan;

public class ArticleMorningEveningEnglish implements DevotionArticle {
	public static final String TAG = ArticleMorningEveningEnglish.class.getSimpleName();
	private String date;
	private String bodyHtml;
	private boolean readyToUse;
	
	public ArticleMorningEveningEnglish(String date) {
		this.date = date;
	}

	public ArticleMorningEveningEnglish(String date, String bodyHtml, boolean readyToUse) {
		this.date = date;
		this.bodyHtml = bodyHtml;
		this.readyToUse = readyToUse;
	}

	@Override public String getDate() {
		return date;
	}

	@Override public boolean getReadyToUse() {
		return readyToUse;
	}

	@Override
	public DevotionActivity.DevotionKind getKind() {
		return DevotionActivity.DevotionKind.ME_EN;
	}

	@Override public String getRawEncoding() {
		return "utf-8";
	}

	@Override public void fillIn(String raw) {
		bodyHtml = raw;
		readyToUse = !raw.startsWith("NG");
	}

	@Override public CharSequence getContent(CallbackSpan.OnClickListener<String> verseClickListener) {
		SpannableStringBuilder sb = new SpannableStringBuilder();
		sb.append(Html.fromHtml(bodyHtml));
		
		// replace URLSpans with CallbackSpans
		URLSpan[] spans = sb.getSpans(0, sb.length(), URLSpan.class);
		for (URLSpan oldSpan: spans) {
			String url = oldSpan.getURL();
			CallbackSpan<String> newSpan = new CallbackSpan<>(url, verseClickListener);
			sb.setSpan(newSpan, sb.getSpanStart(oldSpan), sb.getSpanEnd(oldSpan), 0);
			sb.removeSpan(oldSpan);
		}
		
		return sb;
	}

	@Override public String[] getHeaderTitleBody() {
		return new String[] {null, null, bodyHtml};
	}
}
