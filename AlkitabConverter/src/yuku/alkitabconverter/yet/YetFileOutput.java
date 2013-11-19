package yuku.alkitabconverter.yet;

import yuku.alkitab.model.FootnoteEntry;
import yuku.alkitab.model.XrefEntry;
import yuku.alkitab.util.Ari;
import yuku.alkitab.yes2.model.PericopeData;
import yuku.alkitabconverter.util.FootnoteDb;
import yuku.alkitabconverter.util.Rec;
import yuku.alkitabconverter.util.TextDb;
import yuku.alkitabconverter.util.XrefDb;
import yuku.alkitabconverter.yes_common.Yes2Common;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class YetFileOutput {
	private final OutputStream output;
	private TextDb textDb;
	private PericopeData pericopeData;
	private XrefDb xrefDb;
	private FootnoteDb footnoteDb;
    private Yes2Common.VersionInfo versionInfo;

    public YetFileOutput(File output) throws IOException {
		this.output = new FileOutputStream(output);
	}

	public YetFileOutput(OutputStream output) {
		this.output = output;
	}

	public void setTextDb(final TextDb textDb) {
        this.textDb = textDb;
    }

	public void setPericopeData(final PericopeData pericopeData) {
		this.pericopeData = pericopeData;
	}

	public void setXrefDb(final XrefDb xrefDb) {
		this.xrefDb = xrefDb;
	}

    public void setFootnoteDb(final FootnoteDb footnoteDb) {
        this.footnoteDb = footnoteDb;
    }

    public void setVersionInfo(Yes2Common.VersionInfo versionInfo) {
        this.versionInfo = versionInfo;
    }

	public void write() throws IOException {
		final PrintWriter pw = new PrintWriter(new OutputStreamWriter(output, "utf-8"));

		// info
        final Map<String, String> info = new LinkedHashMap<String, String>();
        if (versionInfo.locale != null) info.put("locale", versionInfo.locale);
        if (versionInfo.shortName != null) info.put("shortName", versionInfo.shortName);
        if (versionInfo.longName != null) info.put("longName", versionInfo.longName);
        if (versionInfo.description != null) info.put("description", versionInfo.description);
        for (Map.Entry<String, String> e : info.entrySet()) {
			pw.printf(Locale.US, "%s\t%s\t%s\n", "info", e.getKey(), e.getValue().replaceAll("[\\r\\n]", " ") /* no newlines allowed */);
		}

        final int[] bookIds = textDb.getBookIds();

        // book names
        for (final int bookId : bookIds) {
            final String bookShortName = versionInfo.getBookShortName(bookId);
            final String bookAbbreviation = versionInfo.getBookAbbreviation(bookId);
            if (bookAbbreviation == null) {
                pw.printf(Locale.US, "%s\t%s\t%s\n", "book_name", bookId + 1, bookShortName);
            } else {
                pw.printf(Locale.US, "%s\t%s\t%s\t%s\n", "book_name", bookId + 1, bookShortName, bookAbbreviation);
            }
        }

		// verses
		for (Rec rec : textDb.toRecList()) {
			pw.printf(Locale.US, "%s\t%s\t%s\t%s\t%s\n", "verse", rec.book_1, rec.chapter_1, rec.verse_1, rec.text);
		}

		// pericope data
		if (pericopeData != null) for (final PericopeData.Entry entry : pericopeData.entries) {
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
}
