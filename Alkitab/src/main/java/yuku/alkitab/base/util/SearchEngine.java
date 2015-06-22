package yuku.alkitab.base.util;

import android.graphics.Typeface;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.util.TimingLogger;
import yuku.alkitab.base.App;
import yuku.alkitab.base.config.AppConfig;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.model.Book;
import yuku.alkitab.model.SingleChapterVerses;
import yuku.alkitab.model.Version;
import yuku.alkitab.util.Ari;
import yuku.alkitab.util.IntArrayList;
import yuku.bintex.BintexReader;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;

public class SearchEngine {
	public static final String TAG = SearchEngine.class.getSimpleName();

	public static class Query implements Parcelable {
		public String query_string;
		public SparseBooleanArray bookIds;
		
		@Override public int describeContents() {
			return 0;
		}
		
		@Override public void writeToParcel(Parcel dest, int flags) {
			dest.writeString(query_string);
			dest.writeSparseBooleanArray(bookIds);
		}

		public static final Parcelable.Creator<Query> CREATOR = new Parcelable.Creator<Query>() {
			@Override public Query createFromParcel(Parcel in) {
				Query res = new Query();
				res.query_string = in.readString();
				res.bookIds = in.readSparseBooleanArray();
				return res;
			}

			@Override public Query[] newArray(int size) {
				return new Query[size];
			}
		};
	}
	
	static class RevIndex extends HashMap<String, int[]> {
		public RevIndex() {
			super(32768);
		}
	}
	
	private static SoftReference<RevIndex> cache_revIndex;
	private static Semaphore revIndexLoading = new Semaphore(1);
	
