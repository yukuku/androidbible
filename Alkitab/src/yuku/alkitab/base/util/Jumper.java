package yuku.alkitab.base.util;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import yuku.alkitab.base.model.Book;

public class Jumper {
	public static final String TAG = Jumper.class.getSimpleName();
	
	private String p_kitab;
	private int p_chapter;
	private int p_verse;
	
	/** If bookId found from OSIS book names, set this to other than -1 and this will be returned */
	private int p_bookIdFromOsis = -1;

	private boolean parseSucceeded = false;
	
	private static class KitabRef {
		String pendek;
		int pos;
		
		public KitabRef() {
		}

		@Override public String toString() {
			return pendek + ":" + pos; //$NON-NLS-1$
		}
	}
	
	private static WeakHashMap<Book[], List<KitabRef>> pendekCache = new WeakHashMap<Book[], List<KitabRef>>();
	
	public Jumper(String referenceToParse) {
		parseSucceeded = parse(referenceToParse);
	}
	
	/**
	 * Ga bisa diparse sebagai bilangan. "4-5" true. "Halo" true. "123" false.
	 */
	private static boolean isKata(String s) {
		char c = s.charAt(0);
		if (c < '0' || c > '9') return true;
		
		try {
			Integer.parseInt(s);
			return false;
		} catch (NumberFormatException e) {
			return true;
		}
	}
	
	private static boolean isAngka(String s) {
		try {
			Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return false;
		}
		return true;
	}
	
