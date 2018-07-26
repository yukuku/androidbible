package yuku.alkitabconverter.yet;

import gnu.trove.list.array.TIntArrayList;
import yuku.alkitab.model.FootnoteEntry;
import yuku.alkitab.model.XrefEntry;
import yuku.alkitab.util.Ari;
import yuku.alkitab.yes2.model.PericopeData;
import yuku.alkitabconverter.util.Rec;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YetFileInput {
	public static class YetFileInputResult {
		public List<Rec> recs;
		public PericopeData pericopeData;
		public Map<String, String> infos;
		public int numberOfBooks;
		public Map<Integer, String> bookNames; // key is book_1
		public Map<Integer, String> bookAbbreviations; // key is book_1
		public LinkedHashMap<Integer /* arif */, XrefEntry> xrefEntries;
		public LinkedHashMap<Integer /* arif */, FootnoteEntry> footnoteEntries;

		void addInfo(String k, String v) {
			if (infos == null) infos = new LinkedHashMap<>();
			infos.put(k, v);
		}
		
		void addPericopeEntry(PericopeData.Entry e) {
			if (pericopeData == null) pericopeData = new PericopeData();
			pericopeData.addEntry(e);
		}
		
		void addRec(Rec rec) {
			if (recs == null) recs = new ArrayList<>();
			recs.add(rec);
		}
		
		void addBookName(int book_1, String bookName, String bookAbbreviation) {
			if (bookNames == null) {
				bookNames = new TreeMap<>();
				bookAbbreviations = new TreeMap<>();
			}

			bookNames.put(book_1, bookName);
			bookAbbreviations.put(book_1, bookAbbreviation);
		}

		void setNumberOfBooks(int numberOfBooks) {
			this.numberOfBooks = numberOfBooks;
		}

		public void addXrefEntry(final int arif, final XrefEntry xe) {
			if (xrefEntries == null) {
				xrefEntries = new LinkedHashMap<>();
			}
			xrefEntries.put(arif, xe);
		}

		public void addFootnoteEntry(final int arif, final FootnoteEntry fe) {
			if (footnoteEntries == null) {
				footnoteEntries = new LinkedHashMap<>();
			}
			footnoteEntries.put(arif, fe);
		}

		/**
		 * @return book names indexed from 0
		 */
		public List<String> getBookNamesAsList() {
			final List<String> res = new ArrayList<>();
			for (Map.Entry<Integer, String> e : bookNames.entrySet()) {
				final int index = e.getKey() - 1;
				while (index + 1 > res.size()) {
					res.add(null);
				}
				res.set(index, e.getValue());
			}
			return res;
		}

		public List<String> getBookAbbreviationsAsList() {
			final List<String> res = new ArrayList<>();
			for (Map.Entry<Integer, String> e : bookAbbreviations.entrySet()) {
				final int index = e.getKey() - 1;
				while (index + 1 > res.size()) {
					res.add(null);
				}
				res.set(index, e.getValue());
			}
			return res;
		}
	}


	public YetFileInputResult parse(String nf) throws Exception {
		return parse(nf, true);
	}

	public YetFileInputResult parse(String nf, boolean check_verse_ordering) throws Exception {
		LinkedHashMap<Integer, Integer> nversePerBook = new LinkedHashMap<>();
		
		
		int lastBook_1 = 1;
		int lastChapter_1 = 1;
		int lastVerse_1 = 0;
		PericopeData.Entry lastPericopeEntry = null;
		
		YetFileInputResult res = new YetFileInputResult();
		
		int report_line_number = 0;
		String report_line_text = null;

		try {
			final Scanner sc = new Scanner(new BufferedInputStream(new FileInputStream(nf), 20*1024*1024), "utf-8");
			while (sc.hasNextLine()) {
				String line = sc.nextLine();
				
				report_line_number++;
				report_line_text = line;
				
				String[] splits = line.split("\t", -1);
				String command = splits[0];
				
				if ("info".equals(command)) {
					String k = splits[1];
					String v = splits[2];
					res.addInfo(k, v);
				} else if ("pericope".equals(command)) {
					int book_1 = Integer.parseInt(splits[1]);
					int chapter_1 = Integer.parseInt(splits[2]);
					int verse_1 = Integer.parseInt(splits[3]);
					String text = splits[4];
					
					lastPericopeEntry = new PericopeData.Entry();
					res.addPericopeEntry(lastPericopeEntry);
					
					lastPericopeEntry.ari = ((book_1 - 1) << 16) | (chapter_1 << 8) | verse_1;
					lastPericopeEntry.block = new PericopeData.Block();
					lastPericopeEntry.block.title = text;
				} else if ("parallel".equals(command)) {
					String text = splits[1];
					if (lastPericopeEntry == null) {
						throw new RuntimeException("parallel encountered before pericope title: " + line);
					}
					lastPericopeEntry.block.addParallel(text);
				} else if ("book_name".equals(command)) {
					int book_1 = Integer.parseInt(splits[1]);
					String bookName = splits[2];
					String bookAbbreviation;

					if (splits.length > 3 && splits[3] != null && !splits[3].isEmpty()) {
						bookAbbreviation = splits[3];
					} else {
						bookAbbreviation = null;
					}

					res.addBookName(book_1, bookName, bookAbbreviation);
				} else if ("verse".equals(command)) {
					int book_1 = Integer.parseInt(splits[1]);
					int chapter_1 = Integer.parseInt(splits[2]);
					int verse_1 = Integer.parseInt(splits[3]);
					String text = splits[4];

					if (check_verse_ordering) {
						boolean validOrdering = false;
						if (verse_1 == lastVerse_1 + 1) {
							// next verse
							validOrdering = true;
						} else if (verse_1 == 1 && chapter_1 == lastChapter_1 + 1) {
							// next chapter, verse 1
							validOrdering = true;
						} else if (verse_1 == 1 && chapter_1 == 1) {
							// new book, chapter 1 verse 1
							validOrdering = true;
							if (book_1 != lastBook_1 + 1) {
								// skipped book(s)
								System.err.println("warning: some book(s) skipped. current book_1: " + book_1 + " previous book_1: " + lastBook_1);
							}
						}

						if (!validOrdering) {
							throw new RuntimeException("wrong verse ordering at line: " + line);
						}
					}
					
					Rec rec = new Rec();
					rec.book_1 = book_1;
					rec.chapter_1 = chapter_1;
					rec.verse_1 = verse_1;
					rec.text = text;

					res.addRec(rec);
					nversePerBook.put(book_1, (nversePerBook.get(book_1) == null? 0: nversePerBook.get(book_1)) + 1);
					
					lastBook_1 = book_1;
					lastChapter_1 = chapter_1;
					lastVerse_1 = verse_1;
				} else if ("xref".equals(command)) {
					int book_1 = Integer.parseInt(splits[1]);
					int chapter_1 = Integer.parseInt(splits[2]);
					int verse_1 = Integer.parseInt(splits[3]);
					int field_1 = Integer.parseInt(splits[4]);
					String content = splits[5];

					XrefEntry xe = new XrefEntry();
					xe.content = content;

					res.addXrefEntry((Ari.encode(book_1 - 1, chapter_1, verse_1) << 8) | field_1, xe);

				} else if ("footnote".equals(command)) {
					int book_1 = Integer.parseInt(splits[1]);
					int chapter_1 = Integer.parseInt(splits[2]);
					int verse_1 = Integer.parseInt(splits[3]);
					int field_1 = Integer.parseInt(splits[4]);
					String content = splits[5];

					FootnoteEntry fe = new FootnoteEntry();
					fe.content = content;

					res.addFootnoteEntry((Ari.encode(book_1 - 1, chapter_1, verse_1) << 8) | field_1, fe);

				} else if (command.trim().startsWith("#") || command.trim().length() == 0) {
					// comment or blank line
				} else {
					System.err.println("unknown line encountered: " + line);
					return null;
				}
			}
		} catch (Exception e) {
			System.err.println("Error in line " + report_line_number + ": " + report_line_text);
			throw e;
		}

		{ // verify footnotes and xref entries exist
			TIntArrayList footnoteArifs = new TIntArrayList();
			TIntArrayList xrefArifs = new TIntArrayList();
			List<String> errors = new ArrayList<>();

			for (final Rec rec : res.recs) {
				final int ari = Ari.encode(rec.book_1 - 1, rec.chapter_1, rec.verse_1);
				final String text = rec.text;

				footnoteMatcher.reset(text);
				while (footnoteMatcher.find()) {
					final int field = Integer.parseInt(footnoteMatcher.group(1));
					if (field < 1 || field > 255) {
						throw new RuntimeException("footnote field not in 1-255: " + text);
					}
					footnoteArifs.add(ari << 8 | field);
				}

				xrefMatcher.reset(text);
				while (xrefMatcher.find()) {
					final int field = Integer.parseInt(xrefMatcher.group(1));
					if (field < 1 || field > 255) {
						throw new RuntimeException("xref field not in 1-255: " + text);
					}
					xrefArifs.add(ari << 8 | field);
				}
			}

			for (final int arif : footnoteArifs.toArray()) {
				if (res.footnoteEntries == null || !res.footnoteEntries.containsKey(arif)) {
					final int ari = arif >>> 8;
					errors.add(String.format("footnote referenced in verse text not found: arif 0x%08x (book_1=%d, chapter_1=%d, verse_1=%d, field=%d)", arif, Ari.toBook(ari) + 1, Ari.toChapter(ari), Ari.toVerse(ari), arif & 0xff));
				}
			}

			for (final int arif : xrefArifs.toArray()) {
				if (res.xrefEntries == null || !res.xrefEntries.containsKey(arif)) {
					final int ari = arif >>> 8;
					errors.add(String.format("xref referenced in verse text not found: arif 0x%08x (book_1=%d, chapter_1=%d, verse_1=%d, field=%d)", arif, Ari.toBook(ari) + 1, Ari.toChapter(ari), Ari.toVerse(ari), arif & 0xff));
				}
			}

			if (errors.size() != 0) {
				for (final String error : errors) {
					System.err.println(error);
				}
				throw new RuntimeException("there are footnotes and/or xrefs not resolved");
			}
		}

		for (Entry<Integer, Integer> e: nversePerBook.entrySet()) {
			System.err.println("book_1 " + e.getKey() + ": " + e.getValue() + " verses");
		}
		res.setNumberOfBooks(nversePerBook.size());
		
		return res;
	}
}
