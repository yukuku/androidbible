package yuku.alkitabconverter.util;

import java.io.File;
import java.util.Scanner;

public class FormatXml {
	public static final String TAG = FormatXml.class.getSimpleName();

	public static int format(String nfi, String nfo) {
		new File(nfo).delete();
		try {
			// patch dulu
			Process p = new ProcessBuilder("xmllint", "--format", "-o", nfo, nfi).redirectErrorStream(true).start();
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
