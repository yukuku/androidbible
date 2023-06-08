package yuku.alkitab.base.storage;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.DatabaseUtils.InsertHelper;
import android.database.sqlite.SQLiteDatabase;
import android.os.Parcel;
import android.util.Pair;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import yuku.alkitab.base.util.AppLog;
import static yuku.alkitab.base.util.Literals.Array;
import static yuku.alkitab.base.util.Literals.ToStringArray;
import yuku.alkitab.base.util.Sqlitil;
import yuku.alkitab.songs.SongBookUtil;
import yuku.alkitab.songs.SongFilter;
import yuku.alkitab.songs.SongFilter.CompiledFilter;
import yuku.alkitab.songs.SongInfo;
import yuku.kpri.model.Song;

public class SongDb {
    private final SongDbHelper helper;

    public SongDb(SongDbHelper helper) {
        this.helper = helper;
    }

    private static byte[] marshallSong(Song song, int dataFormatVersion) {
        Parcel p = Parcel.obtain();
        song.writeToParcelCompat(dataFormatVersion, p, 0);
        byte[] buf = p.marshall();
        p.recycle();
        AppLog.d("Dumper!", "song " + song.code);
        DumperKt.dumpBytes(buf);
        return buf;
    }

private static Song unmarshallSong(byte[] buf, int dataFormatVersion) {
    Parcel p = Parcel.obtain();
    p.unmarshall(buf, 0, buf.length);
    p.setDataPosition(0);
    Song res = Song.createFromParcelCompat(dataFormatVersion, p);
    p.recycle();
    return res;
}

