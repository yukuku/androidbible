package yuku.alkitabconverter.en_kjv_thml;

import java.io.File;
import java.util.ArrayList;

import yuku.alkitabconverter.bdb.BdbProses;
import yuku.alkitabconverter.internal_common.InternalCommon;
import yuku.alkitabconverter.internal_common.ReverseIndexer;
import yuku.alkitabconverter.util.Rec;
import yuku.alkitabconverter.util.TeksDb;

public class ProcessToInternal {
	
	static String INPUT_TEKS_1 = "./bahan/en-kjv-thml/in/kjv3_teks_bdb.txt";
	static String INPUT_TEKS_ENCODING = "utf-8";
	static String INPUT_KITAB = "./bahan/en-kjv-thml/in/book_name_lines.txt";

	public static void main(String[] args) throws Exception {
		ArrayList<Rec> recs = new BdbProses().parse(INPUT_TEKS_1, INPUT_TEKS_ENCODING);
		
		TeksDb teksDb = new TeksDb();
		for (Rec rec: recs) {
			teksDb.append(rec.book_1 - 1, rec.chapter_1, rec.verse_1, rec.text, 0);
		}
		
		////////// CREATE REVERSE INDEX
		
		{
			File outDir = new File("./bahan/en-kjv-thml/raw");
			ReverseIndexer.createReverseIndex(outDir, "kjv", teksDb);
		}
		
		////////// PROSES KE INTERNAL
		
		{
			File outDir = new File("./bahan/en-kjv-thml/raw");
			outDir.mkdir();
			InternalCommon.createInternalFiles(outDir, "kjv", InternalCommon.fileToBookNames(INPUT_KITAB), recs, null);
		}
		
	}
}