	private static int angkain(String s) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return 0;
		}
	}
	
	private boolean parse0(String alamat) {
		alamat = alamat.trim();
		
		if (alamat.length() == 0) {
			return false;
		}
		
		Log.d(TAG, "peloncat tahap 0: " + alamat); //$NON-NLS-1$
		
		//# TAHAP 4: replace en-dash and em-dash to normal dash
		if (alamat.contains("\u2013") || alamat.contains("\u2014")) {
			alamat = alamat.replaceAll("[\u2013\u2014]", "-");

			Log.d(TAG, "peloncat tahap 4: " + alamat); //$NON-NLS-1$
		}
		
		//# TAHAP 5: Buang spasi di sebelah kiri-kanan tanda "-"
		if (alamat.contains("-")) { //$NON-NLS-1$
			alamat = alamat.replaceAll("\\s+-\\s+|\\s+-|-\\s+", "-"); //$NON-NLS-1$ //$NON-NLS-2$
			
			Log.d(TAG, "peloncat tahap 5: " + alamat); //$NON-NLS-1$
		}
		
		//# TAHAP 7: Check whether this is in strict osis ID format.
		// This can be BookName.Chapter.Verse or BookName.Chapter
		// Or, either of the above separated by a '-'
		notosis: {
			if (alamat.indexOf('.') < 0) {
				break notosis; // must contain a dot
			}
			
			String osisId;
			if (alamat.indexOf('-') >= 0) { // optionally a '-'
				String[] osisIds = alamat.split("-"); //$NON-NLS-1$
				if (osisIds.length != 2) { 
					break notosis; // wrong format
				}
				osisId = osisIds[0];
			} else {
				osisId = alamat;
			}
			
			Pattern p = OsisBookNames.getBookNameWithChapterAndOptionalVersePattern();
			Matcher m = p.matcher(osisId);
			if (m.matches()) {
				Log.d(TAG, "peloncat tahap 7: ref matching osis pattern found: " + osisId); //$NON-NLS-1$
				String osisBookName = m.group(1);
				String chapter_s = m.group(2);
				String verse_s = m.group(3);
				
				try {
					p_bookIdFromOsis = OsisBookNames.osisBookNameToBookId(osisBookName);
					p_chapter = Integer.parseInt(chapter_s);
					p_verse = (verse_s == null || verse_s.length() == 0)? 0: Integer.parseInt(verse_s);
				} catch (Exception e) {
					Log.e(TAG, "Should not happen. In peloncat tahap 7", e); //$NON-NLS-1$
				}
				
				Log.d(TAG, "peloncat tahap 7: successfully parsed osis id: " + p_bookIdFromOsis + ' ' + p_chapter + ' ' + p_verse); //$NON-NLS-1$
				return true;
			}
		}
		
		//# TAHAP 10: BELAH BERDASAR SPASI, :, TITIK, dan kosong di antara - dan angka.
		//# Contoh output salah: [Kisah, rasul34, 6-7, 8]
		//# Contoh output betul: [Kisah, rasul34, 6, -, 7, 8]
		String[] parts = alamat.split("((\\s|:|\\.)+|(?=[0-9])(?<=-)|(?=-)(?<=[0-9]))"); //$NON-NLS-1$
		Log.d(TAG, "peloncat tahap 10: " + Arrays.toString(parts)); //$NON-NLS-1$

		//# TAHAP 12: buang string dari bagian yang kosong
		{
			int adaKosong = 0;
			for (String b: parts) {
				if (b.length() == 0) {
					adaKosong++;
					break;
				}
			}
			if (adaKosong > 0) {
				String[] partsWithoutEmpties = new String[parts.length - adaKosong];
				int c = 0;
				for (String b: parts) {
					if (b.length() != 0) {
						partsWithoutEmpties[c++] = b;
					}
				}
				parts = partsWithoutEmpties;
			}
		}
		Log.d(TAG, "peloncat tahap 12: " + Arrays.toString(parts)); //$NON-NLS-1$
		
		if (parts.length == 0) {
			return false;
		}
		
		//# TAHAP 20: lebarin dulu kasus semacam Yoh3 jadi Yoh 3
		//# Contoh output: [Kisah, rasul, 34, 6, -, 7, 8]
		{
			ArrayList<String> bel = new ArrayList<String>();
			
			for (String b: parts) {
				if (isKata(b)) {
					String angka = ""; //$NON-NLS-1$
					for (int i = b.length() - 1; i >= 0; i--) {
						char c = b.charAt(i);
						if (c >= '0' && c <= '9') {
							// angka ketemu
							angka = c + angka;
						} else {
							break;
						}
					}
					
					if (angka.length() > 0) { // ada angka ketemu di belakang kata
						bel.add(b.substring(0, b.length() - angka.length()));
						bel.add(angka);
					} else {
						bel.add(b);
					}
				} else {
					bel.add(b);
				}
			}
			
			parts = bel.toArray(parts);
		}
		Log.d(TAG, "peloncat tahap 20: " + Arrays.toString(parts)); //$NON-NLS-1$
		

		//# TAHAP 25: cari elemen bagian yang "-", lalu buang mulai itu sampe belakang.
		{
			boolean adaStrip = false;
			int di = -1;
			
			for (int i = 0; i < parts.length; i++) {
				if ("-".equals(parts[i]) || "--".equals(parts[i])) { //$NON-NLS-1$
					adaStrip = true;
					di = i;
					break;
				}
			}
			
			if (adaStrip) {
				String[] bel = new String[di];
				System.arraycopy(parts, 0, bel, 0, di);
				parts = bel;
				
				Log.d(TAG, "peloncat tahap 25: " + Arrays.toString(parts)); //$NON-NLS-1$
			}
		}
		
		//# TAHAP 30: ubah semacam "3" "yohanes" jadi "3 yohanes"
		{
			ArrayList<String> bel = new ArrayList<String>(); 
			
			int mulaiKata = 0;
			
			// liat dari kanan mana yang bukan angka, itu mulainya kitab
			for (int i = parts.length - 1; i >= 0; i--) {
				if (! isAngka(parts[i])) {
					// ini dan depannya semua adalah kitab
					mulaiKata = i;
					
					break;
				}
				
				if (i == 0 && parts.length > 2) {
					// kebanyakan, masa lebih dari 2 bilangan
					return false;
				}
			}

			String s = null;
			for (int j = 0; j <= mulaiKata; j++) {
				s = (s == null)? parts[j]: s + " " + parts[j]; //$NON-NLS-1$
			}
			
			bel.add(s);
			for (int j = mulaiKata+1; j < parts.length; j++) {
				bel.add(parts[j]);
			}

			parts = bel.toArray(new String[0]);
		}
		Log.d(TAG, "peloncat tahap 30: " + Arrays.toString(parts)); //$NON-NLS-1$
		
		if (parts.length == 1) { // 1 part only
			// It means it can be CHAPTER or BOOK only
			if (isKata(parts[0])) {
				// kitab
				p_kitab = parts[0];
				return true;
			} else {
				p_chapter = angkain(parts[0]);
				return true;
			}
		}

		if (parts.length == 2) { // 2 parts
			// means it could be CHAPTER VERSE (in the same book)
			if (isAngka(parts[0]) && isAngka(parts[1])) {
				p_chapter = angkain(parts[0]);
				p_verse = angkain(parts[1]);
				return true;
			}
			// or BOOK CHAPTER
			else if (isAngka(parts[1])) {
				p_kitab = parts[0];
				p_chapter = angkain(parts[1]);
				return true;
			}
			return false;
		}
		
		if (parts.length == 3) { // 3 parts
			// it means it must be BOOK CHAPTER VERSE. Could not be otherwise.
			p_kitab = parts[0];
			p_chapter = angkain(parts[1]);
			p_verse = angkain(parts[2]);
			return true;
		}
		
		return false;
	}
	
	private boolean parse(String alamat) {
		boolean res = parse0(alamat);
		
		Log.d(TAG, "peloncat sesudah parse0: p_kitab=" + p_kitab); //$NON-NLS-1$
		Log.d(TAG, "peloncat sesudah parse0: p_chapter=" + p_chapter); //$NON-NLS-1$
		Log.d(TAG, "peloncat sesudah parse0: p_verse=" + p_verse); //$NON-NLS-1$
		
		return res;
	}
	
	private List<KitabRef> createBookCandidates(String[] bookNames, int[] bookIds) {
		// bikin cache semua judul kitab yang dibuang spasinya dan dikecilin semua dan 1 jadi I, 2 jadi II, dst
		final List<KitabRef> res = new ArrayList<KitabRef>();
		
		for (int i = 0, len = bookNames.length; i < len; i++) {
			String judul = bookNames[i].replaceAll("(\\s|-|_)+", "").toLowerCase(Locale.getDefault()); //$NON-NLS-1$ //$NON-NLS-2$

			{
				KitabRef ref = new KitabRef();
				ref.pendek = judul;
				ref.pos = bookIds[i];
					
				res.add(ref);
			}
			
			if (judul.contains("1") || judul.contains("2") || judul.contains("3")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				judul = judul.replace("1", "i").replace("2", "ii").replace("3", "iii");    //$NON-NLS-1$//$NON-NLS-2$//$NON-NLS-3$//$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$
				
				KitabRef ref = new KitabRef();
				ref.pendek = judul;
				ref.pos = bookIds[i];
				
				res.add(ref);
			}
		}
		
		return res;
	}
	
	private int tebakKitab(List<KitabRef> refs) {
		if (p_kitab == null) {
			return -1;
		}
		
		int res = -1;
		
		// 0. bersihin p_kitab
		p_kitab = p_kitab.replaceAll("(\\s|-|_)", "").toLowerCase(Locale.getDefault()); //$NON-NLS-1$ //$NON-NLS-2$
		Log.d(TAG, "tebakKitab fase 0: p_kitab = " + p_kitab); //$NON-NLS-1$
		
		// 1. coba cocokin keseluruhan (co: "kejadian", "yohanes")
		for (Jumper.KitabRef ref: refs) {
			if (ref.pendek.equals(p_kitab)) {
				Log.d(TAG, "tebakKitab fase 1 sukses: " + p_kitab); //$NON-NLS-1$
				return ref.pos;
			}
		}
		
		// 2. coba cocokin depannya, kalo ada 1 doang yang lulus, sukses
		int pos_buatNanti = -1;
		{
			int lulus = 0;
			for (Jumper.KitabRef ref: refs) {
				if (ref.pendek.startsWith(p_kitab)) {
					lulus++;
					if (lulus == 1) pos_buatNanti = ref.pos;
				}
			}
			
			if (lulus == 1) {
				Log.d(TAG, "tebakKitab fase 2 sukses: " + pos_buatNanti + " untuk " + p_kitab); //$NON-NLS-1$ //$NON-NLS-2$
				return pos_buatNanti;
			} else {
				Log.d(TAG, "tebakKitab fase 2: lulus = " + lulus); //$NON-NLS-1$
			}
		}
		
		// 3. String matching hanya kalo p_kitab 2 huruf ato lebih
		if (p_kitab.length() >= 2) {
			int minSkor = 99999999;
			int pos = -1;
			
			for (Jumper.KitabRef ref: refs) {
				int skor = Levenshtein.distance(p_kitab, ref.pendek);
				if (p_kitab.charAt(0) != ref.pendek.charAt(0)) {
					skor += 150; // kira2 1.5 insertion
				}
				
				Log.d(TAG, "tebakKitab fase 3: dengan " + ref.pendek + ":" + ref.pos + " skor " + skor); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				
				if (skor < minSkor) {
					minSkor = skor;
					pos = ref.pos;
				}
			}
			
			if (pos != -1) {
				Log.d(TAG, "tebakKitab fase 3 sukses: " + pos + " dengan skor " + minSkor);  //$NON-NLS-1$//$NON-NLS-2$
				return pos;
			}
		}
		
		// 7. Keluarin yang pertama cocok kalo ada lebih dari 1 yang lulus fase 2
		if (pos_buatNanti != -1) {
			Log.d(TAG, "tebakKitab fase 7 sukses: " + pos_buatNanti + " untuk " + p_kitab); //$NON-NLS-1$ //$NON-NLS-2$
			return pos_buatNanti;
		}
		
		return res;
	}
	
	/**
	 * @return whether the parsing succeeded
	 */
	public boolean getParseSucceeded() {
		return parseSucceeded;
	}
	
	/**
	 * @param books list of books from which the looked for book is searched
	 * @return bookId of one of the books (or -1).
	 */
	public int getBookId(Book[] books) {
		if (p_bookIdFromOsis != -1) return p_bookIdFromOsis;
		
		List<KitabRef> refs = pendekCache.get(books);
		if (refs == null) {
			String[] bookNames = new String[books.length];
			int[] bookIds = new int[books.length];
			
			for (int i = 0; i < books.length; i++) {
				bookNames[i] = books[i].shortName;
				bookIds[i] = books[i].bookId;
			}
			
			refs = createBookCandidates(bookNames, bookIds);
			pendekCache.put(books, refs);
			Log.d(TAG, "entri pendekCache baru: " + refs); //$NON-NLS-1$
		}
		
		return tebakKitab(refs);
	}
	
	/**
	 * @param books list of (bookName, bookId) from which the looked for book is searched
	 * @return bookId of one of the books (or -1).
	 */
	public int getBookId(String[] bookNames, int[] bookIds) {
		if (p_bookIdFromOsis != -1) return p_bookIdFromOsis;
		
		return tebakKitab(createBookCandidates(bookNames, bookIds));
	}
	
	public int getChapter() {
		return p_chapter;
	}
	
	public int getVerse() {
		return p_verse;
	}
}
