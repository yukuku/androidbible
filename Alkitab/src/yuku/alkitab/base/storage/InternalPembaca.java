package yuku.alkitab.base.storage;

import java.io.*;
import java.util.ArrayList;

import yuku.alkitab.base.S;
import yuku.alkitab.base.model.*;
import yuku.bintex.BintexReader;
import android.content.Context;
import android.util.Log;

public class InternalPembaca extends Pembaca {
	public static final String TAG = InternalPembaca.class.getSimpleName();
	
	// # buat cache Asset
	private static InputStream cache_inputStream = null;
	private static String cache_file = null;
	private static int cache_posInput = -1;

	private final String edisiPrefix;
	private final String edisiJudul;
	private final PembacaDecoder pembacaDecoder;

	public InternalPembaca(Context context, String edisiPrefix, String edisiJudul, PembacaDecoder pembacaDecoder) {
		super(context);
		
		this.edisiPrefix = edisiPrefix;
		this.edisiJudul = edisiJudul;
		this.pembacaDecoder = pembacaDecoder;
	}
	
	@Override
	public String getJudul() {
		return edisiJudul;
	}

	@Override
	public Kitab[] bacaInfoKitab() {
		InputStream is = S.openRaw(edisiPrefix + "_index_bt"); //$NON-NLS-1$
		BintexReader in = new BintexReader(is);
		try {
			ArrayList<Kitab> xkitab = new ArrayList<Kitab>();

			try {
				int pos = 0;
				while (true) {
					Kitab k = bacaKitab(in, pos++);
					xkitab.add(k);
				}
			} catch (IOException e) {
				Log.d(TAG, "siapinKitab selesai memuat"); //$NON-NLS-1$
			}

			return xkitab.toArray(new Kitab[xkitab.size()]);
		} finally {
			in.close();
		}
	}
	
	private static Kitab bacaKitab(BintexReader in, int pos) throws IOException {
		Kitab k = new Kitab();
		k.pos = pos;
		
		String awal = in.readShortString();

		if (awal.equals("Kitab")) { //$NON-NLS-1$
			while (true) {
				String key = in.readShortString();
				if (key.equals("nama")) { //$NON-NLS-1$
					k.nama = in.readShortString();
				} else if (key.equals("judul")) { //$NON-NLS-1$
					k.judul = in.readShortString();
					
					k.judul = bersihinJudul(k.judul);
				} else if (key.equals("file")) { //$NON-NLS-1$
					k.file = in.readShortString();
				} else if (key.equals("npasal")) { //$NON-NLS-1$
					k.npasal = in.readInt();
				} else if (key.equals("nayat")) { //$NON-NLS-1$
					k.nayat = new int[k.npasal];
					for (int i = 0; i < k.npasal; i++) {
						k.nayat[i] = in.readUint8();
					}
				} else if (key.equals("pdbBookNumber")) { //$NON-NLS-1$
					k.pdbBookNumber = in.readInt();
				} else if (key.equals("pasal_offset")) { //$NON-NLS-1$
					k.pasal_offset = new int[k.npasal];
					for (int i = 0; i < k.npasal; i++) {
						k.pasal_offset[i] = in.readInt();
					}
				} else if (key.equals("uda")) { //$NON-NLS-1$
					break;
				}
			}
			
			return k;
		} else {
			return null;
		}
	}
	
	private static String bersihinJudul(String judul) {
		return judul.replace('_', ' ');
	}

	@Override
	public String[] muatTeks(Kitab kitab, int pasal_1, boolean janganPisahAyat, boolean hurufKecil) {
		int offset = kitab.pasal_offset[pasal_1 - 1];
		int length = 0;

		try {
			InputStream in;

			// Log.d("alki", "muatTeks kitab=" + kitab.nama + " pasal[1base]=" + pasal + " offset=" + offset);
			// Log.d("alki", "muatTeks cache_file=" + cache_file + " cache_posInput=" + cache_posInput);
			if (cache_inputStream == null) {
				// kasus 1: belum buka apapun
				in = S.openRaw(kitab.file);
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

						in = S.openRaw(kitab.file);
						cache_inputStream = in;

						in.skip(offset);
						cache_posInput = offset;
						// Log.d("alki", "muatTeks masuk kasus 2.1 jelek");
					}
				} else {
					// kasus 2.2: filenya beda, tutup dan buka baru
					cache_inputStream.close();

					in = S.openRaw(kitab.file);
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
				pembacaDecoder.hurufkecilkan(ba);
			}

			if (janganPisahAyat) {
				return new String[] { pembacaDecoder.jadikanStringTunggal(ba) };
			} else {
				return pembacaDecoder.pisahJadiAyat(ba);
			}
		} catch (IOException e) {
			return new String[] { e.getMessage() };
		}
	}

	@Override
	public IndexPerikop bacaIndexPerikop() {
		long wmulai = System.currentTimeMillis();

		InputStream is = S.openRaw(edisiPrefix + "_perikop_index_bt"); //$NON-NLS-1$
		BintexReader in = new BintexReader(is);
		try {
			return IndexPerikop.baca(in);

		} catch (IOException e) {
			Log.e(TAG, "baca perikop index ngaco", e); //$NON-NLS-1$
			return null;
		} finally {
			in.close();
			Log.d(TAG, "Muat index perikop butuh ms: " + (System.currentTimeMillis() - wmulai)); //$NON-NLS-1$
		}
	}

	@Override
	public int muatPerikop(Edisi edisi, int kitab, int pasal, int[] xari, Blok[] xblok, int max) {
		IndexPerikop indexPerikop = edisi.getIndexPerikop();

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

		BintexReader in = new BintexReader(S.openRaw(edisiPrefix + "_perikop_blok_bt")); //$NON-NLS-1$
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
