package yuku.alkitab.base.storage;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Parcel;
import android.util.Pair;
import androidx.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static yuku.alkitab.base.util.Literals.Array;
import yuku.afw.App;
import yuku.alkitab.base.storage.room.SongBookInfoEntity;
import yuku.alkitab.base.storage.room.SongInfoEntity;
import yuku.alkitab.base.storage.room.SongInfoMetaRow;
import yuku.alkitab.base.storage.room.SongRoomDao;
import yuku.alkitab.base.storage.room.SongRoomDatabase;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.base.util.Background;
import yuku.alkitab.base.util.Sqlitil;
import yuku.alkitab.songs.SongBookUtil;
import yuku.alkitab.songs.SongFilter;
import yuku.alkitab.songs.SongFilter.CompiledFilter;
import yuku.alkitab.songs.SongInfo;
import yuku.alkitab.songs.newdoc.LegacyParcelDecoder;
import yuku.alkitab.songs.newdoc.LegacySongConverter;
import yuku.alkitab.songs.newdoc.SongDocument;
import yuku.alkitab.songs.newdoc.SongDocumentJson;
import yuku.kpri.model.Song;

/**
 * Facade over the Room-backed {@link SongRoomDao} carrying the
 * {@code SongInfo} / {@code SongBookInfo} surface its call sites expect.
 *
 * <p>The {@link SongDbHelper} passed to the constructor is unused:
 * {@link SongRoomDatabase} owns the SQLite file, and the one-time copy out of
 * the legacy file runs in {@code SongDbDataMigration}.
 *
 * <p>{@link #writeDocument} always writes UTF-8 JSON, while
 * {@link #readDocument} dispatches on the row's {@code dataFormatVersion} and
 * rewrites a legacy Parcelable row as JSON the first time it is read.
 */
public class SongDb {
    @SuppressWarnings("unused") // kept for ABI parity with the pre-Room constructor
    private final SongDbHelper helper;

    // Double-checked locking: `SongRoomDatabase.get` is itself thread-safe and
    // returns the same singleton, so a double init is harmless, but without
    // `volatile` the JMM could publish a partially-initialised reference.
    private volatile SongRoomDao cachedRoomDao;

    public SongDb(SongDbHelper helper) {
        this.helper = helper;
    }

    private SongRoomDao roomDao() {
        SongRoomDao result = cachedRoomDao;
        if (result == null) {
            synchronized (this) {
                result = cachedRoomDao;
                if (result == null) {
                    result = SongRoomDatabase.get(App.context).songRoomDao();
                    cachedRoomDao = result;
                }
            }
        }
        return result;
    }

