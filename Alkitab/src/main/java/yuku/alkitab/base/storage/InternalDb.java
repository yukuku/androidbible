package yuku.alkitab.base.storage;

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.Build;
import android.provider.BaseColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Pair;
import com.google.gson.reflect.TypeToken;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.DevotionActivity;
import yuku.alkitab.base.ac.MarkerListActivity;
import yuku.alkitab.base.devotion.ArticleMeidA;
import yuku.alkitab.base.devotion.ArticleMorningEveningEnglish;
import yuku.alkitab.base.devotion.ArticleRenunganHarian;
import yuku.alkitab.base.devotion.ArticleRoc;
import yuku.alkitab.base.devotion.ArticleSantapanHarian;
import yuku.alkitab.base.devotion.DevotionArticle;
import yuku.alkitab.base.model.MVersion;
import yuku.alkitab.base.model.MVersionDb;
import yuku.alkitab.base.model.MVersionInternal;
import yuku.alkitab.base.model.PerVersionSettings;
import yuku.alkitab.base.model.ReadingPlan;
import yuku.alkitab.base.model.SyncLog;
import yuku.alkitab.base.model.SyncShadow;
import yuku.alkitab.base.sync.Sync;
import yuku.alkitab.base.sync.SyncAdapter;
import yuku.alkitab.base.sync.SyncRecorder;
import yuku.alkitab.base.sync.Sync_Mabel;
import yuku.alkitab.base.sync.Sync_Pins;
import yuku.alkitab.base.sync.Sync_Rp;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.base.util.Highlights;
import yuku.alkitab.base.util.Sqlitil;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.model.Label;
import yuku.alkitab.model.Marker;
import yuku.alkitab.model.Marker_Label;
import yuku.alkitab.model.ProgressMark;
import yuku.alkitab.model.ProgressMarkHistory;
import yuku.alkitab.util.Ari;
import yuku.alkitab.util.IntArrayList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static yuku.alkitab.base.util.Literals.Array;
import static yuku.alkitab.base.util.Literals.ToStringArray;

