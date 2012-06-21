package yuku.alkitab.base.storage;

import android.database.Cursor;
import android.database.DatabaseUtils.InsertHelper;
import android.database.sqlite.SQLiteDatabase;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.TimingLogger;

import java.util.ArrayList;
import java.util.List;

import yuku.alkitab.base.model.SongInfo;
import yuku.alkitab.base.util.SongFilter;
import yuku.alkitab.base.util.SongFilter.CompiledFilter;
import yuku.kpri.model.Song;

public class SongDb extends yuku.afw.storage.InternalDb {
	public static final String TAG = SongDb.class.getSimpleName();

	public SongDb(SongDbHelper helper) {
		super(helper);
	}
	
	private static byte[] marshall(Parcelable o) {
		Parcel p = Parcel.obtain();
		o.writeToParcel(p, 0);
		byte[] buf = p.marshall();
		p.recycle();
		return buf;
	}
	
	private static <T extends Parcelable> T unmarshall(byte[] buf, Parcelable.Creator<T> creator) {
		Parcel p = Parcel.obtain();
		p.unmarshall(buf, 0, buf.length);
		p.setDataPosition(0);
		T res = creator.createFromParcel(p);
		p.recycle();
		return res;
	}
	
	public void storeSongs(String bookName, List<Song> songs, int dataFormatVersion) {
		TimingLogger tl = new TimingLogger(TAG, "storeSongs");
		SQLiteDatabase db = helper.getWritableDatabase();
		db.beginTransaction();
		try {
			// remove existing one if exists
			for (Song song: songs) {
				db.delete(Table.SongInfo.tableName(), 
				Table.SongInfo.bookName + "=? and " + Table.SongInfo.code + "=? and " + Table.SongInfo.dataFormatVersion + "=?",
				new String[] {bookName, song.code, "" + dataFormatVersion}
				);
			}
			tl.addSplit("finished deleting existings (if any)");
			
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
			
			tl.addSplit("The real insertion of " + songs.size());
			
			for (Song song: songs) {
				ih.prepareForInsert();
				ih.bind(col_bookName, bookName);
				ih.bind(col_code, song.code);
				ih.bind(col_title, song.title);
				ih.bind(col_title_original, song.title_original);
				ih.bind(col_ordering, ordering++);
				ih.bind(col_dataFormatVersion, dataFormatVersion);
				ih.bind(col_data, marshall(song));
				ih.execute();
			}
			
			tl.addSplit("Real insertion finished");
			
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
			tl.dumpToLog();
		}
	}
	
	public Song getSong(String bookName, String code, int dataFormatVersion) {
		SQLiteDatabase db = helper.getReadableDatabase();
		
		Cursor c = db.query(Table.SongInfo.tableName(), 
		new String[] {Table.SongInfo.data.name()}, 
		Table.SongInfo.bookName + "=? and " + Table.SongInfo.code + "=? and " + Table.SongInfo.dataFormatVersion + "=?", 
		new String[] {bookName, code, "" + dataFormatVersion},
		null, null, null);
		
		try {
			if (c.moveToNext()) {
				byte[] data = c.getBlob(0); // ensure col index is correct
				return unmarshall(data, Song.CREATOR);
			} else {
				return null;
			}
		} finally {
			c.close();
		}
	}
	
	public boolean songExists(String bookName, String code, int dataFormatVersion) {
		SQLiteDatabase db = helper.getReadableDatabase();
		
		Cursor c = db.rawQuery("select count(*) from " + Table.SongInfo.tableName() + " where " 
		+ Table.SongInfo.bookName + "=? and " + Table.SongInfo.code + "=? and " + Table.SongInfo.dataFormatVersion + "=?", 
		new String[] {bookName, code, "" + dataFormatVersion});

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
	
	public Song getFirstSongFromBook(String bookName, int dataFormatVersion) {
		SQLiteDatabase db = helper.getReadableDatabase();
		
		Cursor c = db.query(Table.SongInfo.tableName(), 
		new String[] {Table.SongInfo.data.name()}, 
		Table.SongInfo.bookName + "=? and " + Table.SongInfo.dataFormatVersion + "=?", 
		new String[] {bookName, "" + dataFormatVersion},
		null, null, Table.SongInfo.ordering + " asc", "1");
		
		try {
			if (c.moveToNext()) {
				byte[] data = c.getBlob(0); // ensure col index is correct
				return unmarshall(data, Song.CREATOR);
			} else {
				return null;
			}
		} finally {
			c.close();
		}
	}

	public List<SongInfo> getSongInfosByBookName(String bookName, int dataFormatVersion) {
		SQLiteDatabase db = helper.getReadableDatabase();
		List<SongInfo> res = new ArrayList<SongInfo>();

		String[] columns = { // column indexes!
		Table.SongInfo.bookName.name(), // 0
		Table.SongInfo.code.name(), // 1
		Table.SongInfo.title.name(), // 2
		Table.SongInfo.title_original.name(), // 3
		};
	
		Cursor c = querySongs(db, columns, bookName, dataFormatVersion);
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

	public List<SongInfo> getSongInfosByBookNameAndDeepFilter(String bookName, String filter_string, int dataFormatVersion) {
		SQLiteDatabase db = helper.getReadableDatabase();
		
		List<SongInfo> res = new ArrayList<SongInfo>();

		String[] columns = { // column indexes!
		Table.SongInfo.bookName.name(), // 0
		Table.SongInfo.code.name(), // 1
		Table.SongInfo.title.name(), // 2
		Table.SongInfo.title_original.name(), // 3
		Table.SongInfo.data.name(), // 4
		};
		
		CompiledFilter cf = SongFilter.compileFilter(filter_string);
		
		Cursor c = querySongs(db, columns, bookName, dataFormatVersion);
		try {
			while (c.moveToNext()) {
				String bookName2 = c.getString(0);
				String code = c.getString(1);
				String title = c.getString(2);
				String title_original = c.getString(3);
				byte[] data = c.getBlob(4);
				
				Song song = unmarshall(data, Song.CREATOR);
				if (SongFilter.match(song, cf)) {
					res.add(new SongInfo(bookName2, code, title, title_original));
				}
			}
		} finally {
			c.close();
		}
		
		return res;
	}

	private static Cursor querySongs(SQLiteDatabase db, String[] columns, String bookName, int dataFormatVersion) {
		Cursor c;
		if (bookName == null) {
			c = db.query(Table.SongInfo.tableName(), 
			columns, 
			Table.SongInfo.dataFormatVersion + "=?", 
			new String[] {"" + dataFormatVersion},
			null, null, Table.SongInfo.bookName + " asc, " + Table.SongInfo.ordering + " asc");
		} else {
			c = db.query(Table.SongInfo.tableName(), 
			columns, 
			Table.SongInfo.bookName + "=? and " + Table.SongInfo.dataFormatVersion + "=?", 
			new String[] {bookName, "" + dataFormatVersion},
			null, null, Table.SongInfo.ordering + " asc");
		}
		return c;
	}
}
