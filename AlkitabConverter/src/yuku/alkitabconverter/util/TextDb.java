package yuku.alkitabconverter.util;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import yuku.alkitab.base.model.Ari;



public class TextDb {
	public static final String TAG = TextDb.class.getSimpleName();
	
	public interface TextProcessor {
		void process(int ari, VerseState verseState);
	}
	
	public static class VerseState {
		// was: "public int menjorok;" but no longer used
		public String text;
	}
	
	TreeMap<Integer, VerseState> map = new TreeMap<Integer, VerseState>();
	
	public String append(int bookId, int chapter_1, int verse_1, String s, int currentIndent) {
		return append(Ari.encode(bookId, chapter_1, verse_1), s, currentIndent);
	}
	
	public String append(int bookId, int chapter_1, int verse_1, String s, int currentIndent, String separatorWhenExisting) {
		return append(Ari.encode(bookId, chapter_1, verse_1), s, currentIndent, separatorWhenExisting);
	}
	
	/**
	 * @param currentIndent if -1, don't write anything. 
	 * @return
	 */
	public String append(int ari, String s, int currentIndent) {
		return append(ari, s, currentIndent, null);
	}

	/**
	 * @param separatorWhenExisting if the text is appended to an ari that has already some text, append this first, then the text. 
	 * @param currentIndent if -1, don't write anything. 
	 * @return
	 */
	public String append(int ari, String text, int currentIndent, String separatorWhenExisting) {
		VerseState as = map.get(ari);
		boolean isNew = false;
		if (as == null) {
			as = new VerseState();
			as.text = "";
			map.put(ari, as);
			isNew = true;
		}
		
		boolean writtenParaMarker = false;
		
		if (currentIndent != -1) {
			if (currentIndent == -2) {
				as.text += "@^";
			} else if (currentIndent < 0 || currentIndent > 4) {
				throw new RuntimeException("menjorok ngaco: " + currentIndent);
			} else {
				as.text += "@" + String.valueOf(currentIndent);
			}
			
			writtenParaMarker = true;
			
			// was: "update menjoroknya ayatstate" but no longer used
//			for (int i = 0; i < as.isi.length(); i++) {
//				if (as.isi.charAt(i) == '@' && as.isi.charAt(i+1) >= '0' && as.isi.charAt(i+1) <= '4') {
//					as.menjorok = as.isi.charAt(i+1) - '0';
//				}
//			}
		}
		
		if (!isNew) {
			as.text += separatorWhenExisting;
		}
		
		if (writtenParaMarker) {
			as.text += leftSpaceTrim(text);
		} else {
			as.text += text;
		}
		
		// buang spasi di depan kalo ada
		while (as.text.startsWith(" ")) {
			as.text = as.text.substring(1);
		}
		
		// kasih @@ kalo depannya blum ada
		if (as.text.contains("@") && !as.text.startsWith("@@")) {
			as.text = "@@" + as.text;
		}
		
		return as.text;
	}
	
	private static String leftSpaceTrim(String s) {
		for (int i = 0; i < s.length(); i++) {
			if (s.charAt(i) != ' ') {
				return s.substring(i);
			}
		}
		return s;
	}
	
	public void normalize() {
		Set<Integer> keys = new TreeSet<Integer>(map.keySet());
		int last_bookId = -1;
		int last_chapter_1 = 0;
		int last_verse_1 = 0;
		
		for (int ari: keys) {
			int bookId = Ari.toBook(ari);
			int chapter_1 = Ari.toChapter(ari);
			int verse_1 = Ari.toVerse(ari);
			
			if (bookId != last_bookId) {
				// must start with chapter_1 1 and verse_1 1
				if (chapter_1 != 1 || verse_1 != 1) {
					throw new RuntimeException("at " + bookId + " " + chapter_1 + " " + verse_1 + ": " + " new book does not start from 1:1");
				}
				// different book, ignore and restart
				last_bookId = bookId;
				last_chapter_1 = chapter_1;
				last_verse_1 = verse_1;
				continue;
			}
			
			if (chapter_1 == last_chapter_1) {
				if (verse_1 != last_verse_1 + 1) {
					System.out.println("at " + bookId + " " + chapter_1 + " " + verse_1 + ": " + " skipped after " + last_bookId + " " + last_chapter_1 + " " + last_verse_1);
					System.out.println("Adding empty verses:");
					for (int a = last_verse_1 + 1; a < verse_1; a++) {
						System.out.println("  at " + bookId + " " + chapter_1 + " " + a + ": " + " (blank)");
						append(bookId, chapter_1, a, "", 0);
					}
				}
			} else if (chapter_1 == last_chapter_1 + 1) {
				if (verse_1 != 1) {
					throw new RuntimeException("at " + bookId + " " + chapter_1 + " " + verse_1 + ": " + " verse_1 is not 1");
				}
			} else {
				throw new RuntimeException("at " + bookId + " " + chapter_1 + " " + verse_1 + ": " + " so wrong! it's after " + last_bookId + " " + last_chapter_1 + " " + last_verse_1);
			}
			
			last_bookId = bookId;
			last_chapter_1 = chapter_1;
			last_verse_1 = verse_1;
		}
		
		System.out.println("normalize done");
	}
	
