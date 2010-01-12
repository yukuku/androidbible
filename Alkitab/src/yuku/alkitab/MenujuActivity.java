package yuku.alkitab;

import yuku.alkitab.model.*;
import android.app.*;
import android.content.*;
import android.os.*;
import android.util.*;
import android.view.*;
import android.view.View.*;
import android.widget.*;

public class MenujuActivity extends Activity {
	TextView aktif;
	TextView pasif;
	
	Button bOk;
	TextView lPasal;
	TextView lAyat;
	Spinner cbKitab;
	
	int maxPasal = 0;
	int maxAyat = 0;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.menuju);
		
		S.siapinEdisi(getResources());
		S.siapinKitab(getResources());
		
		bOk = (Button) findViewById(R.id.bOk);
		lPasal = (TextView) findViewById(R.id.lPasal);
		lAyat = (TextView) findViewById(R.id.lAyat);
		cbKitab = (Spinner) findViewById(R.id.cbKitab);
		cbKitab.setAdapter(S.getKitabAdapter(this));

		// set kitab, pasal, ayat kini
		cbKitab.setSelection(S.kitab.pos);
		
		cbKitab.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}

			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				try {
					Kitab kitab = S.xkitab[position];
					maxPasal = kitab.npasal;
					
					int pasal = cobaBacaPasal();
					maxAyat = kitab.nayat[pasal-1];
				} catch (Exception e) {
					Log.w("alki-menuju", e);
				}
				
				betulinPasalKelebihan();
				betulinAyatKelebihan();
			}
		});
		
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
				
				int kitab = cbKitab.getSelectedItemPosition();
				
				Intent intent = new Intent();
				intent.putExtra("kitab", kitab);
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
	
	private int cobaBacaPasal() {
		try {
			return Integer.parseInt("0" + lPasal.getText().toString());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private int cobaBacaAyat() {
		try {
			return Integer.parseInt("0" + lAyat.getText().toString());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private void betulinAyatKelebihan() {
		int ayat = cobaBacaAyat();
		
		if (ayat > maxAyat) {
			ayat = maxAyat;
		} else if (ayat <= 0) {
			ayat = 1;
		}
		
		lAyat.setText(ayat + "");
	}
	
	private void betulinPasalKelebihan() {
		int pasal = cobaBacaPasal();
			
		if (pasal > maxPasal) {
			pasal = maxPasal;
		} else if (pasal <= 0) {
			pasal = 1;
		}
		
		lPasal.setText(pasal + "");
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
			
			if (aktif == lPasal) {
				aktif.append(s);
				if (cobaBacaPasal() > maxPasal || cobaBacaPasal() <= 0) {
					aktif.setText(s);
				}
				
				try {
					Kitab kitab = S.xkitab[cbKitab.getSelectedItemPosition()];
					int pasal = cobaBacaPasal();
					
					maxAyat = kitab.nayat[pasal-1];
				} catch (Exception e) {
					Log.w("alki-menuju", e);
				}
			} else if (aktif == lAyat) {
				aktif.append(s);
				if (cobaBacaAyat() > maxAyat || cobaBacaAyat() <= 0) {
					aktif.setText(s);
				}
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
