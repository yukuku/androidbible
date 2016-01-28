package yuku.pdbtoyet.core;

import com.compactbyte.bibleplus.reader.BiblePlusPDB;
import com.compactbyte.bibleplus.reader.BookInfo;
import com.compactbyte.bibleplus.reader.PDBDataStream;
import gnu.trove.map.hash.TIntIntHashMap;
import yuku.alkitab.util.Ari;
import yuku.alkitab.yes2.model.PericopeData;
import yuku.alkitabconverter.util.TextDb;
import yuku.alkitabconverter.yes_common.Yes2Common;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

public class PdbInput {
    private static final Logger log = Logger.getLogger(PdbInput.class.getName());

    public static final String TAG = PdbInput.class.getSimpleName();

	public static class Params {
		public String inputEncoding;
		public boolean includeAddlTitle;
	}

	public static class Result {
		public List<String> wronglyConvertedBookNames;
        public Yes2Common.VersionInfo versionInfo;
        public TextDb textDb;
        public PericopeData pericopeData;
    }

    public static class InputException extends RuntimeException {
        public InputException(String s) {
            super(s);
        }
    }

	public Result read(final PDBDataStream pdbDataStream, final Params params) throws IOException {
		final Result res = new Result();

        BiblePlusPDB pdb_ = new BiblePlusPDB(pdbDataStream, Tabs.hebrewTab, Tabs.greekTab);
		if (params.inputEncoding != null) pdb_.setEncoding(params.inputEncoding);
        Log.d(TAG, "Encoding used: " + params.inputEncoding); //$NON-NLS-1$

        // manual check
        {
            byte[] data = new byte[4];
            pdbDataStream.seek(60);
            pdbDataStream.read(data);
            if (!Arrays.equals(data, "bibl".getBytes())) {
                throw new InputException("Not a valid PDB file. Type of PDB file is '" + new String(data) + "', not 'bibl'");
            }
            pdbDataStream.read(data);
            if (!Arrays.equals(data, "PPBL".getBytes())) {
                throw new InputException("Not a valid PDB file. Creator code of PDB file is '" + new String(data) + "', not 'PPBL'");
            }
        }

        // pdb library check
        pdbDataStream.seek(0);
        final boolean success = pdb_.loadVersionInfo();
        if (!success) {
            throw new InputException("Can't read version info: " + pdb_.getFailReason());
        }
        pdb_.loadWordIndex();

		Log.d(TAG, "============ done reading pdb version info"); //$NON-NLS-1$

		Log.d(TAG, "pdb versionName: " + pdb_.getVersionName()); //$NON-NLS-1$
		Log.d(TAG, "pdb encoding: " + pdb_.getEncoding()); //$NON-NLS-1$

		int nbook = pdb_.getBookCount();
		Log.d(TAG, "pdb getBookCount = " + nbook); //$NON-NLS-1$

        TIntIntHashMap bookIdToPdbBookPosMap_;
        {
			for (int bookPos = 0; bookPos < nbook; bookPos++) {
				BookInfo pdbBookInfo = pdb_.getBook(bookPos);
				pdbBookInfo.openBook();
				int bookNumber = pdbBookInfo.getBookNumber();
				int bookId = PdbBookNumberToBookIdMapping.pdbBookNumberToBookId(bookNumber);
				if (bookId < 0) {
					Log.w(TAG, "bookNumber " + bookNumber + " GA DIKENAL"); //$NON-NLS-1$ //$NON-NLS-2$
					if (res.wronglyConvertedBookNames == null) {
						res.wronglyConvertedBookNames = new ArrayList<>();
					}
					res.wronglyConvertedBookNames.add(pdbBookInfo.getFullName() + " (" + bookNumber + ")"); //$NON-NLS-1$ //$NON-NLS-2$
				}
			}
			bookIdToPdbBookPosMap_ = new TIntIntHashMap();
		}

		for (int bookPos = 0; bookPos < nbook; bookPos++) {
			BookInfo pdbBookInfo = pdb_.getBook(bookPos);
			pdbBookInfo.openBook();

			int pdbBookNumber = pdbBookInfo.getBookNumber();
			int bookId = PdbBookNumberToBookIdMapping.pdbBookNumberToBookId(pdbBookNumber);
			if (bookId < 0) {
				Log.w(TAG, "pdbBookNumber " + pdbBookNumber + " NOT KNOWN"); //$NON-NLS-1$ //$NON-NLS-2$
			} else {
				if (bookIdToPdbBookPosMap_.containsKey(bookId)) {
					// just a warning of duplicate
					if (res.wronglyConvertedBookNames == null) {
						res.wronglyConvertedBookNames = new ArrayList<>();
					}
					res.wronglyConvertedBookNames.add(pdbBookInfo.getFullName() + " (" + pdbBookNumber + "): duplicate"); //$NON-NLS-1$ //$NON-NLS-2$
				}
				bookIdToPdbBookPosMap_.put(bookId, bookPos);
			}
		}

		Log.d(TAG, "bookIdToPdbBookPosMap_ (size " + bookIdToPdbBookPosMap_.size() + ") = " + bookIdToPdbBookPosMap_.toString()); //$NON-NLS-1$ //$NON-NLS-2$

		Log.d(TAG, "============ done reading list of books"); //$NON-NLS-1$


		final int[] sortedBookIds = bookIdToPdbBookPosMap_.keys();
		Arrays.sort(sortedBookIds);

		// Get book names and the abbrs
        final List<String> bookNames = new ArrayList<>();
        final List<String> abbrevations = new ArrayList<>();
        
        for (int bookId : sortedBookIds) {
            int pdbBookPos1 = bookIdToPdbBookPosMap_.get(bookId);
            BookInfo pdbBookInfo = pdb_.getBook(pdbBookPos1);
            pdbBookInfo.openBook();

            while (bookId >= bookNames.size()) {
                bookNames.add(null);
            }
            bookNames.set(bookId, pdbBookInfo.getFullName());
            while (bookId >= abbrevations.size()) {
                abbrevations.add(null);
            }
            abbrevations.set(bookId, pdbBookInfo.getShortName());
        }

        // prepare version info (including book names)
        final Yes2Common.VersionInfo versionInfo = new Yes2Common.VersionInfo();
        versionInfo.description = pdb_.getVersionInfo();
        versionInfo.locale = null;
        versionInfo.longName = pdb_.getVersionName();
        versionInfo.shortName = null;
        versionInfo.setBookNamesAndAbbreviations(bookNames, abbrevations);
        res.versionInfo = versionInfo;

        // read text and pericopes
        res.textDb = new TextDb();
        res.pericopeData = null;
        for (int bookId: sortedBookIds) {
            final int pdbBookPos = bookIdToPdbBookPosMap_.get(bookId);
            final BookInfo pdbBookInfo = pdb_.getBook(pdbBookPos);
            pdbBookInfo.openBook();

            final int chapter_count = pdbBookInfo.getChapterCount();

            for (int chapter_0 = 0; chapter_0 < chapter_count; chapter_0++) {
                int verse_count = pdbBookInfo.getVerseCount(chapter_0 + 1);
                for (int verse_0 = 0; verse_0 < verse_count; verse_0++) {
                    final String[] complete = getCompleteVerseWithPreprocess(pdbBookInfo, chapter_0, verse_0);

                    // text
                    res.textDb.append(bookId, chapter_0 + 1, verse_0 + 1, complete[0], -1);
                    
                    // pericopes, if exist
                    if (params.includeAddlTitle) {
                        if (complete[3].length() > 0) {
                            if (res.pericopeData == null) res.pericopeData = new PericopeData();
                            res.pericopeData.addEntry(createPericopeEntry(3, complete[3], bookId, chapter_0, verse_0));
                        }
                        if (complete[2].length() > 0) {
                            if (res.pericopeData == null) res.pericopeData = new PericopeData();
                            res.pericopeData.addEntry(createPericopeEntry(2, complete[2], bookId, chapter_0, verse_0));
                        }
                    }
                    if (complete[1].length() > 0) {
                        if (res.pericopeData == null) res.pericopeData = new PericopeData();
                        res.pericopeData.addEntry(createPericopeEntry(1, complete[1], bookId, chapter_0, verse_0));
                    }
                }
            }
        }
        
        return res;
	}