    private static byte[] writeDocument(SongDocument doc) {
        return SongDocumentJson.encode(doc).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Fallback for a wire shape {@link LegacyParcelDecoder} does not recognise.
     * Works as long as the OS has not changed since the row was written.
     */
    private static Song unmarshallLegacySongViaPlatformParcel(byte[] buf, int dataFormatVersion) {
        Parcel p = Parcel.obtain();
        p.unmarshall(buf, 0, buf.length);
        p.setDataPosition(0);
        Song res = Song.createFromParcelCompat(dataFormatVersion, p);
        p.recycle();
        return res;
    }

    /**
     * Dispatches JSON vs. legacy Parcelable by {@code dataFormatVersion}. A
     * converted legacy row is written back as JSON, so it converts at most once.
     */
    private SongDocument readDocument(long id, String bookName, String code, byte[] data, int dataFormatVersion) {
        if (dataFormatVersion == SongDocumentJson.DATA_FORMAT_VERSION) {
            return SongDocumentJson.decode(new String(data, StandardCharsets.UTF_8));
        }

        Song legacySong;
        try {
            legacySong = LegacyParcelDecoder.decode(data, dataFormatVersion);
        } catch (Exception e) {
            AppLog.e("SongDb", "LegacyParcelDecoder failed for book=" + bookName + " code=" + code + "; falling back to Parcel.unmarshall", e);
            legacySong = unmarshallLegacySongViaPlatformParcel(data, dataFormatVersion);
        }

        final SongDocument doc = LegacySongConverter.convert(legacySong);
        // This helper runs on the caller's thread, including the main thread and, in a tight
        // loop, the deep-filter scan. Fire the UPDATE on a background thread so a book full of
        // legacy songs cannot stall the UI or a search.
        final byte[] jsonBytes = writeDocument(doc);
        Background.run(() -> roomDao().writeBackJsonSongData(id, jsonBytes));
        return doc;
    }

    /**
     * Deletes every song of the book first, at any {@code dataFormatVersion}, so
     * updating a mixed-version book leaves no stale rows behind.
     */
    public void storeSongs(String bookName, List<SongDocument> docs) {
        final int updateTime = Sqlitil.nowDateTime();
        final List<SongInfoEntity> entities = new ArrayList<>(docs.size());
        int ordering = 1;
        for (SongDocument doc : docs) {
            entities.add(new SongInfoEntity(
                0L,
                bookName,
                doc.getCode(),
                doc.getMeta().getTitle(),
                doc.getMeta().getTitle_original(),
                ordering++,
                SongDocumentJson.DATA_FORMAT_VERSION,
                writeDocument(doc),
                updateTime
            ));
        }
        roomDao().replaceSongsForBookName(bookName, entities);
    }

    public SongDocument getSong(String bookName, String code) {
        final SongInfoEntity row = roomDao().findSongInfoByBookNameAndCode(bookName, code);
        if (row == null || row.getData() == null) {
            return null;
        }
        return readDocument(row.get_id(), row.getBookName(), row.getCode(), row.getData(), row.getDataFormatVersion());
    }

    public boolean songExists(String bookName, String code) {
        return roomDao().countSongInfosByBookNameAndCode(bookName, code) > 0;
    }

    public SongDocument getFirstSongFromBook(String bookName) {
        final SongInfoEntity row = roomDao().findFirstSongInfoByBookName(bookName);
        if (row == null || row.getData() == null) {
            return null;
        }
        return readDocument(row.get_id(), row.getBookName(), row.getCode(), row.getData(), row.getDataFormatVersion());
    }

    /**
     * @return null if there is no song at all
     */
    @Nullable
    public Pair<String /* bookName */, SongDocument> getAnySong() {
        final SongInfoEntity row = roomDao().findAnySongInfo();
        if (row == null || row.getData() == null || row.getBookName() == null) {
            return null;
        }
        return Pair.create(row.getBookName(), readDocument(row.get_id(), row.getBookName(), row.getCode(), row.getData(), row.getDataFormatVersion()));
    }

    public List<SongInfo> listSongInfosByBookName(@Nullable String bookName) {
        // Null bookName means "All song books", the song list's book selector.
        final List<SongInfoMetaRow> rows = (bookName == null)
            ? roomDao().listAllSongInfoMetas()
            : roomDao().listSongInfoMetasByBookName(bookName);
        final List<SongInfo> res = new ArrayList<>(rows.size());
        for (SongInfoMetaRow row : rows) {
            res.add(new SongInfo(row.getBookName(), row.getCode(), row.getTitle(), row.getTitle_original()));
        }
        return res;
    }

    public List<SongInfo> listSongInfosByBookNameAndDeepFilter(String bookName, String filter_string) {
        final CompiledFilter cf = SongFilter.compileFilter(filter_string);
        // Streams through a Cursor, because a Room-returned List would hold the
        // whole catalogue in memory when bookName is null. See SongRoomDao.
        final List<SongInfo> res = new ArrayList<>();
        try (Cursor c = (bookName == null)
            ? roomDao().queryAllDeepFilterRows()
            : roomDao().queryDeepFilterRowsByBookName(bookName)) {
            final int colId = c.getColumnIndexOrThrow("_id");
            final int colBookName = c.getColumnIndexOrThrow("bookName");
            final int colCode = c.getColumnIndexOrThrow("code");
            final int colTitle = c.getColumnIndexOrThrow("title");
            final int colTitleOriginal = c.getColumnIndexOrThrow("title_original");
            final int colData = c.getColumnIndexOrThrow("data");
            final int colDataFormatVersion = c.getColumnIndexOrThrow("dataFormatVersion");
            while (c.moveToNext()) {
                if (c.isNull(colData)) {
                    continue;
                }
                final long id = c.getLong(colId);
                final String rowBookName = c.isNull(colBookName) ? null : c.getString(colBookName);
                final String rowCode = c.isNull(colCode) ? null : c.getString(colCode);
                final byte[] data = c.getBlob(colData);
                final int dataFormatVersion = c.getInt(colDataFormatVersion);
                // Read out of the row before writing back, so the write-back UPDATE (issued at most
                // once per legacy row, inside readDocument) doesn't fight this streaming Cursor.
                final SongDocument doc = readDocument(id, rowBookName, rowCode, data, dataFormatVersion);
                if (SongFilter.match(doc, cf)) {
                    res.add(new SongInfo(
                        rowBookName,
                        rowCode,
                        c.isNull(colTitle) ? null : c.getString(colTitle),
                        c.isNull(colTitleOriginal) ? null : c.getString(colTitleOriginal),
                        // preview up to 2 matching lyric lines under the result (deep search only)
                        SongFilter.findLyricSnippet(doc, cf, 2)
                    ));
                }
            }
        }
        return res;
    }

    /** @return number of songs deleted */
    public int deleteSongBook(final String songBookName) {
        final int count = roomDao().deleteSongBookAndSongs(songBookName);
        // Reclaims the space a song book's worth of rows used. Room cannot
        // VACUUM inside a transaction, so this runs after the DAO's commits.
        SongRoomDatabase.get(App.context).getOpenHelper().getWritableDatabase().execSQL("vacuum");
        return count;
    }

    @Nullable
    public SongBookUtil.SongBookInfo getSongBookInfo(final String name) {
        final SongBookInfoEntity row = roomDao().findSongBookInfoByName(name);
        if (row == null) {
            return null;
        }
        final SongBookUtil.SongBookInfo res = new SongBookUtil.SongBookInfo();
        res.name = row.getName();
        res.title = row.getTitle();
        res.copyright = row.getCopyright();
        return res;
    }

    public List<SongBookUtil.SongBookInfo> listSongBookInfos() {
        final List<SongBookInfoEntity> rows = roomDao().listAllSongBookInfos();
        final List<SongBookUtil.SongBookInfo> res = new ArrayList<>(rows.size());
        for (SongBookInfoEntity row : rows) {
            final SongBookUtil.SongBookInfo info = new SongBookUtil.SongBookInfo();
            info.name = row.getName();
            info.title = row.getTitle();
            info.copyright = row.getCopyright();
            res.add(info);
        }
        return res;
    }

    public int countSongBookInfos() {
        return roomDao().countAllSongBookInfos();
    }

    /**
     * Insert a songbook info row. An existing songbook with the same name, if exists, will be deleted.
     */
    public void insertSongBookInfo(final SongBookUtil.SongBookInfo info) {
        roomDao().insertOrReplaceSongBookInfo(info.name, info.title, info.copyright);
    }

    public int getSongUpdateTime(final String bookName, final String code) {
        final Integer res = roomDao().findUpdateTimeByBookNameAndCode(bookName, code);
        return res == null ? 0 : res;
    }

    /**
     * Legacy-only helper that reads a song-book row from the pre-Room
     * {@code SongDb} SQLite file. Used by {@link SongDbHelper#onUpgrade}'s
     * 4.1-beta2 upgrade path to back-fill {@code SongBookInfo} from the
     * pre-existing {@code SongInfo} table — that upgrade still has to run
     * before {@code SongDbDataMigration} copies the result into Room.
     * Not a Room-aware path.
     */
    public static SongBookUtil.SongBookInfo getSongBookInfo(final SQLiteDatabase db, final String name) {
        try (Cursor c = db.query(Table.SongBookInfo.tableName(), null, Table.SongBookInfo.name + "=?", Array(name), null, null, null)) {
            if (c.moveToNext()) {
                final SongBookUtil.SongBookInfo res = new SongBookUtil.SongBookInfo();
                res.name = name;
                res.title = c.getString(c.getColumnIndexOrThrow(Table.SongBookInfo.title.name()));
                res.copyright = c.getString(c.getColumnIndexOrThrow(Table.SongBookInfo.copyright.name()));
                return res;
            }
            return null;
        }
    }

    /**
     * Legacy-only helper that writes a song-book row to the pre-Room
     * {@code SongDb} SQLite file. Used by {@link SongDbHelper#onUpgrade}.
     * Not a Room-aware path.
     */
    static void insertSongBookInfo(final SQLiteDatabase db, final SongBookUtil.SongBookInfo info) {
        db.beginTransactionNonExclusive();
        try {
            db.delete(Table.SongBookInfo.tableName(), Table.SongBookInfo.name + "=?", Array(info.name));

            final ContentValues cv = new ContentValues();
            cv.put(Table.SongBookInfo.name.name(), info.name);
            cv.put(Table.SongBookInfo.title.name(), info.title);
            cv.put(Table.SongBookInfo.copyright.name(), info.copyright);
            db.insert(Table.SongBookInfo.tableName(), null, cv);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
