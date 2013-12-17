package yuku.alkitab.base.storage;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
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
import yuku.afw.D;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.BookmarkListActivity;
import yuku.alkitab.base.ac.VersionsActivity.MVersionYes;
import yuku.alkitab.base.devotion.ArticleMorningEveningEnglish;
import yuku.alkitab.base.devotion.ArticleRenunganHarian;
import yuku.alkitab.base.devotion.ArticleSantapanHarian;
import yuku.alkitab.base.devotion.DevotionArticle;
import yuku.alkitab.util.Ari;
import yuku.alkitab.model.Bookmark2;
import yuku.alkitab.model.Label;
import yuku.alkitab.model.ProgressMark;
import yuku.alkitab.model.ProgressMarkHistory;
import yuku.alkitab.util.IntArrayList;
import yuku.alkitab.base.model.ReadingPlan;
import yuku.alkitab.base.util.Sqlitil;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

public class InternalDb {
	public static final String TAG = InternalDb.class.getSimpleName();

	private final InternalDbHelper helper;

	public InternalDb(InternalDbHelper helper) {
		this.helper = helper;
	}

	/**
	 * _id is not stored
	 * @param bookmark2
	 */
	private static ContentValues bookmark2ToContentValues(final Bookmark2 bookmark2) {
		ContentValues res = new ContentValues();

		res.put(Db.Bookmark2.ari, bookmark2.ari);
		res.put(Db.Bookmark2.kind, bookmark2.kind);
		res.put(Db.Bookmark2.caption, bookmark2.caption);
		res.put(Db.Bookmark2.addTime, Sqlitil.toInt(bookmark2.addTime));
		res.put(Db.Bookmark2.modifyTime, Sqlitil.toInt(bookmark2.modifyTime));

		return res;
	}

	public static Bookmark2 bookmark2FromCursor(Cursor cursor) {
		int ari = cursor.getInt(cursor.getColumnIndexOrThrow(Db.Bookmark2.ari));
		int jenis = cursor.getInt(cursor.getColumnIndexOrThrow(Db.Bookmark2.kind));

		return bookmark2FromCursor(cursor, ari, jenis);
	}

	private static Bookmark2 bookmark2FromCursor(Cursor cursor, int ari, int kind) {
		long _id = cursor.getLong(cursor.getColumnIndexOrThrow(BaseColumns._ID));
		String caption = cursor.getString(cursor.getColumnIndexOrThrow(Db.Bookmark2.caption));
		Date addTime = Sqlitil.toDate(cursor.getInt(cursor.getColumnIndexOrThrow(Db.Bookmark2.addTime)));
		Date modifyTime = Sqlitil.toDate(cursor.getInt(cursor.getColumnIndexOrThrow(Db.Bookmark2.modifyTime)));

		Bookmark2 res = new Bookmark2(ari, kind, caption, addTime, modifyTime);
		res._id = _id;
		return res;
	}


	public Bookmark2 getBookmarkByAri(int ari, int kind) {
		Cursor cursor = helper.getReadableDatabase().query(
		Db.TABLE_Bookmark2,
		new String[] {BaseColumns._ID, Db.Bookmark2.caption, Db.Bookmark2.addTime, Db.Bookmark2.modifyTime},
		Db.Bookmark2.ari + "=? and " + Db.Bookmark2.kind + "=?",  //$NON-NLS-1$ //$NON-NLS-2$
		new String[] {String.valueOf(ari), String.valueOf(kind)},
		null, null, null
		);

		try {
			if (!cursor.moveToNext()) return null;
			return bookmark2FromCursor(cursor, ari, kind);
		} finally {
			cursor.close();
		}
	}

	public Bookmark2 getBookmarkById(long id) {
		Cursor cursor = helper.getReadableDatabase().query(
		Db.TABLE_Bookmark2,
		null,
		"_id=?", //$NON-NLS-1$
		new String[] {String.valueOf(id)},
		null, null, null
		);

		try {
			if (!cursor.moveToNext()) return null;
			return bookmark2FromCursor(cursor);
		} finally {
			cursor.close();
		}
	}

	public int updateBookmark(Bookmark2 bookmark) {
		return helper.getWritableDatabase().update(Db.TABLE_Bookmark2, bookmark2ToContentValues(bookmark), "_id=?", new String[] {String.valueOf(bookmark._id)}); //$NON-NLS-1$
	}

	public Bookmark2 insertBookmark(int ari, int kind, String caption, Date addTime, Date modifyTime) {
		Bookmark2 res = new Bookmark2(ari, kind, caption, addTime, modifyTime);
		SQLiteDatabase db = helper.getWritableDatabase();
		long _id = db.insert(Db.TABLE_Bookmark2, null, bookmark2ToContentValues(res));
		if (_id == -1) {
			return null;
		} else {
			res._id = _id;
			return res;
		}
	}

