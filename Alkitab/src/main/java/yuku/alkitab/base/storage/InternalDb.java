package yuku.alkitab.base.storage;

import android.util.Pair;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.sqlite.db.SimpleSQLiteQuery;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.ac.MarkerListActivity;
import yuku.alkitab.base.devotion.DevotionArticle;
import yuku.alkitab.base.model.MVersion;
import yuku.alkitab.base.model.MVersionDb;
import yuku.alkitab.base.model.MVersionInternal;
import yuku.alkitab.base.model.PerVersionSettings;
import yuku.alkitab.base.model.ReadingPlan;
import yuku.alkitab.base.model.SyncLog;
import yuku.alkitab.base.model.SyncShadow;
import yuku.alkitab.base.storage.room.AppDatabase;
import yuku.alkitab.base.storage.room.MarkerEntity;
import yuku.alkitab.base.sync.Sync;
import yuku.alkitab.base.sync.SyncApplier;
import yuku.alkitab.base.sync.SyncRecorder;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.base.util.Highlights;
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

    public final InternalDbHelper helper;
    public final VersionDao versionDao;
    public final ProgressMarkDao progressMarkDao;
    public final PerVersionDao perVersionDao;
    public final DevotionDao devotionDao;
    public final LabelDao labelDao;
    public final Marker_LabelDao marker_LabelDao;
    public final ReadingPlanDao readingPlanDao;
    public final MarkerDao markerDao;
    public final SyncShadowDao syncShadowDao;
    public final SyncApplier syncApplier;

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
        this.syncApplier = new SyncApplier(this);
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
        if (marker == null) return;

        AppDatabase.get(App.context).runInTransaction(() -> {
            marker_LabelDao.deleteByMarkerGid(marker.gid);
            markerDao.deleteById(_id);
        });
        Sync.notifySyncNeeded(SyncShadow.SYNC_SET_MABEL);
    }

    public void deleteNonBookmarkMarkerById(long _id) {
        markerDao.deleteById(_id);
        Sync.notifySyncNeeded(SyncShadow.SYNC_SET_MABEL);
    }

    public List<Marker> listMarkers(Marker.Kind kind, long label_id, String sortColumn, boolean sortAscending) {
        // Whitelist sortColumn to defend against injection — even though
        // the caller is the marker-list menu, never trust an interpolated
        // identifier. Falls back to modifyTime if an unexpected value
        // sneaks in (matches what the legacy raw SQL would have done if
        // the column didn't exist: it would have thrown; we prefer a sane
        // default).
        final String safeSort;
        if (Db.Marker.createTime.equals(sortColumn)) safeSort = "createTime";
        else if (Db.Marker.modifyTime.equals(sortColumn)) safeSort = "modifyTime";
        else if (Db.Marker.ari.equals(sortColumn)) safeSort = "ari";
        else if (Db.Marker.caption.equals(sortColumn)) safeSort = "caption";
        else safeSort = "modifyTime";

        final String sortClause = safeSort + (Db.Marker.caption.equals(safeSort) ? " collate NOCASE " : "") + (sortAscending ? " asc" : " desc");

        final String sql;
        final Object[] args;
        if (label_id == 0) { // no restrictions
            sql = "select marker.* from marker where marker.kind = ? order by marker." + sortClause;
            args = new Object[]{kind.code};
        } else if (label_id == MarkerListActivity.LABELID_noLabel) { // only without label
            sql = "select marker.* from marker where marker.kind = ? and marker.gid not in (select distinct marker_gid from marker_label) order by marker." + sortClause;
            args = new Object[]{kind.code};
        } else { // filter by label_id
            final Label label = getLabelById(label_id);
            sql = "select marker.* from marker, marker_label where marker.kind = ? and marker.gid = marker_label.marker_gid and marker_label.label_gid = ? order by marker." + sortClause;
            args = new Object[]{kind.code, label.gid};
        }

        final List<MarkerEntity> entities = AppDatabase.get(App.context).markerDao()
            .listMarkersRaw(new SimpleSQLiteQuery(sql, args));

        final List<Marker> res = new ArrayList<>(entities.size());
        for (final MarkerEntity entity : entities) {
            res.add(MarkerDao.toModel(entity));
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

        // order by modifyTime, so in case a verse has more than one highlight, the latest one is shown.
        // Inclusive upper bound so a marker on verse 255 (ari == ariMax) is included — matches
        // getHighlightColorRgb(int, IntArrayList).
        final List<MarkerEntity> entities = AppDatabase.get(App.context).markerDao()
            .listInAriRangeOrderedByModifyTimeAsc(ariMin, ariMax);

        for (final MarkerEntity entity : entities) {
            final int ari = entity.getAri();
            final int kind = entity.getKind();

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
                final int verseCount = entity.getVerseCount();

                for (int i = 0; i < verseCount; i++) {
                    int mapOffset2 = mapOffset + i;
                    if (mapOffset2 >= highlightColorMap.length) break; // do not go past number of verses in this chapter

                    final Highlights.Info info = Highlights.decode(entity.getCaption());

                    highlightColorMap[mapOffset2] = info;
                }
            }
        }
    }

    /**
     * @param colorRgb may NOT be -1. Use {@link #updateOrInsertHighlights(int, IntArrayList, int)} to delete highlight.
     */
    public void updateOrInsertPartialHighlight(final int ari, final int colorRgb, final CharSequence verseText, final int startOffset, final int endOffset) {
        AppDatabase.get(App.context).runInTransaction(() -> {
            // order by modifyTime desc so we modify the latest one and remove earlier ones if they exist.
            final List<MarkerEntity> existing = AppDatabase.get(App.context).markerDao()
                .listForAriKindOrderedByModifyTimeDesc(ari, Marker.Kind.highlight.code);
            final int hashCode = Highlights.hashCode(verseText.toString());
            final Date now = new Date();

            if (!existing.isEmpty()) { // check if marker exists
                { // modify the latest one
                    final Marker marker = MarkerDao.toModel(existing.get(0));
                    marker.modifyTime = now;
                    marker.caption = Highlights.encode(colorRgb, hashCode, startOffset, endOffset);
                    markerDao.upsert(marker);
                }

                // remove earlier ones if they exist (caused by sync)
                for (int i = 1; i < existing.size(); i++) {
                    markerDao.deleteById(existing.get(i).get_id());
                }
            } else { // insert
                final Marker marker = Marker.createNewMarker(ari, Marker.Kind.highlight, Highlights.encode(colorRgb, hashCode, startOffset, endOffset), 1, now, now);
                markerDao.upsert(marker);
            }
        });

        Sync.notifySyncNeeded(SyncShadow.SYNC_SET_MABEL);
    }

    public void updateOrInsertHighlights(int ari_bookchapter, IntArrayList selectedVerses_1, int colorRgb) {
        AppDatabase.get(App.context).runInTransaction(() -> {
            // every requested verses
            for (int i = 0; i < selectedVerses_1.size(); i++) {
                final int ari = Ari.encodeWithBc(ari_bookchapter, selectedVerses_1.get(i));

                // order by modifyTime desc so we modify the latest one and remove earlier ones if they exist.
                final List<MarkerEntity> existing = AppDatabase.get(App.context).markerDao()
                    .listForAriKindOrderedByModifyTimeDesc(ari, Marker.Kind.highlight.code);

                if (!existing.isEmpty()) { // check if marker exists
                    { // modify the latest one
                        final Marker marker = MarkerDao.toModel(existing.get(0));
                        marker.modifyTime = new Date();
                        if (colorRgb != -1) {
                            marker.caption = Highlights.encode(colorRgb);
                            markerDao.upsert(marker);
                        } else {
                            // delete entry
                            markerDao.deleteById(marker._id);
                        }
                    }

                    // remove earlier ones if they exist (caused by sync)
                    for (int j = 1; j < existing.size(); j++) {
                        markerDao.deleteById(existing.get(j).get_id());
                    }
                } else {
                    if (colorRgb == -1) {
                        // no need to do, from no color to no color
                    } else {
                        final Date now = new Date();
                        final Marker marker = Marker.createNewMarker(ari, Marker.Kind.highlight, Highlights.encode(colorRgb), 1, now, now);
                        markerDao.upsert(marker);
                    }
                }
            }
        });

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

        // check if exists — exclusive lower bound on ari matches the legacy SQL
        final List<MarkerEntity> entities = AppDatabase.get(App.context).markerDao()
            .listForAriRangeExclusiveMinAndKind(ariMin, ariMax, Marker.Kind.highlight.code);

        // put to array first
        for (final MarkerEntity entity : entities) {
            int ari = entity.getAri();
            int index = ari & 0xff;
            final Highlights.Info info = Highlights.decode(entity.getCaption());
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

    /**
     * Get the highlight info for a single verse
     */
    public Highlights.Info getHighlightColorRgb(final int ari) {
        final List<MarkerEntity> entities = AppDatabase.get(App.context).markerDao()
            .listForAriKindOrderedByModifyTimeDesc(ari, Marker.Kind.highlight.code);
        if (entities.isEmpty()) return null;
        return Highlights.decode(entities.get(0).getCaption());
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
        AppDatabase.get(App.context).runInTransaction(() -> {
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
        });
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
        AppDatabase.get(App.context).runInTransaction(() -> {
            marker_LabelDao.deleteByLabelGid(label.gid);
            labelDao.deleteById(_id);
        });
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
        AppDatabase.get(App.context).runInTransaction(() -> {
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
        });
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

        // Single-transaction shift + final ordering update lives in the Room DAO
        // (see LabelRoomDao.reorderById). No-op when from.ordering == to.ordering.
        AppDatabase.get(App.context).labelDao().reorderById(from._id, from.ordering, to.ordering);
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

        // Bookkeeping for the internal-version ordering preference (a single
        // int held in Preferences, not stored in the DB). The internal version
        // sits in the same ordered list as DB versions, so when we shift DB
        // rows around the internal version's ordering may also need to slide.
        final int internal_ordering = Preferences.getInt(Prefkey.internal_version_ordering, MVersionInternal.DEFAULT_ORDERING);
        if (from.ordering > to.ordering) { // move up
            if (to.ordering <= internal_ordering && internal_ordering < from.ordering) {
                Preferences.setInt(Prefkey.internal_version_ordering, internal_ordering + 1);
            }
        } else if (from.ordering < to.ordering) { // move down
            if (from.ordering < internal_ordering && internal_ordering <= to.ordering) {
                Preferences.setInt(Prefkey.internal_version_ordering, internal_ordering - 1);
            }
        }

        if (from instanceof MVersionDb) {
            // Single-transaction shift + final ordering update lives in the
            // Room DAO (see VersionRoomDao.reorderByFilename).
            yuku.alkitab.base.storage.room.AppDatabase
                .get(yuku.afw.App.context)
                .versionDao()
                .reorderByFilename(((MVersionDb) from).filename, from.ordering, to.ordering);
        } else if (from instanceof MVersionInternal) {
            Preferences.setInt(Prefkey.internal_version_ordering, to.ordering);
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
