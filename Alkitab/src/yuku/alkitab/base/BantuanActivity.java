package yuku.alkitab.base;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.*;
import yuku.alkitab.R;

public class BantuanActivity extends Activity {
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
		
		webview.loadUrl(getString(R.string.url_help_index));
	}
}
