package yuku.alkitab.model;

import java.io.*;
import java.util.Arrays;

import yuku.alkitab.U;
import yuku.bintex.BintexReader;
import android.content.Context;
import android.util.Log;

public class YesPembaca implements Pembaca {
	private static final String TAG = YesPembaca.class.getSimpleName();
	
	private String nf;
	private RandomAccessFile f;
	private long teks_dasarOffset;
	
	public YesPembaca(String nf) {
		this.nf = nf;
	}
	
	/**
	 * @return ukuran seksi
	 */
	private int lewatiSampeSeksi(String seksi) throws Exception {
		f.seek(8); // setelah header
		
		while (true) {
			String namaSeksi = readNamaSeksi(f);
			int ukuran = readUkuranSeksi(f);
			
			if (namaSeksi.equals(seksi)) {
				return ukuran;
			} else {
				Log.d(TAG, "seksi dilewati: " + namaSeksi);
				f.skipBytes(ukuran);
			}
		}
	}
	
	private synchronized void init() throws Exception {
		if (f == null) {
			f = new RandomAccessFile(nf, "r");
			f.seek(0);
			
			// cek header
			{
				byte[] buf = new byte[8];
				f.read(buf);
				if (!Arrays.equals(buf, new byte[] {(byte) 0x98, 0x58, 0x0d, 0x0a, 0x00, 0x5d, (byte) 0xe0, 0x01})) {
					throw new RuntimeException("Header ga betul. Ketemunya: " + Arrays.toString(buf));
				}
			}
			
			lewatiSampeSeksi("teks________");
			
			teks_dasarOffset = f.getFilePointer();
			Log.d(TAG, "teks_dasarOffset = " + teks_dasarOffset);
		}
	}
	
	@Override
	public Kitab[] bacaInfoKitab(Edisi edisi, Context context) {
		try {
			init();
			
			Kitab[] res = new Kitab[edisi.nkitab];
			
			int ukuran = lewatiSampeSeksi("infoKitab___");
			byte[] buf = new byte[ukuran];
			f.read(buf);
			BintexReader in = new BintexReader(new ByteArrayInputStream(buf));
			
			for (int kitabPos = 0; kitabPos < edisi.nkitab; kitabPos++) {
				Kitab k = new Kitab();
				
				while (true) {
					String key = in.readShortString();
					
					if (key.equals("pos")) {
						k.pos = in.readInt();
					} else if (key.equals("nama")) {
						k.nama = in.readShortString();
					} else if (key.equals("judul")) {
						k.judul = in.readShortString();
					} else if (key.equals("npasal")) {
						k.npasal = in.readInt();
					} else if (key.equals("nayat")) {
						k.nayat = new int[k.npasal];
						for (int i = 0; i < k.npasal; i++) {
							k.nayat[i] = in.readUint8();
						}
					} else if (key.equals("ayatLoncat")) {
						// TODO di masa depan
						in.readInt();
					} else if (key.equals("pasal_offset")) {
						k.pasal_offset = new int[k.npasal + 1]; // harus ada +1nya kalo YesPembaca
						for (int i = 0; i < k.pasal_offset.length; i++) {
							k.pasal_offset[i] = in.readInt();
						}
					} else if (key.equals("encoding")) {
						// TODO di masa depan
						in.readInt();
					} else if (key.equals("offset")) {
						k.offset = in.readInt();
					} else if (key.equals("end")) {
						break;
					} else {
						Log.w(TAG, "ada key ga dikenal di kitab " + k + " di infoKitab: " + key);
						break;
					}
				}
				
				res[kitabPos] = k;
			}
			
			return res;
		} catch (Exception e) {
			Log.e(TAG, "bacaInfoKitab error", e);
			return null;
		}
	}


	@Override
	public String[] muatTeks(Context context, Edisi edisi, Kitab kitab, int pasal_1, boolean janganPisahAyat, boolean hurufKecil) {
		try {
			init();
			
			long seekTo = teks_dasarOffset;
			seekTo += kitab.offset;
			seekTo += kitab.pasal_offset[pasal_1 - 1];
			f.seek(seekTo);
			
			int length = kitab.pasal_offset[pasal_1] - kitab.pasal_offset[pasal_1 - 1];
			
			Log.d(TAG, "muatTeks kitab=" + kitab.nama + " pasal_1=" + pasal_1 + " offset=" + kitab.offset + " offset pasal: " + kitab.pasal_offset[pasal_1-1]);
			byte[] ba = new byte[length];
			f.read(ba);
			
			if (hurufKecil) {
				U.hurufkecilkanAscii(ba);
			}
			
			if (janganPisahAyat) {
				return new String[] {new String(ba, 0)};
			} else {
				return U.pisahJadiAyatAscii(ba);
			}
		} catch (Exception e) {
			Log.e(TAG, "muatTeks error", e);
			return null;
		}
	}

	static String readNamaSeksi(RandomAccessFile f) throws IOException {
		byte[] buf = new byte[12];
		f.read(buf);
		return new String(buf, 0);
	}

	static int readUkuranSeksi(RandomAccessFile f) throws IOException {
		return f.readInt();
	}
}
