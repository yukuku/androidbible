package yuku.alkitabconverter.yet;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import yuku.alkitabconverter.internal_common.InternalCommon;
import yuku.alkitabconverter.internal_common.ReverseIndexer;
import yuku.alkitabconverter.util.FootnoteDb;
import yuku.alkitabconverter.util.KjvUtils;
import yuku.alkitabconverter.util.Rec;
import yuku.alkitabconverter.util.TextDb;
import yuku.alkitabconverter.util.XrefDb;
import yuku.alkitabconverter.yet.YetFileInput.YetFileInputResult;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class YetToInternal {
	@Parameter private List<String> params = new ArrayList<String>();
	@Parameter(names = "--help", help = true, description = "Show this help") private boolean help = false;
	@Parameter(names = {"--prefix", "-p"}, description = "Set the prefix for internal version") private String prefix = "ddd";

	public static void main(String[] args) throws Exception {
		YetToInternal main = new YetToInternal();
		JCommander jc = new JCommander(main, args);

		if (main.help) {
			jc.setProgramName("java -jar YetToInternal.jar");
			jc.usage();
			System.exit(0);
		}
		
		int retval = main.main();
		System.exit(retval);
	}

	private int main() throws Exception {
		if (params.size() < 1) {
			System.err.println("Usage parameters: <yet-file> [<internal-files-dir>]");
			System.err.println("Use --help to show options");
			return 1;
		}
		
		final String yetfile = params.get(0);
		String internaldir;
		if (params.size() >= 2) {
			internaldir = params.get(1);
		} else {
			internaldir = yetfile.endsWith(".yet") ? yetfile.substring(0, yetfile.length() - 4) : yetfile + ".internal";
		}

		System.err.println("input:  " + yetfile);
		System.err.println("output: " + internaldir);

		{ // make output dir
			final File outputdir = new File(internaldir);
			if (!outputdir.exists()) {
				if (!outputdir.mkdirs()) {
					System.err.println("output dir couldn't be created");
					return 1;
				}
			}
		}

		YetFileInputResult result = new YetFileInput().parse(yetfile);
		if (result == null) {
			// error message given by parse above
			return 1;
		}
		
		if (result.recs == null) {
			System.err.println("yet file doesn't contain any verses");
			return 1;
		} else if (result.infos == null) {
			System.err.println("yet file doesn't contain any info");
			return 1;
		} else if (result.infos.get("longName") == null) {
			System.err.println("yet file doesn't contain info 'longName' which is required");
			return 1;
		} else if (result.bookNames == null) {
			System.err.println("yet file doesn't contain any book names");
			return 1;
		}
		
		{ // check if all book names are available
			Set<Integer> books_1 = new HashSet<Integer>();
			for (Rec rec: result.recs) {
				books_1.add(rec.book_1);
			}
			
			for (Integer book_1: books_1) {
				String bookName = result.bookNames.get(book_1);
				if (bookName == null) {
					System.err.println("yet file doesn't contain book name for book " + book_1);
					return 1;
				}
			}
		}

		// convert recs to textdb
		TextDb textDb = new TextDb();
		for (Rec rec: result.recs) {
			textDb.append(rec.book_1 - 1, rec.chapter_1, rec.verse_1, rec.text, -1);
			if (!KjvUtils.isValidKjv(rec.book_1 - 1, rec.chapter_1, rec.verse_1)) {
				System.err.println("warning: is not a valid verse in KJV versification: verse " + rec.book_1 + " " + rec.chapter_1 + " " + rec.verse_1);
			}
		}

		{ ////////// CREATE REVERSE INDEX
			final File outDir = new File(internaldir);
			ReverseIndexer.createReverseIndex(outDir, prefix, new TextDb(result.recs));
		}

		{ ////////// CONVERT TO INTERNAL
			final File outDir = new File(internaldir);
			final XrefDb xrefDb = result.xrefEntries == null? null: new XrefDb(result.xrefEntries);
			final FootnoteDb footnoteDb = result.footnoteEntries == null? null: new FootnoteDb(result.footnoteEntries);
			InternalCommon.createInternalFiles(outDir, prefix, result.getBookNamesAsList(), result.recs, result.pericopeData, xrefDb, footnoteDb);
		}

		return 0;
	}
}
