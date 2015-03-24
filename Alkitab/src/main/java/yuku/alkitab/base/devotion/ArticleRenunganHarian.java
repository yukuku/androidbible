package yuku.alkitab.base.devotion;

import android.text.Html;
import android.text.SpannableStringBuilder;
import android.util.Log;
import yuku.alkitab.base.App;
import yuku.alkitab.base.ac.DevotionActivity;
import yuku.alkitab.base.widget.CallbackSpan;


public class ArticleRenunganHarian extends ArticleFromSabda {
	static final String TAG = ArticleRenunganHarian.class.getSimpleName();

	public ArticleRenunganHarian(String date) {
		super(date);
	}

	public ArticleRenunganHarian(String date, String body, boolean readyToUse) {
		super(date, body, readyToUse);
	}

	@Override
	public CharSequence getContent(CallbackSpan.OnClickListener<String> verseClickListener) {
		final String template = "" +
			"Bacaan Setahun: %s\n" + // bacaan setahun
			"<p>" +
            "<b>" + "<big>" + "%s" + // judul
            "<br>" +
            "</big>" + "</b>" + "<small>" + "%s (%s)" + // nats_isi, nats_ayat
            "<p>" +
            "<p>" +
			"</small>" + "%s\n" + // isi
			"<p>" +
			"<small>" + "%s\n" + // catatan_kaki
            "</small>" + "";

		try {
			final BodyJson bodyJson = App.getDefaultGson().fromJson(body, BodyJson.class);

			final String html = String.format(template,
				bodyJson.ayat_setahun,
                bodyJson.judul,
                bodyJson.nats == null? null: bodyJson.nats.nats_isi,
				bodyJson.nats == null? null: bodyJson.nats.nats_ayat,
				bodyJson.isi,
				bodyJson.catatan_kaki
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
		return DevotionActivity.DevotionKind.RH;
	}
}


