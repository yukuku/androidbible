package yuku.alkitab.yes;

import android.util.*;

import java.io.*;
import java.lang.annotation.*;

import yuku.bintex.*;

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
	
	public abstract class SeksiBernama implements Seksi {
		private byte[] nama;
		public SeksiBernama(String nama) {
			try {
				this.nama = nama.getBytes("ascii"); //$NON-NLS-1$
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		}
		@Override final public byte[] nama() {
			return nama;
		}
	}
	
	public interface IsiSeksi {
		void toBytes(BintexWriter writer) throws Exception;
	}
	
	public static abstract class InfoEdisi implements IsiSeksi {
		public int versi; // 1; 2 tambah encoding dan keterangan
		public String nama;
		public String judul;
		public String keterangan;
		public int nkitab;
		public int perikopAda; // 0=ga ada, selain 0: nomer versi perikopIndex dan perikopBlok_
		public int encoding; // 1 = ascii; 2 = utf-8
		
		@Override
		public void toBytes(BintexWriter writer) throws Exception {
			writer.writeShortString("versi"); //$NON-NLS-1$
			writer.writeInt(versi);
			
			writer.writeShortString("nama"); //$NON-NLS-1$
			writer.writeShortString(nama);
			
			writer.writeShortString("judul"); //$NON-NLS-1$
			writer.writeShortString(judul);
			
			writer.writeShortString("keterangan"); //$NON-NLS-1$
			writer.writeLongString(keterangan);
			
			writer.writeShortString("nkitab"); //$NON-NLS-1$
			writer.writeInt(nkitab);

			writer.writeShortString("perikopAda"); //$NON-NLS-1$
			writer.writeInt(perikopAda);
			
			writer.writeShortString("encoding"); // mulai versi 2 ada. //$NON-NLS-1$
			writer.writeInt(encoding);

			writer.writeShortString("end"); //$NON-NLS-1$
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
			writer.writeShortString("versi"); //$NON-NLS-1$
			writer.writeInt(versi);
			
			writer.writeShortString("pos"); //$NON-NLS-1$
			writer.writeInt(pos);
			
			writer.writeShortString("nama"); //$NON-NLS-1$
			writer.writeShortString(nama);
			
			writer.writeShortString("judul"); //$NON-NLS-1$
			writer.writeShortString(judul);
			
			writer.writeShortString("npasal"); //$NON-NLS-1$
			writer.writeInt(npasal);
			
			writer.writeShortString("nayat"); //$NON-NLS-1$
			for (int a: nayat) {
				writer.writeUint8(a);
			}
			
			writer.writeShortString("ayatLoncat"); //$NON-NLS-1$
			writer.writeInt(ayatLoncat);
			
			writer.writeShortString("pasal_offset"); //$NON-NLS-1$
			for (int a: pasal_offset) {
				writer.writeInt(a);
			}
			
			writer.writeShortString("encoding"); //$NON-NLS-1$
			writer.writeInt(encoding);
			
			writer.writeShortString("offset"); //$NON-NLS-1$
			writer.writeInt(offset);
			
			if (pdbBookNumber != 0) {
				writer.writeShortString("pdbBookNumber"); //$NON-NLS-1$
				writer.writeInt(pdbBookNumber);
			}
			
			writer.writeShortString("end"); //$NON-NLS-1$
		}

		public static void nullToBytes(BintexWriter writer) throws Exception {
			writer.writeShortString("end"); //$NON-NLS-1$
		}
	}
	
	public static class InfoKitab implements IsiSeksi {
		public Kitab[] xkitab;

		@Override
		public void toBytes(BintexWriter writer) throws Exception {
			for (Kitab kitab: xkitab) {
				if (kitab != null) {
					kitab.toBytes(writer);
				} else {
					Kitab.nullToBytes(writer);
				}
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
				throw new RuntimeException("ayatLoncat ga 0"); //$NON-NLS-1$
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
				Log.d(TAG, "[pos=" + pos + "] tulis nama seksi: " + new String(nama));  //$NON-NLS-1$//$NON-NLS-2$
				os2.writeRaw(nama);
			}
			
			pos = file.getFilePointer();
			{
				byte[] palsu = {-1, -1, -1, -1};
				Log.d(TAG, "[pos=" + pos + "] tulis placeholder ukuran"); //$NON-NLS-1$ //$NON-NLS-2$
				os2.writeRaw(palsu);
			}
			
			int posSebelumIsi = os2.getPos();
			Log.d(TAG, "[pos=" + file.getFilePointer() + "] tulis isi seksi"); //$NON-NLS-1$ //$NON-NLS-2$
			seksi.isi().toBytes(os2);
			int posSesudahIsi = os2.getPos();
			int ukuranIsi = posSesudahIsi - posSebelumIsi;
			Log.d(TAG, "[pos=" + file.getFilePointer() + "] isi seksi selesai ditulis, sebesar " + ukuranIsi);  //$NON-NLS-1$//$NON-NLS-2$
			
			long posUntukMelanjutkan = file.getFilePointer();
			
			{
				file.seek(pos);
				Log.d(TAG, "[pos=" + pos + "] tulis ukuran: " + ukuranIsi);  //$NON-NLS-1$//$NON-NLS-2$
				os2.writeInt(ukuranIsi);
			}
			
			file.seek(posUntukMelanjutkan);
		}
		
		pos = file.getFilePointer();
		Log.d(TAG, "[pos=" + pos + "] tulis penanda tidak ada seksi lagi (____________)");  //$NON-NLS-1$//$NON-NLS-2$
		os2.writeRaw("____________".getBytes("ascii")); //$NON-NLS-1$ //$NON-NLS-2$
		os2.close();
		pos = file.getFilePointer();
		Log.d(TAG, "[pos=" + pos + "] selesai");  //$NON-NLS-1$//$NON-NLS-2$
	}
}
