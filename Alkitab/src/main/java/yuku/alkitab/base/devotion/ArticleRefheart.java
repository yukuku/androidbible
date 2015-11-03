package yuku.alkitab.base.devotion;

import android.support.annotation.NonNull;
import android.text.Html;
import android.text.SpannableStringBuilder;
import yuku.alkitab.base.ac.DevotionActivity;
import yuku.alkitab.base.widget.CallbackSpan;

public class ArticleRefheart extends DevotionArticle {
	public static final String TAG = ArticleRefheart.class.getSimpleName();
	private String date;
	private String bodyHtml;
	private boolean readyToUse;

	public ArticleRefheart(String date) {
		this.date = date;
	}

	public ArticleRefheart(String date, String bodyHtml, boolean readyToUse) {
		this.date = date;
		this.bodyHtml = bodyHtml;
		this.readyToUse = readyToUse;
	}

	@Override
	public String getDate() {
		return date;
	}

	@Override
	public boolean getReadyToUse() {
		return readyToUse;
	}

	@Override
	public DevotionActivity.DevotionKind getKind() {
		return DevotionActivity.DevotionKind.REFHEART;
	}

	@Override
	public void fillIn(String raw) {
		bodyHtml = raw;
		readyToUse = !raw.startsWith("NG");
	}

	@Override
	public CharSequence getContent(CallbackSpan.OnClickListener<String> verseClickListener) {
		final SpannableStringBuilder sb = new SpannableStringBuilder();
		sb.append(Html.fromHtml(bodyHtml));

		convertLinks(sb, verseClickListener);

		return sb;
	}

	@Override
	@NonNull
	public String getBody() {
		return bodyHtml;
	}
}
