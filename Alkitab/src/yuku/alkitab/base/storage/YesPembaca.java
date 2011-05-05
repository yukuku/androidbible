package yuku.alkitab.base.storage;

import java.io.*;
import java.util.Arrays;

import yuku.alkitab.base.model.*;
import yuku.bintex.BintexReader;
import android.content.Context;
import android.util.Log;

public class YesPembaca extends Pembaca {
	private static final String TAG = YesPembaca.class.getSimpleName();
	
	private String nf;
	private RandomAccessFile f;
	private PembacaDecoder pembacaDecoder = new PembacaDecoder.Ascii();
	
	private long teks_dasarOffset;
	private long perikopBlok_dasarOffset;
	
	private String judul;
	private int nkitab;
	
	public YesPembaca(Context context, String nf) {
		super(context);
		
		this.nf = nf;
	}
	
	/**
	 * @return ukuran seksi
	 */
	private int lewatiSampeSeksi(String seksi) throws Exception {
		f.seek(8); // setelah header
		
		while (true) {
			String namaSeksi = readNamaSeksi(f);
			
			if (namaSeksi == null) {
				// sudah mencapai EOF. Maka kasih tau seksi ini ga ada.
				Log.d(TAG, "Seksi tidak ditemukan: " + seksi);
				return -1;
			}
			
			int ukuran = readUkuranSeksi(f);
			
			if (namaSeksi.equals(seksi)) {
				return ukuran;
			} else {
				Log.d(TAG, "seksi dilewati: " + namaSeksi); //$NON-NLS-1$
				f.skipBytes(ukuran);
			}
		}
	}
	
	private synchronized void init() throws Exception {
		if (f == null) {
			f = new RandomAccessFile(nf, "r"); //$NON-NLS-1$
			f.seek(0);
			
			// cek header
			{
				byte[] buf = new byte[8];
				f.read(buf);
				if (!Arrays.equals(buf, new byte[] {(byte) 0x98, 0x58, 0x0d, 0x0a, 0x00, 0x5d, (byte) 0xe0, 0x01})) {
					throw new RuntimeException("Header ga betul. Ketemunya: " + Arrays.toString(buf)); //$NON-NLS-1$
				}
			}
			
			bacaInfoEdisi();
			
			lewatiSampeSeksi("teks________"); //$NON-NLS-1$
			teks_dasarOffset = f.getFilePointer();
			Log.d(TAG, "teks_dasarOffset = " + teks_dasarOffset); //$NON-NLS-1$
		}
	}

	@Override
	public String getJudul() {
		try {
			init();
			return judul;
		} catch (Exception e) {
			Log.e(TAG, "init error", e); //$NON-NLS-1$
			return "";
		}
	}

	public void bacaInfoEdisi() {
		try {
			Log.d(TAG, "bacaInfoEdisi dipanggil");
			
			int ukuran = lewatiSampeSeksi("infoEdisi___"); //$NON-NLS-1$
			byte[] buf = new byte[ukuran];
			f.read(buf);
			BintexReader in = new BintexReader(new ByteArrayInputStream(buf));
			
			String nama = null;
			while (true) {
				String key = in.readShortString();
				
				if (key.equals("versi")) { //$NON-NLS-1$
					int versi = in.readInt();
					if (versi != 1) throw new RuntimeException("Versi Edisi: " + versi + " tidak dikenal"); //$NON-NLS-1$ //$NON-NLS-2$
				} else if (key.equals("nama")) { //$NON-NLS-1$
					nama = in.readShortString();
				} else if (key.equals("judul")) { //$NON-NLS-1$
					this.judul = in.readShortString();
				} else if (key.equals("nkitab")) { //$NON-NLS-1$
					this.nkitab = in.readInt();
				} else if (key.equals("end")) { //$NON-NLS-1$
					break;
				} else {
					Log.w(TAG, "ada key ga dikenal di infoEdisi: " + key); //$NON-NLS-1$ 
					break;
				}
			}
			
			Log.d(TAG, "bacaInfoEdisi selesai, nama=" + nama + " judul=" + judul + " nkitab=" + nkitab);
		} catch (Exception e) {
			Log.e(TAG, "bacaInfoEdisi error", e); //$NON-NLS-1$
		}
	}

