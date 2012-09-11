package yuku.alkitab.base.ac;

import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;

import yuku.alkitab.R;
import yuku.alkitab.base.ac.base.BaseActivity;

public class HelpActivity extends BaseActivity {
	WebView webview;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		webview = new WebView(this);
		WebSettings webSettings = webview.getSettings();
		webSettings.setSavePassword(false);
		webSettings.setSaveFormData(false);
		webSettings.setJavaScriptEnabled(false);
		webSettings.setSupportZoom(true);
		webSettings.setBuiltInZoomControls(true);
		
		setContentView(webview);
		setTitle(R.string.bantuan_judul);
		
		webview.loadUrl(getString(R.string.url_help_index));
	}
}
