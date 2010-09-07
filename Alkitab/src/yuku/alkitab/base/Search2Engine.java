package yuku.alkitab.base;

import java.util.*;

import yuku.alkitab.base.model.*;
import yuku.andoutil.IntArrayList;
import android.content.Context;
import android.graphics.Typeface;
import android.text.*;
import android.text.style.*;
import android.util.Log;

public class Search2Engine {
	private static final String TAG = Search2Engine.class.getSimpleName();

	static String[] tokenkan(String carian) {
		// pisah jadi kata-kata
		String[] xkata = carian.trim().toLowerCase().replaceAll("\\s+", " ").split(" "); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		
		return xkata;
	}

	static IntArrayList cari(Context context, String[] xkata, boolean filter_lama, boolean filter_baru) {
		// urutkan berdasarkan panjang, lalu abjad
		Arrays.sort(xkata, new Comparator<String>() {
			@Override
			public int compare(String object1, String object2) {
				int len1 = object1.length();
				int len2 = object2.length();
				
				if (len1 > len2) return -1;
				if (len1 == len2) {
					return object1.compareTo(object2);
				}
				return 1;
			}
		});
		
		// buang ganda
		{
			ArrayList<String> akata = new ArrayList<String>();
			String terakhir = null;
			for (String kata: xkata) {
				if (!kata.equals(terakhir)) {
					akata.add(kata);
				}
				terakhir = kata;
			}
			xkata = akata.toArray(new String[akata.size()]);
			Log.d(TAG, "xkata = " + Arrays.toString(xkata)); //$NON-NLS-1$
		}
		
		// cari betulan
		IntArrayList hasil = null;
		
		{
			int index = 0;
			
			while (true) {
				if (index >= xkata.length) {
					break;
				}
				
				String kata = xkata[index];
				
				//Debug.startMethodTracing();
				IntArrayList lama = hasil;
	
				{
					long ms = System.currentTimeMillis();
					hasil = cariDalam(context, kata, lama, 10000, filter_lama, filter_baru);
					Log.d(TAG, "cari kata '" + kata + "' pake waktu: " + (System.currentTimeMillis() - ms) + " ms"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				}
	
				if (lama != null) {
					Log.d(TAG, "Akan mengiris " + lama.size() + " elemen dengan " + hasil.size() + " elemen..."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					hasil = iris(lama, hasil);
					Log.d(TAG, "... hasilnya " + hasil.size() + " elemen");  //$NON-NLS-1$//$NON-NLS-2$
				}
				//Debug.stopMethodTracing();
				
				index++;
			}
		}
		
		return hasil;
	}

	private static IntArrayList iris(IntArrayList a, IntArrayList b) {
		IntArrayList res = new IntArrayList(a.size());
		
		int[] aa = a.buffer();
		int[] bb = b.buffer();
		int alen = a.size();
		int blen = b.size();
		
		int apos = 0;
		int bpos = 0;
		
		while (true) {
			if (apos >= alen) break;
			if (bpos >= blen) break;
			
			int av = aa[apos];
			int bv = bb[bpos];
			
			if (av == bv) {
				res.add(av);
				apos++;
				bpos++;
				continue;
			} else if (av > bv) {
				bpos++;
				continue;
			} else { // av < bv
				apos++;
				continue;
			}
		}
		
		return res;
	}

	/**
	 * Keluarkan ari (kitab pasal saja) berikutnya setelah ariKpTerakhir dengan ngesken sumber mulai pos.
	 * @param ppos pointer ke pos. pos akan berubah jadi SATU SETELAH POSISI KETEMU. Jadi jangan ++ lagi di luar sini.
	 */
	private static int ariBerikutnya(IntArrayList sumber, int[] ppos, int ariKpTerakhir) {
		int[] s = sumber.buffer();
		int len = sumber.size();
		int pos = ppos[0];
		
		while (true) {
			if (pos >= len) return 0x0;
			
			int ariIni = s[pos];
			int ariKpIni = Ari.toKitabPasal(ariIni);
			
			if (ariKpIni != ariKpTerakhir) {
				// ketemu!
				pos++;
				ppos[0] = pos;
				return ariKpIni;
			} else {
				// masih sama, maju.
				pos++;
			}
		}
	}

	static IntArrayList cariDalam(Context context, String kata, IntArrayList sumber, int max, boolean filter_lama, boolean filter_baru) {
		IntArrayList res = new IntArrayList();
	
		if (sumber == null) {
			for (Kitab k: S.edisiAktif.volatile_xkitab) {
				//# filter dulu
				if (!filter_lama) {
					if (k.pos >= 0 && k.pos <= 38) {
						continue;
					}
				}
				if (!filter_baru) {
					if (k.pos >= 39 && k.pos <= 65) {
						continue;
					}
				}
				
				int npasal = k.npasal;
				
				for (int pasal_1 = 1; pasal_1 <= npasal; pasal_1++) {
					// coba sepasal sekaligus dulu.
					String sepasal = S.muatTeksJanganPisahAyatHurufKecil(context, S.edisiAktif, k, pasal_1);
					if (sepasal.indexOf(kata) >= 0) {
						// hanya lakukan ini jika dalam sepasal kedetek ada kata
						cariDalamSepasal(sepasal, kata, res, Ari.encode(k.pos, pasal_1, 0));
					}
				}
	
				Log.d(TAG, "cariDalam kitab " + k.nama + " selesai. res.size = " + res.size()); //$NON-NLS-1$ //$NON-NLS-2$
			}
		} else {
			// cari hanya pada kp (kitab pasal) yang ada di sumber.
			int hitung = 0; // buat statistik aja
			
			int[] ppos = new int[1];
			int ariKpKini = 0x000000;
			
			while (true) {
				ariKpKini = ariBerikutnya(sumber, ppos, ariKpKini);
				if (ariKpKini == 0) break; // habis
				
				Kitab k = S.edisiAktif.volatile_xkitab[Ari.toKitab(ariKpKini)];
				int pasal_1 = Ari.toPasal(ariKpKini);
				
				String sepasal = S.muatTeksJanganPisahAyatHurufKecil(context, S.edisiAktif, k, pasal_1);
				if (sepasal.indexOf(kata) >= 0) {
					// hanya lakukan ini jika dalam sepasal kedetek ada kata
					cariDalamSepasal(sepasal, kata, res, ariKpKini);
				}
				
				hitung++;
			}
			
			Log.d(TAG, "cariDalam kitab dengan sumber " + sumber.size() + " perlu baca kp sebanyak " + hitung + " res.size=" + res.size()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
		
		return res;
	}

	private static void cariDalamSepasal(String sepasal, String kata, IntArrayList res, int base) {
		int a = 0;
		int aterakhir = -1;
		
		int posN = sepasal.indexOf(0x0a);
		int posKata = sepasal.indexOf(kata);
		
		while (true) {
			if (posN < posKata) {
				a++;
				posN = sepasal.indexOf(0x0a, posN+1);
				if (posN == -1) {
					return;
				}
			} else {
				if (a != aterakhir) {
					res.add(base + a + 1); // +1 supaya jadi ayat[1base]
					aterakhir = a;
				}
				posKata = sepasal.indexOf(kata, posKata+1);
				if (posKata == -1) {
					return;
				}
			}
		}
	}
	
	static SpannableStringBuilder hilite(String ayat, String[] xkata, int warnaStabilo) {
		SpannableStringBuilder res = new SpannableStringBuilder(ayat);
		
		ayat = ayat.toLowerCase();
		
		int pos = 0;
		int nkata = xkata.length;
		int[] coba = new int[nkata];
		
		while (true) {
			for (int i = 0; i < nkata; i++) {
				coba[i] = ayat.indexOf(xkata[i], pos);
			}
			
			int minpos = Integer.MAX_VALUE;
			int minkata = -1;
			
			for (int i = 0; i < nkata; i++) {
				if (coba[i] >= 0) {
					if (coba[i] < minpos) {
						minpos = coba[i];
						minkata = i;
					}
				}
			}
			
			if (minkata == -1) {
				break; // ga ada lagi
			}
			
			pos = minpos + xkata[minkata].length();
			
			int kepos = minpos + xkata[minkata].length();
			res.setSpan(new StyleSpan(Typeface.BOLD), minpos, kepos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			res.setSpan(new ForegroundColorSpan(warnaStabilo), minpos, kepos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		}
		
		return res;
	}
}
