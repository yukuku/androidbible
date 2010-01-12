package yuku.kbbiandroid;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.content.pm.PackageManager.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.View.*;
import android.view.inputmethod.*;
import android.widget.*;

public class LayarCari extends Activity {
	Button bCari;
	EditText tCari;
	
	public LayarCari() {
		S.layarCari = this;
	}
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        S.kamus = new KamusLuringAndroid(getResources());
        
        setTitle(R.string.kbbiAndroid_n);
        
        bCari = (Button) findViewById(R.id.bCari);
        tCari = (EditText) findViewById(R.id.tCari);
        
        bCari.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				bCari_click();
			}
		});
        
        tCari.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_NULL) {
					bCari_click();
					return true;
				}
				return false;
			}
		});
    }
    
    private void bCari_click() {
		String cari = tCari.getText().toString().trim();
		
		if (cari.length() > 0) {
			Intent intent = new Intent(LayarCari.this, LayarPilih.class);
			intent.putExtra("cari", cari);
			startActivity(intent);
		}
	}

	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	menu.add(Menu.NONE, Menu.NONE, Menu.NONE, getString(R.string.tentang_n));
    	
    	return super.onCreateOptionsMenu(menu);
    }
    
    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
    	String verName = "null";
    	int verCode = -1;
    	
		try {
			PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
			verName = packageInfo.versionName;
			verCode = packageInfo.versionCode;
		} catch (NameNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    	new AlertDialog.Builder(this).setTitle(getString(R.string.tentang_n))
    	.setMessage(Html.fromHtml(getString(R.string.tentangMessage_s, verName, verCode)))
    	.setPositiveButton(R.string.kembali_v, null)
    	.show();
    	
    	return true;
    }
}
