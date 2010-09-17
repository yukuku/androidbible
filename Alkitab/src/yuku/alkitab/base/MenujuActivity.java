package yuku.alkitab.base;

import java.util.*;

import yuku.alkitab.R;
import yuku.alkitab.base.model.Kitab;
import android.app.Activity;
import android.content.Intent;
import android.os.*;
import android.util.Log;
import android.view.*;
import android.view.View.OnClickListener;
import android.widget.*;

public class MenujuActivity extends Activity {
	public static final String TAG = MenujuActivity.class.getSimpleName();
	public static final String EXTRA_ayat = "ayat"; //$NON-NLS-1$
	public static final String EXTRA_pasal = "pasal"; //$NON-NLS-1$
	public static final String EXTRA_kitab = "kitab"; //$NON-NLS-1$

	TextView aktif;
	TextView pasif;
	
	Button bOk;
	TextView lPasal;
	boolean lPasal_pertamaKali = true;
	View lLabelPasal;
	TextView lAyat;
	boolean lAyat_pertamaKali = true;
	View lLabelAyat;
	Spinner cbKitab;
	ImageButton bKeLoncat;
	
	int maxPasal = 0;
	int maxAyat = 0;
	KitabAdapter kitabAdapter;
	Handler handler = new Handler();
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		
		S.siapinEdisi(getApplicationContext());
		S.siapinKitab(getApplicationContext());
		S.bacaPengaturan(this);
		S.terapkanPengaturanBahasa(this, handler, 2);
		S.siapinPengirimFidbek(this);
		
		setContentView(R.layout.menuju);
		
		bOk = (Button) findViewById(R.id.bOk);
		lPasal = (TextView) findViewById(R.id.lPasal);
		lLabelPasal = findViewById(R.id.lLabelPasal);
		lAyat = (TextView) findViewById(R.id.lAyat);
		lLabelAyat = findViewById(R.id.lLabelAyat);
		cbKitab = (Spinner) findViewById(R.id.cbKitab);
		kitabAdapter = new KitabAdapter(S.edisiAktif.volatile_xkitab);
		cbKitab.setAdapter(kitabAdapter);
		bKeLoncat = (ImageButton) findViewById(R.id.bKeLoncat);

		// set kitab, pasal, ayat kini
		cbKitab.setSelection(kitabAdapter.getPositionDariPos(S.kitabAktif.pos));
		
