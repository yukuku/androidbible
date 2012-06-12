package yuku.alkitabconverter.sq_kk;

import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.List;

import yuku.alkitab.yes.YesFile;
import yuku.alkitab.yes.YesFile.InfoEdisi;
import yuku.alkitab.yes.YesFile.InfoKitab;
import yuku.alkitab.yes.YesFile.Teks;
import yuku.alkitabconverter.bdb.BdbProses.Rec;
import yuku.alkitabconverter.unboundbible.UnboundBibleReader;
import yuku.alkitabconverter.util.RecUtil;
import yuku.alkitabconverter.yes_common.YesCommon;

public class Proses1 {
	public static final String TAG = Proses1.class.getSimpleName();
	
	static String INPUT_TEKS_1 = "./bahan/sq-kk/in/albanian_utf8.txt";
	static String INPUT_TEKS_ENCODING = "utf-8";
	static int INPUT_TEKS_ENCODING_YES = 2; // 1: ascii; 2: utf-8;
	static String INPUT_KITAB = "./bahan/sq-kk/in/sq-kk-booknames.txt";
	static String OUTPUT_YES = "./bahan/sq-kk/out/sq-kk.yes";
	static int OUTPUT_ADA_PERIKOP = 0;
	static String INFO_NAMA = "sq-kk";
	static String INFO_JUDUL = "Albanian Bible";
	static String INFO_KETERANGAN = "";

	final static Charset utf8 = Charset.forName("utf-8");
	
	public static void main(String[] args) throws Exception {
		new Proses1().u();
	}

	private void u() throws Exception {
		List<Rec> xrec = UnboundBibleReader.parse(INPUT_TEKS_1, 0, 1, 2, 3);
		
		// post-process
		for (Rec rec: xrec) {
			rec.isi = rec.isi.replace("''", "\"");
		}

		////////// PROSES KE YES

		final InfoEdisi infoEdisi = YesCommon.infoEdisi(INFO_NAMA, INFO_JUDUL, RecUtil.hitungKitab(xrec), OUTPUT_ADA_PERIKOP, INFO_KETERANGAN, INPUT_TEKS_ENCODING_YES);
		final InfoKitab infoKitab = YesCommon.infoKitab(xrec, INPUT_KITAB, INPUT_TEKS_ENCODING, INPUT_TEKS_ENCODING_YES);
		final Teks teks = YesCommon.teks(xrec, INPUT_TEKS_ENCODING);
		//final PerikopBlok perikopBlok = new PerikopBlok(perikopData);
		//final PerikopIndex perikopIndex = new PerikopIndex(perikopData);
		
		YesFile file = YesCommon.bikinYesFile(infoEdisi, infoKitab, teks); //, perikopBlok, perikopIndex);
		
		file.output(new RandomAccessFile(OUTPUT_YES, "rw"));
	}
}
