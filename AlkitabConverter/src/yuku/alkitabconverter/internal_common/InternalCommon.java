package yuku.alkitabconverter.internal_common;

import yuku.alkitab.yes2.model.PericopeData;
import yuku.alkitabconverter.util.CountingOutputStream;
import yuku.alkitabconverter.util.FootnoteDb;
import yuku.alkitabconverter.util.Rec;
import yuku.alkitabconverter.util.TextDb;
import yuku.alkitabconverter.util.XrefDb;
import yuku.bintex.BintexWriter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class InternalCommon {
	public static final String TAG = InternalCommon.class.getSimpleName();

	public final static Charset ascii = Charset.forName("ascii");
	public final static Charset utf8 = Charset.forName("utf8");

	/**
	 * @param prefix e.g. "tb"
	 */
	public static void createInternalFiles(File outDir, String prefix, List<String> bookNames, TextDb teksDb, PericopeData pericopeData) {
		createInternalFiles(outDir, prefix, bookNames, teksDb.toRecList(), pericopeData);
	}
	
	/**
	 * @param prefix e.g. "tb"
	 */
	public static void createInternalFiles(File outDir, String prefix, List<String> bookNames, List<Rec> _recs, PericopeData pericopeData) {
		createInternalFiles(outDir, prefix, bookNames, _recs, pericopeData, null, null);
	}

	/**
	 * @param prefix e.g. "tb"
	 */
	public static void createInternalFiles(File outDir, String prefix, List<String> bookNames, List<Rec> _recs, PericopeData pericopeData, XrefDb xrefDb, FootnoteDb footnoteDb) {
		List<List<Rec>> books = new ArrayList<List<Rec>>();
		
		for (int i = 1; i <= 66; i++) {
			books.add(new ArrayList<Rec>());
		}
		
		for (Rec rec: _recs) {
			final int book_1 = rec.book_1;
			if (book_1 < 1 || book_1 > 66) {
				throw new RuntimeException("book_1 not supported: " + book_1);
			}
			
			books.get(book_1 - 1).add(rec);
		}
		
		try {
			{
				int book_count = 0;
				for (int book_1 = 1; book_1 <= books.size(); book_1++) {
					final List<Rec> recs = books.get(book_1 - 1);
					if (recs != null && recs.size() > 0) {
						book_count++;
					}
				}

				// uint8 version = 3
				// uint8 book_count
				final BintexWriter bw = new BintexWriter(new FileOutputStream(new File(outDir, String.format("%s_index_bt.bt", prefix))));
				bw.writeUint8(3);
				bw.writeUint8(book_count);

				for (int book_1 = 1; book_1 <= books.size(); book_1++) {
					final List<Rec> recs = books.get(book_1 - 1);
					if (recs == null || recs.size() == 0) continue;

					int chapter_count = 0;
					for (Rec rec: recs) {
						if (rec.chapter_1 > chapter_count) {
							chapter_count = rec.chapter_1;
						}
					}
	
					int[] verse_counts;
					{ // verse_counts
						verse_counts = new int[chapter_count];
						for (Rec rec: recs) {
							verse_counts[rec.chapter_1 - 1]++;
						}
					}

					final boolean forOpenSource = "1".equals(System.getProperty("for.open.source"));
					int[] chapter_offsets = new int[chapter_count + 1];
					{ // text
						File f = new File(outDir, String.format("%s_k%02d.txt", prefix, book_1));
						CountingOutputStream counter = new CountingOutputStream(new FileOutputStream(f));
						OutputStreamWriter out = new OutputStreamWriter(counter, "utf-8");
						for (Rec rec: recs) {
							if (rec.text.contains("\n")) {
								throw new RuntimeException("Now text can't contain \\n since it's used as a separator");
							}
							out.write(forOpenSource? (rec.text.replaceAll("[A-Z]", "X").replaceAll("[a-z]", "x")): rec.text);
							out.write('\n');
							out.flush();
							chapter_offsets[rec.chapter_1] = (int) counter.getCount();
						}
						out.close();
					}
					{ // index for each book:
						// uint8 bookId;
						// value<string> shortName
						// value<string> abbreviation
						// value<string> resName
						// uint8 chapter_count
						// uint8[chapter_count] verse_counts
						// varuint[chapter_count+1] chapter_offsets
						bw.writeUint8(book_1 - 1);
						bw.writeValueString(bookNames.get(book_1 - 1));
						bw.writeValueString(null); // TODO support for abbreviation
						bw.writeValueString(String.format("%s_k%02d", prefix, book_1));
						bw.writeUint8(chapter_count);
						for (int i = 0; i < chapter_count; i++) {
							bw.writeUint8(verse_counts[i]);
						}
						for (int i = 0; i < chapter_count + 1; i++) {
							bw.writeVarUint(chapter_offsets[i]);
						}
						
					}
					System.out.println("book: " + bookNames.get(book_1 - 1) + " ch_count=" + chapter_count + " v_counts=" + Arrays.toString(verse_counts) + " offsets=" + Arrays.toString(chapter_offsets));
				}
			
				bw.close();
			}
			
			// perikop
			if (pericopeData != null) {
				BintexWriter bw_blocks = new BintexWriter(new FileOutputStream(new File(outDir, String.format("%s_pericope_blocks_bt.bt", prefix))));
				BintexWriter bw_index = new BintexWriter(new FileOutputStream(new File(outDir, String.format("%s_pericope_index_bt.bt", prefix))));
				
				bw_index.writeInt(pericopeData.entries.size());
				
				for (PericopeData.Entry pe: pericopeData.entries) {
					/* Blok {
						uint8 versi = 3
						autostring judul
						uint8 nparalel
						autostring[nparalel] xparalel
					   }
					 */
					
					int pos = bw_blocks.getPos();
					bw_blocks.writeUint8(3);
					bw_blocks.writeAutoString(pe.block.title);
					int parallel_count = pe.block.parallels == null? 0: pe.block.parallels.size();
					bw_blocks.writeUint8(parallel_count);
					for (int i = 0; i < parallel_count; i++) {
						bw_blocks.writeAutoString(pe.block.parallels.get(i));
					}
					
					bw_index.writeInt(pe.ari);
					bw_index.writeInt(pos);
				}
				
				bw_index.close();
				bw_blocks.close();
			}

			// xrefs
			if (xrefDb != null) {
				final BintexWriter bw = new BintexWriter(new FileOutputStream(new File(outDir, String.format("%s_xrefs_bt.bt", prefix))));
				XrefDb.writeXrefEntriesTo(xrefDb.toEntries(), bw);
				bw.close();
			}

			// footnotes
			if (footnoteDb != null) {
				final BintexWriter bw = new BintexWriter(new FileOutputStream(new File(outDir, String.format("%s_footnotes_bt.bt", prefix))));
				FootnoteDb.writeFootnoteEntriesTo(footnoteDb.toEntries(), bw);
				bw.close();
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
			System.out.println("bookPos " + res.size() + " title: " + judul);
			res.add(judul);
		}
		sc.close();
		return res;
	}
}

