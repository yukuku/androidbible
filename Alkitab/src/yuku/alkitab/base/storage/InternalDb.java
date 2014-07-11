package yuku.alkitab.base.storage;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.provider.BaseColumns;
import android.util.Log;
import yuku.afw.D;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.DevotionActivity;
import yuku.alkitab.base.ac.MarkerListActivity;
import yuku.alkitab.base.devotion.ArticleMeidA;
import yuku.alkitab.base.devotion.ArticleMorningEveningEnglish;
import yuku.alkitab.base.devotion.ArticleRenunganHarian;
import yuku.alkitab.base.devotion.ArticleSantapanHarian;
import yuku.alkitab.base.devotion.DevotionArticle;
import yuku.alkitab.base.model.MVersionDb;
import yuku.alkitab.base.model.ReadingPlan;
import yuku.alkitab.base.util.Sqlitil;
import yuku.alkitab.model.Label;
import yuku.alkitab.model.Marker;
import yuku.alkitab.model.ProgressMark;
import yuku.alkitab.model.ProgressMarkHistory;
import yuku.alkitab.model.util.Gid;
import yuku.alkitab.util.Ari;
import yuku.alkitab.util.IntArrayList;

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
	 */
	private static ContentValues markerToContentValues(final Marker marker) {
		final ContentValues res = new ContentValues();

		res.put(Db.Marker.ari, marker.ari);
		res.put(Db.Marker.gid, marker.gid);
		res.put(Db.Marker.kind, marker.kind.code);
		res.put(Db.Marker.caption, marker.caption);
		res.put(Db.Marker.verseCount, marker.verseCount);
		res.put(Db.Marker.createTime, Sqlitil.toInt(marker.createTime));
		res.put(Db.Marker.modifyTime, Sqlitil.toInt(marker.modifyTime));

		return res;
	}

	public static Marker markerFromCursor(Cursor cursor) {
		final Marker res = Marker.createEmptyMarker();

		res._id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
		res.gid = cursor.getString(cursor.getColumnIndexOrThrow(Db.Marker.gid));
		res.ari = cursor.getInt(cursor.getColumnIndexOrThrow(Db.Marker.ari));
		res.kind = Marker.Kind.fromCode(cursor.getInt(cursor.getColumnIndexOrThrow(Db.Marker.kind)));
		res.caption = cursor.getString(cursor.getColumnIndexOrThrow(Db.Marker.caption));
		res.verseCount = cursor.getInt(cursor.getColumnIndexOrThrow(Db.Marker.verseCount));
		res.createTime = Sqlitil.toDate(cursor.getInt(cursor.getColumnIndexOrThrow(Db.Marker.createTime)));
		res.modifyTime = Sqlitil.toDate(cursor.getInt(cursor.getColumnIndexOrThrow(Db.Marker.modifyTime)));

		return res;
	}

	public Marker getMarkerById(long _id) {
		Cursor cursor = helper.getReadableDatabase().query(
			Db.TABLE_Marker,
			null,
			"_id=?",
			new String[]{String.valueOf(_id)},
			null, null, null
		);

		try {
			if (!cursor.moveToNext()) return null;
			return markerFromCursor(cursor);
		} finally {
			cursor.close();
		}
	}

	/**
	 * Get a marker based on ari, kind, and ordering.
	 * @param ordering (starting from 0) in case of there are multiple markers with the same kind on a verse.
	 * @return null if not found
	 */
	public Marker getMarker(int ari, Marker.Kind kind, int ordering) {
		final Cursor cursor = helper.getReadableDatabase().query(
			Db.TABLE_Marker,
			null,
			Db.Marker.ari + "=? and " + Db.Marker.kind + "=?",
			new String[] {String.valueOf(ari), String.valueOf(kind.code)},
			null, null, "_id asc", ordering + ",1"
		);

		try {
			if (!cursor.moveToNext()) return null;
			return markerFromCursor(cursor);
		} finally {
			cursor.close();
		}
	}

	public int countMarkersForAriKind(final int ari, final Marker.Kind kind) {
		final SQLiteDatabase db = helper.getReadableDatabase();
		return (int) DatabaseUtils.longForQuery(db, "select count(*) from " + Db.TABLE_Marker + " where " + Db.Marker.ari + "=? and " + Db.Marker.kind + "=?", new String[]{String.valueOf(ari), String.valueOf(kind.code)});
	}

	public int updateMarker(Marker marker) {
		return helper.getWritableDatabase().update(Db.TABLE_Marker, markerToContentValues(marker), "_id=?", new String[] {String.valueOf(marker._id)});
	}

	public Marker insertMarker(int ari, Marker.Kind kind, String caption, int verseCount, Date createTime, Date modifyTime) {
		final Marker res = Marker.createNewMarker(ari, kind, caption, verseCount, createTime, modifyTime);
		final SQLiteDatabase db = helper.getWritableDatabase();

		res._id = db.insert(Db.TABLE_Marker, null, markerToContentValues(res));

		return res;
	}

	public void deleteBookmarkById(long _id) {
		final Marker marker = getMarkerById(_id);

		final SQLiteDatabase db = helper.getWritableDatabase();
		db.beginTransaction();
		try {
			db.delete(Db.TABLE_Marker_Label, Db.Marker_Label.marker_gid + "=?", new String[]{marker.gid});
			db.delete(Db.TABLE_Marker, "_id=?", new String[]{String.valueOf(_id)});
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

	public void deleteNonBookmarkMarkerById(long _id) {
		SQLiteDatabase db = helper.getWritableDatabase();
		db.delete(Db.TABLE_Marker, "_id=?", new String[]{String.valueOf(_id)});
	}

	public List<Marker> listMarkers(Marker.Kind kind, long label_id, String sortColumn, boolean sortAscending) {
		final SQLiteDatabase db = helper.getReadableDatabase();
		final String sortClause = sortColumn + (Db.Marker.caption.equals(sortColumn)? " collate NOCASE ": "") + (sortAscending? " asc": " desc");

		final List<Marker> res = new ArrayList<>();
		final Cursor c;
		if (label_id == 0) { // no restrictions
			c = db.query(Db.TABLE_Marker, null, Db.Marker.kind + "=?", new String[] {String.valueOf(kind.code)}, null, null, sortClause);
		} else if (label_id == MarkerListActivity.LABELID_noLabel) { // only without label
			c = db.rawQuery("select " + Db.TABLE_Marker + ".* from " + Db.TABLE_Marker + " where " + Db.TABLE_Marker + "." + Db.Marker.kind + "=? and " + Db.TABLE_Marker + "." + Db.Marker.gid + " not in (select distinct " + Db.Marker_Label.marker_gid + " from " + Db.TABLE_Marker_Label + ") order by " + Db.TABLE_Marker + "." + sortClause, new String[] {String.valueOf(kind.code)});
		} else { // filter by label_id
			final Label label = getLabelById(label_id);
			c = db.rawQuery("select " + Db.TABLE_Marker + ".* from " + Db.TABLE_Marker + ", " + Db.TABLE_Marker_Label + " where " + Db.Marker.kind + "=? and " + Db.TABLE_Marker + "." + Db.Marker.gid + " = " + Db.TABLE_Marker_Label + "." + Db.Marker_Label.marker_gid + " and " + Db.TABLE_Marker_Label + "." + Db.Marker_Label.label_gid + "=? order by " + Db.TABLE_Marker + "." + sortClause, new String[]{String.valueOf(kind.code), label.gid});
		}

		try {
			while (c.moveToNext()) {
				res.add(markerFromCursor(c));
			}
		} finally {
			c.close();
		}

		return res;
	}

	public int countAllMarkers() {
		return (int) DatabaseUtils.queryNumEntries(helper.getReadableDatabase(), Db.TABLE_Marker);
	}

	private SQLiteStatement stmt_countMarkersForBookChapter = null;

	public int countMarkersForBookChapter(int ari_bookchapter) {
		final int ariMin = ari_bookchapter & 0x00ffff00;
		final int ariMax = ari_bookchapter | 0x000000ff;

		if (stmt_countMarkersForBookChapter == null) {
			stmt_countMarkersForBookChapter = helper.getReadableDatabase().compileStatement("select count(*) from " + Db.TABLE_Marker + " where " + Db.Marker.ari + ">=? and " + Db.Marker.ari + "<?");
		}

		stmt_countMarkersForBookChapter.bindLong(1, ariMin);
		stmt_countMarkersForBookChapter.bindLong(2, ariMax);

		return (int) stmt_countMarkersForBookChapter.simpleQueryForLong();
	}


	/**
	 * Put attributes (bookmark count, note count, and highlight color) for each verse.
	 */
	public void putAttributes(final int ari_bookchapter, final int[] bookmarkCountMap, final int[] noteCountMap, final int[] highlightColorMap) {
		final int ariMin = ari_bookchapter & 0x00ffff00;
		final int ariMax = ari_bookchapter | 0x000000ff;

		final String[] params = {
			String.valueOf(ariMin),
			String.valueOf(ariMax),
		};

		final Cursor cursor = helper.getReadableDatabase().rawQuery("select * from " + Db.TABLE_Marker + " where " + Db.Marker.ari + ">=? and " + Db.Marker.ari + "<?", params);
		try {
			final int col_kind = cursor.getColumnIndexOrThrow(Db.Marker.kind);
			final int col_ari = cursor.getColumnIndexOrThrow(Db.Marker.ari);
			final int col_caption = cursor.getColumnIndexOrThrow(Db.Marker.caption);
			final int col_verseCount = cursor.getColumnIndexOrThrow(Db.Marker.verseCount);

			while (cursor.moveToNext()) {
				final int ari = cursor.getInt(col_ari);
				final int kind = cursor.getInt(col_kind);

				int mapOffset = Ari.toVerse(ari) - 1;
				if (mapOffset >= bookmarkCountMap.length) {
					Log.e(TAG, "mapOffset too many " + mapOffset + " happens on ari 0x" + Integer.toHexString(ari));
					continue;
				}

				if (kind == Marker.Kind.bookmark.code) {
					bookmarkCountMap[mapOffset] += 1;
				} else if (kind == Marker.Kind.note.code) {
					noteCountMap[mapOffset] += 1;
				} else if (kind == Marker.Kind.highlight.code) {
					// traverse as far as verseCount
					final int verseCount = cursor.getInt(col_verseCount);

					for (int i = 0; i < verseCount; i++) {
						int mapOffset2 = mapOffset + i;
						if (mapOffset2 >= highlightColorMap.length) break; // do not go past number of verses in this chapter

						final String caption = cursor.getString(col_caption);
						final int colorRgb = U.decodeHighlight(caption);

						highlightColorMap[mapOffset2] = colorRgb;
					}
				}
			}
		} finally {
			cursor.close();
		}
	}

	public void updateOrInsertHighlights(int ari_bookchapter, IntArrayList selectedVerses_1, int colorRgb) {
		final SQLiteDatabase db = helper.getWritableDatabase();

		db.beginTransaction();
		try {
			final String[] params = {null /* for the ari */, String.valueOf(Marker.Kind.highlight.code)};

			// every requested verses
			for (int i = 0; i < selectedVerses_1.size(); i++) {
				final int ari = Ari.encodeWithBc(ari_bookchapter, selectedVerses_1.get(i));
				params[0] = String.valueOf(ari);

				Cursor c = db.query(Db.TABLE_Marker, null, Db.Marker.ari + "=? and " + Db.Marker.kind + "=?", params, null, null, null);
				try {
					if (c.moveToNext()) { // check if marker exists
						final Marker marker = markerFromCursor(c);
						marker.modifyTime = new Date();
						if (colorRgb != -1) {
							marker.caption = U.encodeHighlight(colorRgb);
							db.update(Db.TABLE_Marker, markerToContentValues(marker), "_id=?", new String[] {String.valueOf(marker._id)});
						} else {
							// delete
							db.delete(Db.TABLE_Marker, "_id=?", new String[] {String.valueOf(marker._id)});
						}
					} else {
						if (colorRgb == -1) {
							// no need to do, from no color to no color
						} else {
							final Date now = new Date();
							final Marker marker = Marker.createNewMarker(ari, Marker.Kind.highlight, U.encodeHighlight(colorRgb), 1, now, now);
							db.insert(Db.TABLE_Marker, null, markerToContentValues(marker));
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

	/**
	 * Get the highlight color rgb of several verses.
	 * @return the color rgb or -1 if there are multiple colors.
	 */
	public int getHighlightColorRgb(int ari_bookchapter, IntArrayList selectedVerses_1) {
		int ariMin = ari_bookchapter & 0xffffff00;
		int ariMax = ari_bookchapter | 0x000000ff;
		int[] colors = new int[256];
		int res = -2;

		for (int i = 0; i < colors.length; i++) colors[i] = -1;

		// check if exists
		final Cursor c = helper.getReadableDatabase().query(
			Db.TABLE_Marker, null, Db.Marker.ari + ">? and " + Db.Marker.ari + "<=? and " + Db.Marker.kind + "=?",
			new String[] {String.valueOf(ariMin), String.valueOf(ariMax), String.valueOf(Marker.Kind.highlight.code)},
			null, null, null
		);

		try {
			final int col_ari = c.getColumnIndexOrThrow(Db.Marker.ari);
			final int col_caption = c.getColumnIndexOrThrow(Db.Marker.caption);

			// put to array first
			while (c.moveToNext()) {
				int ari = c.getInt(col_ari);
				int index = ari & 0xff;
				int color = U.decodeHighlight(c.getString(col_caption));
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
			db.delete(Db.TABLE_Devotion, Db.Devotion.name + "=? and " + Db.Devotion.date + "=?", new String[] {article.getKind().name, article.getDate()});

			ContentValues values = new ContentValues();
			values.put(Db.Devotion.name, article.getKind().name);
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
		return db.delete(Db.TABLE_Devotion, Db.Devotion.touchTime + "<?", new String[] {String.valueOf(Sqlitil.toInt(date))});
	}

	/**
	 * Try to get article from local db. Non ready-to-use article will be returned too.
	 */
	public DevotionArticle tryGetDevotion(String name, String date) {
		Cursor c = helper.getReadableDatabase().query(Db.TABLE_Devotion, null, Db.Devotion.name + "=? and " + Db.Devotion.date + "=?", new String[]{name, date}, null, null, null);
		try {
			int col_title = c.getColumnIndexOrThrow(Db.Devotion.title);
			int col_header = c.getColumnIndexOrThrow(Db.Devotion.header);
			int col_body = c.getColumnIndexOrThrow(Db.Devotion.body);
			int col_readyToUse = c.getColumnIndexOrThrow(Db.Devotion.readyToUse);

			if (!c.moveToNext()) {
				return null;
			}

			final DevotionActivity.DevotionKind kind = DevotionActivity.DevotionKind.getByName(name);
			switch (kind) {
				case RH: {
					return new ArticleRenunganHarian(
					date,
					c.getString(col_title),
					c.getString(col_header),
					c.getString(col_body),
					c.getInt(col_readyToUse) > 0
					);
				}
				case SH: {
					return new ArticleSantapanHarian(
					date,
					c.getString(col_title),
					c.getString(col_header),
					c.getString(col_body),
					c.getInt(col_readyToUse) > 0
					);
				}
				case ME_EN: {
					return new ArticleMorningEveningEnglish(date, c.getString(col_body), true);
				}
				case MEID_A: {
					return new ArticleMeidA(date, c.getString(col_body), c.getInt(col_readyToUse) > 0);
				}
				default:
					return null;
			}
		} finally {
			c.close();
		}
	}

	public List<MVersionDb> listAllVersions() {
		List<MVersionDb> res = new ArrayList<>();
		Cursor cursor = helper.getReadableDatabase().query(Db.TABLE_Version, null, null, null, null, null, Db.Version.ordering + " asc");
		try {
			int col_locale = cursor.getColumnIndexOrThrow(Db.Version.locale);
			int col_shortName = cursor.getColumnIndexOrThrow(Db.Version.shortName);
			int col_longName = cursor.getColumnIndexOrThrow(Db.Version.longName);
			int col_description = cursor.getColumnIndexOrThrow(Db.Version.description);
			int col_filename = cursor.getColumnIndexOrThrow(Db.Version.filename);
			int col_preset_name = cursor.getColumnIndexOrThrow(Db.Version.preset_name);
			int col_active = cursor.getColumnIndexOrThrow(Db.Version.active);
			int col_ordering = cursor.getColumnIndexOrThrow(Db.Version.ordering);

			while (cursor.moveToNext()) {
				final MVersionDb mv = new MVersionDb();
				mv.locale = cursor.getString(col_locale);
				mv.shortName = cursor.getString(col_shortName);
				mv.longName = cursor.getString(col_longName);
				mv.description = cursor.getString(col_description);
				mv.filename = cursor.getString(col_filename);
				mv.preset_name = cursor.getString(col_preset_name);
				mv.cache_active = cursor.getInt(col_active) != 0;
				mv.ordering = cursor.getInt(col_ordering);
				res.add(mv);
			}
		} finally {
			cursor.close();
		}
		return res;
	}

	public void setVersionActive(MVersionDb mv, boolean active) {
		final SQLiteDatabase db = helper.getWritableDatabase();
		final ContentValues cv = new ContentValues();
		cv.put(Db.Version.active, active? 1: 0);

		if (mv.preset_name != null) {
			db.update(Db.TABLE_Version, cv, Db.Version.preset_name + "=?", new String[] {mv.preset_name});
		} else {
			db.update(Db.TABLE_Version, cv, Db.Version.filename + "=?", new String[] {mv.filename});
		}
	}

	public int getVersionMaxOrdering() {
		final SQLiteDatabase db = helper.getReadableDatabase();
		return (int) DatabaseUtils.longForQuery(db, "select max(" + Db.Version.ordering + ") from " + Db.TABLE_Version, null);
	}

	public void insertVersionWithActive(MVersionDb mv, boolean active) {
		final SQLiteDatabase db = helper.getWritableDatabase();
		final ContentValues cv = new ContentValues();
		cv.put(Db.Version.locale, mv.locale);
		cv.put(Db.Version.shortName, mv.shortName);
		cv.put(Db.Version.longName, mv.longName);
		cv.put(Db.Version.description, mv.description);
		cv.put(Db.Version.filename, mv.filename);
		cv.put(Db.Version.preset_name, mv.preset_name);
		cv.put(Db.Version.active, active); // special
		cv.put(Db.Version.ordering, mv.ordering);
		db.insert(Db.TABLE_Version, null, cv);
	}

	public boolean hasVersionWithFilename(String filename) {
		final SQLiteDatabase db = helper.getReadableDatabase();
		return DatabaseUtils.longForQuery(db, "select count(*) from " + Db.TABLE_Version + " where " + Db.Version.filename + "=?", new String[] {filename}) > 0;
	}

	public void deleteVersion(MVersionDb mv) {
		final SQLiteDatabase db = helper.getWritableDatabase();

		// delete preset by preset_name
		if (mv.preset_name != null) {
			final int deleted = db.delete(Db.TABLE_Version, Db.Version.preset_name + "=?", new String[]{mv.preset_name});
			if (deleted > 0) {
				return; // finished! if not, we fallback to filename
			}
		}

		db.delete(Db.TABLE_Version, Db.Version.filename + "=?", new String[]{mv.filename});
	}

	public List<Label> listAllLabels() {
		List<Label> res = new ArrayList<Label>();
		Cursor cursor = helper.getReadableDatabase().query(Db.TABLE_Label, null, null, null, null, null, Db.Label.ordering + " asc");
		try {
			while (cursor.moveToNext()) {
				res.add(labelFromCursor(cursor));
			}
		} finally {
			cursor.close();
		}
		return res;
	}

	public List<Label> listLabelsByMarkerId(long marker_id) {
		final Marker marker = getMarkerById(marker_id);

		final List<Label> res = new ArrayList<>();
		final Cursor cursor = helper.getReadableDatabase().rawQuery("select " + Db.TABLE_Label + ".* from " + Db.TABLE_Label + ", " + Db.TABLE_Marker_Label + " where " + Db.TABLE_Marker_Label + "." + Db.Marker_Label.label_gid + " = " + Db.TABLE_Label + "." + Db.Label.gid + " and " + Db.TABLE_Marker_Label + "." + Db.Marker_Label.marker_gid + "=? order by " + Db.TABLE_Label + "." + Db.Label.ordering + " asc", new String[]{marker.gid});
		try {
			while (cursor.moveToNext()) {
				res.add(labelFromCursor(cursor));
			}
		} finally {
			cursor.close();
		}
		return res;
	}

	public static Label labelFromCursor(Cursor c) {
		final Label res = Label.createEmptyLabel();

		res._id = c.getLong(c.getColumnIndexOrThrow("_id"));
		res.gid = c.getString(c.getColumnIndexOrThrow(Db.Label.gid));
		res.title = c.getString(c.getColumnIndexOrThrow(Db.Label.title));
		res.ordering = c.getInt(c.getColumnIndexOrThrow(Db.Label.ordering));
		res.backgroundColor = c.getString(c.getColumnIndexOrThrow(Db.Label.backgroundColor));

		return res;
	}

	/**
	 * _id is not stored
	 */
	private ContentValues labelToContentValues(Label label) {
		final ContentValues res = new ContentValues();

		res.put(Db.Label.gid, label.gid);
		res.put(Db.Label.title, label.title);
		res.put(Db.Label.ordering, label.ordering);
		res.put(Db.Label.backgroundColor, label.backgroundColor);

		return res;
	}

	public int getLabelMaxOrdering() {
		SQLiteDatabase db = helper.getReadableDatabase();
		SQLiteStatement stmt = db.compileStatement("select max(" + Db.Label.ordering + ") from " + Db.TABLE_Label);
		try {
			return (int) stmt.simpleQueryForLong();
		} finally {
			stmt.close();
		}
	}

	public Label insertLabel(String title, String bgColor) {
		final Label res = Label.createNewLabel(title, getLabelMaxOrdering() + 1, bgColor);
		final SQLiteDatabase db = helper.getWritableDatabase();

		res._id = db.insert(Db.TABLE_Label, null, labelToContentValues(res));
		return res;
	}

	public void updateLabels(Marker marker, Set<Label> newLabels) {
		final SQLiteDatabase db = helper.getWritableDatabase();

		final List<Label> oldLabels = listLabelsByMarkerId(marker._id);

		final List<Label> addLabels = new ArrayList<>();
		for (final Label newLabel : newLabels) {
			if (!oldLabels.contains(newLabel)) {
				addLabels.add(newLabel);
			}
		}

		final List<Label> removeLabels = new ArrayList<>();
		for (final Label oldLabel : oldLabels) {
			if (!newLabels.contains(oldLabel)) {
				removeLabels.add(oldLabel);
			}
		}

		db.beginTransaction();
		try {
			// remove
			for (final Label removeLabel : removeLabels) {
				db.delete(Db.TABLE_Marker_Label, Db.Marker_Label.marker_gid + "=? and " + Db.Marker_Label.label_gid + "=?", new String[]{marker.gid, removeLabel.gid});
			}

			// add
			final ContentValues cv = new ContentValues();
			for (final Label addLabel : addLabels) {
				cv.put(Db.Marker_Label.gid, Gid.newGid());
				cv.put(Db.Marker_Label.marker_gid, marker.gid);
				cv.put(Db.Marker_Label.label_gid, addLabel.gid);
				db.insert(Db.TABLE_Marker_Label, null, cv);
			}

			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

    public Label getLabelById(long _id) {
        SQLiteDatabase db = helper.getReadableDatabase();
		Cursor cursor = db.query(Db.TABLE_Label, null, "_id=?", new String[]{String.valueOf(_id)}, null, null, null);
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

	public void deleteLabelById(long _id) {
		final Label label = getLabelById(_id);
		final SQLiteDatabase db = helper.getWritableDatabase();
		db.beginTransaction();
		try {
			db.delete(Db.TABLE_Marker_Label, Db.Marker_Label.label_gid + "=?", new String[]{label.gid});
			db.delete(Db.TABLE_Label, "_id=?", new String[]{String.valueOf(_id)});
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

	public void updateLabel(Label label) {
		SQLiteDatabase db = helper.getWritableDatabase();
		ContentValues cv = labelToContentValues(label);
		db.update(Db.TABLE_Label, cv, "_id=?", new String[] {String.valueOf(label._id)});
	}

	public int countMarkersWithLabel(Label label) {
		final SQLiteDatabase db = helper.getReadableDatabase();
		return (int) DatabaseUtils.longForQuery(db, "select count(*) from " + Db.TABLE_Marker_Label + " where " + Db.Marker_Label.label_gid + "=?", new String[]{label.gid});
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
			new String[]{String.valueOf(preset_id)},
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
		helper.getWritableDatabase().delete(Db.TABLE_ReadingPlan, "_id=?", new String[]{String.valueOf(id)});
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
