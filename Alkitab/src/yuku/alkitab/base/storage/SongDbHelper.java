package yuku.alkitab.base.storage;

import android.database.sqlite.SQLiteDatabase;

public class SongDbHelper extends yuku.afw.storage.InternalDbHelper {
	public static final String TAG = SongDbHelper.class.getSimpleName();

	public SongDbHelper() {
		super("SongDb");
	}
	
	@Override public void createTables(SQLiteDatabase db) {
		StringBuilder sb = new StringBuilder(200);
		sb.append("create table " + Table.SongInfo.tableName() + " ( _id integer primary key ");
		for (Table.SongInfo field: Table.SongInfo.values()) {
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

	@Override public void createIndexes(SQLiteDatabase db) {
		// Song(bookName, code)
		db.execSQL("create index " + Table.SongInfo.tableName() + "_001_index on " + Table.SongInfo.tableName() + " (" 
		+ Table.SongInfo.bookName.name() + ","
		+ Table.SongInfo.code.name() 
		+ ")");

		// Song(bookName, ordering)
		db.execSQL("create index " + Table.SongInfo.tableName() + "_002_index on " + Table.SongInfo.tableName() + " (" 
		+ Table.SongInfo.bookName.name() + ","
		+ Table.SongInfo.ordering.name() 
		+ ")");
	}
}
