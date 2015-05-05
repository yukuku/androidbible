package yuku.alkitab.base.storage;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import yuku.afw.App;

public class SongDbHelper extends SQLiteOpenHelper {
	public static final String TAG = SongDbHelper.class.getSimpleName();

	public SongDbHelper() {
		super(App.context, "SongDb", null, App.getVersionCode());
	}

	@Override
	public void onCreate(final SQLiteDatabase db) {
		setupTableSongInfo(db);
		setupTableSongBookInfo(db);
	}

	@Override
	public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
		if (oldVersion < 14000202 /* 4.1-beta2 */) {
			setupTableSongBookInfo(db);
		}
	}

	private void setupTableSongInfo(final SQLiteDatabase db) {
		{ // table
			final StringBuilder sb = new StringBuilder("create table " + Table.SongInfo.tableName() + " ( _id integer primary key ");
			for (Table.SongInfo field : Table.SongInfo.values()) {
				sb.append(',');
				sb.append(field.name());
				sb.append(' ');
				sb.append(field.type.name());
				if (field.suffix != null) {
					sb.append(' ');
					sb.append(field.suffix);
				}
			}
			sb.append(")");
			db.execSQL(sb.toString());
		}

		// index SongInfo(bookName, code)
		db.execSQL("create index " + Table.SongInfo.tableName() + "_001_index on " + Table.SongInfo.tableName() + " ("
			+ Table.SongInfo.bookName + ","
			+ Table.SongInfo.code
			+ ")");

		// index SongInfo(bookName, ordering)
		db.execSQL("create index " + Table.SongInfo.tableName() + "_002_index on " + Table.SongInfo.tableName() + " ("
			+ Table.SongInfo.bookName + ","
			+ Table.SongInfo.ordering
			+ ")");
	}

	private void setupTableSongBookInfo(final SQLiteDatabase db) {
		{ // table
			final StringBuilder sb = new StringBuilder("create table " + Table.SongBookInfo.tableName() + " ( _id integer primary key ");
			for (Table.SongBookInfo field : Table.SongBookInfo.values()) {
				sb.append(',');
				sb.append(field.name());
				sb.append(' ');
				sb.append(field.type.name());
				if (field.suffix != null) {
					sb.append(' ');
					sb.append(field.suffix);
				}
			}
			sb.append(")");
			db.execSQL(sb.toString());
		}

		// index SongBookInfo(name)
		db.execSQL("create index " + Table.SongBookInfo.tableName() + "_001_index on " + Table.SongBookInfo.tableName() + " ("
			+ Table.SongBookInfo.name
			+ ")");

	}
}
