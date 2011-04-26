package yuku.alkitab.base.storage;

import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.storage.Db.Bukmak;
import yuku.alkitab.base.storage.Db.Bukmak2;
import android.content.*;
import android.content.pm.*;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.*;
import android.database.sqlite.*;
import android.util.Log;

public class InternalDbHelper extends SQLiteOpenHelper {
	public static final String TAG = InternalDbHelper.class.getSimpleName();
	private int version;
	
	public InternalDbHelper(Context context) {
		super(context, "AlkitabDb", null, getVersionCode(context));
		version = versionNumber(context);
	}
	
	public static int getVersionCode(Context context) {
		PackageInfo packageInfo;
		try {
			packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
		} catch (NameNotFoundException e) {
			Log.w(TAG, "pake versi 0 databesnya!"); //$NON-NLS-1$
			return 0;
		}
		return packageInfo.versionCode;
	}
	
	@Override
	public void onOpen(SQLiteDatabase db) {
		// db.execSQL("PRAGMA synchronous=OFF");
	};

	@Override
	public void onCreate(SQLiteDatabase db) {
		Log.d(TAG, "onCreate dipanggil"); //$NON-NLS-1$
		
		try {
			bikinTabelBukmak2(db);
			bikinTabelRenungan(db);
		} catch (SQLException e) {
			Log.e(TAG, "onCreate db ngaco!", e); //$NON-NLS-1$
		}
	}

	private void bikinTabelBukmak2(SQLiteDatabase db) throws SQLException {
		db.execSQL(String.format("create table if not exists %s (" + //$NON-NLS-1$
				"_id integer primary key autoincrement, " + //$NON-NLS-1$
				"%s integer, " + // ari //$NON-NLS-1$
				"%s integer, " + // jenis //$NON-NLS-1$
				"%s text, " + // tulisan //$NON-NLS-1$
				"%s integer, " + // waktuTambah //$NON-NLS-1$
				"%s integer)", // waktuUbah //$NON-NLS-1$
				Db.TABEL_Bukmak2, Db.Bukmak2.ari, Db.Bukmak2.jenis, Db.Bukmak2.tulisan, Db.Bukmak2.waktuTambah, Db.Bukmak2.waktuUbah));
		
		// index Bukmak2(ari)
		db.execSQL(String.format("create index if not exists index_201 on %s (%s)", Db.TABEL_Bukmak2, Db.Bukmak2.ari)); //$NON-NLS-1$
		// index Bukmak2(jenis,ari)
		db.execSQL(String.format("create index if not exists index_202 on %s (%s, %s)", Db.TABEL_Bukmak2, Db.Bukmak2.jenis, Db.Bukmak2.ari)); //$NON-NLS-1$
		// index Bukmak2(jenis,waktuUbah)
		db.execSQL(String.format("create index if not exists index_203 on %s (%s, %s)", Db.TABEL_Bukmak2, Db.Bukmak2.jenis, Db.Bukmak2.waktuUbah)); //$NON-NLS-1$
	}

	private void bikinTabelRenungan(SQLiteDatabase db) throws SQLException {
		db.execSQL(String.format("create table if not exists %s (" + //$NON-NLS-1$
				"_id integer primary key autoincrement, " + //$NON-NLS-1$
				"%s text, " + // nama //$NON-NLS-1$
				"%s text, " + // tgl (yyyymmdd) //$NON-NLS-1$
				"%s text, " + // header //$NON-NLS-1$
				"%s text, " + // judul //$NON-NLS-1$
				"%s text, " + // isi //$NON-NLS-1$
				"%s integer," + // siap pakai //$NON-NLS-1$
				"%s integer)", // waktuSentuh //$NON-NLS-1$
				Db.TABEL_Renungan, Db.Renungan.nama, Db.Renungan.tgl, Db.Renungan.header, Db.Renungan.judul, Db.Renungan.isi, Db.Renungan.siapPakai, Db.Renungan.waktuSentuh));
		
		// index Renungan(nama)
		db.execSQL(String.format("create index if not exists index_101 on %s (%s)", Db.TABEL_Renungan, Db.Renungan.nama)); //$NON-NLS-1$
		// index Renungan(nama,tgl)
		db.execSQL(String.format("create index if not exists index_102 on %s (%s, %s)", Db.TABEL_Renungan, Db.Renungan.nama, Db.Renungan.tgl)); //$NON-NLS-1$
		// index Renungan(tgl)
		db.execSQL(String.format("create index if not exists index_103 on %s (%s)", Db.TABEL_Renungan, Db.Renungan.tgl)); //$NON-NLS-1$
	}
	
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		Log.d(TAG, "onUpgrade dipanggil, oldVersion=" + oldVersion + " newVersion=" + newVersion); //$NON-NLS-1$ //$NON-NLS-2$

		if (oldVersion <= 23) {
			// konvert dari Bukmak ke Bukmak2
			konvertDariBukmakKeBukmak2(db);
		}
		
	}

	@SuppressWarnings("deprecation")
	private void konvertDariBukmakKeBukmak2(SQLiteDatabase db) {
		bikinTabelBukmak2(db);
		
		// pindahin data dari Bukmak ke Bukmak2
		db.beginTransaction();
		try {
			Cursor cursor = db.query(Db.TABEL_Bukmak, new String[] {Bukmak.alamat, Bukmak.kitab, Bukmak.pasal, Bukmak.ayat, Bukmak.waktuTambah}, null, null, null, null, null);

			int kolom_alamat = cursor.getColumnIndex(Bukmak.alamat);
			int kolom_kitab = cursor.getColumnIndex(Bukmak.kitab);
			int kolom_pasal = cursor.getColumnIndex(Bukmak.pasal);
			int kolom_ayat = cursor.getColumnIndex(Bukmak.ayat);
			int kolom_waktuTambah = cursor.getColumnIndex(Bukmak.waktuTambah);
			
			ContentValues cv = new ContentValues();
			
			// default
			cv.put(Bukmak2.jenis, Bukmak2.jenis_bukmak);

			while (true) {
		    	boolean more = cursor.moveToNext();
		    	if (!more) {
		    		break;
		    	}

		    	cv.put(Bukmak2.tulisan, cursor.getString(kolom_alamat));
		    	
		    	int kitab = cursor.getInt(kolom_kitab);
		    	int pasal = cursor.getInt(kolom_pasal);
		    	int ayat = cursor.getInt(kolom_ayat);
		    	int ari = Ari.encode(kitab, pasal, ayat);
		    	
		    	cv.put(Bukmak2.ari, ari);
		    	Integer waktu = new Integer(cursor.getString(kolom_waktuTambah));
		    	cv.put(Bukmak2.waktuTambah, waktu);
		    	cv.put(Bukmak2.waktuUbah, waktu);
				db.insertOrThrow(Db.TABEL_Bukmak2, null, cv);
		    }
		    cursor.close();
			
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}
	
	public int getVersion() {
		return version;
	}

	private static int versionNumber = 0;
	private static int versionNumber(Context context) {
		if (versionNumber != 0) return versionNumber;
		PackageManager packageManager = context.getPackageManager();
		try {
			versionNumber = packageManager.getPackageInfo(context.getPackageName(), 0).versionCode;
			return versionNumber;
		} catch (NameNotFoundException e) {
			Log.e(TAG, "no pkg name", e);
			return 0;
		}
	}


}
