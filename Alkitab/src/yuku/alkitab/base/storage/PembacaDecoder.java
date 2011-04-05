package yuku.alkitab.base.storage;

import java.util.ArrayList;

import yuku.andoutil.Utf8Decoder;

public interface PembacaDecoder {
	void hurufkecilkan(byte[] ba);
	String[] pisahJadiAyat(byte[] ba);
	String jadikanStringTunggal(byte[] ba);

	public class Ascii implements PembacaDecoder {
		public void hurufkecilkan(byte[] ba) {
			int blen = ba.length;
			for (int i = 0; i < blen; i++) {
				byte b = ba[i];
				if (b <= (byte)'Z' && b >= (byte)'A') {
					ba[i] |= 0x20; // perhurufkecilkan
				}
			}
		}
		
		public String[] pisahJadiAyat(byte[] ba) {
			char[] ayatBuf = new char[4000];
			int i = 0;
	
			ArrayList<String> res = new ArrayList<String>(60);
			
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

		@Override
		public String jadikanStringTunggal(byte[] ba) {
			return new String(ba, 0);
		}
	}
	
	public class Utf8 implements PembacaDecoder {
		@Override
		public void hurufkecilkan(byte[] ba) {
			// TODO (keliatannya sih biarkan saja, ga usa jadiin huruf kecil)
		}

		@Override
		public String[] pisahJadiAyat(byte[] ba) {
			ArrayList<String> res = new ArrayList<String>(60);
			
			int len = ba.length;
			int dari = 0;
			for (int pos = 0; pos < len; pos++) {
				byte c = ba[pos];
				if (c == (byte)0x0a) {
					String satu = Utf8Decoder.toString(ba, dari, pos - dari);
					res.add(satu);
					dari = pos + 1;
				}
			}
			
			return res.toArray(new String[res.size()]);
		}

		@Override
		public String jadikanStringTunggal(byte[] ba) {
			return Utf8Decoder.toString(ba);
		}
	}
}
