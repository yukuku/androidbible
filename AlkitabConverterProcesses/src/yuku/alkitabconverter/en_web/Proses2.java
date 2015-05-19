package yuku.alkitabconverter.en_web;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DefaultHandler2;
import yuku.alkitab.util.Ari;
import yuku.alkitab.yes2.model.PericopeData;
import yuku.alkitabconverter.util.FootnoteDb;
import yuku.alkitabconverter.util.TextDb;
import yuku.alkitabconverter.util.UsfmBookName;
import yuku.alkitabconverter.util.XrefDb;
import yuku.alkitabconverter.yes_common.Yes2Common;
import yuku.alkitabconverter.yet.YetFileOutput;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class Proses2 {
	final SAXParserFactory factory = SAXParserFactory.newInstance();

	static String INPUT_PATH_PREFIX = "/Users/yuku/Dropbox/YUKU JAYA/alkitab-sources/step1/usfx/eng-web_usfx";
	static String INPUT_BOOK_NAMES = INPUT_PATH_PREFIX + "/book_names.txt";
	static String INPUT_TEXT_2 = INPUT_PATH_PREFIX + "/eng-web_usfx.xml";
	static String OUTPUT_YET = "/tmp/en-web.yet";
	static String INFO_LOCALE = "en";
	static String INFO_SHORT_NAME = "WEB";
	static String INFO_LONG_NAME = "World English Bible";
	static String INFO_DESCRIPTION = "The World English Bible (WEB) is a Public Domain (no copyright) Modern English translation of the Holy Bible (2014 edition).";
	static boolean AUTOMATIC_TEXT_WITHOUT_PARAGRAPHS = false; // true if we consider text outside paragraph as Bible text as well.
	static boolean XREF_SHIFT_TB = false; // whether detected xref references are TB-verse-shifted

	TextDb teksDb = new TextDb();
	StringBuilder misteri = new StringBuilder();
	XrefDb xrefDb = new XrefDb();
	FootnoteDb footnoteDb = new FootnoteDb();

	PericopeData pericopeData = new PericopeData();
	{
		pericopeData.entries = new ArrayList<>();
	}

	public static void main(String[] args) throws Exception {
		new Proses2().u();
	}

	public void u() throws Exception {
		System.out.println("file " + INPUT_TEXT_2 + " start;");

		FileInputStream in = new FileInputStream(new File(INPUT_TEXT_2));
		SAXParser parser = factory.newSAXParser();
		XMLReader r = parser.getXMLReader();
		System.out.println("input buffer size (old) = " + r.getProperty("http://apache.org/xml/properties/input-buffer-size"));
		r.setProperty("http://apache.org/xml/properties/input-buffer-size", 1048576);
		System.out.println("input buffer size (new) = " + r.getProperty("http://apache.org/xml/properties/input-buffer-size"));
		r.setFeature("http://xml.org/sax/features/namespaces", true);
		parser.parse(in, new Handler());

		System.out.println("file " + INPUT_TEXT_2 + " done; now total rec: " + teksDb.size());

		System.out.println("OUTPUT MISTERI:");
		System.out.println(misteri);

		System.out.println("OUTPUT XREF:");
		xrefDb.processEach(XREF_SHIFT_TB ? XrefDb.defaultShiftTbProcessor : XrefDb.defaultWithoutShiftTbProcessor);
		xrefDb.dump();

		System.out.println("OUTPUT FOOTNOTE:");
		footnoteDb.dump();

		// POST-PROCESS

		teksDb.normalize();
		teksDb.removeEmptyVerses();
		teksDb.dump();

		////////// PROSES KE YET

		final File yetOutputFile = new File(OUTPUT_YET);
		//noinspection ResultOfMethodCallIgnored
		yetOutputFile.getParentFile().mkdir();
		final YetFileOutput yet = new YetFileOutput(yetOutputFile);
		final Yes2Common.VersionInfo versionInfo = new Yes2Common.VersionInfo();
		versionInfo.locale = INFO_LOCALE;
		versionInfo.shortName = INFO_SHORT_NAME;
		versionInfo.longName = INFO_LONG_NAME;
		versionInfo.description = INFO_DESCRIPTION;
		versionInfo.setBookNamesFromFile(INPUT_BOOK_NAMES);
		yet.setVersionInfo(versionInfo);
		yet.setTextDb(teksDb);
		yet.setPericopeData(pericopeData);
		yet.setXrefDb(xrefDb);
		yet.setFootnoteDb(footnoteDb);

		yet.write();
	}

	public class Handler extends DefaultHandler2 {
		private static final int LEVEL_p_r = -2;
		private static final int LEVEL_p_ms = -3;
		private static final int LEVEL_p_mr = -4;

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

		List<PericopeData.Entry> perikopBuffer = new ArrayList<PericopeData.Entry>();
		boolean afterThisMustStartNewPerikop = true; // if true, we have done with a pericope title, so the next text must become a new pericope title instead of appending to existing one

		// states
		int sLevel = 0;
		int menjorokTeks = -1; // -2 adalah para start; 0 1 2 3 4 adalah q level;
		int xref_state = -1; // 0 is initial (just encountered xref tag <x>), 1 is source, 2 is target
		int footnote_state = -1; // 0 is initial (just encountered footnote tag <f>), 1 is fr (reference), 2 is fk (keywords), 3 is ft (text)

		// for preventing split of characters in text elements
		StringBuilder charactersBuffer = new StringBuilder();

		String xrefBuffer = null;
		String footnoteBuffer = null;

		public Handler() {
			tujuanTulis.push(tujuanTulis_misteri);
		}
		
		@Override public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
			flushCharactersBuffer();

			tree[depth++] = localName;

			System.out.print("(start:) ");
			cetak();
			
			String alamat = alamat();
			if (alamat.endsWith("/book")) {
				String id = attributes.getValue("id");
				if ("FRT".equals(id)) {
					kitab_0 = -1; // Front page
				} else if ("GLO".equals(id)) {
					kitab_0 = -2; // glossary
				} else {
					kitab_0 = UsfmBookName.toBookId(id);
				}
				pasal_1 = 0; // do not accept texts before chapter_1 1 start.
				ayat_1 = 0;
				if (AUTOMATIC_TEXT_WITHOUT_PARAGRAPHS) {
					tujuanTulis.push(tujuanTulis_teks);
				}
			} else if (alamat.endsWith("/c")) {
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
				if (pasal_1 == 0) {
					// do not store footnotes for before-chapter text
					tujuanTulis.push(tujuanTulis_misteri);
				} else {
					if (footnoteBuffer != null) {
						throw new RuntimeException("footnoteBuffer is not null but another <f> encountered");
					}
					footnoteBuffer = "";
					tujuanTulis.push(tujuanTulis_footnote);
					footnote_state = 0;
				}
			} else if (alamat.endsWith("/f/fr")) {
				footnote_state = 1;
			} else if (alamat.endsWith("/f/fk")) {
				footnote_state = 2;
			} else if (alamat.endsWith("/f/ft")) {
				footnote_state = 3;
			} else if (alamat.endsWith("/p")) {
				String sfm = attributes.getValue("sfm");
				if (pasal_1 == 0) { // we haven't started chapter 1
					tujuanTulis.push(tujuanTulis_misteri);
				} else if (sfm != null) {
					if (sfm.equals("r")) {
						tujuanTulis.push(tujuanTulis_judulPerikop);
						sLevel = LEVEL_p_r;
					} else if (sfm.equals("mt")) {
						tujuanTulis.push(tujuanTulis_misteri);
					} else if (sfm.equals("ms")) {
						tujuanTulis.push(tujuanTulis_judulPerikop);
						sLevel = LEVEL_p_ms;
					} else if (sfm.equals("mr")) {
						tujuanTulis.push(tujuanTulis_judulPerikop);
						sLevel = LEVEL_p_mr;
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
					} else if (sfm.equals("li")) {
						/*
						 * \li#(_text...)
						 * List item.
						 * · An out-dented paragraph meant to highlight the items of a list.
						 * · Lists may be used to markup the elements of a recurrent structure, such as the days within the creation account, or the Decalogue (10 commandments).
						 * · The variable # represents the level of indent.
						 */
						tujuanTulis.push(tujuanTulis_teks);
						menjorokTeks = 2; // inden 2
					} else if (sfm.equals("nb")) {
						/*
						 * Indicates "no-break" with previous paragraph (regardless of previous paragraph type).
						 * Commonly used in cases where the previous paragraph spans the chapter boundary.
						 */
						tujuanTulis.push(tujuanTulis_teks);
						// do not change menjorokTeks, treat as if it was no new para marker
					} else if (sfm.equals("rq")) {
						/*
						 * Inline quotation reference(s).
						 * A (cross) reference indicating the source text for the preceding quotation (usually an Old Testament quote).
						 */
						tujuanTulis.push(tujuanTulis_xref);
						xref_state = 0;
					} else if (sfm.equals("cls")) {
						/*
						 * Closure of an epistle/letter.
						 * Similar to "With love," or "Sincerely yours,".
						 */
						tujuanTulis.push(tujuanTulis_teks);
						menjorokTeks = 4; // inden 4
					} else if (sfm.equals("is") || sfm.equals("ip") || sfm.equals("ili")) {
						/*
						 * Introduction things (not supported)
						 */
						tujuanTulis.push(tujuanTulis_misteri);
					} else if (sfm.equals("sp")) {
						/*
						 * Speaker (song of solomon)
						 */
						tujuanTulis.push(tujuanTulis_teks);
					} else {
						throw new RuntimeException("p@sfm ga dikenal: " + sfm);
					}
				} else {
					tujuanTulis.push(tujuanTulis_teks);
					menjorokTeks = -2;
				}
			} else if (alamat.endsWith("/q")) {
				tujuanTulis.push(tujuanTulis_teks);
				final String level_s = attributes.getValue("level");
				final int level = level_s == null? 1: Integer.parseInt(level_s);
				if (level >= 1 && level <= 4) {
					menjorokTeks = level;
				} else {
					throw new RuntimeException("q level = " + level);
				}
			} else if (alamat.endsWith("/s")) {
				tujuanTulis.push(tujuanTulis_judulPerikop);
				final String level_s = attributes.getValue("level");
				sLevel = level_s == null? 1: Integer.parseInt(level_s);
			} else if (alamat.endsWith("/x")) {
				if (xrefBuffer != null) {
					throw new RuntimeException("xrefBuffer is not null but another <x> encountered");
				}
				xrefBuffer = "";
				tujuanTulis.push(tujuanTulis_xref);
				xref_state = 0;
			} else if (alamat.endsWith("/x/milestone")) { // after milestone, we will have xref source
				xref_state = 1;
			} else if (alamat.endsWith("/x/xt")) { // after xt, we will have xref target
				xref_state = 2;
			} else if (alamat.endsWith("/x/ref")) { // after ref, we will have definitely the xref target
				xref_state = 2;
			} else if (alamat.endsWith("/wj")) {
				tujuanTulis.push(tujuanTulis_teks);
				tulis("@6");
			}
		}

		@Override public void endElement(String uri, String localName, String qName) throws SAXException {
			flushCharactersBuffer();

			System.out.print("(end:) ");
			cetak();

			String alamat = alamat();
			if (alamat.endsWith("/p")) {
				tujuanTulis.pop();
			} else if (alamat.endsWith("/f")) {
				if (pasal_1 == 0) {
					// do not store footnotes for before-chapter text
				} else {
					if (footnoteBuffer == null) {
						throw new RuntimeException("footnoteBuffer is still null but </f> encountered");
					}
					final int ari = Ari.encode(kitab_0, pasal_1, ayat_1);
					final int footnoteIndex = footnoteDb.addBegin(ari);
					footnoteDb.appendText(ari, footnoteBuffer);
					teksDb.append(ari, "@<f" + (footnoteIndex + 1) + "@>@/", -1);

					footnoteBuffer = null;
				}

				tujuanTulis.pop();
			} else if (alamat.endsWith("/q")) {
				tujuanTulis.pop();
			} else if (alamat.endsWith("/s")) {
				afterThisMustStartNewPerikop = true;
				tujuanTulis.pop();
				// reset xref_state to -1 to prevent undetected unset xref_state
				xref_state = -1;
			} else if (alamat.endsWith("/x")) {
				if (xrefBuffer == null) {
					throw new RuntimeException("xrefBuffer is still null but </x> encountered");
				}

				int ari = Ari.encode(kitab_0, pasal_1, ayat_1);
				final int xrefIndex = xrefDb.addComplete(ari, xrefBuffer);
				teksDb.append(ari, "@<x" + (xrefIndex + 1) + "@>@/", -1);

				xrefBuffer = null;
				tujuanTulis.pop();
			} else if (alamat.endsWith("/wj")) {
				tulis("@5");
				tujuanTulis.pop();
			}

			tree[--depth] = null;
		}

		private void flushCharactersBuffer() {
			if (charactersBuffer.length() > 0) {
				charactersCompleted(charactersBuffer.toString());
				charactersBuffer.setLength(0);
			}
		}

		@Override public void characters(char[] ch, int start, int length) throws SAXException {
			charactersBuffer.append(ch, start, length);
		}

		void charactersCompleted(String text) {
			System.out.println("#text:" + text);
			if (text.trim().length() == 0) {
				// when processing footnotes, we still continue even though the text is whitespace only.
				if (tujuanTulis.peek() != tujuanTulis_footnote) {
					return;
				}
			}
			tulis(text);
		}

		private void tulis(String chars) {
			System.out.println("@@tulis stack: " + tujuanTulisDumpStack());
			Object tujuan = tujuanTulis.peek();
			if (tujuan == tujuanTulis_misteri) {
				System.out.println("$tulis ke misteri " + kitab_0 + " " + pasal_1 + " " + ayat_1 + ":" + chars);
				misteri.append(chars).append('\n');
			} else if (tujuan == tujuanTulis_teks) {
				System.out.println("$tulis ke teks[jenis=" + menjorokTeks + "] " + kitab_0 + " " + pasal_1 + " " + ayat_1 + ":" + chars);
				teksDb.append(kitab_0, pasal_1, ayat_1, chars.replace("\n", " ").replaceAll("\\s+", " "), menjorokTeks);
				menjorokTeks = -1; // reset

				if (perikopBuffer.size() > 0) {
					for (PericopeData.Entry pe: perikopBuffer) {
						pe.block.title = pe.block.title.replace("\n", " ").replace("  ", " ").trim();
						System.out.println("(commit to perikopData " + kitab_0 + " " + pasal_1 + " " + ayat_1 + ":) " + pe.block.title);
						pe.ari = Ari.encode(kitab_0, pasal_1, ayat_1);
						pericopeData.entries.add(pe);
					}
					perikopBuffer.clear();
				}
			} else if (tujuan == tujuanTulis_judulPerikop) {
				// masukin ke data perikop
				String judul = chars;

				if (sLevel == 0 || sLevel == 1 || sLevel == 2 || sLevel == LEVEL_p_mr || sLevel == LEVEL_p_ms) {
					if (afterThisMustStartNewPerikop || perikopBuffer.size() == 0) {
						PericopeData.Entry entry = new PericopeData.Entry();
						entry.ari = 0; // done later when writing teks so we know which verse this pericope starts from
						entry.block = new PericopeData.Block();
						entry.block.title = judul;
						perikopBuffer.add(entry);
						afterThisMustStartNewPerikop = false;
						System.out.println("$tulis ke perikopBuffer (new entry) (size now: " + perikopBuffer.size() + "): " + judul);
					} else if (sLevel > 2) {
						throw new RuntimeException("sLevel not handled: " + sLevel);
					} else {
						perikopBuffer.get(perikopBuffer.size() - 1).block.title += judul;
						System.out.println("$tulis ke perikopBuffer (append to existing) (size now: " + perikopBuffer.size() + "): " + judul);
					}
				} else if (sLevel == LEVEL_p_r) { // paralel
					if (perikopBuffer.size() == 0) {
						throw new RuntimeException("paralel found but no perikop on buffer: " + judul);
					}

					PericopeData.Entry entry = perikopBuffer.get(perikopBuffer.size() - 1);
					entry.block.parallels = parseParalel(judul);
				} else {
					throw new RuntimeException("sLevel = " + sLevel + " not understood: " + judul);
				}

			} else if (tujuan == tujuanTulis_xref) {
				System.out.println("$tulis ke xref (state=" + xref_state + ") " + kitab_0 + " " + pasal_1 + " " + ayat_1 + ":" + chars);
				if (xrefBuffer == null) {
					throw new RuntimeException("xrefBuffer is still null but tujuanTulis is xref");
				}
				xrefBuffer += chars;

			} else if (tujuan == tujuanTulis_footnote) {
				System.out.println("$tulis ke footnote (state=" + footnote_state + ") " + kitab_0 + " " + pasal_1 + " " + ayat_1 + ":" + chars);
				if (footnoteBuffer == null) {
					throw new RuntimeException("footnoteBuffer is still null but tujuanTulis is xref");
				}
				if (footnote_state == 0) {
					final String content;

					// remove caller at the beginning
					if (chars.matches("[a-zA-Z+-]\\s.*")) {
						// remove that first 2 characters
						content = chars.substring(2);
					} else {
						content = chars;
					}

					if (content.trim().length() != 0) {
						footnoteBuffer += content.replace("\n", " ");
					}

				} else if (footnote_state == 2) {
					footnoteBuffer += "@9" + chars.replace("\n", " ") + "@7";
				} else if (footnote_state == 1 || footnote_state == 3) {
					footnoteBuffer += chars.replace("\n", " ");
				} else {
					throw new RuntimeException("footnote_state not supported");
				}
			}
		}

		private String tujuanTulisDumpStack() {
			final StringBuilder sb = new StringBuilder();
			for (final Object t : tujuanTulis) {
				if (sb.length() != 0) {
					sb.append(", ");
				}
				if (t == tujuanTulis_footnote) sb.append("footnote");
				else if (t == tujuanTulis_judulPerikop) sb.append("judulPerikop");
				else if (t == tujuanTulis_misteri) sb.append("misteri");
				else if (t == tujuanTulis_teks) sb.append("teks");
				else if (t == tujuanTulis_xref) sb.append("xref");
				else throw new RuntimeException("SHOULD NOT HAPPEN!!!!");
			}
			return sb.toString();
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

	/**
	 * (Mat. 23:1-36; Mrk. 12:38-40; Luk. 20:45-47) -> [Mat. 23:1-36, Mrk. 12:38-40, Luk. 20:45-47]
	 * (Mat. 10:26-33, 19-20) -> [Mat. 10:26-33, Mat. 10:19-20]
	 * (Mat. 6:25-34, 19-21) -> [Mat. 6:25-34, Mat. 6:19-21]
	 * (Mat. 10:34-36) -> [Mat. 10:34-36]
	 * (Mat. 26:57-58, 69-75; Mrk. 14:53-54, 66-72; Yoh. 18:12-18, 25-27) -> [Mat. 26:57-58, Mat. 26:69-75, Mrk. 14:53-54, Mrk. 14:66-72, Yoh. 18:12-18, Yoh. 18:25-27]
	 * (2Taw. 13:1--14:1) -> [2Taw. 13:1--14:1]
	 * (2Taw. 14:1-5, 15:16--16:13) -> [2Taw. 14:1-5, 2Taw. 15:16--16:13]
	 * (2Taw. 2:13-14, 3:15--5:1) -> [2Taw. 2:13-14, 2Taw. 3:15--5:1]
	 * (2Taw. 34:3-7, 35:1-27) -> [2Taw. 34:3-7, 2Taw. 35:1-27]
	 */
	static List<String> parseParalel(String judul) {
		List<String> res = new ArrayList<String>();

		judul = judul.trim();
		if (judul.startsWith("(")) judul = judul.substring(1);
		if (judul.endsWith(")")) judul = judul.substring(0, judul.length() - 1);

		String kitab = null;
		String pasal = null;
		String ayat = null;

		String[] alamats = judul.split("[;,]");
		for (String alamat : alamats) {
			alamat = alamat.trim();

			String[] bagians = alamat.split(" +", 2);
			String pa;
			if (bagians.length == 1) { // no kitab;
				if (kitab == null) throw new RuntimeException("no existing kitab");
				pa = bagians[0];
			} else {
				kitab = bagians[0];
				pa = bagians[1];
			}

			String[] parts = pa.split(":", 2);
			if (parts.length == 1) { // no pasal
				if (pasal == null) throw new RuntimeException("no existing pasal");
				ayat = parts[0];
			} else {
				pasal = parts[0];
				ayat = parts[1];
			}

			res.add(kitab + " " + pasal + ":" + ayat);
		}

		return res;
	}
}
