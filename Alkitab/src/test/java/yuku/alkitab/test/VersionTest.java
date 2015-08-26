package yuku.alkitab.test;

import junit.framework.TestCase;
import yuku.alkitab.model.Book;
import yuku.alkitab.model.FootnoteEntry;
import yuku.alkitab.model.PericopeBlock;
import yuku.alkitab.model.SingleChapterVerses;
import yuku.alkitab.model.Version;
import yuku.alkitab.model.XrefEntry;
import yuku.alkitab.util.IntArrayList;

import java.util.List;

public class VersionTest extends TestCase {
	Version fakeVersion() {
		return new Version() {
			@Override
			public String getShortName() {
				return null;
			}

			@Override
			public String getLongName() {
				return null;
			}

			@Override
			public String getLocale() {
				return null;
			}

			@Override
			public int getMaxBookIdPlusOne() {
				return 0;
			}

			@Override
			public Book[] getConsecutiveBooks() {
				return new Book[0];
			}

			@Override
			public Book getBook(final int bookId) {
				final Book res = new Book();
				if (bookId == 0) {
					res.shortName = "Genesis";
					res.abbreviation = "Gen";
					res.chapter_count = 2;
					res.verse_counts = new int[]{31, 25};
				} else if (bookId == 1) {
					res.shortName = "Exodus";
					res.abbreviation = "Ex";
					res.chapter_count = 2;
					res.verse_counts = new int[]{22, 25};
				}
				return res;
			}

			@Override
			public Book getFirstBook() {
				return null;
			}

			@Override
			public String loadVerseText(final int ari) {
				return null;
			}

			@Override
			public String loadVerseText(final Book book, final int chapter_1, final int verse_1) {
				return null;
			}

			@Override
			public int loadVersesByAriRanges(final IntArrayList ariRanges, final IntArrayList result_aris, final List<String> result_verses) {
				return 0;
			}

			@Override
			public int loadPericope(final int bookId, final int chapter_1, final int[] aris, final PericopeBlock[] pericopeBlocks, final int max) {
				return 0;
			}

			@Override
			public SingleChapterVerses loadChapterText(final Book book, final int chapter_1) {
				return null;
			}

			@Override
			public SingleChapterVerses loadChapterTextLowercased(final Book book, final int chapter_1) {
				return null;
			}

			@Override
			public String loadChapterTextLowercasedWithoutSplit(final Book book, final int chapter_1) {
				return null;
			}

			@Override
			public XrefEntry getXrefEntry(final int arif) {
				return null;
			}

			@Override
			public FootnoteEntry getFootnoteEntry(final int arif) {
				return null;
			}
		};
	}

	private void testReferenceRange0(final int ari_start, final int ari_end, final String result) throws Throwable {
		final Version version = fakeVersion();
		assertEquals(result, version.referenceRange(ari_start, ari_end));
	}

	public void testReferenceRange1() throws Throwable {
		testReferenceRange0(0x000101, 0x000101, "Genesis 1:1");
		testReferenceRange0(0x000101, 0x000103, "Genesis 1:1–3");
		testReferenceRange0(0x000101, 0x000201, "Genesis 1:1–2:1");
		testReferenceRange0(0x000101, 0x000203, "Genesis 1:1–2:3");

		testReferenceRange0(0x000100, 0x000100, "Genesis 1");
		testReferenceRange0(0x000100, 0x000200, "Genesis 1–2");

		// find out last verse by itself
		testReferenceRange0(0x000101, 0x000100, "Genesis 1:1–31");
		testReferenceRange0(0x000201, 0x000200, "Genesis 2:1–25");
		testReferenceRange0(0x000103, 0x000100, "Genesis 1:3–31");
		testReferenceRange0(0x000203, 0x000200, "Genesis 2:3–25");
		testReferenceRange0(0x000103, 0x000200, "Genesis 1:3–2:25");
		testReferenceRange0(0x000903, 0x000900, "Genesis 9:3–end");

		testReferenceRange0(0x010103, 0x010100, "Exodus 1:3–22");
		testReferenceRange0(0x010203, 0x010200, "Exodus 2:3–25");
		testReferenceRange0(0x010103, 0x010200, "Exodus 1:3–2:25");
		testReferenceRange0(0x010903, 0x010900, "Exodus 9:3–end");

		// first verse implied
		testReferenceRange0(0x000100, 0x000108, "Genesis 1:1–8");
		testReferenceRange0(0x000200, 0x000208, "Genesis 2:1–8");
		testReferenceRange0(0x000100, 0x000208, "Genesis 1:1–2:8");
		testReferenceRange0(0x000200, 0x000908, "Genesis 2:1–9:8");
		testReferenceRange0(0x000200, 0x000900, "Genesis 2–9");
		testReferenceRange0(0x000203, 0x000900, "Genesis 2:3–9:end");

		// different book
		testReferenceRange0(0x000100, 0x010100, "Genesis 1–Exodus 1");
		testReferenceRange0(0x000100, 0x010107, "Genesis 1:1–Exodus 1:7");
		testReferenceRange0(0x000107, 0x010100, "Genesis 1:7–Exodus 1:22");
		testReferenceRange0(0x000107, 0x010107, "Genesis 1:7–Exodus 1:7");
	}
}
