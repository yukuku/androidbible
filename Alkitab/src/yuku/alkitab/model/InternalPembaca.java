package yuku.alkitab.model;

import java.io.*;
import java.util.ArrayList;

import yuku.alkitab.U;
import yuku.bintex.BintexReader;
import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

public class InternalPembaca implements Pembaca {

	// # buat cache Asset
	private static InputStream cache_inputStream = null;
	private static String cache_file = null;
	private static int cache_posInput = -1;

	@Override
	public Kitab[] bacaInfoKitab(Context context, Edisi edisi) {
		Resources resources = context.getResources();

		InputStream is = resources.openRawResource(resources.getIdentifier(edisi.nama + "_index_bt", "raw", context.getPackageName()));
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

			return xkitab.toArray(new Kitab[xkitab.size()]);
		} finally {
			in.close();
		}
	}

	@Override
	public String[] muatTeks(Context context, Edisi edisi, Kitab kitab, int pasal_1, boolean janganPisahAyat, boolean hurufKecil) {
		int offset = kitab.pasal_offset[pasal_1 - 1];
		int length = 0;

		try {
			InputStream in;

			// Log.d("alki", "muatTeks kitab=" + kitab.nama + " pasal[1base]=" + pasal + " offset=" + offset);
			// Log.d("alki", "muatTeks cache_file=" + cache_file + " cache_posInput=" + cache_posInput);
			if (cache_inputStream == null) {
				// kasus 1: belum buka apapun
				in = U.openRaw(context, kitab.file);
				cache_inputStream = in;
				cache_file = kitab.file;

				in.skip(offset);
				cache_posInput = offset;
				// Log.d("alki", "muatTeks masuk kasus 1");
			} else {
				// kasus 2: uda pernah buka. Cek apakah filenya sama
				if (kitab.file.equals(cache_file)) {
					// kasus 2.1: filenya sama.
					if (offset >= cache_posInput) {
						// bagus, kita bisa maju.
						in = cache_inputStream;

						in.skip(offset - cache_posInput);
						cache_posInput = offset;
						// Log.d("alki", "muatTeks masuk kasus 2.1 bagus");
					} else {
						// ga bisa mundur. tutup dan buka lagi.
						cache_inputStream.close();

						in = U.openRaw(context, kitab.file);
						cache_inputStream = in;

						in.skip(offset);
						cache_posInput = offset;
						// Log.d("alki", "muatTeks masuk kasus 2.1 jelek");
					}
				} else {
					// kasus 2.2: filenya beda, tutup dan buka baru
					cache_inputStream.close();

					in = U.openRaw(context, kitab.file);
					cache_inputStream = in;
					cache_file = kitab.file;

					in.skip(offset);
					cache_posInput = offset;
					// Log.d("alki", "muatTeks masuk kasus 2.2");
				}
			}

			if (pasal_1 == kitab.npasal) {
				length = in.available();
			} else {
				length = kitab.pasal_offset[pasal_1] - offset;
			}

			byte[] ba = new byte[length];
			in.read(ba);
			cache_posInput += ba.length;
			// jangan ditutup walau uda baca. Siapa tau masih sama filenya dengan sebelumnya.

			if (hurufKecil) {
				U.hurufkecilkanAscii(ba);
			}

			if (janganPisahAyat) {
				return new String[] { new String(ba, 0) };
			} else {
				return U.pisahJadiAyatAscii(ba);
			}
		} catch (IOException e) {
			return new String[] { e.getMessage() };
		}
	}

	@Override
	public IndexPerikop bacaIndexPerikop(Context context, Edisi edisi) {
		long wmulai = System.currentTimeMillis();

		InputStream is = U.openRaw(context, edisi.nama + "_perikop_index_bt");
		BintexReader in = new BintexReader(is);
		try {
			return IndexPerikop.baca(in);

		} catch (IOException e) {
			Log.e("alki", "baca perikop index ngaco", e);
			return null;
		} finally {
			in.close();
			Log.d("alki", "Muat index perikop butuh ms: " + (System.currentTimeMillis() - wmulai));
		}
	}

	@Override
	public int muatPerikop(Context context, Edisi edisi, int kitab, int pasal, int[] xari, Blok[] xblok, int max) {
		IndexPerikop indexPerikop = edisi.volatile_indexPerikop;

		if (indexPerikop == null) {
			return 0; // ga ada perikop!
		}

		int ariMin = Ari.encode(kitab, pasal, 0);
		int ariMax = Ari.encode(kitab, pasal + 1, 0);
		int res = 0;

		int pertama = indexPerikop.cariPertama(ariMin, ariMax);

		if (pertama == -1) {
			return 0;
		}

		int kini = pertama;

		BintexReader in = new BintexReader(U.openRaw(context, edisi.nama + "_perikop_blok_bt"));
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
}
