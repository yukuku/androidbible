package yuku.alkitabconverter.yet;

import yuku.alkitab.base.model.Ari;
import yuku.alkitab.model.FootnoteEntry;
import yuku.alkitab.model.XrefEntry;
import yuku.alkitab.yes2.model.PericopeData;
import yuku.alkitabconverter.util.FootnoteDb;
import yuku.alkitabconverter.util.Rec;
import yuku.alkitabconverter.util.XrefDb;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

public class YetFileOutput {
	private final File output;

	private Map<String, String> info = new LinkedHashMap<String, String>();
	private Map<Integer, String> bookNames_0 = new LinkedHashMap<Integer, String>();
	private List<Rec> verses;
	private PericopeData pericopeData;
	private XrefDb xrefDb;
	private FootnoteDb footnoteDb;

	public YetFileOutput(File output) {
		this.output = output;
	}

	public void setInfo(final String key, final String value) {
		info.put(key, value);
	}

	public void setVerses(final List<Rec> verses) {
		this.verses = verses;
	}

	public void setBooksFromFileLines(final String book_name_file) throws FileNotFoundException {
		final Scanner sc = new Scanner(new File(book_name_file), "utf-8");
		int c = 0;
		while (sc.hasNextLine()) {
			final String line = sc.nextLine().trim();
			if (line.length() > 0) {
				bookNames_0.put(c, line.trim());
			}
			c++;
		}
	}

	public void setPericopeData(final PericopeData pericopeData) {
		this.pericopeData = pericopeData;
	}

	public void setXrefDb(final XrefDb xrefDb) {
		this.xrefDb = xrefDb;
	}

	public void write() throws Exception {
		final PrintWriter pw = new PrintWriter(output, "utf-8");

		// info
		for (Map.Entry<String, String> e : info.entrySet()) {
			pw.printf(Locale.US, "%s\t%s\t%s\n", "info", e.getKey(), e.getValue());
		}

		// book names
		for (Map.Entry<Integer, String> e : bookNames_0.entrySet()) {
			pw.printf(Locale.US, "%s\t%s\t%s\n", "book_name", e.getKey() + 1, e.getValue());
		}

		// verses
		for (Rec rec : verses) {
			pw.printf(Locale.US, "%s\t%s\t%s\t%s\t%s\n", "verse", rec.book_1, rec.chapter_1, rec.verse_1, rec.text);
		}

		// pericope data
		for (final PericopeData.Entry entry : pericopeData.entries) {
			pw.printf(Locale.US, "%s\t%s\t%s\t%s\t%s\n", "pericope", Ari.toBook(entry.ari) + 1, Ari.toChapter(entry.ari), Ari.toVerse(entry.ari), entry.block.title);
			if (entry.block.parallels != null) {
				for (final String parallel : entry.block.parallels) {
					pw.printf(Locale.US, "%s\t%s\n", "parallel", parallel);
				}
			}
		}

		// xref
		if (xrefDb != null) {
			xrefDb.processEach(new XrefDb.XrefProcessor() {
				@Override
				public void process(final XrefEntry xe, final int ari, final int entryIndex) {
					pw.printf(Locale.US, "%s\t%s\t%s\t%s\t%s\t%s\n", "xref", Ari.toBook(ari) + 1, Ari.toChapter(ari), Ari.toVerse(ari), entryIndex + 1, xe.content);
				}
			});
		}

		// footnotes
		if (footnoteDb != null) {
			footnoteDb.processEach(new FootnoteDb.FootnoteProcessor() {
				@Override
				public void process(final FootnoteEntry fe, final int ari, final int entryIndex) {
					pw.printf(Locale.US, "%s\t%s\t%s\t%s\t%s\t%s\n", "footnote", Ari.toBook(ari) + 1, Ari.toChapter(ari), Ari.toVerse(ari), entryIndex + 1, fe.content);
				}
			});
		}

		pw.close();
	}

	public void setFootnoteDb(final FootnoteDb footnoteDb) {
		this.footnoteDb = footnoteDb;
	}
}
