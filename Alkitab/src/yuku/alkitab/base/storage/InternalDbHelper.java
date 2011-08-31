package yuku.alkitab.base.storage;

import android.content.*;
import android.content.pm.*;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.*;
import android.database.sqlite.*;
import android.util.*;

import yuku.alkitab.base.model.*;
import yuku.alkitab.base.storage.Db.Bukmak;
import yuku.alkitab.base.storage.Db.Bukmak2;

public class InternalDbHelper extends SQLiteOpenHelper {
	public static final String TAG = InternalDbHelper.class.getSimpleName();
	private int version;
	
	public InternalDbHelper(Context context) {
		super(context, "AlkitabDb", null, getVersionCode(context)); //$NON-NLS-1$
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

	@Override public void onCreate(SQLiteDatabase db) {
		Log.d(TAG, "onCreate dipanggil"); //$NON-NLS-1$
		
		try {
			bikinTabelBukmak2(db);
			bikinIndexBukmak2(db);
			bikinTabelRenungan(db);
			bikinIndexRenungan(db);
			bikinTabelEdisi(db);
			bikinIndexEdisi(db);
			bikinTabelLabel(db);
			bikinIndexLabel(db);
			bikinTabelBukmak2_Label(db);
			bikinIndexBukmak2_Label(db);
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
	}
	
	private void bikinIndexBukmak2(SQLiteDatabase db) throws SQLException {
		// index Bukmak2(ari)
		db.execSQL(String.format("create index if not exists index_201 on %s (%s)", Db.TABEL_Bukmak2, Db.Bukmak2.ari)); //$NON-NLS-1$
		// index Bukmak2(jenis,ari)
		db.execSQL(String.format("create index if not exists index_202 on %s (%s, %s)", Db.TABEL_Bukmak2, Db.Bukmak2.jenis, Db.Bukmak2.ari)); //$NON-NLS-1$
		// index Bukmak2(jenis,waktuUbah)
		db.execSQL(String.format("create index if not exists index_203 on %s (%s, %s)", Db.TABEL_Bukmak2, Db.Bukmak2.jenis, Db.Bukmak2.waktuUbah)); //$NON-NLS-1$
		// index Bukmak2(jenis,waktuTambah)
		db.execSQL(String.format("create index if not exists index_204 on %s (%s, %s)", Db.TABEL_Bukmak2, Db.Bukmak2.jenis, Db.Bukmak2.waktuTambah)); //$NON-NLS-1$
		// index Bukmak2(jenis,tulisan)
		db.execSQL(String.format("create index if not exists index_205 on %s (%s, %s collate NOCASE)", Db.TABEL_Bukmak2, Db.Bukmak2.jenis, Db.Bukmak2.tulisan)); //$NON-NLS-1$
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
	}

	private void bikinIndexRenungan(SQLiteDatabase db) throws SQLException {
		// index Renungan(nama)
		db.execSQL(String.format("create index if not exists index_101 on %s (%s)", Db.TABEL_Renungan, Db.Renungan.nama)); //$NON-NLS-1$
		// index Renungan(nama,tgl)
		db.execSQL(String.format("create index if not exists index_102 on %s (%s, %s)", Db.TABEL_Renungan, Db.Renungan.nama, Db.Renungan.tgl)); //$NON-NLS-1$
		// index Renungan(tgl)
		db.execSQL(String.format("create index if not exists index_103 on %s (%s)", Db.TABEL_Renungan, Db.Renungan.tgl)); //$NON-NLS-1$
		// index Renungan(waktuSentuh)
		db.execSQL(String.format("create index if not exists index_104 on %s (%s)", Db.TABEL_Renungan, Db.Renungan.waktuSentuh)); //$NON-NLS-1$
	}
	
	private void bikinTabelEdisi(SQLiteDatabase db) throws SQLException {
		db.execSQL("create table if not exists " + Db.TABEL_Edisi + " (" + //$NON-NLS-1$ //$NON-NLS-2$
			"_id integer primary key autoincrement, " + //$NON-NLS-1$
			Db.Edisi.judul + " text, " + // judul (keliatan sama user) //$NON-NLS-1$
			Db.Edisi.jenis + " text, " + // jenis (yes) //$NON-NLS-1$
			Db.Edisi.keterangan + " text, " + // keterangan tambahan, mungkin bisa diedit user kalo perlu //$NON-NLS-1$
			Db.Edisi.namafile + " text, " + // nama file di sd card (full path) //$NON-NLS-1$
			Db.Edisi.namafile_pdbasal + " text, " + // nama file kalau bekas dikonvert dari pdb (nama doang) //$NON-NLS-1$
			Db.Edisi.aktif + " integer, " + // tampilkan di daftar edisi? //$NON-NLS-1$
			Db.Edisi.urutan + " integer)"); //$NON-NLS-1$
		
	}
	
	private void bikinIndexEdisi(SQLiteDatabase db) throws SQLException {
		// index Edisi(urutan)
		db.execSQL(String.format("create index if not exists index_301 on %s (%s)", Db.TABEL_Edisi, Db.Edisi.urutan)); //$NON-NLS-1$
	}
	
	private void bikinTabelLabel(SQLiteDatabase db) throws SQLException {
		db.execSQL("create table if not exists " + Db.TABEL_Label + " (" + //$NON-NLS-1$ //$NON-NLS-2$
			"_id integer primary key autoincrement, " + //$NON-NLS-1$
			Db.Label.judul + " text, " + //$NON-NLS-1$
			Db.Label.urutan + " integer, " + //$NON-NLS-1$
			Db.Label.warnaLatar + " text)"); //$NON-NLS-1$
	}
	
	private void bikinIndexLabel(SQLiteDatabase db) throws SQLException {
		// index Label(urutan)
		db.execSQL(String.format("create index if not exists index_401 on %s (%s)", Db.TABEL_Label, Db.Label.urutan)); //$NON-NLS-1$
	}
	
	private void bikinTabelBukmak2_Label(SQLiteDatabase db) throws SQLException {
		db.execSQL("create table if not exists " + Db.TABEL_Bukmak2_Label + " (" + //$NON-NLS-1$ //$NON-NLS-2$
				"_id integer primary key autoincrement, " + //$NON-NLS-1$
				Db.Bukmak2_Label.bukmak2_id + " integer, " + //$NON-NLS-1$
				Db.Bukmak2_Label.label_id + " integer)"); //$NON-NLS-1$
	}
	
	private void bikinIndexBukmak2_Label(SQLiteDatabase db) throws SQLException {
		// index Bukmak2_Label(bukmak2_id)
		db.execSQL(String.format("create index if not exists index_501 on %s (%s)", Db.TABEL_Bukmak2_Label, Db.Bukmak2_Label.bukmak2_id)); //$NON-NLS-1$
		// index Bukmak2_Label(label_id)
		db.execSQL(String.format("create index if not exists index_502 on %s (%s)", Db.TABEL_Bukmak2_Label, Db.Bukmak2_Label.label_id)); //$NON-NLS-1$
		// unique index Bukmak2_Label(bukmak2_id, label_id)
		db.execSQL(String.format("create unique index if not exists index_503 on %s (%s, %s)", Db.TABEL_Bukmak2_Label, Db.Bukmak2_Label.bukmak2_id, Db.Bukmak2_Label.label_id)); //$NON-NLS-1$
		
	}
	
	@Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		Log.d(TAG, "onUpgrade dipanggil, oldVersion=" + oldVersion + " newVersion=" + newVersion); //$NON-NLS-1$ //$NON-NLS-2$
		
		if (oldVersion <= 23) {
			// konvert dari Bukmak ke Bukmak2
			konvertDariBukmakKeBukmak2(db);
		}

		if (oldVersion <= 50) {
			// tambah tabel Edisi
			bikinTabelEdisi(db);
			bikinIndexEdisi(db);
		}
		
		if (oldVersion <= 69) { // 70: 2.0.0
			// tambah tabel Label dan Bukmak2_Label
			bikinTabelLabel(db);
			bikinIndexLabel(db);
			bikinTabelBukmak2_Label(db);
			bikinIndexBukmak2_Label(db);
		}
		
		if (oldVersion <= 70) { // 71: 2.0.0 juga
			// tambah index di Bukmak2
			bikinIndexBukmak2(db);
		}
		
		if (oldVersion <= 71) { // 72: 2.0.0 juga
			// tambah index di Renungan
			bikinIndexRenungan(db);
		}
	}

	@SuppressWarnings("deprecation") private void konvertDariBukmakKeBukmak2(SQLiteDatabase db) {
		bikinTabelBukmak2(db);
		bikinIndexBukmak2(db);
		
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
			return 0;
		}
	}
}
