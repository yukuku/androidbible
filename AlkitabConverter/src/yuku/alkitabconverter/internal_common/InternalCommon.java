package yuku.alkitabconverter.internal_common;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import yuku.alkitab.yes.YesFile.PerikopData;
import yuku.alkitabconverter.bdb.BdbProses.Rec;
import yuku.alkitabconverter.util.CountingOutputStream;
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
			{
				BintexWriter bw = new BintexWriter(new FileOutputStream(new File(outDir, String.format("%s_index_bt.bt", prefix))));
				
				for (int kitab_1 = 1; kitab_1 <= 66; kitab_1++) {
					List<Rec> recs = books.get(kitab_1 - 1);
					
					int chapter_count = 0;
					for (Rec rec: recs) {
						if (rec.pasal_1 > chapter_count) {
							chapter_count = rec.pasal_1;
						}
					}
	
					int[] verse_counts;
					{ // verse_counts
						verse_counts = new int[chapter_count];
						for (Rec rec: recs) {
							verse_counts[rec.pasal_1 - 1]++;
						}
					}
	
					int[] chapter_offsets = new int[chapter_count + 1];
					{ // text
						File f = new File(outDir, String.format("%s_k%02d.txt", prefix, kitab_1));
						CountingOutputStream counter = new CountingOutputStream(new FileOutputStream(f));
						OutputStreamWriter out = new OutputStreamWriter(counter, "utf-8");
						for (Rec rec: recs) {
							out.write(rec.isi);
							out.write('\n');
							out.flush();
							chapter_offsets[rec.pasal_1] = (int) counter.getCount();
						}
						out.close();
					}
					{ // index for each book:
						// autostring bookName
						// shortstring resName
						// int chapter_count
						// uint8[chapter_count] verse_counts
						// int[chapter_count+1] chapter_offsets
						bw.writeAutoString(bookNames.get(kitab_1 - 1));
						bw.writeShortString(String.format("%s_k%02d", prefix, kitab_1));
						bw.writeInt(chapter_count);
						for (int i = 0; i < chapter_count; i++) {
							bw.writeUint8(verse_counts[i]);
						}
						for (int i = 0; i < chapter_count + 1; i++) {
							bw.writeInt(chapter_offsets[i]);
						}
						
					}
					System.out.println("book: " + bookNames.get(kitab_1 - 1) + " ch_count=" + chapter_count + " v_counts=" + Arrays.toString(verse_counts) + " offsets=" + Arrays.toString(chapter_offsets));
				}
			
				bw.close();
			}
			
			// perikop
			{
				BintexWriter bw_blocks = new BintexWriter(new FileOutputStream(new File(outDir, String.format("%s_pericope_blocks_bt.bt", prefix))));
				BintexWriter bw_index = new BintexWriter(new FileOutputStream(new File(outDir, String.format("%s_pericope_index_bt.bt", prefix))));
				
				bw_index.writeInt(perikopData.xentri.size());
				
				for (PerikopData.Entri pe: perikopData.xentri) {
					/* Blok {
						uint8 versi = 3
						autostring judul
						uint8 nparalel
						autostring[nparalel] xparalel
					   }
					 */
					
					int pos = bw_blocks.getPos();
					bw_blocks.writeUint8(3);
					bw_blocks.writeAutoString(pe.blok.judul);
					int parallel_count = pe.blok.xparalel == null? 0: pe.blok.xparalel.size();
					bw_blocks.writeUint8(parallel_count);
					for (int i = 0; i < parallel_count; i++) {
						bw_blocks.writeAutoString(pe.blok.xparalel.get(i));
					}
					
					bw_index.writeInt(pe.ari);
					bw_index.writeInt(pos);
				}
				
				bw_index.close();
				bw_blocks.close();
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

