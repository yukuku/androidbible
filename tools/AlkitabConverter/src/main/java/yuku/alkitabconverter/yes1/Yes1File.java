package yuku.alkitabconverter.yes1;

import yuku.alkitabconverter.yes1.Yes1File.PericopeData.Entry;
import yuku.bintex.BintexWriter;

import java.io.FileInputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

@Deprecated
public class Yes1File {
	private static final byte FILE_VERSI = 0x01;
	private static final String TAG = Yes1File.class.getSimpleName();
	
	byte[] FILE_HEADER = {(byte) 0x98, 0x58, 0x0d, 0x0a, 0x00, 0x5d, (byte) 0xe0, FILE_VERSI};
	
	public Seksi[] xseksi;
	
	public interface Seksi {
		byte[] nama();
		IsiSeksi isi();
	}
	
	public abstract class SeksiBernama implements Seksi {
		private byte[] nama;
		public SeksiBernama(String nama) {
			try {
				this.nama = nama.getBytes("ascii");
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
		public String shortName;
		public String longName;
		public String keterangan;
		public String locale;
		public int nkitab;
		public int perikopAda; // 0=ga ada, selain 0: nomer versi perikopIndex dan perikopBlok_
		public int encoding; // 1 = ascii; 2 = utf-8
		
		@Override
		public void toBytes(BintexWriter writer) throws Exception {
			writer.writeShortString("versi");
			writer.writeInt(versi);
			
			if (nama != null) {
				writer.writeShortString("nama");
				writer.writeShortString(nama);
			}
			
			writer.writeShortString("judul");
			writer.writeShortString(longName);
			
			if (shortName != null) {
				writer.writeShortString("shortName");
				writer.writeShortString(shortName);
			}
			
			writer.writeShortString("keterangan");
			writer.writeLongString(keterangan);
			
			writer.writeShortString("nkitab");
			writer.writeInt(nkitab);

			writer.writeShortString("perikopAda");
			writer.writeInt(perikopAda);
			
			writer.writeShortString("encoding"); // mulai versi 2 ada.
			writer.writeInt(encoding);

			if (locale != null) {
				writer.writeShortString("locale");
				writer.writeShortString(locale);
			}
			
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
			
			if (encoding != 0) {
				writer.writeShortString("encoding");
				writer.writeInt(encoding);
			}
			
			writer.writeShortString("offset");
			writer.writeInt(offset);
			
			if (pdbBookNumber != 0) {
				writer.writeShortString("pdbBookNumber");
				writer.writeInt(pdbBookNumber);
			}
			
			writer.writeShortString("end");
		}

		public static void nullToBytes(BintexWriter writer) throws Exception {
			writer.writeShortString("end");
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
	
	public static class PerikopBlok implements IsiSeksi {
		private final PericopeData data;

		public PerikopBlok(PericopeData data) {
			this.data = data;
		}

		@Override public void toBytes(BintexWriter writer) throws Exception {
			int offsetAwalSeksi = writer.getPos();
			for (Entry entry: data.entries) {
				int offsetAwalEntri = writer.getPos();
				
				/*
				 * Blok {
				 * uint8 versi = 2
				 * lstring judul
				 * uint8 nparalel
				 * sstring[nparalel] xparalel
				 * }
				 * 
				 * // OR
				 * 
				 * Blok {
				 * uint8 versi = 3
				 * autostring judul
				 * uint8 nparalel
				 * autostring[nparalel] xparalel
				 * }
				 */				
				
				writer.writeUint8(entry.block.version); // versi
				
				if (entry.block.version == 2) {
					writer.writeLongString(entry.block.title); // judul
					writer.writeUint8(entry.block.parallels == null? 0: entry.block.parallels.size()); // nparalel
					if (entry.block.parallels != null) { // xparalel
						for (String paralel: entry.block.parallels) {
							writer.writeShortString(paralel);
						}
					}
				} else if (entry.block.version == 3) {
					writer.writeAutoString(entry.block.title); // judul
					writer.writeUint8(entry.block.parallels == null? 0: entry.block.parallels.size()); // nparalel
					if (entry.block.parallels != null) { // xparalel
						for (String paralel: entry.block.parallels) {
							writer.writeAutoString(paralel);
						}
					}
				} else {
					throw new RuntimeException("pericope entry.block.version " + entry.block.version + " not supported yet");
				}
				
				entry.block._offset = offsetAwalEntri - offsetAwalSeksi;
			}
		}
	}
	
	public static class PerikopIndex implements IsiSeksi {
		private final PericopeData data;

		public PerikopIndex(PericopeData data) {
			this.data = data;
		}

		@Override public void toBytes(BintexWriter writer) throws Exception {
			writer.writeInt(data.entries.size()); // nentri
			
			for (Entry entry: data.entries) {
				if (entry.block._offset == -1) {
					throw new RuntimeException("offset entri perikop belum dihitung"); // $NON-NLS-1$
				}
				
				writer.writeInt(entry.ari);
				writer.writeInt(entry.block._offset);
			}
		}
	}
	
	public static class PericopeData {
		public static class Entry {
			public int ari;
			public Block block;
		}
		public static class Block {
			public int version;
			public String title;
			public List<String> parallels;
			
			int _offset = -1;
			
			public void addParallel(String parallel) {
				if (parallels == null) parallels = new ArrayList<>();
				parallels.add(parallel);
			}
		}
		
		public List<Entry> entries;
		
		public void addEntry(Entry e) {
			if (entries == null) entries = new ArrayList<>();
			entries.add(e);
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
				if (bisaLog()) Log.d(TAG, "[pos=" + pos + "] tulis nama seksi: " + new String(nama));
				os2.writeRaw(nama);
			}
			
			pos = file.getFilePointer();
			{
				byte[] palsu = {-1, -1, -1, -1};
				if (bisaLog()) Log.d(TAG, "[pos=" + pos + "] tulis placeholder ukuran");
				os2.writeRaw(palsu);
			}
			
			int posSebelumIsi = os2.getPos();
			if (bisaLog()) Log.d(TAG, "[pos=" + file.getFilePointer() + "] tulis isi seksi");
			seksi.isi().toBytes(os2);
			int posSesudahIsi = os2.getPos();
			int ukuranIsi = posSesudahIsi - posSebelumIsi;
			if (bisaLog()) Log.d(TAG, "[pos=" + file.getFilePointer() + "] isi seksi selesai ditulis, sebesar " + ukuranIsi);
			
			long posUntukMelanjutkan = file.getFilePointer();
			
			{
				file.seek(pos);
				if (bisaLog()) Log.d(TAG, "[pos=" + pos + "] tulis ukuran: " + ukuranIsi);
				os2.writeInt(ukuranIsi);
			}
			
			file.seek(posUntukMelanjutkan);
		}
		
		pos = file.getFilePointer();
		if (bisaLog()) Log.d(TAG, "[pos=" + pos + "] tulis penanda tidak ada seksi lagi (____________)");
		os2.writeRaw("____________".getBytes("ascii"));
		os2.close();
		pos = file.getFilePointer();
		if (bisaLog()) Log.d(TAG, "[pos=" + pos + "] selesai");
	}
	
	static Boolean bisaLog = null;
	private static boolean bisaLog() {
		if (bisaLog == null) {
			try {
				Class.forName("android.util.Log");
				bisaLog = true;
			} catch (Exception e) {
				bisaLog = false;
			}
		}
		return bisaLog;
	}

	// dummy
	static class Log {
		public static void d(final String tag, final String msg) {
			System.out.println(tag + "\t" + msg);
		}
	}
}