    /**
     * Store to db songs in a book. Before the songs are stored, all songs of the specified book are deleted.
     */
    public void storeSongs(String bookName, List<Song> songs, int dataFormatVersion) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransactionNonExclusive();
        try {
            // remove existing songs from the same book if any
            db.delete(Table.SongInfo.tableName(),
                Table.SongInfo.bookName + "=? and " + Table.SongInfo.dataFormatVersion + "=?",
                ToStringArray(bookName, dataFormatVersion)
            );

            int ordering = 1; // ordering of the songs for display

            // insert new ones
            @SuppressWarnings("deprecation") final InsertHelper ih = new InsertHelper(db, Table.SongInfo.tableName());

            int col_bookName = ih.getColumnIndex(Table.SongInfo.bookName.name());
            int col_code = ih.getColumnIndex(Table.SongInfo.code.name());
            int col_title = ih.getColumnIndex(Table.SongInfo.title.name());
            int col_title_original = ih.getColumnIndex(Table.SongInfo.title_original.name());
            int col_ordering = ih.getColumnIndex(Table.SongInfo.ordering.name());
            int col_dataFormatVersion = ih.getColumnIndex(Table.SongInfo.dataFormatVersion.name());
            int col_data = ih.getColumnIndex(Table.SongInfo.data.name());
            int col_updateTime = ih.getColumnIndex(Table.SongInfo.updateTime.name());

            for (Song song : songs) {
                ih.prepareForInsert();
                ih.bind(col_bookName, bookName);
                ih.bind(col_code, song.code);
                ih.bind(col_title, song.title);
                ih.bind(col_title_original, song.title_original);
                ih.bind(col_ordering, ordering++);
                ih.bind(col_dataFormatVersion, dataFormatVersion);
                ih.bind(col_data, marshallSong(song, dataFormatVersion));
                ih.bind(col_updateTime, Sqlitil.nowDateTime());
                ih.execute();
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public Song getSong(String bookName, String code) {
        SQLiteDatabase db = helper.getReadableDatabase();

        String[] columns = new String[]{
            Table.SongInfo.data.name(), // 0
            Table.SongInfo.dataFormatVersion.name(), // 1
        };

        try (Cursor c = db.query(
            Table.SongInfo.tableName(),
            columns,
            Table.SongInfo.bookName + "=? and " + Table.SongInfo.code + "=?",
            new String[]{bookName, code},
            null,
            null,
            null
        )) {
            if (c.moveToNext()) {
                byte[] data = c.getBlob(0);
                int dataFormatVersion = c.getInt(1);
                return unmarshallSong(data, dataFormatVersion);
            } else {
                return null;
            }
        }
    }

    public boolean songExists(String bookName, String code) {
        SQLiteDatabase db = helper.getReadableDatabase();

        try (Cursor c = db.rawQuery("select count(*) from " + Table.SongInfo.tableName() + " where "
                + Table.SongInfo.bookName + "=? and " + Table.SongInfo.code + "=?",
            new String[]{bookName, code}
        )) {
            if (c.moveToNext()) {
                return c.getInt(0) > 0;
            } else {
                return false;
            }
        }
    }

    public Song getFirstSongFromBook(String bookName) {
        SQLiteDatabase db = helper.getReadableDatabase();

        String[] columns = new String[]{ // column indexes!
            Table.SongInfo.data.name(), // 0
            Table.SongInfo.dataFormatVersion.name(), // 1
        };

        Cursor c = db.query(Table.SongInfo.tableName(),
            columns,
            Table.SongInfo.bookName + "=?",
            new String[]{bookName},
            null, null, Table.SongInfo.ordering + " asc", "1");

        try {
            if (c.moveToNext()) {
                byte[] data = c.getBlob(0);
                int dataFormatVersion = c.getInt(1);
                return unmarshallSong(data, dataFormatVersion);
            } else {
                return null;
            }
        } finally {
            c.close();
        }
    }

    /**
     * @return null if there is no song at all
     */
    @Nullable
    public Pair<String /* bookName */, Song> getAnySong() {
        final SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(Table.SongInfo.tableName(), ToStringArray(Table.SongInfo.bookName, Table.SongInfo.data, Table.SongInfo.dataFormatVersion), null, null, null, null, Table.SongInfo.bookName + " asc, " + Table.SongInfo.ordering + " asc", "1")) {
            if (c.moveToNext()) {
                final String bookName = c.getString(0);
                final byte[] data = c.getBlob(1);
                final int dataFormatVersion = c.getInt(2);
                final Song song = unmarshallSong(data, dataFormatVersion);
                return Pair.create(bookName, song);
            } else {
                return null;
            }
        }
    }

    public List<SongInfo> listSongInfosByBookName(String bookName) {
        SQLiteDatabase db = helper.getReadableDatabase();
        List<SongInfo> res = new ArrayList<>();

        String[] columns = { // column indexes!
            Table.SongInfo.bookName.name(), // 0
            Table.SongInfo.code.name(), // 1
            Table.SongInfo.title.name(), // 2
            Table.SongInfo.title_original.name(), // 3
        };

        try (Cursor c = querySongs(db, columns, bookName)) {
            while (c.moveToNext()) {
                String bookName2 = c.getString(0);
                String code = c.getString(1);
                String title = c.getString(2);
                String title_original = c.getString(3);
                res.add(new SongInfo(bookName2, code, title, title_original));
            }
        }

        return res;
    }

    public List<SongInfo> listSongInfosByBookNameAndDeepFilter(String bookName, String filter_string) {
        SQLiteDatabase db = helper.getReadableDatabase();

        List<SongInfo> res = new ArrayList<>();

        String[] columns = { // column indexes!
            Table.SongInfo.bookName.name(), // 0
            Table.SongInfo.code.name(), // 1
            Table.SongInfo.title.name(), // 2
            Table.SongInfo.title_original.name(), // 3
            Table.SongInfo.data.name(), // 4
            Table.SongInfo.dataFormatVersion.name(), // 5
        };

        CompiledFilter cf = SongFilter.compileFilter(filter_string);

        try (Cursor c = querySongs(db, columns, bookName)) {
            while (c.moveToNext()) {
                String bookName2 = c.getString(0);
                String code = c.getString(1);
                String title = c.getString(2);
                String title_original = c.getString(3);
                byte[] data = c.getBlob(4);
                int dataFormatVersion = c.getInt(5);

                Song song = unmarshallSong(data, dataFormatVersion);
                if (SongFilter.match(song, cf)) {
                    res.add(new SongInfo(bookName2, code, title, title_original));
                }
            }
        }

        return res;
    }

    private static Cursor querySongs(SQLiteDatabase db, String[] columns, String bookName) {
        Cursor c;
        if (bookName == null) {
            c = db.query(Table.SongInfo.tableName(),
                columns,
                null,
                null,
                null, null, Table.SongInfo.bookName + " asc, " + Table.SongInfo.ordering + " asc");
        } else {
            c = db.query(Table.SongInfo.tableName(),
                columns,
                Table.SongInfo.bookName + "=?",
                new String[]{bookName},
                null, null, Table.SongInfo.ordering + " asc");
        }
        return c;
    }

    /**
     * Delete song book together with its songs.
     *
     * @return number of songs deleted
     */
    public int deleteSongBook(final String songBookName) {
        final SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransactionNonExclusive();
        try {
            // delete song book
            db.delete(Table.SongBookInfo.tableName(), Table.SongBookInfo.name + "=?", Array(songBookName));

            // delete songs
            final int count = db.delete(Table.SongInfo.tableName(), Table.SongInfo.bookName + "=?", Array(songBookName));

            db.setTransactionSuccessful();

            return count;
        } finally {
            db.endTransaction();
            db.execSQL("vacuum");
        }
    }

    @Nullable
    public SongBookUtil.SongBookInfo getSongBookInfo(final String name) {
        final SQLiteDatabase db = helper.getReadableDatabase();
        return getSongBookInfo(db, name);
    }

    /**
     * For migration
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

    public List<SongBookUtil.SongBookInfo> listSongBookInfos() {
        final SQLiteDatabase db = helper.getReadableDatabase();
        try (Cursor c = db.query(Table.SongBookInfo.tableName(), null, null, null, null, null, Table.SongBookInfo.name + " asc")) {
            final int col_name = c.getColumnIndexOrThrow(Table.SongBookInfo.name.name());
            final int col_title = c.getColumnIndexOrThrow(Table.SongBookInfo.title.name());
            final int col_copyright = c.getColumnIndexOrThrow(Table.SongBookInfo.copyright.name());

            final List<SongBookUtil.SongBookInfo> res = new ArrayList<>();
            while (c.moveToNext()) {
                final SongBookUtil.SongBookInfo info = new SongBookUtil.SongBookInfo();
                info.name = c.getString(col_name);
                info.title = c.getString(col_title);
                info.copyright = c.getString(col_copyright);
                res.add(info);
            }

            return res;
        }
    }

    public int countSongBookInfos() {
        final SQLiteDatabase db = helper.getReadableDatabase();
        return (int) DatabaseUtils.queryNumEntries(db, Table.SongBookInfo.tableName());
    }

    /**
     * Insert a songbook info row. An existing songbook with the same name, if exists, will be deleted.
     */
    public void insertSongBookInfo(final SongBookUtil.SongBookInfo info) {
        final SQLiteDatabase db = helper.getWritableDatabase();
        insertSongBookInfo(db, info);
    }

    /**
     * For migration
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

    public int getDataFormatVersionForSongs(final String bookName) {
        final SQLiteDatabase db = helper.getReadableDatabase();
        return (int) DatabaseUtils.longForQuery(db, "select " + Table.SongInfo.dataFormatVersion + " from " + Table.SongInfo.tableName() + " where " + Table.SongInfo.bookName + "=? limit 1", Array(bookName));
    }

    public int getSongUpdateTime(final String bookName, final String code) {
        final SQLiteDatabase db = helper.getReadableDatabase();
        return (int) DatabaseUtils.longForQuery(db, "select " + Table.SongInfo.updateTime + " from " + Table.SongInfo.tableName() + " where " + Table.SongInfo.bookName + "=? and " + Table.SongInfo.code + "=?", Array(bookName, code));
    }
}