    /**
	 * Replaces (0x0e 'b' 0x0e) with (at 9) to start, or (at 7) to end.
	 * Replaces (0x0e 'n' 0x0e) with (at 8).
	 * and will add (at at) on the beginning of such verses.
	 */
	String[] getCompleteVerseWithPreprocess(BookInfo pdbBookInfo, int chapter_0, int verse_0) {
		String[] ss = pdbBookInfo.getCompleteVerse(chapter_0 + 1, verse_0 + 1);

		for (int i = 0; i < ss.length; i++) {
			String s = ss[i];
			if (s == null || s.length() == 0) {
				continue;
			}

			// search for 0x0e shortcut
			if (s.indexOf(0x0e) < 0) {
				continue;
			}

			boolean prependAtAt = false;

			// look for 0x0e 'n' 0x0e
			if (s.indexOf("\u000en\u000e") >= 0) { //$NON-NLS-1$
				prependAtAt = true;
				s = s.replaceAll("\\s*\u000en\u000e\\s*", "@8"); //$NON-NLS-1$ //$NON-NLS-2$
			}

			boolean startingItalic = true;
			while (true) {
				int pos = s.indexOf("\u000eb\u000e"); //$NON-NLS-1$
				if (pos > 0) {
					prependAtAt = true;
					String tag = startingItalic ? "@9" : "@7"; //$NON-NLS-1$ //$NON-NLS-2$
					s = s.substring(0, pos) + tag + s.substring(pos + 3); // TODO remove extraneous spaces
					startingItalic = !startingItalic;
				} else {
					break;
				}
			}

			if (prependAtAt) {
				s = "@@" + s; //$NON-NLS-1$
			}

			ss[i] = s;
		}
		return ss;
	}

	private PericopeData.Entry createPericopeEntry(int type, String title, int bookId, int chapter_0, int verse_0) {
		int ari = Ari.encode(bookId, chapter_0 + 1, verse_0 + 1);

		final PericopeData.Entry entry = new PericopeData.Entry();
		entry.ari = ari;
		entry.block = new PericopeData.Block();
		entry.block.title = title;
		return entry;
	}

	public static class Log {
		public static void d(String tag, String msg) {
            log.fine(tag + " " + msg);
		}

		public static void w(String tag, String msg) {
            log.warning(tag + " " + msg);
		}
	}

}
