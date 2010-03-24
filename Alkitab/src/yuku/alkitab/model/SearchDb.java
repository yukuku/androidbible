package yuku.alkitab.model;

import java.io.File;

import android.content.Context;
import android.database.*;
import android.database.sqlite.*;
import android.os.Environment;
import android.util.Log;

public class SearchDb {
	public static final String TABEL_Fts = "Fts";
	public static final String KOLOM_ari = "ari";
	public static final String KOLOM_edisi_nama = "edisi_nama";
	public static final String KOLOM_content = "content";
	
	public static final String TABEL_UdahIndex = "UdahIndex";
	public static final String KOLOM_waktuJadi = "waktuJadi";
	
	private static SearchDb instance = null;
	
	private Context context_;
	private Helper helper_;

	public static synchronized SearchDb getInstance(Context context) {
		if (instance == null) {
			instance = new SearchDb(context);
		}

		return instance;
	}

	public static String getDbPath(Context context) {
		return Environment.getExternalStorageDirectory() + "/bible/" + context.getPackageName() + "/search_index/s0.db";
	}
	
	public static boolean mkdirs(Context context) {
		String path = getDbPath(context);
		File f = new File(path).getParentFile();
		return (f.exists() && f.isDirectory()) || f.mkdirs();
	}

	public static synchronized boolean cekAdaDb(Context context) {
		String path = getDbPath(context);

		File f = new File(path);
		return f.exists() && f.canRead() && f.canWrite();
	}
	
	public static synchronized boolean cekAdaTabelEdisi(Context context, String edisi_nama) {
		SQLiteDatabase db = null;
		try {
			db = SQLiteDatabase.openDatabase(getDbPath(context), null, 0);
			Cursor cursor = db.rawQuery(String.format("select count(*) as c from %s where %s = ? limit 1", TABEL_UdahIndex, KOLOM_edisi_nama), new String[] {edisi_nama});
			cursor.moveToNext();
			
			int c = cursor.getInt(cursor.getColumnIndexOrThrow("c"));
			return c > 0;
		} catch (SQLException e) {
			Log.e("alki", "cekAdaTabelEdisi", e);
			return false;
		} finally {
			if (db != null) {
				db.close();
			}
		}
	}
	

	/**
	 * @return 1 terbuat baru. 2 uda ada dan bisa dibuka. 0 gagal.
	 */
	public static synchronized int bikinDb(Context context) {
		if (! mkdirs(context)) {
			Log.i("alki", "ga bisa bikin dir buat db");
			return 0;
		}
		
		String path = getDbPath(context);
		
		// cek ada ato ga.
		if (cekAdaDb(context)) {
			// cek buka
			try {
				SQLiteDatabase db = SQLiteDatabase.openDatabase(path, null, 0);
				db.close();
				
				return 2;
			} catch (SQLException e) {
				Log.i("alki", "ga bisa buka db", e);
				
				return 0;
			}
		} else {
			// coba bikin
			try {
				SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(path, null);
				
				db.execSQL(String.format("create virtual table %s using fts3 (" +
						"%s text, " +
						"%s integer, " +
						"%s text)", TABEL_Fts, KOLOM_content, KOLOM_ari, KOLOM_edisi_nama));
				
				db.execSQL(String.format("create table %s (" +
						"%s text, " +
						"%s integer)", TABEL_UdahIndex, KOLOM_edisi_nama, KOLOM_waktuJadi));
				
				db.close();
				
				return 1;
			} catch (SQLException e) {
				Log.i("alki", "ga bisa bikin db", e);
				
				return 0;
			}
		}
	}

	private SearchDb(Context context) {
		context_ = context;
		helper_ = new Helper();
	}

	public SQLiteDatabase getDatabase() {
		return helper_.db;
	}

	private class Helper {
		SQLiteDatabase db;

		public Helper() {
			String path = getDbPath(context_);
			try {
				db = SQLiteDatabase.openDatabase(path, null, 0);
			} catch (SQLiteException e) {
				throw new RuntimeException("Ga bisa bikin ato buka db. Path = " + path);
			}
		}
	}
}
