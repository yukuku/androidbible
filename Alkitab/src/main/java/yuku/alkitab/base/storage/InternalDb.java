package yuku.alkitab.base.storage;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.ac.MarkerListActivity;
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
import static yuku.alkitab.base.util.Literals.Array;
import static yuku.alkitab.base.util.Literals.ToStringArray;
import yuku.alkitab.base.util.Sqlitil;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.model.Label;
import yuku.alkitab.model.Marker;
import yuku.alkitab.model.Marker_Label;
import yuku.alkitab.model.ProgressMark;
import yuku.alkitab.model.ProgressMarkHistory;
import yuku.alkitab.util.Ari;
import yuku.alkitab.util.IntArrayList;

public class InternalDb {
    static final String TAG = InternalDb.class.getSimpleName();

    final InternalDbHelper helper;
    public final VersionDao versionDao;
    public final ProgressMarkDao progressMarkDao;
    public final PerVersionDao perVersionDao;
    public final DevotionDao devotionDao;
    public final LabelDao labelDao;
    public final Marker_LabelDao marker_LabelDao;
    public final ReadingPlanDao readingPlanDao;
    public final MarkerDao markerDao;
    public final SyncShadowDao syncShadowDao;

    public InternalDb(InternalDbHelper helper) {
        this.helper = helper;
        this.versionDao = new VersionDao(helper);
        this.progressMarkDao = new ProgressMarkDao(helper);
        this.perVersionDao = new PerVersionDao(helper);
        this.devotionDao = new DevotionDao(helper);
        this.labelDao = new LabelDao(helper);
        this.marker_LabelDao = new Marker_LabelDao(helper);
        this.readingPlanDao = new ReadingPlanDao(helper);
        this.markerDao = new MarkerDao(helper);
        this.syncShadowDao = new SyncShadowDao(helper);
    }

    public Marker getMarkerById(long _id) {
        return markerDao.getById(_id);
    }

    @Nullable
    public Marker getMarkerByGid(@NonNull final String gid) {
        return markerDao.getByGid(gid);
    }

    /**
     * Ordered by modified time, the newest is first.
     */
    public List<Marker> listMarkersForAriKind(final int ari, final Marker.Kind kind) {
        return markerDao.listForAriKind(ari, kind);
    }

    /**
     * Insert a new marker or update an existing marker.
     *
     * @param marker if the _id is 0, this marker will be inserted. Otherwise, updated.
     */
    public void insertOrUpdateMarker(@NonNull final Marker marker) {
        markerDao.upsert(marker);
        Sync.notifySyncNeeded(SyncShadow.SYNC_SET_MABEL);
    }

    public Marker insertMarker(int ari, Marker.Kind kind, String caption, int verseCount, Date createTime, Date modifyTime) {
        final Marker res = markerDao.insertNew(ari, kind, caption, verseCount, createTime, modifyTime);
        Sync.notifySyncNeeded(SyncShadow.SYNC_SET_MABEL);
        return res;
    }

