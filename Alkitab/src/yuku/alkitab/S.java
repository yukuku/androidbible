package yuku.alkitab;

import java.io.*;
import java.util.*;

import yuku.alkitab.model.*;
import yuku.alkitab.renungan.TukangDonlot;
import yuku.bintex.BintexReader;
import yuku.kirimfidbek.PengirimFidbek;
import android.content.*;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.util.Log;
import android.widget.*;

public class S {
	/**
	 * penerapan dari pengaturan
	 */
	public static class penerapan {
		public static float ukuranTeksPx;
		public static Typeface jenisHuruf;
		public static int tebalHuruf;
		public static int warnaHuruf; 
		public static int indenParagraf;
		public static int menjorokSatu;
		public static int menjorokDua;
		public static boolean sortKitabAlfabet;
		public static boolean matikanTahanAyat;
		public static boolean nyalakanTerusLayar;
	}
	
	/**
	 * Seting yang tetep hidup walau aktiviti dimusnahkan.
	 * Pastikan ga ada acuan ke aktiviti, supaya memori ga bocor.
	 */
	public static class penampungan {
		public static String renungan_nama = null;
		public static Date renungan_tanggalan = null;
		public static String search_carian = null;
	}
	
	//# 33nya harus siap di siapinEdisi
	public static Edisi[] xedisi;
	public static Edisi edisi;
	public static IndexPerikop indexPerikop;
	
	//# 22nya harus siap di siapinKitab
	public static Kitab[] xkitab;
	public static Kitab kitab;

	public static PengirimFidbek pengirimFidbek;
	public static TukangDonlot tukangDonlot;
	
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
		
