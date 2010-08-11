package yuku.yes;

import java.io.*;
import java.lang.annotation.*;
import java.lang.reflect.Field;

import yuku.bintex.BintexWriter;

public class YesFile {
	private static final byte FILE_VERSI = 0x01;
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
	
	public static class InfoEdisi implements IsiSeksi {
		@key("nama") protected String nama;
		@key("judul") protected String judul;
		@key("format") protected int format;
		@key("nkitab") protected int nkitab;
		
		@Override
		public void toBytes(BintexWriter writer) throws Exception {
			Field[] fields = InfoEdisi.class.getDeclaredFields();
			for (Field f: fields) {
				key annotation = f.getAnnotation(key.class);
				if (annotation != null) {
					writer.writeShortString(annotation.value());
					if (f.getType() == int.class) {
						writer.writeInt(f.getInt(this));
					} else if (f.getType() == String.class) {
						writer.writeShortString((String) f.get(this));
					} else {
						throw new RuntimeException("type ga dikenal: " + f.getType());
					}
				}
			}
			writer.writeShortString("end");
		}
	}
	
	public static class Kitab {
		@key("pos") public int pos;
		@key("nama") public String nama;
		@key("judul") public String judul;
		@key("npasal") public int npasal;
		@key("nayat") public int[] nayat;
		@key("ayatLoncat") public int ayatLoncat;
		@key("pasal_offset") public int[] pasal_offset;
		@key("encoding") public int encoding;
		@key("offset") public int offset;
		
		public void toBytes(BintexWriter writer) throws Exception {
			Field[] fields = Kitab.class.getDeclaredFields();
			for (Field f: fields) {
				key annotation = f.getAnnotation(key.class);
				if (annotation != null) {
					writer.writeShortString(annotation.value());
					if (f.getType() == int.class) {
						writer.writeInt(f.getInt(this));
					} else if (f.getType() == String.class) {
						writer.writeShortString((String) f.get(this));
					} else if (f.getType() == int[].class) {
						for (int a: (int[])f.get(this)) {
							if (f.getName().equals("nayat")) {
								writer.writeUint8(a);
							} else {
								writer.writeInt(a);
							}
						}
					} else {
						throw new RuntimeException("type ga dikenal: " + f.getType());
					}
				}
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
		int ayatLoncat = 0;
		
		public String[] xisi;
		
		@Override
		public void toBytes(BintexWriter writer) throws Exception {
			if (ayatLoncat != 0) {
				throw new RuntimeException("ayatLoncat ga 0");
			}
			
			for (String isi: xisi) {
				writer.writeRaw(isi.getBytes("ascii"));
				writer.writeUint8('\n');
			}
		}
	}
	
	public void output(OutputStream os) throws Exception {
		BintexWriter os2 = new BintexWriter(os);
		os2.writeRaw(FILE_HEADER);
		
		for (Seksi seksi: xseksi) {
			os2.writeRaw(seksi.nama());
			
			ByteArrayOutputStream buf = new ByteArrayOutputStream();
			
			BintexWriter writer = new BintexWriter(buf);
			seksi.isi().toBytes(writer);
			writer.close();
			
			int ukuran = buf.size();
			os2.writeInt(ukuran);
			
			os2.writeRaw(buf.toByteArray());
		}
		
		os.write("____________".getBytes("ascii"));
	}
}
