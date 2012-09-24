package yuku.alkitab.base.cp;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

import java.util.Arrays;
import java.util.List;

import yuku.afw.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.VersionsActivity.MVersionPreset;
import yuku.alkitab.base.config.BuildConfig;
import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.model.Book;
import yuku.alkitab.base.util.IntArrayList;
import yuku.alkitab.base.util.LidToAri;

public class Provider extends ContentProvider {
	public static final String TAG = Provider.class.getSimpleName();

	private static final String AUTHORITY = "yuku.alkitab.provider";
	private static final int PATH_bible_verses_single_by_lid = 1;
	private static final int PATH_bible_verses_single_by_ari = 2;
	private static final int PATH_bible_verses_range_by_lid = 3;
	private static final int PATH_bible_verses_range_by_ari = 4;
	private static final int PATH_bible_versions = 5;
	
    private static final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
    	Log.d(TAG, Provider.class.getName() + " @@static_init");
    	
    	uriMatcher.addURI(AUTHORITY, "bible/verses/single/by-lid/#", PATH_bible_verses_single_by_lid); 
    	uriMatcher.addURI(AUTHORITY, "bible/verses/single/by-ari/#", PATH_bible_verses_single_by_ari); 
    	uriMatcher.addURI(AUTHORITY, "bible/verses/range/by-lid/*", PATH_bible_verses_range_by_lid); 
    	uriMatcher.addURI(AUTHORITY, "bible/verses/range/by-ari/*", PATH_bible_verses_range_by_ari); 
    	uriMatcher.addURI(AUTHORITY, "bible/versions", PATH_bible_versions); 
    }

    @Override public boolean onCreate() {
    	Log.d(TAG, "@@onCreate");
    	
    	yuku.afw.App.initWithAppContext(getContext().getApplicationContext());
    	yuku.alkitab.base.App.staticInit();
    	
		return true;
	}

	@Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		Log.d(TAG, "@@query uri=" + uri + " projection=" + Arrays.toString(projection) + " selection=" + selection + " args=" + Arrays.toString(selectionArgs) + " sortOrder=" + sortOrder);
		
		int uriMatch = uriMatcher.match(uri);
		Log.d(TAG, "uriMatch=" + uriMatch);
		
		String formatting_s = uri.getQueryParameter("formatting");
		boolean formatting = parseBoolean(formatting_s);
		
		Cursor res;
		
		switch (uriMatch) {
		case PATH_bible_verses_single_by_lid: {
			res = getCursorForSingleVerseLid(parseInt(uri.getLastPathSegment(), Integer.MIN_VALUE), formatting);
		} break;
		case PATH_bible_verses_single_by_ari: {
			res = getCursorForSingleVerseAri(parseInt(uri.getLastPathSegment(), Integer.MIN_VALUE), formatting);
		} break;
		case PATH_bible_verses_range_by_lid: {
			String range = uri.getLastPathSegment();
			IntArrayList lids = decodeLidRange(range);
			res = getCursorForRangeVerseLid(lids, formatting);
		} break;
		case PATH_bible_verses_range_by_ari: {
			String range = uri.getLastPathSegment();
			IntArrayList aris = decodeAriRange(range);
			res = getCursorForRangeVerseAri(aris, formatting);
		} break;
		case PATH_bible_versions: {
			res = getCursorForBibleVersions();
		} break;
		default: {
			res = null;
		} break;
		}
		
		Log.d(TAG, "returning " + (res == null? "null": "cursor with " + res.getCount() + " rows"));
		return res;
	}

	private IntArrayList decodeLidRange(String range) {
		IntArrayList res = new IntArrayList();
		
		String[] splits = range.split(",");
		for (String split: splits) {
			int start, end;
			if (split.indexOf('-') != -1) {
				String[] startEnd = split.split("-", 2);
				start = parseInt(startEnd[0], Integer.MIN_VALUE);
				end = parseInt(startEnd[1], Integer.MIN_VALUE);
			} else {
				start = end = parseInt(split, Integer.MIN_VALUE);
			}
			
			if (start != Integer.MIN_VALUE && end != Integer.MIN_VALUE) {
				for (int i = start; i <= end; i++) {
					res.add(i);
				}
			}
		}
		
		return res;
	}
	
	/**
	 * Also supports verse 0 for the whole chapter (0xbbcc00)
	 */
	private IntArrayList decodeAriRange(String range) {
		IntArrayList res = new IntArrayList();
		
		String[] splits = range.split(",");
		for (String split: splits) {
			int start, end;
			if (split.indexOf('-') != -1) {
				String[] startEnd = split.split("-", 2);
				start = parseInt(startEnd[0], Integer.MIN_VALUE);
				end = parseInt(startEnd[1], Integer.MIN_VALUE);
			} else {
				start = end = parseInt(split, Integer.MIN_VALUE);
			}
			
			if (start != Integer.MIN_VALUE && end != Integer.MIN_VALUE) {
				start &= 0xffffff;
				end &= 0xffffff;
				
				// case: 0xXXYY00 - 0xXXYY00 (whole single chapter) 
				if (start == end && Ari.toVerse(start) == 0) {
					for (int i = start | 0x01, to = start | 0xff; i <= to; i++) {
						res.add(i);
					}
				} else if (end >= start) {
					if (Ari.toVerse(start) == 0) {
						start = start | 0x01;
					}
					if (Ari.toVerse(end) == 0) {
						end = end | 0xff;
					}
					for (int i = start; i <= end; i++) {
						if ((i & 0xff) != 0) res.add(i);
					}
				}
			}
		}
		
		return res;
	}

	private Cursor getCursorForSingleVerseLid(int lid, boolean formatting) {
		int ari = LidToAri.lidToAri(lid);
		return getCursorForSingleVerseAri(ari, formatting);
	}

	private Cursor getCursorForSingleVerseAri(int ari, boolean formatting) {
		MatrixCursor res = new MatrixCursor(new String[] {"_id", "ari", "bookName", "text"});
		
		Log.d(TAG, "getting ari 0x" + Integer.toHexString(ari));
		
		if (ari != Integer.MIN_VALUE && ari != 0) {
			Book book = S.activeVersion.getBook(Ari.toBook(ari));
			if (book != null) {
				String text = S.muatSatuAyat(S.activeVersion, ari);
				if (formatting == false) {
					text = U.removeSpecialCodes(text);
				}
				res.addRow(new Object[] {1, ari, book.judul, text});
			}
		}
		
		return res;
	}
	
	/* TODO optimize for cases where the different verses of same chapter are accessed */ 
	private Cursor getCursorForRangeVerseLid(IntArrayList lids, boolean formatting) {
		MatrixCursor res = new MatrixCursor(new String[] {"_id", "ari", "bookName", "text"});
		
		int c = 0;
		for (int i = 0, len = lids.size(); i < len; i++) {
			int lid = lids.get(i);
			int ari = LidToAri.lidToAri(lid);
			if (ari != 0) {
				Book book = S.activeVersion.getBook(Ari.toBook(ari));
				if (book != null) {
					String text = S.muatSatuAyat(S.activeVersion, ari);
					if (formatting == false) {
						text = U.removeSpecialCodes(text);
					}
					res.addRow(new Object[] {++c, ari, book.judul, text});
				}
			}
		}
		
		return res;
	}
	
	/* TODO optimize for cases where the different verses of same chapter are accessed */ 
	private Cursor getCursorForRangeVerseAri(IntArrayList aris, boolean formatting) {
		MatrixCursor res = new MatrixCursor(new String[] {"_id", "ari", "bookName", "text"});

		int c = 0;
		for (int i = 0, len = aris.size(); i < len; i++) {
			int ari = aris.get(i);
			if (ari != 0) {
				Book book = S.activeVersion.getBook(Ari.toBook(ari));
				if (book != null) {
					int chapter_1 = Ari.toChapter(ari);
					if (chapter_1 >= 1 && chapter_1 <= book.nchapter) {
						int verse_1 = Ari.toVerse(ari);
						if (verse_1 >= 1 && verse_1 <= book.nverses[chapter_1-1]) {
							String text = S.muatSatuAyat(S.activeVersion, ari);
							if (formatting == false) {
								text = U.removeSpecialCodes(text);
							}
							res.addRow(new Object[] {++c, ari, book.judul, text});
						}
					}
				}
			}
		}
		
		return res;
	}
	
	private Cursor getCursorForBibleVersions() {
		MatrixCursor res = new MatrixCursor(new String[] {"_id", "ari", "bookName", "text"});

		S.getEdisiInternal();
		List<MVersionPreset> presets = BuildConfig.get(App.context).presets;
		S.getDb().listAllVersions();
		return null;
	}
	
	/** Similar to Integer.parseInt() but supports 0x and won't throw any exception when failed */
	private static int parseInt(String s, int def) {
		if (s == null || s.length() == 0) return def;
		
		// need to trim?
		if (s.charAt(0) == ' ' || s.charAt(s.length() - 1) == ' ') {
			s = s.trim();
		}
		
		// 0x?
		if (s.startsWith("0x")) {
			try {
				return Integer.parseInt(s.substring(2), 16);
			} catch (NumberFormatException e) {
				return def;
			}
		}
		
		// normal decimal
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return def;
		}
	}
	
	private static boolean parseBoolean(String s) {
		if (s == null) return false;
		if (s.equals("0")) return false;
		if (s.equals("1")) return true;
		if (s.equals("false")) return false;
		if (s.equals("true")) return true;
		s = s.toLowerCase();
		if (s.equals("false")) return false;
		if (s.equals("true")) return true;
		if (s.equals("no")) return false;
		if (s.equals("yes")) return true;
		int n = parseInt(s, Integer.MIN_VALUE);
		if (n == 0) return false;
		if (n != Integer.MIN_VALUE) return true;
		return false;
	}

	@Override public String getType(Uri uri) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override public Uri insert(Uri uri, ContentValues values) {
		throw new UnsupportedOperationException();
	}

	@Override public int delete(Uri uri, String selection, String[] selectionArgs) {
		throw new UnsupportedOperationException();
	}

	@Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		throw new UnsupportedOperationException();
	}
}