		if (S.edisi.perikopAda) {
			if (S.indexPerikop != null && S.edisi.nama.equals(S.indexPerikop)) {
				// indexPerikop uda kemuat dan uda betul
			} else {
				long wmulai = System.currentTimeMillis();
				
				InputStream is = resources.openRawResource(getRawInt(resources, S.edisi.nama + "_perikop_index_bt"));
				BintexReader in = new BintexReader(is);
				try {
					S.indexPerikop = IndexPerikop.baca(in, S.edisi.nama, getRawInt(resources, S.edisi.nama + "_perikop_blok_bt"));
				} catch (IOException e) {
					Log.e("alki", "baca perikop index ngaco", e);
				} finally {
					in.close();
				}
				
				Log.d("alki", "Muat index perikop butuh ms: " + (System.currentTimeMillis() - wmulai));
			}
		} else {
			S.indexPerikop = null;
		}
	}

	public static void siapinKitab(Resources resources) {
		if (xedisi == null || edisi == null) {
			siapinEdisi(resources);
		}
		if (xkitab != null) return;

		Log.d("alki", "siapinKitab mulai");
		InputStream is = resources.openRawResource(getRawInt(resources, edisi.nama + "_index_bt"));
		BintexReader in = new BintexReader(is);
		try {
			ArrayList<Kitab> xkitab = new ArrayList<Kitab>();
	
			try {
				int pos = 0;
				while (true) {
					Kitab k = Kitab.baca(in, pos++);
					xkitab.add(k);
				}
			} catch (IOException e) {
				Log.d("alki", "siapinKitab selesai memuat");
			}
	
			S.xkitab = xkitab.toArray(new Kitab[xkitab.size()]);
			S.kitab = S.xkitab[0]; // nanti diset sama luar 
		} finally {
			in.close();
		}
		Log.d("alki", "siapinKitab selesai");
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
			
			if (pasal == 45 && kitab.nama.equals("Mazmur")) {
				Log.e("alki", "VERSI PERCOBAAN MENJOROKKKKK");
				
				res.set(0, "@@@0Untuk pemimpin biduan. Menurut lagu: Bunga bakung. Dari bani Korah. Nyanyian pengajaran; nyanyian kasih.@1(45-2) Hatiku meluap dengan kata-kata indah, @2aku hendak menyampaikan sajakku kepada raja; @2lidahku ialah pena seorang jurutulis yang mahir.@8");
				res.set(1, "@@@1(45-3) Engkau yang terelok di antara anak-anak manusia, @2kemurahan tercurah pada bibirmu, @2sebab itu Allah telah memberkati engkau untuk selama-lamanya.");
				res.set(2, "@@@1(45-4) Ikatlah pedangmu pada pinggang, hai pahlawan, @2dalam keagunganmu dan semarakmu!");
				res.set(3, "@@@1(45-5) Dalam semarakmu itu majulah demi kebenaran, @2perikemanusiaan dan keadilan! Biarlah tangan kananmu mengajarkan engkau perbuatan-perbuatan yang dahsyat!");
				res.set(4, "@@@1(45-6) Anak-anak panahmu tajam, menembus jantung musuh raja; @2bangsa-bangsa jatuh di bawah kakimu.@8");
				res.set(5, "@@@1(45-7) Takhtamu kepunyaan Allah, tetap untuk seterusnya dan selamanya, @2dan tongkat kerajaanmu adalah tongkat kebenaran.");
				res.set(6, "@@@1(45-8) Engkau mencintai keadilan dan membenci kefasikan; @2sebab itu Allah, Allahmu, telah mengurapi engkau @2dengan minyak sebagai tanda kesukaan, melebihi teman-teman sekutumu.");
				res.set(7, "@@@1(45-9) Segala pakaianmu berbau mur, gaharu dan cendana; @2dari istana gading permainan kecapi menyukakan engkau;");
				res.set(8, "@@@1(45-10) di antara mereka yang disayangi terdapat puteri-puteri raja, @2di sebelah kananmu berdiri permaisuri berpakaian emas dari Ofir.@8");
				res.set(9, "@@@1(45-11) Dengarlah, hai puteri, lihatlah, dan sendengkanlah telingamu, @2lupakanlah bangsamu dan seisi rumah ayahmu!");
				res.set(10, "@@@1(45-12) Biarlah raja menjadi gairah karena keelokanmu, @2sebab dialah tuanmu! Sujudlah kepadanya!");
				res.set(11, "@@@1(45-13) Puteri Tirus datang dengan pemberian-pemberian; @2orang-orang kaya di antara rakyat akan mengambil muka kepadamu.@8");
				res.set(12, "@@@1(45-14) Keindahan belaka puteri raja itu di dalam, @2pakaiannya berpakankan emas.");
				res.set(13, "@@@1(45-15) Dengan pakaian bersulam berwarna-warna ia dibawa kepada raja; @2anak-anak dara mengikutinya, yakni teman-temannya, @2yang didatangkan untuk dia.");
				res.set(14, "@@@1(45-16) Dengan sukacita dan sorak-sorai mereka dibawa, @2mereka masuk ke dalam istana raja.@8");
				res.set(15, "@@@1(45-17) Para bapa leluhurmu hendaknya diganti oleh anak-anakmu nanti; @2engkau akan mengangkat mereka menjadi pembesar di seluruh bumi.");
				res.set(16, "@@@1(45-18) Aku mau memasyhurkan namamu turun-temurun; @2sebab itu bangsa-bangsa akan bersyukur kepadamu untuk seterusnya dan selamanya.");

			}

			return res.toArray(new String[res.size()]);
		} catch (IOException e) {
			return new String[] { e.getMessage() };
		}
	}
	
	public static int muatPerikop(Resources resources, int kitab, int pasal, int[] xari, Blok[] xblok, int max) {
		int ariMin = Ari.encode(kitab, pasal, 0);
		int ariMax = Ari.encode(kitab, pasal+1, 0);
		int res = 0;
		
		IndexPerikop indexPerikop = S.indexPerikop;
		
		int pertama = indexPerikop.cariPertama(ariMin, ariMax);
		
		if (pertama == -1) {
			return 0;
		}
		
		int kini = pertama;
		
		BintexReader in = new BintexReader(resources.openRawResource(indexPerikop.perikopBlokResId));
		try {
			while (true) {
				int ari = indexPerikop.getAri(kini);
				
				if (ari >= ariMax) {
					// habis. Uda ga relevan
					break;
				}
				
				Blok blok = indexPerikop.getBlok(in, kini);
				kini++;
				
				if (res < max) {
					xari[res] = ari;
					xblok[res] = blok;
					res++;
				} else {
					break;
				}
			}
		} finally {
			in.close();
		}
		
		return res;
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
			pengirimFidbek = new PengirimFidbek(activity, getPreferences(activity));
			pengirimFidbek.activateDefaultUncaughtExceptionHandler("Ada kesalahan. Cobalah ulangi, dan jika tetap terjadi, tolong kirim imel ke yuku+alkitab@ikitek.com. Terima kasih.");
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

	public static SharedPreferences getPreferences(Context context) {
		return context.getSharedPreferences(context.getPackageName(), 0);
	}
}
