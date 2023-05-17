package yuku.alkitabconverter.in_tb_usfm;

import yuku.alkitabconverter.internal_common.InternalCommon;
import yuku.alkitabconverter.util.FootnoteDb;
import yuku.alkitabconverter.util.XrefDb;
import yuku.alkitabconverter.yet.YetFileInput;

import java.io.File;

// Process from yet file to internal
public class Proses3 {
	static String INPUT_YET = "../../../bahan-alkitab/in-tb/in-tb.yet";
	static String OUTPUT_PREFIX = "tb";
	static String OUTPUT_INTERNAL = "../../../bahan-alkitab/in-tb/raw/";

	public static void main(String[] args) throws Exception {
		if (args.length >= 2) {
			INPUT_YET = args[0];
			OUTPUT_INTERNAL = args[1];
		}

		new Proses3().u();
	}

	public void u() throws Exception {
		final YetFileInput.YetFileInputResult yet = new YetFileInput().parse(INPUT_YET);

		////////// CONVERT TO INTERNAL

		{
			File outDir = new File(OUTPUT_INTERNAL);
			XrefDb xrefDb = yet.xrefEntries == null? null: new XrefDb(yet.xrefEntries);
			FootnoteDb footnoteDb = yet.footnoteEntries == null? null: new FootnoteDb(yet.footnoteEntries);
			InternalCommon.createInternalFiles(outDir, OUTPUT_PREFIX, yet.getBookNamesAsList(), yet.recs, yet.pericopeData, xrefDb, footnoteDb);
		}
	}
}

