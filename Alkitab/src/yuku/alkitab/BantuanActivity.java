package yuku.alkitab;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.*;

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
		
		webview.loadUrl("file:///android_asset/help/html-in/index.html");
	}
}
