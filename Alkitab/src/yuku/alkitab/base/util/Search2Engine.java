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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;

import yuku.afw.App;
import yuku.afw.D;
import yuku.alkitab.base.S;
import yuku.alkitab.base.config.AppConfig;
import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.model.Book;
import yuku.bintex.BintexReader;

public class Search2Engine {
	public static final String TAG = Search2Engine.class.getSimpleName();

	public static class Query implements Parcelable {
		public String carian;
		public SparseBooleanArray xkitabPos;
		
		@Override public int describeContents() {
			return 0;
		}
		
		@Override public void writeToParcel(Parcel dest, int flags) {
			dest.writeString(carian);
			dest.writeSparseBooleanArray(xkitabPos);
		}

		public static final Parcelable.Creator<Query> CREATOR = new Parcelable.Creator<Query>() {
			@Override public Query createFromParcel(Parcel in) {
				Query res = new Query();
				res.carian = in.readString();
				res.xkitabPos = in.readSparseBooleanArray();
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
	
	public static IntArrayList searchByGrep(Query query) {
		String[] xkata = QueryTokenizer.tokenize(query.carian);
		
		// urutkan berdasarkan panjang, lalu abjad
		Arrays.sort(xkata, new Comparator<String>() {
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
		
		// buang ganda
		{
			ArrayList<String> akata = new ArrayList<String>();
			String terakhir = null;
			for (String kata: xkata) {
				if (!kata.equals(terakhir)) {
					akata.add(kata);
				}
				terakhir = kata;
			}
			xkata = akata.toArray(new String[akata.size()]);
			Log.d(TAG, "xkata = " + Arrays.toString(xkata)); //$NON-NLS-1$
		}
		
		// cari betulan
		IntArrayList hasil = null;
		
		{
			int index = 0;
			
			while (true) {
				if (index >= xkata.length) {
					break;
				}
				
				String kata = xkata[index];
				
				//Debug.startMethodTracing();
				IntArrayList lama = hasil;
	
				{
					long ms = System.currentTimeMillis();
					hasil = searchByGrepInside(kata, lama, query.xkitabPos);
					Log.d(TAG, "cari kata '" + kata + "' pake waktu: " + (System.currentTimeMillis() - ms) + " ms"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				}
	
				if (lama != null) {
					Log.d(TAG, "Akan mengiris " + lama.size() + " elemen dengan " + hasil.size() + " elemen..."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					hasil = iris(lama, hasil);
					Log.d(TAG, "... hasilnya " + hasil.size() + " elemen");  //$NON-NLS-1$//$NON-NLS-2$
				}
				//Debug.stopMethodTracing();
				
				index++;
			}
		}
		
		return hasil;
	}

	private static IntArrayList iris(IntArrayList a, IntArrayList b) {
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
				continue;
			} else if (av > bv) {
				bpos++;
				continue;
			} else { // av < bv
				apos++;
				continue;
			}
		}
		
		return res;
	}

	/**
	 * Keluarkan ari (kitab pasal saja) berikutnya setelah ariKpTerakhir dengan ngesken sumber mulai pos.
	 * @param ppos pointer ke pos. pos akan berubah jadi SATU SETELAH POSISI KETEMU. Jadi jangan ++ lagi di luar sini.
	 */
	private static int ariBerikutnya(IntArrayList sumber, int[] ppos, int ariKpTerakhir) {
		int[] s = sumber.buffer();
		int len = sumber.size();
		int pos = ppos[0];
		
		while (true) {
			if (pos >= len) return 0x0;
			
			int ariIni = s[pos];
			int ariKpIni = Ari.toBookChapter(ariIni);
			
			if (ariKpIni != ariKpTerakhir) {
				// ketemu!
				pos++;
				ppos[0] = pos;
				return ariKpIni;
			} else {
				// masih sama, maju.
				pos++;
			}
		}
	}

	static IntArrayList searchByGrepInside(String kata, IntArrayList sumber, SparseBooleanArray xkitabPos) {
		IntArrayList res = new IntArrayList();
		boolean pakeTambah = false;
		
		if (QueryTokenizer.isPlussedToken(kata)) {
			pakeTambah = true;
			kata = QueryTokenizer.tokenWithoutPlus(kata);
		}
	
		if (sumber == null) {
			for (Book k: S.activeVersion.getConsecutiveBooks()) {
				if (xkitabPos.get(k.bookId, false) == false) {
					continue; // ga termasuk dalam kitab yang dipilih
				}
				
				int npasal = k.nchapter;
				
				for (int pasal_1 = 1; pasal_1 <= npasal; pasal_1++) {
					// coba sepasal sekaligus dulu.
					String sepasal = S.loadChapterTextLowercasedWithoutSplit(S.activeVersion, k, pasal_1);
					if (sepasal.indexOf(kata) >= 0) {
						// hanya lakukan ini jika dalam sepasal kedetek ada kata
						searchByGrepInChapter(sepasal, kata, res, Ari.encode(k.bookId, pasal_1, 0), pakeTambah);
					}
				}
	
				if (D.EBUG) Log.d(TAG, "cariDalam kitab " + k.nama + " selesai. res.size = " + res.size()); //$NON-NLS-1$ //$NON-NLS-2$
			}
		} else {
			// cari hanya pada kp (kitab pasal) yang ada di sumber.
			int hitung = 0; // buat statistik aja
			
			int[] ppos = new int[1];
			int ariKpKini = 0x000000;
			
			while (true) {
				ariKpKini = ariBerikutnya(sumber, ppos, ariKpKini);
				if (ariKpKini == 0) break; // habis
				
				// ga usa cek kitab null, karena masuk sini hanya kalau udah pernah search token sebelumnya
				// yang berdasarkan getConsecutiveXkitab yang ga mungkin ada nullnya.
				Book k = S.activeVersion.getBook(Ari.toBook(ariKpKini));
				int pasal_1 = Ari.toChapter(ariKpKini);
				
				String sepasal = S.loadChapterTextLowercasedWithoutSplit(S.activeVersion, k, pasal_1);
				if (sepasal.indexOf(kata) >= 0) {
					// hanya lakukan ini jika dalam sepasal kedetek ada kata
					searchByGrepInChapter(sepasal, kata, res, ariKpKini, pakeTambah);
				}
				
				hitung++;
			}
			
			Log.d(TAG, "cariDalam kitab dengan sumber " + sumber.size() + " perlu baca kp sebanyak " + hitung + " res.size=" + res.size()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
		
		return res;
	}

	private static void searchByGrepInChapter(String sepasal, String kata, IntArrayList res, int base, boolean pakeTambah) {
		int a = 0;
		int aterakhir = -1;
		
		int posKata;
		if (pakeTambah) {
			posKata = indexOfWholeWord(sepasal, kata, 0);
			if (posKata == -1) {
				return;
			}
		} else {
			posKata = sepasal.indexOf(kata);
		}
		
		int posN = sepasal.indexOf(0x0a);
		
		while (true) {
			if (posN < posKata) {
				a++;
				posN = sepasal.indexOf(0x0a, posN+1);
				if (posN == -1) {
					return;
				}
			} else {
				if (a != aterakhir) {
					res.add(base + a + 1); // +1 supaya jadi ayat_1
					aterakhir = a;
				}
				if (pakeTambah) {
					posKata = indexOfWholeWord(sepasal, kata, posKata+1);
				} else {
					posKata = sepasal.indexOf(kata, posKata+1);
				}
				if (posKata == -1) {
					return;
				}
			}
		}
	}
	
	public static IntArrayList searchByRevIndex(Query query) {
		TimingLogger timing = new TimingLogger("RevIndex", "searchByRevIndex");
		RevIndex revIndex;
		revIndexLoading.acquireUninterruptibly();
		try {
			revIndex = loadRevIndex();
			if (revIndex == null) {
				Log.w(TAG, "Cannot load revindex (internal error)!");
				return searchByGrep(query);
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
			Set<String> tokenSet = new LinkedHashSet<String>(Arrays.asList(QueryTokenizer.tokenize(query.carian)));
			Log.d(TAG, "Tokens before retokenization:");
			for (String token: tokenSet) {
				Log.d(TAG, "- token: " + token);
			}
			
			Set<String> tokenSet2 = new LinkedHashSet<String>();
			for (String token: tokenSet) {
				if (QueryTokenizer.isMultiwordToken(token)) {
					if (multiwords == null) {
						multiwords = new ArrayList<String>();
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
			
			tokens = new ArrayList<String>(tokenSet2);
		}
		
		timing.addSplit("Tokenize query");
		
		// optimization, if user doesn't filter any books
		boolean wholeBibleSearched = true;
		boolean[] searchedBookIds = new boolean[66];
		if (query.xkitabPos == null) {
			Arrays.fill(searchedBookIds, true);
		} else {
			for (int i = 0; i < 66; i++) {
				searchedBookIds[i] = query.xkitabPos.get(i, false);
				if (searchedBookIds[i] == false) {
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
					if (word.indexOf(token_bare) >= 0) match = true;
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
			
			String[] loadedChapter = null; // the currently loaded chapter, to prevent repeated loading of same chapter
			int loadedAriCv = 0; // chapter and verse of current Ari
			for (int i = 0, len = res.size(); i < len; i++) {
				int ari = res.get(i);
				
				int ariCv = Ari.toBookChapter(ari);
				if (ariCv != loadedAriCv) { // we can't reuse, we need to load from disk
					Book book = S.activeVersion.getBook(Ari.toBook(ari));
					if (book != null) {
						loadedChapter = S.loadChapterTextLowercased(S.activeVersion, book, Ari.toChapter(ari));
						loadedAriCv = ariCv;
					}
				}
				
				int verse_1 = Ari.toVerse(ari);
				if (verse_1 >= 1 && verse_1 <= loadedChapter.length) {
					String text = loadedChapter[verse_1 - 1];
					if (text != null) {
						boolean passed = true;
						for (int j = 0, len2 = multiwords_bare.length; j < len2; j++) {
							String multiword_bare = multiwords_bare[j];
							boolean multiword_plussed = multiwords_plussed[j];
							
							if ((multiword_plussed && indexOfWholeWord(text, multiword_bare, 0) < 0) || (!multiword_plussed && text.indexOf(multiword_bare) < 0)) {
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
	
	private static RevIndex loadRevIndex() {
		if (cache_revIndex != null) {
			RevIndex res = cache_revIndex.get();
			if (res != null) {
				return res;
			}
		}
		
		RevIndex res = new RevIndex();
		
		InputStream raw = new BufferedInputStream(S.openRaw(AppConfig.get(App.context).internalPrefix + "_revindex_bt"), 65536);
		
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
		} catch (IOException e) {	
			return null;
		}
		
		cache_revIndex = new SoftReference<RevIndex>(res);
		return res;
	}

	/**
	 * case sensitive! pastikan s dan xkata sudah dilowercase sebelum masuk sini.
	 */
	public static boolean memenuhiCarian(String s, String[] xkata) {
		for (String kata: xkata) {
			boolean pakeTambah = false;
			
			if (QueryTokenizer.isPlussedToken(kata)) {
				pakeTambah = true;
				kata = QueryTokenizer.tokenWithoutPlus(kata);
			}
			
			int posKata;
			if (pakeTambah) {
				posKata = indexOfWholeWord(s, kata, 0);
			} else {
				posKata = s.indexOf(kata);
			}
			if (posKata == -1) {
				return false;
			}
		}
		return true;
	}

	private static int indexOfWholeWord(String text, String word, int start) {
		int len = text.length();
		
		while (true) {
			int pos = text.indexOf(word, start);
			//Log.d(TAG, "pos=" + pos + " untuk " + kata + " pada: " + sepasal);   //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$
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

	public static SpannableStringBuilder hilite(String s, String[] xkata, int warnaHilite) {
		SpannableStringBuilder res = new SpannableStringBuilder(s);
		
		if (xkata == null) {
			return res;
		}
		
		int nkata = xkata.length;
		boolean[] xpakeTambah = new boolean[nkata];
		{ // point to copy
			String[] xkata2 = new String[nkata];
			System.arraycopy(xkata, 0, xkata2, 0, nkata);
			for (int i = 0; i < nkata; i++) {
				if (QueryTokenizer.isPlussedToken(xkata2[i])) {
					xkata2[i] = QueryTokenizer.tokenWithoutPlus(xkata2[i]);
					xpakeTambah[i] = true;
				}
			}
			xkata = xkata2;
		}
		
		s = s.toLowerCase(Locale.getDefault());
		
		int pos = 0;
		int[] coba = new int[nkata];
		
		while (true) {
			for (int i = 0; i < nkata; i++) {
				if (xpakeTambah[i]) {
					coba[i] = indexOfWholeWord(s, xkata[i], pos);
				} else {
					coba[i] = s.indexOf(xkata[i], pos);
				}
			}
			
			int minpos = Integer.MAX_VALUE;
			int minkata = -1;
			
			for (int i = 0; i < nkata; i++) {
				if (coba[i] >= 0) { // bukan -1 yang berarti ga ketemu
					if (coba[i] < minpos) {
						minpos = coba[i];
						minkata = i;
					}
				}
			}
			
			if (minkata == -1) {
				break; // ga ada lagi
			}
			
			pos = minpos + xkata[minkata].length();
			
			int kepos = minpos + xkata[minkata].length();
			res.setSpan(new StyleSpan(Typeface.BOLD), minpos, kepos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			res.setSpan(new ForegroundColorSpan(warnaHilite), minpos, kepos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		}
		
		return res;
	}
}
