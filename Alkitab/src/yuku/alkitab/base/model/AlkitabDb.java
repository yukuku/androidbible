package yuku.alkitab.base.model;

import yuku.alkitab.R;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.*;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.*;
import android.database.sqlite.*;
import android.os.SystemClock;
import android.provider.BaseColumns;
import android.util.Log;

public class AlkitabDb {
	public static final String TAG = AlkitabDb.class.getSimpleName();

	@Deprecated
	public static final String TABEL_Bukmak = "Bukmak"; //$NON-NLS-1$
	@Deprecated
	public static final String KOLOM_Bukmak_alamat = "alamat"; //$NON-NLS-1$
	@Deprecated
	public static final String KOLOM_Bukmak_cuplikan = "cuplikan"; //$NON-NLS-1$
	@Deprecated
	public static final String KOLOM_Bukmak_waktuTambah = "waktuTambah"; //$NON-NLS-1$
	@Deprecated
	public static final String KOLOM_Bukmak_kitab = "kitab"; //$NON-NLS-1$
	@Deprecated
	public static final String KOLOM_Bukmak_pasal = "pasal"; //$NON-NLS-1$
	@Deprecated
	public static final String KOLOM_Bukmak_ayat = "ayat"; //$NON-NLS-1$
	
	public static final String TABEL_Bukmak2 = "Bukmak2"; //$NON-NLS-1$
	public static final String KOLOM_Bukmak2_ari = "ari"; //$NON-NLS-1$
	public static final String KOLOM_Bukmak2_jenis = "jenis"; //$NON-NLS-1$
	public static final String KOLOM_Bukmak2_tulisan = "tulisan"; //$NON-NLS-1$
	public static final String KOLOM_Bukmak2_waktuTambah = "waktuTambah"; //$NON-NLS-1$
	public static final String KOLOM_Bukmak2_waktuUbah = "waktuUbah"; //$NON-NLS-1$
	public static final int ENUM_Bukmak2_jenis_bukmak = 1;
	public static final int ENUM_Bukmak2_jenis_catatan = 2;
	
	public static final String TABEL_Renungan = "Renungan"; //$NON-NLS-1$
	public static final String KOLOM_Renungan_nama = "nama"; //$NON-NLS-1$
	public static final String KOLOM_Renungan_tgl = "tgl"; //$NON-NLS-1$
	public static final String KOLOM_Renungan_header = "header"; //$NON-NLS-1$
	public static final String KOLOM_Renungan_judul = "judul"; //$NON-NLS-1$
	public static final String KOLOM_Renungan_isi = "isi"; //$NON-NLS-1$
	public static final String KOLOM_Renungan_siapPakai = "siapPakai"; //$NON-NLS-1$
	public static final String KOLOM_Renungan_waktuSentuh = "waktuSentuh"; //$NON-NLS-1$

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
		
