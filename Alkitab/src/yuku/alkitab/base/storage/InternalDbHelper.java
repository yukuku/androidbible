package yuku.alkitab.base.storage;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import yuku.afw.App;

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
		
		createTableMarker(db);
		createIndexMarker(db);
		createTableDevotion(db);
		createIndexDevotion(db);
		createTableVersion(db);
		createIndexVersion(db);
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
	}

	@Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		Log.d(TAG, "@@onUpgrade oldVersion=" + oldVersion + " newVersion=" + newVersion); //$NON-NLS-1$ //$NON-NLS-2$

		// No more support for Bukmak version 1 table (last published: 2010-06-14)
		// if (oldVersion <= 23) {
		//	convertFromBookmarkToBookmark2(db);
		// }

		if (oldVersion <= 50) {
			// new table Version
			createTableVersion(db);
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
			addShortNameColumnAndIndexToVersion(db);
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

		if (oldVersion < 14000163) { // last version that don't use Marker table
			createTableMarker(db);
			createIndexMarker(db);
			createTableMarker_Label(db);
			createIndexMarker_Label(db);

			convertFromBookmark2ToMarker(db);
		}
	}

	private void createTableMarker(SQLiteDatabase db) {
		db.execSQL(
			"create table if not exists " + Db.TABLE_Marker + " (" +
			"_id integer primary key autoincrement, " +
			Db.Marker.ari + " integer, " +
			Db.Marker.kind + " integer, " +
			Db.Marker.caption + " text, " +
			Db.Marker.verseCount + " integer, " +
			Db.Marker.createTime + " integer, " +
			Db.Marker.modifyTime + " integer)"
		);
	}

	private void createIndexMarker(SQLiteDatabase db) {
		db.execSQL("create index if not exists index_Marker_01 on " + Db.TABLE_Marker + " (" + Db.Marker.ari + ")");
		db.execSQL("create index if not exists index_Marker_02 on " + Db.TABLE_Marker + " (" + Db.Marker.kind + ", " + Db.Marker.ari + ")");
		db.execSQL("create index if not exists index_Marker_03 on " + Db.TABLE_Marker + " (" + Db.Marker.kind + ", " + Db.Marker.modifyTime + ")");
		db.execSQL("create index if not exists index_Marker_04 on " + Db.TABLE_Marker + " (" + Db.Marker.kind + ", " + Db.Marker.createTime + ")");
		db.execSQL("create index if not exists index_Marker_05 on " + Db.TABLE_Marker + " (" + Db.Marker.kind + ", " + Db.Marker.caption + " collate NOCASE)");
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

	private void createTableMarker_Label(SQLiteDatabase db) {
		db.execSQL("create table if not exists " + Db.TABLE_Marker_Label + " (" +
			"_id integer primary key autoincrement, " +
			Db.Marker_Label.marker_id + " integer, " +
			Db.Marker_Label.label_id + " integer)"
		);
	}

	private void createIndexMarker_Label(SQLiteDatabase db) {
		db.execSQL("create        index if not exists index_Marker_Label_01 on " + Db.TABLE_Marker_Label + " (" + Db.Marker_Label.marker_id + ")");
		db.execSQL("create        index if not exists index_Marker_Label_02 on " + Db.TABLE_Marker_Label + " (" + Db.Marker_Label.label_id + ")");
		db.execSQL("create unique index if not exists index_Marker_Label_03 on " + Db.TABLE_Marker_Label + " (" + Db.Marker_Label.marker_id + ", " + Db.Marker_Label.label_id + ")");
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

	private void addShortNameColumnAndIndexToVersion(SQLiteDatabase db) {
		db.execSQL("alter table " + Db.TABLE_Version + " add column " + Db.Version.shortName + " text");

		// make sure these two matches the ones in createIndexVersion()
		db.execSQL("create index if not exists index_302 on " + Db.TABLE_Version + " (" + Db.Version.shortName + ")"); //$NON-NLS-1$
		db.execSQL("create index if not exists index_303 on " + Db.TABLE_Version + " (" + Db.Version.title + ")"); //$NON-NLS-1$
	}

	/**
	 * Converts Bookmark2 to Marker table
	 * and Bookmark2_Label to Marker_Label
	 */
	private void convertFromBookmark2ToMarker(final SQLiteDatabase db) {
		final String TABLE_Bookmark2 = "Bukmak2"; //$NON-NLS-1$
		class Bookmark2 {
			public static final String ari = "ari"; //$NON-NLS-1$
			public static final String kind = "jenis"; //$NON-NLS-1$
			public static final String caption = "tulisan"; //$NON-NLS-1$
			public static final String addTime = "waktuTambah"; //$NON-NLS-1$
			public static final String modifyTime = "waktuUbah"; //$NON-NLS-1$
		}

		final String TABLE_Bookmark2_Label = "Bukmak2_Label"; //$NON-NLS-1$
		class Bookmark2_Label {
			public static final String bookmark2_id = "bukmak2_id"; //$NON-NLS-1$
			public static final String label_id = "label_id"; //$NON-NLS-1$
		}

		// We need to maintain _id to prevent complications with Marker_Label
		db.beginTransaction();
		try {
			{ // Marker -> Marker
				final Cursor c = db.query(TABLE_Bookmark2,
					new String[] {"_id", Bookmark2.ari, Bookmark2.kind, Bookmark2.caption, Bookmark2.addTime, Bookmark2.modifyTime},
					null, null, null, null, "_id asc"
				);
				final ContentValues cv = new ContentValues();
				while (c.moveToNext()) {
					cv.put("_id", c.getLong(0));
					cv.put(Db.Marker.ari, c.getInt(1));
					cv.put(Db.Marker.kind, c.getInt(2));
					cv.put(Db.Marker.caption, c.getString(3));
					cv.put(Db.Marker.createTime, c.getLong(4));
					cv.put(Db.Marker.modifyTime, c.getLong(5));
					cv.put(Db.Marker.verseCount, 1);
					db.insert(Db.TABLE_Marker, null, cv);
				}
			}

			{ // Bookmark2_Label -> Marker_Label
				final Cursor c = db.query(TABLE_Bookmark2_Label,
					new String[] {"_id", Bookmark2_Label.bookmark2_id, Bookmark2_Label.label_id},
					null, null, null, null, "_id asc"
				);
				final ContentValues cv = new ContentValues();
				while (c.moveToNext()) {
					cv.put("_id", c.getLong(0));
					cv.put(Db.Marker_Label.marker_id, c.getLong(1));
					cv.put(Db.Marker_Label.label_id, c.getLong(2));
					db.insert(Db.TABLE_Marker_Label, null, cv);
				}
			}

			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}
}
