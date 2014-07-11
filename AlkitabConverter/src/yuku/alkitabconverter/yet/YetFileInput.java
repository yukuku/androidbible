package yuku.alkitabconverter.yet;

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

public class YetFileInput {
	public static class YetFileInputResult {
		public List<Rec> recs;
		public PericopeData pericopeData;
		public Map<String, String> infos;
		public int numberOfBooks;
		public Map<Integer, String> bookNames; // key is book_1
		public Map<Integer, String> bookAbbreviations; // key is book_1
		public LinkedHashMap<Integer, XrefEntry> xrefEntries;
		public LinkedHashMap<Integer, FootnoteEntry> footnoteEntries;

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

					// check verse ordering
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
			
		for (Entry<Integer, Integer> e: nversePerBook.entrySet()) {
			System.err.println("book_1 " + e.getKey() + ": " + e.getValue() + " verses");
		}
		res.setNumberOfBooks(nversePerBook.size());
		
		return res;
	}
}
