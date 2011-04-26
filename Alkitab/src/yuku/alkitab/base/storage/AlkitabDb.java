package yuku.alkitab.base.storage;

import java.util.*;

import yuku.alkitab.base.U;
import yuku.alkitab.base.model.*;
import yuku.alkitab.base.renungan.*;
import yuku.andoutil.Sqlitil;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.*;
import android.provider.BaseColumns;
import android.util.Log;

public class AlkitabDb {
	public static final String TAG = AlkitabDb.class.getSimpleName();

	private final InternalDbHelper helper;

	public AlkitabDb(InternalDbHelper helper) {
		this.helper = helper;
	}
	
	public Bukmak2 getBukmakByAri(int ari, int jenis) {
		Cursor cursor = helper.getReadableDatabase().query(
			Db.TABEL_Bukmak2, 
			new String[] {BaseColumns._ID, Db.Bukmak2.tulisan, Db.Bukmak2.waktuTambah, Db.Bukmak2.waktuUbah}, 
			Db.Bukmak2.ari + "=? and " + Db.Bukmak2.jenis + "=?",  //$NON-NLS-1$ //$NON-NLS-2$
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
		Cursor cursor = helper.getReadableDatabase().query(
				Db.TABEL_Bukmak2, 
				new String[] {Db.Bukmak2.ari, Db.Bukmak2.jenis, Db.Bukmak2.tulisan, Db.Bukmak2.waktuTambah, Db.Bukmak2.waktuUbah}, 
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
		return helper.getWritableDatabase().update(Db.TABEL_Bukmak2, bukmak.toContentValues(), "_id=?", new String[] {String.valueOf(bukmak._id)}); //$NON-NLS-1$
	}
	
	public long insertBukmak(Bukmak2 bukmak) {
		return helper.getWritableDatabase().insert(Db.TABEL_Bukmak2, null, bukmak.toContentValues());
	}
	
	private String[] sql_hapusBukmak_params = new String[2];
	public int hapusBukmakByAri(int ari, int jenis) {
		sql_hapusBukmak_params[0] = String.valueOf(jenis);
		sql_hapusBukmak_params[1] = String.valueOf(ari);
		
		return helper.getWritableDatabase().delete(Db.TABEL_Bukmak2, Db.Bukmak2.jenis + "=? and " + Db.Bukmak2.ari + "=?", sql_hapusBukmak_params); //$NON-NLS-1$ //$NON-NLS-2$
	}

	public void hapusBukmakById(long id) {
		helper.getWritableDatabase().delete(Db.TABEL_Bukmak2, "_id=?", new String[] {String.valueOf(id)}); //$NON-NLS-1$
	}

	public Cursor listBukmak(String[] cursorColumnsSelect, int jenisBukmak) {
		return helper.getReadableDatabase().query(Db.TABEL_Bukmak2, cursorColumnsSelect, Db.Bukmak2.jenis + "=?", new String[] {String.valueOf(jenisBukmak)}, null, null, Db.Bukmak2.waktuUbah + " desc"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	public void importBukmak(List<Bukmak2> list, boolean tumpuk) {
		SQLiteDatabase db = helper.getWritableDatabase();
		
		db.beginTransaction();
		try {
			String where = Db.Bukmak2.ari + "=? and " + Db.Bukmak2.jenis + "=?"; //$NON-NLS-1$ //$NON-NLS-2$
			String[] plc = new String[2];
			
			for (Bukmak2 bukmak2: list) {
				plc[0] = String.valueOf(bukmak2.ari);
				plc[1] = String.valueOf(bukmak2.jenis);
				
				boolean ada = false;
				Cursor cursor = db.query(Db.TABEL_Bukmak2, null, where, plc, null, null, null);
				if (cursor.moveToNext()) {
					// ada, maka kita perlu hapus
					ada = true;
				}
				cursor.close();
				
				//  ada  tumpuk:     delete insert
				//  ada !tumpuk: (nop)
				// !ada  tumpuk:            insert
				// !ada !tumpuk:            insert
				
				if (ada && tumpuk) {
					db.delete(Db.TABEL_Bukmak2, where, plc);
				}
				if ((ada && tumpuk) || (!ada)) {
					db.insert(Db.TABEL_Bukmak2, null, bukmak2.toContentValues());
				}
			}
	
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

	public Cursor listSemuaBukmak() {
		return helper.getReadableDatabase().query(Db.TABEL_Bukmak2, null, null, null, null, null, null);
	}

	private static String sql_countAtribut = "select count(*) from " + Db.TABEL_Bukmak2 + " where " + Db.Bukmak2.ari + ">=? and " + Db.Bukmak2.ari + "<?"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	private SQLiteStatement stmt_countAtribut = null;
	public int countAtribut(int ari_kitabpasal) {
		int ariMin = ari_kitabpasal & 0x00ffff00;
		int ariMax = ari_kitabpasal | 0x000000ff;
		
		if (stmt_countAtribut == null) {
			stmt_countAtribut = helper.getReadableDatabase().compileStatement(sql_countAtribut);
		}
		
		stmt_countAtribut.clearBindings();
		stmt_countAtribut.bindLong(1, ariMin);
		stmt_countAtribut.bindLong(2, ariMax);
		
		return (int) stmt_countAtribut.simpleQueryForLong();
	}
	
	private static String sql_getCatatan = "select * from " + Db.TABEL_Bukmak2 + " where " + Db.Bukmak2.ari + ">=? and " + Db.Bukmak2.ari + "<?"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	private String[] sql_getCatatan_params = new String[2];
	/**
	 * @param map_0 adalah ayat, basis 0
	 * @return null kalau ga ada warna stabilo, atau int[] kalau ada, sesuai offset map_0.
	 */
	public int[] putAtribut(int ari_kitabpasal, int[] map_0) {
		int ariMin = ari_kitabpasal & 0x00ffff00;
		int ariMax = ari_kitabpasal | 0x000000ff;
		int[] res = null;
		
		sql_getCatatan_params[0] = String.valueOf(ariMin);
		sql_getCatatan_params[1] = String.valueOf(ariMax);
		Cursor cursor = helper.getReadableDatabase().rawQuery(sql_getCatatan, sql_getCatatan_params);
		try {
			int kolom_jenis = cursor.getColumnIndexOrThrow(Db.Bukmak2.jenis);
			int kolom_ari = cursor.getColumnIndexOrThrow(Db.Bukmak2.ari);
			int kolom_tulisan = cursor.getColumnIndexOrThrow(Db.Bukmak2.tulisan);
			while (cursor.moveToNext()) {
				int ari = cursor.getInt(kolom_ari);
				int jenis = cursor.getInt(kolom_jenis);
				
				int ofsetMap = Ari.toAyat(ari) - 1; // dari basis1 ke basis 0
				if (ofsetMap >= map_0.length) {
					Log.e(TAG, "ofsetMap kebanyakan " + ofsetMap + " terjadi pada ari 0x" + Integer.toHexString(ari)); //$NON-NLS-1$ //$NON-NLS-2$
				} else {
					if (jenis == Db.Bukmak2.jenis_bukmak) {
						map_0[ofsetMap] |= 0x1;
					} else if (jenis == Db.Bukmak2.jenis_catatan) {
						map_0[ofsetMap] |= 0x2;
					} else if (jenis == Db.Bukmak2.jenis_stabilo) {
						map_0[ofsetMap] |= 0x4;
						
						String tulisan = cursor.getString(kolom_tulisan);
						int warnaRgb = U.dekodStabilo(tulisan);
						
						if (res == null) res = new int[map_0.length];
						res[ofsetMap] = warnaRgb;
					}
				}
			}
		} finally {
			cursor.close();
		}
		return res;
	}

	public void updateAtauInsertStabilo(int ari, int warnaRgb) {
		// cek dulu ada ato ga
		Cursor c = helper.getWritableDatabase().query(Db.TABEL_Bukmak2, null, Db.Bukmak2.ari + "=? and " + Db.Bukmak2.jenis + "=?", new String[] {String.valueOf(ari), String.valueOf(Db.Bukmak2.jenis_stabilo)}, null, null, null); //$NON-NLS-1$ //$NON-NLS-2$
		try {
			if (c.moveToNext()) {
				// sudah ada!
				Bukmak2 bukmak = Bukmak2.dariCursor(c);
				long id = c.getLong(c.getColumnIndexOrThrow("_id")); //$NON-NLS-1$
				if (warnaRgb != -1) {
					bukmak.tulisan = U.enkodStabilo(warnaRgb);
					helper.getWritableDatabase().update(Db.TABEL_Bukmak2, bukmak.toContentValues(), "_id=?", new String[] {String.valueOf(id)}); //$NON-NLS-1$
				} else {
					// delete
					helper.getWritableDatabase().delete(Db.TABEL_Bukmak2, "_id=?", new String[] {String.valueOf(id)}); //$NON-NLS-1$
				}
			} else {
				// belum ada!
				if (warnaRgb == -1) {
					// ga usa ngapa2in, dari belum ada jadi tetep ga ada
				} else {
					Date kini = new Date();
					Bukmak2 bukmak = new Bukmak2(ari, Db.Bukmak2.jenis_stabilo, U.enkodStabilo(warnaRgb), kini, kini); 
					helper.getWritableDatabase().insert(Db.TABEL_Bukmak2, null, bukmak.toContentValues());
				}
			}
		} finally {
			c.close();
		}
	}

	public int getWarnaRgbStabilo(int ari) {
		// cek dulu ada ato ga
		Cursor c = helper.getReadableDatabase().query(Db.TABEL_Bukmak2, null, Db.Bukmak2.ari + "=? and " + Db.Bukmak2.jenis + "=?", new String[] {String.valueOf(ari), String.valueOf(Db.Bukmak2.jenis_stabilo)}, null, null, null); //$NON-NLS-1$ //$NON-NLS-2$
		try {
			if (c.moveToNext()) {
				// sudah ada!
				Bukmak2 bukmak = Bukmak2.dariCursor(c);
				return U.dekodStabilo(bukmak.tulisan);
			} else {
				return -1;
			}
		} finally {
			c.close();
		}
	}

	public boolean simpanArtikelKeRenungan(IArtikel artikel) {
		boolean res = false;
		
		SQLiteDatabase db = helper.getWritableDatabase();

		db.beginTransaction();
		try {
			// hapus dulu yang lama.
			db.delete(Db.TABEL_Renungan, Db.Renungan.nama + "=? and " + Db.Renungan.tgl + "=?", new String[] {artikel.getNama(), artikel.getTgl()}); //$NON-NLS-1$ //$NON-NLS-2$

			ContentValues values = new ContentValues();
			values.put(Db.Renungan.nama, artikel.getNama());
			values.put(Db.Renungan.tgl, artikel.getTgl());
			values.put(Db.Renungan.siapPakai, artikel.getSiapPakai()? 1: 0);
			
			if (artikel.getSiapPakai()) {
				values.put(Db.Renungan.judul, artikel.getJudul().toString());
				values.put(Db.Renungan.isi, artikel.getIsiHtml());
				values.put(Db.Renungan.header, artikel.getHeaderHtml());
			} else {
				values.put(Db.Renungan.judul, (String)null);
				values.put(Db.Renungan.isi, (String)null);
				values.put(Db.Renungan.header, (String)null);
			}
			
			values.put(Db.Renungan.waktuSentuh, Sqlitil.nowDateTime());
			
			db.insert(Db.TABEL_Renungan, null, values);
			
			db.setTransactionSuccessful();
			
			res = true;
			Log.d(TAG, "TukangDonlot donlot selesai dengan sukses dan uda masuk ke db"); //$NON-NLS-1$
		} finally {
			db.endTransaction();
		}
		return res;
	}
	
	/**
	 * Coba ambil artikel dari db lokal. Artikel ga siap pakai pun akan direturn.
	 */
	public IArtikel cobaAmbilRenungan(String nama, String tgl) {
		Cursor c = helper.getReadableDatabase().query(Db.TABEL_Renungan, null, Db.Renungan.nama + "=? and " + Db.Renungan.tgl + "=?", new String[] { nama, tgl }, null, null, null); //$NON-NLS-1$ //$NON-NLS-2$
		try {
			if (c.moveToNext()) {
				IArtikel res = null;
				if (nama.equals("rh")) { //$NON-NLS-1$
					res = new ArtikelRenunganHarian(
						tgl,
						c.getString(c.getColumnIndexOrThrow(Db.Renungan.judul)),
						c.getString(c.getColumnIndexOrThrow(Db.Renungan.header)),
						c.getString(c.getColumnIndexOrThrow(Db.Renungan.isi)),
						c.getInt(c.getColumnIndexOrThrow(Db.Renungan.siapPakai)) > 0
					);
				} else if (nama.equals("sh")) { //$NON-NLS-1$
					res = new ArtikelSantapanHarian(
						tgl,
						c.getString(c.getColumnIndexOrThrow(Db.Renungan.judul)),
						c.getString(c.getColumnIndexOrThrow(Db.Renungan.header)),
						c.getString(c.getColumnIndexOrThrow(Db.Renungan.isi)),
						c.getInt(c.getColumnIndexOrThrow(Db.Renungan.siapPakai)) > 0
					);
				}
	
				return res;
			} else {
				return null;
			}
		} finally {
			c.close();
		}
	}
}
