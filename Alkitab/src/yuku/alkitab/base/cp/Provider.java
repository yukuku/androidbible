package yuku.alkitab.base.cp;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.VersionsActivity.MVersionPreset;
import yuku.alkitab.base.ac.VersionsActivity.MVersionYes;
import yuku.alkitab.base.config.VersionConfig;
import yuku.alkitab.base.util.LidToAri;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.model.Book;
import yuku.alkitab.model.SingleChapterVerses;
import yuku.alkitab.util.Ari;
import yuku.alkitab.util.IntArrayList;
import yuku.alkitabintegration.AlkitabIntegrationUtil;
import yuku.alkitabintegration.provider.VerseProvider;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class Provider extends ContentProvider {
	public static final String TAG = Provider.class.getSimpleName();

	private static final int PATHID_bible_verses_single_by_lid = 1;
	private static final int PATHID_bible_verses_single_by_ari = 2;
	private static final int PATHID_bible_verses_range_by_lid = 3;
	private static final int PATHID_bible_verses_range_by_ari = 4;
	private static final int PATHID_bible_versions = 5;
	
    private static UriMatcher uriMatcher;

	@Override
	public void attachInfo(final Context context, final ProviderInfo info) {
		super.attachInfo(context, info);

		final String authority;
		if (BuildConfig.DEBUG) {
			authority = info.authority;
		} else {
			if (!U.equals(info.authority, AlkitabIntegrationUtil.DEFAULT_ALKITAB_PROVIDER_AUTHORITY)) {
				throw new RuntimeException("Bad build: DEFAULT_ALKITAB_PROVIDER_AUTHORITY and manifest authority are not the same");
			}
			authority = AlkitabIntegrationUtil.DEFAULT_ALKITAB_PROVIDER_AUTHORITY;
		}

		if (uriMatcher == null) {
			uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
			uriMatcher.addURI(authority, VerseProvider.PATH_bible_verses_single_by_lid + "#", PATHID_bible_verses_single_by_lid);
			uriMatcher.addURI(authority, VerseProvider.PATH_bible_verses_single_by_ari + "#", PATHID_bible_verses_single_by_ari);
			uriMatcher.addURI(authority, VerseProvider.PATH_bible_verses_range_by_lid + "*", PATHID_bible_verses_range_by_lid);
			uriMatcher.addURI(authority, VerseProvider.PATH_bible_verses_range_by_ari + "*", PATHID_bible_verses_range_by_ari);
			uriMatcher.addURI(authority, "bible/versions", PATHID_bible_versions);
		}
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
		case PATHID_bible_verses_single_by_lid: {
			res = getCursorForSingleVerseLid(Ari.parseInt(uri.getLastPathSegment(), Integer.MIN_VALUE), formatting);
		} break;
		case PATHID_bible_verses_single_by_ari: {
			res = getCursorForSingleVerseAri(Ari.parseInt(uri.getLastPathSegment(), Integer.MIN_VALUE), formatting);
		} break;
		case PATHID_bible_verses_range_by_lid: {
			String range = uri.getLastPathSegment();
			IntArrayList lids = decodeLidRange(range);
			res = getCursorForRangeVerseLid(lids, formatting);
		} break;
		case PATHID_bible_verses_range_by_ari: {
			String range = uri.getLastPathSegment();
			IntArrayList aris = decodeAriRange(range);
			res = getCursorForRangeVerseAri(aris, formatting);
		} break;
		case PATHID_bible_versions: {
			res = getCursorForBibleVersions();
		} break;
		default: {
			res = null;
		} break;
		}
		
		Log.d(TAG, "returning " + (res == null? "null": "cursor with " + res.getCount() + " rows"));
		return res;
	}

	/**
	 * @return [start, end, start, end, ...]
	 */
	private IntArrayList decodeLidRange(String range) {
		IntArrayList res = new IntArrayList();
		
		String[] splits = range.split(",");
		for (String split: splits) {
			int start, end;
			if (split.indexOf('-') != -1) {
				String[] startEnd = split.split("-", 2);
				start = Ari.parseInt(startEnd[0], Integer.MIN_VALUE);
				end = Ari.parseInt(startEnd[1], Integer.MIN_VALUE);
			} else {
				start = end = Ari.parseInt(split, Integer.MIN_VALUE);
			}
			
			if (start != Integer.MIN_VALUE && end != Integer.MIN_VALUE) {
				res.add(start);
				res.add(end);
			}
		}
		
		return res;
	}
	
	/**
	 * Also supports verse 0 for the whole chapter (0xbbcc00)
	 * 
	 * @return [start, end, start, end, ...]
	 */
	private IntArrayList decodeAriRange(String range) {
		IntArrayList res = new IntArrayList();
		
		String[] splits = range.split(",");
		for (String split: splits) {
			int start, end;
			if (split.indexOf('-') != -1) {
				String[] startEnd = split.split("-", 2);
				start = Ari.parseInt(startEnd[0], Integer.MIN_VALUE);
				end = Ari.parseInt(startEnd[1], Integer.MIN_VALUE);
			} else {
				start = end = Ari.parseInt(split, Integer.MIN_VALUE);
			}
			
			if (start != Integer.MIN_VALUE && end != Integer.MIN_VALUE) {
				start &= 0xffffff;
				end &= 0xffffff;
				
				if (start == end && Ari.toVerse(start) == 0) {
					// case: 0xXXYY00 - 0xXXYY00 (whole single chapter)
					res.add(start | 0x01);
					res.add(start | 0xff);
				} else if (end >= start) {
					if (Ari.toVerse(start) == 0) {
						start = start | 0x01;
					}
					if (Ari.toVerse(end) == 0) {
						end = end | 0xff;
					}
					res.add(start);
					res.add(end);
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
		MatrixCursor res = new MatrixCursor(new String[] {"_id", VerseProvider.COLUMN_ari, VerseProvider.COLUMN_bookName, VerseProvider.COLUMN_text});
		
		Log.d(TAG, "getting ari 0x" + Integer.toHexString(ari));
		
		if (ari != Integer.MIN_VALUE && ari != 0) {
			Book book = S.activeVersion.getBook(Ari.toBook(ari));
			if (book != null) {
				String text = S.activeVersion.loadVerseText(ari);
				if (text != null) {
					if (!formatting) {
						text = U.removeSpecialCodes(text);
					}
					res.addRow(new Object[]{1, ari, book.shortName, text});
				}
			}
		}
		
		return res;
	}
	
	private Cursor getCursorForRangeVerseLid(IntArrayList lids, boolean formatting) {
		IntArrayList aris = new IntArrayList(lids.size());
		for (int i = 0, len = lids.size(); i < len; i+=2) {
			int lid_start = lids.get(i);
			int lid_end = lids.get(i + 1);
			int ari_start = LidToAri.lidToAri(lid_start);
			int ari_end = LidToAri.lidToAri(lid_end);
			aris.add(ari_start);
			aris.add(ari_end);
		}
		
		return getCursorForRangeVerseAri(aris, formatting);
	}

	private Cursor getCursorForRangeVerseAri(IntArrayList ariRanges, boolean formatting) {
		MatrixCursor res = new MatrixCursor(new String[] {"_id", VerseProvider.COLUMN_ari, VerseProvider.COLUMN_bookName, VerseProvider.COLUMN_text});

		int c = 0;
		for (int i = 0, len = ariRanges.size(); i < len; i+=2) {
			int ari_start = ariRanges.get(i);
			int ari_end = ariRanges.get(i + 1);
			
			if (ari_start == 0 || ari_end == 0) {
				continue;
			}
			
			if (ari_start == ari_end) {
				// case: single verse
				int ari = ari_start;
				Book book = S.activeVersion.getBook(Ari.toBook(ari));
				if (book != null) {
					String text = S.activeVersion.loadVerseText(ari);
					if (formatting == false) {
						text = U.removeSpecialCodes(text);
					}
					res.addRow(new Object[] {++c, ari, book.shortName, text});
				}
			} else {
				int ari_start_bc = Ari.toBookChapter(ari_start);
				int ari_end_bc = Ari.toBookChapter(ari_end);
				
				if (ari_start_bc == ari_end_bc) {
					// case: multiple verses in the same chapter
					Book book = S.activeVersion.getBook(Ari.toBook(ari_start));
					if (book != null) {
						c += resultForOneChapter(res, book, c, ari_start_bc, Ari.toVerse(ari_start), Ari.toVerse(ari_end), formatting);
					}
				} else {
					// case: multiple verses in different chapters
					for (int ari_bc = ari_start_bc; ari_bc <= ari_end_bc; ari_bc += 0x0100) {
						Book book = S.activeVersion.getBook(Ari.toBook(ari_bc));
						int chapter_1 = Ari.toChapter(ari_bc);
						if (book == null || chapter_1 <= 0 || chapter_1 > book.chapter_count) {
							continue;
						}
						
						if (ari_bc == ari_start_bc) { // we're at the first requested chapter
							c += resultForOneChapter(res, book, c, ari_bc, Ari.toVerse(ari_start), 0xff, formatting); 
						} else if (ari_bc == ari_end_bc) { // we're at the last requested chapter
							c += resultForOneChapter(res, book, c, ari_bc, 0x01, Ari.toVerse(ari_end), formatting);
						} else { // we're at the middle, request all verses!
							c += resultForOneChapter(res, book, c, ari_bc, 0x01, 0xff, formatting);
						}
					}
				}
			}
		}
		
		return res;
	}
	
	/**
	 * @return number of verses put into the cursor
	 */
	private int resultForOneChapter(MatrixCursor cursor, Book book, int last_c, int ari_bc, int v_1_start, int v_1_end, boolean formatting) {
		int count = 0;
		SingleChapterVerses verses = S.activeVersion.loadChapterText(book, Ari.toChapter(ari_bc));
		for (int v_1 = v_1_start; v_1 <= v_1_end; v_1++) {
			int v_0 = v_1 - 1;
			if (v_0 < verses.getVerseCount()) {
				int ari = ari_bc | v_1;
				String text = verses.getVerse(v_0);
				if (formatting == false) {
					text = U.removeSpecialCodes(text);
				}
				count++;
				cursor.addRow(new Object[] {last_c + count, ari, book.shortName, text});
			} else {
				// we're done with this chapter, no need to loop again
				break;
			}
		}
		return count;
	}
	
	private Cursor getCursorForBibleVersions() {
		MatrixCursor res = new MatrixCursor(new String[] {"_id", "type", "available", "shortName", "longName", "description"});

		final VersionConfig c = VersionConfig.get();
		long _id = 0;
		{ // internal
			res.addRow(new Object[] {++_id, "internal", 1, c.internalShortName, c.internalLongName, c.internalLongName});
		}
		{ // presets
			for (MVersionPreset preset: c.presets) {
				res.addRow(new Object[] {++_id, "preset", preset.hasDataFile()? 1: 0, preset.shortName != null? preset.shortName: preset.longName, preset.longName, preset.longName});
			}
		}
		{ // yes
			List<MVersionYes> yeses = S.getDb().listAllVersions();
			for (MVersionYes yes: yeses) {
				res.addRow(new Object[] {++_id, "yes", yes.hasDataFile()? 1:0, yes.shortName != null? yes.shortName: yes.longName, yes.longName, yes.description});
			}
		}
		
		return res;
	}

    private static boolean parseBoolean(String s) {
		if (s == null) return false;
		if (s.equals("0")) return false;
		if (s.equals("1")) return true;
		if (s.equals("false")) return false;
		if (s.equals("true")) return true;
		s = s.toLowerCase(Locale.US);
		if (s.equals("false")) return false;
		if (s.equals("true")) return true;
		if (s.equals("no")) return false;
		if (s.equals("yes")) return true;
		int n = Ari.parseInt(s, Integer.MIN_VALUE);
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
