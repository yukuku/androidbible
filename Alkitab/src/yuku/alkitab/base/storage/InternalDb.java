package yuku.alkitab.base.storage;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.provider.BaseColumns;
import android.util.Log;

import gnu.trove.list.TIntList;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.hash.TIntLongHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.BukmakListActivity;
import yuku.alkitab.base.ac.EdisiActivity.MEdisiYes;
import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.model.Bukmak2;
import yuku.alkitab.base.model.Label;
import yuku.alkitab.base.renungan.ArtikelRenunganHarian;
import yuku.alkitab.base.renungan.ArtikelSantapanHarian;
import yuku.alkitab.base.renungan.IArtikel;
import yuku.andoutil.IntArrayList;
import yuku.andoutil.Sqlitil;

public class InternalDb {
	public static final String TAG = InternalDb.class.getSimpleName();

	private final InternalDbHelper helper;

	public InternalDb(InternalDbHelper helper) {
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
			
			return Bukmak2.dariCursor(cursor, ari, jenis);
		} finally {
			cursor.close();
		}
	}
	
	public Bukmak2 getBukmakById(long id) {
		Cursor cursor = helper.getReadableDatabase().query(
			Db.TABEL_Bukmak2, 
			null, 
			"_id=?", //$NON-NLS-1$
			new String[] {String.valueOf(id)}, 
			null, null, null
		);
		
		try {
			if (!cursor.moveToNext()) return null;
			
			return Bukmak2.dariCursor(cursor);
		} finally {
			cursor.close();
		}
	}

	public int updateBukmak(Bukmak2 bukmak) {
		return helper.getWritableDatabase().update(Db.TABEL_Bukmak2, bukmak.toContentValues(), "_id=?", new String[] {String.valueOf(bukmak._id)}); //$NON-NLS-1$
	}
	
	public Bukmak2 insertBukmak(int ari, int jenis, String tulisan, Date waktuTambah, Date waktuUbah) {
		Bukmak2 res = new Bukmak2(ari, jenis, tulisan, waktuTambah, waktuUbah);
		SQLiteDatabase db = helper.getWritableDatabase();
		long _id = db.insert(Db.TABEL_Bukmak2, null, res.toContentValues());
		if (_id == -1) {
			return null;
		} else {
			res._id = _id;
			return res;
		}
	}
	
	public void hapusBukmakByAri(int ari, int jenis) {
		SQLiteDatabase db = helper.getWritableDatabase();
		db.beginTransaction();
		try {
			SQLiteStatement stmt = db.compileStatement("select _id from " + Db.TABEL_Bukmak2 + " where " + Db.Bukmak2.jenis + "=? and " + Db.Bukmak2.ari + "=?"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			stmt.bindLong(1, jenis);
			stmt.bindLong(2, ari);
			long _id = stmt.simpleQueryForLong();
			
			String[] params = {String.valueOf(_id)};
			db.delete(Db.TABEL_Bukmak2_Label, Db.Bukmak2_Label.bukmak2_id + "=?", params); //$NON-NLS-1$
			db.delete(Db.TABEL_Bukmak2, "_id=?", params); //$NON-NLS-1$
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

	public void hapusBukmakById(long id) {
		SQLiteDatabase db = helper.getWritableDatabase();
		db.beginTransaction();
		try {
			String[] params = new String[] {String.valueOf(id)};
			db.delete(Db.TABEL_Bukmak2_Label, Db.Bukmak2_Label.bukmak2_id + "=?", params); //$NON-NLS-1$
			db.delete(Db.TABEL_Bukmak2, "_id=?", params); //$NON-NLS-1$
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

	public Cursor listBukmak(int jenis, long labelId, String sortColumn, boolean sortAscending) {
        SQLiteDatabase db = helper.getReadableDatabase();

        String sortClause = sortColumn + (Db.Bukmak2.tulisan.equals(sortColumn)? " collate NOCASE ": "") + (sortAscending? " asc": " desc"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        
        if (labelId == 0) { // no restrictions
            return db.query(Db.TABEL_Bukmak2, null, Db.Bukmak2.jenis + "=?", new String[]{String.valueOf(jenis)}, null, null, sortClause); //$NON-NLS-1$
        } else if (labelId == BukmakListActivity.LABELID_noLabel) { // only without label
            return db.rawQuery("select " + Db.TABEL_Bukmak2 + ".* from " + Db.TABEL_Bukmak2 + " where " + Db.TABEL_Bukmak2 + "." + Db.Bukmak2.jenis + "=? and " + Db.TABEL_Bukmak2 + "." + BaseColumns._ID + " not in (select " + Db.Bukmak2_Label.bukmak2_id + " from " + Db.TABEL_Bukmak2_Label + ") order by " + Db.TABEL_Bukmak2 + "." + sortClause, new String[] {String.valueOf(jenis)});  //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$
        } else { // filter by labelId
            return db.rawQuery("select " + Db.TABEL_Bukmak2 + ".* from " + Db.TABEL_Bukmak2 + ", " + Db.TABEL_Bukmak2_Label + " where " + Db.Bukmak2.jenis + "=? and " + Db.TABEL_Bukmak2 + "." + BaseColumns._ID + " = " + Db.TABEL_Bukmak2_Label + "." + Db.Bukmak2_Label.bukmak2_id + " and " + Db.TABEL_Bukmak2_Label + "." + Db.Bukmak2_Label.label_id + "=? order by " + Db.TABEL_Bukmak2 + "." + sortClause, new String[] {String.valueOf(jenis), String.valueOf(labelId)});          //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$//$NON-NLS-5$//$NON-NLS-6$//$NON-NLS-7$//$NON-NLS-8$//$NON-NLS-9$//$NON-NLS-10$ //$NON-NLS-11$ //$NON-NLS-12$
        }
	}

	public void importBukmak(List<Bukmak2> xbukmak, boolean tumpuk, TObjectIntHashMap<Bukmak2> bukmakToRelIdMap, TIntLongHashMap labelRelIdToAbsIdMap, TIntObjectHashMap<TIntList> bukmak2RelIdToLabelRelIdsMap) {
		SQLiteDatabase db = helper.getWritableDatabase();
		db.beginTransaction();
		try {
			TIntLongHashMap bukmakRelIdToAbsIdMap = new TIntLongHashMap();

			{ // tulis bukmak2 baru
				String[] params1 = new String[1];
				String[] params2 = new String[2];
				for (Bukmak2 bukmak: xbukmak) {
					int bukmak2_relId = bukmakToRelIdMap.get(bukmak);
					
					params2[0] = String.valueOf(bukmak.ari);
					params2[1] = String.valueOf(bukmak.jenis);
					
					long _id = -1;
					
					boolean ada = false;
					Cursor cursor = db.query(Db.TABEL_Bukmak2, null, Db.Bukmak2.ari + "=? and " + Db.Bukmak2.jenis + "=?", params2, null, null, null); //$NON-NLS-1$ //$NON-NLS-2$
					if (cursor.moveToNext()) {
						ada = true;
						_id = cursor.getLong(cursor.getColumnIndexOrThrow(BaseColumns._ID)); /* [1] */
					}
					cursor.close();
					
					// --------------------------------- dapet _id dari
					//  ada  tumpuk:     delete insert     [2]
					//  ada !tumpuk: (nop)                 [1]
					// !ada  tumpuk:            insert     [2]
					// !ada !tumpuk:            insert     [2]
					
					if (ada && tumpuk) {
						params1[0] = String.valueOf(_id);
						db.delete(Db.TABEL_Bukmak2, "_id=?", params1);
						db.delete(Db.TABEL_Bukmak2_Label, Db.Bukmak2_Label.bukmak2_id + "=?", params1); //$NON-NLS-1$
					}
					if ((ada && tumpuk) || (!ada)) {
						_id = db.insert(Db.TABEL_Bukmak2, null, bukmak.toContentValues()); /* [2] */
					}
					
					// map it
					bukmakRelIdToAbsIdMap.put(bukmak2_relId, _id);
				}
			}
			
			{ // sekarang pemasangan label
				String where = Db.Bukmak2_Label.bukmak2_id + "=?";
				String[] params = {null};
				ContentValues cv = new ContentValues();
				
				// nlabel>0  tumpuk:  delete insert
				// nlabel>0 !tumpuk: (nop)
				// nlabel=0  tumpuk:         insert
				// nlabel=0 !tumpuk:         insert
				
				for (int bukmak2_relId: bukmak2RelIdToLabelRelIdsMap.keys()) {
					TIntList label_relIds = bukmak2RelIdToLabelRelIdsMap.get(bukmak2_relId);
					
					long bukmak2_id = bukmakRelIdToAbsIdMap.get(bukmak2_relId);
					
					if (bukmak2_id > 0) {
						params[0] = String.valueOf(bukmak2_id);
						
						// cek ada berapa label untuk bukmak2_id ini
						int nlabel = 0;
						Cursor c = db.rawQuery("select count(*) from " + Db.TABEL_Bukmak2_Label + " where " + where, params);
						try {
							c.moveToNext();
							nlabel = c.getInt(0);
						} finally {
							c.close();
						}
						
						if (nlabel>0 && tumpuk) {
							db.delete(Db.TABEL_Bukmak2_Label, where, params);
						}
						if ((nlabel>0 && tumpuk) || (!(nlabel>0))) {
							for (int label_relId: label_relIds.toArray()) {
								long label_id = labelRelIdToAbsIdMap.get(label_relId);
								if (label_id > 0) {
									cv.put(Db.Bukmak2_Label.bukmak2_id, bukmak2_id);
									cv.put(Db.Bukmak2_Label.label_id, label_id);
									db.insert(Db.TABEL_Bukmak2_Label, null, cv);
								} else {
									Log.w(TAG, "label_id ngaco!: " + label_id);
								}
							}
						}
					} else {
						Log.w(TAG, "bukmak2_id ngaco!: " + bukmak2_id);
					}
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

	private SQLiteStatement stmt_countAtribut = null;
	public int countAtribut(int ari_kitabpasal) {
		int ariMin = ari_kitabpasal & 0x00ffff00;
		int ariMax = ari_kitabpasal | 0x000000ff;
		
		if (stmt_countAtribut == null) {
			stmt_countAtribut = helper.getReadableDatabase().compileStatement("select count(*) from " + Db.TABEL_Bukmak2 + " where " + Db.Bukmak2.ari + ">=? and " + Db.Bukmak2.ari + "<?");//$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		}
		
		stmt_countAtribut.clearBindings();
		stmt_countAtribut.bindLong(1, ariMin);
		stmt_countAtribut.bindLong(2, ariMax);
		
		return (int) stmt_countAtribut.simpleQueryForLong();
	}
	
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
		Cursor cursor = helper.getReadableDatabase().rawQuery("select * from " + Db.TABEL_Bukmak2 + " where " + Db.Bukmak2.ari + ">=? and " + Db.Bukmak2.ari + "<?", sql_getCatatan_params); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
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

	public void updateAtauInsertStabilo(int ariKp, IntArrayList ayatTerpilih, int warnaRgb) {
		SQLiteDatabase db = helper.getWritableDatabase();
		
		String[] params = {null /* buat ari */, String.valueOf(Db.Bukmak2.jenis_stabilo)};
		
		db.beginTransaction();
		try {
			// setiap ayat yang diminta
			for (int i = 0; i < ayatTerpilih.size(); i++) {
				int ayat_1 = ayatTerpilih.get(i);
				int ari = Ari.encodeWithKp(ariKp, ayat_1);
				params[0] = String.valueOf(ari);
				
				Cursor c = db.query(Db.TABEL_Bukmak2, null, Db.Bukmak2.ari + "=? and " + Db.Bukmak2.jenis + "=?", params, null, null, null); //$NON-NLS-1$ //$NON-NLS-2$
				try {
					if (c.moveToNext()) { // cek dulu ada ato ga
						// sudah ada!
						Bukmak2 bukmak = Bukmak2.dariCursor(c);
						bukmak.waktuUbah = new Date();
						if (warnaRgb != -1) {
							bukmak.tulisan = U.enkodStabilo(warnaRgb);
							db.update(Db.TABEL_Bukmak2, bukmak.toContentValues(), "_id=?", new String[] {String.valueOf(bukmak._id)}); //$NON-NLS-1$
						} else {
							// delete
							db.delete(Db.TABEL_Bukmak2, "_id=?", new String[] {String.valueOf(bukmak._id)}); //$NON-NLS-1$
						}
					} else {
						// belum ada!
						if (warnaRgb == -1) {
							// ga usa ngapa2in, dari belum ada jadi tetep ga ada
						} else {
							Date kini = new Date();
							Bukmak2 bukmak = new Bukmak2(ari, Db.Bukmak2.jenis_stabilo, U.enkodStabilo(warnaRgb), kini, kini); 
							db.insert(Db.TABEL_Bukmak2, null, bukmak.toContentValues());
						}
					}
				} finally {
					c.close();
				}
			}
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
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
	
	public int getWarnaRgbStabilo(int ariKp, IntArrayList terpilih) {
		int ariMin = ariKp;
		int ariMax = ariKp | 0xff;
		int[] xwarna = new int[256];
		int res = -2;
		
		for (int i = 0; i < xwarna.length; i++) xwarna[i] = -1;
		
		// cek dulu ada ato ga
		Cursor c = helper.getReadableDatabase().query(Db.TABEL_Bukmak2, null, Db.Bukmak2.ari + ">? and " + Db.Bukmak2.ari + "<=? and " + Db.Bukmak2.jenis + "=?", new String[] {String.valueOf(ariMin), String.valueOf(ariMax), String.valueOf(Db.Bukmak2.jenis_stabilo)}, null, null, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		try {
			int ari_col = c.getColumnIndexOrThrow(Db.Bukmak2.ari);
			int tulisan_col = c.getColumnIndexOrThrow(Db.Bukmak2.tulisan);
			
			// masukin aja ke array dulu
			while (c.moveToNext()) { 
				int ari = c.getInt(ari_col);
				int index = ari & 0xff;
				int warna = U.dekodStabilo(c.getString(tulisan_col));
				xwarna[index] = warna;
			}
			
			// tentukan warna default. Kalau semua berwarna x, maka jadi x. Kalau ada salah satu yang bukan x, jadi -1;
			for (int i = 0; i < terpilih.size(); i++) {
				int ayat_1 = terpilih.get(i);
				int warna = xwarna[ayat_1];
				if (res == -2) {
					res = warna;
				} else if (warna != res) {
					return -1;
				}
			}
			
			if (res == -2) return -1;
			return res;
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
	
	public int hapusRenunganBerwaktuSentuhSebelum(Date date) {
		SQLiteDatabase db = helper.getWritableDatabase();
		return db.delete(Db.TABEL_Renungan, Db.Renungan.waktuSentuh + "<?", new String[] {String.valueOf(Sqlitil.toInt(date))}); //$NON-NLS-1$
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

	public List<MEdisiYes> listSemuaEdisi() {
		List<MEdisiYes> res = new ArrayList<MEdisiYes>();
		Cursor cursor = helper.getReadableDatabase().query(Db.TABEL_Edisi, null, null, null, null, null, Db.Edisi.urutan + " asc"); //$NON-NLS-1$
		try {
			int col_aktif = cursor.getColumnIndexOrThrow(Db.Edisi.aktif);
			int col_judul = cursor.getColumnIndexOrThrow(Db.Edisi.judul);
			int col_keterangan = cursor.getColumnIndexOrThrow(Db.Edisi.keterangan);
			int col_namafile = cursor.getColumnIndexOrThrow(Db.Edisi.namafile);
			int col_namafile_pdbasal = cursor.getColumnIndexOrThrow(Db.Edisi.namafile_pdbasal);
			int col_urutan = cursor.getColumnIndexOrThrow(Db.Edisi.urutan);
			
			while (cursor.moveToNext()) {
				MEdisiYes yes = new MEdisiYes();
				yes.cache_aktif = cursor.getInt(col_aktif) != 0;
				yes.jenis = Db.Edisi.jenis_yes;
				yes.keterangan = cursor.getString(col_keterangan);
				yes.judul = cursor.getString(col_judul);
				yes.namafile = cursor.getString(col_namafile);
				yes.namafile_pdbasal = cursor.getString(col_namafile_pdbasal);
				yes.urutan = cursor.getInt(col_urutan);
				res.add(yes);
			}
		} finally {
			cursor.close();
		}
		return res;
	}

	public void setEdisiYesAktif(String namafile, boolean aktif) {
		SQLiteDatabase db = helper.getWritableDatabase();
		ContentValues cv = new ContentValues();
		cv.put(Db.Edisi.aktif, aktif? 1: 0);
		db.update(Db.TABEL_Edisi, cv, Db.Edisi.jenis + "=? and " + Db.Edisi.namafile + "=?", new String[] {String.valueOf(Db.Edisi.jenis_yes), namafile}); //$NON-NLS-1$ //$NON-NLS-2$
	}

	public int getUrutanTerbesarEdisiYes() {
		SQLiteDatabase db = helper.getReadableDatabase();
		SQLiteStatement stmt = db.compileStatement("select max(" + Db.Edisi.urutan + ") from " + Db.TABEL_Edisi);  //$NON-NLS-1$//$NON-NLS-2$
		return (int) stmt.simpleQueryForLong();
	}

	public void tambahEdisiYesDenganAktif(MEdisiYes edisi, boolean aktif) {
		SQLiteDatabase db = helper.getWritableDatabase();
		ContentValues cv = new ContentValues();
		cv.put(Db.Edisi.aktif, aktif);
		cv.put(Db.Edisi.jenis, Db.Edisi.jenis_yes);
		cv.put(Db.Edisi.judul, edisi.judul);
		cv.put(Db.Edisi.keterangan, edisi.keterangan);
		cv.put(Db.Edisi.namafile, edisi.namafile);
		cv.put(Db.Edisi.namafile_pdbasal, edisi.namafile_pdbasal);
		cv.put(Db.Edisi.urutan, edisi.urutan);
		Log.d(TAG, "tambah edisi yes: " + cv.toString()); //$NON-NLS-1$
		db.insert(Db.TABEL_Edisi, null, cv);
	}

	public boolean adakahEdisiYesDenganNamafile(String namafile) {
		SQLiteDatabase db = helper.getReadableDatabase();
		SQLiteStatement stmt = db.compileStatement("select count(*) from " + Db.TABEL_Edisi + " where " + Db.Edisi.jenis + "=? and " + Db.Edisi.namafile + "=?");    //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$
		stmt.clearBindings();
		stmt.bindLong(1, Db.Edisi.jenis_yes);
		stmt.bindString(2, namafile);
		return stmt.simpleQueryForLong() > 0;
	}

	public void hapusEdisiYes(MEdisiYes edisi) {
		SQLiteDatabase db = helper.getWritableDatabase();
		db.delete(Db.TABEL_Edisi, Db.Edisi.namafile + "=?", new String[] {edisi.namafile}); //$NON-NLS-1$
	}
	
	public List<Label> listSemuaLabel() {
		List<Label> res = new ArrayList<Label>();
		Cursor cursor = helper.getReadableDatabase().query(Db.TABEL_Label, null, null, null, null, null, Db.Label.urutan + " asc"); //$NON-NLS-1$
		try {
			while (cursor.moveToNext()) {
				res.add(Label.fromCursor(cursor));
			}
		} finally {
			cursor.close();
		}
		return res;
	}
	
	/**
	 * @return null when not found
	 */
	public List<Label> listLabels(long bukmak2_id) {
		List<Label> res = null;
		Cursor cursor = helper.getReadableDatabase().rawQuery("select " + Db.TABEL_Label + ".* from " + Db.TABEL_Label + ", " + Db.TABEL_Bukmak2_Label + " where " + Db.TABEL_Bukmak2_Label + "." + Db.Bukmak2_Label.label_id + " = " + Db.TABEL_Label + "." + BaseColumns._ID + " and " + Db.TABEL_Bukmak2_Label + "." + Db.Bukmak2_Label.bukmak2_id + " = ?  order by " + Db.TABEL_Label + "." + Db.Label.urutan + " asc", new String[] {String.valueOf(bukmak2_id)});       //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$//$NON-NLS-5$//$NON-NLS-6$//$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$ //$NON-NLS-12$
		try {
			while (cursor.moveToNext()) {
				if (res == null) res = new ArrayList<Label>();
				res.add(Label.fromCursor(cursor));
			}
		} finally {
			cursor.close();
		}
		return res;
	}
	
	/**
	 * @return null when not found
	 */
	public TLongList listLabelIds(long bukmak2_id) {
		TLongList res = null;
		Cursor cursor = helper.getReadableDatabase().rawQuery("select " + Db.TABEL_Bukmak2_Label + "." + Db.Bukmak2_Label.label_id + " from " + Db.TABEL_Bukmak2_Label + " where " + Db.TABEL_Bukmak2_Label + "." + Db.Bukmak2_Label.bukmak2_id + "=?", new String[] {String.valueOf(bukmak2_id)});       //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$//$NON-NLS-5$//$NON-NLS-6$//$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$ //$NON-NLS-12$
		try {
			int col_label_id = cursor.getColumnIndexOrThrow(Db.Bukmak2_Label.label_id);
			while (cursor.moveToNext()) {
				if (res == null) res = new TLongArrayList();
				res.add(cursor.getLong(col_label_id));
			}
		} finally {
			cursor.close();
		}
		return res;
	}

	public int getUrutanTerbesarLabel() {
		SQLiteDatabase db = helper.getReadableDatabase();
		SQLiteStatement stmt = db.compileStatement("select max(" + Db.Label.urutan + ") from " + Db.TABEL_Label);  //$NON-NLS-1$//$NON-NLS-2$
		return (int) stmt.simpleQueryForLong();
	}

	public Label tambahLabel(String judul) {
		Label res = new Label(-1, judul, getUrutanTerbesarLabel() + 1, ""); //$NON-NLS-1$
		SQLiteDatabase db = helper.getWritableDatabase();
		long _id = db.insert(Db.TABEL_Label, null, res.toContentValues());
		if (_id == -1) {
			return null;
		} else {
			res._id = _id;
			return res;
		}
	}

	public void updateLabels(Bukmak2 bukmak, Set<Label> labels) {
		SQLiteDatabase db = helper.getWritableDatabase();
		db.beginTransaction();
		try {
			// hapus semua
			db.delete(Db.TABEL_Bukmak2_Label, Db.Bukmak2_Label.bukmak2_id + "=?", new String[] {String.valueOf(bukmak._id)}); //$NON-NLS-1$
			
			// tambah semua
			ContentValues cv = new ContentValues();
			for (Label label: labels) {
				cv.put(Db.Bukmak2_Label.bukmak2_id, bukmak._id);
				cv.put(Db.Bukmak2_Label.label_id, label._id);
				db.insert(Db.TABEL_Bukmak2_Label, null, cv);
			}
			
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

    public Label getLabelById(long labelId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor cursor = db.query(Db.TABEL_Label, null, BaseColumns._ID + "=?", new String[] {String.valueOf(labelId)}, null, null, null); //$NON-NLS-1$
        try {
            if (cursor.moveToNext()) {
                return Label.fromCursor(cursor);
            } else {
                return null;
            }
        } finally {
            cursor.close();
        }
    }

	public void hapusLabelById(long id) {
		SQLiteDatabase db = helper.getWritableDatabase();
		db.beginTransaction();
		try {
			String[] params = new String[] {String.valueOf(id)};
			db.delete(Db.TABEL_Bukmak2_Label, Db.Bukmak2_Label.label_id + "=?", params); //$NON-NLS-1$
			db.delete(Db.TABEL_Label, "_id=?", params); //$NON-NLS-1$
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

	public void updateLabel(Label label) {
		SQLiteDatabase db = helper.getWritableDatabase();
		ContentValues cv = label.toContentValues();
		db.update(Db.TABEL_Label, cv, "_id=?", new String[] {String.valueOf(label._id)}); //$NON-NLS-1$
	}

	public int countBukmakDenganLabel(Label label) {
		SQLiteDatabase db = helper.getReadableDatabase();
		SQLiteStatement stmt = db.compileStatement("select count(*) from " + Db.TABEL_Bukmak2_Label + " where " + Db.Bukmak2_Label.label_id + "=?"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		stmt.bindLong(1, label._id);
		return (int) stmt.simpleQueryForLong();
	}
}
