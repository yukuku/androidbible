package yuku.alkitabintegration.provider;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

import yuku.alkitabintegration.AlkitabIntegrationUtil;

public class VerseProvider {
	public static final String TAG = VerseProvider.class.getSimpleName();
	
	public static class Verse {
		public long _id;
		public int ari;
		public String bookName;
		public String text;
		
		public int getBookId() {
			return (0xff0000 & ari) >> 16;
		}
		
		public int getChapter() {
			return (0x00ff00 & ari) >> 8;
		}
		
		public int getVerse() {
			return (0x0000ff & ari);
		}
		
		@Override public String toString() {
			return bookName + " " + getChapter() + ":" + getVerse() + " " + text;
		}
	}
	
	public static class VerseRanges {
		private int[] ranges = new int[16];
		private int size = 0;
		
		public void addRange(int bookId_start, int chapter_1_start, int verse_1_start, int bookId_end, int chapter_1_end, int verse_1_end) {
			int start = (bookId_start << 16) | (chapter_1_start << 8) | verse_1_start;
			int end = (bookId_end << 16) | (chapter_1_end << 8) | verse_1_end;
			
			addRange(start, end);
		}

		public void addRange(int ari_start, int ari_end) {
			if (ranges.length < size + 2) {
				int[] ranges_new = new int[ranges.length * 2];
				System.arraycopy(ranges, 0, ranges_new, 0, size);
				ranges = ranges_new;
			}
			
			ranges[size++] = ari_start;
			ranges[size++] = ari_end;
		}
		
		@Override public String toString() {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < size; i += 2) {
				if (sb.length() != 0) {
					sb.append(',');
				}
				sb.append(ranges[i]).append('-').append(ranges[i+1]);
			}
			return sb.toString();
		}
	}
	
	public static final String PATH_bible_verses_single_by_lid = "bible/verses/single/by-lid/";
	public static final String PATH_bible_verses_single_by_ari = "bible/verses/single/by-ari/";
	public static final String PATH_bible_verses_range_by_lid = "bible/verses/range/by-lid/";
	public static final String PATH_bible_verses_range_by_ari = "bible/verses/range/by-ari/";

	public static final String COLUMN_ari = "ari";
	public static final String COLUMN_bookName = "bookName";
	public static final String COLUMN_text = "text";
	
	private ContentResolver cr;
	
	public VerseProvider(ContentResolver cr) {
		this.cr = cr;
	}
	
	/**
	 * Reads a single verse from the default version.
	 * @param bookId 0 for Genesis, up to 65 for Revelation
	 * @param chapter_1 Chapter number starting from 1
	 * @param verse_1 Verse number starting from 1
	 * @return null when failed or the requested verse
	 */
	public Verse getVerse(int bookId, int chapter_1, int verse_1) {
		int ari = (bookId << 16) | (chapter_1 << 8) | (verse_1);
		return getVerse(ari);
	}

	/**
	 * Reads a single verse from the default version.
	 * @param ari a combination of bookId (byte 2), chapter_1 (byte 1) and verse_1 (byte 0) in an int (with byte number 3 for MSB and 0 for LSB).
	 * @return null when failed or the requested verse
	 */
	public Verse getVerse(int ari) {
		Cursor c = cr.query(Uri.parse("content://" + AlkitabIntegrationUtil.getProviderAuthority() + "/" + PATH_bible_verses_single_by_ari + ari + "?formatting=0"), null, null, null, null);
		if (c == null) {
			return null;
		}
		
		try {
			int col__id = c.getColumnIndexOrThrow("_id");
			int col_ari = c.getColumnIndexOrThrow(COLUMN_ari);
			int col_bookName = c.getColumnIndexOrThrow(COLUMN_bookName);
			int col_text = c.getColumnIndexOrThrow(COLUMN_text);
			
			if (c.moveToNext()) {
				Verse res = new Verse();
				res._id = c.getLong(col__id);
				res.ari = c.getInt(col_ari);
				res.bookName = c.getString(col_bookName);
				res.text = c.getString(col_text);
				return res;
			} else {
				return null;
			}
		} finally {
			c.close();
		}
	}
	
	/**
	 * Reads verses from the default version using verse ranges.
	 * @return null when failed, empty when no verses satisfy the requested ranges, or verses from the requested ranges.
	 */
	public List<Verse> getVerses(VerseRanges ranges) {
		Cursor c = cr.query(Uri.parse("content://" + AlkitabIntegrationUtil.getProviderAuthority() + "/" + PATH_bible_verses_range_by_ari + ranges.toString() + "?formatting=0"), null, null, null, null);
		if (c == null) {
			return null;
		}
		
		try {
			List<Verse> res = new ArrayList<Verse>();
			
			int col__id = c.getColumnIndexOrThrow("_id");
			int col_ari = c.getColumnIndexOrThrow(COLUMN_ari);
			int col_bookName = c.getColumnIndexOrThrow(COLUMN_bookName);
			int col_text = c.getColumnIndexOrThrow(COLUMN_text);
			
			while (c.moveToNext()) {
				Verse v = new Verse();
				v._id = c.getLong(col__id);
				v.ari = c.getInt(col_ari);
				v.bookName = c.getString(col_bookName);
				v.text = c.getString(col_text);
				res.add(v);
			}
			
			return res;
		} finally {
			c.close();
		}
	}
}