	public void dump(PrintStream ps) {
		ps.println("TOTAL text: " + map.size());
		for (Entry<Integer, VerseState> e: map.entrySet()) {
			ps.printf("%d\t%d\t%d\t%s%n", Ari.toBook(e.getKey()) + 1, Ari.toChapter(e.getKey()), Ari.toVerse(e.getKey()), e.getValue().text);
		}
	}
	
	public void dump() {
		dump(System.out);
	}

	public int size() {
		return map.size();
	}

	public List<Rec> toRecList() {
		List<Rec> res = new ArrayList<Rec>();
		for (Entry<Integer, VerseState> e: map.entrySet()) {
			Rec rec = new Rec();
			int ari = e.getKey();
			rec.book_1 = Ari.toBook(ari) + 1;
			rec.chapter_1 = Ari.toChapter(ari);
			rec.verse_1 = Ari.toVerse(ari);
			rec.text = e.getValue().text;
			res.add(rec);
		}
		return res;
	}

	public void processEach(TextProcessor textProcessor) {
		for (Map.Entry<Integer, VerseState> e: map.entrySet()) {
			textProcessor.process(e.getKey(), e.getValue());
		}
	}

	public int getBookCount() {
		Set<Integer> bookIds = new LinkedHashSet<>();
		for (Map.Entry<Integer, VerseState> e: map.entrySet()) {
			int bookId = Ari.toBook(e.getKey());
			bookIds.add(bookId);
		}
		return bookIds.size();
	}
	
	public int[] getBookIds() {
		Set<Integer> bookIds = new TreeSet<>();
		for (Map.Entry<Integer, VerseState> e: map.entrySet()) {
			int bookId = Ari.toBook(e.getKey());
			bookIds.add(bookId);
		}
		int[] res = new int[bookIds.size()];
		int c = 0;
		for (Integer bookId: bookIds) {
			res[c++] = bookId;
		}
		return res;
	}
	
	/**
	 * No skipped chapters recognized. So if a book has chapters [1, 5, 6], this returns 6, not 3. 
	 */
	public int getChapterCountForBook(int bookId) {
		int maxChapter = 0;
		for (Map.Entry<Integer, VerseState> e: map.entrySet()) {
			int ari = e.getKey();
			if (Ari.toBook(ari) == bookId) {
				int chapter_1 = Ari.toChapter(ari);
				if (chapter_1 > maxChapter) maxChapter = chapter_1; 
			}
		}
		return maxChapter;
	}

	/**
	 * No skipped verses recognized. So if a chapter has verses [1, 5, 6], this returns 6, not 3. 
	 */
	public int getVerseCountForBookChapter(int bookId, int chapter_1) {
		int maxVerse = 0;
		for (Map.Entry<Integer, VerseState> e: map.entrySet()) {
			int ari = e.getKey();
			if (Ari.toBook(ari) == bookId && Ari.toChapter(ari) == chapter_1) {
				int verse_1 = Ari.toVerse(ari);
				if (verse_1 > maxVerse) maxVerse = verse_1; 
			}
		}
		return maxVerse;
	}

	public String getVerseText(int bookId, int chapter_1, int verse_1) {
		return map.get(Ari.encode(bookId, chapter_1, verse_1)).text;
	}
}
