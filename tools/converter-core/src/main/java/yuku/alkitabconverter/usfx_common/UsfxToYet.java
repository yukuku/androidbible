package yuku.alkitabconverter.usfx_common;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DefaultHandler2;
import yuku.alkitab.util.Ari;
import yuku.alkitab.yes2.model.PericopeData;
import yuku.alkitabconverter.util.FootnoteDb;
import yuku.alkitabconverter.util.TextDb;
import yuku.alkitabconverter.util.XrefDb;
import yuku.alkitabconverter.yes_common.Yes2Common;
import yuku.alkitabconverter.yet.YetFileOutput;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class UsfxToYet {
	static final SAXParserFactory factory = SAXParserFactory.newInstance();
	
	TextDb textDb = new TextDb();
	StringBuilder mystery = new StringBuilder();
	XrefDb xrefDb = new XrefDb();
	FootnoteDb footnoteDb = new FootnoteDb();

	PericopeData pericopeData = new PericopeData();
	{
		pericopeData.entries = new ArrayList<>();
	}

	public void u(final InputStream[] inputs, final int[] books_0, final String info_locale, final String info_short_name, final String info_long_name, final String info_description, final List<String> book_names, final List<String> book_abbrs, final OutputStream output_yet) throws IOException {
		for (int i = 0; i < inputs.length; i++) {
			final InputStream input = inputs[i];
			final int book_0 = books_0[i];

			System.out.println("input start;");

			try {
				SAXParser parser = factory.newSAXParser();
				XMLReader r = parser.getXMLReader();
				System.out.println("input buffer size (old) = " + r.getProperty("http://apache.org/xml/properties/input-buffer-size"));
				r.setProperty("http://apache.org/xml/properties/input-buffer-size", 1048576);
				System.out.println("input buffer size (new) = " + r.getProperty("http://apache.org/xml/properties/input-buffer-size"));
				r.setFeature("http://xml.org/sax/features/namespaces", true);
				parser.parse(input, new Handler(book_0));
			} catch (IOException e) {
				throw e;
			} catch (Exception e) {
				throw new RuntimeException(e);
			}

			System.out.println("input done; now total rec: " + textDb.size());
		}
		
		System.out.println("OUTPUT MYSTERY:");
		System.out.println(mystery);

		System.out.println("OUTPUT XREF:");
		xrefDb.processEach(XrefDb.defaultShiftTbProcessor);
		xrefDb.dump();

		System.out.println("OUTPUT FOOTNOTE:");
		footnoteDb.dump();

		// POST-PROCESS
		
		textDb.normalize();
		textDb.removeEmptyVerses();
		textDb.dump();

		////////// PROSES KE YET

		final YetFileOutput yet = new YetFileOutput(output_yet);
		final Yes2Common.VersionInfo versionInfo = new Yes2Common.VersionInfo();
		versionInfo.locale = info_locale;
		versionInfo.shortName = info_short_name;
		versionInfo.longName = info_long_name;
		versionInfo.description = info_description;
		versionInfo.setBookNamesAndAbbreviations(book_names, book_abbrs);
		yet.setVersionInfo(versionInfo);
		yet.setTextDb(textDb);
		yet.setPericopeData(pericopeData);
		yet.setXrefDb(xrefDb);
		yet.setFootnoteDb(footnoteDb);

		yet.write();
	}

	public class Handler extends DefaultHandler2 {
		private static final int LEVEL_p_r = -2;
		private static final int LEVEL_p_ms = -3;
		private static final int LEVEL_p_mr = -4;

		int book_0 = -1;
		int chapter_1 = 0;
		int verse_1 = 0;
		
		String[] tree = new String[80];
		int depth = 0;
		
		Stack<Object> writeTarget = new Stack<>();
		Object writeTarget_mystery = new Object();
		Object writeTarget_text = new Object();
		Object writeTarget_pericopeTitle = new Object();
		Object writeTarget_xref = new Object();
		Object writeTarget_footnote = new Object();

		List<PericopeData.Entry> pericopeBuffer = new ArrayList<>();
		boolean afterThisMustStartNewPerikop = true; // if true, we have done with a pericope title, so the next text must become a new pericope title instead of appending to existing one

		// states
		int sLevel = 0;
		int textIndent = -1; // -2 adalah para start; 0 1 2 3 4 adalah q level;
		int xref_state = -1; // 0 is initial (just encountered xref tag <x>), 1 is source, 2 is target
		int footnote_state = -1; // 0 is initial (just encountered footnote tag <f>), 1 is fr (reference), 2 is fk (keywords), 3 is ft (text)

		// for preventing split of characters in text elements
		StringBuilder charactersBuffer = new StringBuilder();

		public Handler(int book_0) {
			this.book_0 = book_0;
			writeTarget.push(writeTarget_mystery);
		}
		
		@Override public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
			flushCharactersBuffer();

			tree[depth++] = localName;

			System.out.print("(start:) ");
			print();
			
			String alamat = address();
			if (alamat.endsWith("/c")) {
				String id = attributes.getValue("id");
				System.out.println("#c:" + id);
				chapter_1 = Integer.parseInt(id.trim());
				verse_1 = 1; // reset ayat tiap ganti pasal
			} else if (alamat.endsWith("/v")) {
				String id = attributes.getValue("id");
				System.out.println("#v:" + id);
				try {
					verse_1 = Integer.parseInt(id);
				} catch (NumberFormatException e) {
					System.out.println("// number format exception for: " + id);
					// get until first non number
					for (int pos = 0; pos < id.length(); pos++) {
						if (!Character.isDigit(id.charAt(pos))) {
							String s = id.substring(0, pos);
							verse_1 = Integer.parseInt(s);
							System.out.println("// number format exception simplified to: " + s);
							break;
						}
					}
				}
			} else if (alamat.endsWith("/f")) {
				writeTarget.push(writeTarget_footnote);
				footnote_state = 0;
			} else if (alamat.endsWith("/f/fr")) {
				footnote_state = 1;
			} else if (alamat.endsWith("/f/fk")) {
				footnote_state = 2;
			} else if (alamat.endsWith("/f/ft")) {
				footnote_state = 3;
			} else if (alamat.endsWith("/p")) {
				String sfm = attributes.getValue("sfm");
				if (sfm != null) {
					switch (sfm) {
						case "r":
							writeTarget.push(writeTarget_pericopeTitle);
							sLevel = LEVEL_p_r;
							break;
						case "mt":
							writeTarget.push(writeTarget_mystery);
							break;
						case "ms":
							writeTarget.push(writeTarget_pericopeTitle);
							sLevel = LEVEL_p_ms;
							break;
						case "mr":
							writeTarget.push(writeTarget_pericopeTitle);
							sLevel = LEVEL_p_mr;
							break;
						case "mi":
							writeTarget.push(writeTarget_text);
							textIndent = 2;
							break;
						case "pi":  // Indented para
							writeTarget.push(writeTarget_text);
							textIndent = 1;
							break;
						case "pc":  // Centered para
							writeTarget.push(writeTarget_text);
							textIndent = 2;
							break;
						case "m":
						/*
						 * Flush left (margin) paragraph.
						 * • No first line indent.
						 * • Followed immediately by a space and paragraph text, or by a new line and a verse marker.
						 * • Usually used to resume prose at the margin (without indent) after poetry or OT quotation (i.e. continuation of the previous paragraph).
						 */
							writeTarget.push(writeTarget_text);
							textIndent = 0; // inden 0

							break;
						default:
							throw new RuntimeException("p@sfm ga dikenal: " + sfm);
					}
				} else {
					writeTarget.push(writeTarget_text);
					textIndent = -2;
				}
			} else if (alamat.endsWith("/q")) {
				writeTarget.push(writeTarget_text);
				int level = Integer.parseInt(attributes.getValue("level"));
				if (level >= 1 && level <= 4) {
					textIndent = level;
				} else {
					throw new RuntimeException("q level = " + level);
				}
			} else if (alamat.endsWith("/s")) {
				writeTarget.push(writeTarget_pericopeTitle);
				sLevel = Integer.parseInt(attributes.getValue("level"));
			} else if (alamat.endsWith("/x")) {
				writeTarget.push(writeTarget_xref);
				xref_state = 0;
			} else if (alamat.endsWith("/x/milestone")) { // after milestone, we will have xref source
				xref_state = 1;
			} else if (alamat.endsWith("/x/xt")) { // after xt, we will have xref target
				xref_state = 2;
			} else if (alamat.endsWith("/wj")) {
				writeTarget.push(writeTarget_text);
				write("@6");
			}
		}

		@Override public void endElement(String uri, String localName, String qName) throws SAXException {
			flushCharactersBuffer();

			System.out.print("(end:) ");
			print();

			String alamat = address();
			if (alamat.endsWith("/p")) {
				writeTarget.pop();
			} else if (alamat.endsWith("/f")) {
				writeTarget.pop();
			} else if (alamat.endsWith("/s")) {
				afterThisMustStartNewPerikop = true;
				writeTarget.pop();
			} else if (alamat.endsWith("/x")) {
				writeTarget.pop();
			} else if (alamat.endsWith("/wj")) {
				write("@5");
				writeTarget.pop();
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
				if (writeTarget.peek() != writeTarget_footnote) {
					return;
				}
			}
			write(text);
		}

		private void write(String chars) {
			Object target = writeTarget.peek();
			if (target == writeTarget_mystery) {
				System.out.println("$tulis ke mystery " + book_0 + " " + chapter_1 + " " + verse_1 + ":" + chars);
				mystery.append(chars).append('\n');
			} else if (target == writeTarget_text) {
				System.out.println("$tulis ke teks[jenis=" + textIndent + "] " + book_0 + " " + chapter_1 + " " + verse_1 + ":" + chars);
				textDb.append(book_0, chapter_1, verse_1, chars.replace("\n", " ").replaceAll("\\s+", " "), textIndent);
				textIndent = -1; // reset

				if (pericopeBuffer.size() > 0) {
					for (PericopeData.Entry pe: pericopeBuffer) {
						pe.block.title = pe.block.title.replace("\n", " ").replace("  ", " ").trim();
						System.out.println("(commit to perikopData " + book_0 + " " + chapter_1 + " " + verse_1 + ":) " + pe.block.title);
						pe.ari = Ari.encode(book_0, chapter_1, verse_1);
						pericopeData.entries.add(pe);
					}
					pericopeBuffer.clear();
				}
			} else if (target == writeTarget_pericopeTitle) {
				// masukin ke data perikop
				final String title = chars;

				if (sLevel == 0 || sLevel == 1 || sLevel == LEVEL_p_mr || sLevel == LEVEL_p_ms) {
					if (afterThisMustStartNewPerikop || pericopeBuffer.size() == 0) {
						PericopeData.Entry entry = new PericopeData.Entry();
						entry.ari = 0; // done later when writing teks so we know which verse this pericope starts from
						entry.block = new PericopeData.Block();
						entry.block.title = title;
						pericopeBuffer.add(entry);
						afterThisMustStartNewPerikop = false;
						System.out.println("$tulis ke pericopeBuffer (new entry) (size now: " + pericopeBuffer.size() + "): " + title);
					} else {
						pericopeBuffer.get(pericopeBuffer.size() - 1).block.title += title;
						System.out.println("$tulis ke pericopeBuffer (append to existing) (size now: " + pericopeBuffer.size() + "): " + title);
					}
				} else if (sLevel == LEVEL_p_r) { // paralel
					if (pericopeBuffer.size() == 0) {
						throw new RuntimeException("paralel found but no perikop on buffer: " + title);
					}

					PericopeData.Entry entry = pericopeBuffer.get(pericopeBuffer.size() - 1);
					entry.block.parallels = parseParallel(title);
				} else if (sLevel == 2) {
					System.out.println("$tulis ke tempat sampah (perikop level 2): " + title);
				} else {
					throw new RuntimeException("sLevel = " + sLevel + " not understood: " + title);
				}

			} else if (target == writeTarget_xref) {
				System.out.println("$tulis ke xref (state=" + xref_state + ") " + book_0 + " " + chapter_1 + " " + verse_1 + ":" + chars);
				int ari = Ari.encode(book_0, chapter_1, verse_1);
				if (xref_state == 0) {
					// compatibility when \x and \x* are written without any \xo or \xt markers.
					// Check chars, if it contains more than just spaces, -, +, or a character, it means it looks like a complete xref entry.
					final String content = chars;
					final int xrefIndex;
					if (content.replaceFirst("[-+a-zA-Z]", "").replaceAll("\\s", "").length() > 0) {
						xrefIndex = xrefDb.addComplete(ari, chars);
					} else {
						xrefIndex = xrefDb.addBegin(ari);
					}
					textDb.append(ari, "@<x" + (xrefIndex + 1) + "@>@/", -1);
				} else if (xref_state == 1) {
					xrefDb.appendText(ari, chars);
				} else if (xref_state == 2) {
					xrefDb.appendText(ari, chars);
				} else {
					throw new RuntimeException("xref_state not supported");
				}

			} else if (target == writeTarget_footnote) {
				System.out.println("$tulis ke footnote (state=" + footnote_state + ") " + book_0 + " " + chapter_1 + " " + verse_1 + ":" + chars);
				final int ari = Ari.encode(book_0, chapter_1, verse_1);
				if (footnote_state == 0) {
					final String content;

					// remove caller at the beginning
					if (chars.matches("[a-zA-Z+-]\\s.*")) {
						// remove that first 2 characters
						content = chars.substring(2);
					} else {
						content = chars;
					}

					final int footnoteIndex = footnoteDb.addBegin(ari);

					if (content.trim().length() != 0) {
						footnoteDb.appendText(ari, content.replace("\n", " "));
					}

					textDb.append(ari, "@<f" + (footnoteIndex + 1) + "@>@/", -1);
				} else if (footnote_state == 2) {
					footnoteDb.appendText(ari, "@9" + chars.replace("\n", " ") + "@7");
				} else if (footnote_state == 1 || footnote_state == 3) {
					footnoteDb.appendText(ari, chars.replace("\n", " "));
				} else {
					throw new RuntimeException("footnote_state not supported");
				}
			}
		}

		void print() {
			for (int i = 0; i < depth; i++) {
				System.out.print('/');
				System.out.print(tree[i]);
			}
			System.out.println();
		}

		private StringBuilder a = new StringBuilder();

		private String address() {
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
	static List<String> parseParallel(String judul) {
		List<String> res = new ArrayList<>();

		judul = judul.trim();
		if (judul.startsWith("(")) judul = judul.substring(1);
		if (judul.endsWith(")")) judul = judul.substring(0, judul.length() - 1);

		String kitab = null;
		String pasal = null;
		String ayat;

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
