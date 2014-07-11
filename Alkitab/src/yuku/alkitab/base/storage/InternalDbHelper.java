package yuku.alkitab.base.storage;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.v4.util.LongSparseArray;
import android.util.Log;
import yuku.afw.App;
import yuku.alkitab.model.util.Gid;

public class InternalDbHelper extends SQLiteOpenHelper {
	public static final String TAG = InternalDbHelper.class.getSimpleName();
	
	public InternalDbHelper(Context context) {
		super(context, "AlkitabDb", null, App.getVersionCode());
	}
	
	@Override
	public void onOpen(SQLiteDatabase db) {
		// db.execSQL("PRAGMA synchronous=OFF");
	}

	@Override public void onCreate(SQLiteDatabase db) {
		Log.d(TAG, "@@onCreate");
		
		createTableMarker(db);
		createIndexMarker(db);
		createTableDevotion(db);
		createIndexDevotion(db);
		createTableEdisi(db);
		createTableLabel(db);
		createIndexLabel(db);
		createTableMarker_Label(db);
		createIndexMarker_Label(db);
		createTableProgressMark(db);
		createIndexProgressMark(db);
		insertDefaultProgressMarks(db);
		createTableProgressMarkHistory(db);
		createIndexProgressMarkHistory(db);
		createTableReadingPlan(db);
		createTableReadingPlanProgress(db);
		createIndexReadingPlanProgress(db);
		createTableVersion(db);
		createIndexVersion(db);
	}

	@Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		Log.d(TAG, "@@onUpgrade oldVersion=" + oldVersion + " newVersion=" + newVersion);

		// No more support for Bookmark (Bukmak) version 1 table (last published: 2010-06-14)
		// if (oldVersion <= 23) {
		//	convertFromBookmarkToBookmark2(db);
		// }

		if (oldVersion <= 50) {
			// new table Version
			createTableEdisi(db);
			createIndexVersion(db);
		}

		if (oldVersion <= 69) { // 70: 2.0.0
			// new tables Label and Marker_Label
			createTableLabel(db);
			createIndexLabel(db);
			createTableMarker_Label(db);
			createIndexMarker_Label(db);
		}

		if (oldVersion <= 70) { // 71: 2.0.0 too
			createIndexMarker(db);
		}

		if (oldVersion <= 71) { // 72: 2.0.0 too
			createIndexDevotion(db);
		}

		if (oldVersion > 50 && oldVersion <= 102) { // 103: 2.7.1
			addShortNameColumnAndIndexToEdisi(db);
		}

		if (oldVersion <= 126) { // 127: 3.2.0
			createTableProgressMark(db);
			createIndexProgressMark(db);
			insertDefaultProgressMarks(db);
			createTableProgressMarkHistory(db);
			createIndexProgressMarkHistory(db);
		}

		// bug in 137 (3.3.3) where ReadingPlanProgress table is created with a wrong column name.
		// so that column name is found, drop that table.
		if (oldVersion >= 137 && oldVersion <= 142) {
			boolean needDrop = false;
			final Cursor c = db.rawQuery("pragma table_info(" + Db.TABLE_ReadingPlanProgress + ")", null);
			if (c != null) {
				while (c.moveToNext()) {
					final String name = c.getString(1 /* "name" column */);
					Log.d(TAG, "column name: " + name);
					if ("checkedTime".equals(name)) { // this is a bad column name
						needDrop = true;
					}
				}
			}
			if (needDrop) {
				Log.d(TAG, "table need to be dropped: " + Db.TABLE_ReadingPlanProgress);
				db.execSQL("drop table " + Db.TABLE_ReadingPlanProgress);
			}
		}

		if (oldVersion <= 142) { // 143: 3.4.4
			// (These tables were first introduced in 3.4.0, but because of the above bug, this needs to be recreated)
			createTableReadingPlan(db);
			createTableReadingPlanProgress(db);
			createIndexReadingPlanProgress(db);
		}

		if (oldVersion < 14000163) { // last version that doesn't use Marker table
			addGidColumnToLabel(db);

			createTableMarker(db);
			createIndexMarker(db);
			createTableMarker_Label(db);
			createIndexMarker_Label(db);

			convertFromBookmark2ToMarker(db);
		}

