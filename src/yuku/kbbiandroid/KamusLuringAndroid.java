package yuku.kbbiandroid;

import java.io.*;
import java.util.*;

import android.content.res.*;
import android.util.*;

public class KamusLuringAndroid {
	private char awalAktif_ = 0;
	private ArrayList<String> df_daftar_;
	private int df_index_;
	private int arti_aktif_ = -1;
	private ArrayList<String> arti_daftar_;
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

	public synchronized String arti(String kata) {
		kata = kata.trim().toLowerCase();

		// siapin kata di df_daftar_
		df_siapin(kata);
		int indexKetemu = -1;

		final ArrayList<String> _df_daftar = df_daftar_; // cache
		int i = Collections.binarySearch(_df_daftar, kata);
		if (i >= 0) {
			indexKetemu = df_index_ + i;
		} else {
			// bin search gagal, pake cara kuno deh.
			for (int j = 0; j < _df_daftar.size(); j++) {
				if (kata.equals(_df_daftar.get(j))) {
					indexKetemu = df_index_ + j;
				}
			}
		}

		if (indexKetemu == -1) {
			return "";
		}

		return arti_ambil(indexKetemu);
	}

	/**
	 * Akan ngambil arti, dan sebelah2nya juga
	 * 
	 * @param ketemu
	 *            index global
	 * @return
	 */
	private synchronized String arti_ambil(int ketemu) {
		int db = ketemu / 500; // harus 500! karena pemecahan file arti tiap 500 definisi

		try {
			if (db != arti_aktif_) {
				Log.d("kamus", "arti_ambil mulai");
				arti_aktif_ = db;

				Utf8Reader in = new Utf8Reader(new BufferedInputStream(resources_.openRawResource(getR_arti(db)), 256*1024));
				char[] bufArti = new char[65535];
				int i = 0;
				arti_daftar_ = new ArrayList<String>(1000);
				final ArrayList<String> _arti_daftar = arti_daftar_;

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
				in.close();
				Log.d("kamus", "arti_ambil selesai");
			}
		} catch (IOException e) {
			Log.e("kamus", "IOException di arti_ambil", e);
		}

		return arti_daftar_.get(ketemu - db * 500);
	}

	private void df_siapin(String cari) {
		try {
			// Debug.startMethodTracing("df_siapin");

			char awal = cari.charAt(0);
			if (awal != awalAktif_) {
				Log.d("kamus", "df_siapin mulai");

				awalAktif_ = awal;

				Utf8Reader in = new Utf8Reader(new BufferedInputStream(resources_.openRawResource(getR_df(awal)), 8192));
				char[] bufKata = new char[80]; // max len kata = 80
				int i = 0;

				df_daftar_ = new ArrayList<String>(1000);
				ArrayList<String> _df_daftar = df_daftar_;
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
				in.close();

				Log.d("kamus", "df_siapin selesai");
			}
		} catch (IOException e) {
			Log.e("kamus", "IOException di df_siapin", e);
		} finally {
			// Debug.stopMethodTracing();
		}
	}

	public String[] kandidat(String cari, int max) {
		cari = cari.trim().toLowerCase();

		if (cari == null || cari.length() == 0) {
			return new String[0];
		}
		if (cari.charAt(0) < 'a' || cari.charAt(0) > 'z') {
			return new String[0];
		}

		df_siapin(cari);

		ArrayList<String> ret = new ArrayList<String>();

		for (int i = 0; i < df_daftar_.size(); i++) {
			String contoh = df_daftar_.get(i);
			if (contoh.startsWith(cari)) {
				ret.add(contoh);
				if (ret.size() >= max) {
					break;
				}
			}
		}

		String[] ret2 = new String[ret.size()];
		ret.toArray(ret2);

		return ret2;
	}
}
