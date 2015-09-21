package yuku.alkitabconverter.util;

import java.io.File;
import java.util.Scanner;

public class Usfm2Usfx {
	public static final String TAG = Usfm2Usfx.class.getSimpleName();

	public static int convert(String nfi, String nfo) {
		new File(nfo).delete();
		try {
			// patch dulu
			Process p = new ProcessBuilder("mono", new File("./prog/wordsend/usfm2usfx.exe").getAbsolutePath(), "-o", nfo, nfi).redirectErrorStream(true).start();
			Scanner sc = new Scanner(p.getInputStream());
			while (sc.hasNextLine()) {
				System.out.println(sc.nextLine());
			}
			sc.close();
			return p.waitFor();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
