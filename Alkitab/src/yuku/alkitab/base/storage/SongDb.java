package yuku.alkitab.base.storage;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils.InsertHelper;
import android.database.sqlite.SQLiteDatabase;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.TimingLogger;

import java.util.List;

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
	
	public void storeSongs(String bookName, List<Song> songs, int dataFormatVersion, ContentValues reuseCv) {
		TimingLogger tl = new TimingLogger(TAG, "storeSongs");
		SQLiteDatabase db = helper.getWritableDatabase();
		db.beginTransaction();
		try {
			// remove existing one if exists
			for (Song song: songs) {
				db.delete(Table.Song.tableName(), 
				Table.Song.bookName + "=? and " + Table.Song.code + "=? and " + Table.Song.dataFormatVersion + "=?",
				new String[] {bookName, song.code, "" + dataFormatVersion}
				);
			}
			tl.addSplit("finished deleting existings (if any)");
			
			int ordering = 1; // ordering of the songs for display
			
			// insert new ones
			InsertHelper ih = new InsertHelper(db, Table.Song.tableName());
			
			int col_bookName = ih.getColumnIndex(Table.Song.bookName.name());
			int col_code = ih.getColumnIndex(Table.Song.code.name());
			int col_title = ih.getColumnIndex(Table.Song.title.name());
			int col_title_original = ih.getColumnIndex(Table.Song.title_original.name());
			int col_ordering = ih.getColumnIndex(Table.Song.ordering.name());
			int col_dataFormatVersion = ih.getColumnIndex(Table.Song.dataFormatVersion.name());
			int col_data = ih.getColumnIndex(Table.Song.data.name());
			
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
		
		Cursor c = db.query(Table.Song.tableName(), 
		new String[] {Table.Song.data.name()}, 
		Table.Song.bookName + "=? and " + Table.Song.code + "=? and " + Table.Song.dataFormatVersion + "=?", 
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
}
