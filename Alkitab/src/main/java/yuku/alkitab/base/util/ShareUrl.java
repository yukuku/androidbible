package yuku.alkitab.base.util;

import android.app.Activity;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.afollestad.materialdialogs.MaterialDialog;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.FormEncodingBuilder;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import yuku.alkitab.base.App;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Version;
import yuku.alkitab.util.Ari;
import yuku.alkitab.util.IntArrayList;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class ShareUrl {
	public interface Callback {
		void onSuccess(String shareUrl);
		void onUserCancel();
		void onError(Exception e);
		void onFinally();
	}

	public static void make(@NonNull final Activity activity, final boolean immediatelyCancel, @NonNull final String verseText, final int ari_bc, @NonNull final IntArrayList selectedVerses_1, @NonNull final String reference, @NonNull final Version version, @Nullable final String preset_name, @NonNull final Callback callback) {
		if (immediatelyCancel) { // user explicitly ask for not submitting url
			callback.onUserCancel();
			callback.onFinally();
			return;
		}

		final StringBuilder aris = new StringBuilder();

		for (int i = 0, len = selectedVerses_1.size(); i < len; i++) {
			final int verse_1 = selectedVerses_1.get(i);
			final int ari = Ari.encodeWithBc(ari_bc, verse_1);
			if (aris.length() != 0) {
				aris.append(',');
			}
			aris.append(ari);
		}
		final FormEncodingBuilder form = new FormEncodingBuilder()
			.add("verseText", verseText)
			.add("aris", aris.toString())
			.add("verseReferences", reference);

		if (preset_name != null) {
			form.add("preset_name", preset_name);
		}

		final String versionLongName = version.getLongName();
		if (versionLongName != null) {
			form.add("versionLongName", versionLongName);
		}

		final String versionShortName = version.getShortName();
		if (versionShortName != null) {
			form.add("versionShortName", versionShortName);
		}

		final Call call = App.getOkHttpClient().newCall(
			new Request.Builder()
				.url("http://www.bibleforandroid.com/v/create")
			.post(form.build())
				.build()
		);

		// when set to true, do not call any callback
		final AtomicBoolean done = new AtomicBoolean();

		final MaterialDialog dialog = new MaterialDialog.Builder(activity)
			.content("Getting share URL…")
			.progress(true, 0)
			.negativeText(R.string.cancel)
			.callback(new MaterialDialog.ButtonCallback() {
				@Override
				public void onNegative(final MaterialDialog dialog) {
					if (!done.getAndSet(true)) {
						done.set(true);
						callback.onUserCancel();
						dialog.dismiss();
						callback.onFinally();
					}
				}
			})
			.dismissListener(dialog1 -> {
				if (!done.getAndSet(true)) {
					callback.onUserCancel();
					dialog1.dismiss();
					callback.onFinally();
				}
			})
			.show();

		call.enqueue(new com.squareup.okhttp.Callback() {
			@Override
			public void onFailure(final Request request, final IOException e) {
				if (!done.getAndSet(true)) {
					activity.runOnUiThread(() -> {
						callback.onError(e);
						dialog.dismiss();
						callback.onFinally();
					});
				}
			}

			@Override
			public void onResponse(final Response response) throws IOException {
				if (!done.getAndSet(true)) {
					final ShareUrlResponseJson obj = App.getDefaultGson().fromJson(response.body().charStream(), ShareUrlResponseJson.class);
					if (obj.success) {
						activity.runOnUiThread(() -> {
							callback.onSuccess(obj.share_url);
							dialog.dismiss();
							callback.onFinally();
						});
					} else {
						activity.runOnUiThread(() -> {
							callback.onError(new Exception(obj.message));
							dialog.dismiss();
							callback.onFinally();
						});
					}
				}
			}
		});
	}

	static class ShareUrlResponseJson {
		public boolean success;
		public String message;
		public String share_url;
	}
}
