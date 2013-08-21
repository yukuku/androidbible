package yuku.alkitab.base.storage;

public class Db {
	public static final String TABLE_Bookmark2 = "Bukmak2"; //$NON-NLS-1$
	public static final class Bookmark2 {
		public static final String ari = "ari"; //$NON-NLS-1$
		public static final String kind = "jenis"; //$NON-NLS-1$
		public static final String caption = "tulisan"; //$NON-NLS-1$
		public static final String addTime = "waktuTambah"; //$NON-NLS-1$
		public static final String modifyTime = "waktuUbah"; //$NON-NLS-1$
		public static final int kind_bookmark = 1;
		public static final int kind_note = 2;
		public static final int kind_highlight = 3;
	}
	
	
	public static final String TABLE_Devotion = "Renungan"; //$NON-NLS-1$
	public static final class Devotion {
		public static final String name = "nama"; //$NON-NLS-1$
		public static final String date = "tgl"; //$NON-NLS-1$
		public static final String header = "header"; //$NON-NLS-1$
		public static final String title = "judul"; //$NON-NLS-1$
		public static final String body = "isi"; //$NON-NLS-1$
		public static final String readyToUse = "siapPakai"; //$NON-NLS-1$
		public static final String touchTime = "waktuSentuh"; //$NON-NLS-1$
	}
	
	public static final String TABLE_Version = "Edisi"; //$NON-NLS-1$
	public static final class Version {
		public static final String shortName = "shortName"; //$NON-NLS-1$
		public static final String title = "judul"; //$NON-NLS-1$
		public static final String description = "keterangan"; //$NON-NLS-1$
		public static final String kind = "jenis"; //$NON-NLS-1$
		public static final String filename = "namafile"; //$NON-NLS-1$
		public static final String filename_originalpdb = "namafile_pdbasal"; //$NON-NLS-1$
		public static final String active = "aktif"; //$NON-NLS-1$
		public static final String ordering = "urutan"; //$NON-NLS-1$
		public static final int kind_internal = 1; // not used in db, only in models 
		public static final int kind_preset = 2; // not used in db, only in models 
		public static final int kind_yes = 3;
	}
	
	public static final String TABLE_Label = "Label"; //$NON-NLS-1$
	public static final class Label {
		public static final String title = "judul"; //$NON-NLS-1$
		public static final String ordering = "urutan"; //$NON-NLS-1$
		public static final String backgroundColor = "warnaLatar"; //$NON-NLS-1$
	}
	
	public static final String TABLE_Bookmark2_Label = "Bukmak2_Label"; //$NON-NLS-1$
	public static final class Bookmark2_Label {
		public static final String bookmark2_id = "bukmak2_id"; //$NON-NLS-1$
		public static final String label_id = "label_id"; //$NON-NLS-1$
	}

	public static final String TABLE_Progress_Mark = "ProgressMark";
	public static final class ProgressMark {
		public static final String caption = "caption";
	}

	public static final String TABLE_Progress_History = "progress_history";
	public static final class ProgressHistory {
		public static final String progress_mark_id = "progress_mark_id";
		public static final String ari = "ari";
		public static final String time = "time";
	}
}