	@Override
	public Kitab[] bacaInfoKitab() {
		try {
			Log.d(TAG, "bacaInfoKitab dipanggil");
			
			init();
			
			Kitab[] res = new Kitab[this.nkitab];
			
			int ukuran = lewatiSampeSeksi("infoKitab___"); //$NON-NLS-1$
			byte[] buf = new byte[ukuran];
			f.read(buf);
			BintexReader in = new BintexReader(new ByteArrayInputStream(buf));
			
			Log.d(TAG, "akan membaca " + this.nkitab + " kitab");
			for (int kitabPos = 0; kitabPos < res.length; kitabPos++) {
				Kitab k = new Kitab();
				
				while (true) {
					String key = in.readShortString();
					
					if (key.equals("versi")) { //$NON-NLS-1$
						int versi = in.readInt();
						if (versi != 1) throw new RuntimeException("Versi Kitab: " + versi + " tidak dikenal"); //$NON-NLS-1$ //$NON-NLS-2$
					} else if (key.equals("pos")) { //$NON-NLS-1$
						k.pos = in.readInt();
					} else if (key.equals("nama")) { //$NON-NLS-1$
						k.nama = in.readShortString();
					} else if (key.equals("judul")) { //$NON-NLS-1$
						k.judul = in.readShortString();
					} else if (key.equals("npasal")) { //$NON-NLS-1$
						k.npasal = in.readInt();
					} else if (key.equals("nayat")) { //$NON-NLS-1$
						k.nayat = new int[k.npasal];
						for (int i = 0; i < k.npasal; i++) {
							k.nayat[i] = in.readUint8();
						}
					} else if (key.equals("ayatLoncat")) { //$NON-NLS-1$
						// TODO di masa depan
						in.readInt();
					} else if (key.equals("pasal_offset")) { //$NON-NLS-1$
						k.pasal_offset = new int[k.npasal + 1]; // harus ada +1nya kalo YesPembaca
						for (int i = 0; i < k.pasal_offset.length; i++) {
							k.pasal_offset[i] = in.readInt();
						}
					} else if (key.equals("encoding")) { //$NON-NLS-1$
						// TODO di masa depan
						in.readInt();
					} else if (key.equals("offset")) { //$NON-NLS-1$
						k.offset = in.readInt();
					} else if (key.equals("end")) { //$NON-NLS-1$
						break;
					} else {
						Log.w(TAG, "ada key ga dikenal di kitab " + k + " di infoKitab: " + key); //$NON-NLS-1$ //$NON-NLS-2$
						break;
					}
				}
				
				res[kitabPos] = k;
			}
			
			return res;
		} catch (Exception e) {
			Log.e(TAG, "bacaInfoKitab error", e); //$NON-NLS-1$
			return null;
		}
	}
	
	@Override
	public String[] muatTeks(Kitab kitab, int pasal_1, boolean janganPisahAyat, boolean hurufKecil) {
		try {
			init();
			
			long seekTo = teks_dasarOffset;
			seekTo += kitab.offset;
			seekTo += kitab.pasal_offset[pasal_1 - 1];
			f.seek(seekTo);
			
			int length = kitab.pasal_offset[pasal_1] - kitab.pasal_offset[pasal_1 - 1];
			
			Log.d(TAG, "muatTeks kitab=" + kitab.nama + " pasal_1=" + pasal_1 + " offset=" + kitab.offset + " offset pasal: " + kitab.pasal_offset[pasal_1-1]); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			byte[] ba = new byte[length];
			f.read(ba);
			
			if (hurufKecil) {
				pembacaDecoder.hurufkecilkan(ba);
			}
			
			if (janganPisahAyat) {
				return new String[] {pembacaDecoder.jadikanStringTunggal(ba)};
			} else {
				return pembacaDecoder.pisahJadiAyat(ba);
			}
		} catch (Exception e) {
			Log.e(TAG, "muatTeks error", e); //$NON-NLS-1$
			return null;
		}
	}

	static String readNamaSeksi(RandomAccessFile f) throws IOException {
		byte[] buf = new byte[12];
		int read = f.read(buf);
		return read <= 0? null: new String(buf, 0);
	}

	static int readUkuranSeksi(RandomAccessFile f) throws IOException {
		return f.readInt();
	}

	@Override
	public IndexPerikop bacaIndexPerikop() {
		long wmulai = System.currentTimeMillis();
		try {
			init();
			
			int ukuran = lewatiSampeSeksi("perikopIndex"); //$NON-NLS-1$
			
			if (ukuran < 0) {
				Log.d(TAG, "Tidak ada index perikop");
				return null;
			}
			
			BintexReader in = new BintexReader(new RandomInputStream(f));
			return IndexPerikop.baca(in);
		} catch (Exception e) {
			Log.e(TAG, "bacaIndexPerikop error", e); //$NON-NLS-1$
			return null;
		} finally {
			Log.d(TAG, "Muat index perikop butuh ms: " + (System.currentTimeMillis() - wmulai)); //$NON-NLS-1$
		}
	}

	@Override
	public int muatPerikop(Edisi edisi, int kitab, int pasal, int[] xari, Blok[] xblok, int max) {
		try {
			init();
			
			Log.d(TAG, "muatPerikop dipanggil untuk kitab=" + kitab + " pasal_1=" + pasal); //$NON-NLS-1$ //$NON-NLS-2$
			IndexPerikop indexPerikop = edisi.getIndexPerikop();
			if (indexPerikop == null) {
				return 0; // ga ada perikop!
			}
	
			int ariMin = Ari.encode(kitab, pasal, 0);
			int ariMax = Ari.encode(kitab, pasal + 1, 0);
	
			int pertama = indexPerikop.cariPertama(ariMin, ariMax);
			if (pertama == -1) {
				return 0;
			}
	
			int kini = pertama;
			int res = 0;
			
			if (perikopBlok_dasarOffset != 0) {
				f.seek(perikopBlok_dasarOffset);
			} else {
				lewatiSampeSeksi("perikopBlok_"); //$NON-NLS-1$
				perikopBlok_dasarOffset = f.getFilePointer();
			}
			
			BintexReader in = new BintexReader(new RandomInputStream(f));
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
	
			return res;
		} catch (Exception e) {
			Log.e(TAG, "gagal muatPerikop", e); //$NON-NLS-1$
			return 0;
		}
	}
}
