package yuku.alkitabconverter.el_whubs4;

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
	
	static String INPUT_TEKS_1 = "./bahan/el-whubs4/in/greek_WH_UBS4_utf8.txt";
	static String INPUT_TEKS_ENCODING = "utf-8";
	static int INPUT_TEKS_ENCODING_YES = 2; // 1: ascii; 2: utf-8;
	static String INPUT_KITAB = "./bahan/el-whubs4/in/el-whubs4-kitab.txt";
	static String OUTPUT_YES = "./bahan/el-whubs4/out/el-whubs4.yes";
	static int OUTPUT_ADA_PERIKOP = 0;
	static String INFO_NAMA = "el-whubs4";
	static String INFO_JUDUL = "Greek NT: Westcott/Hort, UBS4 variants";
	static String INFO_KETERANGAN = "The Westcott-Hort edition of 1881. Readings of Nestle27/UBS4 shown.\n" + 
			"\n" + 
			"{VAR1: Refers to Westcott-Hort} \n" + 
			"{VAR2: Refers to Nestle-Aland 27th/UBS4}";

	final static Charset utf8 = Charset.forName("utf-8");
	
	public static void main(String[] args) throws Exception {
		new Proses1().u();
	}

	private void u() throws Exception {
		List<Rec> xrec = UnboundBibleReader.parse(INPUT_TEKS_1, 0, 1, 2, 5);
		
		// post-process
		//for (Rec rec: xrec) {
		//}

		////////// PROSES KE YES

		final InfoEdisi infoEdisi = YesCommon.infoEdisi(INFO_NAMA, null, INFO_JUDUL, RecUtil.hitungKitab(xrec), OUTPUT_ADA_PERIKOP, INFO_KETERANGAN, INPUT_TEKS_ENCODING_YES);
		final InfoKitab infoKitab = YesCommon.infoKitab(xrec, INPUT_KITAB, INPUT_TEKS_ENCODING, INPUT_TEKS_ENCODING_YES);
		final Teks teks = YesCommon.teks(xrec, INPUT_TEKS_ENCODING);
		//final PerikopBlok perikopBlok = new PerikopBlok(perikopData);
		//final PerikopIndex perikopIndex = new PerikopIndex(perikopData);
		
		YesFile file = YesCommon.bikinYesFile(infoEdisi, infoKitab, teks); //, perikopBlok, perikopIndex);
		
		file.output(new RandomAccessFile(OUTPUT_YES, "rw"));
	}
}
