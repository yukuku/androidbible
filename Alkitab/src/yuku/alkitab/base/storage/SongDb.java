package yuku.alkitab.base.storage;

import android.database.Cursor;
import android.database.DatabaseUtils.InsertHelper;
import android.database.sqlite.SQLiteDatabase;
import android.os.Parcel;
import android.util.TimingLogger;
import yuku.alkitab.base.util.SongFilter;
import yuku.alkitab.base.util.SongFilter.CompiledFilter;
import yuku.alkitab.model.SongInfo;
import yuku.kpri.model.Song;

import java.util.ArrayList;
import java.util.List;

public class SongDb extends yuku.afw.storage.InternalDb {
	public static final String TAG = SongDb.class.getSimpleName();

	public SongDb(SongDbHelper helper) {
		super(helper);
	}
	
	private static byte[] marshallSong(Song song, int dataFormatVersion) {
		Parcel p = Parcel.obtain();
		song.writeToParcelCompat(dataFormatVersion, p, 0);
		byte[] buf = p.marshall();
		p.recycle();
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
	
	public void storeSongs(String bookName, List<Song> songs, int dataFormatVersion) {
		TimingLogger tl = new TimingLogger(TAG, "storeSongs"); //$NON-NLS-1$
		SQLiteDatabase db = helper.getWritableDatabase();
		db.beginTransaction();
		try {
			// remove existing one if exists
			for (Song song: songs) {
				db.delete(Table.SongInfo.tableName(), 
				Table.SongInfo.bookName + "=? and " + Table.SongInfo.code + "=? and " + Table.SongInfo.dataFormatVersion + "=?", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				new String[] {bookName, song.code, "" + dataFormatVersion} //$NON-NLS-1$
				);
			}
			tl.addSplit("finished deleting existings (if any)"); //$NON-NLS-1$
			
			int ordering = 1; // ordering of the songs for display
			
			// insert new ones
			InsertHelper ih = new InsertHelper(db, Table.SongInfo.tableName());
			
			int col_bookName = ih.getColumnIndex(Table.SongInfo.bookName.name());
			int col_code = ih.getColumnIndex(Table.SongInfo.code.name());
			int col_title = ih.getColumnIndex(Table.SongInfo.title.name());
			int col_title_original = ih.getColumnIndex(Table.SongInfo.title_original.name());
			int col_ordering = ih.getColumnIndex(Table.SongInfo.ordering.name());
			int col_dataFormatVersion = ih.getColumnIndex(Table.SongInfo.dataFormatVersion.name());
			int col_data = ih.getColumnIndex(Table.SongInfo.data.name());
			
			tl.addSplit("The real insertion of " + songs.size()); //$NON-NLS-1$
			
			for (Song song: songs) {
				ih.prepareForInsert();
				ih.bind(col_bookName, bookName);
				ih.bind(col_code, song.code);
				ih.bind(col_title, song.title);
				ih.bind(col_title_original, song.title_original);
				ih.bind(col_ordering, ordering++);
				ih.bind(col_dataFormatVersion, dataFormatVersion);
				ih.bind(col_data, marshallSong(song, dataFormatVersion));
				ih.execute();
			}
			
			tl.addSplit("Real insertion finished"); //$NON-NLS-1$
			
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
			tl.dumpToLog();
		}
	}
	
	public Song getSong(String bookName, String code) {
		SQLiteDatabase db = helper.getReadableDatabase();
		
		String[] columns = new String[] {
		Table.SongInfo.data.name(), // 0
		Table.SongInfo.dataFormatVersion.name(), // 1
		};
		
		Cursor c = db.query(Table.SongInfo.tableName(), 
		columns, 
		Table.SongInfo.bookName + "=? and " + Table.SongInfo.code + "=?",  //$NON-NLS-1$ //$NON-NLS-2$
		new String[] {bookName, code}, 
		null, null, null);
		
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
	
	public boolean songExists(String bookName, String code) {
		SQLiteDatabase db = helper.getReadableDatabase();
		
		Cursor c = db.rawQuery("select count(*) from " + Table.SongInfo.tableName() + " where "  //$NON-NLS-1$ //$NON-NLS-2$
		+ Table.SongInfo.bookName + "=? and " + Table.SongInfo.code + "=?",  //$NON-NLS-1$ //$NON-NLS-2$ 
		new String[] {bookName, code}); 

		try {
			if (c.moveToNext()) {
				return c.getInt(0) > 0;
			} else {
				return false;
			}
		} finally {
			c.close();
		}
	}
	
	public Song getFirstSongFromBook(String bookName) {
		SQLiteDatabase db = helper.getReadableDatabase();
		
		String[] columns = new String[] { // column indexes!
		Table.SongInfo.data.name(), // 0
		Table.SongInfo.dataFormatVersion.name(), // 1
		};
		
		Cursor c = db.query(Table.SongInfo.tableName(), 
		columns, 
		Table.SongInfo.bookName + "=?",  //$NON-NLS-1$ 
		new String[] {bookName}, 
		null, null, Table.SongInfo.ordering + " asc", "1"); //$NON-NLS-1$ //$NON-NLS-2$
		
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

	public List<SongInfo> listSongInfosByBookName(String bookName) {
		SQLiteDatabase db = helper.getReadableDatabase();
		List<SongInfo> res = new ArrayList<>();

		String[] columns = { // column indexes!
		Table.SongInfo.bookName.name(), // 0
		Table.SongInfo.code.name(), // 1
		Table.SongInfo.title.name(), // 2
		Table.SongInfo.title_original.name(), // 3
		};
	
		Cursor c = querySongs(db, columns, bookName);
		try {
			while (c.moveToNext()) {
				String bookName2 = c.getString(0);
				String code = c.getString(1);
				String title = c.getString(2);
				String title_original = c.getString(3);
				res.add(new SongInfo(bookName2, code, title, title_original));
			}
		} finally {
			c.close();
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
		
		Cursor c = querySongs(db, columns, bookName);
		try {
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
		} finally {
			c.close();
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
			null, null, Table.SongInfo.bookName + " asc, " + Table.SongInfo.ordering + " asc"); //$NON-NLS-1$ //$NON-NLS-2$
		} else {
			c = db.query(Table.SongInfo.tableName(), 
			columns, 
			Table.SongInfo.bookName + "=?",  //$NON-NLS-1$ 
			new String[] {bookName},
			null, null, Table.SongInfo.ordering + " asc"); //$NON-NLS-1$
		}
		return c;
	}

	public int deleteAllSongs() {
		SQLiteDatabase db = helper.getWritableDatabase();
		int count = db.delete(Table.SongInfo.tableName(), "1", null); //$NON-NLS-1$
		db.execSQL("vacuum"); //$NON-NLS-1$
		return count;
	}
}
