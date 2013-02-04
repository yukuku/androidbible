package yuku.alkitabconverter.af_1953;

import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.List;

import yuku.alkitab.yes1.Yes1File;
import yuku.alkitab.yes1.Yes1File.InfoEdisi;
import yuku.alkitab.yes1.Yes1File.InfoKitab;
import yuku.alkitab.yes1.Yes1File.Teks;
import yuku.alkitabconverter.unboundbible.UnboundBibleReader;
import yuku.alkitabconverter.util.Rec;
import yuku.alkitabconverter.util.RecUtil;
import yuku.alkitabconverter.yes_common.YesCommon;

public class Proses1 {
	static String BASE_VERSION_NAME = "af-1953";
	
	static String INPUT_TEKS_1 = "./bahan/" + BASE_VERSION_NAME + "/in/afrikaans_1953_utf8_edited.txt";
	public static String INPUT_TEKS_ENCODING = "utf-8";
	public static int INPUT_TEKS_ENCODING_YES = 2; // 1: ascii; 2: utf-8;
	public static String INPUT_KITAB = "./bahan/" + BASE_VERSION_NAME + "/in/book_name_lines.txt";
	static String OUTPUT_YES = "./bahan/" + BASE_VERSION_NAME + "/out/" + BASE_VERSION_NAME + ".yes";
	public static int OUTPUT_ADA_PERIKOP = 0;
	static String INFO_NAMA = BASE_VERSION_NAME;
	static String INFO_SHORT_TITLE = "Afrikaans";
	static String INFO_LONG_TITLE = "Afrikaans Die Bybel";
	static String INFO_KETERANGAN = "Die Bybel (1953)";

	final Charset utf8 = Charset.forName("utf-8");
	
	public static void main(String[] args) throws Exception {
		new Proses1().u();
	}

	private void u() throws Exception {
		List<Rec> xrec = UnboundBibleReader.parse(INPUT_TEKS_1, 0, 1, 2, 5);
		
		System.out.println("Total verses: " + xrec.size());

		////////// PROSES KE YES
		final InfoEdisi infoEdisi = YesCommon.infoEdisi(INFO_NAMA, INFO_SHORT_TITLE, INFO_LONG_TITLE, RecUtil.hitungKitab(xrec), OUTPUT_ADA_PERIKOP, INFO_KETERANGAN, INPUT_TEKS_ENCODING_YES, null);
		final InfoKitab infoKitab = YesCommon.infoKitab(xrec, INPUT_KITAB, INPUT_TEKS_ENCODING, INPUT_TEKS_ENCODING_YES);
		final Teks teks = YesCommon.teks(xrec, INPUT_TEKS_ENCODING);
		
		Yes1File file = YesCommon.bikinYesFile(infoEdisi, infoKitab, teks);
		
		file.output(new RandomAccessFile(OUTPUT_YES, "rw"));
	}
}
