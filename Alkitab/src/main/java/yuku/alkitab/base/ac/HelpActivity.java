package yuku.alkitab.base.ac;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import com.afollestad.materialdialogs.AlertDialogWrapper;
import yuku.afw.V;
import yuku.alkitab.base.App;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.dialog.VersesDialog;
import yuku.alkitab.base.util.Announce;
import yuku.alkitab.base.util.TargetDecoder;
import yuku.alkitab.debug.R;
import yuku.alkitab.util.IntArrayList;
import yuku.alkitabintegration.display.Launcher;

import java.util.Locale;

public class HelpActivity extends BaseActivity {
	private static final String EXTRA_page = "customPage";
	private static final String EXTRA_showMessagePanel = "showMessagePanel";
	private static final String EXTRA_message = "message";
	private static final String EXTRA_okIntent = "okIntent";
	private static final String EXTRA_announcementIds = "announcementIds";

	WebView webview;
	View bOk;
	View bCancel;
	Intent okIntent;
	long[] announcementIds;

	public static Intent createIntent(String page, boolean showMessagePanel, String message, Intent okIntent) {
		Intent res = new Intent(App.context, HelpActivity.class);
		res.putExtra(EXTRA_page, page);
		res.putExtra(EXTRA_showMessagePanel, showMessagePanel);
		res.putExtra(EXTRA_message, message);
		res.putExtra(EXTRA_okIntent, okIntent);
		return res;
	}

	public static Intent createViewAnnouncementIntent(final long[] announcementIds) {
		return new Intent(App.context, HelpActivity.class)
			.putExtra(EXTRA_announcementIds, announcementIds);
	}

	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreateWithNonToolbarUpButton(savedInstanceState);
		setContentView(R.layout.activity_help);

		webview = V.get(this, R.id.webView);
		bOk = V.get(this, R.id.bOk);
		bCancel = V.get(this, R.id.bCancel);
		View panelFaqOnly = V.get(this, R.id.panelFaqOnly);
		TextView tMessage = V.get(this, R.id.tMessage);

		WebSettings webSettings = webview.getSettings();
		//noinspection deprecation
		webSettings.setSavePassword(false);
		webSettings.setSaveFormData(false);
		webSettings.setJavaScriptEnabled(false);

		bOk.setOnClickListener(bOk_click);
		bCancel.setOnClickListener(bCancel_click);

		final String page = getIntent().getStringExtra(EXTRA_page);
		okIntent = getIntent().getParcelableExtra(EXTRA_okIntent);
		announcementIds = getIntent().getLongArrayExtra(EXTRA_announcementIds);

		final String message = getIntent().getStringExtra(EXTRA_message);
		tMessage.setText(message);

		final boolean showMessagePanel = getIntent().getBooleanExtra(EXTRA_showMessagePanel, false);
		if (!showMessagePanel) {
			panelFaqOnly.setVisibility(View.GONE);
		}

		if (page != null) {
			if (page.startsWith("http:") || page.startsWith("https:")) {
				webview.loadUrl(page);
			} else {
				webview.loadUrl("file:///android_asset/" + page);
			}
		} else if (announcementIds != null) {
			final Locale locale = getResources().getConfiguration().locale;
			webview.loadUrl("https://alkitab-host.appspot.com/announce/view?ids=" + App.getDefaultGson().toJson(announcementIds) + (locale == null ? "" : ("&locale=" + locale.toString())));
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

				setTitle(view.getTitle());

				if (announcementIds != null) {
					Announce.markAsRead(announcementIds);
				}
			}
		});
	}
	
	View.OnClickListener bOk_click = new View.OnClickListener() {
		@Override public void onClick(View v) {
			if (okIntent != null) {
				startActivity(okIntent);
			}
			finish();
		}
	};

	View.OnClickListener bCancel_click = v -> finish();
}
