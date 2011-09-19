package yuku.alkitab.base.storage;

public class Db {
	@Deprecated public static final String TABEL_Bukmak = "Bukmak"; //$NON-NLS-1$
	public static final class Bukmak {
		@Deprecated public static final String alamat = "alamat"; //$NON-NLS-1$
		@Deprecated public static final String cuplikan = "cuplikan"; //$NON-NLS-1$
		@Deprecated public static final String waktuTambah = "waktuTambah"; //$NON-NLS-1$
		@Deprecated public static final String kitab = "kitab"; //$NON-NLS-1$
		@Deprecated public static final String pasal = "pasal"; //$NON-NLS-1$
		@Deprecated public static final String ayat = "ayat"; //$NON-NLS-1$
	}
	
	public static final String TABEL_Bukmak2 = "Bukmak2"; //$NON-NLS-1$
	public static final class Bukmak2 {
		public static final String ari = "ari"; //$NON-NLS-1$
		public static final String jenis = "jenis"; //$NON-NLS-1$
		public static final String tulisan = "tulisan"; //$NON-NLS-1$
		public static final String waktuTambah = "waktuTambah"; //$NON-NLS-1$
		public static final String waktuUbah = "waktuUbah"; //$NON-NLS-1$
		public static final int jenis_bukmak = 1;
		public static final int jenis_catatan = 2;
		public static final int jenis_stabilo = 3;
	}
	
	
	public static final String TABEL_Renungan = "Renungan"; //$NON-NLS-1$
	public static final class Renungan {
		public static final String nama = "nama"; //$NON-NLS-1$
		public static final String tgl = "tgl"; //$NON-NLS-1$
		public static final String header = "header"; //$NON-NLS-1$
		public static final String judul = "judul"; //$NON-NLS-1$
		public static final String isi = "isi"; //$NON-NLS-1$
		public static final String siapPakai = "siapPakai"; //$NON-NLS-1$
		public static final String waktuSentuh = "waktuSentuh"; //$NON-NLS-1$
	}
	
	public static final String TABEL_Edisi = "Edisi"; //$NON-NLS-1$
	public static final class Edisi {
		public static final String judul = "judul"; //$NON-NLS-1$
		public static final String keterangan = "keterangan"; //$NON-NLS-1$
		public static final String jenis = "jenis"; //$NON-NLS-1$
		public static final String namafile = "namafile"; //$NON-NLS-1$
		public static final String namafile_pdbasal = "namafile_pdbasal"; //$NON-NLS-1$
		public static final String aktif = "aktif"; //$NON-NLS-1$
		public static final String urutan = "urutan"; //$NON-NLS-1$
		public static final int jenis_internal = 1; // ga dipake di db, hanya dipake di model 
		public static final int jenis_preset = 2; // ga dipake di db, hanya dipake di model 
		public static final int jenis_yes = 3;
	}
	
	public static final String TABEL_Label = "Label"; //$NON-NLS-1$
	public static final class Label {
		public static final String judul = "judul"; //$NON-NLS-1$
		public static final String urutan = "urutan"; //$NON-NLS-1$
		public static final String warnaLatar = "warnaLatar"; //$NON-NLS-1$
	}
	
	public static final String TABEL_Bukmak2_Label = "Bukmak2_Label"; //$NON-NLS-1$
	public static final class Bukmak2_Label {
		public static final String bukmak2_id = "bukmak2_id"; //$NON-NLS-1$
		public static final String label_id = "label_id"; //$NON-NLS-1$
	}
}
