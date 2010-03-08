package yuku.alkitab;

import java.io.*;
import java.util.*;

import yuku.alkitab.model.*;
import yuku.bintex.BintexReader;
import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import android.widget.ArrayAdapter;

public class S {
	public static final String NAMA_PREFERENCES = "yuku.alkitab";
	
	public static Edisi[] xedisi;
	public static Edisi edisi;
	public static Kitab[] xkitab;
	public static Kitab kitab;

	public static PengirimFidbek pengirimFidbek;
	
	private static int getRawInt(Resources resources, String rid) {
		return resources.getIdentifier(rid, "raw", "yuku.alkitab");
	}

	public static void siapinEdisi(Resources resources) {
		if (xedisi != null) return;

		Scanner sc = new Scanner(resources.openRawResource(R.raw.edisi_index));

		ArrayList<Edisi> xedisi = new ArrayList<Edisi>();

		while (sc.hasNext()) {
			Edisi e = Edisi.baca(sc);
			xedisi.add(e);
		}

		S.xedisi = xedisi.toArray(new Edisi[xedisi.size()]);
		S.edisi = S.xedisi[0]; // TODO selalu pilih edisi pertama
	}

	public static void siapinKitab(Resources resources) {
		if (xedisi == null || edisi == null) {
			siapinEdisi(resources);
		}
		if (xkitab != null) return;

		try {
			//Debug.startMethodTracing("siapinKitab");
			
//			InputStream is = resources.openRawResource(getRawInt(resources, edisi.nama + "_index"));
//			Utf8Reader in = new Utf8Reader(is);
//			SimpleScanner sc = new SimpleScanner(in, 200);
			InputStream is = resources.openRawResource(getRawInt(resources, edisi.nama + "_index_bt"));
			BintexReader in = new BintexReader(is);
	
			ArrayList<Kitab> xkitab = new ArrayList<Kitab>();
	
			try {
				int pos = 0;
				while (true) {
					Kitab k = Kitab.baca(in, pos++);
					xkitab.add(k);
					
					Log.d("alkitab", "siapinKitab memuat " + k.judul);
				}
			} catch (IOException e) {
				Log.d("alkitab", "siapinKitab selesai memuat");
			}
	
			S.xkitab = xkitab.toArray(new Kitab[xkitab.size()]);
			S.kitab = S.xkitab[0]; // TODO selalu pilih edisi pertama
		} finally {
			//Debug.stopMethodTracing();
		}
		
	}

	/**
	 * @param pasal
	 *            harus betul! antara 1 sampe npasal, 0 ga boleh
	 */
	public static String[] muatTeks(Resources resources, int pasal) {
		int offset = kitab.pasal_offset[pasal - 1];
		int length = 0;

		try {
			InputStream in = resources.openRawResource(getRawInt(resources, kitab.file));
			in.skip(offset);

			if (pasal == kitab.npasal) {
				length = in.available();
			} else {
				length = kitab.pasal_offset[pasal] - offset;
			}

			ByteArrayInputStream bais = null;

			byte[] ba = new byte[length];
			in.read(ba);
			in.close();
			
			bais = new ByteArrayInputStream(ba);

			Utf8Reader reader = new Utf8Reader(bais);
			char[] ayatBuf = new char[4000];
			int i = 0;

			ArrayList<String> res = new ArrayList<String>();
			
			while (true) {
				int c = reader.read();
				if (c == -1) {
					break;
				} else if (c == '\n') {
					String satu = new String(ayatBuf, 0, i);
					res.add(satu);
					i = 0;
				} else {
					ayatBuf[i++] = (char) c;
				}
			}

			return res.toArray(new String[res.size()]);
		} catch (IOException e) {
			return new String[] { e.getMessage() };
		}
	}

