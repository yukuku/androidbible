package yuku.alkitab.base.ac;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.Arrays;
import java.util.Comparator;

import yuku.alkitab.R;
import yuku.alkitab.base.IsiActivity;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.model.Book;
import yuku.alkitab.base.storage.Preferences;

public class MenujuActivity extends BaseActivity {
	public static final String TAG = MenujuActivity.class.getSimpleName();
	public static final String EXTRA_verse = "ayat"; //$NON-NLS-1$
	public static final String EXTRA_chapter = "pasal"; //$NON-NLS-1$
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
	KitabAdapter adapter;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		U.enableTitleBarOnlyForHolo(this);
		
		S.prepareBook();
		S.calculateAppliedValuesBasedOnPreferences();
		
		setContentView(R.layout.activity_menuju);
		
		bOk = (Button) findViewById(R.id.bOk);
		lPasal = (TextView) findViewById(R.id.lPasal);
		lLabelPasal = findViewById(R.id.lLabelPasal);
		lAyat = (TextView) findViewById(R.id.lAyat);
		lLabelAyat = findViewById(R.id.lLabelAyat);
		cbKitab = (Spinner) findViewById(R.id.cbKitab);
		cbKitab.setAdapter(adapter = new KitabAdapter());
		bKeLoncat = (ImageButton) findViewById(R.id.bKeLoncat);

		// set kitab, pasal, ayat kini
		cbKitab.setSelection(adapter.getPositionDariPos(S.activeBook.pos));
		
		cbKitab.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}

			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				Book k = adapter.getItem(position);
				maxPasal = k.npasal;
				
				int pasal_0 = cobaBacaPasal() - 1;
				if (pasal_0 >= 0 && pasal_0 < k.npasal) {
					maxAyat = k.nayat[pasal_0];
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
				
				int kitab = adapter.getItem(cbKitab.getSelectedItemPosition()).pos;
				
				Intent intent = new Intent();
				intent.putExtra(EXTRA_kitab, kitab);
				intent.putExtra(EXTRA_chapter, pasal);
				intent.putExtra(EXTRA_verse, ayat);
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
			int pasal = intent.getIntExtra(EXTRA_chapter, 0);
			if (pasal != 0) {
				lPasal.setText(String.valueOf(pasal));
			}
			int ayat = intent.getIntExtra(EXTRA_verse, 0);
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
	
	@Override protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString("lPasal.text", lPasal.getText().toString()); //$NON-NLS-1$
		outState.putString("lAyat.text", lAyat.getText().toString()); //$NON-NLS-1$
		outState.putBoolean("lPasal_pertamaKali", lPasal_pertamaKali); //$NON-NLS-1$
		outState.putBoolean("lAyat_pertamaKali", lAyat_pertamaKali); //$NON-NLS-1$
		outState.putInt("cbKitab.selectedItemPosition", cbKitab.getSelectedItemPosition()); //$NON-NLS-1$
		outState.putInt("maxPasal", maxPasal); //$NON-NLS-1$
		outState.putInt("maxAyat", maxAyat); //$NON-NLS-1$
	}
	
	@Override protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		lPasal.setText(savedInstanceState.getString("lPasal.text")); //$NON-NLS-1$
		lAyat.setText(savedInstanceState.getString("lAyat.text")); //$NON-NLS-1$
		lPasal_pertamaKali = savedInstanceState.getBoolean("lPasal_pertamaKali"); //$NON-NLS-1$
		lAyat_pertamaKali = savedInstanceState.getBoolean("lAyat_pertamaKali"); //$NON-NLS-1$
		cbKitab.setSelection(savedInstanceState.getInt("cbKitab.selectedItemPosition")); //$NON-NLS-1$
		maxPasal = savedInstanceState.getInt("maxPasal"); //$NON-NLS-1$
		maxAyat = savedInstanceState.getInt("maxAyat"); //$NON-NLS-1$
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
	
	int cobaBacaPasal() {
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

	void betulinAyatKelebihan() {
		int ayat = cobaBacaAyat();
		
		if (ayat > maxAyat) {
			ayat = maxAyat;
		} else if (ayat <= 0) {
			ayat = 1;
		}
		
		lAyat.setText(String.valueOf(ayat));
	}
	
	void betulinPasalKelebihan() {
		int pasal = cobaBacaPasal();
			
		if (pasal > maxPasal) {
			pasal = maxPasal;
		} else if (pasal <= 0) {
			pasal = 1;
		}
		
		lPasal.setText(String.valueOf(pasal));
	}

	void pencet(String s) {
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
				
				Book k = adapter.getItem(cbKitab.getSelectedItemPosition());
				int pasal_1 = cobaBacaPasal();
				if (pasal_1 >= 1 && pasal_1 <= k.nayat.length) {
					maxAyat = k.nayat[pasal_1-1];
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

	void aktifin(TextView aktif, TextView pasif) {
		this.aktif = aktif;
		this.pasif = pasif;
		warnain();
	}

	private void warnain() {
		if (aktif != null) aktif.setBackgroundColor(0x803030f0);
		if (pasif != null) pasif.setBackgroundDrawable(null);
	}
	
	private class KitabAdapter extends BaseAdapter {
		Book[] xkitabc_;
		
		public KitabAdapter() {
			Book[] xkitabc = S.activeVersion.getConsecutiveBooks();
			
			if (Preferences.getBoolean(R.string.pref_sortKitabAlfabet_key, R.bool.pref_sortKitabAlfabet_default)) {
				// bikin kopian, supaya ga obok2 array lama
				xkitabc_ = new Book[xkitabc.length];
				System.arraycopy(xkitabc, 0, xkitabc_, 0, xkitabc.length);
				
				// sort!
				Arrays.sort(xkitabc_, new Comparator<Book>() {
					@Override
					public int compare(Book a, Book b) {
						return a.judul.compareToIgnoreCase(b.judul);
					}
				});
			} else {
				xkitabc_ = xkitabc;
			}
		}

		/**
		 * @return 0 kalo ga ada (biar default dan ga eror)
		 */
		public int getPositionDariPos(int pos) {
			for (int i = 0; i < xkitabc_.length; i++) {
				if (xkitabc_[i].pos == pos) {
					return i;
				}
			}
			return 0;
		}

		@Override
		public int getCount() {
			return xkitabc_.length;
		}

		@Override
		public Book getItem(int position) {
			return xkitabc_[position];
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup viewGroup) {
			TextView res = convertView != null? (TextView) convertView: (TextView) LayoutInflater.from(MenujuActivity.this).inflate(android.R.layout.simple_spinner_item, null);
			res.setText(xkitabc_[position].judul);
			return res;
		}
		
		@Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
			CheckedTextView res = convertView != null? (CheckedTextView) convertView: (CheckedTextView) LayoutInflater.from(MenujuActivity.this).inflate(android.R.layout.select_dialog_singlechoice, null);

			Book k = getItem(position);
			res.setText(k.judul);
			res.setTextColor(U.getWarnaBerdasarkanKitabPos(k.pos));
			
			return res;
		}
	}
}
