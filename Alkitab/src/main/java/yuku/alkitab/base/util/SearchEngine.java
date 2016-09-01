package yuku.alkitab.base.util;

import android.graphics.Typeface;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

	/**
	 * Contains processed tokens that is more efficient to be passed in to methods here such as
	 * {@link #hilite(CharSequence, ReadyTokens, int)} and {@link #satisfiesTokens(String, ReadyTokens)}.
	 */
	public static class ReadyTokens {
		final int token_count;
		final boolean[] hasPlusses;
		/** Already without plusses */
		final String[] tokens;
		final String[][] multiwords_tokens;

		public ReadyTokens(final String[] tokens) {
			final int token_count = tokens.length;
			this.token_count = token_count;
			this.hasPlusses = new boolean[token_count];
			this.tokens = new String[token_count];
			this.multiwords_tokens = new String[token_count][];

			for (int i = 0; i < token_count; i++) {
				final String token = tokens[i];
				if (QueryTokenizer.isPlussedToken(token)) {
					this.hasPlusses[i] = true;

					final String tokenWithoutPlus = QueryTokenizer.tokenWithoutPlus(token);
					this.tokens[i] = tokenWithoutPlus;

					final String[] multiword = QueryTokenizer.tokenizeMultiwordToken(tokenWithoutPlus);
					if (multiword != null) {
						this.multiwords_tokens[i] = multiword;
					}
				} else {
					this.tokens[i] = token;
				}
			}
		}
	}

	private static SoftReference<RevIndex> cache_revIndex;
	private static Semaphore revIndexLoading = new Semaphore(1);

	public static IntArrayList searchByGrep(final Version version, final Query query) {
		String[] tokens = QueryTokenizer.tokenize(query.query_string);

		// sort by word length, then alphabetically
		Arrays.sort(tokens, (object1, object2) -> {
			final int len1 = object1.length();
			final int len2 = object2.length();

			if (len1 > len2) return -1;
			if (len1 == len2) {
				return object1.compareTo(object2);
			}
			return 1;
		});

		// remove duplicates
		{
			final ArrayList<String> atokens = new ArrayList<>();
			String last = null;
			for (String token: tokens) {
				if (!token.equals(last)) {
					atokens.add(token);
				}
				last = token;
			}
			tokens = atokens.toArray(new String[atokens.size()]);
			if (BuildConfig.DEBUG) Log.d(TAG, "tokens = " + Arrays.toString(tokens));
		}

		// really search
		IntArrayList result = null;

		for (final String token : tokens) {
			final IntArrayList prev = result;

			{
				long ms = System.currentTimeMillis();
				result = searchByGrepInside(version, token, prev, query.bookIds);
				Log.d(TAG, "search token '" + token + "' needed: " + (System.currentTimeMillis() - ms) + " ms");
			}

			if (prev != null) {
				Log.d(TAG, "Will intersect " + prev.size() + " elements with " + result.size() + " elements...");
				result = intersect(prev, result);
				Log.d(TAG, "... the result is " + result.size() + " elements");
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

	static IntArrayList searchByGrepInside(final Version version, String token, final IntArrayList source, final SparseBooleanArray bookIds) {
		final IntArrayList res = new IntArrayList();
		final boolean hasPlus = QueryTokenizer.isPlussedToken(token);

		if (hasPlus) {
			token = QueryTokenizer.tokenWithoutPlus(token);
		}

		if (source == null) {
			for (Book book: version.getConsecutiveBooks()) {
				if (!bookIds.get(book.bookId, false)) {
					continue; // the book is not included in selected books to be searched
				}

				for (int chapter_1 = 1; chapter_1 <= book.chapter_count; chapter_1++) {
					// try to find it wholly in a chapter
					final int ariBc = Ari.encode(book.bookId, chapter_1, 0);
					searchByGrepForOneChapter(version, book, chapter_1, token, hasPlus, ariBc, res);
				}

				if (BuildConfig.DEBUG) Log.d(TAG, "searchByGrepInside book " + book.shortName + " done. res.size = " + res.size());
			}
		} else {
			// search only on book-chapters that are in the source
			int count = 0; // for stats

			int[] ppos = new int[1];
			int curAriBc = 0x000000;

			while (true) {
				curAriBc = nextAri(source, ppos, curAriBc);
				if (curAriBc == 0) break; // no more

				// No need to check null book, because we go here only after searching a previous token which is based on
				// getConsecutiveBooks, which is impossible to have null books.
				final Book book = version.getBook(Ari.toBook(curAriBc));
				final int chapter_1 = Ari.toChapter(curAriBc);

				searchByGrepForOneChapter(version, book, chapter_1, token, hasPlus, curAriBc, res);

				count++;
			}

			if (BuildConfig.DEBUG) Log.d(TAG, "searchByGrepInside book with source " + source.size() + " needed to read as many as " + count + " book-chapter. res.size=" + res.size());
		}

		return res;
	}

	/**
	 * @param token searched token without plusses
	 * @param res (output) result aris
	 * @param ariBc book-chapter ari, with verse must be set to 0
	 * @param hasPlus whether the token had plus
	 */
	private static void searchByGrepForOneChapter(final Version version, final Book book, final int chapter_1, final String token, final boolean hasPlus, final int ariBc, final IntArrayList res) {
		// This is a string of one chapter with verses joined by 0x0a ('\n')
		final String oneChapter = version.loadChapterTextLowercasedWithoutSplit(book, chapter_1);
		if (oneChapter == null) {
			return;
		}

		int verse_0 = 0;
		int lastV = -1;

		// Initial search
		String[] multiword = null;
		final int[] consumedLengthPtr = {0};

		// posToken is the last found position of the searched token
		// consumedLength is how much characters in the oneChapter was consumed when searching for the token.
		// Both of these variables must be set together in all cases.
		int posToken;
		int consumedLength;

		if (hasPlus) {
			multiword = QueryTokenizer.tokenizeMultiwordToken(token);

			if (multiword != null) {
				posToken = indexOfWholeMultiword(oneChapter, multiword, 0, true, consumedLengthPtr);
				consumedLength = consumedLengthPtr[0];
			} else {
				posToken = indexOfWholeWord(oneChapter, token, 0);
				consumedLength = token.length();
			}
		} else {
			posToken = oneChapter.indexOf(token, 0);
			consumedLength = token.length();
		}

		if (posToken == -1) {
			// initial search does not return results. It means the whole chapter does not contain the token.
			return;
		}

		int posN = oneChapter.indexOf(0x0a);

		while (true) {
			if (posN < posToken) {
				verse_0++;
				posN = oneChapter.indexOf(0x0a, posN + 1);
				if (posN == -1) {
					return;
				}
			} else {
				if (verse_0 != lastV) {
					res.add(ariBc + verse_0 + 1); // +1 to make it verse_1
					lastV = verse_0;
				}
				if (hasPlus) {
					if (multiword != null) {
						posToken = indexOfWholeMultiword(oneChapter, multiword, posToken + consumedLength, true, consumedLengthPtr);
						consumedLength = consumedLengthPtr[0];
					} else {
						posToken = indexOfWholeWord(oneChapter, token, posToken + consumedLength);
						consumedLength = token.length();
					}
				} else {
					posToken = oneChapter.indexOf(token, posToken + consumedLength);
					consumedLength = token.length();
				}
				if (posToken == -1) {
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

		final ReadyTokens rt = new ReadyTokens(QueryTokenizer.tokenize(query.query_string));

		if (BuildConfig.DEBUG) {
			Log.d(TAG, "Tokens after retokenization:");
			for (String token: rt.tokens) {
				Log.d(TAG, "- token: " + token);
			}

			Log.d(TAG, "Multiwords:");
			for (String[] multiword: rt.multiwords_tokens) {
				Log.d(TAG, "- multiword: " + Arrays.toString(multiword));
			}
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

		for (int i = 0; i < rt.token_count; i++) {
			if (rt.multiwords_tokens[i] != null) {
				// This is multiword token, handled separately below
				continue;
			}

			final String token_bare = rt.tokens[i];
			final boolean plussed = rt.hasPlusses[i];

			Arrays.fill(passBitmapOr, false);

			for (Map.Entry<String, int[]> e : revIndex.entrySet()) {
				String word = e.getKey();

				boolean match = false;
				if (plussed) {
					if (word.equals(token_bare)) match = true;
				} else {
					if (word.contains(token_bare)) match = true;
				}

				if (match) {
					int[] lids = e.getValue();
					for (int lid : lids) {
						passBitmapOr[lid] = true; // OR operation
					}
				}
			}

			int c = 0;
			for (boolean b : passBitmapOr) {
				if (b) c++;
			}
			timing.addSplit("gather lid for token '" + token_bare + "' (" + c + ")");

			// AND operation with existing word(s)
			for (int j = passBitmapOr.length - 1; j >= 0; j--) {
				passBitmapAnd[j] &= passBitmapOr[j];
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
		final List<String[]> multiwords = new ArrayList<>();
		for (final String[] multiword_tokens : rt.multiwords_tokens) {
			if (multiword_tokens != null) {
				multiwords.add(multiword_tokens);
			}
		}

		if (multiwords.size() > 0) {
			final IntArrayList res2 = new IntArrayList(res.size());

			final int[] consumedLengthPtr = {0};

			SingleChapterVerses loadedChapter = null; // the currently loaded chapter, to prevent repeated loading of same chapter
			int loadedAriCv = 0; // chapter and verse of current Ari
			for (int i = 0, len = res.size(); i < len; i++) {
				final int ari = res.get(i);

				final int ariCv = Ari.toBookChapter(ari);
				if (ariCv != loadedAriCv) { // we can't reuse, we need to load from disk
					final Book book = version.getBook(Ari.toBook(ari));
					if (book == null) {
						continue;
					} else {
						loadedChapter = version.loadChapterTextLowercased(book, Ari.toChapter(ari));
						loadedAriCv = ariCv;
					}
				}

				if (loadedChapter == null) {
					continue;
				}

				final int verse_1 = Ari.toVerse(ari);
				if (verse_1 >= 1 && verse_1 <= loadedChapter.getVerseCount()) {
					final String text = loadedChapter.getVerse(verse_1 - 1);
					if (text != null) {
						boolean passed = true;
						for (final String[] multiword_tokens : multiwords) {
							if (indexOfWholeMultiword(text, multiword_tokens, 0, false, consumedLengthPtr) == -1) {
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

	public static void preloadRevIndex() {
		Background.run(() -> {
			TimingLogger timing = new TimingLogger("RevIndex", "preloadRevIndex");
			revIndexLoading.acquireUninterruptibly();
			try {
				loadRevIndex();
				timing.addSplit("loadRevIndex");
			} finally {
				revIndexLoading.release();
				timing.dumpToLog();
			}
		});
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
	 * Case sensitive! Make sure <code>s</code> and <code>rt</code> tokens have been lowercased (or normalized).
	 */
	public static boolean satisfiesTokens(final String s, @NonNull final ReadyTokens rt) {
		for (int i = 0; i < rt.token_count; i++) {
			final boolean hasPlus = rt.hasPlusses[i];

			final int posToken;
			if (hasPlus) {
				final String[] multiword_tokens = rt.multiwords_tokens[i];
				if (multiword_tokens != null) {
					posToken = indexOfWholeMultiword(s, multiword_tokens, 0, false, null);
				} else {
					posToken = indexOfWholeWord(s, rt.tokens[i], 0);
				}
			} else {
				posToken = s.indexOf(rt.tokens[i]);
			}

			if (posToken == -1) {
				return false;
			}
		}
		return true;
	}

	/**
	 * This looks for a word that is surrounded by non-letter-or-digit characters.
	 * This works well only if the word is not a multiword.
	 * @param text haystack
	 * @param word needle
	 * @param start start at character
	 * @return -1 or position of the word
	 */
	private static int indexOfWholeWord(String text, String word, int start) {
		final int len = text.length();

		while (true) {
			final int pos = text.indexOf(word, start);
			if (pos == -1) return -1;

			// check left
			// [pos] [charat pos-1] [charat pos-2]
			//  0                                    ok
			// >1       alnum            '@'         ok
			// >1       alnum          not '@'       ng
			// >0       alnum                        ng
			// >0     not alnum                      ok
			if (pos != 0 && Character.isLetterOrDigit(text.charAt(pos - 1))) {
				if (pos != 1 && text.charAt(pos - 2) == '@') {
					// oh, before this word there is a tag. Then it is OK.
				} else {
					start = pos + 1; // give up
					continue;
				}
			}

			// check right
			int end = pos + word.length();
			// [end]   [charat end]
			// len         *         ok
			// != len    alnum       ng
			// != len  not alnum     ok
			if (end != len && Character.isLetterOrDigit(text.charAt(end))) {
				start = pos + 1; // give up
				continue;
			}

			// passed
			return pos;
		}
	}

	/**
	 * This looks for a multiword that is surrounded by non-letter characters.
	 * This works for multiword because it tries to strip tags and punctuations from the text before matching.
	 * @param text haystack.
	 * @param multiword multiword that has been split into words. Must have at least one element.
	 * @param start character index of text to start searching from
	 * @param isNewlineDelimitedText <code>text</code> has '\n' as delimiter between verses. <code>multiword</code> cannot be searched across different verses.
	 * @param consumedLengthPtr (length-1 array output) how many characters matched from the source text to satisfy the multiword. Will be 0 if this method returns -1.
	 * @return -1 or position of the multiword.
	 */
	private static int indexOfWholeMultiword(String text, String[] multiword, int start, boolean isNewlineDelimitedText, @Nullable int[] consumedLengthPtr) {
		final int len = text.length();
		final String firstWord = multiword[0];

		findAllWords: while (true) {
			final int firstPos = indexOfWholeWord(text, firstWord, start);
			if (firstPos == -1) {
				// not even the first word is found, so we give up
				if (consumedLengthPtr != null) consumedLengthPtr[0] = 0;
				return -1;
			}

			int pos = firstPos + firstWord.length();

			// find the next words, but we want to consume everything after the previous word that is
			// not eligible as search characters, which are tags and non-letters.
			for (int i = 1, multiwordLen = multiword.length; i < multiwordLen; i++) {
				final int posBeforeConsume = pos;
				// consume!
				while (pos < len) {
					final char c = text.charAt(pos);
					if (c == '@') {
						if (pos == len - 1) {
							// bad data (nothing after '@')
						} else {
							pos++;
							final char d = text.charAt(pos);
							if (d == '<') {
								final int closingTagStart = text.indexOf("@>", pos + 1);
								if (closingTagStart == -1) {
									// bad data (no closing tag)
								} else {
									pos = closingTagStart + 1;
								}
							} else {
								// single-letter formatting code, move on...
							}
						}
					} else if (Character.isLetterOrDigit(c)) {
						break;
					} else if (isNewlineDelimitedText && c == '\n') {
						// can't cross verse boundary, so we give up and try from beginning again
						start = pos + 1;
						continue findAllWords;
					} else {
						// non-letter, move on...
					}

					pos++;
				}

				if (BuildConfig.DEBUG) {
					Log.d(TAG, "=========================");
					Log.d(TAG, "multiword: " + Arrays.toString(multiword));
					Log.d(TAG, "text     : #" + text.substring(Math.max(0, posBeforeConsume - multiword[i - 1].length()), Math.min(len, posBeforeConsume + 80)) + "#");
					Log.d(TAG, "skipped  : #" + text.substring(posBeforeConsume, pos) + "#");
					Log.d(TAG, "=========================////");
				}

				final String word = multiword[i];

				final int foundWordStart = indexOfWholeWord(text, word, pos);
				if (foundWordStart == -1 /* Not found... */ || foundWordStart != pos /* ...or another word comes */) {
					// subsequent words is not found at the correct position, so loop from beginning again
					start = pos;
					continue findAllWords;
				}

				// prepare for next iteration
				pos = foundWordStart + word.length();
			}

			// all words are found!
			if (consumedLengthPtr != null) consumedLengthPtr[0] = pos - firstPos;
			return firstPos;
		}
	}

	public static SpannableStringBuilder hilite(final CharSequence s, final ReadyTokens rt, int hiliteColor) {
		final SpannableStringBuilder res = new SpannableStringBuilder(s);

		if (rt == null) {
			return res;
		}

		final int token_count = rt.token_count;

		// from source text, produce a plain text lowercased
		final char[] newString = new char[s.length()];
		for (int i = 0, len = s.length(); i < len; i++) {
			final char c = s.charAt(i);
			if (c >= 'A' && c <= 'Z') {
				newString[i] = (char) (c | 0x20);
			} else {
				newString[i] = Character.toLowerCase(c);
			}
		}
		final String plainText = new String(newString);

		int pos = 0;
		final int[] attempts = new int[token_count];
		final int[] consumedLengths = new int[token_count];

		// local vars for optimizations
		final boolean[] hasPlusses = rt.hasPlusses;
		final String[] tokens = rt.tokens;
		final String[][] multiwords_tokens = rt.multiwords_tokens;

		// temp buf
		final int[] consumedLengthPtr = {0};
		while (true) {
			for (int i = 0; i < token_count; i++) {
				if (hasPlusses[i]) {
					if (multiwords_tokens[i] != null) {
						attempts[i] = indexOfWholeMultiword(plainText, multiwords_tokens[i], pos, false, consumedLengthPtr);
						consumedLengths[i] = consumedLengthPtr[0];
					} else {
						attempts[i] = indexOfWholeWord(plainText, tokens[i], pos);
						consumedLengths[i] = tokens[i].length();
					}
				} else {
					attempts[i] = plainText.indexOf(tokens[i], pos);
					consumedLengths[i] = tokens[i].length();
				}
			}

			// from the attempts above, find the earliest
			int minpos = Integer.MAX_VALUE;
			int mintokenindex = -1;

			for (int i = 0; i < token_count; i++) {
				if (attempts[i] >= 0) { // not -1 which means not found
					if (attempts[i] < minpos) {
						minpos = attempts[i];
						mintokenindex = i;
					}
				}
			}

			if (mintokenindex == -1) {
				break; // no more
			}

			final int topos = minpos + consumedLengths[mintokenindex];
			res.setSpan(new StyleSpan(Typeface.BOLD), minpos, topos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			res.setSpan(new ForegroundColorSpan(hiliteColor), minpos, topos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			pos = topos;
		}

		return res;
	}
}
