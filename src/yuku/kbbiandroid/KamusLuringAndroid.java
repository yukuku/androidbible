package yuku.kbbiandroid;

import java.io.*;
import java.util.*;

import android.content.res.*;
import android.util.*;

public class KamusLuringAndroid extends Kamus {
	private char awalAktif_ = 0;
	private Vector<String> df_daftar_;
	private int df_index_;
	private int arti_aktif_ = -1;
	private Vector<String> arti_daftar_;
	private Resources resources_;

	public KamusLuringAndroid(Resources resources) {
		resources_ = resources;
	}

	private int getR_df(char depan) {
		int a = R.raw.df_a;

		return a + (depan - 'a');
	}

	private int getR_arti(int ke) {
		int nol = R.raw.arti_00;

		return nol + (ke - 0);
	}

	@Override
	public String arti(String kata) {
		kata = kata.trim().toLowerCase();
		df_siapin(kata);
		int ketemu = -1;
		
		Vector<String> _df_daftar = df_daftar_;

		for (int i = 0; i < _df_daftar.size(); i++) {
			String contoh = _df_daftar.get(i);
			if (contoh.equals(kata)) {
				ketemu = df_index_ + i;
				break;
			}
		}

		if (ketemu == -1) {
			return "";
		}

		return arti_ambil(ketemu);
	}

	private String arti_ambil(int ketemu) {
		int db = ketemu / 500; // harus 500! karena pemecahan file arti tiap 500 definisi
		
		if (db != arti_aktif_) {
			arti_aktif_ = db;
			
			try {
				Utf8Reader in = null;
				try {
					in = new Utf8Reader(new BufferedInputStream(resources_.openRawResource(getR_arti(db))));
					char[] bufArti = new char[65535];
					int i = 0;
					arti_daftar_ = new Vector<String>(1000);
					final Vector<String> _arti_daftar = arti_daftar_;

					while (true) {
						int c = in.read();
						if (c < 0) break;
						if (c != '\n') {
							bufArti[i++] = (char) c;
						} else {
							_arti_daftar.add(new String(bufArti, 0, i));
							i = 0;
						}
					}
				} finally {
					in.close();
				}
			} catch (IOException e) {
				Log.e("kamus", "IOException di arti_ambil", e);
			}
		}

		return arti_daftar_.get(ketemu - db * 500);
	}

	private void df_siapin(String cari) {
		try {
			//Debug.startMethodTracing("df_siapin");
			
			char awal = cari.charAt(0);
			if (awal != awalAktif_) {
				awalAktif_ = awal;

				try {
					Utf8Reader in = null;
					try {
						in = new Utf8Reader(new BufferedInputStream(resources_.openRawResource(getR_df(awal))));
						char[] bufKata = new char[80];
						int i = 0;

						df_daftar_ = new Vector<String>(1000);
						Vector<String> _df_daftar = df_daftar_;
						int _df_index = df_index_ = -1;

						while (true) {
							int c = in.read();
							if (c < 0) break;
							if (c != '\n') {
								bufKata[i++] = (char) c;
							} else {
								if (_df_index == -1) {
									_df_index = Integer.parseInt(new String(bufKata, 0, i));
								} else {
									_df_daftar.add(new String(bufKata, 0, i));
								}
								i = 0;
							}
						}

						df_index_ = _df_index;
					} finally {
						in.close();
					}
				} catch (IOException e) {
					Log.e("kamus", "IOException di df_ambil", e);
				}
			}
		} finally {
			//Debug.stopMethodTracing();
		}
	}

	@Override
	public String[] kandidat(String cari) {
		cari = cari.trim().toLowerCase();

		if (cari == null || cari.length() == 0) {
			return new String[0];
		}
		if (cari.charAt(0) < 'a' || cari.charAt(0) > 'z') {
			return new String[0];
		}

		df_siapin(cari);

		Vector<String> ret = new Vector<String>();

		for (int i = 0; i < df_daftar_.size(); i++) {
			String contoh = df_daftar_.elementAt(i);
			if (contoh.startsWith(cari)) {
				ret.addElement(contoh);
			}
		}

		String[] ret2 = new String[ret.size()];
		ret.copyInto(ret2);

		return ret2;
	}

}
