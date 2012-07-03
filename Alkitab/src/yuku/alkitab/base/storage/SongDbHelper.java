package yuku.alkitab.base.storage;

import android.database.sqlite.SQLiteDatabase;

public class SongDbHelper extends yuku.afw.storage.InternalDbHelper {
	public static final String TAG = SongDbHelper.class.getSimpleName();

	public SongDbHelper() {
		super("SongDb"); //$NON-NLS-1$
	}
	
	@Override public void createTables(SQLiteDatabase db) {
		StringBuilder sb = new StringBuilder(200);
		sb.append("create table " + Table.SongInfo.tableName() + " ( _id integer primary key "); //$NON-NLS-1$ //$NON-NLS-2$
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
		sb.append(")"); //$NON-NLS-1$
		db.execSQL(sb.toString());
	}

	@Override public void createIndexes(SQLiteDatabase db) {
		// Song(bookName, code)
		db.execSQL("create index " + Table.SongInfo.tableName() + "_001_index on " + Table.SongInfo.tableName() + " ("  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		+ Table.SongInfo.bookName.name() + "," //$NON-NLS-1$
		+ Table.SongInfo.code.name() 
		+ ")"); //$NON-NLS-1$

		// Song(bookName, ordering)
		db.execSQL("create index " + Table.SongInfo.tableName() + "_002_index on " + Table.SongInfo.tableName() + " ("  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		+ Table.SongInfo.bookName.name() + "," //$NON-NLS-1$
		+ Table.SongInfo.ordering.name() 
		+ ")"); //$NON-NLS-1$
	}
}
