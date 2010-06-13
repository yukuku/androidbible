package yuku.alkitab;

import java.io.*;
import java.util.*;

import yuku.alkitab.model.*;
import yuku.alkitab.renungan.*;
import yuku.bintex.*;
import yuku.kirimfidbek.*;
import android.content.*;
import android.content.res.*;
import android.graphics.*;
import android.util.*;
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
		public static boolean gebug_tehelBewarna = true;
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
	
	//# buat cache Asset
	private static InputStream cache_inputStream = null;
	private static String cache_file = null;
	private static int cache_posInput = -1;
	
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
		return muatTeks(resources, kitab, pasal, false, false);
	}

	/**
	 * @param pasal
	 *            harus betul! antara 1 sampe npasal, 0 ga boleh
	 */
	public static String muatTeksJanganPisahAyatHurufKecil(Resources resources, Kitab kitab, int pasal) {
		return muatTeks(resources, kitab, pasal, true, true)[0];
	}
	
	private static String[] muatTeks(Resources resources, Kitab kitab, int pasal, boolean janganPisahAyat, boolean hurufKecil) {
		int offset = kitab.pasal_offset[pasal - 1];
		int length = 0;

		try {
			InputStream in;
			
			//Log.d("alki", "muatTeks kitab=" + kitab.nama + " pasal[1base]=" + pasal + " offset=" + offset);
			//Log.d("alki", "muatTeks cache_file=" + cache_file + " cache_posInput=" + cache_posInput);
			if (cache_inputStream == null) {
				// kasus 1: belum buka apapun
				in = resources.openRawResource(getRawInt(resources, kitab.file));
				cache_inputStream = in;
				cache_file = kitab.file;
				
				in.skip(offset);
				cache_posInput = offset;
				//Log.d("alki", "muatTeks masuk kasus 1");
			} else {
				// kasus 2: uda pernah buka. Cek apakah filenya sama
				if (kitab.file.equals(cache_file)) {
					// kasus 2.1: filenya sama.
					if (offset >= cache_posInput) {
						// bagus, kita bisa maju.
						in = cache_inputStream;
						
						in.skip(offset - cache_posInput);
						cache_posInput = offset;
						//Log.d("alki", "muatTeks masuk kasus 2.1 bagus");
					} else {
						// ga bisa mundur. tutup dan buka lagi.
						cache_inputStream.close();
						
						in = resources.openRawResource(getRawInt(resources, kitab.file));
						cache_inputStream = in;
						
						in.skip(offset);
						cache_posInput = offset;
						//Log.d("alki", "muatTeks masuk kasus 2.1 jelek");
					}
				} else {
					// kasus 2.2: filenya beda, tutup dan buka baru
					cache_inputStream.close();
					
					in = resources.openRawResource(getRawInt(resources, kitab.file));
					cache_inputStream = in;
					cache_file = kitab.file;
					
					in.skip(offset);
					cache_posInput = offset;
					//Log.d("alki", "muatTeks masuk kasus 2.2");
				}
			}

			if (pasal == kitab.npasal) {
				length = in.available();
			} else {
				length = kitab.pasal_offset[pasal] - offset;
			}

			byte[] ba = new byte[length];
			in.read(ba);
			cache_posInput += ba.length;
			// jangan ditutup walau uda baca. Siapa tau masih sama filenya dengan sebelumnya.
			
			if (hurufKecil) {
				int blen = ba.length;
				for (int i = 0; i < blen; i++) {
					if (ba[i] <= (byte)'Z' && ba[i] >= (byte)'A') {
						ba[i] |= 0x20; // perhurufkecilkan
					}
				}
			}
			
			if (janganPisahAyat) {
				return new String[] {new String(ba, 0)};
			} else {
				char[] ayatBuf = new char[4000];
				int i = 0;
	
				ArrayList<String> res = new ArrayList<String>();
				
				//# HANYA BERLAKU KALAU SEMUA byte hanya 7-bit. Akan rusak kalo ada yang 0x80.
				int len = ba.length;
				for (int pos = 0; pos < len; pos++) {
					byte c = ba[pos];
					if (c == (byte)0x0a) {
						String satu = new String(ayatBuf, 0, i);
						res.add(satu);
						i = 0;
					} else {
						ayatBuf[i++] = (char) c;
					}
				}
				
				return res.toArray(new String[res.size()]);
			}
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
	
	// di sini supaya bisa dites dari luar
	public static int[] bikinPenunjukKotak(int nayat, int[] perikop_xari, Blok[] perikop_xblok, int nblok) {
		int[] res = new int[nayat + nblok];
		
		int posBlok = 0;
		int posAyat = 0;
		int posPK = 0;
		
		while (true) {
			// cek apakah judul perikop, DAN perikop masih ada
			if (posBlok < nblok) {
				// masih memungkinkan
				if (Ari.toAyat(perikop_xari[posBlok]) - 1 == posAyat) {
					// ADA PERIKOP.
					res[posPK++] = -posBlok-1;
					posBlok++;
					continue;
				}
			}
			
			// cek apakah ga ada ayat lagi
			if (posAyat >= nayat) {
				break;
			}
			
			// uda ga ada perikop, ATAU belom saatnya perikop. Maka masukin ayat.
			res[posPK++] = posAyat;
			posAyat++;
			continue;
		}
		
		if (res.length != posPK) {
			// ada yang ngaco! di algo di atas
			throw new RuntimeException("Algo selip2an perikop salah! posPK=" + posPK + " posAyat=" + posAyat + " posBlok=" + posBlok + " nayat=" + nayat + " nblok_=" + nblok + " xari:" + Arrays.toString(perikop_xari) + " xblok:" + Arrays.toString(perikop_xblok));
		}
		
		return res;
	}

	/**
	 * Kalo ayat ga berawalan @: ga ngapa2in
	 * Sebaliknya, buang semua @ dan 1 karakter setelahnya.
	 * @param ayat
	 */
	public static String buangKodeKusus(String ayat) {
		if (ayat.length() == 0) return ayat;
		if (ayat.charAt(0) != '@') return ayat;
		
		StringBuilder sb = new StringBuilder(ayat.length());
		int pos = 2;
		
		while (true) {
			int p = ayat.indexOf('@', pos);
			if (p == -1) {
				break;
			}
			
			sb.append(ayat, pos, p);
			pos = p + 2;
		}
		
		sb.append(ayat, pos, ayat.length());
		return sb.toString();
	}
}
