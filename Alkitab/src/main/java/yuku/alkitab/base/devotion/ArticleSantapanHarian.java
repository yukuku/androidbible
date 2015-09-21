package yuku.alkitab.base.devotion;

import android.text.Html;
import android.text.SpannableStringBuilder;
import yuku.alkitab.base.ac.DevotionActivity;
import yuku.alkitab.base.widget.CallbackSpan;


public class ArticleSantapanHarian extends ArticleFromSabda {
	public ArticleSantapanHarian(String date) {
		super(date);
	}

	public ArticleSantapanHarian(String date, String body, boolean readyToUse) {
		super(date, body, readyToUse);
	}

	@Override
	public CharSequence getContent(CallbackSpan.OnClickListener<String> verseClickListener) {
		final SpannableStringBuilder res = new SpannableStringBuilder(Html.fromHtml(body));
		convertLinks(res, verseClickListener);
		return res;
	}

	@Override
	public DevotionActivity.DevotionKind getKind() {
		return DevotionActivity.DevotionKind.SH;
	}
}
