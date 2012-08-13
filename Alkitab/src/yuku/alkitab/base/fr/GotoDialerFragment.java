package yuku.alkitab.base.fr;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.Arrays;
import java.util.Comparator;

import yuku.afw.V;
import yuku.alkitab.R;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.fr.base.BaseFragment;
import yuku.alkitab.base.model.Book;
import yuku.alkitab.base.storage.Preferences;

public class GotoDialerFragment extends BaseFragment {
	public static final String TAG = GotoDialerFragment.class.getSimpleName();
	public static final String EXTRA_verse = "ayat"; //$NON-NLS-1$
	public static final String EXTRA_chapter = "pasal"; //$NON-NLS-1$
	public static final String EXTRA_kitab = "kitab"; //$NON-NLS-1$

	TextView aktif;
	TextView pasif;

	Button bOk;
	TextView lPasal;
	View lLabelPasal;
	TextView lAyat;
	View lLabelAyat;
	Spinner cbKitab;

	boolean lPasal_pertamaKali = true;
	boolean lAyat_pertamaKali = true;

	int maxPasal = 0;
	int maxAyat = 0;
	KitabAdapter adapter;

	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View res = inflater.inflate(R.layout.fragment_goto_dialer, container, false);
		
		bOk = V.get(res, R.id.bOk);
		lPasal = V.get(res, R.id.lPasal);
		lLabelPasal = V.get(res, R.id.lLabelPasal);
		lAyat = V.get(res, R.id.lAyat);
		lLabelAyat = V.get(res, R.id.lLabelAyat);
		cbKitab = V.get(res, R.id.cbKitab);
		cbKitab.setAdapter(adapter = new KitabAdapter());

		lPasal.setOnClickListener(tombolPasalListener);
		if (lLabelPasal != null) { // not always present in layout
			lLabelPasal.setOnClickListener(tombolPasalListener);
		}

		lAyat.setOnClickListener(tombolAyatListener);
		if (lLabelAyat != null) { // not always present in layout
			lLabelAyat.setOnClickListener(tombolAyatListener);
		}

		res.findViewById(R.id.bAngka0).setOnClickListener(tombolListener);
		res.findViewById(R.id.bAngka1).setOnClickListener(tombolListener);
		res.findViewById(R.id.bAngka2).setOnClickListener(tombolListener);
		res.findViewById(R.id.bAngka3).setOnClickListener(tombolListener);
		res.findViewById(R.id.bAngka4).setOnClickListener(tombolListener);
		res.findViewById(R.id.bAngka5).setOnClickListener(tombolListener);
		res.findViewById(R.id.bAngka6).setOnClickListener(tombolListener);
		res.findViewById(R.id.bAngka7).setOnClickListener(tombolListener);
		res.findViewById(R.id.bAngka8).setOnClickListener(tombolListener);
		res.findViewById(R.id.bAngka9).setOnClickListener(tombolListener);
		res.findViewById(R.id.bAngkaC).setOnClickListener(tombolListener);
		res.findViewById(R.id.bAngkaPindah).setOnClickListener(tombolListener);

