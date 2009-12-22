package yuku.alkitab;

import yuku.alkitab.model.*;
import android.app.*;
import android.os.*;
import android.widget.*;

public class EdisiActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.pilih_edisi);
		
		S.siapinEdisi(getResources());
		
		ArrayAdapter<Edisi> adapter = new ArrayAdapter<Edisi>(this, R.layout.listitem, R.id.lLabel, S.xedisi);
		
		ListView lsEdisi = (ListView) findViewById(R.id.lsEdisi);
		lsEdisi.setAdapter(adapter);
	}
}