	public static ArrayAdapter<String> getKitabAdapter(Context context) {
		String[] content = new String[xkitab.length];
		
		for (int i = 0; i < xkitab.length; i++) {
			content[i] = xkitab[i].judul;
		}
		
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, content);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		return adapter;
	}

	public static void siapinPengirimFidbek(IsiActivity activity) {
		if (pengirimFidbek == null) {
			pengirimFidbek = new PengirimFidbek(activity);
		}
	}
	
	public static class Peloncat {
		private String p_kitab;
		private int p_pasal;
		private int p_ayat;
		
		private boolean isKata(String s) {
			char c = s.charAt(0);
			if (c < '0' && c > '9') return true;
			
			try {
				Integer.parseInt(s);
				return false;
			} catch (NumberFormatException e) {
				return true;
			}
		}
		
		private boolean isAngka(String s) {
			try {
				Integer.parseInt(s);
			} catch (NumberFormatException e) {
				return false;
			}
			return true;
		}
		
		private int angkain(String s) {
			try {
				return Integer.parseInt(s);
			} catch (NumberFormatException e) {
				return 0;
			}
		}
		
		private boolean parse0(String alamat) {
			alamat = alamat.trim();
			Log.d("alki", "peloncat tahap 0: " + alamat);
			
			String[] bagian = alamat.split("(\\s|:|\\.)+");
			Log.d("alki", "peloncat tahap 10: " + Arrays.toString(bagian));
			
			if (bagian.length == 0) {
				return false;
			}
			
			// lebarin dulu kasus semacam Yoh3 jadi Yoh 3
			{
				ArrayList<String> bel = new ArrayList<String>();
				
				for (String b: bagian) {
					if (isKata(b)) {
						String angka = "";
						for (int i = b.length() - 1; i >= 0; i--) {
							char c = b.charAt(i);
							if (c >= '0' && c <= '9') {
								// angka ketemu
								angka = c + angka;
							} else {
								break;
							}
						}
						
						if (angka.length() > 0) { // ada angka ketemu di belakang kata
							bel.add(b.substring(0, b.length() - angka.length()));
							bel.add(angka);
						} else {
							bel.add(b);
						}
					} else {
						bel.add(b);
					}
				}
				
				bagian = bel.toArray(bagian);
			}
			Log.d("alki", "peloncat tahap 20: " + Arrays.toString(bagian));
			
			// ubah semacam "3" "yohanes" jadi "3 yohanes"
			{
				ArrayList<String> bel = new ArrayList<String>(); 
				
				int mulaiKata = 0;
				
				// liat dari kanan mana yang bukan angka, itu mulainya kitab
				for (int i = bagian.length - 1; i >= 0; i--) {
					if (! isAngka(bagian[i])) {
						// ini dan depannya semua adalah kitab
						mulaiKata = i;
					}
					
					if (i == 0 && bagian.length > 2) {
						// kebanyakan, masa lebih dari 2 bilangan
						return false;
					}
				}

				String s = null;
				for (int j = 0; j <= mulaiKata; j++) {
					s = (s == null)? bagian[j]: s + " " + bagian[j];
				}
				
				bel.add(s);
				for (int j = mulaiKata+1; j < bagian.length; j++) {
					bel.add(bagian[j]);
				}

				bagian = bel.toArray(new String[0]);
			}
			Log.d("alki", "peloncat tahap 30: " + Arrays.toString(bagian));
			
			if (bagian.length == 1) { // 1 bagian doang
				// , berati PASAL ato KITAB doang
				if (isKata(bagian[0])) {
					// kitab
					p_kitab = bagian[0];
					return true;
				} else {
					p_pasal = angkain(bagian[0]);
					return true;
				}
			}

			if (bagian.length == 2) { // 2 bagian
				// , berarti bisa PASAL AYAT (dalam kitab sama)
				if (isAngka(bagian[0]) && isAngka(bagian[1])) {
					p_pasal = angkain(bagian[0]);
					p_ayat = angkain(bagian[1]);
					return true;
				}
				// atau KITAB PASAL
				else if (isAngka(bagian[1])) {
					p_kitab = bagian[0];
					p_pasal = angkain(bagian[1]);
					return true;
				}
				return false;
			}
			
			if (bagian.length == 3) { // 3 bagian
				// , berarti harus KITAB PASAL AYAT. Ga boleh yang lain
				p_kitab = bagian[0];
				p_pasal = angkain(bagian[1]);
				p_ayat = angkain(bagian[2]);
				return true;
			}
			
			return false;
		}
		
		boolean parse(String alamat) {
			boolean res = parse0(alamat);
			
			Log.d("alki", "peloncat sesudah parse0: p_kitab=" + p_kitab);
			Log.d("alki", "peloncat sesudah parse0: p_pasal=" + p_pasal);
			Log.d("alki", "peloncat sesudah parse0: p_ayat=" + p_ayat);
			
			return res;
		}
		
		private int tebakKitab() {
			return -1;
		}
		
		int getKitab() {
			return tebakKitab();
		}
		
		int getPasal() {
			return p_pasal;
		}
		
		int getAyat() {
			return p_ayat;
		}
	}
}
