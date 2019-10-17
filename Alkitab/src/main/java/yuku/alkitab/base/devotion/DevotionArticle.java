package yuku.alkitab.base.devotion;

import androidx.annotation.NonNull;
import android.text.SpannableStringBuilder;
import android.text.style.URLSpan;
import yuku.alkitab.base.ac.DevotionActivity;
import yuku.alkitab.base.widget.CallbackSpan;


public abstract class DevotionArticle {
	public abstract CharSequence getContent(CallbackSpan.OnClickListener<String> listener);

	/**
	 * @return Date of this devotion in yyyymmdd format.
	 */
	public abstract String getDate();
	public abstract boolean getReadyToUse();
	
	//# used by external
	public abstract DevotionActivity.DevotionKind getKind();

	/**
	 * From raw, implementations must fill in other data like header, title, and body.
	 * Also must set their own "readyToUse" property.
	 */
	public abstract void fillIn(String raw);

	/**
	 * Replace URLSpans with CallbackSpans for verse links
 	 */
	protected void convertLinks(final SpannableStringBuilder sb, final CallbackSpan.OnClickListener<String> verseClickListener) {
		URLSpan[] spans = sb.getSpans(0, sb.length(), URLSpan.class);
		for (URLSpan oldSpan: spans) {
			String url = oldSpan.getURL();
			if (url.startsWith("http:") || url.startsWith("https:")) {
				continue; // do not change web links
			}
			CallbackSpan<String> newSpan = new CallbackSpan<>(url, verseClickListener);
			sb.setSpan(newSpan, sb.getSpanStart(oldSpan), sb.getSpanEnd(oldSpan), 0);
			sb.removeSpan(oldSpan);
		}
	}

	@NonNull public abstract String getBody();
}
