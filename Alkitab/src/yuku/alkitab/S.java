package yuku.alkitab;

import java.io.*;
import java.util.*;

import yuku.alkitab.model.*;
import yuku.bintex.BintexReader;
import yuku.kirimfidbek.PengirimFidbek;
import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import android.widget.*;

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
	public static String[] muatTeks(Resources resources, Kitab kitab, int pasal) {
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

	public static synchronized void siapinPengirimFidbek(final IsiActivity activity) {
		if (pengirimFidbek == null) {
			pengirimFidbek = new PengirimFidbek(activity, activity.getSharedPreferences(S.NAMA_PREFERENCES, 0));
			pengirimFidbek.setOnSuccessListener(new PengirimFidbek.OnSuccessListener() {
				@Override
				public void onSuccess(final byte[] response) {
					activity.handler.post(new Runnable() {
						@Override
						public void run() {
							Log.d("alki", "KirimFidbek respon: " + new String(response, 0, response.length));
							Toast.makeText(activity, R.string.fidbekMakasih_s, Toast.LENGTH_SHORT).show();
						}
					});
				}
			});
		}
	}
	
	public static class Peloncat {
		private String p_kitab;
		private int p_pasal;
		private int p_ayat;
		
		private static class KitabRef {
			String pendek;
			int pos;
			
			public String toString() {
				return pendek + ":" + pos;
			}
		}
		
		private IdentityHashMap<Kitab[], KitabRef[]> pendekCache = new IdentityHashMap<Kitab[], KitabRef[]>();
		
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
			
			if (alamat.length() == 0) {
				return false;
			}
			
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
						
						break;
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
			if (p_kitab == null) {
				return -1;
			}
			
			int res = -1;
			
			// 0. bikin cache semua judul kitab yang dibuang spasinya dan dikecilin semua dan 1 jadi I, 2 jadi II, dst
			{
				KitabRef[] refs = pendekCache.get(S.xkitab);
				
				if (refs == null) {
					ArrayList<KitabRef> a = new ArrayList<KitabRef>();
					
					for (Kitab k: S.xkitab) {
						String judul = k.judul.replaceAll("(\\s|-|_)+", "").toLowerCase();
						
						{
							KitabRef ref = new KitabRef();
							ref.pendek = judul;
							ref.pos = k.pos;
							
							a.add(ref);
						}
						
						if (judul.contains("1") || judul.contains("2") || judul.contains("3")) {
							judul = judul.replaceAll("1", "i").replaceAll("2", "ii").replaceAll("3", "iii");
							
							KitabRef ref = new KitabRef();
							ref.pendek = judul;
							ref.pos = k.pos;
							
							a.add(ref);
						}
					}
					
					refs = a.toArray(new KitabRef[0]);
					pendekCache.put(S.xkitab, refs);
					Log.d("alki", "entri pendekCache baru: " + Arrays.toString(refs));
				}
			}
			
			// 0 juga. bersihin p_kitab
			KitabRef[] refs = pendekCache.get(S.xkitab);
			p_kitab = p_kitab.replaceAll("(\\s|-|_)", "").toLowerCase();
			Log.d("alki", "tebakKitab fase 0: p_kitab = " + p_kitab);
			
			// 1. coba cocokin keseluruhan (co: "kejadian", "yohanes")
			for (KitabRef ref: refs) {
				if (ref.pendek.equals(p_kitab)) {
					Log.d("alki", "tebakKitab fase 1 sukses: " + p_kitab);
					return ref.pos;
				}
			}
			
			// 2. coba cocokin depannya, kalo ada 1 doang yang lulus, sukses
			int pos_buatNanti = -1;
			{
				int lulus = 0;
				for (KitabRef ref: refs) {
					if (ref.pendek.startsWith(p_kitab)) {
						lulus++;
						if (lulus == 1) pos_buatNanti = ref.pos;
					}
				}
				
				if (lulus == 1) {
					Log.d("alki", "tebakKitab fase 2 sukses: " + pos_buatNanti + " untuk " + p_kitab);
					return pos_buatNanti;
				} else {
					Log.d("alki", "tebakKitab fase 2: lulus = " + lulus);
				}
			}
			
			// 3. String matching hanya kalo p_kitab 2 huruf ato lebih
			if (p_kitab.length() >= 2) {
				int minSkor = 9999999;
				int pos = -1;
				
				for (KitabRef ref: refs) {
					int skor = Levenshtein.distance(p_kitab, ref.pendek);
					Log.d("alki", String.format("tebakKitab fase 3: dengan %s:%d skor %d", ref.pendek, ref.pos, skor));
					
					if (skor < minSkor) {
						minSkor = skor;
						pos = ref.pos;
					}
				}
				
				if (pos != -1) {
					Log.d("alki", "tebakKitab fase 3 sukses: " + pos + " dengan skor " + minSkor);
					return pos;
				}
			}
			
			// 7. Keluarin yang pertama cocok kalo ada lebih dari 1 yang lulus fase 2
			if (pos_buatNanti != -1) {
				Log.d("alki", "tebakKitab fase 7 sukses: " + pos_buatNanti + " untuk " + p_kitab);
				return pos_buatNanti;
			}
			
			return res;
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
