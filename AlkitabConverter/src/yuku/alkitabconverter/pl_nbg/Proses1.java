package yuku.alkitabconverter.pl_nbg;

import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.ArrayList;

import yuku.alkitab.yes.YesFile;
import yuku.alkitab.yes.YesFile.InfoEdisi;
import yuku.alkitab.yes.YesFile.InfoKitab;
import yuku.alkitab.yes.YesFile.Teks;
import yuku.alkitabconverter.bdb.BdbProses;
import yuku.alkitabconverter.bdb.BdbProses.Rec;
import yuku.alkitabconverter.util.RecUtil;
import yuku.alkitabconverter.yes_common.YesCommon;

public class Proses1 {
	static String INPUT_TEKS_1 = "./bahan/pl-nbg/in/nbg_text.txt";
	public static String INPUT_TEKS_ENCODING = "utf-8";
	public static int INPUT_TEKS_ENCODING_YES = 2; // 1: ascii; 2: utf-8;
	public static String INPUT_KITAB = "./bahan/pl-nbg/in/nbg_books_name-nonums.txt";
	static String OUTPUT_YES = "./bahan/pl-nbg/out/pl-nbg.yes";
	public static int OUTPUT_ADA_PERIKOP = 0;
	static String INFO_NAMA = "pl-nbg";
	static String INFO_JUDUL = "Nowa Biblia Gdańska";
	static String INFO_KETERANGAN = "Śląskie Towarzystwo Biblijne. Copyrights are not restricted.";

	final Charset utf8 = Charset.forName("utf-8");
	
	public static void main(String[] args) throws Exception {
		new Proses1().u();
	}

	private void u() throws Exception {
		ArrayList<Rec> xrec = new BdbProses().parse(INPUT_TEKS_1, "utf-8");
		
		System.out.println("Total verses: " + xrec.size());

		////////// PROSES KE YES
		final InfoEdisi infoEdisi = YesCommon.infoEdisi(INFO_NAMA, INFO_JUDUL, RecUtil.hitungKitab(xrec), OUTPUT_ADA_PERIKOP, INFO_KETERANGAN, INPUT_TEKS_ENCODING_YES);
		final InfoKitab infoKitab = YesCommon.infoKitab(xrec, INPUT_KITAB, INPUT_TEKS_ENCODING, INPUT_TEKS_ENCODING_YES);
		final Teks teks = YesCommon.teks(xrec, INPUT_TEKS_ENCODING);
		
		YesFile file = YesCommon.bikinYesFile(infoEdisi, infoKitab, teks);
		
		file.output(new RandomAccessFile(OUTPUT_YES, "rw"));
	}
}
