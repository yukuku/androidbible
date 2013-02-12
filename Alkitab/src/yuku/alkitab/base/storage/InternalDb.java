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

import yuku.afw.D;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.BookmarkListActivity;
import yuku.alkitab.base.ac.VersionsActivity.MVersionYes;
import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.model.Bookmark2;
import yuku.alkitab.base.model.Label;
import yuku.alkitab.base.renungan.ArtikelRenunganHarian;
import yuku.alkitab.base.renungan.ArtikelSantapanHarian;
import yuku.alkitab.base.renungan.IArtikel;
import yuku.alkitab.base.util.IntArrayList;
import yuku.alkitab.base.util.Sqlitil;

public class InternalDb {
	public static final String TAG = InternalDb.class.getSimpleName();

	private final InternalDbHelper helper;

	public InternalDb(InternalDbHelper helper) {
		this.helper = helper;
	}
	
	public Bookmark2 getBukmakByAri(int ari, int jenis) {
		Cursor cursor = helper.getReadableDatabase().query(
			Db.TABLE_Bookmark2, 
			new String[] {BaseColumns._ID, Db.Bookmark2.caption, Db.Bookmark2.addTime, Db.Bookmark2.modifyTime}, 
			Db.Bookmark2.ari + "=? and " + Db.Bookmark2.kind + "=?",  //$NON-NLS-1$ //$NON-NLS-2$
			new String[] {String.valueOf(ari), String.valueOf(jenis)}, 
			null, null, null
		);
		
		try {
			if (!cursor.moveToNext()) return null;
			
			return Bookmark2.dariCursor(cursor, ari, jenis);
		} finally {
			cursor.close();
		}
	}
	
	public Bookmark2 getBukmakById(long id) {
		Cursor cursor = helper.getReadableDatabase().query(
			Db.TABLE_Bookmark2, 
			null, 
			"_id=?", //$NON-NLS-1$
			new String[] {String.valueOf(id)}, 
			null, null, null
		);
		
		try {
			if (!cursor.moveToNext()) return null;
			
			return Bookmark2.fromCursor(cursor);
		} finally {
			cursor.close();
		}
	}

	public int updateBukmak(Bookmark2 bukmak) {
		return helper.getWritableDatabase().update(Db.TABLE_Bookmark2, bukmak.toContentValues(), "_id=?", new String[] {String.valueOf(bukmak._id)}); //$NON-NLS-1$
	}
	