	public void deleteBookmarkByAri(int ari, int kind) {
		SQLiteDatabase db = helper.getWritableDatabase();
		db.beginTransaction();
		try {
			long _id;

			SQLiteStatement stmt = db.compileStatement("select _id from " + Db.TABLE_Bookmark2 + " where " + Db.Bookmark2.kind + "=? and " + Db.Bookmark2.ari + "=?"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			try {
				stmt.bindLong(1, kind);
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

	public void deleteBookmarkById(long id) {
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

	public Cursor listBookmarks(int kind, long labelId, String sortColumn, boolean sortAscending) {
		SQLiteDatabase db = helper.getReadableDatabase();

		String sortClause = sortColumn + (Db.Bookmark2.caption.equals(sortColumn)? " collate NOCASE ": "") + (sortAscending? " asc": " desc"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

		if (labelId == 0) { // no restrictions
			return db.query(Db.TABLE_Bookmark2, null, Db.Bookmark2.kind + "=?", new String[] {String.valueOf(kind)}, null, null, sortClause); //$NON-NLS-1$
		} else if (labelId == BookmarkListActivity.LABELID_noLabel) { // only without label
			return db.rawQuery("select " + Db.TABLE_Bookmark2 + ".* from " + Db.TABLE_Bookmark2 + " where " + Db.TABLE_Bookmark2 + "." + Db.Bookmark2.kind + "=? and " + Db.TABLE_Bookmark2 + "." + BaseColumns._ID + " not in (select " + Db.Bookmark2_Label.bookmark2_id + " from " + Db.TABLE_Bookmark2_Label + ") order by " + Db.TABLE_Bookmark2 + "." + sortClause, new String[] {String.valueOf(kind)});  //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$
		} else { // filter by labelId
			return db.rawQuery("select " + Db.TABLE_Bookmark2 + ".* from " + Db.TABLE_Bookmark2 + ", " + Db.TABLE_Bookmark2_Label + " where " + Db.Bookmark2.kind + "=? and " + Db.TABLE_Bookmark2 + "." + BaseColumns._ID + " = " + Db.TABLE_Bookmark2_Label + "." + Db.Bookmark2_Label.bookmark2_id + " and " + Db.TABLE_Bookmark2_Label + "." + Db.Bookmark2_Label.label_id + "=? order by " + Db.TABLE_Bookmark2 + "." + sortClause, new String[] {String.valueOf(kind), String.valueOf(labelId)});          //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$//$NON-NLS-5$//$NON-NLS-6$//$NON-NLS-7$//$NON-NLS-8$//$NON-NLS-9$//$NON-NLS-10$ //$NON-NLS-11$ //$NON-NLS-12$
		}
	}

	public void importBookmarks(List<Bookmark2> bookmarks, boolean overwrite, TObjectIntHashMap<Bookmark2> bookmarkToRelIdMap, TIntLongHashMap labelRelIdToAbsIdMap, TIntObjectHashMap<TIntList> bookmarkRelIdToLabelRelIdsMap) {
		SQLiteDatabase db = helper.getWritableDatabase();
		db.beginTransaction();
		try {
			TIntLongHashMap bookmarkRelIdToAbsIdMap = new TIntLongHashMap();

			{ // write new bookmarks
				String[] params1 = new String[1];
				String[] params2 = new String[2];
				for (Bookmark2 bookmark : bookmarks) {
					int bookmark_relId = bookmarkToRelIdMap.get(bookmark);

					params2[0] = String.valueOf(bookmark.ari);
					params2[1] = String.valueOf(bookmark.kind);

					long _id = -1;

					boolean ada = false;
					Cursor cursor = db.query(Db.TABLE_Bookmark2, null, Db.Bookmark2.ari + "=? and " + Db.Bookmark2.kind + "=?", params2, null, null, null); //$NON-NLS-1$ //$NON-NLS-2$
					if (cursor.moveToNext()) {
						ada = true;
						_id = cursor.getLong(cursor.getColumnIndexOrThrow(BaseColumns._ID)); /* [1] */
					}
					cursor.close();

					// ----------------------------------------- get _id from
					//  exists  overwrite:     delete insert     [2]
					//  exists !overwrite: (nop)                 [1]
					// !exists  overwrite:            insert     [2]
					// !exists !overwrite:            insert     [2]

					if (ada && overwrite) {
						params1[0] = String.valueOf(_id);
						db.delete(Db.TABLE_Bookmark2, "_id=?", params1); //$NON-NLS-1$
						db.delete(Db.TABLE_Bookmark2_Label, Db.Bookmark2_Label.bookmark2_id + "=?", params1); //$NON-NLS-1$
					}
					if ((ada && overwrite) || (!ada)) {
						_id = db.insert(Db.TABLE_Bookmark2, null, bookmark2ToContentValues(bookmark)); /* [2] */
					}

					// map it
					bookmarkRelIdToAbsIdMap.put(bookmark_relId, _id);
				}
			}

			{ // now is label assignments
				String where = Db.Bookmark2_Label.bookmark2_id + "=?"; //$NON-NLS-1$
				String[] params = {null};
				ContentValues cv = new ContentValues();

				// nlabel>0  overwrite:  delete insert
				// nlabel>0 !overwrite: (nop)
				// nlabel=0  overwrite:         insert
				// nlabel=0 !overwrite:         insert

				for (int bookmark_relId : bookmarkRelIdToLabelRelIdsMap.keys()) {
					TIntList label_relIds = bookmarkRelIdToLabelRelIdsMap.get(bookmark_relId);

					long bookmark_id = bookmarkRelIdToAbsIdMap.get(bookmark_relId);

					if (bookmark_id > 0) {
						params[0] = String.valueOf(bookmark_id);

						// check how many labels for this bookmark_id
						int nlabel = 0;
						Cursor c = db.rawQuery("select count(*) from " + Db.TABLE_Bookmark2_Label + " where " + where, params); //$NON-NLS-1$ //$NON-NLS-2$
						try {
							c.moveToNext();
							nlabel = c.getInt(0);
						} finally {
							c.close();
						}

						if (nlabel > 0 && overwrite) {
							db.delete(Db.TABLE_Bookmark2_Label, where, params);
						}
						if ((nlabel > 0 && overwrite) || (!(nlabel > 0))) {
							for (int label_relId : label_relIds.toArray()) {
								long label_id = labelRelIdToAbsIdMap.get(label_relId);
								if (label_id > 0) {
									cv.put(Db.Bookmark2_Label.bookmark2_id, bookmark_id);
									cv.put(Db.Bookmark2_Label.label_id, label_id);
									db.insert(Db.TABLE_Bookmark2_Label, null, cv);
								} else {
									Log.w(TAG, "label_id ngaco!: " + label_id); //$NON-NLS-1$
								}
							}
						}
					} else {
						Log.w(TAG, "wrong bookmark_id!: " + bookmark_id); //$NON-NLS-1$
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

	public int countAllBookmarks() {
		return (int) DatabaseUtils.queryNumEntries(helper.getReadableDatabase(), Db.TABLE_Bookmark2);
	}

	private SQLiteStatement stmt_countAttribute = null;

	public int countAttributes(int ari_bookchapter) {
		int ariMin = ari_bookchapter & 0x00ffff00;
		int ariMax = ari_bookchapter | 0x000000ff;

		if (stmt_countAttribute == null) {
			stmt_countAttribute = helper.getReadableDatabase().compileStatement("select count(*) from " + Db.TABLE_Bookmark2 + " where " + Db.Bookmark2.ari + ">=? and " + Db.Bookmark2.ari + "<?");//$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		}

		stmt_countAttribute.clearBindings();
		stmt_countAttribute.bindLong(1, ariMin);
		stmt_countAttribute.bindLong(2, ariMax);

		return (int) stmt_countAttribute.simpleQueryForLong();
	}

	private String[] sql_putAttributes_params = new String[2];

	/**
	 * @param map_0 the verses 0-based.
	 * @return null if no highlights, or colors when there are highlights according to offsets of map_0.
	 */
	public int[] putAttributes(int ari_bookchapter, int[] map_0) {
		int ariMin = ari_bookchapter & 0x00ffff00;
		int ariMax = ari_bookchapter | 0x000000ff;
		int[] res = null;

		sql_putAttributes_params[0] = String.valueOf(ariMin);
		sql_putAttributes_params[1] = String.valueOf(ariMax);
		Cursor cursor = helper.getReadableDatabase().rawQuery("select * from " + Db.TABLE_Bookmark2 + " where " + Db.Bookmark2.ari + ">=? and " + Db.Bookmark2.ari + "<?", sql_putAttributes_params); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		try {
			int col_kind = cursor.getColumnIndexOrThrow(Db.Bookmark2.kind);
			int col_ari = cursor.getColumnIndexOrThrow(Db.Bookmark2.ari);
			int col_caption = cursor.getColumnIndexOrThrow(Db.Bookmark2.caption);
			while (cursor.moveToNext()) {
				int ari = cursor.getInt(col_ari);
				int kind = cursor.getInt(col_kind);

				int mapOffset = Ari.toVerse(ari) - 1;
				if (mapOffset >= map_0.length) {
					Log.e(TAG, "ofsetMap kebanyakan " + mapOffset + " terjadi pada ari 0x" + Integer.toHexString(ari)); //$NON-NLS-1$ //$NON-NLS-2$
				} else {
					if (kind == Db.Bookmark2.kind_bookmark) {
						map_0[mapOffset] |= 0x1;
					} else if (kind == Db.Bookmark2.kind_note) {
						map_0[mapOffset] |= 0x2;
					} else if (kind == Db.Bookmark2.kind_highlight) {
						map_0[mapOffset] |= 0x4;

						String caption = cursor.getString(col_caption);
						int colorRgb = U.decodeHighlight(caption);

						if (res == null) res = new int[map_0.length];
						res[mapOffset] = colorRgb;
					}
				}
			}
		} finally {
			cursor.close();
		}
		return res;
	}

	public void updateOrInsertHighlights(int ari_bookchapter, IntArrayList selectedVerses_1, int colorRgb) {
		SQLiteDatabase db = helper.getWritableDatabase();

		String[] params = {null /* buat ari */, String.valueOf(Db.Bookmark2.kind_highlight)};

		db.beginTransaction();
		try {
			// every requested verses
			for (int i = 0; i < selectedVerses_1.size(); i++) {
				int verse_1 = selectedVerses_1.get(i);
				int ari = Ari.encodeWithBc(ari_bookchapter, verse_1);
				params[0] = String.valueOf(ari);

				Cursor c = db.query(Db.TABLE_Bookmark2, null, Db.Bookmark2.ari + "=? and " + Db.Bookmark2.kind + "=?", params, null, null, null); //$NON-NLS-1$ //$NON-NLS-2$
				try {
					if (c.moveToNext()) { // check if bookmark exists
						Bookmark2 bookmark = bookmark2FromCursor(c);
						bookmark.modifyTime = new Date();
						if (colorRgb != -1) {
							bookmark.caption = U.encodeHighlight(colorRgb);
							db.update(Db.TABLE_Bookmark2, bookmark2ToContentValues(bookmark), "_id=?", new String[] {String.valueOf(bookmark._id)}); //$NON-NLS-1$
						} else {
							// delete
							db.delete(Db.TABLE_Bookmark2, "_id=?", new String[] {String.valueOf(bookmark._id)}); //$NON-NLS-1$
						}
					} else {
						if (colorRgb == -1) {
							// no need to do, from no color to no color
						} else {
							Date now = new Date();
							Bookmark2 bookmark = new Bookmark2(ari, Db.Bookmark2.kind_highlight, U.encodeHighlight(colorRgb), now, now);
							db.insert(Db.TABLE_Bookmark2, null, bookmark2ToContentValues(bookmark));
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

	public int getHighlightColorRgb(int ari_bookchapter, IntArrayList selectedVerses_1) {
		int ariMin = ari_bookchapter;
		int ariMax = ari_bookchapter | 0xff;
		int[] colors = new int[256];
		int res = -2;

		for (int i = 0; i < colors.length; i++) colors[i] = -1;

		// check if exists
		Cursor c = helper.getReadableDatabase().query(Db.TABLE_Bookmark2, null, Db.Bookmark2.ari + ">? and " + Db.Bookmark2.ari + "<=? and " + Db.Bookmark2.kind + "=?", new String[] {String.valueOf(ariMin), String.valueOf(ariMax), String.valueOf(Db.Bookmark2.kind_highlight)}, null, null, null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		try {
			int ari_col = c.getColumnIndexOrThrow(Db.Bookmark2.ari);
			int tulisan_col = c.getColumnIndexOrThrow(Db.Bookmark2.caption);

			// put to array first
			while (c.moveToNext()) {
				int ari = c.getInt(ari_col);
				int index = ari & 0xff;
				int color = U.decodeHighlight(c.getString(tulisan_col));
				colors[index] = color;
			}

			// determine default color. If all has color x, then it's x. If one of them is not x, then it's -1.
			for (int i = 0; i < selectedVerses_1.size(); i++) {
				int verse_1 = selectedVerses_1.get(i);
				int color = colors[verse_1];
				if (res == -2) {
					res = color;
				} else if (color != res) {
					return -1;
				}
			}

			if (res == -2) return -1;
			return res;
		} finally {
			c.close();
		}
	}

	public void storeArticleToDevotions(DevotionArticle article) {
		SQLiteDatabase db = helper.getWritableDatabase();

		db.beginTransaction();
		try {
			// first delete the existing
			db.delete(Db.TABLE_Devotion, Db.Devotion.name + "=? and " + Db.Devotion.date + "=?", new String[] {article.getName(), article.getDate()}); //$NON-NLS-1$ //$NON-NLS-2$

			ContentValues values = new ContentValues();
			values.put(Db.Devotion.name, article.getName());
			values.put(Db.Devotion.date, article.getDate());
			values.put(Db.Devotion.readyToUse, article.getReadyToUse()? 1: 0);

			if (article.getReadyToUse()) {
				String[] headerTitleBody = article.getHeaderTitleBody();
				values.put(Db.Devotion.header, headerTitleBody[0]);
				values.put(Db.Devotion.title, headerTitleBody[1]);
				values.put(Db.Devotion.body, headerTitleBody[2]);
			} else {
				values.putNull(Db.Devotion.header);
				values.putNull(Db.Devotion.title);
				values.putNull(Db.Devotion.body);
			}

			values.put(Db.Devotion.touchTime, Sqlitil.nowDateTime());

			db.insert(Db.TABLE_Devotion, null, values);

			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

	public int deleteDevotionsWithTouchTimeBefore(Date date) {
		SQLiteDatabase db = helper.getWritableDatabase();
		return db.delete(Db.TABLE_Devotion, Db.Devotion.touchTime + "<?", new String[] {String.valueOf(Sqlitil.toInt(date))}); //$NON-NLS-1$
	}

	/**
	 * Try to get article from local db. Non ready-to-use article will be returned too.
	 */
	public DevotionArticle tryGetDevotion(String name, String date) {
		Cursor c = helper.getReadableDatabase().query(Db.TABLE_Devotion, null, Db.Devotion.name + "=? and " + Db.Devotion.date + "=?", new String[] {name, date}, null, null, null); //$NON-NLS-1$ //$NON-NLS-2$
		try {
			int col_title = c.getColumnIndexOrThrow(Db.Devotion.title);
			int col_header = c.getColumnIndexOrThrow(Db.Devotion.header);
			int col_body = c.getColumnIndexOrThrow(Db.Devotion.body);
			int col_readyToUse = c.getColumnIndexOrThrow(Db.Devotion.readyToUse);

			if (c.moveToNext()) {
				DevotionArticle res = null;
				if (name.equals("rh")) { //$NON-NLS-1$
					res = new ArticleRenunganHarian(
					date,
					c.getString(col_title),
					c.getString(col_header),
					c.getString(col_body),
					c.getInt(col_readyToUse) > 0
					);
				} else if (name.equals("sh")) { //$NON-NLS-1$
					res = new ArticleSantapanHarian(
					date,
					c.getString(col_title),
					c.getString(col_header),
					c.getString(col_body),
					c.getInt(col_readyToUse) > 0
					);
				} else if (name.equals("me-en")) {
					res = new ArticleMorningEveningEnglish(date, c.getString(col_body), true);
				}

				return res;
			} else {
				return null;
			}
		} finally {
			c.close();
		}
	}

	public List<MVersionYes> listAllVersions() {
		List<MVersionYes> res = new ArrayList<MVersionYes>();
		Cursor cursor = helper.getReadableDatabase().query(Db.TABLE_Version, null, null, null, null, null, Db.Version.ordering + " asc"); //$NON-NLS-1$
		try {
			int col_active = cursor.getColumnIndexOrThrow(Db.Version.active);
			int col_shortName = cursor.getColumnIndexOrThrow(Db.Version.shortName);
			int col_title = cursor.getColumnIndexOrThrow(Db.Version.title);
			int col_description = cursor.getColumnIndexOrThrow(Db.Version.description);
			int col_filename = cursor.getColumnIndexOrThrow(Db.Version.filename);
			int col_filename_originalpdb = cursor.getColumnIndexOrThrow(Db.Version.filename_originalpdb);
			int col_ordering = cursor.getColumnIndexOrThrow(Db.Version.ordering);

			while (cursor.moveToNext()) {
				MVersionYes yes = new MVersionYes();
				yes.cache_active = cursor.getInt(col_active) != 0;
				yes.type = Db.Version.kind_yes;
				yes.description = cursor.getString(col_description);
				yes.shortName = cursor.getString(col_shortName);
				yes.longName = cursor.getString(col_title);
				yes.filename = cursor.getString(col_filename);
				yes.originalPdbFilename = cursor.getString(col_filename_originalpdb);
				yes.ordering = cursor.getInt(col_ordering);
				res.add(yes);
			}
		} finally {
			cursor.close();
		}
		return res;
	}

	public void setYesVersionActive(String filename, boolean active) {
		SQLiteDatabase db = helper.getWritableDatabase();
		ContentValues cv = new ContentValues();
		cv.put(Db.Version.active, active? 1: 0);
		db.update(Db.TABLE_Version, cv, Db.Version.kind + "=? and " + Db.Version.filename + "=?", new String[] {String.valueOf(Db.Version.kind_yes), filename}); //$NON-NLS-1$ //$NON-NLS-2$
	}

	public int getYesVersionMaxOrdering() {
		SQLiteDatabase db = helper.getReadableDatabase();
		SQLiteStatement stmt = db.compileStatement("select max(" + Db.Version.ordering + ") from " + Db.TABLE_Version);  //$NON-NLS-1$//$NON-NLS-2$
		try {
			return (int) stmt.simpleQueryForLong();
		} finally {
			stmt.close();
		}
	}

	public void insertYesVersionWithActive(MVersionYes version, boolean active) {
		SQLiteDatabase db = helper.getWritableDatabase();
		ContentValues cv = new ContentValues();
		cv.put(Db.Version.active, active);
		cv.put(Db.Version.kind, Db.Version.kind_yes);
		cv.put(Db.Version.shortName, version.shortName);
		cv.put(Db.Version.title, version.longName);
		cv.put(Db.Version.description, version.description);
		cv.put(Db.Version.filename, version.filename);
		cv.put(Db.Version.filename_originalpdb, version.originalPdbFilename);
		cv.put(Db.Version.ordering, version.ordering);
		db.insert(Db.TABLE_Version, null, cv);
	}

	public boolean hasYesVersionWithFilename(String filename) {
		SQLiteDatabase db = helper.getReadableDatabase();
		SQLiteStatement stmt = db.compileStatement("select count(*) from " + Db.TABLE_Version + " where " + Db.Version.kind + "=? and " + Db.Version.filename + "=?");    //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$
		try {
			stmt.clearBindings();
			stmt.bindLong(1, Db.Version.kind_yes);
			stmt.bindString(2, filename);
			return stmt.simpleQueryForLong() > 0;
		} finally {
			stmt.close();
		}
	}

	public void deleteYesVersion(MVersionYes version) {
		SQLiteDatabase db = helper.getWritableDatabase();
		db.delete(Db.TABLE_Version, Db.Version.filename + "=?", new String[] {version.filename}); //$NON-NLS-1$
	}

	public List<Label> listAllLabels() {
		List<Label> res = new ArrayList<Label>();
		Cursor cursor = helper.getReadableDatabase().query(Db.TABLE_Label, null, null, null, null, null, Db.Label.ordering + " asc"); //$NON-NLS-1$
		try {
			while (cursor.moveToNext()) {
				res.add(labelFromCursor(cursor));
			}
		} finally {
			cursor.close();
		}
		return res;
	}

	/**
	 * @return null when not found
	 */
	public List<Label> listLabelsByBookmarkId(long bookmark_id) {
		List<Label> res = null;
		Cursor cursor = helper.getReadableDatabase().rawQuery("select " + Db.TABLE_Label + ".* from " + Db.TABLE_Label + ", " + Db.TABLE_Bookmark2_Label + " where " + Db.TABLE_Bookmark2_Label + "." + Db.Bookmark2_Label.label_id + " = " + Db.TABLE_Label + "." + BaseColumns._ID + " and " + Db.TABLE_Bookmark2_Label + "." + Db.Bookmark2_Label.bookmark2_id + " = ?  order by " + Db.TABLE_Label + "." + Db.Label.ordering + " asc", new String[] {String.valueOf(bookmark_id)});       //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$//$NON-NLS-5$//$NON-NLS-6$//$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$ //$NON-NLS-12$
		try {
			while (cursor.moveToNext()) {
				if (res == null) res = new ArrayList<Label>();
				res.add(labelFromCursor(cursor));
			}
		} finally {
			cursor.close();
		}
		return res;
	}

	public static Label labelFromCursor(Cursor c) {
		Label res = new Label();
		res._id = c.getLong(c.getColumnIndexOrThrow(BaseColumns._ID));
		res.title = c.getString(c.getColumnIndexOrThrow(Db.Label.title));
		res.ordering = c.getInt(c.getColumnIndexOrThrow(Db.Label.ordering));
		res.backgroundColor = c.getString(c.getColumnIndexOrThrow(Db.Label.backgroundColor));
		return res;
	}

	public ContentValues labelToContentValues(Label label) {
		ContentValues res = new ContentValues();
		// skip _id
		res.put(Db.Label.title, label.title);
		res.put(Db.Label.ordering, label.ordering);
		res.put(Db.Label.backgroundColor, label.backgroundColor);
		return res;
	}

	/**
	 * @return null when not found
	 */
	public TLongList listLabelIdsByBookmarkId(long bookmark_id) {
		TLongList res = null;
		Cursor cursor = helper.getReadableDatabase().rawQuery("select " + Db.TABLE_Bookmark2_Label + "." + Db.Bookmark2_Label.label_id + " from " + Db.TABLE_Bookmark2_Label + " where " + Db.TABLE_Bookmark2_Label + "." + Db.Bookmark2_Label.bookmark2_id + "=?", new String[] {String.valueOf(bookmark_id)});       //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$//$NON-NLS-5$//$NON-NLS-6$
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

	public int getLabelMaxOrdering() {
		SQLiteDatabase db = helper.getReadableDatabase();
		SQLiteStatement stmt = db.compileStatement("select max(" + Db.Label.ordering + ") from " + Db.TABLE_Label);  //$NON-NLS-1$//$NON-NLS-2$
		try {
			return (int) stmt.simpleQueryForLong();
		} finally {
			stmt.close();
		}
	}

	public Label insertLabel(String title, String bgColor) {
		Label res = new Label(-1, title, getLabelMaxOrdering() + 1, bgColor);
		SQLiteDatabase db = helper.getWritableDatabase();
		long _id = db.insert(Db.TABLE_Label, null, labelToContentValues(res));
		if (_id == -1) {
			return null;
		} else {
			res._id = _id;
			return res;
		}
	}

	public void updateLabels(Bookmark2 bookmark, Set<Label> labels) {
		SQLiteDatabase db = helper.getWritableDatabase();
		db.beginTransaction();
		try {
			// remove all
			db.delete(Db.TABLE_Bookmark2_Label, Db.Bookmark2_Label.bookmark2_id + "=?", new String[] {String.valueOf(bookmark._id)}); //$NON-NLS-1$

			// add all
			ContentValues cv = new ContentValues();
			for (Label label : labels) {
				cv.put(Db.Bookmark2_Label.bookmark2_id, bookmark._id);
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
                return labelFromCursor(cursor);
            } else {
                return null;
            }
        } finally {
            cursor.close();
        }
    }

	public void deleteLabelById(long id) {
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
		ContentValues cv = labelToContentValues(label);
		db.update(Db.TABLE_Label, cv, "_id=?", new String[] {String.valueOf(label._id)}); //$NON-NLS-1$
	}

	public int countBookmarksWithLabel(Label label) {
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

	public List<ProgressMark> listAllProgressMarks() {
		List<ProgressMark> res = new ArrayList<ProgressMark>();
		Cursor cursor = helper.getReadableDatabase().query(Db.TABLE_ProgressMark, null, null, null, null, null, null);
		try {
			while (cursor.moveToNext()) {
				res.add(progressMarkFromCursor(cursor));
			}
		} finally {
			cursor.close();
		}
		return res;
	}

	public ProgressMark getProgressMarkByPresetId(final int preset_id) {
		Cursor cursor = helper.getReadableDatabase().query(
		Db.TABLE_ProgressMark,
		null,
		Db.ProgressMark.preset_id + "=?",
		new String[] {String.valueOf(preset_id)},
		null, null, null
		);

		try {
			if (!cursor.moveToNext()) return null;

			return progressMarkFromCursor(cursor);
		} finally {
			cursor.close();
		}
	}

	public int updateProgressMark(ProgressMark progressMark) {
		insertProgressMarkHistory(progressMark);
		return helper.getWritableDatabase().update(Db.TABLE_ProgressMark, progressMarkToContentValues(progressMark), Db.ProgressMark.preset_id + "=?", new String[] {String.valueOf(progressMark.preset_id)});
	}

	public void insertProgressMarkHistory(ProgressMark progressMark) {
		ContentValues cv = new ContentValues();
		cv.put(Db.ProgressMarkHistory.progress_mark_preset_id, progressMark.preset_id);
		cv.put(Db.ProgressMarkHistory.progress_mark_caption, progressMark.caption);
		cv.put(Db.ProgressMarkHistory.ari, progressMark.ari);
		cv.put(Db.ProgressMarkHistory.createTime, Sqlitil.toInt(progressMark.modifyTime));
		helper.getWritableDatabase().insert(Db.TABLE_ProgressMarkHistory, null, cv);
	}

	public List<ProgressMarkHistory> listProgressMarkHistoryByPresetId(final int preset_id) {
		final Cursor c = helper.getReadableDatabase().rawQuery("select * from " + Db.TABLE_ProgressMarkHistory + " where " + Db.ProgressMarkHistory.progress_mark_preset_id + "=? order by " + Db.ProgressMarkHistory.createTime + " asc", new String[] {String.valueOf(preset_id)});
		try {
			final List<ProgressMarkHistory> res = new ArrayList<ProgressMarkHistory>();
			while (c.moveToNext()) {
				res.add(progressMarkHistoryFromCursor(c));
			}
			return res;
		} finally {
			c.close();
		}
	}

	public static ProgressMark progressMarkFromCursor(Cursor c) {
		ProgressMark res = new ProgressMark();
		res._id = c.getLong(c.getColumnIndexOrThrow(BaseColumns._ID));
		res.preset_id = c.getInt(c.getColumnIndexOrThrow(Db.ProgressMark.preset_id));
		res.caption = c.getString(c.getColumnIndexOrThrow(Db.ProgressMark.caption));
		res.ari = c.getInt(c.getColumnIndexOrThrow(Db.ProgressMark.ari));
		res.modifyTime = Sqlitil.toDate(c.getInt(c.getColumnIndexOrThrow(Db.ProgressMark.modifyTime)));

		return res;
	}

	public static ContentValues progressMarkToContentValues(ProgressMark progressMark) {
		ContentValues cv = new ContentValues();
		cv.put(Db.ProgressMark.preset_id, progressMark.preset_id);
		cv.put(Db.ProgressMark.caption, progressMark.caption);
		cv.put(Db.ProgressMark.ari, progressMark.ari);
		cv.put(Db.ProgressMark.modifyTime, Sqlitil.toInt(progressMark.modifyTime));
		return cv;
	}

	public static ProgressMarkHistory progressMarkHistoryFromCursor(Cursor c) {
		final ProgressMarkHistory res = new ProgressMarkHistory();
		res._id = c.getLong(c.getColumnIndexOrThrow(BaseColumns._ID));
		res.progress_mark_preset_id = c.getInt(c.getColumnIndexOrThrow(Db.ProgressMarkHistory.progress_mark_preset_id));
		res.progress_mark_caption = c.getString(c.getColumnIndexOrThrow(Db.ProgressMarkHistory.progress_mark_caption));
		res.ari = c.getInt(c.getColumnIndexOrThrow(Db.ProgressMarkHistory.ari));
		res.createTime = Sqlitil.toDate(c.getInt(c.getColumnIndexOrThrow(Db.ProgressMarkHistory.createTime)));

		return res;
	}

	public long insertReadingPlan(final ReadingPlan.ReadingPlanInfo info, byte[] data) {
		final ContentValues cv = new ContentValues();
		cv.put(Db.ReadingPlan.version, info.version);
		cv.put(Db.ReadingPlan.name, info.name);
		cv.put(Db.ReadingPlan.title, info.title);
		cv.put(Db.ReadingPlan.description, info.description);
		cv.put(Db.ReadingPlan.duration, info.duration);
		cv.put(Db.ReadingPlan.startTime, info.startTime);
		cv.put(Db.ReadingPlan.data, data);
		return helper.getWritableDatabase().insert(Db.TABLE_ReadingPlan, null, cv);
	}

	public long insertReadingPlanProgress(final long readingPlanId, final int readingCode, final long checkTime) {
		ContentValues cv = new ContentValues();
		cv.put(Db.ReadingPlanProgress.reading_plan_id, readingPlanId);
		cv.put(Db.ReadingPlanProgress.reading_code, readingCode);
		cv.put(Db.ReadingPlanProgress.checkTime, checkTime);
		return helper.getWritableDatabase().insert(Db.TABLE_ReadingPlanProgress, null, cv);
	}

	public int deleteReadingPlanProgress(final long readingPlanId, final int readingCode) {
		ContentValues cv = new ContentValues();
		cv.put(Db.ReadingPlanProgress.reading_plan_id, readingPlanId);
		cv.put(Db.ReadingPlanProgress.reading_code, readingCode);
		return helper.getWritableDatabase().delete(Db.TABLE_ReadingPlanProgress, Db.ReadingPlanProgress.reading_plan_id + "=? AND " + Db.ReadingPlanProgress.reading_code + "=?", new String[] {String.valueOf(readingPlanId), String.valueOf(readingCode)});
	}

	public IntArrayList getReadingPlanProgressId(final long readingPlanId, final int readingCode) {
		IntArrayList res = new IntArrayList();
		final Cursor c = helper.getReadableDatabase().query(Db.TABLE_ReadingPlanProgress, new String[] {"_id"}, Db.ReadingPlanProgress.reading_plan_id + "=? AND " + Db.ReadingPlanProgress.reading_code + "=?", new String[] {String.valueOf(readingPlanId), String.valueOf(readingCode)}, null, null, null);
		while (c.moveToNext()) {
			res.add(c.getInt(0));
		}
		c.close();
		return res;
	}

	public List<ReadingPlan.ReadingPlanInfo> listAllReadingPlanInfo() {
		final Cursor c = helper.getReadableDatabase().query(Db.TABLE_ReadingPlan,
		new String[] {"_id", Db.ReadingPlan.version, Db.ReadingPlan.name, Db.ReadingPlan.title, Db.ReadingPlan.description, Db.ReadingPlan.duration, Db.ReadingPlan.startTime},
		null, null, null, null, null);
		List<ReadingPlan.ReadingPlanInfo> infos = new ArrayList<ReadingPlan.ReadingPlanInfo>();
		while (c.moveToNext()) {
			ReadingPlan.ReadingPlanInfo info = new ReadingPlan.ReadingPlanInfo();
			info.id = c.getLong(0);
			info.version = c.getInt(1);
			info.name = c.getString(2);
			info.title = c.getString(3);
			info.description = c.getString(4);
			info.duration = c.getInt(5);
			info.startTime = c.getLong(6);
			infos.add(info);
		}
		c.close();
		return infos;
	}

	public byte[] getBinaryReadingPlanById(long id) {
		byte[] buffer = null;
		final Cursor c = helper.getReadableDatabase().query(Db.TABLE_ReadingPlan, new String[] {Db.ReadingPlan.data}, "_id=?", new String[] {String.valueOf(id)}, null, null, null);
		while (c.moveToNext()) {
			buffer = c.getBlob(0);
		}
		c.close();
		return buffer;
	}

	public IntArrayList getAllReadingCodesByReadingPlanId(long id) {
		IntArrayList res = new IntArrayList();
		final Cursor c = helper.getReadableDatabase().query(Db.TABLE_ReadingPlanProgress, new String[] {Db.ReadingPlanProgress.reading_code}, Db.ReadingPlanProgress.reading_plan_id + "=?", new String[] {String.valueOf(id)}, null, null, null);
		while (c.moveToNext()) {
			res.add(c.getInt(0));
		}
		c.close();
		return res;
	}

	public void deleteReadingPlanById(long id) {
		helper.getWritableDatabase().delete(Db.TABLE_ReadingPlanProgress, Db.ReadingPlanProgress.reading_plan_id + "=?", new String[] {String.valueOf(id)});
		helper.getWritableDatabase().delete(Db.TABLE_ReadingPlan, "_id=?", new String[] {String.valueOf(id)});
	}

	public int updateStartDate(long id, long startDate) {
		ContentValues cv = new ContentValues();
		cv.put(Db.ReadingPlan.startTime, startDate);
		return helper.getWritableDatabase().update(Db.TABLE_ReadingPlan, cv, "_id=?", new String[] {String.valueOf(id)});
	}

	public List<String> listReadingPlanNames() {
		final List<String> res = new ArrayList<String>();
		final Cursor c = helper.getReadableDatabase().query(Db.TABLE_ReadingPlan, new String[] {Db.ReadingPlan.name}, null, null, null, null, null);
		try {
			while (c.moveToNext()) {
				res.add(c.getString(0));
			}
			return res;
		} finally {
			c.close();
		}
	}
}
