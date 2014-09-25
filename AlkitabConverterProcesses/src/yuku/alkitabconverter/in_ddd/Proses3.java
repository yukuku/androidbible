package yuku.alkitabconverter.in_ddd;

import yuku.alkitabconverter.internal_common.InternalCommon;
import yuku.alkitabconverter.internal_common.ReverseIndexer;
import yuku.alkitabconverter.util.FootnoteDb;
import yuku.alkitabconverter.util.TextDb;
import yuku.alkitabconverter.util.XrefDb;
import yuku.alkitabconverter.yet.YetFileInput;

import java.io.File;

// Process from yet file to internal and revindex
public class Proses3 {
	static String INPUT_YET = "../../../bahan-alkitab/in-ddd/in-ddd.yet";
	static String OUTPUT_PREFIX = "ddd";
	static String OUTPUT_INTERNAL = "../../../bahan-alkitab/in-ddd/raw/";

	public static void main(String[] args) throws Exception {
		new Proses3().u();
	}

	public void u() throws Exception {
		final YetFileInput.YetFileInputResult yet = new YetFileInput().parse(INPUT_YET);

		File outDir = new File(OUTPUT_INTERNAL);
		outDir.mkdir();

		////////// CREATE REVERSE INDEX

		{
			ReverseIndexer.createReverseIndex(outDir, OUTPUT_PREFIX, new TextDb(yet.recs));
		}

		////////// CONVERT TO INTERNAL

		{
			XrefDb xrefDb = yet.xrefEntries == null? null: new XrefDb(yet.xrefEntries);
			FootnoteDb footnoteDb = yet.footnoteEntries == null? null: new FootnoteDb(yet.footnoteEntries);
			InternalCommon.createInternalFiles(outDir, OUTPUT_PREFIX, yet.getBookNamesAsList(), yet.recs, yet.pericopeData, xrefDb, footnoteDb);
		}
	}
}

