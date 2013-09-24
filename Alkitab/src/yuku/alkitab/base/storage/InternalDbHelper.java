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
		Log.d(TAG, "@@onCreate"); //$NON-NLS-1$
		
		createTableBookmark2(db);
		createIndexBookmark2(db);
		createTableDevotion(db);
		createIndexDevotion(db);
		createTableVersion(db);
		createIndexVersion(db);
		createTableLabel(db);
		createIndexLabel(db);
		createTableBookmark2_Label(db);
		createIndexBookmark2_Label(db);
		createTableProgressMark(db);
		createIndexProgressMark(db);
		insertDefaultProgressMarks(db);
		createTableProgressMarkHistory(db);
		createIndexProgressMarkHistory(db);
	}

	private void createTableBookmark2(SQLiteDatabase db) {
		db.execSQL("create table if not exists " + Db.TABLE_Bookmark2 + " (" + //$NON-NLS-1$
		"_id integer primary key autoincrement, " + //$NON-NLS-1$
		Db.Bookmark2.ari + " integer, " + //$NON-NLS-1$
		Db.Bookmark2.kind + " integer, " + //$NON-NLS-1$
		Db.Bookmark2.caption + " text, " + //$NON-NLS-1$
		Db.Bookmark2.addTime + " integer, " + //$NON-NLS-1$
		Db.Bookmark2.modifyTime + " integer)"); //$NON-NLS-1$
	}
	
	private void createIndexBookmark2(SQLiteDatabase db) {
		db.execSQL("create index if not exists index_201 on " + Db.TABLE_Bookmark2 + " (" + Db.Bookmark2.ari + ")"); //$NON-NLS-1$
		db.execSQL("create index if not exists index_202 on " + Db.TABLE_Bookmark2 + " (" + Db.Bookmark2.kind + ", " + Db.Bookmark2.ari + ")"); //$NON-NLS-1$
		db.execSQL("create index if not exists index_203 on " + Db.TABLE_Bookmark2 + " (" + Db.Bookmark2.kind + ", " + Db.Bookmark2.modifyTime + ")"); //$NON-NLS-1$
		db.execSQL("create index if not exists index_204 on " + Db.TABLE_Bookmark2 + " (" + Db.Bookmark2.kind + ", " + Db.Bookmark2.addTime + ")"); //$NON-NLS-1$
		db.execSQL("create index if not exists index_205 on " + Db.TABLE_Bookmark2 + " (" + Db.Bookmark2.kind + ", " + Db.Bookmark2.caption + " collate NOCASE)"); //$NON-NLS-1$
	}
	
	private void createTableDevotion(SQLiteDatabase db) {
		db.execSQL("create table if not exists " + Db.TABLE_Devotion + " (" + //$NON-NLS-1$
		"_id integer primary key autoincrement, " + //$NON-NLS-1$
		Db.Devotion.name + " text, " + //$NON-NLS-1$
		Db.Devotion.date + " text, " + //$NON-NLS-1$
		Db.Devotion.header + " text, " + //$NON-NLS-1$
		Db.Devotion.title + " text, " + //$NON-NLS-1$
		Db.Devotion.body + " text, " + //$NON-NLS-1$
		Db.Devotion.readyToUse + " integer," + //$NON-NLS-1$
		Db.Devotion.touchTime + " integer)"); //$NON-NLS-1$
	}

	private void createIndexDevotion(SQLiteDatabase db) {
		db.execSQL("create index if not exists index_101 on " + Db.TABLE_Devotion + " (" + Db.Devotion.name + ")"); //$NON-NLS-1$
		db.execSQL("create index if not exists index_102 on " + Db.TABLE_Devotion + " (" + Db.Devotion.name + ", " + Db.Devotion.date + ")"); //$NON-NLS-1$
		db.execSQL("create index if not exists index_103 on " + Db.TABLE_Devotion + " (" + Db.Devotion.date + ")"); //$NON-NLS-1$
		db.execSQL("create index if not exists index_104 on " + Db.TABLE_Devotion + " (" + Db.Devotion.touchTime + ")"); //$NON-NLS-1$
	}
	
	private void createTableVersion(SQLiteDatabase db) {
		db.execSQL("create table if not exists " + Db.TABLE_Version + " (" + //$NON-NLS-1$ //$NON-NLS-2$
		"_id integer primary key autoincrement, " + //$NON-NLS-1$
		Db.Version.shortName + " text, " + //$NON-NLS-1$
		Db.Version.title + " text, " + //$NON-NLS-1$
		Db.Version.kind + " text, " + //$NON-NLS-1$
		Db.Version.description + " text, " + //$NON-NLS-1$
		Db.Version.filename + " text, " + //$NON-NLS-1$
		Db.Version.filename_originalpdb + " text, " + //$NON-NLS-1$
		Db.Version.active + " integer, " + //$NON-NLS-1$
		Db.Version.ordering + " integer)"); //$NON-NLS-1$
	}
	
	private void createIndexVersion(SQLiteDatabase db) {
		db.execSQL("create index if not exists index_301 on " + Db.TABLE_Version + " (" + Db.Version.ordering + ")"); //$NON-NLS-1$

		// make sure these two matches the ones on addShortNameColumnAndIndexToVersion()
		db.execSQL("create index if not exists index_302 on " + Db.TABLE_Version + " (" + Db.Version.shortName + ")"); //$NON-NLS-1$
		db.execSQL("create index if not exists index_303 on " + Db.TABLE_Version + " (" + Db.Version.title + ")"); //$NON-NLS-1$
	}
	
	private void createTableLabel(SQLiteDatabase db) {
		db.execSQL("create table if not exists " + Db.TABLE_Label + " (" + //$NON-NLS-1$ //$NON-NLS-2$
		"_id integer primary key autoincrement, " + //$NON-NLS-1$
		Db.Label.title + " text, " + //$NON-NLS-1$
		Db.Label.ordering + " integer, " + //$NON-NLS-1$
		Db.Label.backgroundColor + " text)"); //$NON-NLS-1$
	}
	
	private void createIndexLabel(SQLiteDatabase db) {
		db.execSQL("create index if not exists index_401 on " + Db.TABLE_Label + " (" + Db.Label.ordering + ")"); //$NON-NLS-1$
	}
	
	private void createTableBookmark2_Label(SQLiteDatabase db) {
		db.execSQL("create table if not exists " + Db.TABLE_Bookmark2_Label + " (" + //$NON-NLS-1$ //$NON-NLS-2$
		"_id integer primary key autoincrement, " + //$NON-NLS-1$
		Db.Bookmark2_Label.bookmark2_id + " integer, " + //$NON-NLS-1$
		Db.Bookmark2_Label.label_id + " integer)"); //$NON-NLS-1$
	}
	
	private void createIndexBookmark2_Label(SQLiteDatabase db) {
		db.execSQL("create index if not exists index_501 on "        + Db.TABLE_Bookmark2_Label + " (" + Db.Bookmark2_Label.bookmark2_id + ")"); //$NON-NLS-1$
		db.execSQL("create index if not exists index_502 on "        + Db.TABLE_Bookmark2_Label + " (" + Db.Bookmark2_Label.label_id + ")"); //$NON-NLS-1$
		db.execSQL("create unique index if not exists index_503 on " + Db.TABLE_Bookmark2_Label + " (" + Db.Bookmark2_Label.bookmark2_id + ", " + Db.Bookmark2_Label.label_id + ")"); //$NON-NLS-1$
	}

	private void createTableProgressMark(SQLiteDatabase db) {
		db.execSQL("create table if not exists " + Db.TABLE_ProgressMark + " (" +
		"_id integer primary key autoincrement, " +
		Db.ProgressMark.preset_id + " integer, " +
		Db.ProgressMark.caption + " text, " +
		Db.ProgressMark.ari + " integer, " +
		Db.ProgressMark.modifyTime + " integer)");
	}

	private void createIndexProgressMark(SQLiteDatabase db) {
		db.execSQL("create index if not exists index_601 on " + Db.TABLE_ProgressMark + " (" + Db.ProgressMark.preset_id + ")"); //$NON-NLS-1$
	}

	private void createTableProgressMarkHistory(SQLiteDatabase db) {
		db.execSQL("create table if not exists " + Db.TABLE_ProgressMarkHistory + " (" +
		"_id integer primary key autoincrement, " +
		Db.ProgressMarkHistory.progress_mark_preset_id + " integer, " +
		Db.ProgressMarkHistory.progress_mark_caption + " integer, " +
		Db.ProgressMarkHistory.ari + " integer, " +
		Db.ProgressMarkHistory.createTime + " integer)");
	}

	private void createIndexProgressMarkHistory(SQLiteDatabase db) {
		db.execSQL("create index if not exists index_701 on " + Db.TABLE_ProgressMarkHistory + " (" + Db.ProgressMarkHistory.progress_mark_preset_id + ", " + Db.ProgressMarkHistory.createTime + ")");
	}

	private void insertDefaultProgressMarks(SQLiteDatabase db) {
		ContentValues cv = new ContentValues();
		cv.put(Db.ProgressMark.ari, 0);
		for (int i = 0; i < 5; i++) {
			cv.put(Db.ProgressMark.preset_id, i);
			db.insert(Db.TABLE_ProgressMark, null, cv);
		}
	}
	
	@Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		Log.d(TAG, "@@onUpgrade oldVersion=" + oldVersion + " newVersion=" + newVersion); //$NON-NLS-1$ //$NON-NLS-2$
		
		if (oldVersion <= 23) {
			convertFromBookmarkToBookmark2(db);
		}

		if (oldVersion <= 50) {
			// new table Version
			createTableVersion(db);
			createIndexVersion(db);
		}
		
		if (oldVersion <= 69) { // 70: 2.0.0
			// new tables Label and Bookmark2_Label
			createTableLabel(db);
			createIndexLabel(db);
			createTableBookmark2_Label(db);
			createIndexBookmark2_Label(db);
		}
		
		if (oldVersion <= 70) { // 71: 2.0.0 too
			createIndexBookmark2(db);
		}
		
		if (oldVersion <= 71) { // 72: 2.0.0 too
			createIndexDevotion(db);
		}
		
		if (oldVersion > 50 && oldVersion <= 102) { // 103: 2.7.1
			addShortNameColumnAndIndexToVersion(db);
		}

		if (oldVersion <= 126) { // 127: 3.2.0
			createTableProgressMark(db);
			createIndexProgressMark(db);
			insertDefaultProgressMarks(db);
			createTableProgressMarkHistory(db);
			createIndexProgressMarkHistory(db);
		}
	}

	private void addShortNameColumnAndIndexToVersion(SQLiteDatabase db) {
		db.execSQL("alter table " + Db.TABLE_Version + " add column " + Db.Version.shortName + " text");

		// make sure these two matches the ones in createIndexVersion()
		db.execSQL("create index if not exists index_302 on " + Db.TABLE_Version + " (" + Db.Version.shortName + ")"); //$NON-NLS-1$
		db.execSQL("create index if not exists index_303 on " + Db.TABLE_Version + " (" + Db.Version.title + ")"); //$NON-NLS-1$
	}

	// Legacy code, so most of them are not in English
	private void convertFromBookmarkToBookmark2(SQLiteDatabase db) {
		String TABEL_Bukmak = "Bukmak"; //$NON-NLS-1$
		class Bukmak {
			public static final String alamat = "alamat"; //$NON-NLS-1$
			public static final String waktuTambah = "waktuTambah"; //$NON-NLS-1$
			public static final String kitab = "kitab"; //$NON-NLS-1$
			public static final String pasal = "pasal"; //$NON-NLS-1$
			public static final String ayat = "ayat"; //$NON-NLS-1$
		}

		createTableBookmark2(db);
		createIndexBookmark2(db);
		
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
