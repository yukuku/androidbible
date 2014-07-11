package yuku.alkitab.base.pdbconvert;

import android.content.Context;
import android.util.Log;
import com.compactbyte.android.bible.PDBFileStream;
import com.compactbyte.bibleplus.reader.BiblePlusPDB;
import com.compactbyte.bibleplus.reader.BookInfo;
import gnu.trove.map.hash.TIntIntHashMap;
import yuku.alkitab.debug.R;
import yuku.alkitab.util.Ari;
import yuku.alkitab.yes2.Yes2Writer;
import yuku.alkitab.yes2.io.RandomAccessFileRandomOutputStream;
import yuku.alkitab.yes2.io.RandomOutputStream;
import yuku.alkitab.yes2.model.PericopeData;
import yuku.alkitab.yes2.model.VerseBytes;
import yuku.alkitab.yes2.model.Yes2Book;
import yuku.alkitab.yes2.section.BooksInfoSection;
import yuku.alkitab.yes2.section.PericopesSection;
import yuku.alkitab.yes2.section.VersionInfoSection;
import yuku.alkitab.yes2.section.base.SectionContent;
import yuku.bintex.BintexWriter;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ConvertPdbToYes2 {
	public static final String TAG = ConvertPdbToYes2.class.getSimpleName();

	BiblePlusPDB pdb_;

	TIntIntHashMap bookIdToPdbBookPosMap_;
	PericopeData pericopeData_;

	ConvertProgressListener convertProgressListener_;
	
	public interface ConvertProgressListener {
		void onProgress(int at, String message);
		void onFinish();
	}
	
	public static class ConvertParams {
		public String inputEncoding;
		public boolean includeAddlTitle;
	}
	
	public static class ConvertResult {
		public Throwable exception;
		public List<String> wronglyConvertedBookNames;
	}
	
	public void setConvertProgressListener(ConvertProgressListener l) {
		this.convertProgressListener_ = l;
	}
	
	void progress(int at, String message) {
		if (convertProgressListener_ != null) {
			convertProgressListener_.onProgress(at, message);
		}
	}
	
	void finish() {
		if (convertProgressListener_ != null) {
			convertProgressListener_.onFinish();
		}
	}
	
	public ConvertResult convert(final Context context, String filenamepdb, String yesFilename, ConvertParams params) {
		ConvertResult res = new ConvertResult();
		
		try {
			progress(0, context.getString(R.string.cp_opening_pdb_file));
			pdb_ = new BiblePlusPDB(new PDBFileStream(filenamepdb), Tabs.hebrewTab, Tabs.greekTab);
			if (params.inputEncoding != null) pdb_.setEncoding(params.inputEncoding);
			Log.d(TAG, "Encoding used: " + params.inputEncoding); //$NON-NLS-1$
			progress(10, context.getString(R.string.cp_loading_version_info));
			pdb_.loadVersionInfo();
			progress(20, context.getString(R.string.cp_loading_word_index));
			pdb_.loadWordIndex();
			
			Log.d(TAG, "============ done reading pdb version info"); //$NON-NLS-1$
			
			Log.d(TAG, "pdb versionName: " + pdb_.getVersionName()); //$NON-NLS-1$
			Log.d(TAG, "pdb encoding: " + pdb_.getEncoding()); //$NON-NLS-1$
			
			int nbook = pdb_.getBookCount();
			Log.d(TAG, "pdb getBookCount = " + nbook); //$NON-NLS-1$
			
			progress(30, context.getString(R.string.cp_analyzing_available_books));
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
			
			progress(40, context.getString(R.string.cp_mapping_books));
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
			
			progress(100, context.getString(R.string.cp_constructing_book_info));
			// this will also build the pericope blocks and indexes
			BooksInfoSection booksInfoSection = getBooksInfo(context, 100, params.includeAddlTitle, sortedBookIds);
			
			progress(200, context.getString(R.string.cp_constructing_version_info));
			VersionInfoSection versionInfoSection = getVersionInfo();
			
			Yes2Writer yesWriter = new Yes2Writer();
			yesWriter.sections.add(versionInfoSection);
			yesWriter.sections.add(booksInfoSection);
			
			if (pericopeData_ != null) {
				progress(510, context.getString(R.string.cp_writing_num_section_pericope_titles, pericopeData_.entries.size()));
				PericopesSection pericopesSection = new PericopesSection(pericopeData_);
				yesWriter.sections.add(pericopesSection);
			}
			
			LazyText lazyText = new LazyText(context, 800, sortedBookIds);
			yesWriter.sections.add(lazyText);
			
			progress(700, context.getString(R.string.cp_writing_translated_file));
			RandomOutputStream output = new RandomAccessFileRandomOutputStream(new RandomAccessFile(yesFilename, "rw")); //$NON-NLS-1$
			
			yesWriter.writeToFile(output);
			output.close();
			
			pdb_.close();
		} catch (Throwable e) {
			pdb_ = null;
			Log.e(TAG, "Error reading pdb: ", e); //$NON-NLS-1$
			res.exception = e;
		}
		finish();
		return res;
	}
	
	private VersionInfoSection getVersionInfo() {
		VersionInfoSection res = new VersionInfoSection();
		res.book_count = bookIdToPdbBookPosMap_.size();
		res.description = pdb_.getVersionInfo();
		res.hasPericopes = pericopeData_ == null? 0: 1;
		res.locale = null;
		res.longName = pdb_.getVersionName();
		res.shortName = null;
		res.textEncoding = 2; // utf-8
		return res;
	}

	private BooksInfoSection getBooksInfo(final Context context, int baseProgress, boolean includeAddlTitle, int[] sortedBookIds) throws Exception {
		// no nulls allowed
		final List<Yes2Book> yes2books = new ArrayList<>();
		
		// for the text offset from the beginning of text section
		int offsetTotal = 0;
		// for the text offset from the beginning of book
		int offsetPassed = 0;
		
		for (int bookId: sortedBookIds) {
			int pdbBookPos = bookIdToPdbBookPosMap_.get(bookId);
			BookInfo pdbBookInfo = pdb_.getBook(pdbBookPos);
			pdbBookInfo.openBook();
			
			progress(baseProgress + 1 + bookId, context.getString(R.string.cp_reading_book_info, pdbBookInfo.getFullName()));

			Yes2Book b = new Yes2Book();
			b.bookId = bookId;
			b.chapter_count = pdbBookInfo.getChapterCount();
			b.chapter_offsets = new int[b.chapter_count + 1];
			b.offset = offsetTotal;
			b.shortName = pdbBookInfo.getFullName();
			b.verse_counts = new int[b.chapter_count];
			
			b.chapter_offsets[0] = 0;
			for (int chapter_0 = 0; chapter_0 < b.chapter_count; chapter_0++) {
				b.verse_counts[chapter_0] = pdbBookInfo.getVerseCount(chapter_0 + 1);
				
				for (int verse_0 = 0; verse_0 < b.verse_counts[chapter_0]; verse_0++) {
					String[] complete = getCompleteVerseWithPreprocess(pdbBookInfo, chapter_0, verse_0);
					offsetPassed += VerseBytes.bytesForAVerse(complete[0]).length;
					
					// pericopes!
					if (includeAddlTitle) {
						if (complete[3].length() > 0) storePericope(3, complete[3], bookId, chapter_0, verse_0); 
						if (complete[2].length() > 0) storePericope(2, complete[2], bookId, chapter_0, verse_0); 
					}
					if (complete[1].length() > 0) storePericope(1, complete[1], bookId, chapter_0, verse_0);
				}
				b.chapter_offsets[chapter_0 + 1] = offsetPassed;
			}
			Log.d(TAG, "book " + b.shortName + " (pdbBookNumber=" + pdbBookInfo.getBookNumber() + ", bookId=" + bookId + ") chapter_offsets: " + Arrays.toString(b.chapter_offsets));  //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

			yes2books.add(b);
			
			//# reset
			offsetTotal += offsetPassed;
			offsetPassed = 0;
		}
		
		if (yes2books.size() != bookIdToPdbBookPosMap_.size()) {
			throw new RuntimeException("Some internal error, res size != bookIdToPdbBookPosMap_ size"); //$NON-NLS-1$
		}
		
		BooksInfoSection res = new BooksInfoSection();
		res.yes2Books = yes2books;
		return res;
	}

	/**
	 * Replaces (0x0e 'b' 0x0e) with (at 9) to start, or (at 7) to end.
	 * Replaces (0x0e 'n' 0x0e) with (at 8).
	 * and will add (at at) on the beginning of such verses.
	 * @return
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
			if (s.contains("\u000en\u000e")) { //$NON-NLS-1$
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
	
	private void storePericope(int type, String title, int bookId, int chapter_0, int verse_0) {
		int ari = Ari.encode(bookId, chapter_0 + 1, verse_0 + 1);

		if (pericopeData_ == null) {
			pericopeData_ = new PericopeData();
		}
		
		PericopeData.Entry entry = new PericopeData.Entry();
		entry.ari = ari;
		entry.block = new PericopeData.Block();
		entry.block.title = title;
		pericopeData_.addEntry(entry);
	}

	/**
	 * Each verse is written as follows:
	 *
	 *	- varuint length_in_bytes
	 *  - byte[length_in_bytes] encoded_text
	 */
	public class LazyText extends SectionContent implements SectionContent.Writer {
		private final int baseProgress;
		private final Context context;
		private final int[] sortedBookIds;
		
		public LazyText(Context context, int baseProgress, int[] sortedBookIds) {
			super("text");
			this.context = context;
			this.baseProgress = baseProgress;
			this.sortedBookIds = sortedBookIds;
		}
		
		@Override public void write(RandomOutputStream output) throws IOException {
			BintexWriter bw = new BintexWriter(output);
			
			for (int bookId: sortedBookIds) {
				int pdbBookPos = bookIdToPdbBookPosMap_.get(bookId);
				BookInfo pdbBookInfo = pdb_.getBook(pdbBookPos);
				pdbBookInfo.openBook();

				int chapter_count = pdbBookInfo.getChapterCount();
				progress(baseProgress + 1 + bookId, context.getString(R.string.cp_writing_text_of_book_chapters, pdbBookInfo.getFullName(), chapter_count));
				
				for (int chapter_0 = 0; chapter_0 < chapter_count; chapter_0++) {
					int verse_count = pdbBookInfo.getVerseCount(chapter_0 + 1);
					for (int verse_0 = 0; verse_0 < verse_count; verse_0++) {
						String s = getCompleteVerseWithPreprocess(pdbBookInfo, chapter_0, verse_0)[0];
						byte[] bytes = VerseBytes.bytesForAVerse(s);
						bw.writeRaw(bytes);
					}
				}
			}
		}
	}
}
