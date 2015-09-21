package yuku.alkitab.test;

import junit.framework.TestCase;
import yuku.alkitab.base.util.Jumper;
import yuku.alkitab.model.Book;

public class JumperTest extends TestCase {
	public static final String TAG = JumperTest.class.getSimpleName();
	static Book[] books;

	static {
		String[] ss = {
				"Kejadian", // 0
				"Keluaran",
				"Imamat",
				"Bilangan",
				"Ulangan",
				"Yosua",
				"Hakim-hakim",
				"Rut",
				"1 Samuel",
				"2 Samuel",
				"1 Raja-raja", // 10
				"2 Raja-raja",
				"1 Tawarikh",
				"2 Tawarikh",
				"Ezra",
				"Nehemia",
				"Ester",
				"Ayub",
				"Mazmur",
				"Amsal",
				"Pengkhotbah", // 20
				"Kidung Agung",
				"Yesaya",
				"Yeremia",
				"Ratapan",
				"Yehezkiel",
				"Daniel",
				"Hosea",
				"Yoel",
				"Amos",
				"Obaja", // 30
				"Yunus",
				"Mikha",
				"Nahum",
				"Habakuk",
				"Zefanya",
				"Hagai",
				"Zakharia",
				"Maleakhi",
				"Matius",
				"Markus", // 40
				"Lukas",
				"Yohanes",
				"Kisah Para Rasul", // 43
				"Roma",
				"1 Korintus",
				"2 Korintus",
				"Galatia",
				"Efesus",
				"Filipi",
				"Kolose", // 50
				"1 Tesalonika",
				"2 Tesalonika",
				"1 Timotius",
				"2 Timotius",
				"Titus",
				"Filemon",
				"Ibrani",
				"Yakobus",
				"1 Petrus",
				"2 Petrus", // 60
				"1 Yohanes",
				"2 Yohanes",
				"3 Yohanes",
				"Yudas",
				"Wahyu", // 65
		};

		books = new Book[ss.length];
		for (int i = 0; i < ss.length; i++) {
			Book k = new Book();
			k.shortName = ss[i];
			k.bookId = i;
			books[i] = k;
		}
	}

	void testParse(String reference, boolean expectOk, int expectBookId, int expectChapter_1, int expectVerse_1, final boolean expectHasRange) {
		long startTime = System.currentTimeMillis();

		//noinspection Convert2Lambda,Anonymous2MethodRef
		final Jumper.Logger logger = new Jumper.Logger() {
			@Override
			public void d(final String msg) {
				System.out.println(msg);
			}
		};

		try {
			final Jumper jumper = new Jumper(reference, logger);
			
			boolean ok = jumper.getParseSucceeded();
			if (ok) {
				int bookId = jumper.getBookId(books);
				assertEquals(expectBookId, bookId);

				int chapter_1 = jumper.getChapter();
				assertEquals(expectChapter_1, chapter_1);

				int verse_1 = jumper.getVerse();
				assertEquals(expectVerse_1, verse_1);

				boolean hasRange = jumper.getHasRange();
				assertEquals(expectHasRange, hasRange);

				logger.d(reference + " -> " + expectBookId + " " + expectChapter_1 + " " + expectVerse_1);
			} else {
				assertEquals(expectOk, ok);

				logger.d(reference + " -> false");
			}
		} finally {
			logger.d("need " + (System.currentTimeMillis() - startTime) + " ms");
		}
	}

