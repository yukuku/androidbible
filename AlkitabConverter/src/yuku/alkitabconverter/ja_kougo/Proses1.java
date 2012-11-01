package yuku.alkitabconverter.ja_kougo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.ext.DefaultHandler2;

import yuku.alkitab.yes.YesFile;
import yuku.alkitab.yes.YesFile.InfoEdisi;
import yuku.alkitab.yes.YesFile.InfoKitab;
import yuku.alkitab.yes.YesFile.PericopeData;
import yuku.alkitab.yes.YesFile.PericopeData.Block;
import yuku.alkitab.yes.YesFile.PericopeData.Entry;
import yuku.alkitab.yes.YesFile.PerikopBlok;
import yuku.alkitab.yes.YesFile.PerikopIndex;
import yuku.alkitab.yes.YesFile.Teks;
import yuku.alkitabconverter.OsisBookNames;
import yuku.alkitabconverter.util.Rec;
import yuku.alkitabconverter.util.RecUtil;
import yuku.alkitabconverter.yes_common.YesCommon;

public class Proses1 {
	static String INPUT_TEKS_1 = "../Alkitab/publikasi/ja-kougo/xml/";
	public static String INPUT_TEKS_ENCODING = "utf-8";
	public static int INPUT_TEKS_ENCODING_YES = 2; // 1: ascii; 2: utf-8;
	public static String INPUT_KITAB = "../Alkitab/publikasi/ja-kougo/ja-kougo-kitab.txt";
	static String OUTPUT_YES = "../Alkitab/publikasi/ja-kougo/ja-kougo.yes";
	public static int OUTPUT_ADA_PERIKOP = 1;

	final SAXParserFactory factory = SAXParserFactory.newInstance();
	Handler handler;

	List<Rec> xrec = new ArrayList<Rec>();
	PericopeData pericopeData = new PericopeData();
	{
		pericopeData.entries = new ArrayList<Entry>();
	}

	public static void main(String[] args) throws Exception {
		new Proses1().u();
	}

	public void u() throws Exception {
		handler = new Handler();

		String[] files = new File(INPUT_TEKS_1).list(new FilenameFilter() {
			@Override public boolean accept(File dir, String name) {
				return name.endsWith(".xml");
			}
		});
		
		Arrays.sort(files);
		
		for (String file : files) {
			//System.out.println(file);
			FileInputStream in = new FileInputStream(new File(INPUT_TEKS_1, file));
			SAXParser parser = factory.newSAXParser();
			parser.getXMLReader().setFeature("http://xml.org/sax/features/namespaces", true);
			parser.parse(in, handler);
			
			//System.out.println("file " + file + " done; now total rec: " + xrec.size());
		}
		
		// POST-PROCESS
		for (Rec rec: xrec) {
			// tambah @@ kalo perlu
			if (rec.text.contains("@") && !rec.text.startsWith("@@")) {
				rec.text = "@@" + rec.text;
			}
			
			// betulin 〔セラ yang ga ada kurung tutupnya
			rec.text = rec.text.replaceAll("(\u3014(ヒガヨン、)?セラ)(($|[^\u3015]))", "$1\u3015$3");
			
			System.out.println(rec.book_1 + "\t" + rec.chapter_1 + "\t" + rec.verse_1 + "\t" + rec.text);
		}
		//System.out.println("Total rec: " + xrec.size());

		////////// PROSES KE YES

		final InfoEdisi infoEdisi = YesCommon.infoEdisi("ja-kougo", null, "口語訳", RecUtil.hitungKitab(xrec), OUTPUT_ADA_PERIKOP, "新約1954年/旧約1955年", INPUT_TEKS_ENCODING_YES, null);
		final InfoKitab infoKitab = YesCommon.infoKitab(xrec, INPUT_KITAB, INPUT_TEKS_ENCODING, INPUT_TEKS_ENCODING_YES);
		final Teks teks = YesCommon.teks(xrec, INPUT_TEKS_ENCODING);
		
		YesFile file = YesCommon.bikinYesFile(infoEdisi, infoKitab, teks, new PerikopBlok(pericopeData), new PerikopIndex(pericopeData));
		
		file.output(new RandomAccessFile(OUTPUT_YES, "rw"));
	}

	public class Handler extends DefaultHandler2 {
		int kitab_1 = 0;
		int pasal_1 = 0;
		int ayat_1 = 0;
		int lastAri = 0;
		boolean ayatSid = false;
		
		String[] tree = new String[80];
		int depth = 0;
		StringBuilder b = new StringBuilder();
		StringBuilder b_comment = new StringBuilder();
		boolean simpan = false;
		boolean simpan_comment = false;
		
		int indenDiXml = 0;
		int indenDiAyat = 0;

		@Override public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
			tree[depth++] = localName;

			//System.out.print("(start:) ");
			//cetak();
			
