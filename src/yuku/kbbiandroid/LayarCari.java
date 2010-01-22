package yuku.kbbiandroid;

import java.io.*;

import yuku.laporpakai.*;
import android.app.*;
import android.content.*;
import android.os.*;
import android.util.*;
import android.view.*;
import android.view.View.*;
import android.view.inputmethod.*;
import android.widget.*;

public class LayarCari extends Activity {
	Button bCari;
	EditText tCari;
	CommonMenuHandler commonMenuHandler;
	
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
        
        laporPakai();
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
		new MenuInflater(this).inflate(R.menu.utama, menu);
    	
    	return super.onCreateOptionsMenu(menu);
    }
    
    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
    	if (commonMenuHandler == null) commonMenuHandler = new CommonMenuHandler(this);
    	return commonMenuHandler.handle(featureId, item);
    }
    
    private void laporPakai() {
    	Thread thread = new Thread(new Runnable() {
			@Override
			public void run() {
				Params params = PengumpulData.getInformation(LayarCari.this);
				params.put("extra/activity", LayarCari.class.getName());
				params.put("extra/aksi", "onCreate");
				
				try {
					Log.d("PengirimData", "mulai post");
					byte[] response = PengirimData.postData(params, getString(R.string.laporPakai_url));
					Log.d("PengirimData", "response = " + new String(response));
				} catch (IOException e) {
					Log.i("PengirimData", "gagal post", e);
				}
			}
		});
    	thread.setPriority(Thread.MIN_PRIORITY);
    	thread.start();
    }
}
