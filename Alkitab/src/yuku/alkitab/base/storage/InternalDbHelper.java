package yuku.alkitab.base.storage;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import yuku.afw.App;
import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.storage.Db.Bookmark2;

public class InternalDbHelper extends SQLiteOpenHelper {
	public static final String TAG = InternalDbHelper.class.getSimpleName();
	
	public InternalDbHelper(Context context) {
		super(context, "AlkitabDb", null, App.getVersionCode()); //$NON-NLS-1$
	}
	
	@Override
	public void onOpen(SQLiteDatabase db) {
		// db.execSQL("PRAGMA synchronous=OFF");
	}

	@Override public void onCreate(SQLiteDatabase db) {
		Log.d(TAG, "onCreate dipanggil"); //$NON-NLS-1$
		
		createTableBukmak2(db);
		createIndexBukmak2(db);
		createTableRenungan(db);
		createIndexRenungan(db);
		createTableEdisi(db);
		createIndexEdisi(db);
		createTableLabel(db);
		createIndexLabel(db);
		createTableBukmak2_Label(db);
		createIndexBukmak2_Label(db);
		createTableProgressMark(db);
		insertDefaultProgressMarks(db);
	}

	private void createTableBukmak2(SQLiteDatabase db) {
		db.execSQL(String.format("create table if not exists %s (" + //$NON-NLS-1$
			"_id integer primary key autoincrement, " + //$NON-NLS-1$
			"%s integer, " + // ari //$NON-NLS-1$
			"%s integer, " + // jenis //$NON-NLS-1$
			"%s text, " + // tulisan //$NON-NLS-1$
			"%s integer, " + // waktuTambah //$NON-NLS-1$
			"%s integer)", // waktuUbah //$NON-NLS-1$
			Db.TABLE_Bookmark2, Db.Bookmark2.ari, Db.Bookmark2.kind, Db.Bookmark2.caption, Db.Bookmark2.addTime, Db.Bookmark2.modifyTime));
	}
	
	private void createIndexBukmak2(SQLiteDatabase db) {
		// index Bukmak2(ari)
		db.execSQL(String.format("create index if not exists index_201 on %s (%s)", Db.TABLE_Bookmark2, Db.Bookmark2.ari)); //$NON-NLS-1$
		// index Bukmak2(jenis,ari)
		db.execSQL(String.format("create index if not exists index_202 on %s (%s, %s)", Db.TABLE_Bookmark2, Db.Bookmark2.kind, Db.Bookmark2.ari)); //$NON-NLS-1$
		// index Bukmak2(jenis,waktuUbah)
		db.execSQL(String.format("create index if not exists index_203 on %s (%s, %s)", Db.TABLE_Bookmark2, Db.Bookmark2.kind, Db.Bookmark2.modifyTime)); //$NON-NLS-1$
		// index Bukmak2(jenis,waktuTambah)
		db.execSQL(String.format("create index if not exists index_204 on %s (%s, %s)", Db.TABLE_Bookmark2, Db.Bookmark2.kind, Db.Bookmark2.addTime)); //$NON-NLS-1$
		// index Bukmak2(jenis,tulisan)
		db.execSQL(String.format("create index if not exists index_205 on %s (%s, %s collate NOCASE)", Db.TABLE_Bookmark2, Db.Bookmark2.kind, Db.Bookmark2.caption)); //$NON-NLS-1$
	}
	
	private void createTableRenungan(SQLiteDatabase db) {
		db.execSQL(String.format("create table if not exists %s (" + //$NON-NLS-1$
			"_id integer primary key autoincrement, " + //$NON-NLS-1$
			"%s text, " + // nama //$NON-NLS-1$
			"%s text, " + // tgl (yyyymmdd) //$NON-NLS-1$
			"%s text, " + // header //$NON-NLS-1$
			"%s text, " + // judul //$NON-NLS-1$
			"%s text, " + // isi //$NON-NLS-1$
			"%s integer," + // siap pakai //$NON-NLS-1$
			"%s integer)", // waktuSentuh //$NON-NLS-1$
			Db.TABLE_Devotion, Db.Devotion.name, Db.Devotion.date, Db.Devotion.header, Db.Devotion.title, Db.Devotion.body, Db.Devotion.readyToUse, Db.Devotion.touchTime));
	}