			String alamat = alamat();
			if (alamat.equals("/book")) {
				String bookName = attributes.getValue("id");
				kitab_1 = OsisBookNames.abbrToKitab0(bookName) + 1;
			} else if (alamat.endsWith("/chapter")) {
				String pasal_s = attributes.getValue("id");
				pasal_1 = Integer.parseInt(pasal_s);
				ayat_1 = 1; // reset to 1
			} else if (alamat.endsWith("/verse")) {
				String ayat_s = attributes.getValue("id");
				
				if (ayat_s != null) {
					String[] splits = ayat_s.split(":", 2);
					if (splits.length < 2) throw new RuntimeException("ayat ngaco: " + ayat_s);
					try {
						ayat_1 = Integer.parseInt(splits[1]);
					} catch (NumberFormatException e) {
						b.append("(" + ayat_s + ") ");
						ayat_1 = parseIntSecukupnya(splits[1]);
					}
					ayatSid = false;
					simpan = true;
				} else {
					String ayat_s2 = attributes.getValue("sid");
					if (ayat_s2 != null) {
						String[] splits = ayat_s2.split(":", 2);
						if (splits.length < 2) throw new RuntimeException("ayat ngaco: " + ayat_s);
						try {
							ayat_1 = Integer.parseInt(splits[1]);
						} catch (NumberFormatException e) {
							b.append("(" + ayat_s2 + ") ");
							ayat_1 = parseIntSecukupnya(splits[1]);
						}
						ayatSid = true;
						simpan = true;
					} else {
						String ayat_s3 = attributes.getValue("eid");
						if (ayat_s3 != null && ayatSid) {
							newVerse(kitab_1, pasal_1, ayat_1, b.toString());
							b.setLength(0);
						}
						simpan = false;
					}
				}
			} else if (alamat.endsWith("/l")) {
				indenDiXml++;
				if (indenDiXml == 1) {
					b.append("@1");
					indenDiAyat = 1;
				} else if (indenDiXml > 1) {
					throw new RuntimeException("inden di xml: " + indenDiXml);
				}
			} else if (alamat.endsWith("/comment")) {
				simpan_comment = true;
			}
		}

		@Override public void endElement(String uri, String localName, String qName) throws SAXException {
			//System.out.print("(end:) ");
			//cetak();

			String alamat = alamat();
			if (alamat.endsWith("/verse")) {
				if (!ayatSid) {
					newVerse(kitab_1, pasal_1, ayat_1, b.toString());
					b.setLength(0);
					simpan = false;
				}
			} else if (alamat.endsWith("/l")) {
				indenDiXml--;
			} else if (alamat.endsWith("/p")) {
				// tambah @8 di ayat terakhir ATAU buffer b (belum jadi ayat)
				if (b.length() > 0) {
					b.append("@8");
				} else {
					Rec lastRec = xrec.get(xrec.size() - 1);
					lastRec.text = lastRec.text + "@8";
				}
			} else if (alamat.endsWith("/comment")) {
				simpan_comment = false;
				
				String comment = b_comment.toString();
				b_comment.setLength(0);
				
				// masukin ke data perikop
				Entry entry = new Entry();
				entry.ari = (kitab_1 - 1) << 16 | pasal_1 << 8 | ayat_1;
				entry.block = new Block();
				entry.block.version = 2;
				entry.block.title = comment;
				pericopeData.entries.add(entry);
				
				System.out.println("Perikop: " + kitab_1 + " " + pasal_1 + " " + ayat_1 + " " + comment);
			}

			tree[--depth] = null;
		}

		private int parseIntSecukupnya(String s) {
			for (int i = 0; i < s.length(); i++) {
				if (!Character.isDigit(s.charAt(i))) {
					s = s.substring(0, i);
					break;
				}
			}
			return Integer.parseInt(s);
		}

		private void newVerse(int kitab_1, int pasal_1, int ayat_1, String isi) {
			int ari = (kitab_1 - 1) << 16 | pasal_1 << 8 | ayat_1;
			if (ari > lastAri) {
				if (pasal_1 == ((lastAri & 0x00ff00) >> 8)) {
					// isi dengan kekosongan
					for (int a_1 = ((lastAri+1) & 0xff); a_1 < ayat_1; a_1++) {
						Rec rec = new Rec();
						rec.book_1 = kitab_1;
						rec.chapter_1 = pasal_1;
						rec.verse_1 = a_1;
						rec.text = "";
						xrec.add(rec);
					}
				}
				
				Rec rec = new Rec();
				rec.book_1 = kitab_1;
				rec.chapter_1 = pasal_1;
				rec.verse_1 = ayat_1;
				rec.text = isi;
				xrec.add(rec);
				
				// reset inden ke 0 lagi
				indenDiAyat = 0;
				
				lastAri = ari; // bukan di bawah.
			} else { // ari sama lagi, ato malah mundur. Maka append ke rec terakhir
				Rec lastRec = xrec.get(xrec.size()-1);
				lastRec.text += " ";
				lastRec.text += " (" + pasal_1 + ":" + ayat_1 + ") ";
				lastRec.text += isi;
			}
		}
		
		@Override public void characters(char[] ch, int start, int length) throws SAXException {
			if (simpan) {
				if (indenDiXml == 0 && indenDiAyat != 0) {
					b.append("@0");
					indenDiAyat = 0;
				}
				b.append(ch, start, length);
			} else if (simpan_comment) {
				b_comment.append(ch, start, length);
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