	public static IntArrayList searchByGrep(final Version version, final Query query) {
		String[] words = QueryTokenizer.tokenize(query.query_string);
		
		// urutkan berdasarkan panjang, lalu abjad
		Arrays.sort(words, new Comparator<String>() {
			@Override
			public int compare(String object1, String object2) {
				int len1 = object1.length();
				int len2 = object2.length();
				
				if (len1 > len2) return -1;
				if (len1 == len2) {
					return object1.compareTo(object2);
				}
				return 1;
			}
		});
		
		// remove duplicates
		{
			ArrayList<String> awords = new ArrayList<>();
			String last = null;
			for (String word: words) {
				if (!word.equals(last)) {
					awords.add(word);
				}
				last = word;
			}
			words = awords.toArray(new String[awords.size()]);
			if (BuildConfig.DEBUG) Log.d(TAG, "words = " + Arrays.toString(words));
		}
		
		// really search
		IntArrayList result = null;
		
		{
			int index = 0;
			
			while (true) {
				if (index >= words.length) {
					break;
				}
				
				String word = words[index];
				
				IntArrayList prev = result;
	
				{
					long ms = System.currentTimeMillis();
					result = searchByGrepInside(version, word, prev, query.bookIds);
					Log.d(TAG, "search word '" + word + "' needed: " + (System.currentTimeMillis() - ms) + " ms"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				}
	
				if (prev != null) {
					Log.d(TAG, "Will intersect " + prev.size() + " elements with " + result.size() + " elements..."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					result = intersect(prev, result);
					Log.d(TAG, "... the result is " + result.size() + " elements");  //$NON-NLS-1$//$NON-NLS-2$
				}
				
				index++;
			}
		}
		
		return result;
	}

	private static IntArrayList intersect(IntArrayList a, IntArrayList b) {
		IntArrayList res = new IntArrayList(a.size());
		
		int[] aa = a.buffer();
		int[] bb = b.buffer();
		int alen = a.size();
		int blen = b.size();
		
		int apos = 0;
		int bpos = 0;
		
		while (true) {
			if (apos >= alen) break;
			if (bpos >= blen) break;
			
			int av = aa[apos];
			int bv = bb[bpos];
			
			if (av == bv) {
				res.add(av);
				apos++;
				bpos++;
			} else if (av > bv) {
				bpos++;
			} else { // av < bv
				apos++;
			}
		}
		
		return res;
	}

	/**
	 * Return the next ari (with only book and chapter) after the lastAriBc by scanning the source starting from pos.
	 * @param ppos pointer to pos. pos will be changed to ONE AFTER THE FOUND POSITION. So do not do another increment (++) outside this method.
	 */
	private static int nextAri(IntArrayList source, int[] ppos, int lastAriBc) {
		int[] s = source.buffer();
		int len = source.size();
		int pos = ppos[0];
		
		while (true) {
			if (pos >= len) return 0x0;
			
			int curAri = s[pos];
			int curAriBc = Ari.toBookChapter(curAri);
			
			if (curAriBc != lastAriBc) {
				// found!
				pos++;
				ppos[0] = pos;
				return curAriBc;
			} else {
				// still the same one, move to next.
				pos++;
			}
		}
	}

	static IntArrayList searchByGrepInside(final Version version, String word, final IntArrayList source, final SparseBooleanArray bookIds) {
		final IntArrayList res = new IntArrayList();
		boolean hasPlus = false;
		
		if (QueryTokenizer.isPlussedToken(word)) {
			hasPlus = true;
			word = QueryTokenizer.tokenWithoutPlus(word);
		}
	
		if (source == null) {
			for (Book book: version.getConsecutiveBooks()) {
				if (!bookIds.get(book.bookId, false)) {
					continue; // the book is not included in selected books to be searched
				}
				
				int chapter_count = book.chapter_count;
				
				for (int chapter_1 = 1; chapter_1 <= chapter_count; chapter_1++) {
					// try to find it wholly in a chapter
					String oneChapter = version.loadChapterTextLowercasedWithoutSplit(book, chapter_1);
					if (oneChapter != null && oneChapter.contains(word)) {
						// only do the following when inside a chapter, word is found
						searchByGrepInChapter(oneChapter, word, res, Ari.encode(book.bookId, chapter_1, 0), hasPlus);
					}
				}
	
				if (BuildConfig.DEBUG) Log.d(TAG, "searchByGrepInside book " + book.shortName + " done. res.size = " + res.size()); //$NON-NLS-1$ //$NON-NLS-2$
			}
		} else {
			// search only on book-chapters that are in the source
			int count = 0; // for stats
			
			int[] ppos = new int[1];
			int curAriBc = 0x000000;
			
			while (true) {
				curAriBc = nextAri(source, ppos, curAriBc);
				if (curAriBc == 0) break; // habis
				
				// No need to check null book, because we go here only after searching a previous token which is based on
				// getConsecutiveBooks which is impossible to have null books.
				final Book book = version.getBook(Ari.toBook(curAriBc));
				int chapter_1 = Ari.toChapter(curAriBc);
				
				final String oneChapter = version.loadChapterTextLowercasedWithoutSplit(book, chapter_1);
				if (oneChapter.contains(word)) {
					// Only to the following if inside a chapter we find the word.
					searchByGrepInChapter(oneChapter, word, res, curAriBc, hasPlus);
				}
				
				count++;
			}
			
			if (BuildConfig.DEBUG) Log.d(TAG, "searchByGrepInside book with source " + source.size() + " needed to read as many as " + count + " book-chapter. res.size=" + res.size()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
		
		return res;
	}

	private static void searchByGrepInChapter(String oneChapter, String word, IntArrayList res, int base, boolean hasPlus) {
		int verse_0 = 0;
		int lastV = -1;
		
		int posWord;
		if (hasPlus) {
			posWord = indexOfWholeWord(oneChapter, word, 0);
			if (posWord == -1) {
				return;
			}
		} else {
			posWord = oneChapter.indexOf(word);
		}
		
		int posN = oneChapter.indexOf(0x0a);
		
		while (true) {
			if (posN < posWord) {
				verse_0++;
				posN = oneChapter.indexOf(0x0a, posN+1);
				if (posN == -1) {
					return;
				}
			} else {
				if (verse_0 != lastV) {
					res.add(base + verse_0 + 1); // +1 to make it verse_1
					lastV = verse_0;
				}
				if (hasPlus) {
					posWord = indexOfWholeWord(oneChapter, word, posWord+1);
				} else {
					posWord = oneChapter.indexOf(word, posWord+1);
				}
				if (posWord == -1) {
					return;
				}
			}
		}
	}
	
	public static IntArrayList searchByRevIndex(final Version version, final Query query) {
		TimingLogger timing = new TimingLogger("RevIndex", "searchByRevIndex");
		RevIndex revIndex;
		revIndexLoading.acquireUninterruptibly();
		try {
			revIndex = loadRevIndex();
			if (revIndex == null) {
				Log.w(TAG, "Cannot load revindex (internal error)!");
				return searchByGrep(version, query);
			}
		} finally {
			revIndexLoading.release();
		}
		timing.addSplit("Load rev index");
		
		boolean[] passBitmapOr = new boolean[32768];
		boolean[] passBitmapAnd = new boolean[32768];
		Arrays.fill(passBitmapAnd, true);
		
		// Query e.g.: "a b" c +"d e" +f
		List<String> tokens; // this will be: "a" "b" "c" "+d" "+e" "+f"
		List<String> multiwords = null; // this will be: "a b" "+d e"
		{
			Set<String> tokenSet = new LinkedHashSet<>(Arrays.asList(QueryTokenizer.tokenize(query.query_string)));
			Log.d(TAG, "Tokens before retokenization:");
			for (String token: tokenSet) {
				Log.d(TAG, "- token: " + token);
			}
			
			Set<String> tokenSet2 = new LinkedHashSet<>();
			for (String token: tokenSet) {
				if (QueryTokenizer.isMultiwordToken(token)) {
					if (multiwords == null) {
						multiwords = new ArrayList<>();
					}
					multiwords.add(token);
					boolean token_plussed = QueryTokenizer.isPlussedToken(token);
					String token_bare = QueryTokenizer.tokenWithoutPlus(token);
					for (String token2: QueryTokenizer.tokenizeMultiwordToken(token_bare)) {
						if (token_plussed) {
							tokenSet2.add("+" + token2);
						} else {
							tokenSet2.add(token2);
						}
					}
				} else {
					tokenSet2.add(token);
				}
			}
			
			if (BuildConfig.DEBUG) {
				Log.d(TAG, "Tokens after retokenization:");
				for (String token: tokenSet2) {
					Log.d(TAG, "- token: " + token);
				}
				
				if (multiwords != null) {
					Log.d(TAG, "Multiwords:");
					for (String multiword: multiwords) {
						Log.d(TAG, "- multiword: " + multiword);
					}
				}
			}
			
			tokens = new ArrayList<>(tokenSet2);
		}
		
		timing.addSplit("Tokenize query");
		
		// optimization, if user doesn't filter any books
		boolean wholeBibleSearched = true;
		boolean[] searchedBookIds = new boolean[66];
		if (query.bookIds == null) {
			Arrays.fill(searchedBookIds, true);
		} else {
			for (int i = 0; i < 66; i++) {
				searchedBookIds[i] = query.bookIds.get(i, false);
				if (!searchedBookIds[i]) {
					wholeBibleSearched = false;
				}
			}
		}
		
		for (String token: tokens) {
			boolean plussed = QueryTokenizer.isPlussedToken(token);
			String token_bare = QueryTokenizer.tokenWithoutPlus(token);
			
			Arrays.fill(passBitmapOr, false);
			
			for (Map.Entry<String, int[]> e: revIndex.entrySet()) {
				String word = e.getKey();

				boolean match = false;
				if (plussed) {
					if (word.equals(token_bare)) match = true;
				} else {
					if (word.contains(token_bare)) match = true;
				}
				
				if (match) {
					int[] lids = e.getValue();
					for (int lid: lids) {
						passBitmapOr[lid] = true; // OR operation
					}
				}
			}
			
			int c = 0;
			for (boolean b: passBitmapOr) {
				if (b) c++;
			}
			timing.addSplit("gather lid for token '" + token + "' (" + c + ")");
			
			// AND operation with existing word(s)
			for (int i = passBitmapOr.length - 1; i >= 0; i--) {
				passBitmapAnd[i] &= passBitmapOr[i];
			}
			timing.addSplit("AND operation");
		}

		IntArrayList res = new IntArrayList();
		for (int i = 0, len = passBitmapAnd.length; i < len; i++) {
			if (passBitmapAnd[i]) {
				if (wholeBibleSearched) {
					int ari = LidToAri.lidToAri(i);
					if (ari > 0) res.add(ari);
				} else {
					// check first if this lid is in the searched portion
					int bookId = LidToAri.bookIdForLid(i);
					if (bookId >= 0 && searchedBookIds[bookId]) {
						int ari = LidToAri.lidToAri(i);
						if (ari > 0) res.add(ari);
					}
				}
			}
		}
		timing.addSplit("convert matching lids to aris (" + res.size() + ")");
		
		// last check: whether multiword tokens are all matching. No way to find this except by loading the text
		// and examining one by one whether the text contains those multiword tokens
		if (multiwords != null) {
			IntArrayList res2 = new IntArrayList(res.size());
			
			// separate the pluses
			String[] multiwords_bare = new String[multiwords.size()];
			boolean[] multiwords_plussed = new boolean[multiwords.size()];
			
			for (int i = 0, len = multiwords.size(); i < len; i++) {
				String multiword = multiwords.get(i);
				multiwords_bare[i] = QueryTokenizer.tokenWithoutPlus(multiword);
				multiwords_plussed[i] = QueryTokenizer.isPlussedToken(multiword);
			}
			
			SingleChapterVerses loadedChapter = null; // the currently loaded chapter, to prevent repeated loading of same chapter
			int loadedAriCv = 0; // chapter and verse of current Ari
			for (int i = 0, len = res.size(); i < len; i++) {
				int ari = res.get(i);
				
				int ariCv = Ari.toBookChapter(ari);
				if (ariCv != loadedAriCv) { // we can't reuse, we need to load from disk
					Book book = version.getBook(Ari.toBook(ari));
					if (book != null) {
						loadedChapter = version.loadChapterTextLowercased(book, Ari.toChapter(ari));
						loadedAriCv = ariCv;
					}
				}
				
				int verse_1 = Ari.toVerse(ari);
				if (verse_1 >= 1 && verse_1 <= loadedChapter.getVerseCount()) {
					String text = loadedChapter.getVerse(verse_1 - 1);
					if (text != null) {
						boolean passed = true;
						for (int j = 0, len2 = multiwords_bare.length; j < len2; j++) {
							String multiword_bare = multiwords_bare[j];
							boolean multiword_plussed = multiwords_plussed[j];
							
							if ((multiword_plussed && indexOfWholeWord(text, multiword_bare, 0) < 0) || (!multiword_plussed && !text.contains(multiword_bare))) {
								passed = false;
								break;
							}
						}
						if (passed) {
							res2.add(ari);
						}
					}
				}
			}
			
			res = res2;
			
			timing.addSplit("filter for multiword tokens (" + res.size() + ")");
		}

		timing.dumpToLog();

		return res;
	}
	
	@SuppressWarnings("synthetic-access") public static void preloadRevIndex() {
		new Thread() {
			@Override public void run() {
				TimingLogger timing = new TimingLogger("RevIndex", "preloadRevIndex");
				revIndexLoading.acquireUninterruptibly();
				try {
					loadRevIndex();
					timing.addSplit("loadRevIndex");
				} finally {
					revIndexLoading.release();
					timing.dumpToLog();
				}
			}
		}.start();
	}
	
	/**
	 * Revindex: an index used for searching quickly.
	 * The index is keyed on the word for searching, and the value is the list of verses' lid (KJV verse number, 1..31102).
	 * 
	 * Format of the Revindex file:
	 *   int total_word_count
	 *   {
	 *      uint8 word_len
	 *      int word_by_len_count // the number of words having length of word_len
	 *      {
	 *          byte[word_len] word // the word itself, stored as 8-bit per character
	 *          uint16 lid_count // the number of verses having this word
	 *          byte[] verse_list // see below
	 *      }[word_by_len_count]
	 *   }[] // until total_word_count is taken
	 *   
	 * The verses in verse_list are stored in either 8bit or 16bit, depending on the difference to the last entry before the current entry.
	 * The first entry on the list is always 16 bit.
	 * If one verse is specified in 16 bits, the 15-bit LSB is the verse lid itself (max 32767, although 31102 is the real max)
	 * in binary: 1xxxxxxx xxxxxxxx where x is the absolute verse lid as 15 bit uint.
	 * If one verse is specified in 8 bits, the 7-bit LSB is the difference between this verse and the last verse.
	 * in binary: 0ddddddd where d is the relative verse lid as 7 bit uint.
	 * For example, if a word is located at lids [0xff, 0x100, 0x300, 0x305], the stored data in the disk will be 
	 * in bytes: 0x80, 0xff, 0x01, 0x83, 0x00, 0x05.
	 */
	private static RevIndex loadRevIndex() {
		if (cache_revIndex != null) {
			RevIndex res = cache_revIndex.get();
			if (res != null) {
				return res;
			}
		}

		final InputStream assetInputStream;
		try {
			assetInputStream = App.context.getAssets().open("internal/" + AppConfig.get().internalPrefix + "_revindex_bt.bt");
		} catch (IOException e) {
			Log.d(TAG, "RevIndex is not available");
			return null;
		}

		final RevIndex res = new RevIndex();
		final InputStream raw = new BufferedInputStream(assetInputStream, 65536);
		
		byte[] buf = new byte[256];
		try {
			BintexReader br = new BintexReader(raw);
			
			int total_word_count = br.readInt();
			int word_count = 0;
			
			while (true) {
				int word_len = br.readUint8();
				int word_by_len_count = br.readInt();
				
				for (int i = 0; i < word_by_len_count; i++) {
					br.readRaw(buf, 0, word_len);
					@SuppressWarnings("deprecation") String word = new String(buf, 0, 0, word_len);
					
					int lid_count = br.readUint16();
					int last_lid = 0;
					int[] lids = new int[lid_count];
					int pos = 0;
					for (int j = 0; j < lid_count; j++) {
						int lid;
						int h = br.readUint8();
						if (h < 0x80) {
							lid = last_lid + h;
						} else {
							int l = br.readUint8();
							lid = ((h << 8) | l) & 0x7fff;
						}
						last_lid = lid;
						lids[pos++] = lid;
					}
					
					res.put(word, lids);
				}
				
				word_count += word_by_len_count;
				if (word_count >= total_word_count) {
					break;
				}
			}

			br.close();
		} catch (IOException e) {	
			return null;
		}
		
		cache_revIndex = new SoftReference<>(res);
		return res;
	}

	/**
	 * Case sensitive! Make sure s and words have been lowercased (or normalized).
	 */
	public static boolean satisfiesQuery(String s, String[] words) {
		for (String word: words) {
			boolean hasPlus = false;
			
			if (QueryTokenizer.isPlussedToken(word)) {
				hasPlus = true;
				word = QueryTokenizer.tokenWithoutPlus(word);
			}
			
			int wordPos;
			if (hasPlus) {
				wordPos = indexOfWholeWord(s, word, 0);
			} else {
				wordPos = s.indexOf(word);
			}
			if (wordPos == -1) {
				return false;
			}
		}
		return true;
	}

	private static int indexOfWholeWord(String text, String word, int start) {
		int len = text.length();
		
		while (true) {
			int pos = text.indexOf(word, start);
			if (pos == -1) return -1;
			
			// pos bukan -1
			
			// cek kiri
			// [pos] [charat pos-1]
			//  0        *          ok
			// >0       alpha       ng
			// >0      !alpha       ok
			if (pos != 0 && Character.isLetter(text.charAt(pos - 1))) {
				start = pos + 1;
				continue;
			}
			
			// cek kanan
			int end = pos + word.length();
			// [end] [charat end]
			// len       *         ok
			// != len  alpha       ng
			// != len  !alpha      ok
			if (end != len && Character.isLetter(text.charAt(end))) {
				start = pos + 1;
				continue;
			}
			
			// lulus
			return pos;
		}
	}

	public static SpannableStringBuilder hilite(final CharSequence s, String[] words, int hiliteColor) {
		final SpannableStringBuilder res = new SpannableStringBuilder(s);
		
		if (words == null) {
			return res;
		}
		
		int word_count = words.length;
		boolean[] hasPlusses = new boolean[word_count];
		{ // point to copy
			String[] words2 = new String[word_count];
			System.arraycopy(words, 0, words2, 0, word_count);
			for (int i = 0; i < word_count; i++) {
				if (QueryTokenizer.isPlussedToken(words2[i])) {
					words2[i] = QueryTokenizer.tokenWithoutPlus(words2[i]);
					hasPlusses[i] = true;
				}
			}
			words = words2;
		}

		// produce a plain text lowercased
		final char[] newString = new char[s.length()];
		for (int i = 0, len = s.length(); i < len; i++) {
			final char c = s.charAt(i);
			if (c >= 'A' && c <= 'Z') {
				newString[i] = (char) (c | 0x20);
			} else {
				newString[i] = c;
			}
		}
		final String plainText = new String(newString);
		
		int pos = 0;
		int[] attempt = new int[word_count];
		
		while (true) {
			for (int i = 0; i < word_count; i++) {
				if (hasPlusses[i]) {
					attempt[i] = indexOfWholeWord(plainText, words[i], pos);
				} else {
					attempt[i] = plainText.indexOf(words[i], pos);
				}
			}
			
			int minpos = Integer.MAX_VALUE;
			int minword = -1;
			
			for (int i = 0; i < word_count; i++) {
				if (attempt[i] >= 0) { // not -1 which means not found
					if (attempt[i] < minpos) {
						minpos = attempt[i];
						minword = i;
					}
				}
			}
			
			if (minword == -1) {
				break; // no more
			}
			
			pos = minpos + words[minword].length();
			
			int topos = minpos + words[minword].length();
			res.setSpan(new StyleSpan(Typeface.BOLD), minpos, topos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			res.setSpan(new ForegroundColorSpan(hiliteColor), minpos, topos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		}
		
		return res;
	}
}