		return res;
	}

	@Override public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		// set kitab, pasal, ayat kini
		cbKitab.setSelection(adapter.getPositionDariPos(S.activeBook.pos));
		cbKitab.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override public void onNothingSelected(AdapterView<?> parent) {}

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
			@Override public void onClick(View v) {
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
//			TODO 	setResult(RESULT_OK, intent);
//
//				finish();
			}
		});

		aktif = lPasal;
		pasif = lAyat;

		{
			// TODO Intent intent = getIntent();
			int pasal = 5; // TODO intent.getIntExtra(EXTRA_pasal, 0);
			if (pasal != 0) {
				lPasal.setText(String.valueOf(pasal));
			}
			int ayat = 17; // TODO intent.getIntExtra(EXTRA_ayat, 0);
			if (ayat != 0) {
				lAyat.setText(String.valueOf(ayat));
			}
		}

		warnain();
	}

	OnClickListener tombolPasalListener = new OnClickListener() {
		@Override public void onClick(View v) {
			aktifin(lPasal, lAyat);
		}
	};
	
	OnClickListener tombolAyatListener = new OnClickListener() {
		@Override public void onClick(View v) {
			aktifin(lAyat, lPasal);
		}
	};

	OnClickListener tombolListener = new OnClickListener() {
		@Override public void onClick(View v) {
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
	

	@Override public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (outState == null) return;
		outState.putString("lPasal.text", lPasal.getText().toString()); //$NON-NLS-1$
		outState.putString("lAyat.text", lAyat.getText().toString()); //$NON-NLS-1$
		outState.putBoolean("lPasal_pertamaKali", lPasal_pertamaKali); //$NON-NLS-1$
		outState.putBoolean("lAyat_pertamaKali", lAyat_pertamaKali); //$NON-NLS-1$
		outState.putInt("cbKitab.selectedItemPosition", cbKitab.getSelectedItemPosition()); //$NON-NLS-1$
		outState.putInt("maxPasal", maxPasal); //$NON-NLS-1$
		outState.putInt("maxAyat", maxAyat); //$NON-NLS-1$
	}

//	TODO (move to oncreate) protected void onRestoreInstanceState(Bundle savedInstanceState) {
//		super.onRestoreInstanceState(savedInstanceState);
//		lPasal.setText(savedInstanceState.getString("lPasal.text")); //$NON-NLS-1$
//		lAyat.setText(savedInstanceState.getString("lAyat.text")); //$NON-NLS-1$
//		lPasal_pertamaKali = savedInstanceState.getBoolean("lPasal_pertamaKali"); //$NON-NLS-1$
//		lAyat_pertamaKali = savedInstanceState.getBoolean("lAyat_pertamaKali"); //$NON-NLS-1$
//		cbKitab.setSelection(savedInstanceState.getInt("cbKitab.selectedItemPosition")); //$NON-NLS-1$
//		maxPasal = savedInstanceState.getInt("maxPasal"); //$NON-NLS-1$
//		maxAyat = savedInstanceState.getInt("maxAyat"); //$NON-NLS-1$
//	}

//	TODO (move to activity) @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
//		if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
//			pencet(String.valueOf((char) ('0' + keyCode - KeyEvent.KEYCODE_0)));
//			return true;
//		} else if (keyCode == KeyEvent.KEYCODE_STAR) {
//			pencet("C"); //$NON-NLS-1$
//			return true;
//		} else if (keyCode == KeyEvent.KEYCODE_POUND) {
//			pencet(":"); //$NON-NLS-1$
//			return true;
//		}
//
//		return super.onKeyDown(keyCode, event);
//	}

	int cobaBacaPasal() {
		try {
			return Integer.parseInt("0" + lPasal.getText().toString()); //$NON-NLS-1$
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	int cobaBacaAyat() {
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
					maxAyat = k.nayat[pasal_1 - 1];
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
					@Override public int compare(Book a, Book b) {
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

		@Override public int getCount() {
			return xkitabc_.length;
		}

		@Override public Book getItem(int position) {
			return xkitabc_[position];
		}

		@Override public long getItemId(int position) {
			return position;
		}

		@Override public View getView(int position, View convertView, ViewGroup viewGroup) {
			TextView res = (TextView) (convertView != null ? convertView : LayoutInflater.from(getActivity()).inflate(android.R.layout.simple_spinner_item, null));
			res.setText(xkitabc_[position].judul);
			return res;
		}

		@Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
			CheckedTextView res = (CheckedTextView) (convertView != null ? convertView : LayoutInflater.from(getActivity()).inflate(android.R.layout.select_dialog_singlechoice, null));

			Book k = getItem(position);
			res.setText(k.judul);
			res.setTextColor(U.getWarnaBerdasarkanKitabPos(k.pos));

			return res;
		}
	}
}
