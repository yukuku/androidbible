package yuku.alkitab.base.devotion;

import android.text.Html;
import android.text.SpannableStringBuilder;
import android.util.Log;
import yuku.alkitab.base.App;
import yuku.alkitab.base.ac.DevotionActivity;
import yuku.alkitab.base.widget.CallbackSpan;


public class ArticleSantapanHarian extends ArticleFromSabda {
	static final String TAG = ArticleSantapanHarian.class.getSimpleName();

	public ArticleSantapanHarian(String date) {
		super(date);
	}

	public ArticleSantapanHarian(String date, String body, boolean readyToUse) {
		super(date, body, readyToUse);
	}

	@Override public CharSequence getContent(CallbackSpan.OnClickListener<String> verseClickListener) {
		final String template = "" +
			"%s\n" + // tanggal
			"<p>" +
			"%s\n" + // judul
			"<p>" +
			"%s\n" + // ayat
			"<p>" +
			"%s\n" + // isi
			"";

		try {
			final BodyJson bodyJson = App.getDefaultGson().fromJson(body, BodyJson.class);

			final String html = String.format(template,
				bodyJson.tanggal,
				bodyJson.judul,
				bodyJson.ayat,
				bodyJson.isi
			);

			final SpannableStringBuilder res = new SpannableStringBuilder(Html.fromHtml(html));
			convertLinks(res, verseClickListener);
			return res;
		} catch (Exception e) {
			Log.d(TAG, "Probably json parsing error. body: " + body);
			return "Error parsing body json: " + e.getMessage() + "\n\nPlease reload this devotion.";
		}
	}

	@Override
	public DevotionActivity.DevotionKind getKind() {
		return DevotionActivity.DevotionKind.SH;
	}
}
