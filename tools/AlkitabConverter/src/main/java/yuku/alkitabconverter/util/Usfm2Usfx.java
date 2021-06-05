package yuku.alkitabconverter.util;

import java.io.File;
import java.nio.file.Paths;
import java.util.Scanner;

public class Usfm2Usfx {
	static final String TAG = Usfm2Usfx.class.getSimpleName();

	public static int convert(String nfi, String nfo) {

		final String nfiString = Paths.get(Paths.get("").toAbsolutePath().toString()).relativize(Paths.get(nfi)).toString();
		final String nfoString = Paths.get(Paths.get("").toAbsolutePath().toString()).relativize(Paths.get(nfo)).toString();

		new File(nfo).delete();
		try {
			// patch dulu
			Process p = new ProcessBuilder("mono", new File("./prog/wordsend/usfm2usfx.exe").getAbsolutePath(), "-o", nfoString, nfiString).redirectErrorStream(true).start();
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
