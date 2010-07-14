package yuku.alkitab.model;

import java.util.*;

import yuku.andoutil.*;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.*;
import android.database.sqlite.*;
import android.os.*;
import android.provider.*;
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
	SQLiteDatabase db;
	
	private AlkitabDb(Context context) {
		long wmulai = SystemClock.uptimeMillis();
		Helper helper = new Helper(context);

		this.context = context;
		this.db = helper.getWritableDatabase();
		
		Log.d("alki", "membuka database butuh ms: " + (SystemClock.uptimeMillis() - wmulai));
	}
	
	public SQLiteDatabase getDatabase() {
		return db;
	}
	
	public Bukmak2 getBukmak(int ari, int jenis) {
		Cursor cursor = db.query(
				TABEL_Bukmak2, 
				new String[] {BaseColumns._ID, KOLOM_Bukmak2_tulisan, KOLOM_Bukmak2_waktuTambah, KOLOM_Bukmak2_waktuUbah}, 
				KOLOM_Bukmak2_ari + "=? and " + KOLOM_Bukmak2_jenis + "=?", 
				new String[] {String.valueOf(ari), String.valueOf(jenis)}, 
				null, null, null);
		
		try {
			if (!cursor.moveToNext()) return null;
			
			int _id = cursor.getInt(cursor.getColumnIndexOrThrow(BaseColumns._ID));
			String tulisan = cursor.getString(cursor.getColumnIndexOrThrow(AlkitabDb.KOLOM_Bukmak2_tulisan));
			Date waktuTambah = Sqlitil.toDate(cursor.getInt(cursor.getColumnIndexOrThrow(AlkitabDb.KOLOM_Bukmak2_waktuTambah)));
			Date waktuUbah = Sqlitil.toDate(cursor.getInt(cursor.getColumnIndexOrThrow(AlkitabDb.KOLOM_Bukmak2_waktuUbah)));
			
			Bukmak2 res = new Bukmak2(ari, jenis, tulisan, waktuTambah, waktuUbah);
			res._id = _id;
			return res;
		} finally {
			cursor.close();
		}
	}
	

	public int updateBukmak(Bukmak2 bukmak) {
		return db.update(TABEL_Bukmak2, bukmak.toContentValues(), "_id=?", new String[] {String.valueOf(bukmak._id)});
	}
	
	public long insertBukmak(Bukmak2 bukmak) {
		return db.insert(TABEL_Bukmak2, null, bukmak.toContentValues());
	}
	
	private String[] sql_hapusBukmak_params = new String[2];
	public int hapusBukmak(int ari, int jenis) {
		sql_hapusBukmak_params[0] = String.valueOf(jenis);
		sql_hapusBukmak_params[1] = String.valueOf(ari);
		
		return db.delete(TABEL_Bukmak2, KOLOM_Bukmak2_jenis + "=? and " + KOLOM_Bukmak2_ari + "=?", sql_hapusBukmak_params);
	}

	private static String sql_countCatatan = "select count(*) from " + TABEL_Bukmak2 + " where " + KOLOM_Bukmak2_jenis + "=" + ENUM_Bukmak2_jenis_catatan + " and " + KOLOM_Bukmak2_ari + ">=? and " + KOLOM_Bukmak2_ari + "<?";
	private SQLiteStatement stmt_countCatatan = null;
	public int countCatatan(int ari_kitabpasal) {
		int ariMin = ari_kitabpasal & 0x00ffff00;
		int ariMax = ari_kitabpasal | 0x000000ff;
		
		if (stmt_countCatatan == null) {
			stmt_countCatatan = db.compileStatement(sql_countCatatan);
		}
		
		stmt_countCatatan.clearBindings();
		stmt_countCatatan.bindLong(1, ariMin);
		stmt_countCatatan.bindLong(2, ariMax);
		
		return (int) stmt_countCatatan.simpleQueryForLong();
	}
	
	private static String sql_getCatatan = "select * from " + TABEL_Bukmak2 + " where " + KOLOM_Bukmak2_jenis + "=" + ENUM_Bukmak2_jenis_catatan + " and " + KOLOM_Bukmak2_ari + ">=? and " + KOLOM_Bukmak2_ari + "<?";
	private String[] sql_getCatatan_params = new String[2];
	public int getCatatan(int ari_kitabpasal, int[] out) {
		int ariMin = ari_kitabpasal & 0x00ffff00;
		int ariMax = ari_kitabpasal | 0x000000ff;
		int res = 0;
		
		sql_getCatatan_params[0] = String.valueOf(ariMin);
		sql_getCatatan_params[1] = String.valueOf(ariMax);
		Cursor cursor = db.rawQuery(sql_getCatatan, sql_getCatatan_params);
		try {
			int index_ari = cursor.getColumnIndexOrThrow(KOLOM_Bukmak2_ari);
			while (cursor.moveToNext()) {
				out[res++] = cursor.getInt(index_ari);
			}
		} finally {
			cursor.close();
		}
		return res;
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

	public void dump() {
		Cursor c = db.query(TABEL_Bukmak2, null, null, null, null, null, null);
		while (c.moveToNext()) {
			String a = "";
			for (int i = 0; i < c.getColumnCount(); i++) {
				String sel = c.getString(i);
				a += " | " + sel;
			}
			Log.i("alki", a);
		}
		c.close();
	}
}
