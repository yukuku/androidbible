package yuku.alkitab.base.ac;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import com.afollestad.materialdialogs.MaterialDialog;
import java.io.PrintWriter;
import java.io.StringWriter;
import yuku.alkitab.base.App;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.dialog.VersesDialog;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.base.util.TargetDecoder;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.debug.R;
import yuku.alkitab.util.IntArrayList;
import yuku.alkitabintegration.display.Launcher;

public class HelpActivity extends BaseActivity {
	static final String TAG = HelpActivity.class.getSimpleName();

	private static final String EXTRA_page = "page";
	private static final String EXTRA_overrideTitle = "overrideTitle";
	private static final String EXTRA_overflowMenuItemTitle = "overflowMenuItemTitle";
	private static final String EXTRA_overflowMenuItemIntent = "overflowMenuItemIntent";
	public static final int REQCODE_overflowMenuItem = 1;

	WebView webview;
	View progress;

	String overflowMenuItemTitle;
	Intent overflowMenuItemIntent;

	public static Intent createIntentWithOverflowMenu(final String page, final String overrideTitle, final String overflowMenuItemTitle, final Intent overflowMenuItemIntent) {
		return _createIntent(page, overrideTitle, overflowMenuItemTitle, overflowMenuItemIntent);
	}

	public static Intent createIntent(final String page, final String overrideTitle) {
		return _createIntent(page, overrideTitle, null, null);
	}

	private static Intent _createIntent(final String page, final String overrideTitle, final String overflowMenuItemTitle, final Intent overflowMenuItemIntent) {
		return new Intent(App.context, HelpActivity.class)
			.putExtra(EXTRA_page, page)
			.putExtra(EXTRA_overrideTitle, overrideTitle)
			.putExtra(EXTRA_overflowMenuItemTitle, overflowMenuItemTitle)
			.putExtra(EXTRA_overflowMenuItemIntent, overflowMenuItemIntent);
	}

	@SuppressLint("SetJavaScriptEnabled")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Missing WebView exception sometimes occur when webview is being updated.
		// https://console.firebase.google.com/u/0/project/alkitab-host-hrd/crashlytics/app/android:yuku.alkitab/issues/3f47086cbb547d618eaf6be34bd96407
		try {
			setContentView(R.layout.activity_help);
		} catch (Exception e) {
			final StringWriter buf = new StringWriter();
			e.printStackTrace(new PrintWriter(buf));
			final Intent alert = AlertDialogActivity.createOkIntent("Missing WebView error", "Please try again later.\n\n" + buf.toString());
			startActivity(alert);
			finish();
			return;
		}

		final Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		final ActionBar ab = getSupportActionBar();
		assert ab != null;
		ab.setDisplayHomeAsUpEnabled(true);

		webview = findViewById(R.id.webview);
		progress = findViewById(R.id.progress);

		if (BuildConfig.DEBUG) {
				WebView.setWebContentsDebuggingEnabled(true);
		}

		final WebSettings webSettings = webview.getSettings();
		webSettings.setSavePassword(false);
		webSettings.setSaveFormData(false);
		webSettings.setJavaScriptEnabled(true);

		final String page = getIntent().getStringExtra(EXTRA_page);
		final String overrideTitle = getIntent().getStringExtra(EXTRA_overrideTitle);
		overflowMenuItemTitle = getIntent().getStringExtra(EXTRA_overflowMenuItemTitle);
		overflowMenuItemIntent = getIntent().getParcelableExtra(EXTRA_overflowMenuItemIntent);

		if (overrideTitle != null) {
			setTitle(overrideTitle);
		}

		webview.setVisibility(View.GONE);
		progress.setVisibility(View.VISIBLE);

		if (page != null) {
			if (page.startsWith("http:") || page.startsWith("https:")) {
				webview.loadUrl(page);
			} else {
				webview.loadUrl("file:///android_asset/" + page);
			}
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
					}
					return true;
					case "alkitab": {
						// send back to caller
						final Intent intent = new Intent();
						intent.setData(uri);
						setResult(RESULT_OK, intent);
						finish();
					}
					return true;
					case "suggest":
						startActivity(com.example.android.wizardpager.MainActivity.createIntent(App.context));
						finish();
						return true;
					case "bible":
						// try to decode using OSIS format
						final String ssp = uri.getSchemeSpecificPart();
						final IntArrayList ariRanges = TargetDecoder.decode("o:" + ssp);
						if (ariRanges == null || ariRanges.size() == 0) {
							new MaterialDialog.Builder(HelpActivity.this)
								.content(getString(R.string.alamat_tidak_sah_alamat, url))
								.positiveText(R.string.ok)
								.show();
						} else {
							final VersesDialog dialog = VersesDialog.newInstance(ariRanges);
							dialog.setListener(new VersesDialog.VersesDialogListener() {
								@Override
								public void onVerseSelected(final int ari) {
									AppLog.d(TAG, "Verse link clicked from page");
									startActivity(Launcher.openAppAtBibleLocation(ari));
								}
							});
							dialog.show(getSupportFragmentManager(), "VersesDialog");
						}
						return true;
				}
				return false;
			}

			@Override
			public void onReceivedError(final WebView view, final int errorCode, final String description, final String failingUrl) {
				super.onReceivedError(view, errorCode, description, failingUrl);

				webview.setVisibility(View.VISIBLE);
				progress.setVisibility(View.GONE);
			}

			@Override
			public void onPageFinished(final WebView view, final String url) {
				super.onPageFinished(view, url);

				webview.setVisibility(View.VISIBLE);
				progress.setVisibility(View.GONE);

				if (overrideTitle == null) {
					setTitle(view.getTitle());
				}
			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		if (overflowMenuItemTitle != null) {
			final MenuItem item = menu.add(0, 1, 0, overflowMenuItemTitle);
			item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
		}

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		if (item.getItemId() == 1) {
			startActivityForResult(overflowMenuItemIntent, REQCODE_overflowMenuItem);
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		if (requestCode == REQCODE_overflowMenuItem && resultCode == RESULT_OK) {
			setResult(resultCode, data);
			finish();
		}
	}
}
