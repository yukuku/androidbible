package yuku.alkitabconverter.in_tsi_usfm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.ext.DefaultHandler2;

import yuku.alkitab.yes1.Yes1File;
import yuku.alkitab.yes1.Yes1File.InfoEdisi;
import yuku.alkitab.yes1.Yes1File.InfoKitab;
import yuku.alkitab.yes1.Yes1File.PericopeData;
import yuku.alkitab.yes1.Yes1File.PerikopBlok;
import yuku.alkitab.yes1.Yes1File.PerikopIndex;
import yuku.alkitab.yes1.Yes1File.Teks;
import yuku.alkitab.yes1.Yes1File.PericopeData.Entry;
import yuku.alkitabconverter.util.Ari;
import yuku.alkitabconverter.util.Rec;
import yuku.alkitabconverter.util.RecUtil;
import yuku.alkitabconverter.util.TeksDb;
import yuku.alkitabconverter.yes_common.YesCommon;

public class Proses2 {
	final SAXParserFactory factory = SAXParserFactory.newInstance();
	
	public static String INPUT_TEKS_ENCODING = "utf-8";
	public static int INPUT_TEKS_ENCODING_YES = 2; // 1: ascii; 2: utf-8;
	public static String INPUT_KITAB = "./bahan/in-tsi-usfm/in/in-tsi-usfm-kitab.txt";
	static String OUTPUT_YES = "./bahan/in-tsi-usfm/out/in-tsi.yes";
	public static int OUTPUT_ADA_PERIKOP = 1;
	static String INFO_NAMA = "in-tsi";
	static String INFO_JUDUL = "TSI";
	static String INFO_KETERANGAN = "Terjemahan Sederhana Indonesia (TSI) diterbitkan oleh Pioneer Bible Translators. Teks TSI lengkap dengan catatan-catatan kaki dapat dibaca di m.bahasakita.net atau tsi.bahasakita.net. Tim penerjemah memohon Anda menulis masukan, usulan, atau komentar lain halaman di Facebook “tsi sederhana.” Anda boleh pesan buku-buku TSI yang lain melalui e-mail kepada tim.kita@bahasakita.net. Anda bebas juga untuk mencetak kitab-kitab TSI sesuai informasi yang terdapat di http://bahasakita.net.";
	static String INPUT_TEKS_2 = "./bahan/in-tsi-usfm/mid/"; 

	TeksDb teksDb = new TeksDb();
	StringBuilder misteri = new StringBuilder();
	PericopeData pericopeData = new PericopeData();
	{
		pericopeData.entries = new ArrayList<Entry>();
	}

	public static void main(String[] args) throws Exception {
		new Proses2().u();
	}

	public void u() throws Exception {
		String[] files = new File(INPUT_TEKS_2).list(new FilenameFilter() {
			@Override public boolean accept(File dir, String name) {
				return name.endsWith("-utf8.usfx.xml");
			}
		});
		
		Arrays.sort(files);
		
		for (String file : files) {
			System.out.println("file " + file + " start;");
			
			FileInputStream in = new FileInputStream(new File(INPUT_TEKS_2, file));
			SAXParser parser = factory.newSAXParser();
			parser.getXMLReader().setFeature("http://xml.org/sax/features/namespaces", true);
			parser.parse(in, new Handler(Integer.parseInt(file.substring(0, 2))));
			
			System.out.println("file " + file + " done; now total rec: " + teksDb.size());
		}
		
		System.out.println("MISTERI:");
		System.out.println(misteri);
		
		// POST-PROCESS
		
		teksDb.normalize();
		
		teksDb.dump();

		////////// PROSES KE YES

		List<Rec> xrec = teksDb.toRecList();
		
		final InfoEdisi infoEdisi = YesCommon.infoEdisi(INFO_NAMA, null, INFO_JUDUL, RecUtil.hitungKitab(xrec), OUTPUT_ADA_PERIKOP, INFO_KETERANGAN, INPUT_TEKS_ENCODING_YES, null);
		final InfoKitab infoKitab = YesCommon.infoKitab(xrec, INPUT_KITAB, INPUT_TEKS_ENCODING, INPUT_TEKS_ENCODING_YES);
		final Teks teks = YesCommon.teks(xrec, INPUT_TEKS_ENCODING);
		
		Yes1File file = YesCommon.bikinYesFile(infoEdisi, infoKitab, teks, new PerikopBlok(pericopeData), new PerikopIndex(pericopeData));
		
		file.output(new RandomAccessFile(OUTPUT_YES, "rw"));
	}

