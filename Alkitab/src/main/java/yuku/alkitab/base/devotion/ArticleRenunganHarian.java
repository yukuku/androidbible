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
			"%s\n" + // tanggal
			"<p>" +
			"Bacaan Setahun: %s\n" + // bacaan setahun
			"<p>" +
			"%s (%s)" + // nats_isi, nats_ayat
			"<p>" +
			"%s" + // judul
			"<p>" +
			"Bacaan: %s\n" + // ayat
			"<p>" +
			"%s\n" + // isi
			"<p>" +
			"%s\n" + // catatan_kaki
			"";

		try {
			final BodyJson bodyJson = App.getDefaultGson().fromJson(body, BodyJson.class);

			final String html = String.format(template,
				bodyJson.tanggal,
				bodyJson.ayat_setahun,
				bodyJson.nats == null? null: bodyJson.nats.nats_isi,
				bodyJson.nats == null? null: bodyJson.nats.nats_ayat,
				bodyJson.judul,
				bodyJson.ayat,
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


