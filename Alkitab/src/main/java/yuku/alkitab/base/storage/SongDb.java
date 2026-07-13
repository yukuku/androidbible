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
 * Facade over the Room-backed {@link SongRoomDao} that preserves the
 * legacy {@code SongInfo} / {@code SongBookInfo} public surface. Existing
 * call sites in {@code SongListActivity}, {@code SongViewActivity}, and
 * {@code SongBookUtil} don't need to change.
 *
 * <p>Two tables, one facade: both belong to the Songs subsystem and are
 * commonly written together during song-book installation
 * ({@code SongBookUtil.handleDownloadResult} calls
 * {@link #insertSongBookInfo(SongBookUtil.SongBookInfo)} then
 * {@link #storeSongs(String, List, int)}).
 *
 * <p>Cross-file note: this DAO is constructed with {@link SongDbHelper}
 * only so its constructor signature stays unchanged. The helper is no
 * longer used internally; {@link SongRoomDatabase} supplies the underlying
 * SQLite file. The one-time data copy runs in
 * {@code yuku.alkitab.base.storage.room.SongDbDataMigration}.
 *
 * <p>{@code VACUUM} preservation: {@link #deleteSongBook(String)} retains
 * the legacy facade's post-delete {@code VACUUM} to reclaim disk space
 * after dropping a song book's worth of rows. Room cannot run
 * {@code VACUUM} inside a transaction, so the facade issues it via the
 * underlying {@code SupportSQLiteDatabase} after the DAO transaction has
 * committed.
 *
 * <p>REM-21 (Parcelable → JSON, portable-songs android-implementation-plan.md
 * §5/§6) layers on top of this storage-engine swap: {@link #writeDocument}
 * always writes UTF-8 JSON at {@link SongDocumentJson#DATA_FORMAT_VERSION};
 * {@link #readDocument} dispatches JSON-vs-legacy-Parcelable by the row's
 * {@code dataFormatVersion} and lazily rewrites legacy rows as JSON the
 * first time they're read (at most once per row). The BLOB column itself
 * (opaque {@code byte[]}) is unchanged by either REM-32 or REM-21 — only
 * what's inside it changed.
 */
public class SongDb {
    @SuppressWarnings("unused") // kept for ABI parity with the pre-Room constructor
    private final SongDbHelper helper;

    // `volatile` + double-checked locking so concurrent callers either
    // see the fully-published `SongRoomDao` reference or do one extra
    // resolution under the lock. The underlying `SongRoomDatabase.get`
    // is itself thread-safe and returns the same singleton, so a benign
    // double init is functionally harmless — but without `volatile` the
    // JMM could publish a partially-initialised reference, and explicit
    // DCL keeps the contract obvious at the call site.
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

    /**
     * @see SongDocumentJson#DATA_FORMAT_VERSION
     */
    private static byte[] writeDocument(SongDocument doc) {
        return SongDocumentJson.encode(doc).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * design §4.5 fallback: if the pure-JVM {@link LegacyParcelDecoder} throws (an
     * unrecognised wire shape), fall back to the platform {@code Parcel.unmarshall()} path, which
     * still works as long as the OS hasn't changed since the row was written.
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
     * Single song-read helper (android-implementation-plan.md §5): dispatches JSON vs. legacy
     * Parcelable by {@code dataFormatVersion}. Legacy rows are decoded via {@link LegacyParcelDecoder}
     * (falling back to the platform {@link Parcel} if that throws), converted to a
     * {@link SongDocument} via {@link LegacySongConverter}, and the JSON is written back to the row
     * so the conversion happens at most once per row.
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
        roomDao().writeBackJsonSongData(id, writeDocument(doc));
        return doc;
    }

    /**
     * Store to db songs in a book. Before the songs are stored, all songs of the specified book are deleted.
     */
    public void storeSongs(String bookName, List<SongDocument> docs, int dataFormatVersion) {
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
                dataFormatVersion,
                writeDocument(doc),
                updateTime
            ));
        }
        roomDao().replaceSongsForBookNameAndDataFormatVersion(bookName, dataFormatVersion, entities);
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
        // Metadata-only listing: deliberately avoid SELECT * so we don't
        // pull the multi-kB `data` BLOB into the heap for every row. The
        // legacy facade used the same column-list trick (cf. the pre-Room
        // `listSongInfosByBookName` query projection).
        // Null bookName means "All song books" (the song list's book
        // selector), same contract as the legacy facade's querySongs.
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
        // Stream rows via a Cursor instead of materialising every row
        // (with its `data` BLOB) into a Room-returned List. For
        // `bookName == null` that List would hold the entire song
        // catalogue in memory; on a device with several large song books
        // installed that's an OOM risk. The Cursor walks one row at a
        // time, deserialises + tests + drops, bounding memory by the
        // CursorWindow + one song. Matches the legacy facade's streaming
        // behaviour.
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
                        c.isNull(colTitleOriginal) ? null : c.getString(colTitleOriginal)
                    ));
                }
            }
        }
        return res;
    }

    /**
     * Delete song book together with its songs.
     *
     * @return number of songs deleted
     */
    public int deleteSongBook(final String songBookName) {
        final int count = roomDao().deleteSongBookAndSongs(songBookName);
        // Reclaim the disk space the deleted rows used. The legacy facade
        // ran `VACUUM` after committing its transaction; preserve that
        // behaviour by issuing it against the underlying SupportSQLite
        // database outside any Room transaction.
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

    public int getDataFormatVersionForSongs(final String bookName) {
        final Integer res = roomDao().findDataFormatVersionForBookName(bookName);
        return res == null ? 0 : res;
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
