package yuku.alkitab.base.ac;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import yuku.afw.App;
import yuku.afw.V;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.dialog.VersesDialog;
import yuku.alkitab.base.util.IntArrayList;
import yuku.alkitab.base.util.TargetDecoder;
import yuku.alkitab.debug.R;
import yuku.alkitabintegration.display.Launcher;

public class HelpActivity extends BaseActivity {
	private static final String EXTRA_isFaq = "isFaq";
	private static final String EXTRA_customPage = "customPage";

	WebView webview;
	View bOk;
	View bCancel;
	
	boolean isFaq;
	
	public static Intent createIntent(boolean isFaq) {
		Intent res = new Intent(App.context, HelpActivity.class);
		res.putExtra(EXTRA_isFaq, isFaq);
		return res;
	}

	public static Intent createIntent(final boolean isFaq, final String customPage) {
		Intent res = new Intent(App.context, HelpActivity.class);
		res.putExtra(EXTRA_isFaq, isFaq);
		res.putExtra(EXTRA_customPage, customPage);
		return res;
	}

	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_help);

		webview = V.get(this, R.id.webView);
		bOk = V.get(this, R.id.bOk);
		bCancel = V.get(this, R.id.bCancel);
		View panelFaqOnly = V.get(this, R.id.panelFaqOnly);

		WebSettings webSettings = webview.getSettings();
		webSettings.setSavePassword(false);
		webSettings.setSaveFormData(false);
		webSettings.setJavaScriptEnabled(false);
		webSettings.setSupportZoom(true);
		webSettings.setBuiltInZoomControls(true);

		bOk.setOnClickListener(bOk_click);
		bCancel.setOnClickListener(bCancel_click);

		isFaq = getIntent().getBooleanExtra(EXTRA_isFaq, false);
		if (!isFaq) {
			setTitle(R.string.bantuan_judul);
			panelFaqOnly.setVisibility(View.GONE);
		} else {
			setTitle(R.string.beri_saran_title);
		}

		final String customPage = getIntent().getStringExtra(EXTRA_customPage);
		if (customPage != null) {
			webview.loadUrl("file:///android_asset/" + customPage);
		} else {
			String page = isFaq? "faq.html": "index.html";
			if (U.equals("in", getResources().getConfiguration().locale.getLanguage())) {
				webview.loadUrl("file:///android_asset/help/html-in/" + page);
			} else {
				webview.loadUrl("file:///android_asset/help/html-en/" + page);
			}
		}

		webview.setWebViewClient(new WebViewClient() {
			@Override
			public boolean shouldOverrideUrlLoading(WebView view, String url) {
				final Uri uri = Uri.parse(url);
				final String scheme = uri.getScheme();

				if ("http".equals(scheme) || "https".equals(scheme)) {
					// open in external browser
					final Intent intent = new Intent(Intent.ACTION_VIEW);
					intent.setData(uri);
					startActivity(intent);
					return true;
				}

				if ("bible".equals(scheme)) {
					// try to decode using OSIS format
					final String ssp = uri.getSchemeSpecificPart();
					final IntArrayList ariRanges = TargetDecoder.decode("o:" + ssp);
					if (ariRanges == null || ariRanges.size() == 0) {
						new AlertDialog.Builder(HelpActivity.this)
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
								final Intent intent = Launcher.openAppAtBibleLocation(ari);
								startActivity(intent);
							}
						});
					}
					return true;
				}
				return false;
			}
		});
	}

	@Override
	public void onBackPressed() {
		if (webview.canGoBack()) {
			webview.goBack();
		} else {
			super.onBackPressed();
		}
	}

	View.OnClickListener bOk_click = new View.OnClickListener() {
		@Override public void onClick(View v) {
			startActivity(new Intent(App.context, com.example.android.wizardpager.MainActivity.class));
			finish();
		}
	};

	View.OnClickListener bCancel_click = new View.OnClickListener() {
		@Override public void onClick(View v) {
			finish();
		}
	};
}
