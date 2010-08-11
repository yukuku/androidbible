package yuku.alkitab;

import yuku.alkitab.model.Edisi;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;

public class EdisiActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.pilih_edisi);
		
		S.siapinEdisi(getApplicationContext());
		
		final ArrayAdapter<Edisi> adapter = new ArrayAdapter<Edisi>(this, R.layout.listitem, R.id.lLabel, S.xedisi);
		
		ListView lsEdisi = (ListView) findViewById(R.id.lsEdisi);
		lsEdisi.setAdapter(adapter);
		
		lsEdisi.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Edisi edisi = adapter.getItem(position);
				
				Intent intent = new Intent();
				intent.putExtra("nama", edisi.nama);
				setResult(RESULT_OK, intent);
				
				finish();
			}
		});
	}
}
