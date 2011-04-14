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
		String bersih = carian.trim().toLowerCase().replaceAll("\\s+", " "); //$NON-NLS-1$ //$NON-NLS-2$
		
		// cari dari kiri ke kanan, tiap ada kutip, ubah mode
		boolean modeKutip = false;
		int pos = 0;
		ArrayList<String> xkata = new ArrayList<String>();
		while (true) {
			if (modeKutip) {
				// cari kutip berikutnya
				int posKutip = bersih.indexOf('"', pos);
				if (posKutip != -1) {
					xkata.add(bersih.substring(pos, posKutip));
					pos = posKutip + 1;
				} else {
					// abisin aja
					xkata.add(bersih.substring(pos));
					pos = bersih.length();
				}
				modeKutip = false;
			} else {
				// cari spasi ato kutip pertama
				int posSpasi = bersih.indexOf(' ', pos);
				int posKutip = bersih.indexOf('"', pos);
				
				if (posSpasi == -1 && posKutip == -1) {
					// ga ada lagi, ambil semua dan beres
					xkata.add(bersih.substring(pos));
					pos = bersih.length();
				} else if ((posSpasi != -1 && posSpasi <= posKutip) || posKutip == -1) {
					// ambil kata sampe spasi
					xkata.add(bersih.substring(pos, posSpasi));
					pos = posSpasi + 1;
				} else if ((posKutip != -1 && posKutip <= posSpasi) || posSpasi == -1) {
					// ambil kata sampe kutip
					xkata.add(bersih.substring(pos, posKutip));
					pos = posKutip + 1;
					modeKutip = true;
				}
			}
			if (pos >= bersih.length()) {
				break;
			}
		}
		
		//# bersih2 terakhir
		ArrayList<String> xkata2 = new ArrayList<String>(xkata.size());
		for (int i = 0, len = xkata.size(); i < len; i++) {
			String kata = xkata.get(i);
			kata = kata.trim();
			if (kata.length() > 0 && tanpaTambah(kata).length() > 0) {
				xkata2.add(kata);
			}
		}
		return xkata2.toArray(new String[xkata2.size()]);
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
		boolean pakeTambah = false;
		
		if (adaTambah(kata)) {
			pakeTambah = true;
			kata = tanpaTambah(kata);
		}
	
		if (sumber == null) {
			for (Kitab k: S.edisiAktif.getXkitab()) {
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
					String sepasal = S.muatTeksJanganPisahAyatHurufKecil(S.edisiAktif, k, pasal_1);
					if (sepasal.indexOf(kata) >= 0) {
						// hanya lakukan ini jika dalam sepasal kedetek ada kata
						cariDalamSepasal(sepasal, kata, res, Ari.encode(k.pos, pasal_1, 0), pakeTambah);
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
				
				Kitab k = S.edisiAktif.getXkitab()[Ari.toKitab(ariKpKini)];
				int pasal_1 = Ari.toPasal(ariKpKini);
				
				String sepasal = S.muatTeksJanganPisahAyatHurufKecil(S.edisiAktif, k, pasal_1);
				if (sepasal.indexOf(kata) >= 0) {
					// hanya lakukan ini jika dalam sepasal kedetek ada kata
					cariDalamSepasal(sepasal, kata, res, ariKpKini, pakeTambah);
				}
				
				hitung++;
			}
			
			Log.d(TAG, "cariDalam kitab dengan sumber " + sumber.size() + " perlu baca kp sebanyak " + hitung + " res.size=" + res.size()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
		
		return res;
	}

	private static void cariDalamSepasal(String sepasal, String kata, IntArrayList res, int base, boolean pakeTambah) {
		int a = 0;
		int aterakhir = -1;
		
		int posKata;
		if (pakeTambah) {
			posKata = indexOfWholeWord(sepasal, kata, 0);
			if (posKata == -1) {
				return;
			}
		} else {
			posKata = sepasal.indexOf(kata);
		}
		
		int posN = sepasal.indexOf(0x0a);
		
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
				if (pakeTambah) {
					posKata = indexOfWholeWord(sepasal, kata, posKata+1);
				} else {
					posKata = sepasal.indexOf(kata, posKata+1);
				}
				if (posKata == -1) {
					return;
				}
			}
		}
	}
	
	private static int indexOfWholeWord(String sepasal, String kata, int start) {
		int len = sepasal.length();
		
		while (true) {
			int pos = sepasal.indexOf(kata, start);
			//Log.d(TAG, "pos=" + pos + " untuk " + kata + " pada: " + sepasal);   //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$
			if (pos == -1) return -1;
			
			// pos bukan -1
			
			// cek kiri
			// [pos] [charat pos-1]
			//  0        *          ok
			// >0       alpha       ng
			// >0      !alpha       ok
			if (pos != 0 && Character.isLetter(sepasal.charAt(pos - 1))) {
				start = pos + 1;
				continue;
			}
			
			// cek kanan
			int end = pos + kata.length();
			// [end] [charat end]
			// len       *         ok
			// != len  alpha       ng
			// != len  !alpha      ok
			if (end != len && Character.isLetter(sepasal.charAt(end))) {
				start = pos + 1;
				continue;
			}
			
			// lulus
			return pos;
		}
	}

	static SpannableStringBuilder hilite(String ayat, String[] xkata, int warnaStabilo) {
		SpannableStringBuilder res = new SpannableStringBuilder(ayat);
		{
			String[] xkata2 = new String[xkata.length];
			System.arraycopy(xkata, 0, xkata2, 0, xkata.length);
			for (int i = 0; i < xkata.length; i++) {
				if (adaTambah(xkata2[i])) {
					xkata2[i] = tanpaTambah(xkata2[i]);
				}
			}
			xkata = xkata2;
		}
		
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
	
	static boolean adaTambah(String kata) {
		return (kata.startsWith("+")); //$NON-NLS-1$
	}
	
	static String tanpaTambah(String kata) {
		int pos = 0;
		while (true) {
			if (pos >= kata.length()) break;
			if (kata.charAt(pos) == '+') {
				pos++;
			} else {
				break;
			}
		}
		if (pos == 0) return kata;
		return kata.substring(pos);
	}
}