	public class Handler extends DefaultHandler2 {
		int kitab_0 = -1;
		int pasal_1 = 0;
		int ayat_1 = 0;
		
		String[] tree = new String[80];
		int depth = 0;
		
		Stack<Object> tujuanTulis = new Stack<Object>();
		Object tujuanTulis_misteri = new Object();
		Object tujuanTulis_teks = new Object();
		Object tujuanTulis_judulPerikop = new Object();
		Object tujuanTulis_xref = new Object();
		Object tujuanTulis_footnote = new Object();
		
		int sLevel = 0;
		int menjorokTeks = -1; // -1 p; 1 2 3 adalah q level;
		
		public Handler(int kitab_0) {
			this.kitab_0 = kitab_0;
			tujuanTulis.push(tujuanTulis_misteri);
		}
		
		@Override public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
			tree[depth++] = localName;

			System.out.print("(start:) ");
			cetak();
			
			String alamat = alamat();
			if (alamat.endsWith("/c")) {
				String id = attributes.getValue("id");
				System.out.println("#c:" + id);
				pasal_1 = Integer.parseInt(id.trim());
				ayat_1 = 1; // reset ayat tiap ganti pasal
			} else if (alamat.endsWith("/v")) {
				String id = attributes.getValue("id");
				System.out.println("#v:" + id);
				try {
					ayat_1 = Integer.parseInt(id);
				} catch (NumberFormatException e) {
					System.out.println("// number format exception for: " + id);
					// get until first non number
					for (int pos = 0; pos < id.length(); pos++) {
						if (!Character.isDigit(id.charAt(pos))) {
							String s = id.substring(0, pos);
							ayat_1 = Integer.parseInt(s); 
							System.out.println("// number format exception simplified to: " + s);
							break;
						}
					}
				}
			} else if (alamat.endsWith("/f")) {
				tujuanTulis.push(tujuanTulis_footnote);
			} else if (alamat.endsWith("/p")) {
				String sfm = attributes.getValue("sfm");
				if (sfm != null) {
					if (sfm.equals("r")) {
						tujuanTulis.push(tujuanTulis_judulPerikop);
					} else if (sfm.equals("mt")) {
						tujuanTulis.push(tujuanTulis_misteri);
					} else if (sfm.equals("ms")) {
						tujuanTulis.push(tujuanTulis_judulPerikop);
					} else if (sfm.equals("mr")) {
						tujuanTulis.push(tujuanTulis_judulPerikop);
					} else if (sfm.equals("mi")) {
						tujuanTulis.push(tujuanTulis_teks);
						menjorokTeks = 2;
					} else if (sfm.equals("pi")) { // Indented para
						tujuanTulis.push(tujuanTulis_teks);
						menjorokTeks = 1;
					} else if (sfm.equals("pc")) { // Centered para
						tujuanTulis.push(tujuanTulis_teks);
						menjorokTeks = 2;
					} else if (sfm.equals("m")) {
						/*
						 * Flush left (margin) paragraph.
						 * • No first line indent.
						 * • Followed immediately by a space and paragraph text, or by a new line and a verse marker.
						 * • Usually used to resume prose at the margin (without indent) after poetry or OT quotation (i.e. continuation of the previous paragraph).
						 */
						tujuanTulis.push(tujuanTulis_teks);
						menjorokTeks = 0; // inden 0
					} else {
						throw new RuntimeException("p@sfm ga dikenal: " + sfm);
					}
				} else {
					tujuanTulis.push(tujuanTulis_teks);
					menjorokTeks = -1;
				}
			} else if (alamat.endsWith("/q")) {
				tujuanTulis.push(tujuanTulis_teks);
				int level = Integer.parseInt(attributes.getValue("level"));
				if (level >= 1 && level <= 4) {
					menjorokTeks = level;
				} else {
					throw new RuntimeException("q level = " + level);
				}
			} else if (alamat.endsWith("/s")) {
				tujuanTulis.push(tujuanTulis_judulPerikop);
				sLevel = Integer.parseInt(attributes.getValue("level"));
			} else if (alamat.endsWith("/x")) {
				tujuanTulis.push(tujuanTulis_xref);
			} else if (alamat.endsWith("/wj")) {
				tujuanTulis.push(tujuanTulis_teks);
				tulis("@6");
			}
		}

