package yuku.alkitab.model;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.*;
import android.database.sqlite.*;
import android.util.*;

public class AlkitabDb {
	@Deprecated
	public static final String TABEL_Bukmak = "Bukmak";
	@Deprecated
	public static final String KOLOM_Bukmak_alamat = "alamat";
	@Deprecated
	public static final String KOLOM_Bukmak_cuplikan = "cuplikan";
	@Deprecated
	public static final String KOLOM_Bukmak_waktuTambah = "waktuTambah";
	@Deprecated
	public static final String KOLOM_Bukmak_kitab = "kitab";
	@Deprecated
	public static final String KOLOM_Bukmak_pasal = "pasal";
	@Deprecated
	public static final String KOLOM_Bukmak_ayat = "ayat";
	
	public static final String TABEL_Bukmak2 = "Bukmak2";
	public static final String KOLOM_Bukmak2_ari = "ari";
	public static final String KOLOM_Bukmak2_jenis = "jenis";
	public static final String KOLOM_Bukmak2_tulisan = "tulisan";
	public static final String KOLOM_Bukmak2_waktuTambah = "waktuTambah";
	public static final String KOLOM_Bukmak2_waktuUbah = "waktuUbah";
	public static final int ENUM_Bukmak2_jenis_bukmak = 1;
	public static final int ENUM_Bukmak2_jenis_catatan = 2;
	
	public static final String TABEL_Renungan = "Renungan";
	public static final String KOLOM_Renungan_nama = "nama";
	public static final String KOLOM_Renungan_tgl = "tgl";
	public static final String KOLOM_Renungan_header = "header";
	public static final String KOLOM_Renungan_judul = "judul";
	public static final String KOLOM_Renungan_isi = "isi";
	public static final String KOLOM_Renungan_siapPakai = "siapPakai";
	public static final String KOLOM_Renungan_waktuSentuh = "waktuSentuh";

	public static int getVersionCode(Context context) {
		PackageInfo packageInfo;
		try {
			packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
		} catch (NameNotFoundException e) {
			Log.w("alki", "pake versi 0 databesnya!");
			return 0;
		}
		return packageInfo.versionCode;
	}
	
	private static AlkitabDb instance = null;
	
	public static synchronized AlkitabDb getInstance(Context context) {
		if (instance == null) {
			instance = new AlkitabDb(context);
		}
		
		return instance;
	}
	
	Context context;
	Helper helper;
	SQLiteDatabase db;
	
	private AlkitabDb(Context context) {
		this.context = context;
		this.helper = new Helper(context);
		this.db = helper.getWritableDatabase();
	}
	
	public Helper getHelper() {
		return helper;
	}
	
	public SQLiteDatabase getDatabase() {
		return db;
	}
	
	private class Helper extends SQLiteOpenHelper {
		public Helper(Context context) {
			super(context, AlkitabDb.class.getSimpleName(), null, getVersionCode(context));
		}
		
		@Override
		public void onCreate(SQLiteDatabase db) {
			Log.d("alki", "onCreate dipanggil");
			
			try {
				bikinTabelBukmak2(db);
				bikinTabelRenungan(db);
			} catch (SQLException e) {
				Log.e("alki", "onCreate db ngaco!", e);
			}
		}
	
		private void bikinTabelBukmak2(SQLiteDatabase db) throws SQLException {
			db.execSQL(String.format("create table if not exists %s (" +
					"_id integer primary key autoincrement, " +
					"%s integer, " + // ari
					"%s integer, " + // jenis
					"%s text, " + // tulisan
					"%s integer, " + // waktuTambah
					"%s integer)", // waktuUbah
					TABEL_Bukmak2, KOLOM_Bukmak2_ari, KOLOM_Bukmak2_jenis, KOLOM_Bukmak2_tulisan, KOLOM_Bukmak2_waktuTambah, KOLOM_Bukmak2_waktuUbah));
			
			// index Bukmak2(ari)
			db.execSQL(String.format("create index if not exists index_201 on %s (%s)", TABEL_Bukmak2, KOLOM_Bukmak2_ari));
			// index Bukmak2(jenis,ari)
			db.execSQL(String.format("create index if not exists index_202 on %s (%s, %s)", TABEL_Bukmak2, KOLOM_Bukmak2_jenis, KOLOM_Bukmak2_ari));
			// index Bukmak2(jenis,waktuUbah)
			db.execSQL(String.format("create index if not exists index_203 on %s (%s, %s)", TABEL_Bukmak2, KOLOM_Bukmak2_jenis, KOLOM_Bukmak2_waktuUbah));
		}
	