	private void createIndexRenungan(SQLiteDatabase db) {
		// index Renungan(nama)
		db.execSQL(String.format("create index if not exists index_101 on %s (%s)", Db.TABLE_Devotion, Db.Devotion.name)); //$NON-NLS-1$
		// index Renungan(nama,tgl)
		db.execSQL(String.format("create index if not exists index_102 on %s (%s, %s)", Db.TABLE_Devotion, Db.Devotion.name, Db.Devotion.date)); //$NON-NLS-1$
		// index Renungan(tgl)
		db.execSQL(String.format("create index if not exists index_103 on %s (%s)", Db.TABLE_Devotion, Db.Devotion.date)); //$NON-NLS-1$
		// index Renungan(waktuSentuh)
		db.execSQL(String.format("create index if not exists index_104 on %s (%s)", Db.TABLE_Devotion, Db.Devotion.touchTime)); //$NON-NLS-1$
	}
	
	private void createTableEdisi(SQLiteDatabase db) {
		db.execSQL("create table if not exists " + Db.TABLE_Version + " (" + //$NON-NLS-1$ //$NON-NLS-2$
			"_id integer primary key autoincrement, " + //$NON-NLS-1$
			Db.Version.shortName + " text, " + //$NON-NLS-1$
			Db.Version.title + " text, " + // judul (keliatan sama user) //$NON-NLS-1$
			Db.Version.kind + " text, " + // jenis (yes) //$NON-NLS-1$
			Db.Version.description + " text, " + // keterangan tambahan, mungkin bisa diedit user kalo perlu //$NON-NLS-1$
			Db.Version.filename + " text, " + // nama file di sd card (full path) //$NON-NLS-1$
			Db.Version.filename_originalpdb + " text, " + // nama file kalau bekas dikonvert dari pdb (nama doang) //$NON-NLS-1$
			Db.Version.active + " integer, " + // tampilkan di daftar edisi? //$NON-NLS-1$
			Db.Version.ordering + " integer)"); //$NON-NLS-1$
		
	}
	
	private void createIndexEdisi(SQLiteDatabase db) {
		// index Edisi(urutan)
		db.execSQL(String.format("create index if not exists index_301 on %s (%s)", Db.TABLE_Version, Db.Version.ordering)); //$NON-NLS-1$
		
		// index Edisi(shortName)
		db.execSQL(String.format("create index if not exists index_302 on %s (%s)", Db.TABLE_Version, Db.Version.shortName)); //$NON-NLS-1$
		
		// index Edisi(judul)
		db.execSQL(String.format("create index if not exists index_303 on %s (%s)", Db.TABLE_Version, Db.Version.title)); //$NON-NLS-1$
	}
	
	private void createTableLabel(SQLiteDatabase db) {
		db.execSQL("create table if not exists " + Db.TABLE_Label + " (" + //$NON-NLS-1$ //$NON-NLS-2$
			"_id integer primary key autoincrement, " + //$NON-NLS-1$
			Db.Label.title + " text, " + //$NON-NLS-1$
			Db.Label.ordering + " integer, " + //$NON-NLS-1$
			Db.Label.backgroundColor + " text)"); //$NON-NLS-1$
	}
	
	private void createIndexLabel(SQLiteDatabase db) {
		// index Label(urutan)
		db.execSQL(String.format("create index if not exists index_401 on %s (%s)", Db.TABLE_Label, Db.Label.ordering)); //$NON-NLS-1$
	}
	
	private void createTableBukmak2_Label(SQLiteDatabase db) {
		db.execSQL("create table if not exists " + Db.TABLE_Bookmark2_Label + " (" + //$NON-NLS-1$ //$NON-NLS-2$
				"_id integer primary key autoincrement, " + //$NON-NLS-1$
				Db.Bookmark2_Label.bookmark2_id + " integer, " + //$NON-NLS-1$
				Db.Bookmark2_Label.label_id + " integer)"); //$NON-NLS-1$
	}
	
	private void createIndexBukmak2_Label(SQLiteDatabase db) {
		// index Bukmak2_Label(bukmak2_id)
		db.execSQL(String.format("create index if not exists index_501 on %s (%s)", Db.TABLE_Bookmark2_Label, Db.Bookmark2_Label.bookmark2_id)); //$NON-NLS-1$
		// index Bukmak2_Label(label_id)
		db.execSQL(String.format("create index if not exists index_502 on %s (%s)", Db.TABLE_Bookmark2_Label, Db.Bookmark2_Label.label_id)); //$NON-NLS-1$
		// unique index Bukmak2_Label(bukmak2_id, label_id)
		db.execSQL(String.format("create unique index if not exists index_503 on %s (%s, %s)", Db.TABLE_Bookmark2_Label, Db.Bookmark2_Label.bookmark2_id, Db.Bookmark2_Label.label_id)); //$NON-NLS-1$
		
	}

	private void createTableProgressMark(SQLiteDatabase db) {
		db.execSQL("create table if not exists " + Db.TABLE_ProgressMark + " (" +
				"_id integer primary key autoincrement, " +
				Db.ProgressMark.caption + " text, " +
				Db.ProgressMark.ari + " integer, " +
				Db.ProgressMark.modifyTime + " integer)");
	}

