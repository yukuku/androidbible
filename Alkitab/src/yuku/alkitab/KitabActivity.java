package yuku.alkitab;

import yuku.alkitab.model.*;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.widget.AdapterView.*;

public class KitabActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.pilih_kitab);
		
		S.siapinKitab(getResources());
		
		final ArrayAdapter<Kitab> adapter = new ArrayAdapter<Kitab>(this, R.layout.listitem, R.id.lLabel, S.xkitab);
		
		ListView lsKitab = (ListView) findViewById(R.id.lsKitab);
		lsKitab.setAdapter(adapter);
		
		lsKitab.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Kitab kitab = adapter.getItem(position);
				
				Intent intent = new Intent();
				intent.putExtra("nama", kitab.nama);
				setResult(RESULT_OK, intent);
				
				finish();
			}
		});
	}
}
