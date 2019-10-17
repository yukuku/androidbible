package yuku.alkitab.base.devotion;

import androidx.annotation.NonNull;
import android.text.Html;
import android.text.SpannableStringBuilder;
import yuku.alkitab.base.ac.DevotionActivity;
import yuku.alkitab.base.widget.CallbackSpan;

public class ArticleMorningEveningEnglish extends DevotionArticle {
	public static final String TAG = ArticleMorningEveningEnglish.class.getSimpleName();
	private String date;
	private String body;
	private boolean readyToUse;

	public ArticleMorningEveningEnglish(String date) {
		this.date = date;
	}

	public ArticleMorningEveningEnglish(String date, String body, boolean readyToUse) {
		this.date = date;
		this.body = body;
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
		return DevotionActivity.DevotionKind.ME_EN;
	}

	@Override
	public void fillIn(String raw) {
		body = raw;
		readyToUse = !raw.startsWith("NG");
	}

	@Override
	public CharSequence getContent(CallbackSpan.OnClickListener<String> verseClickListener) {
		SpannableStringBuilder sb = new SpannableStringBuilder();
		sb.append(Html.fromHtml(body));

		convertLinks(sb, verseClickListener);

		return sb;
	}

	@Override
	@NonNull
	public String getBody() {
		return body;
	}
}
