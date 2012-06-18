package yuku.alkitab.base.storage;

import android.database.sqlite.SQLiteDatabase;

public class SongDbHelper extends yuku.afw.storage.InternalDbHelper {
	public static final String TAG = SongDbHelper.class.getSimpleName();

	@Override public void createTables(SQLiteDatabase db) {
		StringBuilder sb = new StringBuilder(200);
		sb.append("create table " + Table.Song.tableName() + " ( _id integer primary key "); //$NON-NLS-1$ //$NON-NLS-2$
		for (Table.Song field: Table.Song.values()) {
			sb.append(',');
			sb.append(field.name());
			sb.append(' ');
			sb.append(field.type.name());
			if (field.suffix != null) {
				sb.append(' ');
				sb.append(field.suffix);
			}
		}
		sb.append(")"); //$NON-NLS-1$
		db.execSQL(sb.toString());
	}

	@Override public void createIndexes(SQLiteDatabase db) {
		// Song(bookName, code)
		db.execSQL("create index " + Table.Song.tableName() + "_001_index on " + Table.Song.tableName() + " (" 
		+ Table.Song.bookName.name() + ","
		+ Table.Song.code.name() 
		+ ")");

		// Song(bookName, ordering)
		db.execSQL("create index " + Table.Song.tableName() + "_002_index on " + Table.Song.tableName() + " (" 
		+ Table.Song.bookName.name() + ","
		+ Table.Song.ordering.name() 
		+ ")");
	}
}