	private void insertDefaultProgressMarks(SQLiteDatabase db) {
		ContentValues cv = new ContentValues();
		cv.put(Db.ProgressMark.ari, 0);
		for (int i = 0; i < 5; i++) {
			db.insert(Db.TABLE_ProgressMark, null, cv);
		}
	}
	
	@Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		Log.d(TAG, "onUpgrade dipanggil, oldVersion=" + oldVersion + " newVersion=" + newVersion); //$NON-NLS-1$ //$NON-NLS-2$
		
		if (oldVersion <= 23) {
			// konvert dari Bukmak ke Bukmak2
			convertFromBookmarkToBookmark2(db);
		}

		if (oldVersion <= 50) {
			// tambah tabel Edisi
			createTableEdisi(db);
			createIndexEdisi(db);
		}
		
		if (oldVersion <= 69) { // 70: 2.0.0
			// tambah tabel Label dan Bukmak2_Label
			createTableLabel(db);
			createIndexLabel(db);
			createTableBukmak2_Label(db);
			createIndexBukmak2_Label(db);
		}
		
		if (oldVersion <= 70) { // 71: 2.0.0 juga
			// tambah index di Bukmak2
			createIndexBukmak2(db);
		}
		
		if (oldVersion <= 71) { // 72: 2.0.0 juga
			// tambah index di Renungan
			createIndexRenungan(db);
		}
		
		if (oldVersion <= 102) { // 103: 2.7.1 
			addShortNameColumnAndIndexToEdisi(db);
		}

		if (oldVersion <= 126) { // 127: 3.2.0
			createTableProgressMark(db);
			insertDefaultProgressMarks(db);
		}
	}

	private void addShortNameColumnAndIndexToEdisi(SQLiteDatabase db) {
		db.execSQL("alter table " + Db.TABLE_Version + " add column " + Db.Version.shortName + " text");
		
		// index Edisi(shortName)
		db.execSQL(String.format("create index if not exists index_302 on %s (%s)", Db.TABLE_Version, Db.Version.shortName)); //$NON-NLS-1$
		
		// index Edisi(judul)
		db.execSQL(String.format("create index if not exists index_303 on %s (%s)", Db.TABLE_Version, Db.Version.title)); //$NON-NLS-1$
	}

	private void convertFromBookmarkToBookmark2(SQLiteDatabase db) {
		String TABEL_Bukmak = "Bukmak"; //$NON-NLS-1$
		class Bukmak {
			public static final String alamat = "alamat"; //$NON-NLS-1$
			public static final String waktuTambah = "waktuTambah"; //$NON-NLS-1$
			public static final String kitab = "kitab"; //$NON-NLS-1$
			public static final String pasal = "pasal"; //$NON-NLS-1$
			public static final String ayat = "ayat"; //$NON-NLS-1$
		}

		createTableBukmak2(db);
		createIndexBukmak2(db);
		
		// pindahin data dari Bukmak ke Bukmak2
		db.beginTransaction();
		try {
			Cursor cursor = db.query(TABEL_Bukmak, new String[] {Bukmak.alamat, Bukmak.kitab, Bukmak.pasal, Bukmak.ayat, Bukmak.waktuTambah}, null, null, null, null, null);

			int kolom_alamat = cursor.getColumnIndex(Bukmak.alamat);
			int kolom_kitab = cursor.getColumnIndex(Bukmak.kitab);
			int kolom_pasal = cursor.getColumnIndex(Bukmak.pasal);
			int kolom_ayat = cursor.getColumnIndex(Bukmak.ayat);
			int kolom_waktuTambah = cursor.getColumnIndex(Bukmak.waktuTambah);
			
			ContentValues cv = new ContentValues();
			
			// default
			cv.put(Bookmark2.kind, Bookmark2.kind_bookmark);

			while (true) {
		    	boolean more = cursor.moveToNext();
		    	if (!more) {
		    		break;
		    	}

		    	cv.put(Bookmark2.caption, cursor.getString(kolom_alamat));
		    	
		    	int kitab = cursor.getInt(kolom_kitab);
		    	int pasal = cursor.getInt(kolom_pasal);
		    	int ayat = cursor.getInt(kolom_ayat);
		    	int ari = Ari.encode(kitab, pasal, ayat);
		    	
		    	cv.put(Bookmark2.ari, ari);
		    	Integer waktu = Integer.valueOf(cursor.getString(kolom_waktuTambah));
		    	cv.put(Bookmark2.addTime, waktu);
		    	cv.put(Bookmark2.modifyTime, waktu);
				db.insertOrThrow(Db.TABLE_Bookmark2, null, cv);
		    }
		    cursor.close();
			
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}
}