@TargetApi(Build.VERSION_CODES.KITKAT)
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

	private static Marker_Label marker_LabelFromCursor(Cursor cursor) {
		final Marker_Label res = Marker_Label.createEmptyMarker_Label();

		res._id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
		res.gid = cursor.getString(cursor.getColumnIndexOrThrow(Db.Marker_Label.gid));
		res.marker_gid = cursor.getString(cursor.getColumnIndexOrThrow(Db.Marker_Label.marker_gid));
		res.label_gid = cursor.getString(cursor.getColumnIndexOrThrow(Db.Marker_Label.label_gid));

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

	@Nullable public Marker getMarkerByGid(@NonNull final String gid) {
		final Cursor cursor = helper.getReadableDatabase().query(Db.TABLE_Marker, null, Db.Marker.gid + "=?", Array(gid), null, null, null);

		try {
			if (!cursor.moveToNext()) return null;
			return markerFromCursor(cursor);
		} finally {
			cursor.close();
		}
	}

	/**
	 * Ordered by modified time, the newest is first.
	 */
	public List<Marker> listMarkersForAriKind(final int ari, final Marker.Kind kind) {
		final SQLiteDatabase db = helper.getReadableDatabase();
		final Cursor c = db.query(Db.TABLE_Marker, null, Db.Marker.ari + "=? and " + Db.Marker.kind + "=?", ToStringArray(ari, kind.code), null, null, Db.Marker.modifyTime + " desc", null);
		try {
			final List<Marker> res = new ArrayList<>();
			while (c.moveToNext()) {
				res.add(markerFromCursor(c));
			}
			return res;
		} finally {
			c.close();
		}
	}

	/**
	 * Insert a new marker or update an existing marker.
	 * @param marker if the _id is 0, this marker will be inserted. Otherwise, updated.
	 */
	public void insertOrUpdateMarker(@NonNull final Marker marker) {
		final SQLiteDatabase db = helper.getWritableDatabase();
		if (marker._id != 0) {
			db.update(Db.TABLE_Marker, markerToContentValues(marker), "_id=?", Array(String.valueOf(marker._id)));
		} else {
			marker._id = db.insert(Db.TABLE_Marker, null, markerToContentValues(marker));
		}
		Sync.notifySyncNeeded(SyncShadow.SYNC_SET_MABEL);
	}

	public Marker insertMarker(int ari, Marker.Kind kind, String caption, int verseCount, Date createTime, Date modifyTime) {
		final Marker res = Marker.createNewMarker(ari, kind, caption, verseCount, createTime, modifyTime);
		final SQLiteDatabase db = helper.getWritableDatabase();

		res._id = db.insert(Db.TABLE_Marker, null, markerToContentValues(res));
		Sync.notifySyncNeeded(SyncShadow.SYNC_SET_MABEL);

		return res;
	}

	/** Used in migration from v3 */
	public static long insertMarker(final SQLiteDatabase db, final Marker marker) {
		marker._id = db.insert(Db.TABLE_Marker, null, markerToContentValues(marker));
		Sync.notifySyncNeeded(SyncShadow.SYNC_SET_MABEL);

		return marker._id;
	}

	public void deleteMarkerById(long _id) {
		final Marker marker = getMarkerById(_id);

		final SQLiteDatabase db = helper.getWritableDatabase();
		db.beginTransactionNonExclusive();
		try {
			db.delete(Db.TABLE_Marker_Label, Db.Marker_Label.marker_gid + "=?", new String[]{marker.gid});
			db.delete(Db.TABLE_Marker, "_id=?", new String[]{String.valueOf(_id)});
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
		Sync.notifySyncNeeded(SyncShadow.SYNC_SET_MABEL);
	}

	public void deleteNonBookmarkMarkerById(long _id) {
		SQLiteDatabase db = helper.getWritableDatabase();
		db.delete(Db.TABLE_Marker, "_id=?", new String[]{String.valueOf(_id)});
		Sync.notifySyncNeeded(SyncShadow.SYNC_SET_MABEL);
	}

	public List<Marker> listMarkers(Marker.Kind kind, long label_id, String sortColumn, boolean sortAscending) {
		final SQLiteDatabase db = helper.getReadableDatabase();
		final String sortClause = sortColumn + (Db.Marker.caption.equals(sortColumn)? " collate NOCASE ": "") + (sortAscending? " asc": " desc");

		final List<Marker> res = new ArrayList<>();
		final Cursor c;
		if (label_id == 0) { // no restrictions
			c = db.query(Db.TABLE_Marker, null, Db.Marker.kind + "=?", new String[]{String.valueOf(kind.code)}, null, null, sortClause);
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

	public List<Marker> listAllMarkers() {
		final SQLiteDatabase db = helper.getReadableDatabase();
		final Cursor c = db.query(Db.TABLE_Marker, null, null, null, null, null, null);
		final List<Marker> res = new ArrayList<>();

		try {
			while (c.moveToNext()) {
				res.add(markerFromCursor(c));
			}
		} finally {
			c.close();
		}

		return res;
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
	public void putAttributes(final int ari_bookchapter, final int[] bookmarkCountMap, final int[] noteCountMap, final Highlights.Info[] highlightColorMap) {
		final int ariMin = ari_bookchapter & 0x00ffff00;
		final int ariMax = ari_bookchapter | 0x000000ff;

		final String[] params = {
			String.valueOf(ariMin),
			String.valueOf(ariMax),
		};

		// order by modifyTime, so in case a verse has more than one highlight, the latest one is shown
		final Cursor cursor = helper.getReadableDatabase().rawQuery("select * from " + Db.TABLE_Marker + " where " + Db.Marker.ari + ">=? and " + Db.Marker.ari + "<? order by " + Db.Marker.modifyTime, params);
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
					AppLog.e(TAG, "mapOffset too many " + mapOffset + " happens on ari 0x" + Integer.toHexString(ari));
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
						final Highlights.Info info = Highlights.decode(caption);

						highlightColorMap[mapOffset2] = info;
					}
				}
			}
		} finally {
			cursor.close();
		}
	}

	/**
	 * @param colorRgb may NOT be -1. Use {@link #updateOrInsertHighlights(int, IntArrayList, int)} to delete highlight.
	 */
	public void updateOrInsertPartialHighlight(final int ari, final int colorRgb, final CharSequence verseText, final int startOffset, final int endOffset) {
		final SQLiteDatabase db = helper.getWritableDatabase();

		db.beginTransactionNonExclusive();
		try {
			// order by modifyTime desc so we modify the latest one and remove earlier ones if they exist.
			final Cursor c = db.query(Db.TABLE_Marker, null, Db.Marker.ari + "=? and " + Db.Marker.kind + "=?", ToStringArray(ari, Marker.Kind.highlight.code), null, null, Db.Marker.modifyTime + " desc");
			try {
				final int hashCode = Highlights.hashCode(verseText.toString());
				final Date now = new Date();

				if (c.moveToNext()) { // check if marker exists
					{ // modify the latest one
						final Marker marker = markerFromCursor(c);
						marker.modifyTime = now;
						marker.caption = Highlights.encode(colorRgb, hashCode, startOffset, endOffset);
						db.update(Db.TABLE_Marker, markerToContentValues(marker), "_id=?", ToStringArray(marker._id));
					}

					// remove earlier ones if they exist (caused by sync)
					while (c.moveToNext()) {
						final long _id = c.getLong(c.getColumnIndexOrThrow("_id"));
						db.delete(Db.TABLE_Marker, "_id=?", ToStringArray(_id));
					}
				} else { // insert
					final Marker marker = Marker.createNewMarker(ari, Marker.Kind.highlight, Highlights.encode(colorRgb, hashCode, startOffset, endOffset), 1, now, now);
					db.insert(Db.TABLE_Marker, null, markerToContentValues(marker));
				}
			} finally {
				c.close();
			}
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}

		Sync.notifySyncNeeded(SyncShadow.SYNC_SET_MABEL);
	}

	public void updateOrInsertHighlights(int ari_bookchapter, IntArrayList selectedVerses_1, int colorRgb) {
		final SQLiteDatabase db = helper.getWritableDatabase();

		db.beginTransactionNonExclusive();
		try {
			final String[] params = ToStringArray(null /* for the ari */, Marker.Kind.highlight.code);

			// every requested verses
			for (int i = 0; i < selectedVerses_1.size(); i++) {
				final int ari = Ari.encodeWithBc(ari_bookchapter, selectedVerses_1.get(i));
				params[0] = String.valueOf(ari);

				// order by modifyTime desc so we modify the latest one and remove earlier ones if they exist.
				final Cursor c = db.query(Db.TABLE_Marker, null, Db.Marker.ari + "=? and " + Db.Marker.kind + "=?", params, null, null, Db.Marker.modifyTime + " desc");
				try {
					if (c.moveToNext()) { // check if marker exists
						{ // modify the latest one
							final Marker marker = markerFromCursor(c);
							marker.modifyTime = new Date();
							if (colorRgb != -1) {
								marker.caption = Highlights.encode(colorRgb);
								db.update(Db.TABLE_Marker, markerToContentValues(marker), "_id=?", ToStringArray(marker._id));
							} else {
								// delete entry
								db.delete(Db.TABLE_Marker, "_id=?", ToStringArray(marker._id));
							}
						}

						// remove earlier ones if they exist (caused by sync)
						while (c.moveToNext()) {
							final long _id = c.getLong(c.getColumnIndexOrThrow("_id"));
							db.delete(Db.TABLE_Marker, "_id=?", ToStringArray(_id));
						}
					} else {
						if (colorRgb == -1) {
							// no need to do, from no color to no color
						} else {
							final Date now = new Date();
							final Marker marker = Marker.createNewMarker(ari, Marker.Kind.highlight, Highlights.encode(colorRgb), 1, now, now);
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

		Sync.notifySyncNeeded(SyncShadow.SYNC_SET_MABEL);
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
			new String[]{String.valueOf(ariMin), String.valueOf(ariMax), String.valueOf(Marker.Kind.highlight.code)},
			null, null, null
		);

		try {
			final int col_ari = c.getColumnIndexOrThrow(Db.Marker.ari);
			final int col_caption = c.getColumnIndexOrThrow(Db.Marker.caption);

			// put to array first
			while (c.moveToNext()) {
				int ari = c.getInt(col_ari);
				int index = ari & 0xff;
				final Highlights.Info info = Highlights.decode(c.getString(col_caption));
				colors[index] = info.colorRgb;
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

	/**
	 * Get the highlight info for a single verse
	 */
	public Highlights.Info getHighlightColorRgb(final int ari) {
		try (Cursor c = helper.getReadableDatabase().query(
			Db.TABLE_Marker, null, Db.Marker.ari + "=? and " + Db.Marker.kind + "=?",
			ToStringArray(ari, Marker.Kind.highlight.code),
			null,
			null,
			Db.Marker.modifyTime + " desc"
		)) {
			final int col_caption = c.getColumnIndexOrThrow(Db.Marker.caption);

			// put to array first
			if (c.moveToNext()) {
				return Highlights.decode(c.getString(col_caption));
			} else {
				return null;
			}
		}
	}

	public void storeArticleToDevotions(DevotionArticle article) {
		final SQLiteDatabase db = helper.getWritableDatabase();

		final ContentValues values = new ContentValues();
		values.put(Table.Devotion.name.name(), article.getKind().name);
		values.put(Table.Devotion.date.name(), article.getDate());
		values.put(Table.Devotion.readyToUse.name(), article.getReadyToUse() ? 1 : 0);

		if (article.getReadyToUse()) {
			values.put(Table.Devotion.body.name(), article.getBody());
		} else {
			values.putNull(Table.Devotion.body.name());
		}

		values.put(Table.Devotion.touchTime.name(), Sqlitil.nowDateTime());
		values.put(Table.Devotion.dataFormatVersion.name(), 1);

		db.beginTransactionNonExclusive();
		try {
			// first delete the existing
			db.delete(Table.Devotion.tableName(), Table.Devotion.name + "=? and " + Table.Devotion.date + "=?", new String[]{article.getKind().name, article.getDate()});
			db.insert(Table.Devotion.tableName(), null, values);

			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

	public int deleteDevotionsWithTouchTimeBefore(Date date) {
		final SQLiteDatabase db = helper.getWritableDatabase();
		return db.delete(Table.Devotion.tableName(), Table.Devotion.touchTime + "<?", ToStringArray(Sqlitil.toInt(date)));
	}

	/**
	 * Try to get article from local db. Non ready-to-use article will be returned too.
	 */
	public DevotionArticle tryGetDevotion(String name, String date) {
		try (Cursor c = helper.getReadableDatabase().query(Table.Devotion.tableName(), null, Table.Devotion.name + "=? and " + Table.Devotion.date + "=? and " + Table.Devotion.dataFormatVersion + "=?", ToStringArray(name, date, 1), null, null, null)) {
			final int col_body = c.getColumnIndexOrThrow(Table.Devotion.body.name());
			final int col_readyToUse = c.getColumnIndexOrThrow(Table.Devotion.readyToUse.name());

			if (!c.moveToNext()) {
				return null;
			}

			final DevotionActivity.DevotionKind kind = DevotionActivity.DevotionKind.getByName(name);
			switch (kind) {
				case RH: {
					return new ArticleRenunganHarian(date, c.getString(col_body), c.getInt(col_readyToUse) > 0);
				}
				case SH: {
					return new ArticleSantapanHarian(date, c.getString(col_body), c.getInt(col_readyToUse) > 0);
				}
				case ME_EN: {
					return new ArticleMorningEveningEnglish(date, c.getString(col_body), true);
				}
				case MEID_A: {
					return new ArticleMeidA(date, c.getString(col_body), c.getInt(col_readyToUse) > 0);
				}
				case ROC: {
					return new ArticleRoc(date, c.getString(col_body), c.getInt(col_readyToUse) > 0);
				}
			}
		}

		throw new RuntimeException("Should not be reachable");
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
			int col_modifyTime = cursor.getColumnIndexOrThrow(Db.Version.modifyTime);
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
				mv.modifyTime = cursor.getInt(col_modifyTime);
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
		cv.put(Db.Version.active, active ? 1 : 0);

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

	/**
	 * If the filename of the inserted mv already exists in the table,
	 * update is performed instead of an insert.
	 * In that case, the mv.ordering will be changed to the one in the table,
	 * and the passed-in mv.ordering will not be used.
	 */
	public void insertOrUpdateVersionWithActive(MVersionDb mv, boolean active) {
		final SQLiteDatabase db = helper.getWritableDatabase();
		final ContentValues cv = new ContentValues();
		cv.put(Db.Version.locale, mv.locale);
		cv.put(Db.Version.shortName, mv.shortName);
		cv.put(Db.Version.longName, mv.longName);
		cv.put(Db.Version.description, mv.description);
		cv.put(Db.Version.filename, mv.filename);
		cv.put(Db.Version.preset_name, mv.preset_name);
		cv.put(Db.Version.modifyTime, mv.modifyTime);
		cv.put(Db.Version.active, active); // special
		cv.put(Db.Version.ordering, mv.ordering);

		db.beginTransactionNonExclusive();
		try { // prevent insert for the same filename (absolute path), update instead
			try (Cursor c = db.query(Db.TABLE_Version, Array("_id", Db.Version.ordering), Db.Version.filename + "=?", Array(mv.filename), null, null, null)) {
				if (c.moveToNext()) {
					final long _id = c.getLong(0);
					final int ordering = c.getInt(1);

					mv.ordering = ordering;
					cv.put(Db.Version.ordering, ordering);

					db.update(Db.TABLE_Version, cv, "_id=?", ToStringArray(_id));
				} else {
					db.insert(Db.TABLE_Version, null, cv);
				}
			}

			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
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
		List<Label> res = new ArrayList<>();
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

	public List<Marker_Label> listAllMarker_Labels() {
		final List<Marker_Label> res = new ArrayList<>();
		final Cursor cursor = helper.getReadableDatabase().query(Db.TABLE_Marker_Label, null, null, null, null, null, null);
		try {
			while (cursor.moveToNext()) {
				res.add(marker_LabelFromCursor(cursor));
			}
		} finally {
			cursor.close();
		}
		return res;
	}

	public List<Marker_Label> listMarker_LabelsByMarker(final Marker marker) {
		final List<Marker_Label> res = new ArrayList<>();
		final Cursor cursor = helper.getReadableDatabase().query(Db.TABLE_Marker_Label, null, Db.Marker_Label.marker_gid + "=?", ToStringArray(marker.gid), null, null, null);
		try {
			while (cursor.moveToNext()) {
				res.add(marker_LabelFromCursor(cursor));
			}
		} finally {
			cursor.close();
		}
		return res;
	}

	public List<Label> listLabelsByMarker(final Marker marker) {
		final List<Label> res = new ArrayList<>();
		final Cursor cursor = helper.getReadableDatabase().rawQuery("select " + Db.TABLE_Label + ".* from " + Db.TABLE_Label + ", " + Db.TABLE_Marker_Label + " where " + Db.TABLE_Marker_Label + "." + Db.Marker_Label.label_gid + " = " + Db.TABLE_Label + "." + Db.Label.gid + " and " + Db.TABLE_Marker_Label + "." + Db.Marker_Label.marker_gid + "=? order by " + Db.TABLE_Label + "." + Db.Label.ordering + " asc", Array(marker.gid));
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
	private static ContentValues labelToContentValues(Label label) {
		final ContentValues res = new ContentValues();

		res.put(Db.Label.gid, label.gid);
		res.put(Db.Label.title, label.title);
		res.put(Db.Label.ordering, label.ordering);
		res.put(Db.Label.backgroundColor, label.backgroundColor);

		return res;
	}

	/**
	 * _id is not stored
	 */
	@NonNull private static ContentValues marker_labelToContentValues(@NonNull Marker_Label marker_label) {
		final ContentValues res = new ContentValues();

		res.put(Db.Marker_Label.gid, marker_label.gid);
		res.put(Db.Marker_Label.marker_gid, marker_label.marker_gid);
		res.put(Db.Marker_Label.label_gid, marker_label.label_gid);

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
		Sync.notifySyncNeeded(SyncShadow.SYNC_SET_MABEL);
		return res;
	}

	public void updateLabels(final Marker marker, final Set<Label> newLabels) {
		final SQLiteDatabase db = helper.getWritableDatabase();

		db.beginTransactionNonExclusive();
		try {
			final List<Marker_Label> oldMls = listMarker_LabelsByMarker(marker);

			// helper list
			final List<String> oldMlLabelGids = new ArrayList<>();
			for (final Marker_Label oldMl : oldMls) {
				oldMlLabelGids.add(oldMl.label_gid);
			}


			// calculate labels to be added
			final List<Label> addLabels = new ArrayList<>();

			for (final Label newLabel : newLabels) {
				if (!oldMlLabelGids.contains(newLabel.gid)) {
					addLabels.add(newLabel);
				}
			}

			// calculate marker_labels to be removed
			final List<Marker_Label> removeMls = new ArrayList<>();
			{
				// helper list
				final List<String> newLabelGids = new ArrayList<>();
				for (final Label newLabel : newLabels) {
					newLabelGids.add(newLabel.gid);
				}

				for (int i = 0; i < oldMls.size(); i++) {
					final Marker_Label oldMl = oldMls.get(i);

					// look for duplicate labels
					if (oldMlLabelGids.subList(i + 1, oldMlLabelGids.size()).contains(oldMl.label_gid)) {
						removeMls.add(oldMl);
						continue;
					}

					// if the old one is not in the new ones
					if (!newLabelGids.contains(oldMl.label_gid)) {
						removeMls.add(oldMl);
					}
				}
			}

			// remove
			for (final Marker_Label removeMl : removeMls) {
				db.delete(Db.TABLE_Marker_Label, "_id=?", ToStringArray(removeMl._id));
			}

			// add
			for (final Label addLabel : addLabels) {
				final Marker_Label marker_label = Marker_Label.createNewMarker_Label(marker.gid, addLabel.gid);
				db.insert(Db.TABLE_Marker_Label, null, marker_labelToContentValues(marker_label));
			}

			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
		Sync.notifySyncNeeded(SyncShadow.SYNC_SET_MABEL);
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

    @Nullable public Label getLabelByGid(@NonNull final String gid) {
		final Cursor cursor = helper.getReadableDatabase().query(Db.TABLE_Label, null, Db.Label.gid + "=?", Array(gid), null, null, null);

		try {
			if (!cursor.moveToNext()) return null;
			return labelFromCursor(cursor);
		} finally {
			cursor.close();
		}
	}

    @Nullable public Marker_Label getMarker_LabelByGid(@NonNull final String gid) {
		final Cursor cursor = helper.getReadableDatabase().query(Db.TABLE_Marker_Label, null, Db.Marker_Label.gid + "=?", Array(gid), null, null, null);

		try {
			if (!cursor.moveToNext()) return null;
			return marker_LabelFromCursor(cursor);
		} finally {
			cursor.close();
		}
	}

	/** This is so special: delete label and the associated marker_labels */
	public void deleteLabelAndMarker_LabelsByLabelId(long _id) {
		final Label label = getLabelById(_id);
		final SQLiteDatabase db = helper.getWritableDatabase();
		db.beginTransactionNonExclusive();
		try {
			db.delete(Db.TABLE_Marker_Label, Db.Marker_Label.label_gid + "=?", new String[]{label.gid});
			db.delete(Db.TABLE_Label, "_id=?", new String[]{String.valueOf(_id)});
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
		Sync.notifySyncNeeded(SyncShadow.SYNC_SET_MABEL);
	}

	/**
	 * Insert a new label or update an existing label.
	 * @param label if the _id is 0, this label will be inserted. Otherwise, updated.
	 */
	public void insertOrUpdateLabel(@NonNull final Label label) {
		final SQLiteDatabase db = helper.getWritableDatabase();
		if (label._id != 0) {
			db.update(Db.TABLE_Label, labelToContentValues(label), "_id=?", Array(String.valueOf(label._id)));
		} else {
			label._id = db.insert(Db.TABLE_Label, null, labelToContentValues(label));
		}
		Sync.notifySyncNeeded(SyncShadow.SYNC_SET_MABEL);
	}

	/**
	 * Insert a new marker-label association or update an existing one.
	 * @param marker_label if the _id is 0, this label will be inserted. Otherwise, updated.
	 */
	public void insertOrUpdateMarker_Label(@NonNull final Marker_Label marker_label) {
		final SQLiteDatabase db = helper.getWritableDatabase();
		if (marker_label._id != 0) {
			db.update(Db.TABLE_Marker_Label, marker_labelToContentValues(marker_label), "_id=?", ToStringArray(marker_label._id));
		} else {
			marker_label._id = db.insert(Db.TABLE_Marker_Label, null, marker_labelToContentValues(marker_label));
		}
		Sync.notifySyncNeeded(SyncShadow.SYNC_SET_MABEL);
	}

	/** Used in migration from v3 */
	public static long insertMarker_LabelIfNotExists(final SQLiteDatabase db, final Marker_Label marker_label) {
		db.beginTransactionNonExclusive();
		try {
			final Cursor cursor = db.rawQuery("select _id from " + Db.TABLE_Marker_Label + " where " + Db.Marker_Label.marker_gid + "=? and " + Db.Marker_Label.label_gid + "=?", Array(marker_label.marker_gid, marker_label.label_gid));
			try {
				if (cursor.moveToNext()) {
					marker_label._id = cursor.getLong(0);
				} else {
					marker_label._id = db.insert(Db.TABLE_Marker_Label, null, marker_labelToContentValues(marker_label));
				}
			} finally {
				cursor.close();
			}
			db.setTransactionSuccessful();
		} finally {
            db.endTransaction();
		}

		Sync.notifySyncNeeded(SyncShadow.SYNC_SET_MABEL);

		return marker_label._id;
	}

	public int countMarkersWithLabel(Label label) {
		final SQLiteDatabase db = helper.getReadableDatabase();
		return (int) DatabaseUtils.longForQuery(db, "select count(*) from " + Db.TABLE_Marker_Label + " where " + Db.Marker_Label.label_gid + "=?", new String[]{label.gid});
	}

	public void sortLabelsAlphabetically() {
		final SQLiteDatabase db = helper.getWritableDatabase();
		db.beginTransactionNonExclusive();
		try {
			final List<Label> labels = listAllLabels();
			Collections.sort(labels, (lhs, rhs) -> {
				if (lhs.title == null || rhs.title == null) {
					return 0;
				}

				return lhs.title.compareToIgnoreCase(rhs.title);
			});

			for (int i = 0; i < labels.size(); i++) {
				final Label label = labels.get(i);
				label.ordering = i + 1;
				db.update(Db.TABLE_Label, labelToContentValues(label), "_id=?", ToStringArray(label._id));
			}

			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
		Sync.notifySyncNeeded(SyncShadow.SYNC_SET_MABEL);
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

		if (BuildConfig.DEBUG) {
			AppLog.d(TAG, "@@reorderLabels from _id=" + from._id + " ordering=" + from.ordering + " to _id=" + to._id + " ordering=" + to.ordering);
		}

		SQLiteDatabase db = helper.getWritableDatabase();
		db.beginTransactionNonExclusive();
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
		Sync.notifySyncNeeded(SyncShadow.SYNC_SET_MABEL);
	}

	public void reorderVersions(MVersion from, MVersion to) {
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

		if (BuildConfig.DEBUG) {
			AppLog.d(TAG, "@@reorderVersions from id=" + from.getVersionId() + " ordering=" + from.ordering + " to id=" + to.getVersionId() + " ordering=" + to.ordering);
		}

		SQLiteDatabase db = helper.getWritableDatabase();
		db.beginTransactionNonExclusive();
		try {
			{
				final int internal_ordering = Preferences.getInt(Prefkey.internal_version_ordering, MVersionInternal.DEFAULT_ORDERING);
				if (from.ordering > to.ordering) { // move up
					db.execSQL("update " + Db.TABLE_Version + " set " + Db.Version.ordering + "=(" + Db.Version.ordering + "+1) where ?<=" + Db.Version.ordering + " and " + Db.Version.ordering + "<?", new Object[]{to.ordering, from.ordering});
					if (to.ordering <= internal_ordering && internal_ordering < from.ordering) {
						Preferences.setInt(Prefkey.internal_version_ordering, internal_ordering + 1);
					}
				} else if (from.ordering < to.ordering) { // move down
					db.execSQL("update " + Db.TABLE_Version + " set " + Db.Version.ordering + "=(" + Db.Version.ordering + "-1) where ?<" + Db.Version.ordering + " and " + Db.Version.ordering + "<=?", new Object[]{from.ordering, to.ordering});
					if (from.ordering < internal_ordering && internal_ordering <= to.ordering) {
						Preferences.setInt(Prefkey.internal_version_ordering, internal_ordering - 1);
					}
				}
			}

			// both move up and move down arrives at this final step
			if (from instanceof MVersionDb) {
				db.execSQL("update " + Db.TABLE_Version + " set " + Db.Version.ordering + "=? where " + Db.Version.filename + "=?", new Object[]{to.ordering, ((MVersionDb) from).filename});
			} else if (from instanceof MVersionInternal) {
				Preferences.setInt(Prefkey.internal_version_ordering, to.ordering);
			}

			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

	/**
	 * Lists all progress marks that are not empty.
	 * (Empty ones will have an ari of 0. They will be excluded.)
	 */
	public List<ProgressMark> listAllProgressMarks() {
		final List<ProgressMark> res = new ArrayList<>();
		final Cursor cursor = helper.getReadableDatabase().query(Db.TABLE_ProgressMark, null, Db.ProgressMark.ari + " != 0", null, null, null, null);
		try {
			while (cursor.moveToNext()) {
				res.add(progressMarkFromCursor(cursor));
			}
		} finally {
			cursor.close();
		}

		return res;
	}

	/**
	 * Count the number of progress marks that are not empty.
	 * (Empty ones will have an ari of 0. They will be excluded.)
	 */
	public int countAllProgressMarks() {
		return (int) DatabaseUtils.queryNumEntries(helper.getReadableDatabase(), Db.TABLE_ProgressMark, Db.ProgressMark.ari + " != 0");
	}

	@Nullable public ProgressMark getProgressMarkByPresetId(final int preset_id) {
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

	/**
	 * Insert a new progress mark (if preset_id is not found), or update an existing progress mark.
	 */
	public void insertOrUpdateProgressMark(@NonNull final ProgressMark progressMark) {
		final SQLiteDatabase db = helper.getWritableDatabase();

		final ContentValues cv = new ContentValues();
		cv.put(Db.ProgressMarkHistory.progress_mark_preset_id, progressMark.preset_id);
		cv.put(Db.ProgressMarkHistory.progress_mark_caption, progressMark.caption);
		cv.put(Db.ProgressMarkHistory.ari, progressMark.ari);
		cv.put(Db.ProgressMarkHistory.createTime, Sqlitil.toInt(progressMark.modifyTime));

		db.beginTransactionNonExclusive();
		try {
			// the progress mark history first
			db.insert(Db.TABLE_ProgressMarkHistory, null, cv);

			final long count = DatabaseUtils.queryNumEntries(db, Db.TABLE_ProgressMark, Db.ProgressMark.preset_id + "=?", ToStringArray(progressMark.preset_id));
			if (count > 0) {
				db.update(Db.TABLE_ProgressMark, progressMarkToContentValues(progressMark), Db.ProgressMark.preset_id + "=?", ToStringArray(progressMark.preset_id));
			} else {
				db.insert(Db.TABLE_ProgressMark, null, progressMarkToContentValues(progressMark));
			}

			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}

		Sync.notifySyncNeeded(SyncShadow.SYNC_SET_PINS);
	}

	public List<ProgressMarkHistory> listProgressMarkHistoryByPresetId(final int preset_id) {
		final Cursor c = helper.getReadableDatabase().rawQuery("select * from " + Db.TABLE_ProgressMarkHistory + " where " + Db.ProgressMarkHistory.progress_mark_preset_id + "=? order by " + Db.ProgressMarkHistory.createTime + " asc", new String[]{String.valueOf(preset_id)});
		try {
			final List<ProgressMarkHistory> res = new ArrayList<>();
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
		final long res = helper.getWritableDatabase().insert(Db.TABLE_ReadingPlan, null, cv);

		// this adds the 'startTime' attribute to the sync entity (when any of the rp progress has been checked)
		Sync.notifySyncNeeded(SyncShadow.SYNC_SET_RP);

		return res;
	}

	public void insertOrUpdateReadingPlanProgress(final String gid, final int readingCode, final long checkTime) {
		final SQLiteDatabase db = helper.getWritableDatabase();
		db.beginTransactionNonExclusive();
		try {
			db.delete(Db.TABLE_ReadingPlanProgress, Db.ReadingPlanProgress.reading_plan_progress_gid + "=? and " + Db.ReadingPlanProgress.reading_code + "=?", ToStringArray(gid, readingCode));

			final ContentValues cv = new ContentValues();
			cv.put(Db.ReadingPlanProgress.reading_plan_progress_gid, gid);
			cv.put(Db.ReadingPlanProgress.reading_code, readingCode);
			cv.put(Db.ReadingPlanProgress.checkTime, checkTime);
			db.insert(Db.TABLE_ReadingPlanProgress, null, cv);

			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}

		Sync.notifySyncNeeded(SyncShadow.SYNC_SET_RP);
	}

	public void insertOrUpdateMultipleReadingPlanProgresses(final String gid, final IntArrayList readingCodes, final long checkTime) {
		final SQLiteDatabase db = helper.getWritableDatabase();
		db.beginTransactionNonExclusive();
		try {
			final ContentValues cv = new ContentValues();
			cv.put(Db.ReadingPlanProgress.reading_plan_progress_gid, gid);
			cv.put(Db.ReadingPlanProgress.checkTime, checkTime);

			for (int i = 0, len = readingCodes.size(); i < len; i++) {
				final int readingCode = readingCodes.get(i);

				db.delete(Db.TABLE_ReadingPlanProgress, Db.ReadingPlanProgress.reading_plan_progress_gid + "=? and " + Db.ReadingPlanProgress.reading_code + "=?", ToStringArray(gid, readingCode));

				// specific update
				cv.put(Db.ReadingPlanProgress.reading_code, readingCode);

				db.insert(Db.TABLE_ReadingPlanProgress, null, cv);
			}

			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}

		Sync.notifySyncNeeded(SyncShadow.SYNC_SET_RP);
	}

	public void deleteReadingPlanProgress(final String gid, final int readingCode) {
		helper.getWritableDatabase().delete(Db.TABLE_ReadingPlanProgress, Db.ReadingPlanProgress.reading_plan_progress_gid + "=? and " + Db.ReadingPlanProgress.reading_code + "=?", ToStringArray(gid, readingCode));

		Sync.notifySyncNeeded(SyncShadow.SYNC_SET_RP);
	}

	public void deleteAllReadingPlanProgressForGid(final String gid) {
		helper.getWritableDatabase().delete(Db.TABLE_ReadingPlanProgress, Db.ReadingPlanProgress.reading_plan_progress_gid + "=?", Array(gid));

		Sync.notifySyncNeeded(SyncShadow.SYNC_SET_RP);
	}

	/**
	 * Get the list of reading plan gid with their done reading codes.
	 * The only source of data is from ReadingPlanProgress table, but since reading plans with no done is not listed in ReadingPlanProgress,
	 * please take care of it.
	 */
	public Map<String /* gid */, TIntSet /* done reading codes */> getReadingPlanProgressSummaryForSync() {
		final SQLiteDatabase db = helper.getReadableDatabase();
		final Map<String, TIntSet> res = new HashMap<>();
		try (Cursor c = db.query(Db.TABLE_ReadingPlanProgress, Array(Db.ReadingPlanProgress.reading_plan_progress_gid, Db.ReadingPlanProgress.reading_code), null, null, null, null, null)) {
			while (c.moveToNext()) {
				final String gid = c.getString(0);
				final int readingCode = c.getInt(1);

				TIntSet set = res.get(gid);
				if (set == null) {
					set = new TIntHashSet();
					res.put(gid, set);
				}

				set.add(readingCode);
			}
		}

		return res;
	}

	public List<ReadingPlan.ReadingPlanInfo> listAllReadingPlanInfo() {
		final Cursor c = helper.getReadableDatabase().query(Db.TABLE_ReadingPlan,
		new String[] {"_id", Db.ReadingPlan.version, Db.ReadingPlan.name, Db.ReadingPlan.title, Db.ReadingPlan.description, Db.ReadingPlan.duration, Db.ReadingPlan.startTime},
		null, null, null, null, null);
		List<ReadingPlan.ReadingPlanInfo> infos = new ArrayList<>();
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

	public Pair<String, byte[]> getReadingPlanNameAndData(long _id) {
		final Cursor c = helper.getReadableDatabase().query(Db.TABLE_ReadingPlan, Array(Db.ReadingPlan.name, Db.ReadingPlan.data), "_id=?", ToStringArray(_id), null, null, null);
		try {
			if (c.moveToNext()) {
				return Pair.create(c.getString(0), c.getBlob(1));
			}
			return null;
		} finally {
			c.close();
		}
	}

	public IntArrayList getAllReadingCodesByReadingPlanProgressGid(final String gid) {
		IntArrayList res = new IntArrayList();
		try (Cursor c = helper.getReadableDatabase().query(
			Db.TABLE_ReadingPlanProgress,
			Array(Db.ReadingPlanProgress.reading_code),
			Db.ReadingPlanProgress.reading_plan_progress_gid + "=?",
			Array(gid),
			null,
			null,
			Db.ReadingPlanProgress.reading_code + " asc"
		)) {
			while (c.moveToNext()) {
				res.add(c.getInt(0));
			}
		}
		return res;
	}

	/**
	 * Deletes the reading plan, but not the progress.
	 * The progress will be kept, so it is not considered as deleted during sync.
	 */
	public void deleteReadingPlanById(long id) {
		helper.getWritableDatabase().delete(Db.TABLE_ReadingPlan, "_id=?", ToStringArray(id));

		// this removes the 'startTime' attribute from the sync entity
		Sync.notifySyncNeeded(SyncShadow.SYNC_SET_RP);
	}

	public void updateReadingPlanStartDate(long id, long startDate) {
		final ContentValues cv = new ContentValues();
		cv.put(Db.ReadingPlan.startTime, startDate);
		helper.getWritableDatabase().update(Db.TABLE_ReadingPlan, cv, "_id=?", ToStringArray(id));

		Sync.notifySyncNeeded(SyncShadow.SYNC_SET_RP);
	}

	public List<String> listReadingPlanNames() {
		final List<String> res = new ArrayList<>();
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

	@Nullable public SyncShadow getSyncShadowBySyncSetName(final String syncSetName) {
		// Getting a sync shadow that has a size bigger than 2 MB will cause crash,
		// because of system CursorWindow implementation that sets the max memory allocated
		// to be 2 MB, as defined in system resource:
		// <integer name="config_cursorWindowSize">2048</integer>
		// So we will get the size first, and then allocate memory,
		// and get the data in chunks.
		final SQLiteDatabase db = helper.getReadableDatabase();
		db.beginTransactionNonExclusive();
		try {
			final int data_len;
			final long _id;
			final int revno;

			{ // get blob len
				final Cursor c = db.rawQuery(
					"select "
						+ Table.SyncShadow.revno.name() + ", " // col 0
						+ "length(" + Table.SyncShadow.data.name() + "), " // col 1
						+ "_id " // col 2
						+ " from " + Table.SyncShadow.tableName()
						+ " where " + Table.SyncShadow.syncSetName + "=?",
					Array(syncSetName)
				);
				try {
					if (c.moveToNext()) {
						revno = c.getInt(0);
						data_len = c.getInt(1);
						_id = c.getLong(2);
					} else {
						return null;
					}
				} finally {
					c.close();
				}
			}

			final byte[] data = new byte[data_len];

			{ // fill in blob
				final int chunkSize = 1000_000;
				for (int i = 0; i < data_len; i += chunkSize) {
					final Cursor c = db.rawQuery(
						// sqlite substr func is 1-indexed
						"select "
							+ "substr(" + Table.SyncShadow.data.name() + ", " + (i + 1) + ", " + chunkSize + ")" // col 0
							+ " from " + Table.SyncShadow.tableName()
							+ " where _id=?",
						ToStringArray(_id)
					);

					try {
						if (c.moveToNext()) {
							final byte[] chunk = c.getBlob(0);
							if (i + chunk.length != data_len) {
								// not the last one
								if (chunk.length != chunkSize) {
									throw new RuntimeException("Not the requested size of chunk retrieved. data_len=" + data_len + " i=" + i + " chunk.len=" + chunk.length);
								}
								System.arraycopy(chunk, 0, data, i, chunkSize);
							} else {
								// the last one
								System.arraycopy(chunk, 0, data, i, chunk.length);
							}
						} else {
							throw new RuntimeException("Cursor moveToNext returns false, does not make sense, since previous query has indicated that this cursor has rows.");
						}
					} finally {
						c.close();
					}
				}
			}

			db.setTransactionSuccessful();

			final SyncShadow res = new SyncShadow();
			res.syncSetName = syncSetName;
			res.revno = revno;
			res.data = data;
			return res;
		} finally {
			db.endTransaction();
		}
	}

	public int getRevnoFromSyncShadowBySyncSetName(final String syncSetName) {
		final SQLiteDatabase db = helper.getReadableDatabase();
		final Cursor c = db.query(Table.SyncShadow.tableName(), Array(
			Table.SyncShadow.revno.name()
		), Table.SyncShadow.syncSetName + "=?", Array(syncSetName), null, null, null);
		try {
			if (c.moveToNext()) {
				return c.getInt(0);
			}
		} finally {
			c.close();
		}
		return 0;
	}

	@NonNull private static ContentValues syncShadowToContentValues(@NonNull final SyncShadow ss) {
		final ContentValues res = new ContentValues();
		res.put(Table.SyncShadow.syncSetName.name(), ss.syncSetName);
		res.put(Table.SyncShadow.revno.name(), ss.revno);
		res.put(Table.SyncShadow.data.name(), ss.data);
		return res;
	}

	/**
	 * Create or update a sync shadow, based on the sync set name.
	 * @param ss if the {@link yuku.alkitab.base.model.SyncShadow#syncSetName} is already on the database, this method will replace it. Otherwise, this method will insert a new one.
	 */
	public void insertOrUpdateSyncShadowBySyncSetName(@NonNull final SyncShadow ss) {
		final SQLiteDatabase db = helper.getWritableDatabase();
		db.beginTransactionNonExclusive();
		try {
			final long count = DatabaseUtils.queryNumEntries(db, Table.SyncShadow.tableName(), Table.SyncShadow.syncSetName + "=?", Array(ss.syncSetName));
			if (count > 0) {
				db.update(Table.SyncShadow.tableName(), syncShadowToContentValues(ss), Table.SyncShadow.syncSetName + "=?", Array(ss.syncSetName));
			} else {
				db.insert(Table.SyncShadow.tableName(), null, syncShadowToContentValues(ss));
			}
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

	public void deleteSyncShadowBySyncSetName(final String syncSetName) {
		final SQLiteDatabase db = helper.getWritableDatabase();
		db.delete(Table.SyncShadow.tableName(), Table.SyncShadow.syncSetName + "=?", Array(syncSetName));
	}

	/**
	 * Makes the current database updated with patches (append delta) from server.
	 * Also updates the shadow (both data and the revno).
	 * @return {@link yuku.alkitab.base.sync.Sync.ApplyAppendDeltaResult#ok} if database and sync shadow are updated. Otherwise else.
	 */
	@NonNull public Sync.ApplyAppendDeltaResult applyMabelAppendDelta(final int final_revno, final List<Sync.Entity<Sync_Mabel.Content>> shadowEntities, final Sync.ClientState<Sync_Mabel.Content> clientState, @NonNull final Sync.Delta<Sync_Mabel.Content> append_delta, @NonNull final List<Sync.Entity<Sync_Mabel.Content>> entitiesBeforeSync, @NonNull final String simpleTokenBeforeSync) {
		final SQLiteDatabase db = helper.getWritableDatabase();
		db.beginTransactionNonExclusive();
		Sync.notifySyncUpdatesOngoing(SyncShadow.SYNC_SET_MABEL, true);
		try {
			{ // if the current entities are not the same as the ones had when contacting server, reject this append delta.
				final List<Sync.Entity<Sync_Mabel.Content>> currentEntities = Sync_Mabel.getEntitiesFromCurrent();
				if (!Sync.entitiesEqual(currentEntities, entitiesBeforeSync)) {
					return Sync.ApplyAppendDeltaResult.dirty_entities;
				}
			}

			{ // if the current simpleToken has changed (sync user logged off or changed), reject this append delta
				final String simpleToken = Preferences.getString(Prefkey.sync_simpleToken);
				if (!U.equals(simpleToken, simpleTokenBeforeSync)) {
					return Sync.ApplyAppendDeltaResult.dirty_sync_account;
				}
			}

			// apply changes, which is server append delta, to current entities
			for (final Sync.Operation<Sync_Mabel.Content> o : append_delta.operations) {
				switch (o.opkind) {
					case del:
						switch (o.kind) {
							case Sync.Entity.KIND_MARKER:
								deleteMarkerByGid(o.gid);
								break;
							case Sync.Entity.KIND_LABEL:
								deleteLabelByGid(o.gid);
								break;
							case Sync.Entity.KIND_MARKER_LABEL:
								deleteMarker_LabelByGid(o.gid);
								break;
							default:
								return Sync.ApplyAppendDeltaResult.unknown_kind;
						}
						break;
					case add:
					case mod:
						switch (o.kind) {
							case Sync.Entity.KIND_MARKER:
								final Marker marker = getMarkerByGid(o.gid);
								final Marker newMarker = Sync_Mabel.updateMarkerWithEntityContent(marker, o.gid, o.content);
								insertOrUpdateMarker(newMarker);
								break;
							case Sync.Entity.KIND_LABEL:
								final Label label = getLabelByGid(o.gid);
								final Label newLabel = Sync_Mabel.updateLabelWithEntityContent(label, o.gid, o.content);
								insertOrUpdateLabel(newLabel);
								break;
							case Sync.Entity.KIND_MARKER_LABEL:
								final Marker_Label marker_label = getMarker_LabelByGid(o.gid);
								final Marker_Label newMarker_label = Sync_Mabel.updateMarker_LabelWithEntityContent(marker_label, o.gid, o.content);
								insertOrUpdateMarker_Label(newMarker_label);
								break;
							default:
								return Sync.ApplyAppendDeltaResult.unknown_kind;
						}
						break;
				}
			}

			// if we reach here, the current entities has been updated with the append delta.

			// apply changes, which are client delta, and server append delta, to shadow entities
			final List<Sync.Entity<Sync_Mabel.Content>> shadowEntitiesPatched1 = SyncAdapter.patchNoConflict(shadowEntities, clientState.delta.operations);
			final List<Sync.Entity<Sync_Mabel.Content>> shadowEntitiesPatched2 = SyncAdapter.patchNoConflict(shadowEntitiesPatched1, append_delta.operations);

			final SyncShadow ss = Sync_Mabel.shadowFromEntities(shadowEntitiesPatched2, final_revno);
			insertOrUpdateSyncShadowBySyncSetName(ss);

			db.setTransactionSuccessful();

			return Sync.ApplyAppendDeltaResult.ok;
		} finally {
			Sync.notifySyncUpdatesOngoing(SyncShadow.SYNC_SET_MABEL, false);
			db.endTransaction();
		}
	}

	/**
	 * Makes the current database updated with patches (append delta) from server.
	 * Also updates the shadow (both data and the revno).
	 * @return {@link yuku.alkitab.base.sync.Sync.ApplyAppendDeltaResult#ok} if database and sync shadow are updated. Otherwise else.
	 */
	@NonNull public Sync.ApplyAppendDeltaResult applyPinsAppendDelta(final int final_revno, @NonNull final Sync.Delta<Sync_Pins.Content> append_delta, @NonNull final List<Sync.Entity<Sync_Pins.Content>> entitiesBeforeSync, @NonNull final String simpleTokenBeforeSync) {
		final SQLiteDatabase db = helper.getWritableDatabase();
		db.beginTransactionNonExclusive();
		Sync.notifySyncUpdatesOngoing(SyncShadow.SYNC_SET_PINS, true);
		try {
			{ // if the current entities are not the same as the ones had when contacting server, reject this append delta.
				final List<Sync.Entity<Sync_Pins.Content>> currentEntities = Sync_Pins.getEntitiesFromCurrent();
				if (!Sync.entitiesEqual(currentEntities, entitiesBeforeSync)) {
					return Sync.ApplyAppendDeltaResult.dirty_entities;
				}
			}

			{ // if the current simpleToken has changed (sync user logged off or changed), reject this append delta
				final String simpleToken = Preferences.getString(Prefkey.sync_simpleToken);
				if (!U.equals(simpleToken, simpleTokenBeforeSync)) {
					return Sync.ApplyAppendDeltaResult.dirty_sync_account;
				}
			}

			for (final Sync.Operation<Sync_Pins.Content> o : append_delta.operations) {
				switch (o.opkind) {
					case del:
					case add:
						return Sync.ApplyAppendDeltaResult.unsupported_operation;
					case mod:
						switch (o.kind) {
							case Sync.Entity.KIND_PINS: {
								// the whole logic to update all pins with the ones received from server (all pins in one entity)
								final Sync_Pins.Content content = o.content;
								final List<Sync_Pins.Content.Pin> pins = content.pins;

								for (final Sync_Pins.Content.Pin pin : pins) {
									final int preset_id = pin.preset_id;

									ProgressMark pm = getProgressMarkByPresetId(preset_id);
									if (pm == null) {
										pm = new ProgressMark();
										pm.preset_id = pin.preset_id;
									}
									pm.ari = pin.ari;
									pm.caption = pin.caption;
									pm.modifyTime = Sqlitil.toDate(pin.modifyTime);
									insertOrUpdateProgressMark(pm);
								}
							} break;
							default:
								return Sync.ApplyAppendDeltaResult.unknown_kind;
						}
						break;
				}
			}

			// if we reach here, the local database has been updated with the append delta.
			final SyncShadow ss = Sync_Pins.shadowFromEntities(Sync_Pins.getEntitiesFromCurrent(), final_revno);
			insertOrUpdateSyncShadowBySyncSetName(ss);

			db.setTransactionSuccessful();

			return Sync.ApplyAppendDeltaResult.ok;
		} finally {
			Sync.notifySyncUpdatesOngoing(SyncShadow.SYNC_SET_PINS, false);
			db.endTransaction();
		}
	}

	/**
	 * Makes the current database updated with patches (append delta) from server.
	 * Also updates the shadow (both data and the revno).
	 * @return {@link yuku.alkitab.base.sync.Sync.ApplyAppendDeltaResult#ok} if database and sync shadow are updated. Otherwise else.
	 */
	@NonNull public Sync.ApplyAppendDeltaResult applyRpAppendDelta(final int final_revno, @NonNull final Sync.Delta<Sync_Rp.Content> append_delta, @NonNull final List<Sync.Entity<Sync_Rp.Content>> entitiesBeforeSync, @NonNull final String simpleTokenBeforeSync) {
		final SQLiteDatabase db = helper.getWritableDatabase();
		db.beginTransactionNonExclusive();
		Sync.notifySyncUpdatesOngoing(SyncShadow.SYNC_SET_RP, true);
		try {
			{ // if the current entities are not the same as the ones had when contacting server, reject this append delta.
				final List<Sync.Entity<Sync_Rp.Content>> currentEntities = Sync_Rp.getEntitiesFromCurrent();
				if (!Sync.entitiesEqual(currentEntities, entitiesBeforeSync)) {
					return Sync.ApplyAppendDeltaResult.dirty_entities;
				}
			}

			{ // if the current simpleToken has changed (sync user logged off or changed), reject this append delta
				final String simpleToken = Preferences.getString(Prefkey.sync_simpleToken);
				if (!U.equals(simpleToken, simpleTokenBeforeSync)) {
					return Sync.ApplyAppendDeltaResult.dirty_sync_account;
				}
			}

			for (final Sync.Operation<Sync_Rp.Content> o : append_delta.operations) {
				if (!U.equals(o.kind, Sync.Entity.KIND_RP_PROGRESS)) {
					return Sync.ApplyAppendDeltaResult.unknown_kind;
				}

				switch (o.opkind) {
					case del: {
						db.delete(Db.TABLE_ReadingPlanProgress, Db.ReadingPlanProgress.reading_plan_progress_gid + "=?", Array(o.gid));
					} break;
					case add:
					case mod: {
						// the whole logic to update all pins with the ones received from server (all pins in one entity)
						final Sync_Rp.Content content = o.content;
						final IntArrayList readingCodes = getAllReadingCodesByReadingPlanProgressGid(o.gid);
						final TIntHashSet src = new TIntHashSet(readingCodes.size()); // our source (the current 'done' list)
						for (int i = 0, len = readingCodes.size(); i < len; i++) {
							src.add(readingCodes.get(i));
						}
						final TIntHashSet dst = new TIntHashSet(content.done); // our destination (want to be like this)

						{ // deletions
							final TIntHashSet to_del = new TIntHashSet(src);
							to_del.removeAll(dst);
							to_del.forEach(value -> {
								db.delete(Db.TABLE_ReadingPlanProgress, Db.ReadingPlanProgress.reading_plan_progress_gid + "=? and " + Db.ReadingPlanProgress.reading_code + "=?", ToStringArray(o.gid, value));
								return true;
							});
						}

						{ // additions
							final TIntHashSet to_add = new TIntHashSet(dst);
							to_add.removeAll(src);

							// unchanging properties
							final ContentValues cv = new ContentValues();
							cv.put(Db.ReadingPlanProgress.reading_plan_progress_gid, o.gid);
							cv.put(Db.ReadingPlanProgress.checkTime, System.currentTimeMillis());

							to_add.forEach(value -> {
								cv.put(Db.ReadingPlanProgress.reading_code, value);
								helper.getWritableDatabase().insert(Db.TABLE_ReadingPlanProgress, null, cv);
								return true;
							});
						}

						// update startTime
						if (content.startTime != null) {
							for (final ReadingPlan.ReadingPlanInfo info : listAllReadingPlanInfo()) {
								if (U.equals(ReadingPlan.gidFromName(info.name), o.gid)) {
									if (info.startTime != content.startTime) {
										final ContentValues cv = new ContentValues();
										cv.put(Db.ReadingPlan.startTime, content.startTime);
										db.update(Db.TABLE_ReadingPlan, cv, "_id=?", ToStringArray(info.id));
									}
									break;
								}
							}
						}
					} break;
				}
			}

			// if we reach here, the local database has been updated with the append delta.
			final SyncShadow ss = Sync_Rp.shadowFromEntities(Sync_Rp.getEntitiesFromCurrent(), final_revno);
			insertOrUpdateSyncShadowBySyncSetName(ss);

			db.setTransactionSuccessful();

			return Sync.ApplyAppendDeltaResult.ok;
		} finally {
			Sync.notifySyncUpdatesOngoing(SyncShadow.SYNC_SET_RP, false);
			db.endTransaction();
		}
	}

	/**
	 * Deletes a marker by gid.
	 * @return true when deleted.
	 */
	public boolean deleteMarkerByGid(final String gid) {
		final boolean deleted = helper.getWritableDatabase().delete(Db.TABLE_Marker, Db.Marker.gid + "=?", Array(gid)) > 0;
		if (deleted) {
			Sync.notifySyncNeeded(SyncShadow.SYNC_SET_MABEL);
		}
		return deleted;
	}

	/**
	 * Deletes a label by gid.
	 * @return true when deleted.
	 */
	public boolean deleteLabelByGid(final String gid) {
		final boolean deleted = helper.getWritableDatabase().delete(Db.TABLE_Label, Db.Label.gid + "=?", Array(gid)) > 0;
		if (deleted) {
			Sync.notifySyncNeeded(SyncShadow.SYNC_SET_MABEL);
		}
		return deleted;
	}

	/**
	 * Deletes a marker-label association by gid.
	 * @return true when deleted.
	 */
	public boolean deleteMarker_LabelByGid(final String gid) {
		final boolean deleted = helper.getWritableDatabase().delete(Db.TABLE_Marker_Label, Db.Marker_Label.gid + "=?", Array(gid)) > 0;
		if (deleted) {
			Sync.notifySyncNeeded(SyncShadow.SYNC_SET_MABEL);
		}
		return deleted;
	}

	public void insertSyncLog(final int createTime, final SyncRecorder.EventKind kind, final String syncSetName, final String params) {
		final ContentValues cv = new ContentValues(4);
		cv.put(Table.SyncLog.createTime.name(), createTime);
		cv.put(Table.SyncLog.kind.name(), kind.code);
		cv.put(Table.SyncLog.syncSetName.name(), syncSetName);
		cv.put(Table.SyncLog.params.name(), params);
		helper.getWritableDatabase().insert(Table.SyncLog.tableName(), null, cv);
	}

	public List<SyncLog> listLatestSyncLog(final int maxrows) {
		final Cursor c = helper.getReadableDatabase().query(Table.SyncLog.tableName(),
			ToStringArray(
				Table.SyncLog.createTime,
				Table.SyncLog.kind,
				Table.SyncLog.syncSetName,
				Table.SyncLog.params
			),
			null, null, null, null, Table.SyncLog.createTime + " desc", "" + maxrows);
		try {
			final List<SyncLog> res = new ArrayList<>();
			while (c.moveToNext()) {
				final SyncLog row = new SyncLog();
				row.createTime = Sqlitil.toDate(c.getInt(0));
				row.kind_code = c.getInt(1);
				row.syncSetName = c.getString(2);
				final String params_s = c.getString(3);
				if (params_s == null) {
					row.params = null;
				} else {
					row.params = App.getDefaultGson().fromJson(params_s, new TypeToken<Map<String, Object>>() {}.getType());
				}
				res.add(row);
			}
			return res;
		} finally {
			c.close();
		}
	}

	@NonNull public PerVersionSettings getPerVersionSettings(@NonNull final String versionId) {
		try (Cursor c = helper.getReadableDatabase().query(Table.PerVersion.tableName(), ToStringArray(Table.PerVersion.settings), Table.PerVersion.versionId + "=?", Array(versionId), null, null, null)) {
			if (c.moveToNext()) {
				return App.getDefaultGson().fromJson(c.getString(0), PerVersionSettings.class);
			} else {
				return PerVersionSettings.createDefault();
			}
		}
	}

	public void storePerVersionSettings(@NonNull final String versionId, @NonNull PerVersionSettings settings) {
		final ContentValues cv = new ContentValues();
		cv.put(Table.PerVersion.versionId.name(), versionId);
		cv.put(Table.PerVersion.settings.name(), App.getDefaultGson().toJson(settings));

		helper.getWritableDatabase().replace(Table.PerVersion.tableName(), null, cv);
	}

	// Do not use this except in rare circumstances
	public SQLiteDatabase getWritableDatabase() {
		return helper.getWritableDatabase();
	}
}
