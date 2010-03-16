package yuku.alkitab.model;

import android.database.SQLException;
import android.database.sqlite.*;
import android.util.Log;

public class SearchDb {
	public static final String TABEL_Fts = "Fts";
	/** Alkitab Resource Identifier */
	public static final String KOLOM_ari = "ari";
	public static final String KOLOM_edisi_nama = "edisi_nama";
	public static final String KOLOM_content = "content";

	private static SearchDb instance = null;
	private static String path = null;

	public static void setPath(String path) {
		SearchDb.path = path;
	}

	public static synchronized SearchDb getInstance() {
		if (instance == null) {
			instance = new SearchDb();
		}

		return instance;
	}

	private Helper helper;
	private SQLiteDatabase db;

	private SearchDb() {
		helper = new Helper();
	}

	public SQLiteDatabase getDatabase() {
		return db;
	}

	private class Helper {
		public Helper() {
			try {
				db = SQLiteDatabase.openDatabase(path, null, 0);
			} catch (SQLiteException e) {
				// mungkin belum ada, mari bikin
				Log.d("alki", "mari bikin tabel index");
				db = SQLiteDatabase.openOrCreateDatabase(path, null);
				
				try {
					db.execSQL(String.format("create virtual table %s using fts3 (" +
							"%s text, " +
							"%s integer, " +
							"%s text)", TABEL_Fts, KOLOM_content, KOLOM_ari, KOLOM_edisi_nama));
				} catch (SQLException e1) {
					Log.e("alki", "ngaco!", e1);
				}
			}
		}
	}
}