		Log.d(TAG, "membuka database butuh ms: " + (SystemClock.uptimeMillis() - wmulai)); //$NON-NLS-1$
	}
	
	public SQLiteDatabase getDatabase() {
		return db;
	}
	
	public Bukmak2 getBukmakByAri(int ari, int jenis) {
		Cursor cursor = db.query(
			TABEL_Bukmak2, 
			new String[] {BaseColumns._ID, KOLOM_Bukmak2_tulisan, KOLOM_Bukmak2_waktuTambah, KOLOM_Bukmak2_waktuUbah}, 
			KOLOM_Bukmak2_ari + "=? and " + KOLOM_Bukmak2_jenis + "=?",  //$NON-NLS-1$ //$NON-NLS-2$
			new String[] {String.valueOf(ari), String.valueOf(jenis)}, 
			null, null, null
		);
		
		try {
			if (!cursor.moveToNext()) return null;
			
			Bukmak2 res = Bukmak2.dariCursor(cursor, ari, jenis);
			res._id = cursor.getInt(cursor.getColumnIndexOrThrow(BaseColumns._ID));
			
			return res;
		} finally {
			cursor.close();
		}
	}
	
	public Bukmak2 getBukmakById(long id) {
		Cursor cursor = db.query(
				TABEL_Bukmak2, 
				new String[] {KOLOM_Bukmak2_ari, KOLOM_Bukmak2_jenis, KOLOM_Bukmak2_tulisan, KOLOM_Bukmak2_waktuTambah, KOLOM_Bukmak2_waktuUbah}, 
				"_id=?", //$NON-NLS-1$
				new String[] {String.valueOf(id)}, 
				null, null, null);
		try {
			if (!cursor.moveToNext()) return null;
			
			Bukmak2 res = Bukmak2.dariCursor(cursor);
			res._id = (int) id;
			
			return res;
		} finally {
			cursor.close();
		}
	}
	

	public int updateBukmak(Bukmak2 bukmak) {
		return db.update(TABEL_Bukmak2, bukmak.toContentValues(), "_id=?", new String[] {String.valueOf(bukmak._id)}); //$NON-NLS-1$
	}
	
	public long insertBukmak(Bukmak2 bukmak) {
		return db.insert(TABEL_Bukmak2, null, bukmak.toContentValues());
	}
	
	private String[] sql_hapusBukmak_params = new String[2];
	public int hapusBukmak(int ari, int jenis) {
		sql_hapusBukmak_params[0] = String.valueOf(jenis);
		sql_hapusBukmak_params[1] = String.valueOf(ari);
		
		return db.delete(TABEL_Bukmak2, KOLOM_Bukmak2_jenis + "=? and " + KOLOM_Bukmak2_ari + "=?", sql_hapusBukmak_params); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static String sql_countAtribut = "select count(*) from " + TABEL_Bukmak2 + " where " + KOLOM_Bukmak2_ari + ">=? and " + KOLOM_Bukmak2_ari + "<?"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	private SQLiteStatement stmt_countAtribut = null;
	public int countAtribut(int ari_kitabpasal) {
		int ariMin = ari_kitabpasal & 0x00ffff00;
		int ariMax = ari_kitabpasal | 0x000000ff;
		
		if (stmt_countAtribut == null) {
			stmt_countAtribut = db.compileStatement(sql_countAtribut);
		}
		
		stmt_countAtribut.clearBindings();
		stmt_countAtribut.bindLong(1, ariMin);
		stmt_countAtribut.bindLong(2, ariMax);
		
		return (int) stmt_countAtribut.simpleQueryForLong();
	}
	
	private static String sql_getCatatan = "select * from " + TABEL_Bukmak2 + " where " + KOLOM_Bukmak2_ari + ">=? and " + KOLOM_Bukmak2_ari + "<?"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	private String[] sql_getCatatan_params = new String[2];
	/**
	 * map_0 adalah ayat, basis 0
	 */
	public int putAtribut(int ari_kitabpasal, int[] map_0) {
		int ariMin = ari_kitabpasal & 0x00ffff00;
		int ariMax = ari_kitabpasal | 0x000000ff;
		int res = 0;
		
		sql_getCatatan_params[0] = String.valueOf(ariMin);
		sql_getCatatan_params[1] = String.valueOf(ariMax);
		Cursor cursor = db.rawQuery(sql_getCatatan, sql_getCatatan_params);
		try {
			int kolom_jenis = cursor.getColumnIndexOrThrow(KOLOM_Bukmak2_jenis);
			int kolom_ari = cursor.getColumnIndexOrThrow(KOLOM_Bukmak2_ari);
			while (cursor.moveToNext()) {
				int ari = cursor.getInt(kolom_ari);
				int jenis = cursor.getInt(kolom_jenis);
				
				int ofsetMap = Ari.toAyat(ari) - 1; // dari basis1 ke basis 0
				if (ofsetMap >= map_0.length) {
					Log.e(TAG, "ofsetMap kebanyakan " + ofsetMap + " terjadi pada ari 0x" + Integer.toHexString(ari)); //$NON-NLS-1$ //$NON-NLS-2$
				} else {
					if (jenis == ENUM_Bukmak2_jenis_bukmak) {
						map_0[ofsetMap] |= 0x1;
					} else if (jenis == ENUM_Bukmak2_jenis_catatan) {
						map_0[ofsetMap] |= 0x2;
					}
				}
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
					TABEL_Bukmak2, KOLOM_Bukmak2_ari, KOLOM_Bukmak2_jenis, KOLOM_Bukmak2_tulisan, KOLOM_Bukmak2_waktuTambah, KOLOM_Bukmak2_waktuUbah));
			
			// index Bukmak2(ari)
			db.execSQL(String.format("create index if not exists index_201 on %s (%s)", TABEL_Bukmak2, KOLOM_Bukmak2_ari)); //$NON-NLS-1$
			// index Bukmak2(jenis,ari)
			db.execSQL(String.format("create index if not exists index_202 on %s (%s, %s)", TABEL_Bukmak2, KOLOM_Bukmak2_jenis, KOLOM_Bukmak2_ari)); //$NON-NLS-1$
			// index Bukmak2(jenis,waktuUbah)
			db.execSQL(String.format("create index if not exists index_203 on %s (%s, %s)", TABEL_Bukmak2, KOLOM_Bukmak2_jenis, KOLOM_Bukmak2_waktuUbah)); //$NON-NLS-1$
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
					TABEL_Renungan, KOLOM_Renungan_nama, KOLOM_Renungan_tgl, KOLOM_Renungan_header, KOLOM_Renungan_judul, KOLOM_Renungan_isi, KOLOM_Renungan_siapPakai, KOLOM_Renungan_waktuSentuh));
			
			// index Renungan(nama)
			db.execSQL(String.format("create index if not exists index_101 on %s (%s)", TABEL_Renungan, KOLOM_Renungan_nama)); //$NON-NLS-1$
			// index Renungan(nama,tgl)
			db.execSQL(String.format("create index if not exists index_102 on %s (%s, %s)", TABEL_Renungan, KOLOM_Renungan_nama, KOLOM_Renungan_tgl)); //$NON-NLS-1$
			// index Renungan(tgl)
			db.execSQL(String.format("create index if not exists index_103 on %s (%s)", TABEL_Renungan, KOLOM_Renungan_tgl)); //$NON-NLS-1$
		}
		
		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			Log.d(TAG, "onUpgrade dipanggil, oldVersion=" + oldVersion + " newVersion=" + newVersion); //$NON-NLS-1$ //$NON-NLS-2$

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
			        	Integer waktu = new Integer(cursor.getString(kolom_waktuTambah));
			        	cv.put(KOLOM_Bukmak2_waktuTambah, waktu);
			        	cv.put(KOLOM_Bukmak2_waktuUbah, waktu);
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
					.setMessage(context.getString(R.string.selamat_datang_di_versi_baru_pembatas, c))
					.setPositiveButton(R.string.ok, null)
					.create()
					.show();
				} else {
					new AlertDialog.Builder(context)
					.setMessage(R.string.gagal_mengubah_pembatas_buku)
					.setPositiveButton(R.string.ok, null)
					.create()
					.show();
				}
			}
			
		}
	}

	public void dump() {
		Cursor c = db.query(TABEL_Bukmak2, null, null, null, null, null, null);
		while (c.moveToNext()) {
			String a = ""; //$NON-NLS-1$
			for (int i = 0; i < c.getColumnCount(); i++) {
				String sel = c.getString(i);
				a += " | " + sel; //$NON-NLS-1$
			}
			Log.i(TAG, a);
		}
		c.close();
	}
}