		private void bikinTabelRenungan(SQLiteDatabase db) throws SQLException {
			db.execSQL(String.format("create table if not exists %s (" +
					"_id integer primary key autoincrement, " +
					"%s text, " + // nama
					"%s text, " + // tgl (yyyymmdd)
					"%s text, " + // header
					"%s text, " + // judul
					"%s text, " + // isi
					"%s integer," + // siap pakai
					"%s integer)", // waktuSentuh
					TABEL_Renungan, KOLOM_Renungan_nama, KOLOM_Renungan_tgl, KOLOM_Renungan_header, KOLOM_Renungan_judul, KOLOM_Renungan_isi, KOLOM_Renungan_siapPakai, KOLOM_Renungan_waktuSentuh));
			
			// index Renungan(nama)
			db.execSQL(String.format("create index if not exists index_101 on %s (%s)", TABEL_Renungan, KOLOM_Renungan_nama));
			// index Renungan(nama,tgl)
			db.execSQL(String.format("create index if not exists index_102 on %s (%s, %s)", TABEL_Renungan, KOLOM_Renungan_nama, KOLOM_Renungan_tgl));
			// index Renungan(tgl)
			db.execSQL(String.format("create index if not exists index_103 on %s (%s)", TABEL_Renungan, KOLOM_Renungan_tgl));
		}
		
		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			Log.d("alki", "onUpgrade dipanggil, oldVersion=" + oldVersion + " newVersion=" + newVersion);

			if (oldVersion <= 23) {
				// konvert dari Bukmak ke Bukmak2
				bikinTabelBukmak2(db);
				
				int c = 0;
				boolean sukses = false;
				
				// pindahin data dari Bukmak ke Bukmak2
				db.beginTransaction();
				try {
					Cursor cursor = db.query(TABEL_Bukmak, new String[] {KOLOM_Bukmak_alamat, KOLOM_Bukmak_kitab, KOLOM_Bukmak_pasal, KOLOM_Bukmak_ayat, KOLOM_Bukmak_waktuTambah}, null, null, null, null, null);

					int kolom_alamat = cursor.getColumnIndex(KOLOM_Bukmak_alamat);
					int kolom_kitab = cursor.getColumnIndex(KOLOM_Bukmak_kitab);
					int kolom_pasal = cursor.getColumnIndex(KOLOM_Bukmak_pasal);
					int kolom_ayat = cursor.getColumnIndex(KOLOM_Bukmak_ayat);
					int kolom_waktuTambah = cursor.getColumnIndex(KOLOM_Bukmak_waktuTambah);
					
					ContentValues cv = new ContentValues();
					
					// default
					cv.put(KOLOM_Bukmak2_jenis, ENUM_Bukmak2_jenis_bukmak);

					while (true) {
			        	boolean more = cursor.moveToNext();
			        	if (!more) {
			        		break;
			        	}

			        	cv.put(KOLOM_Bukmak2_tulisan, cursor.getString(kolom_alamat));
			        	
			        	int kitab = cursor.getInt(kolom_kitab);
			        	int pasal = cursor.getInt(kolom_pasal);
			        	int ayat = cursor.getInt(kolom_ayat);
			        	int ari = Ari.encode(kitab, pasal, ayat);
			        	
			        	cv.put(KOLOM_Bukmak2_ari, ari);
			        	cv.put(KOLOM_Bukmak2_waktuTambah, new Integer(cursor.getString(kolom_waktuTambah)));
						db.insertOrThrow(TABEL_Bukmak2, null, cv);
						
						c++;
			        }
			        cursor.close();
					
					db.setTransactionSuccessful();
					sukses = true;
				} finally {
					db.endTransaction();
				}
				
				if (sukses) {
					new AlertDialog.Builder(context)
					.setMessage("Selamat datang di versi baru! Pembatas-pembatas buku yang anda miliki sudah diubah menjadi versi baru. Kini anda bisa menambahkan keterangan pada setiap pembatas buku.\nPembatas buku ditemukan: " + c)
					.setPositiveButton("OK", null)
					.create()
					.show();
				} else {
					new AlertDialog.Builder(context)
					.setMessage("GAGAL mengubah pembatas buku ke versi baru.\n\nMohon kirim email ke yukuku@gmail.com untuk melaporkan peristiwa ini supaya bisa dibetulkan di versi selanjutnya.")
					.setPositiveButton("OK", null)
					.create()
					.show();
				}
			}
			
		}
	}
}
