package yuku.alkitab.base.ac;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.afollestad.materialdialogs.AlertDialogWrapper;
import yuku.afw.V;
import yuku.alkitab.base.App;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.dialog.VersesDialog;
import yuku.alkitab.base.util.Announce;
import yuku.alkitab.base.util.TargetDecoder;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.debug.R;
import yuku.alkitab.util.IntArrayList;
import yuku.alkitabintegration.display.Launcher;

import java.util.Locale;

public class HelpActivity extends BaseActivity {
	private static final String EXTRA_page = "page";
	private static final String EXTRA_overrideTitle = "overrideTitle";
	private static final String EXTRA_announcementIds = "announcementIds";

	WebView webview;

	public static Intent createIntent(final String page) {
		return createIntent(page, null);
	}

	public static Intent createIntent(final String page, final String overrideTitle) {
		return new Intent(App.context, HelpActivity.class)
			.putExtra(EXTRA_page, page)
			.putExtra(EXTRA_overrideTitle, overrideTitle);
	}

	public static Intent createViewAnnouncementIntent(final long[] announcementIds) {
		return new Intent(App.context, HelpActivity.class)
			.putExtra(EXTRA_announcementIds, announcementIds);
	}

	@SuppressLint("SetJavaScriptEnabled")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreateWithNonToolbarUpButton(savedInstanceState);
		setContentView(R.layout.activity_help);

		webview = V.get(this, R.id.webView);

		if (BuildConfig.DEBUG) {
			if (Build.VERSION.SDK_INT >= 19) {
				WebView.setWebContentsDebuggingEnabled(true);
			}
		}

		final WebSettings webSettings = webview.getSettings();
		//noinspection deprecation
		webSettings.setSavePassword(false);
		webSettings.setSaveFormData(false);
		webSettings.setJavaScriptEnabled(true);

		final String page = getIntent().getStringExtra(EXTRA_page);
		final String overrideTitle = getIntent().getStringExtra(EXTRA_overrideTitle);
		final long[] announcementIds = getIntent().getLongArrayExtra(EXTRA_announcementIds);

		if (overrideTitle != null) {
			setTitle(overrideTitle);
		}

		if (page != null) {
			if (page.startsWith("http:") || page.startsWith("https:")) {
				webview.loadUrl(page);
			} else {
				webview.loadUrl("file:///android_asset/" + page);
			}
		} else if (announcementIds != null) {
			final Locale locale = getResources().getConfiguration().locale;
			final String url = "https://alkitab-host.appspot.com/announce/view?ids=" + App.getDefaultGson().toJson(announcementIds) + (locale == null ? "" : ("&locale=" + locale.toString()));
			if (BuildConfig.DEBUG) {
				Log.d(TAG, "loading announce view url: " + url);
			}
			webview.loadUrl(url);
		}

		webview.setWebViewClient(new WebViewClient() {
			@Override
			public boolean shouldOverrideUrlLoading(WebView view, String url) {
				final Uri uri = Uri.parse(url);
				final String scheme = uri.getScheme();

				if (scheme == null) {
					return false;
				}

				switch (scheme) {
					case "http":
					case "https":
					case "market": {
						// open in external browser
						final Intent intent = new Intent(Intent.ACTION_VIEW);
						intent.setData(uri);
						startActivity(intent);
					} return true;
					case "alkitab": {
						// send back to caller
						final Intent intent = new Intent();
						intent.setData(uri);
						setResult(RESULT_OK, intent);
						finish();
					} return true;
					case "suggest":
						startActivity(com.example.android.wizardpager.MainActivity.createIntent(App.context));
						finish();
						return true;
					case "bible":
						// try to decode using OSIS format
						final String ssp = uri.getSchemeSpecificPart();
						final IntArrayList ariRanges = TargetDecoder.decode("o:" + ssp);
						if (ariRanges == null || ariRanges.size() == 0) {
							new AlertDialogWrapper.Builder(HelpActivity.this)
								.setMessage(getString(R.string.alamat_tidak_sah_alamat, url))
								.setPositiveButton(R.string.ok, null)
								.show();
						} else {
							final VersesDialog dialog = VersesDialog.newInstance(ariRanges);
							dialog.show(getSupportFragmentManager(), VersesDialog.class.getSimpleName());
							dialog.setListener(new VersesDialog.VersesDialogListener() {
								@Override
								public void onVerseSelected(final VersesDialog dialog, final int ari) {
									Log.d(TAG, "Verse link clicked from page");
									startActivity(Launcher.openAppAtBibleLocation(ari));
								}
							});
						}
						return true;
				}
				return false;
			}

			@Override
			public void onPageFinished(final WebView view, final String url) {
				super.onPageFinished(view, url);

				if (overrideTitle == null) {
					setTitle(view.getTitle());
				}

				if (announcementIds != null) {
					Announce.markAsRead(announcementIds);
				}
			}
		});
	}
}
