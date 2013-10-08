package yuku.alkitab.base.ac;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;

import yuku.afw.App;
import yuku.afw.V;
import yuku.alkitab.debug.R;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.base.BaseActivity;

public class HelpActivity extends BaseActivity {
	private static final String EXTRA_isFaq = "isFaq";
	
	WebView webview;
	View bOk;
	View bCancel;
	
	boolean isFaq;
	
	public static Intent createIntent(boolean isFaq) {
		Intent res = new Intent(App.context, HelpActivity.class);
		res.putExtra(EXTRA_isFaq, isFaq);
		return res;
	}
	
	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_help);
		setTitle(R.string.bantuan_judul);
		
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
			panelFaqOnly.setVisibility(View.GONE);
		}

		String page = isFaq? "faq.html": "index.html";
		if (U.equals("in", getResources().getConfiguration().locale.getLanguage())) {
			webview.loadUrl("file:///android_asset/help/html-in/" + page);
		} else {
			webview.loadUrl("file:///android_asset/help/html-en/" + page);
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