		if (oldVersion < 14000165) { // last version that doesn't use the new Version table
			convertFromEdisiToVersion(db);
		}
	}

	private void createTableMarker(SQLiteDatabase db) {
		db.execSQL(
			"create table if not exists " + Db.TABLE_Marker + " (" +
				"_id integer primary key autoincrement, " +
				Db.Marker.gid + " text," +
				Db.Marker.ari + " integer, " +
				Db.Marker.kind + " integer, " +
				Db.Marker.caption + " text, " +
				Db.Marker.verseCount + " integer, " +
				Db.Marker.createTime + " integer, " +
				Db.Marker.modifyTime + " integer" +
				")"
		);
	}

	private void createIndexMarker(SQLiteDatabase db) {
		db.execSQL("create index if not exists index_Marker_01 on " + Db.TABLE_Marker + " (" + Db.Marker.ari + ")");
		db.execSQL("create index if not exists index_Marker_02 on " + Db.TABLE_Marker + " (" + Db.Marker.kind + ", " + Db.Marker.ari + ")");
		db.execSQL("create index if not exists index_Marker_03 on " + Db.TABLE_Marker + " (" + Db.Marker.kind + ", " + Db.Marker.modifyTime + ")");
		db.execSQL("create index if not exists index_Marker_04 on " + Db.TABLE_Marker + " (" + Db.Marker.kind + ", " + Db.Marker.createTime + ")");
		db.execSQL("create index if not exists index_Marker_05 on " + Db.TABLE_Marker + " (" + Db.Marker.kind + ", " + Db.Marker.caption + " collate NOCASE)");
		db.execSQL("create index if not exists index_Marker_06 on " + Db.TABLE_Marker + " (" + Db.Marker.gid + ")");
	}

	private void createTableDevotion(SQLiteDatabase db) {
		db.execSQL("create table if not exists " + Db.TABLE_Devotion + " (" +
		"_id integer primary key autoincrement, " +
		Db.Devotion.name + " text, " +
		Db.Devotion.date + " text, " +
		Db.Devotion.header + " text, " +
		Db.Devotion.title + " text, " +
		Db.Devotion.body + " text, " +
		Db.Devotion.readyToUse + " integer," +
		Db.Devotion.touchTime + " integer)");
	}

	private void createIndexDevotion(SQLiteDatabase db) {
		db.execSQL("create index if not exists index_101 on " + Db.TABLE_Devotion + " (" + Db.Devotion.name + ")");
		db.execSQL("create index if not exists index_102 on " + Db.TABLE_Devotion + " (" + Db.Devotion.name + ", " + Db.Devotion.date + ")");
		db.execSQL("create index if not exists index_103 on " + Db.TABLE_Devotion + " (" + Db.Devotion.date + ")");
		db.execSQL("create index if not exists index_104 on " + Db.TABLE_Devotion + " (" + Db.Devotion.touchTime + ")");
	}

	private void createTableEdisi(SQLiteDatabase db) {
		db.execSQL("create table if not exists Edisi (" +
		"_id integer primary key autoincrement, " +
		"shortName text, " +
		"judul text, " +
		"jenis text, " +
		"keterangan text, " +
		Db.Version.filename + "namafile text, " +
		"namafile_pdbasal text, " +
		"aktif integer, " +
		"urutan integer)");
	}

	void createTableVersion(SQLiteDatabase db) {
		db.execSQL("create table if not exists " + Db.TABLE_Version + " (" +
				"_id integer primary key autoincrement, " +
				Db.Version.locale + " text," +
				Db.Version.shortName + " text," +
				Db.Version.longName + " text," +
				Db.Version.description + " text," +
				Db.Version.filename + " text," +
				Db.Version.preset_name + " text," +
				Db.Version.active + " integer," +
				Db.Version.ordering + " integer)"
		);
	}

	void createIndexVersion(SQLiteDatabase db) {
		db.execSQL("create index if not exists index_Version_01 on " + Db.TABLE_Version + " (" + Db.Version.ordering + ")");
		db.execSQL("create index if not exists index_Version_02 on " + Db.TABLE_Version + " (" + Db.Version.active + "," + Db.Version.longName + ")");
		db.execSQL("create index if not exists index_Version_03 on " + Db.TABLE_Version + " (" + Db.Version.preset_name + ")");
	}

	private void createTableLabel(SQLiteDatabase db) {
		db.execSQL("create table if not exists " + Db.TABLE_Label + " (" +
				"_id integer primary key autoincrement, " +
				Db.Label.gid + " text," +
				Db.Label.title + " text, " +
				Db.Label.ordering + " integer, " +
				Db.Label.backgroundColor + " text" +
				")"
		);
	}

	private void createIndexLabel(SQLiteDatabase db) {
		db.execSQL("create index if not exists index_401 on " + Db.TABLE_Label + " (" + Db.Label.ordering + ")");
		db.execSQL("create index if not exists index_402 on " + Db.TABLE_Label + " (" + Db.Label.gid + ")");
	}

	private void createTableMarker_Label(SQLiteDatabase db) {
		db.execSQL("create table if not exists " + Db.TABLE_Marker_Label + " (" +
				"_id integer primary key autoincrement, " +
				Db.Marker_Label.gid + " text," +
				Db.Marker_Label.marker_gid + " text, " +
				Db.Marker_Label.label_gid + " text" +
				")"
		);
	}

	private void createIndexMarker_Label(SQLiteDatabase db) {
		db.execSQL("create        index if not exists index_Marker_Label_01 on " + Db.TABLE_Marker_Label + " (" + Db.Marker_Label.marker_gid + ")");
		db.execSQL("create        index if not exists index_Marker_Label_02 on " + Db.TABLE_Marker_Label + " (" + Db.Marker_Label.label_gid + ")");
		db.execSQL("create unique index if not exists index_Marker_Label_03 on " + Db.TABLE_Marker_Label + " (" + Db.Marker_Label.marker_gid + ", " + Db.Marker_Label.label_gid + ")");
		db.execSQL("create unique index if not exists index_Marker_Label_04 on " + Db.TABLE_Marker_Label + " (" + Db.Marker_Label.gid + ")");
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
		db.execSQL("create index if not exists index_601 on " + Db.TABLE_ProgressMark + " (" + Db.ProgressMark.preset_id + ")");
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

	private void createTableReadingPlan(final SQLiteDatabase db) {
		db.execSQL("create table if not exists " + Db.TABLE_ReadingPlan + " (" +
		"_id integer primary key autoincrement, " +
		Db.ReadingPlan.version + " integer, " +
		Db.ReadingPlan.name + " text, " +
		Db.ReadingPlan.title + " text, " +
		Db.ReadingPlan.description + " text, " +
		Db.ReadingPlan.duration + " integer, " +
		Db.ReadingPlan.startTime + " integer, " +
		Db.ReadingPlan.data + " blob)");
	}

	private void createTableReadingPlanProgress(final SQLiteDatabase db) {
		db.execSQL("create table if not exists " + Db.TABLE_ReadingPlanProgress + " (" +
		"_id integer primary key autoincrement, " +
		Db.ReadingPlanProgress.reading_plan_id + " integer, " +
		Db.ReadingPlanProgress.reading_code + " integer, " +
		Db.ReadingPlanProgress.checkTime + " integer)");
	}

	private void createIndexReadingPlanProgress(SQLiteDatabase db) {
		db.execSQL("create unique index if not exists index_901 on " + Db.TABLE_ReadingPlanProgress + " (" + Db.ReadingPlanProgress.reading_plan_id + ", " + Db.ReadingPlanProgress.reading_code + ")");
	}

	// This needs to be kept, for upgrading from version 51-102 to 14000165
	private void addShortNameColumnAndIndexToEdisi(SQLiteDatabase db) {
		db.execSQL("alter table Edisi add column shortName text");
	}

	private void addGidColumnToLabel(SQLiteDatabase db) {
		db.execSQL("alter table " + Db.TABLE_Label + " add column " + Db.Label.gid + " text");

		// make sure this one matches the one in createIndexLabel()
		db.execSQL("create index if not exists index_402 on " + Db.TABLE_Label + " (" + Db.Label.gid + ")");
	}

	/**
	 * Converts Bookmark2 to Marker table
	 * and Bookmark2_Label to Marker_Label table
	 * and add gid to all labels
	 */
	private void convertFromBookmark2ToMarker(final SQLiteDatabase db) {
		final String TABLE_Bookmark2 = "Bukmak2";
		class Bookmark2 {
			public static final String ari = "ari";
			public static final String kind = "jenis";
			public static final String caption = "tulisan";
			public static final String addTime = "waktuTambah";
			public static final String modifyTime = "waktuUbah";
		}

		final String TABLE_Bookmark2_Label = "Bukmak2_Label";
		class Bookmark2_Label {
			public static final String bookmark2_id = "bukmak2_id";
			public static final String label_id = "label_id";
		}

		// We need to maintain _id to prevent complications with Marker_Label
		db.beginTransaction();
		try {
			final LongSparseArray<String> idToGid_marker = new LongSparseArray<>();
			final LongSparseArray<String> idToGid_label = new LongSparseArray<>();

			{ // Bookmark2 -> Marker
				final Cursor c = db.query(TABLE_Bookmark2,
					new String[]{"_id", Bookmark2.ari, Bookmark2.kind, Bookmark2.caption, Bookmark2.addTime, Bookmark2.modifyTime},
					null, null, null, null, "_id asc"
				);

				final ContentValues cv = new ContentValues();
				while (c.moveToNext()) {
					final long _id = c.getLong(0);
					final String gid = Gid.newGid();

					idToGid_marker.put(_id, gid);

					cv.put("_id", _id);
					cv.put(Db.Marker.ari, c.getInt(1));
					cv.put(Db.Marker.kind, c.getInt(2));
					cv.put(Db.Marker.caption, c.getString(3));
					cv.put(Db.Marker.createTime, c.getLong(4));
					cv.put(Db.Marker.modifyTime, c.getLong(5));
					cv.put(Db.Marker.verseCount, 1);
					cv.put(Db.Marker.gid, gid);
					db.insert(Db.TABLE_Marker, null, cv);
				}

				c.close();
			}

			{ // add gid to all Labels
				final String[] args = {null};
				final Cursor c = db.query(Db.TABLE_Label, new String[]{"_id"}, null, null, null, null, null);
				final ContentValues cv = new ContentValues();
				while (c.moveToNext()) {
					final long _id = c.getLong(0);
					final String gid = Gid.newGid();

					idToGid_label.put(_id, gid);

					cv.put(Db.Label.gid, gid);
					args[0] = String.valueOf(_id);
					db.update(Db.TABLE_Label, cv, "_id = ?", args);
				}
				c.close();
			}

			{ // Bookmark2_Label -> Marker_Label
				final Cursor c = db.query(TABLE_Bookmark2_Label,
					new String[] {"_id", Bookmark2_Label.bookmark2_id, Bookmark2_Label.label_id},
					null, null, null, null, "_id asc"
				);
				final ContentValues cv = new ContentValues();
				while (c.moveToNext()) {
					final long _id = c.getLong(0);
					final long marker_id = c.getLong(1);
					final long label_id = c.getLong(2);

					final String marker_gid = idToGid_marker.get(marker_id);
					final String label_gid = idToGid_label.get(label_id);

					cv.put("_id", _id);
					cv.put(Db.Marker_Label.gid, Gid.newGid());
					cv.put(Db.Marker_Label.marker_gid, marker_gid);
					cv.put(Db.Marker_Label.label_gid, label_gid);
					db.insert(Db.TABLE_Marker_Label, null, cv);
				}
			}

			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

	/**
	 * Converts the old version (Edisi) table, to the new Version table.
	 *
	 * This will keep user's added yes file (excluding the preset versions)
	 */
	private void convertFromEdisiToVersion(final SQLiteDatabase db) {
		final String TABLE_Edisi = "Edisi";
		class Edisi {
			public static final String shortName = "shortName";
			public static final String title = "judul";
			public static final String description = "keterangan";
			public static final String kind = "jenis";
			public static final String filename = "namafile";
			// unused: public static final String filename_originalpdb = "namafile_pdbasal";
			public static final String active = "aktif";
			public static final String ordering = "urutan";
		}

		db.beginTransaction();
		try {
			// Edisi -> Version
			final Cursor c = db.query(TABLE_Edisi,
				new String[]{Edisi.shortName, Edisi.title, Edisi.description, Edisi.filename, Edisi.active},
				null, null, null, null, Edisi.ordering + " asc"
			);
			final ContentValues cv = new ContentValues();
			while(c.moveToNext()) {
				final long _id = c.getLong(0);
				cv.put("_id", _id);
				cv.put(Db.Version.locale, (String) null);
				cv.put(Db.Version.shortName, c.getString(0));
				cv.put(Db.Version.longName, c.getString(1));
				cv.put(Db.Version.description, c.getString(2));
				cv.put(Db.Version.filename, c.getString(3));
				cv.put(Db.Version.active, c.getInt(4));
				db.insert(Db.TABLE_Version, null, cv);
			}

			c.close();

			db.execSQL("drop table " + TABLE_Edisi);

			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}
}