		@Override public void endElement(String uri, String localName, String qName) throws SAXException {
			System.out.print("(end:) ");
			cetak();

			String alamat = alamat();
			if (alamat.endsWith("/p")) {
				tujuanTulis.pop();
			} else if (alamat.endsWith("/f")) {
				tujuanTulis.pop();
			} else if (alamat.endsWith("/s")) {
				tujuanTulis.pop();
			} else if (alamat.endsWith("/x")) {
				tujuanTulis.pop();
			} else if (alamat.endsWith("/wj")) {
				tulis("@5");
				tujuanTulis.pop();
			}

			tree[--depth] = null;
		}

		@Override public void characters(char[] ch, int start, int length) throws SAXException {
			String chars = new String(ch, start, length);
			if (chars.trim().length() > 0) {
				System.out.println("#text:" + chars);
				tulis(chars);
			}
		}

		private void tulis(String chars) {
			Object tujuan = tujuanTulis.peek();
			if (tujuan == tujuanTulis_misteri) {
				System.out.println("$tulis ke misteri " + kitab_0 + " " + pasal_1 + " " + ayat_1 + ":" + chars);
				misteri.append(chars).append('\n');
			} else if (tujuan == tujuanTulis_teks) {
				System.out.println("$tulis ke teks[jenis=" + menjorokTeks + "] " + kitab_0 + " " + pasal_1 + " " + ayat_1 + ":" + chars);
				teksDb.append(kitab_0, pasal_1, ayat_1, chars.replace("\n", " ").replaceAll("\\s+", " "), menjorokTeks == -1? 0: menjorokTeks);
			} else if (tujuan == tujuanTulis_judulPerikop) {
				System.out.println("$tulis ke judulPerikop[level=" + sLevel + "] " + kitab_0 + " " + pasal_1 + " " + ayat_1 + " level " + sLevel + ":" + chars);
				// masukin ke data perikop
				String judul = chars.replace("\n", " ").replace("  ", " ").trim();
				PericopeData.Entry entry = new PericopeData.Entry();
				entry.ari = Ari.encode(kitab_0, pasal_1, ayat_1);
				entry.block = new PericopeData.Block();
				entry.block.version = 2;
				entry.block.title = judul;
				pericopeData.entries.add(entry);
			} else if (tujuan == tujuanTulis_xref) {
				System.out.println("$tulis ke xref " + kitab_0 + " " + pasal_1 + " " + ayat_1 + ":" + chars);
			} else if (tujuan == tujuanTulis_footnote) {
				System.out.println("$tulis ke footnote " + kitab_0 + " " + pasal_1 + " " + ayat_1 + ":" + chars);
			}
		}

		void cetak() {
			for (int i = 0; i < depth; i++) {
				System.out.print('/');
				System.out.print(tree[i]);
			}
			System.out.println();
		}

		private StringBuilder a = new StringBuilder();

		private String alamat() {
			a.setLength(0);
			for (int i = 0; i < depth; i++) {
				a.append('/').append(tree[i]);
			}
			return a.toString();
		}

	}
}
