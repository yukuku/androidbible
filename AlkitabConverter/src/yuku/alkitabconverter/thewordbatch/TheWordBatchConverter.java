package yuku.alkitabconverter.thewordbatch;

import yuku.alkitab.util.Ari;
import yuku.alkitabconverter.util.KjvUtils;
import yuku.alkitabconverter.util.Rec;
import yuku.alkitabconverter.util.TextDb;
import yuku.alkitabconverter.yes_common.Yes2Common;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

public class TheWordBatchConverter {
	static String DATA_DIR = "/Users/yuku/j/operasi/theword";
	
	List<String> appConfigEntries = new ArrayList<String>();
	
	public static void main(String[] args) throws Exception {
		new TheWordBatchConverter().convertAll();
	}

	void convertAll() throws Exception {
		// look for directories starting with "ready-"
		File[] superdirs = new File(DATA_DIR).listFiles(new FileFilter() {
			@Override public boolean accept(File f) {
				return f.isDirectory() && f.getName().startsWith("ready-");
			}
		});
	
		// look for all directories under them but only those with corresponding ".properties" file
		for (final File superdir: superdirs) {
			File[] dirs = superdir.listFiles(new FileFilter() {
				@Override public boolean accept(File f) {
					return f.isDirectory() && new File(superdir, f.getName() + ".properties").exists();
				}
			});
			
			for (File dir: dirs) {
				processVersion(superdir, dir);
			}
		}
		
		Collections.sort(appConfigEntries);
		
		for (String s: appConfigEntries) {
			System.out.println(s);
		}
	}

	void processVersion(File superdir, File dir) throws Exception {
		// look for text: ending in .ont
		File textFile = null;
		
		for (File f: dir.listFiles()) {
			if (f.getName().endsWith(".ont")) {
				textFile = f;
				break;
			}
		}
		
		if (textFile == null) {
			throw new RuntimeException("ont file not found in dir " + dir);
		}
		
		// read properties file
		Yes2Common.VersionInfo versionInfo = new Yes2Common.VersionInfo();

		InputStreamReader propInput = new InputStreamReader(new FileInputStream(new File(superdir, dir.getName() + ".properties")), "utf-8");			Properties prop = new Properties();
		prop.load(propInput);
		versionInfo.locale = prop.getProperty("versionInfo.locale");
		versionInfo.shortName = prop.getProperty("versionInfo.shortName");
		versionInfo.longName = prop.getProperty("versionInfo.longName");
		versionInfo.description = prop.getProperty("versionInfo.description");
		String outputName = prop.getProperty("output.name");
		propInput.close();
		
		// read booknames file
		Scanner sc = new Scanner(new File(superdir, dir.getName() + ".booknames.txt"));
		List<String> bookNames = new ArrayList<String>();
		while (sc.hasNextLine()) {
			bookNames.add(sc.nextLine());
		}
		versionInfo.setBookNamesAndAbbreviations(bookNames, null);
		sc.close();

		TextDb textDb = processTextFile(textFile);

		PrintStream ps = new PrintStream(new File("/tmp/" + outputName + ".txt"));
		textDb.dump(ps);
		ps.close();

		Yes2Common.createYesFile(new File("/tmp", outputName + ".yes"), versionInfo, textDb, null, true);
		
		appConfigEntries.add(String.format("<preset locale=%-6s shortName=%-9s longName=%s filename_preset=%s url=%s />", q(versionInfo.locale), q(versionInfo.shortName), q(versionInfo.longName), q(outputName + ".yes"), q("http://alkitab-host.appspot.com/addon/yes2/" + outputName + "--1.yes.gz")));
		
		System.out.println("Processing finished, total verses: " + textDb.size());
	}

	String q(String s) {
		return '"' + s.replace("\"", "&quot;") + '"';
	}

	TextDb processTextFile(File textFile) throws Exception {
		List<Rec> recs = new ArrayList<Rec>();

		Scanner sc = new Scanner(textFile, "utf-8");
		int lid = 0;
		while (sc.hasNextLine()) {
			String line = sc.nextLine();
			line = line.trim();

			lid++;
			int ari = KjvUtils.lidToAri(lid);

			Rec rec = new Rec();
			rec.book_1 = Ari.toBook(ari) + 1;
			rec.chapter_1 = Ari.toChapter(ari);
			rec.verse_1 = Ari.toVerse(ari);
			rec.text = line;

			recs.add(rec);

			if (lid == 31102) {
				break; // done, don't continue
			}
		}
		sc.close();

		TextDb textDb = new TextDb();
		
		for (Rec rec: recs) {
			// post-process
			String verseText = rec.text;
			verseText = postProcessText(verseText);

			textDb.append(rec.book_1 - 1, rec.chapter_1, rec.verse_1, verseText, -1);
		}
		
		return textDb;
	}

	private static String postProcessText(String verseText) {
		{ // look for <I> (italics)
			if (verseText.contains("<I>") && verseText.contains("</I>")) {
				verseText = verseText.replaceAll("<I>", "@9").replaceAll("</I>", "@7");
			} else if (verseText.contains("<I>") ^ verseText.contains("</I>")) {
				throw new RuntimeException("Verse contains <I> or </I> but no corresponding tag");
			}
		}
		{ // look for start para marker ¶
			if (verseText.contains("\u00B6")) {
				verseText = verseText.replaceAll("\u00B6 ?", "@^");
			}
		}
		return verseText;
	}
}
