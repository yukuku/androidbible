package yuku.alkitabconverter.internal_common;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import yuku.alkitab.yes.YesFile.PerikopData;
import yuku.alkitabconverter.bdb.BdbProses.Rec;
import yuku.alkitabconverter.util.TeksDb;
import yuku.bintex.BintexWriter;

public class InternalCommon {
	public static final String TAG = InternalCommon.class.getSimpleName();

	public final static Charset ascii = Charset.forName("ascii");
	public final static Charset utf8 = Charset.forName("utf8");

	/**
	 * @param outDir
	 * @param prefix e.g. "tb"
	 * @param teksDb
	 * @param perikopData
	 */
	public static void createInternalFiles(File outDir, String prefix, List<String> bookNames, TeksDb teksDb, PerikopData perikopData) {
		List<List<Rec>> books = new ArrayList<List<Rec>>();
		
		for (int i = 1; i <= 66; i++) {
			books.add(new ArrayList<Rec>());
		}
		
		for (Rec rec: teksDb.toRecList()) {
			int kitab_1 = rec.kitab_1;
			if (kitab_1 < 1 || kitab_1 > 66) {
				throw new RuntimeException("kitab_1 not supported: " + kitab_1);
			}
			
			books.get(kitab_1 - 1).add(rec);
		}
		
		try {
			BintexWriter bw = new BintexWriter(new FileOutputStream(new File(outDir, String.format("%s_index_bt.bt", prefix))));
			
			for (int kitab_1 = 1; kitab_1 <= 66; kitab_1++) {
				{ // text
					File f = new File(outDir, String.format("%s_k%02d.txt", prefix, kitab_1));
					OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(f), "utf-8");
					List<Rec> recs = books.get(kitab_1 - 1);
					for (Rec rec: recs) {
						out.write(rec.isi);
						out.write('\n');
					}
					out.close();
				}
				{ // index for each book:
					// autostring bookName
					// shortstring resName
					// int chapter_count
					// uint8[] verse_counts
					// int[] chapter_offsets
					
				}
			}
		
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public static List<String> fileToBookNames(String filename) throws FileNotFoundException {
		// parse file nama kitab
		List<String> res = new ArrayList<String>(); // indexnya sama dengan kitabPos
		Scanner sc = new Scanner(new File(filename));
		while (sc.hasNextLine()) {
			String judul = sc.nextLine().trim();
			judul = judul.replace('_', ' ');
			System.out.println("kitabPos " + res.size() + " judul: " + judul);
			res.add(judul);
		}
		sc.close();
		return res;
	}
}