	public void testParse1() throws Throwable {
		testParse("Kej 1:1", true, 0, 1, 1, false);
		testParse("K 1:1", true, 0, 1, 1, false);
		testParse("K 1 1", true, 0, 1, 1, false);
		testParse("K 1  1", true, 0, 1, 1, false);
		testParse("Kej1 1", true, 0, 1, 1, false);
		testParse("K1.1", true, 0, 1, 1, false);
		testParse("Keluaran 1:1", true, 1, 1, 1, false);
		testParse("Kel 1:1", true, 1, 1, 1, false);
		testParse("Klauran 1:1", true, 1, 1, 1, false);
		testParse("Kej 13", true, 0, 13, 0, false);
		testParse("Kej13", true, 0, 13, 0, false);
		testParse("Kej 1 3", true, 0, 1, 3, false);
		testParse("Kej1 34", true, 0, 1, 34, false);
		testParse("Kej", true, 0, 0, 0, false);
		testParse("Kel", true, 1, 0, 0, false);
		testParse("Kid", true, 21, 0, 0, false);

		testParse("Kej 4 5 1 3", false, 0, 0, 0, false);
		testParse("Kej 4 5 1", false, 0, 0, 0, false);

		testParse("Kisah", true, 43, 0, 0, false);
		testParse("Kisah 4", true, 43, 4, 0, false);
		testParse("Kisah 4 999", true, 43, 4, 999, false);
		testParse("Kisah para", true, 43, 0, 0, false);
		testParse("Kisah para 4", true, 43, 4, 0, false);
		testParse("Kisah para 4 999", true, 43, 4, 999, false);
		testParse("Kisah rasul", true, 43, 0, 0, false);
		testParse("Kisah rasul 4", true, 43, 4, 0, false);
		testParse("Kisah rasul 4 999", true, 43, 4, 999, false);

		// number at front
		testParse("1yoh", true, 61, 0, 0, false);
		testParse("2yo", true, 62, 0, 0, false);
		testParse("3y", true, 63, 0, 0, false);
		testParse("1yoh 4 5", true, 61, 4, 5, false);
		testParse("2yo 4 5", true, 62, 4, 5, false);
		testParse("3y 4 5", true, 63, 4, 5, false);
		testParse("1 yoh 4 5", true, 61, 4, 5, false);
		testParse("2 yo 4 5", true, 62, 4, 5, false);
		testParse("3 y 4 5", true, 63, 4, 5, false);
		testParse("1 yoh4 5", true, 61, 4, 5, false);
		testParse("2 yo4 5", true, 62, 4, 5, false);
		testParse("3 y4 5", true, 63, 4, 5, false);
		testParse("1yoh4 5", true, 61, 4, 5, false);
		testParse("2yo4 5", true, 62, 4, 5, false);
		testParse("3y4 5", true, 63, 4, 5, false);
		testParse("1yoh4", true, 61, 4, 0, false);
		testParse("2yo4", true, 62, 4, 0, false);
		testParse("3y4", true, 63, 4, 0, false);

		// tanda strip di nama kitab
		testParse("1raja-raja 4 999", true, 10, 4, 999, false);
		testParse("2 raja-raja 4 999", true, 11, 4, 999, false);
		testParse("kidung-agung 4 999", true, 21, 4, 999, false);
		testParse("3-yoha 4 999", true, 63, 4, 999, false);

		// with verse range!
		testParse("Kejadian 1-2", true, 0, 1, 0, true);
		testParse("Kejadian 1:2-3", true, 0, 1, 2, true);
		testParse("Kejadian 1:2-3:4", true, 0, 1, 2, true);

		testParse("Kejadian 11-22", true, 0, 11, 0, true);
		testParse("Kejadian 11- 22", true, 0, 11, 0, true);
		testParse("Kejadian 11 -22", true, 0, 11, 0, true);
		testParse("Kejadian 11 - 22", true, 0, 11, 0, true);
		testParse("Kejadian 11:22-33:44", true, 0, 11, 22, true);

		// looks like verse range, but it is not actually one
		testParse("Kisah-rasul", true, 43, 0, 0, false);
		testParse("Kisah-rasul-4", true, 43, 4, 0, false);
		testParse("Kisah-rasul-4 999", true, 43, 4, 999, false);
		testParse("Kisah-rasul-4:999", true, 43, 4, 999, false);

		// another verse ranges
		testParse("1yoh 4-5", true, 61, 4, 0, true);
		testParse("2 yo 5:6-7", true, 62, 5, 6, true);
		testParse("3y6:7-8", true, 63, 6, 7, true);

		// verses with a b etc
		testParse("Kejadian 11:13-22", true, 0, 11, 13, true);
		testParse("Kejadian 11:13b-22", true, 0, 11, 13, true);
		testParse("Kejadian 11:13a-22", true, 0, 11, 13, true);
		testParse("Kejadian 11:13-22c", true, 0, 11, 13, true);
		testParse("Kejadian 11:13e-22", true, 0, 11, 13, true);
		testParse("Kejadian 11:13e-22e", true, 0, 11, 13, true);

		// OSIS without and with ranges
		testParse("Gen.1", true, 0, 1, 0, false);
		testParse("John.2", true, 42, 2, 0, false);
		testParse("Rev.11.13", true, 65, 11, 13, false);
		testParse("Gen.11-Gen.12", true, 0, 11, 0, true);
		testParse("Gen.11.12-Gen.22.3", true, 0, 11, 12, true);
		testParse("Rev.11.13-Rev.12", true, 65, 11, 13, true);


		String[] ss = { "Kej", "Kel",
				"Im", "Bil", "Ul", "Yos",
				"Hak", "Rut", "1Sam", "2Sam", "1Raj", "2Raj", "1Taw", "2Taw", "Ezr",
				"Neh", "Est", "Ayub", "Mazm", "Ams", "Pengkh", "Kid",
				"Yes", "Yer", "Rat", "Yeh", "Dan", "Hos", "Yoel", "Am", "Ob", "Yun",
				"Mi", "Nah", "Hab", "Zef", "Hag", "Za", "Mal",
				"Mat", "Mr", "Luk", "Yoh", "Kis", "Rom", "1Kor", "2Kor", "Gal", "Ef", "Fili", "Kol",
				"1Tes", "2Tes", "1Tim", "2Tim", "Tit",
				"Filem", "Ibr", "Yak", "1Pet", "2Pet", "1Yoh", "2Yoh", "3Yoh", "Yud",
				"Wahy",
		};
		
		for (int i = 0; i < ss.length; i++) {
			String s = ss[i];
			
			testParse(s, true, i, 0, 0, false);
		}
	}
}