    public void deleteMarkerById(long _id) {
        final Marker marker = markerDao.getById(_id);

        final SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransactionNonExclusive();
        try {
            marker_LabelDao.deleteByMarkerGid(marker.gid);
            markerDao.deleteById(_id);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        Sync.notifySyncNeeded(SyncShadow.SYNC_SET_MABEL);
    }

    public void deleteNonBookmarkMarkerById(long _id) {
        markerDao.deleteById(_id);
        Sync.notifySyncNeeded(SyncShadow.SYNC_SET_MABEL);
    }

    public List<Marker> listMarkers(Marker.Kind kind, long label_id, String sortColumn, boolean sortAscending) {
        final SQLiteDatabase db = helper.getReadableDatabase();
        final String sortClause = sortColumn + (Db.Marker.caption.equals(sortColumn) ? " collate NOCASE " : "") + (sortAscending ? " asc" : " desc");

        final List<Marker> res = new ArrayList<>();
        final Cursor c;
        if (label_id == 0) { // no restrictions
            c = db.query(Db.TABLE_Marker, null, Db.Marker.kind + "=?", new String[]{String.valueOf(kind.code)}, null, null, sortClause);
        } else if (label_id == MarkerListActivity.LABELID_noLabel) { // only without label
            c = db.rawQuery("select " + Db.TABLE_Marker + ".* from " + Db.TABLE_Marker + " where " + Db.TABLE_Marker + "." + Db.Marker.kind + "=? and " + Db.TABLE_Marker + "." + Db.Marker.gid + " not in (select distinct " + Db.Marker_Label.marker_gid + " from " + Db.TABLE_Marker_Label + ") order by " + Db.TABLE_Marker + "." + sortClause, new String[]{String.valueOf(kind.code)});
        } else { // filter by label_id
            final Label label = getLabelById(label_id);
            c = db.rawQuery("select " + Db.TABLE_Marker + ".* from " + Db.TABLE_Marker + ", " + Db.TABLE_Marker_Label + " where " + Db.Marker.kind + "=? and " + Db.TABLE_Marker + "." + Db.Marker.gid + " = " + Db.TABLE_Marker_Label + "." + Db.Marker_Label.marker_gid + " and " + Db.TABLE_Marker_Label + "." + Db.Marker_Label.label_gid + "=? order by " + Db.TABLE_Marker + "." + sortClause, new String[]{String.valueOf(kind.code), label.gid});
        }

        try {
            while (c.moveToNext()) {
                res.add(MarkerDao.markerFromCursor(c));
            }
        } finally {
            c.close();
        }

        return res;
    }

    public List<Marker> listAllMarkers() {
        return markerDao.listAll();
    }

    /**
     * TODO this is only called together with {@link #putAttributes(int, int[], int[], Highlights.Info[])}, make it private.
     */
    public int countMarkersForBookChapter(int ari_bookchapter) {
        final int ariMin = ari_bookchapter & 0x00ffff00;
        final int ariMax = ari_bookchapter | 0x000000ff;
        return markerDao.countForAriRange(ariMin, ariMax);
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

        // order by modifyTime, so in case a verse has more than one highlight, the latest one is shown.
        // Inclusive upper bound so a marker on verse 255 (ari == ariMax) is included — matches
        // getHighlightColorRgb(int, IntArrayList).
        try (Cursor cursor = helper.getReadableDatabase().rawQuery("select * from " + Db.TABLE_Marker + " where " + Db.Marker.ari + ">=? and " + Db.Marker.ari + "<=? order by " + Db.Marker.modifyTime, params)) {
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
            try (Cursor c = db.query(Db.TABLE_Marker, null, Db.Marker.ari + "=? and " + Db.Marker.kind + "=?", ToStringArray(ari, Marker.Kind.highlight.code), null, null, Db.Marker.modifyTime + " desc")) {
                final int hashCode = Highlights.hashCode(verseText.toString());
                final Date now = new Date();

                if (c.moveToNext()) { // check if marker exists
                    { // modify the latest one
                        final Marker marker = MarkerDao.markerFromCursor(c);
                        marker.modifyTime = now;
                        marker.caption = Highlights.encode(colorRgb, hashCode, startOffset, endOffset);
                        db.update(Db.TABLE_Marker, MarkerDao.markerToContentValues(marker), "_id=?", ToStringArray(marker._id));
                    }

                    // remove earlier ones if they exist (caused by sync)
                    while (c.moveToNext()) {
                        final long _id = c.getLong(c.getColumnIndexOrThrow("_id"));
                        db.delete(Db.TABLE_Marker, "_id=?", ToStringArray(_id));
                    }
                } else { // insert
                    final Marker marker = Marker.createNewMarker(ari, Marker.Kind.highlight, Highlights.encode(colorRgb, hashCode, startOffset, endOffset), 1, now, now);
                    db.insert(Db.TABLE_Marker, null, MarkerDao.markerToContentValues(marker));
                }
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
                try (Cursor c = db.query(Db.TABLE_Marker, null, Db.Marker.ari + "=? and " + Db.Marker.kind + "=?", params, null, null, Db.Marker.modifyTime + " desc")) {
                    if (c.moveToNext()) { // check if marker exists
                        { // modify the latest one
                            final Marker marker = MarkerDao.markerFromCursor(c);
                            marker.modifyTime = new Date();
                            if (colorRgb != -1) {
                                marker.caption = Highlights.encode(colorRgb);
                                db.update(Db.TABLE_Marker, MarkerDao.markerToContentValues(marker), "_id=?", ToStringArray(marker._id));
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
                            db.insert(Db.TABLE_Marker, null, MarkerDao.markerToContentValues(marker));
                        }
                    }
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
     *
     * @return the color rgb or -1 if there are multiple colors.
     */
    public int getHighlightColorRgb(int ari_bookchapter, IntArrayList selectedVerses_1) {
        int ariMin = ari_bookchapter & 0xffffff00;
        int ariMax = ari_bookchapter | 0x000000ff;
        int[] colors = new int[256];
        int res = -2;

        Arrays.fill(colors, -1);

        // check if exists

        try (Cursor c = helper.getReadableDatabase().query(
            Db.TABLE_Marker, null, Db.Marker.ari + ">? and " + Db.Marker.ari + "<=? and " + Db.Marker.kind + "=?",
            new String[]{String.valueOf(ariMin), String.valueOf(ariMax), String.valueOf(Marker.Kind.highlight.code)},
            null, null, null
        )) {
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
        devotionDao.storeArticle(article);
    }

    public int deleteDevotionsWithTouchTimeBefore(Date date) {
        return devotionDao.deleteWithTouchTimeBefore(date);
    }

    /**
     * Try to get article from local db. Non ready-to-use article will be returned too.
     */
    public DevotionArticle tryGetDevotion(String name, String date) {
        return devotionDao.tryGet(name, date);
    }

    @NonNull
    public List<MVersionDb> listAllVersions() {
        return versionDao.listAll();
    }

    public void setVersionActive(MVersionDb mv, boolean active) {
        versionDao.setActive(mv, active);
    }

    public int getVersionMaxOrdering() {
        return versionDao.getMaxOrdering();
    }

    public void insertOrUpdateVersionWithActive(MVersionDb mv, boolean active) {
        versionDao.insertOrUpdateWithActive(mv, active);
    }

    public void deleteVersion(MVersionDb mv) {
        versionDao.delete(mv);
    }

    public List<Label> listAllLabels() {
        return labelDao.listAll();
    }

    public List<Marker_Label> listAllMarker_Labels() {
        return marker_LabelDao.listAll();
    }

    public List<Marker_Label> listMarker_LabelsByMarker(final Marker marker) {
        return marker_LabelDao.listByMarker(marker);
    }

    @NonNull
    public List<Label> listLabelsByMarker(final Marker marker) {
        return labelDao.listByMarker(marker);
    }

    public int getLabelMaxOrdering() {
        return labelDao.getMaxOrdering();
    }

    public Label insertLabel(String title, String bgColor) {
        final Label res = labelDao.insertNew(title, bgColor);
        Sync.notifySyncNeeded(SyncShadow.SYNC_SET_MABEL);
        return res;
    }

    public void updateLabels(final Marker marker, final Set<Label> newLabels) {
        final SQLiteDatabase db = helper.getWritableDatabase();

        db.beginTransactionNonExclusive();
        try {
            final List<Marker_Label> oldMls = marker_LabelDao.listByMarker(marker);

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

            for (final Marker_Label removeMl : removeMls) {
                marker_LabelDao.deleteById(removeMl._id);
            }

            for (final Label addLabel : addLabels) {
                marker_LabelDao.insert(Marker_Label.createNewMarker_Label(marker.gid, addLabel.gid));
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        Sync.notifySyncNeeded(SyncShadow.SYNC_SET_MABEL);
    }

    public Label getLabelById(long _id) {
        return labelDao.getById(_id);
    }

    @Nullable
    public Label getLabelByGid(@NonNull final String gid) {
        return labelDao.getByGid(gid);
    }

    @Nullable
    public Marker_Label getMarker_LabelByGid(@NonNull final String gid) {
        return marker_LabelDao.getByGid(gid);
    }

    /**
     * This is so special: delete label and the associated marker_labels
     */
    public void deleteLabelAndMarker_LabelsByLabelId(long _id) {
        final Label label = labelDao.getById(_id);
        final SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransactionNonExclusive();
        try {
            marker_LabelDao.deleteByLabelGid(label.gid);
            labelDao.deleteById(_id);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        Sync.notifySyncNeeded(SyncShadow.SYNC_SET_MABEL);
    }

    public void insertOrUpdateLabel(@NonNull final Label label) {
        labelDao.upsert(label);
        Sync.notifySyncNeeded(SyncShadow.SYNC_SET_MABEL);
    }

    public void insertOrUpdateMarker_Label(@NonNull final Marker_Label marker_label) {
        marker_LabelDao.upsert(marker_label);
        Sync.notifySyncNeeded(SyncShadow.SYNC_SET_MABEL);
    }

    public int countMarkersWithLabel(Label label) {
        return marker_LabelDao.countByLabelGid(label.gid);
    }

    public void sortLabelsAlphabetically() {
        final SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransactionNonExclusive();
        try {
            final List<Label> labels = labelDao.listAll();
            labels.sort((lhs, rhs) -> {
                if (lhs.title == null || rhs.title == null) {
                    return 0;
                }
                return lhs.title.compareToIgnoreCase(rhs.title);
            });

            for (int i = 0; i < labels.size(); i++) {
                final Label label = labels.get(i);
                label.ordering = i + 1;
                labelDao.upsert(label);
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
                db.execSQL("update " + Db.TABLE_Label + " set " + Db.Label.ordering + "=(" + Db.Label.ordering + "+1) where ?<=" + Db.Label.ordering + " and " + Db.Label.ordering + "<?", new Object[]{to.ordering, from.ordering});
                db.execSQL("update " + Db.TABLE_Label + " set " + Db.Label.ordering + "=? where _id=?", new Object[]{to.ordering, from._id});
            } else if (from.ordering < to.ordering) { // move down
                db.execSQL("update " + Db.TABLE_Label + " set " + Db.Label.ordering + "=(" + Db.Label.ordering + "-1) where ?<" + Db.Label.ordering + " and " + Db.Label.ordering + "<=?", new Object[]{from.ordering, to.ordering});
                db.execSQL("update " + Db.TABLE_Label + " set " + Db.Label.ordering + "=? where _id=?", new Object[]{to.ordering, from._id});
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

    public List<ProgressMark> listAllProgressMarks() {
        return progressMarkDao.listAll();
    }

    public int countAllProgressMarks() {
        return progressMarkDao.countAll();
    }

    @Nullable
    public ProgressMark getProgressMarkByPresetId(final int preset_id) {
        return progressMarkDao.getByPresetId(preset_id);
    }

    public void insertOrUpdateProgressMark(@NonNull final ProgressMark progressMark) {
        progressMarkDao.insertOrUpdate(progressMark);
        Sync.notifySyncNeeded(SyncShadow.SYNC_SET_PINS);
    }

    public List<ProgressMarkHistory> listProgressMarkHistoryByPresetId(final int preset_id) {
        return progressMarkDao.listHistoryByPresetId(preset_id);
    }

    public long insertReadingPlan(final ReadingPlan.ReadingPlanInfo info, byte[] data) {
        final long res = readingPlanDao.insert(info, data);
        // this adds the 'startTime' attribute to the sync entity (when any of the rp progress has been checked)
        Sync.notifySyncNeeded(SyncShadow.SYNC_SET_RP);
        return res;
    }

    public void insertOrUpdateReadingPlanProgress(final String gid, final int readingCode, final long checkTime) {
        readingPlanDao.insertOrUpdateProgress(gid, readingCode, checkTime);
        Sync.notifySyncNeeded(SyncShadow.SYNC_SET_RP);
    }

    /**
     * Removes all existing reading codes that matches the specified gid and adds the one specified in readingCodes.
     *
     * @param checkTime the time of checking the reading code, applied to all reading codes.
     */
    public void replaceReadingPlanProgress(final String gid, final IntArrayList readingCodes, final long checkTime) {
        readingPlanDao.replaceProgress(gid, readingCodes, checkTime);
        Sync.notifySyncNeeded(SyncShadow.SYNC_SET_RP);
    }

    public void insertOrUpdateMultipleReadingPlanProgresses(final String gid, final IntArrayList readingCodes, final long checkTime) {
        readingPlanDao.insertOrUpdateMultipleProgresses(gid, readingCodes, checkTime);
        Sync.notifySyncNeeded(SyncShadow.SYNC_SET_RP);
    }

    public void deleteReadingPlanProgress(final String gid, final int readingCode) {
        readingPlanDao.deleteProgress(gid, readingCode);
        Sync.notifySyncNeeded(SyncShadow.SYNC_SET_RP);
    }

    public void deleteAllReadingPlanProgressForGid(final String gid) {
        readingPlanDao.deleteAllProgressForGid(gid);
        Sync.notifySyncNeeded(SyncShadow.SYNC_SET_RP);
    }

    /**
     * Get the list of reading plan gid with their done reading codes.
     * The only source of data is from ReadingPlanProgress table, but since reading plans with no done is not listed in ReadingPlanProgress,
     * please take care of it.
     */
    public Map<String /* gid */, Set<Integer> /* done reading codes */> getReadingPlanProgressSummaryForSync() {
        return readingPlanDao.getProgressSummaryForSync();
    }

    @NonNull
    public List<ReadingPlan.ReadingPlanInfo> listAllReadingPlanInfo() {
        return readingPlanDao.listAllInfo();
    }

    public Pair<String, byte[]> getReadingPlanNameAndData(long _id) {
        return readingPlanDao.getNameAndData(_id);
    }

    public IntArrayList getAllReadingCodesByReadingPlanProgressGid(final String gid) {
        return readingPlanDao.getAllReadingCodesByProgressGid(gid);
    }

    /**
     * Deletes the reading plan, but not the progress.
     * The progress will be kept, so it is not considered as deleted during sync.
     */
    public void deleteReadingPlanById(long id) {
        readingPlanDao.deleteById(id);
        // this removes the 'startTime' attribute from the sync entity
        Sync.notifySyncNeeded(SyncShadow.SYNC_SET_RP);
    }

    public void updateReadingPlanStartDate(long id, long startDate) {
        readingPlanDao.updateStartDate(id, startDate);
        Sync.notifySyncNeeded(SyncShadow.SYNC_SET_RP);
    }

    public List<String> listReadingPlanNames() {
        return readingPlanDao.listNames();
    }

    @Nullable
    public SyncShadow getSyncShadowBySyncSetName(final String syncSetName) {
        return syncShadowDao.getBySyncSetName(syncSetName);
    }

    public int getRevnoFromSyncShadowBySyncSetName(final String syncSetName) {
        return syncShadowDao.getRevnoBySyncSetName(syncSetName);
    }

    /**
     * Create or update a sync shadow, based on the sync set name.
     *
     * @param ss if the {@link yuku.alkitab.base.model.SyncShadow#syncSetName} is already on the database, this method will replace it. Otherwise, this method will insert a new one.
     */
    public void insertOrUpdateSyncShadowBySyncSetName(@NonNull final SyncShadow ss) {
        syncShadowDao.insertOrUpdateBySyncSetName(ss);
    }

    public void deleteSyncShadowBySyncSetName(final String syncSetName) {
        syncShadowDao.deleteBySyncSetName(syncSetName);
    }

    /**
     * Makes the current database updated with patches (append delta) from server.
     * Also updates the shadow (both data and the revno).
     *
     * @return {@link yuku.alkitab.base.sync.Sync.ApplyAppendDeltaResult#ok} if database and sync shadow are updated. Otherwise else.
     */
    @NonNull
    public Sync.ApplyAppendDeltaResult applyMabelAppendDelta(final int final_revno, final List<Sync.Entity<Sync_Mabel.Content>> shadowEntities, final Sync.ClientState<Sync_Mabel.Content> clientState, @NonNull final Sync.Delta<Sync_Mabel.Content> append_delta, @NonNull final List<Sync.Entity<Sync_Mabel.Content>> entitiesBeforeSync, @NonNull final String simpleTokenBeforeSync) {
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
                if (!simpleTokenBeforeSync.equals(simpleToken)) {
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
     *
     * @return {@link yuku.alkitab.base.sync.Sync.ApplyAppendDeltaResult#ok} if database and sync shadow are updated. Otherwise else.
     */
    @NonNull
    public Sync.ApplyAppendDeltaResult applyPinsAppendDelta(final int final_revno, @NonNull final Sync.Delta<Sync_Pins.Content> append_delta, @NonNull final List<Sync.Entity<Sync_Pins.Content>> entitiesBeforeSync, @NonNull final String simpleTokenBeforeSync) {
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
                if (!simpleTokenBeforeSync.equals(simpleToken)) {
                    return Sync.ApplyAppendDeltaResult.dirty_sync_account;
                }
            }

            for (final Sync.Operation<Sync_Pins.Content> o : append_delta.operations) {
                switch (o.opkind) {
                    case del:
                    case add:
                        return Sync.ApplyAppendDeltaResult.unsupported_operation;
                    case mod:
                        if (Sync.Entity.KIND_PINS.equals(o.kind)) {// the whole logic to update all pins with the ones received from server (all pins in one entity)
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
                        } else {
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
     *
     * @return {@link yuku.alkitab.base.sync.Sync.ApplyAppendDeltaResult#ok} if database and sync shadow are updated. Otherwise else.
     */
    @NonNull
    public Sync.ApplyAppendDeltaResult applyRpAppendDelta(final int final_revno, @NonNull final Sync.Delta<Sync_Rp.Content> append_delta, @NonNull final List<Sync.Entity<Sync_Rp.Content>> entitiesBeforeSync, @NonNull final String simpleTokenBeforeSync) {
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
                if (!simpleTokenBeforeSync.equals(simpleToken)) {
                    return Sync.ApplyAppendDeltaResult.dirty_sync_account;
                }
            }

            for (final Sync.Operation<Sync_Rp.Content> o : append_delta.operations) {
                if (!Sync.Entity.KIND_RP_PROGRESS.equals(o.kind)) {
                    return Sync.ApplyAppendDeltaResult.unknown_kind;
                }

                switch (o.opkind) {
                    case del -> db.delete(Db.TABLE_ReadingPlanProgress, Db.ReadingPlanProgress.reading_plan_progress_gid + "=?", Array(o.gid));
                    case add, mod -> {
                        // the whole logic to update all pins with the ones received from server (all pins in one entity)
                        final Sync_Rp.Content content = o.content;
                        final IntArrayList readingCodes = getAllReadingCodesByReadingPlanProgressGid(o.gid);
                        final Set<Integer> src = new HashSet<>(readingCodes.size()); // our source (the current 'done' list)
                        for (int i = 0, len = readingCodes.size(); i < len; i++) {
                            src.add(readingCodes.get(i));
                        }
                        final Set<Integer> dst = new HashSet<>(content.done); // our destination (want to be like this)

                        { // deletions
                            final Set<Integer> to_del = new HashSet<>(src);
                            to_del.removeAll(dst);
                            for (Integer value : to_del) {
                                db.delete(Db.TABLE_ReadingPlanProgress, Db.ReadingPlanProgress.reading_plan_progress_gid + "=? and " + Db.ReadingPlanProgress.reading_code + "=?", ToStringArray(o.gid, value));
                            }
                        }

                        { // additions
                            final Set<Integer> to_add = new HashSet<>(dst);
                            to_add.removeAll(src);

                            // unchanging properties
                            final ContentValues cv = new ContentValues();
                            cv.put(Db.ReadingPlanProgress.reading_plan_progress_gid, o.gid);
                            cv.put(Db.ReadingPlanProgress.checkTime, System.currentTimeMillis());

                            for (Integer value : to_add) {
                                cv.put(Db.ReadingPlanProgress.reading_code, value);
                                helper.getWritableDatabase().insert(Db.TABLE_ReadingPlanProgress, null, cv);
                            }
                        }

                        // update startTime
                        if (content.startTime != null) {
                            for (final ReadingPlan.ReadingPlanInfo info : listAllReadingPlanInfo()) {
                                if (ReadingPlan.gidFromName(info.name).equals(o.gid)) {
                                    if (info.startTime != content.startTime) {
                                        final ContentValues cv = new ContentValues();
                                        cv.put(Db.ReadingPlan.startTime, content.startTime);
                                        db.update(Db.TABLE_ReadingPlan, cv, "_id=?", ToStringArray(info.id));
                                    }
                                    break;
                                }
                            }
                        }
                    }
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
     *
     * @return true when deleted.
     */
    public boolean deleteMarkerByGid(final String gid) {
        final boolean deleted = markerDao.deleteByGid(gid) > 0;
        if (deleted) {
            Sync.notifySyncNeeded(SyncShadow.SYNC_SET_MABEL);
        }
        return deleted;
    }

    /**
     * Deletes a label by gid.
     */
    public void deleteLabelByGid(final String gid) {
        if (labelDao.deleteByGid(gid) > 0) {
            Sync.notifySyncNeeded(SyncShadow.SYNC_SET_MABEL);
        }
    }

    /**
     * Deletes a marker-label association by gid.
     *
     * @return true when deleted.
     */
    public boolean deleteMarker_LabelByGid(final String gid) {
        final boolean deleted = marker_LabelDao.deleteByGid(gid) > 0;
        if (deleted) {
            Sync.notifySyncNeeded(SyncShadow.SYNC_SET_MABEL);
        }
        return deleted;
    }

    public void insertSyncLog(final int createTime, final SyncRecorder.EventKind kind, final String syncSetName, final String params) {
        syncShadowDao.insertLog(createTime, kind, syncSetName, params);
    }

    public List<SyncLog> listLatestSyncLog(final int maxrows) {
        return syncShadowDao.listLatest(maxrows);
    }

    @NonNull
    public PerVersionSettings getPerVersionSettings(@NonNull final String versionId) {
        return perVersionDao.getSettings(versionId);
    }

    public void storePerVersionSettings(@NonNull final String versionId, @NonNull PerVersionSettings settings) {
        perVersionDao.storeSettings(versionId, settings);
    }
}
