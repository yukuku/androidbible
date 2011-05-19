package yuku.alkitab.yes;

import java.io.*;
import java.lang.annotation.*;

import yuku.bintex.BintexWriter;
import android.util.Log;

public class YesFile {
	private static final byte FILE_VERSI = 0x01;
	private static final String TAG = YesFile.class.getSimpleName();
	
	byte[] FILE_HEADER = {(byte) 0x98, 0x58, 0x0d, 0x0a, 0x00, 0x5d, (byte) 0xe0, FILE_VERSI};
	
	public Seksi[] xseksi;
	
	@Retention(RetentionPolicy.RUNTIME)
	@interface key {
		String value();
	}
	
	public interface Seksi {
		byte[] nama();
		IsiSeksi isi();
	}
	
	public interface IsiSeksi {
		void toBytes(BintexWriter writer) throws Exception;
	}
	
	public static abstract class InfoEdisi implements IsiSeksi {
		public int versi; // 1 
		public String nama;
		public String judul;
		public int nkitab;
		public int perikopAda; // 0=ga ada, selain 0: nomer versi perikopIndex dan perikopBlok_
		
		@Override
		public void toBytes(BintexWriter writer) throws Exception {
			writer.writeShortString("versi");
			writer.writeInt(versi);
			
			writer.writeShortString("nama");
			writer.writeShortString(nama);
			
			writer.writeShortString("judul");
			writer.writeShortString(judul);
			
			writer.writeShortString("nkitab");
			writer.writeInt(nkitab);

			writer.writeShortString("perikopAda");
			writer.writeInt(perikopAda);

			writer.writeShortString("end");
		}
	}
	
	public static class Kitab {
		public int versi; // 1; 2 mulai ada pdbBookNumber
		public int pos;
		public int pdbBookNumber;
		public String nama;
		public String judul;
		public int npasal;
		public int[] nayat;
		public int ayatLoncat;
		public int[] pasal_offset;
		public int encoding;
		public int offset;
		
		public void toBytes(BintexWriter writer) throws Exception {
			writer.writeShortString("versi");
			writer.writeInt(versi);
			
			writer.writeShortString("pos");
			writer.writeInt(pos);
			
			writer.writeShortString("nama");
			writer.writeShortString(nama);
			
			writer.writeShortString("judul");
			writer.writeShortString(judul);
			
			writer.writeShortString("npasal");
			writer.writeInt(npasal);
			
			writer.writeShortString("nayat");
			for (int a: nayat) {
				writer.writeUint8(a);
			}
			
			writer.writeShortString("ayatLoncat");
			writer.writeInt(ayatLoncat);
			
			writer.writeShortString("pasal_offset");
			for (int a: pasal_offset) {
				writer.writeInt(a);
			}
			
			writer.writeShortString("encoding");
			writer.writeInt(encoding);
			
			writer.writeShortString("offset");
			writer.writeInt(offset);
			
			if (pdbBookNumber != 0) {
				writer.writeShortString("pdbBookNumber");
				writer.writeInt(pdbBookNumber);
			}
			
			writer.writeShortString("end");
		}
	}
	
	public static class InfoKitab implements IsiSeksi {
		public Kitab[] xkitab;

		@Override
		public void toBytes(BintexWriter writer) throws Exception {
			for (Kitab kitab: xkitab) {
				kitab.toBytes(writer);
			}
		}
	}
	
	public static class Teks implements IsiSeksi {
		private final String encoding;

		public Teks(String encoding) {
			this.encoding = encoding;
			
		}
		
		int ayatLoncat = 0;
		
		public String[] xisi;
		
		@Override
		public void toBytes(BintexWriter writer) throws Exception {
			if (ayatLoncat != 0) {
				throw new RuntimeException("ayatLoncat ga 0");
			}
			
			for (String isi: xisi) {
				writer.writeRaw(isi.getBytes(encoding));
				writer.writeUint8('\n');
			}
		}
	}
	
	public static class NemplokSeksi implements IsiSeksi {
		private String nf;

		public NemplokSeksi(String nf) {
			this.nf = nf;
		}

		@Override
		public void toBytes(BintexWriter writer) throws Exception {
			FileInputStream in = new FileInputStream(nf);
			byte[] b = new byte[10000];
			while (true) {
				int r = in.read(b);
				if (r <= 0) break;
				writer.writeRaw(b, 0, r);
			}
			in.close();
		}
	}
	
	public void output(RandomAccessFile file) throws Exception {
		RandomOutputStream ros = new RandomOutputStream(file);
		BintexWriter os2 = new BintexWriter(ros);
		os2.writeRaw(FILE_HEADER);
		
		long pos = file.getFilePointer();
		for (Seksi seksi: xseksi) {
			pos = file.getFilePointer();
			{
				byte[] nama = seksi.nama();
				Log.d(TAG, "[pos=" + pos + "] tulis nama seksi: " + new String(nama));
				os2.writeRaw(nama);
			}
			
			pos = file.getFilePointer();
			{
				byte[] palsu = {-1, -1, -1, -1};
				Log.d(TAG, "[pos=" + pos + "] tulis placeholder ukuran");
				os2.writeRaw(palsu);
			}
			
			int posSebelumIsi = os2.getPos();
			Log.d(TAG, "[pos=" + file.getFilePointer() + "] tulis isi seksi");
			seksi.isi().toBytes(os2);
			int posSesudahIsi = os2.getPos();
			int ukuranIsi = posSesudahIsi - posSebelumIsi;
			Log.d(TAG, "[pos=" + file.getFilePointer() + "] isi seksi selesai ditulis, sebesar " + ukuranIsi);
			
			long posUntukMelanjutkan = file.getFilePointer();
			
			{
				file.seek(pos);
				Log.d(TAG, "[pos=" + pos + "] tulis ukuran: " + ukuranIsi);
				os2.writeInt(ukuranIsi);
			}
			
			file.seek(posUntukMelanjutkan);
		}
		
		pos = file.getFilePointer();
		Log.d(TAG, "[pos=" + pos + "] tulis penanda tidak ada seksi lagi (____________)");
		os2.writeRaw("____________".getBytes("ascii"));
		os2.close();
		pos = file.getFilePointer();
		Log.d(TAG, "[pos=" + pos + "] selesai");
	}
}
