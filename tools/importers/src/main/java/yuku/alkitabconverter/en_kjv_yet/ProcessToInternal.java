package yuku.alkitabconverter.en_kjv_yet;

import yuku.alkitabconverter.internal_common.InternalCommon;
import yuku.alkitabconverter.util.TextDb;
import yuku.alkitabconverter.yet.YetFileInput;

import java.io.File;

public class ProcessToInternal {
	
	static String INPUT_YET = "./bahan-alkitab/en-kjv-fusion-yet/in/en-kjv.yet";
	static String OUTPUT_INTERNAL = "./bahan-alkitab/en-kjv-fusion-yet/raw";

	public static void main(String[] args) throws Exception {
		////////// READ YET FILE

		final YetFileInput.YetFileInputResult yet = new YetFileInput().parse(INPUT_YET);

		////////// PROSES KE INTERNAL

		{
			File outDir = new File(OUTPUT_INTERNAL);
			outDir.mkdir();
			InternalCommon.createInternalFiles(outDir, "kjv", yet.getBookNamesAsList(), yet.recs, yet.pericopeData);
		}
	}
}