	public Bookmark2 insertBukmak(int ari, int jenis, String tulisan, Date waktuTambah, Date waktuUbah) {
		Bookmark2 res = new Bookmark2(ari, jenis, tulisan, waktuTambah, waktuUbah);
		SQLiteDatabase db = helper.getWritableDatabase();
		long _id = db.insert(Db.TABLE_Bookmark2, null, res.toContentValues());
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
			long _id;
			
			SQLiteStatement stmt = db.compileStatement("select _id from " + Db.TABLE_Bookmark2 + " where " + Db.Bookmark2.kind + "=? and " + Db.Bookmark2.ari + "=?"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			try {
				stmt.bindLong(1, jenis);
				stmt.bindLong(2, ari);
				_id = stmt.simpleQueryForLong();
			} finally {
				stmt.close();
			}
			
			String[] params = {String.valueOf(_id)};
			db.delete(Db.TABLE_Bookmark2_Label, Db.Bookmark2_Label.bookmark2_id + "=?", params); //$NON-NLS-1$
			db.delete(Db.TABLE_Bookmark2, "_id=?", params); //$NON-NLS-1$
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
			db.delete(Db.TABLE_Bookmark2_Label, Db.Bookmark2_Label.bookmark2_id + "=?", params); //$NON-NLS-1$
			db.delete(Db.TABLE_Bookmark2, "_id=?", params); //$NON-NLS-1$
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

	public Cursor listBookmarks(int jenis, long labelId, String sortColumn, boolean sortAscending) {
        SQLiteDatabase db = helper.getReadableDatabase();

        String sortClause = sortColumn + (Db.Bookmark2.caption.equals(sortColumn)? " collate NOCASE ": "") + (sortAscending? " asc": " desc"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        
        if (labelId == 0) { // no restrictions
            return db.query(Db.TABLE_Bookmark2, null, Db.Bookmark2.kind + "=?", new String[]{String.valueOf(jenis)}, null, null, sortClause); //$NON-NLS-1$
        } else if (labelId == BookmarkListActivity.LABELID_noLabel) { // only without label
            return db.rawQuery("select " + Db.TABLE_Bookmark2 + ".* from " + Db.TABLE_Bookmark2 + " where " + Db.TABLE_Bookmark2 + "." + Db.Bookmark2.kind + "=? and " + Db.TABLE_Bookmark2 + "." + BaseColumns._ID + " not in (select " + Db.Bookmark2_Label.bookmark2_id + " from " + Db.TABLE_Bookmark2_Label + ") order by " + Db.TABLE_Bookmark2 + "." + sortClause, new String[] {String.valueOf(jenis)});  //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$
        } else { // filter by labelId
            return db.rawQuery("select " + Db.TABLE_Bookmark2 + ".* from " + Db.TABLE_Bookmark2 + ", " + Db.TABLE_Bookmark2_Label + " where " + Db.Bookmark2.kind + "=? and " + Db.TABLE_Bookmark2 + "." + BaseColumns._ID + " = " + Db.TABLE_Bookmark2_Label + "." + Db.Bookmark2_Label.bookmark2_id + " and " + Db.TABLE_Bookmark2_Label + "." + Db.Bookmark2_Label.label_id + "=? order by " + Db.TABLE_Bookmark2 + "." + sortClause, new String[] {String.valueOf(jenis), String.valueOf(labelId)});          //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$//$NON-NLS-5$//$NON-NLS-6$//$NON-NLS-7$//$NON-NLS-8$//$NON-NLS-9$//$NON-NLS-10$ //$NON-NLS-11$ //$NON-NLS-12$
        }
	}

	public void importBookmarks(List<Bookmark2> xbukmak, boolean tumpuk, TObjectIntHashMap<Bookmark2> bukmakToRelIdMap, TIntLongHashMap labelRelIdToAbsIdMap, TIntObjectHashMap<TIntList> bukmak2RelIdToLabelRelIdsMap) {
		SQLiteDatabase db = helper.getWritableDatabase();
		db.beginTransaction();
		try {
			TIntLongHashMap bukmakRelIdToAbsIdMap = new TIntLongHashMap();

			{ // tulis bukmak2 baru
				String[] params1 = new String[1];
				String[] params2 = new String[2];
				for (Bookmark2 bukmak: xbukmak) {
					int bukmak2_relId = bukmakToRelIdMap.get(bukmak);
					
					params2[0] = String.valueOf(bukmak.ari);
					params2[1] = String.valueOf(bukmak.kind);
					
					long _id = -1;
					
					boolean ada = false;
					Cursor cursor = db.query(Db.TABLE_Bookmark2, null, Db.Bookmark2.ari + "=? and " + Db.Bookmark2.kind + "=?", params2, null, null, null); //$NON-NLS-1$ //$NON-NLS-2$
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
						db.delete(Db.TABLE_Bookmark2, "_id=?", params1); //$NON-NLS-1$
						db.delete(Db.TABLE_Bookmark2_Label, Db.Bookmark2_Label.bookmark2_id + "=?", params1); //$NON-NLS-1$
					}
					if ((ada && tumpuk) || (!ada)) {
						_id = db.insert(Db.TABLE_Bookmark2, null, bukmak.toContentValues()); /* [2] */
					}
					
					// map it
					bukmakRelIdToAbsIdMap.put(bukmak2_relId, _id);
				}
			}
			
			{ // sekarang pemasangan label
				String where = Db.Bookmark2_Label.bookmark2_id + "=?"; //$NON-NLS-1$
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
						Cursor c = db.rawQuery("select count(*) from " + Db.TABLE_Bookmark2_Label + " where " + where, params); //$NON-NLS-1$ //$NON-NLS-2$
						try {
							c.moveToNext();
							nlabel = c.getInt(0);
						} finally {
							c.close();
						}
						
						if (nlabel>0 && tumpuk) {
							db.delete(Db.TABLE_Bookmark2_Label, where, params);
						}
						if ((nlabel>0 && tumpuk) || (!(nlabel>0))) {
							for (int label_relId: label_relIds.toArray()) {
								long label_id = labelRelIdToAbsIdMap.get(label_relId);
								if (label_id > 0) {
									cv.put(Db.Bookmark2_Label.bookmark2_id, bukmak2_id);
									cv.put(Db.Bookmark2_Label.label_id, label_id);
									db.insert(Db.TABLE_Bookmark2_Label, null, cv);
								} else {
									Log.w(TAG, "label_id ngaco!: " + label_id); //$NON-NLS-1$
								}
							}
						}
					} else {
						Log.w(TAG, "bukmak2_id ngaco!: " + bukmak2_id); //$NON-NLS-1$
					}
				}
			}
	
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

	public Cursor listAllBookmarks() {
		return helper.getReadableDatabase().query(Db.TABLE_Bookmark2, null, null, null, null, null, null);
	}

	private SQLiteStatement stmt_countAtribut = null;
	public int countAtribut(int ari_kitabpasal) {
		int ariMin = ari_kitabpasal & 0x00ffff00;
		int ariMax = ari_kitabpasal | 0x000000ff;
		
		if (stmt_countAtribut == null) {
			stmt_countAtribut = helper.getReadableDatabase().compileStatement("select count(*) from " + Db.TABLE_Bookmark2 + " where " + Db.Bookmark2.ari + ">=? and " + Db.Bookmark2.ari + "<?");//$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
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
		Cursor cursor = helper.getReadableDatabase().rawQuery("select * from " + Db.TABLE_Bookmark2 + " where " + Db.Bookmark2.ari + ">=? and " + Db.Bookmark2.ari + "<?", sql_getCatatan_params); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		try {
			int kolom_jenis = cursor.getColumnIndexOrThrow(Db.Bookmark2.kind);
			int kolom_ari = cursor.getColumnIndexOrThrow(Db.Bookmark2.ari);
			int kolom_tulisan = cursor.getColumnIndexOrThrow(Db.Bookmark2.caption);
			while (cursor.moveToNext()) {
				int ari = cursor.getInt(kolom_ari);
				int jenis = cursor.getInt(kolom_jenis);
				
				int ofsetMap = Ari.toVerse(ari) - 1; // dari basis1 ke basis 0
				if (ofsetMap >= map_0.length) {
					Log.e(TAG, "ofsetMap kebanyakan " + ofsetMap + " terjadi pada ari 0x" + Integer.toHexString(ari)); //$NON-NLS-1$ //$NON-NLS-2$
				} else {
					if (jenis == Db.Bookmark2.kind_bookmark) {
						map_0[ofsetMap] |= 0x1;
					} else if (jenis == Db.Bookmark2.kind_note) {
						map_0[ofsetMap] |= 0x2;
					} else if (jenis == Db.Bookmark2.kind_highlight) {
						map_0[ofsetMap] |= 0x4;
						
						String tulisan = cursor.getString(kolom_tulisan);
						int warnaRgb = U.decodeHighlight(tulisan);
						
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
		
		String[] params = {null /* buat ari */, String.valueOf(Db.Bookmark2.kind_highlight)};
		
		db.beginTransaction();
		try {
			// setiap ayat yang diminta
			for (int i = 0; i < ayatTerpilih.size(); i++) {
				int ayat_1 = ayatTerpilih.get(i);
				int ari = Ari.encodeWithBc(ariKp, ayat_1);
				params[0] = String.valueOf(ari);
				
				Cursor c = db.query(Db.TABLE_Bookmark2, null, Db.Bookmark2.ari + "=? and " + Db.Bookmark2.kind + "=?", params, null, null, null); //$NON-NLS-1$ //$NON-NLS-2$
				try {
					if (c.moveToNext()) { // cek dulu ada ato ga
						// sudah ada!
						Bookmark2 bukmak = Bookmark2.fromCursor(c);
						bukmak.modifyTime = new Date();
						if (warnaRgb != -1) {
							bukmak.caption = U.encodeHighlight(warnaRgb);
							db.update(Db.TABLE_Bookmark2, bukmak.toContentValues(), "_id=?", new String[] {String.valueOf(bukmak._id)}); //$NON-NLS-1$
						} else {
							// delete
							db.delete(Db.TABLE_Bookmark2, "_id=?", new String[] {String.valueOf(bukmak._id)}); //$NON-NLS-1$
						}
					} else {
						// belum ada!
						if (warnaRgb == -1) {
							// ga usa ngapa2in, dari belum ada jadi tetep ga ada
						} else {
							Date kini = new Date();
							Bookmark2 bukmak = new Bookmark2(ari, Db.Bookmark2.kind_highlight, U.encodeHighlight(warnaRgb), kini, kini); 
							db.insert(Db.TABLE_Bookmark2, null, bukmak.toContentValues());
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
		Cursor c = helper.getReadableDatabase().query(Db.TABLE_Bookmark2, null, Db.Bookmark2.ari + "=? and " + Db.Bookmark2.kind + "=?", new String[] {String.valueOf(ari), String.valueOf(Db.Bookmark2.kind_highlight)}, null, null, null); //$NON-NLS-1$ //$NON-NLS-2$
		try {
			if (c.moveToNext()) {
				// sudah ada!
				Bookmark2 bukmak = Bookmark2.fromCursor(c);
				return U.decodeHighlight(bukmak.caption);
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
		Cursor c = helper.getReadableDatabase().query(Db.TABLE_Bookmark2, null, Db.Bookmark2.ari + ">? and " + Db.Bookmark2.ari + "<=? and " + Db.Bookmark2.kind + "=?", new String[] {String.valueOf(ariMin), String.valueOf(ariMax), String.valueOf(Db.Bookmark2.kind_highlight)}, null, null, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		try {
			int ari_col = c.getColumnIndexOrThrow(Db.Bookmark2.ari);
			int tulisan_col = c.getColumnIndexOrThrow(Db.Bookmark2.caption);
			
			// masukin aja ke array dulu
			while (c.moveToNext()) { 
				int ari = c.getInt(ari_col);
				int index = ari & 0xff;
				int warna = U.decodeHighlight(c.getString(tulisan_col));
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
			db.delete(Db.TABLE_Devotion, Db.Devotion.name + "=? and " + Db.Devotion.date + "=?", new String[] {artikel.getNama(), artikel.getTgl()}); //$NON-NLS-1$ //$NON-NLS-2$

			ContentValues values = new ContentValues();
			values.put(Db.Devotion.name, artikel.getNama());
			values.put(Db.Devotion.date, artikel.getTgl());
			values.put(Db.Devotion.readyToUse, artikel.getSiapPakai()? 1: 0);
			
			if (artikel.getSiapPakai()) {
				values.put(Db.Devotion.title, artikel.getJudul().toString());
				values.put(Db.Devotion.body, artikel.getIsiHtml());
				values.put(Db.Devotion.header, artikel.getHeaderHtml());
			} else {
				values.put(Db.Devotion.title, (String)null);
				values.put(Db.Devotion.body, (String)null);
				values.put(Db.Devotion.header, (String)null);
			}
			
			values.put(Db.Devotion.touchTime, Sqlitil.nowDateTime());
			
			db.insert(Db.TABLE_Devotion, null, values);
			
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
		return db.delete(Db.TABLE_Devotion, Db.Devotion.touchTime + "<?", new String[] {String.valueOf(Sqlitil.toInt(date))}); //$NON-NLS-1$
	}
	
	/**
	 * Coba ambil artikel dari db lokal. Artikel ga siap pakai pun akan direturn.
	 */
	public IArtikel cobaAmbilRenungan(String nama, String tgl) {
		Cursor c = helper.getReadableDatabase().query(Db.TABLE_Devotion, null, Db.Devotion.name + "=? and " + Db.Devotion.date + "=?", new String[] { nama, tgl }, null, null, null); //$NON-NLS-1$ //$NON-NLS-2$
		try {
			if (c.moveToNext()) {
				IArtikel res = null;
				if (nama.equals("rh")) { //$NON-NLS-1$
					res = new ArtikelRenunganHarian(
						tgl,
						c.getString(c.getColumnIndexOrThrow(Db.Devotion.title)),
						c.getString(c.getColumnIndexOrThrow(Db.Devotion.header)),
						c.getString(c.getColumnIndexOrThrow(Db.Devotion.body)),
						c.getInt(c.getColumnIndexOrThrow(Db.Devotion.readyToUse)) > 0
					);
				} else if (nama.equals("sh")) { //$NON-NLS-1$
					res = new ArtikelSantapanHarian(
						tgl,
						c.getString(c.getColumnIndexOrThrow(Db.Devotion.title)),
						c.getString(c.getColumnIndexOrThrow(Db.Devotion.header)),
						c.getString(c.getColumnIndexOrThrow(Db.Devotion.body)),
						c.getInt(c.getColumnIndexOrThrow(Db.Devotion.readyToUse)) > 0
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

	public List<MVersionYes> getAllVersions() {
		List<MVersionYes> res = new ArrayList<MVersionYes>();
		Cursor cursor = helper.getReadableDatabase().query(Db.TABLE_Version, null, null, null, null, null, Db.Version.ordering + " asc"); //$NON-NLS-1$
		try {
			int col_aktif = cursor.getColumnIndexOrThrow(Db.Version.active);
			int col_shortName = cursor.getColumnIndexOrThrow(Db.Version.shortName);
			int col_judul = cursor.getColumnIndexOrThrow(Db.Version.title);
			int col_keterangan = cursor.getColumnIndexOrThrow(Db.Version.description);
			int col_namafile = cursor.getColumnIndexOrThrow(Db.Version.filename);
			int col_namafile_pdbasal = cursor.getColumnIndexOrThrow(Db.Version.filename_originalpdb);
			int col_urutan = cursor.getColumnIndexOrThrow(Db.Version.ordering);
			
			while (cursor.moveToNext()) {
				MVersionYes yes = new MVersionYes();
				yes.cache_active = cursor.getInt(col_aktif) != 0;
				yes.type = Db.Version.kind_yes;
				yes.description = cursor.getString(col_keterangan);
				yes.shortName = cursor.getString(col_shortName);
				yes.longName = cursor.getString(col_judul);
				yes.filename = cursor.getString(col_namafile);
				yes.originalPdbFilename = cursor.getString(col_namafile_pdbasal);
				yes.ordering = cursor.getInt(col_urutan);
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
		cv.put(Db.Version.active, aktif? 1: 0);
		db.update(Db.TABLE_Version, cv, Db.Version.kind + "=? and " + Db.Version.filename + "=?", new String[] {String.valueOf(Db.Version.kind_yes), namafile}); //$NON-NLS-1$ //$NON-NLS-2$
	}

	public int getUrutanTerbesarEdisiYes() {
		SQLiteDatabase db = helper.getReadableDatabase();
		SQLiteStatement stmt = db.compileStatement("select max(" + Db.Version.ordering + ") from " + Db.TABLE_Version);  //$NON-NLS-1$//$NON-NLS-2$
		try {
			return (int) stmt.simpleQueryForLong();
		} finally {
			stmt.close();
		}
	}

	public void tambahEdisiYesDenganAktif(MVersionYes edisi, boolean aktif) {
		SQLiteDatabase db = helper.getWritableDatabase();
		ContentValues cv = new ContentValues();
		cv.put(Db.Version.active, aktif);
		cv.put(Db.Version.kind, Db.Version.kind_yes);
		cv.put(Db.Version.shortName, edisi.shortName);
		cv.put(Db.Version.title, edisi.longName);
		cv.put(Db.Version.description, edisi.description);
		cv.put(Db.Version.filename, edisi.filename);
		cv.put(Db.Version.filename_originalpdb, edisi.originalPdbFilename);
		cv.put(Db.Version.ordering, edisi.ordering);
		Log.d(TAG, "tambah edisi yes: " + cv.toString()); //$NON-NLS-1$
		db.insert(Db.TABLE_Version, null, cv);
	}

	public boolean adakahEdisiYesDenganNamafile(String namafile) {
		SQLiteDatabase db = helper.getReadableDatabase();
		SQLiteStatement stmt = db.compileStatement("select count(*) from " + Db.TABLE_Version + " where " + Db.Version.kind + "=? and " + Db.Version.filename + "=?");    //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$
		try {
			stmt.clearBindings();
			stmt.bindLong(1, Db.Version.kind_yes);
			stmt.bindString(2, namafile);
			return stmt.simpleQueryForLong() > 0;
		} finally {
			stmt.close();
		}
	}

	public void hapusEdisiYes(MVersionYes edisi) {
		SQLiteDatabase db = helper.getWritableDatabase();
		db.delete(Db.TABLE_Version, Db.Version.filename + "=?", new String[] {edisi.filename}); //$NON-NLS-1$
	}
	
	public List<Label> getAllLabels() {
		List<Label> res = new ArrayList<Label>();
		Cursor cursor = helper.getReadableDatabase().query(Db.TABLE_Label, null, null, null, null, null, Db.Label.ordering + " asc"); //$NON-NLS-1$
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
	public List<Label> getLabels(long bukmak2_id) {
		List<Label> res = null;
		Cursor cursor = helper.getReadableDatabase().rawQuery("select " + Db.TABLE_Label + ".* from " + Db.TABLE_Label + ", " + Db.TABLE_Bookmark2_Label + " where " + Db.TABLE_Bookmark2_Label + "." + Db.Bookmark2_Label.label_id + " = " + Db.TABLE_Label + "." + BaseColumns._ID + " and " + Db.TABLE_Bookmark2_Label + "." + Db.Bookmark2_Label.bookmark2_id + " = ?  order by " + Db.TABLE_Label + "." + Db.Label.ordering + " asc", new String[] {String.valueOf(bukmak2_id)});       //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$//$NON-NLS-5$//$NON-NLS-6$//$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$ //$NON-NLS-12$
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
	public TLongList getLabelIds(long bukmak2_id) {
		TLongList res = null;
		Cursor cursor = helper.getReadableDatabase().rawQuery("select " + Db.TABLE_Bookmark2_Label + "." + Db.Bookmark2_Label.label_id + " from " + Db.TABLE_Bookmark2_Label + " where " + Db.TABLE_Bookmark2_Label + "." + Db.Bookmark2_Label.bookmark2_id + "=?", new String[] {String.valueOf(bukmak2_id)});       //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$//$NON-NLS-5$//$NON-NLS-6$
		try {
			int col_label_id = cursor.getColumnIndexOrThrow(Db.Bookmark2_Label.label_id);
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
		SQLiteStatement stmt = db.compileStatement("select max(" + Db.Label.ordering + ") from " + Db.TABLE_Label);  //$NON-NLS-1$//$NON-NLS-2$
		try {
			return (int) stmt.simpleQueryForLong();
		} finally {
			stmt.close();
		}
	}

	public Label tambahLabel(String judul, String warnaLatar) {
		Label res = new Label(-1, judul, getUrutanTerbesarLabel() + 1, warnaLatar); 
		SQLiteDatabase db = helper.getWritableDatabase();
		long _id = db.insert(Db.TABLE_Label, null, res.toContentValues());
		if (_id == -1) {
			return null;
		} else {
			res._id = _id;
			return res;
		}
	}

	public void updateLabels(Bookmark2 bukmak, Set<Label> labels) {
		SQLiteDatabase db = helper.getWritableDatabase();
		db.beginTransaction();
		try {
			// hapus semua
			db.delete(Db.TABLE_Bookmark2_Label, Db.Bookmark2_Label.bookmark2_id + "=?", new String[] {String.valueOf(bukmak._id)}); //$NON-NLS-1$
			
			// tambah semua
			ContentValues cv = new ContentValues();
			for (Label label: labels) {
				cv.put(Db.Bookmark2_Label.bookmark2_id, bukmak._id);
				cv.put(Db.Bookmark2_Label.label_id, label._id);
				db.insert(Db.TABLE_Bookmark2_Label, null, cv);
			}
			
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

    public Label getLabelById(long labelId) {
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor cursor = db.query(Db.TABLE_Label, null, BaseColumns._ID + "=?", new String[] {String.valueOf(labelId)}, null, null, null); //$NON-NLS-1$
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
			db.delete(Db.TABLE_Bookmark2_Label, Db.Bookmark2_Label.label_id + "=?", params); //$NON-NLS-1$
			db.delete(Db.TABLE_Label, "_id=?", params); //$NON-NLS-1$
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

	public void updateLabel(Label label) {
		SQLiteDatabase db = helper.getWritableDatabase();
		ContentValues cv = label.toContentValues();
		db.update(Db.TABLE_Label, cv, "_id=?", new String[] {String.valueOf(label._id)}); //$NON-NLS-1$
	}

	public int countBukmakDenganLabel(Label label) {
		SQLiteDatabase db = helper.getReadableDatabase();
		SQLiteStatement stmt = db.compileStatement("select count(*) from " + Db.TABLE_Bookmark2_Label + " where " + Db.Bookmark2_Label.label_id + "=?"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		try {
			stmt.bindLong(1, label._id);
			return (int) stmt.simpleQueryForLong();
		} finally {
			stmt.close();
		}
	}

	public int deleteDevotionsWithLessThanInTitle() {
		SQLiteDatabase db = helper.getWritableDatabase();
		return db.delete(Db.TABLE_Devotion, Db.Devotion.title + " like '%<%'", null);
	}

	public void reorderLabels(Label from, Label to) {
		// original order: A101 B[102] C103 D[104] E105
		
		// case: move up from=104 to=102:
		//   increase ordering for (to <= ordering < from)
		//   A101 B[103] C104 D[104] E105
		//   replace ordering of 'from' to 'to'
		//   A101 B[103] C104 D[102] E105
				
		// case: move down from=102 to=104:
		//   decrease ordering for (from < ordering <= to)
		//   A101 B[102] C102 D[103] E105
		//   replace ordering of 'from' to 'to'
		//   A101 B[104] C102 D[103] E105
		
		if (D.EBUG) {
			Log.d(TAG, "@@reorderLabels from _id=" + from._id + " ordering=" + from.ordering + " to _id=" + to._id + " ordering=" + to.ordering);
		}
		
		SQLiteDatabase db = helper.getWritableDatabase();
		db.beginTransaction();
		try {
			if (from.ordering > to.ordering) { // move up
				db.execSQL("update " + Db.TABLE_Label + " set " + Db.Label.ordering + "=(" + Db.Label.ordering + "+1) where ?<=" + Db.Label.ordering + " and " + Db.Label.ordering + "<?", new Object[] {to.ordering, from.ordering});
				db.execSQL("update " + Db.TABLE_Label + " set " + Db.Label.ordering + "=? where _id=?", new Object[] {to.ordering, from._id});
			} else if (from.ordering < to.ordering) { // move down
				db.execSQL("update " + Db.TABLE_Label + " set " + Db.Label.ordering + "=(" + Db.Label.ordering + "-1) where ?<" + Db.Label.ordering + " and " + Db.Label.ordering + "<=?", new Object[] {from.ordering, to.ordering});
				db.execSQL("update " + Db.TABLE_Label + " set " + Db.Label.ordering + "=? where _id=?", new Object[] {to.ordering, from._id});
			}
			
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}
}
