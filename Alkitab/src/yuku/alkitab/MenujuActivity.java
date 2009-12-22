package yuku.alkitab;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.view.View.*;
import android.widget.*;

public class MenujuActivity extends Activity {
	TextView aktif;
	TextView pasif;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.menuju);
		
		S.siapinEdisi(getResources());
		S.siapinKitab(getResources());
		
		final Button bOk = (Button) findViewById(R.id.bOk);
		
		final TextView lPasal = (TextView) findViewById(R.id.lPasal);
		final TextView lAyat = (TextView) findViewById(R.id.lAyat);
		
		bOk.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				int pasal = 0;
				int ayat = 0;
				
				try {
					pasal = Integer.parseInt(lPasal.getText().toString());
					ayat = Integer.parseInt(lAyat.getText().toString());
				} catch (NumberFormatException e) {
					// biarin 0 aja
				}
				
				Intent intent = new Intent();
				intent.putExtra("pasal", pasal);
				intent.putExtra("ayat", ayat);
				setResult(RESULT_OK, intent);
				
				finish();
			}
		});
		
		lPasal.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				aktifin(lPasal, lAyat);
			}
		});
		
		lAyat.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				aktifin(lAyat, lPasal);
			}
		});
		
		aktif = lPasal;
		pasif = lAyat;
		
		{
			Intent intent = getIntent();
			int pasal = intent.getIntExtra("pasal", 0);
			if (pasal != 0) {
				lPasal.setText("" + pasal);
			}
			int ayat = intent.getIntExtra("ayat", 0);
			if (ayat != 0) {
				lAyat.setText("" + ayat);
			}
		}
		
		OnClickListener tombolListener = new OnClickListener() {
			@Override
			public void onClick(View v) {
				int id = v.getId();
				if (id == R.id.bAngka0) pencet("0");
				if (id == R.id.bAngka1) pencet("1");
				if (id == R.id.bAngka2) pencet("2");
				if (id == R.id.bAngka3) pencet("3");
				if (id == R.id.bAngka4) pencet("4");
				if (id == R.id.bAngka5) pencet("5");
				if (id == R.id.bAngka6) pencet("6");
				if (id == R.id.bAngka7) pencet("7");
				if (id == R.id.bAngka8) pencet("8");
				if (id == R.id.bAngka9) pencet("9");
				if (id == R.id.bAngkaC) pencet("C");
				if (id == R.id.bAngkaPindah) pencet(":");
			}
		};
		
		findViewById(R.id.bAngka0).setOnClickListener(tombolListener);
		findViewById(R.id.bAngka1).setOnClickListener(tombolListener);
		findViewById(R.id.bAngka2).setOnClickListener(tombolListener);
		findViewById(R.id.bAngka3).setOnClickListener(tombolListener);
		findViewById(R.id.bAngka4).setOnClickListener(tombolListener);
		findViewById(R.id.bAngka5).setOnClickListener(tombolListener);
		findViewById(R.id.bAngka6).setOnClickListener(tombolListener);
		findViewById(R.id.bAngka7).setOnClickListener(tombolListener);
		findViewById(R.id.bAngka8).setOnClickListener(tombolListener);
		findViewById(R.id.bAngka9).setOnClickListener(tombolListener);
		findViewById(R.id.bAngkaC).setOnClickListener(tombolListener);
		findViewById(R.id.bAngkaPindah).setOnClickListener(tombolListener);
		
		warnain();
	}

	private void pencet(String s) {
		if (aktif != null) {
			if (s.equals("C")) {
				aktif.setText("");
				return;
			} else if (s.equals(":")) {
				if (pasif != null) {
					aktifin(pasif, aktif);
				}
				return;
			}
			
			try {
				Integer.parseInt(aktif.getText().toString());
				aktif.append(s);
			} catch (NumberFormatException e) {
				aktif.setText(s);
			}
		}
	}

	private void aktifin(TextView aktif, TextView pasif) {
		this.aktif = aktif;
		this.pasif = pasif;
		warnain();
	}

	private void warnain() {
		if (aktif != null) aktif.setBackgroundColor(0x803030f0);
		if (pasif != null) pasif.setBackgroundDrawable(null);
	}
}