		cbKitab.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}

			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				try {
					Kitab kitab = S.edisiAktif.volatile_xkitab[(int) id];
					maxPasal = kitab.npasal;
					
					int pasal = cobaBacaPasal();
					maxAyat = kitab.nayat[pasal-1];
				} catch (Exception e) {
					Log.w(TAG, e);
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
				
				// itemid yang menentukan pos
				int kitab = (int) cbKitab.getSelectedItemId();
				
				Intent intent = new Intent();
				intent.putExtra(EXTRA_kitab, kitab);
				intent.putExtra(EXTRA_pasal, pasal);
				intent.putExtra(EXTRA_ayat, ayat);
				setResult(RESULT_OK, intent);
				
				finish();
			}
		});
		
		OnClickListener tombolPasalListener = new OnClickListener() {
			@Override
			public void onClick(View v) {
				aktifin(lPasal, lAyat);
			}
		};
		lPasal.setOnClickListener(tombolPasalListener);
		if (lLabelPasal != null) {
			lLabelPasal.setOnClickListener(tombolPasalListener);
		}
		
		OnClickListener tombolAyatListener = new OnClickListener() {
			@Override
			public void onClick(View v) {
				aktifin(lAyat, lPasal);
			}
		};
		lAyat.setOnClickListener(tombolAyatListener);
		if (lLabelAyat != null) {
			lLabelAyat.setOnClickListener(tombolAyatListener);
		}
		
		aktif = lPasal;
		pasif = lAyat;
		
		{
			Intent intent = getIntent();
			int pasal = intent.getIntExtra(EXTRA_pasal, 0);
			if (pasal != 0) {
				lPasal.setText(String.valueOf(pasal));
			}
			int ayat = intent.getIntExtra(EXTRA_ayat, 0);
			if (ayat != 0) {
				lAyat.setText(String.valueOf(ayat));
			}
		}
		
		OnClickListener tombolListener = new OnClickListener() {
			@Override
			public void onClick(View v) {
				int id = v.getId();
				if (id == R.id.bAngka0) pencet("0"); //$NON-NLS-1$
				if (id == R.id.bAngka1) pencet("1"); //$NON-NLS-1$
				if (id == R.id.bAngka2) pencet("2"); //$NON-NLS-1$
				if (id == R.id.bAngka3) pencet("3"); //$NON-NLS-1$
				if (id == R.id.bAngka4) pencet("4"); //$NON-NLS-1$
				if (id == R.id.bAngka5) pencet("5"); //$NON-NLS-1$
				if (id == R.id.bAngka6) pencet("6"); //$NON-NLS-1$
				if (id == R.id.bAngka7) pencet("7"); //$NON-NLS-1$
				if (id == R.id.bAngka8) pencet("8"); //$NON-NLS-1$
				if (id == R.id.bAngka9) pencet("9"); //$NON-NLS-1$
				if (id == R.id.bAngkaC) pencet("C"); //$NON-NLS-1$
				if (id == R.id.bAngkaPindah) pencet(":"); //$NON-NLS-1$
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
		
		bKeLoncat.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setResult(IsiActivity.RESULT_pindahCara);
				finish();
			}
		});
		
		warnain();
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
			pencet(String.valueOf((char)('0' + keyCode - KeyEvent.KEYCODE_0)));
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_STAR) {
			pencet("C"); //$NON-NLS-1$
			return true;
		} else if (keyCode == KeyEvent.KEYCODE_POUND) {
			pencet(":"); //$NON-NLS-1$
			return true;
		}

		return super.onKeyDown(keyCode, event);
	}
	
	private int cobaBacaPasal() {
		try {
			return Integer.parseInt("0" + lPasal.getText().toString()); //$NON-NLS-1$
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private int cobaBacaAyat() {
		try {
			return Integer.parseInt("0" + lAyat.getText().toString()); //$NON-NLS-1$
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
		
		lAyat.setText(String.valueOf(ayat));
	}
	
	private void betulinPasalKelebihan() {
		int pasal = cobaBacaPasal();
			
		if (pasal > maxPasal) {
			pasal = maxPasal;
		} else if (pasal <= 0) {
			pasal = 1;
		}
		
		lPasal.setText(String.valueOf(pasal));
	}

	private void pencet(String s) {
		if (aktif != null) {
			if (s.equals("C")) { //$NON-NLS-1$
				aktif.setText(""); //$NON-NLS-1$
				return;
			} else if (s.equals(":")) { //$NON-NLS-1$
				if (pasif != null) {
					aktifin(pasif, aktif);
				}
				return;
			}
			
			if (aktif == lPasal) {
				if (lPasal_pertamaKali) {
					aktif.setText(s);
					lPasal_pertamaKali = false;
				} else {
					aktif.append(s);
				}
				
				if (cobaBacaPasal() > maxPasal || cobaBacaPasal() <= 0) {
					aktif.setText(s);
				}
				
				try {
					Kitab kitab = S.edisiAktif.volatile_xkitab[(int) cbKitab.getSelectedItemId()];
					int pasal = cobaBacaPasal();
					
					maxAyat = kitab.nayat[pasal-1];
				} catch (Exception e) {
					Log.w(TAG, e);
				}
			} else if (aktif == lAyat) {
				if (lAyat_pertamaKali) {
					aktif.setText(s);
					lAyat_pertamaKali = false;
				} else {
					aktif.append(s);
				}
				
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
	
	private class KitabAdapter extends BaseAdapter {
		Kitab[] xkitab_;
		
		public KitabAdapter(Kitab[] xkitab) {
			if (!S.penerapan.sortKitabAlfabet) {
				xkitab_ = xkitab;
			} else {
				xkitab_ = new Kitab[xkitab.length];
				System.arraycopy(xkitab, 0, xkitab_, 0, xkitab.length);
				
				// sort!
				Arrays.sort(xkitab_, new Comparator<Kitab>() {
					@Override
					public int compare(Kitab a, Kitab b) {
						return a.judul.compareTo(b.judul);
					}
				});
			}
		}

		/**
		 * @return 0 kalo ga ada (biar default dan ga eror)
		 */
		public int getPositionDariPos(int pos) {
			for (int i = 0; i < xkitab_.length; i++) {
				if (xkitab_[i].pos == pos) {
					return i;
				}
			}
			return 0;
		}

		@Override
		public int getCount() {
			return xkitab_.length;
		}

		@Override
		public Object getItem(int position) {
			return xkitab_[position];
		}

		@Override
		public long getItemId(int position) {
			return xkitab_[position].pos;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup viewGroup) {
			TextView res = (TextView) LayoutInflater.from(MenujuActivity.this).inflate(android.R.layout.simple_spinner_item, null);
			res.setText(xkitab_[position].judul);
						
			return res;
		}
		
		@Override
		public View getDropDownView(int position, View convertView, ViewGroup parent) {
			CheckedTextView res;
			
			if (convertView == null) {
				res = (CheckedTextView) LayoutInflater.from(MenujuActivity.this).inflate(android.R.layout.simple_spinner_dropdown_item, null);
			} else {
				res = (CheckedTextView) convertView;
			}
			
			res.setText(xkitab_[position].judul);
						
			return res;
		}
	}
}
