package yuku.alkitabconverter.util;

import java.io.File;
import java.util.Scanner;

public class Patch {
	public static final String TAG = Patch.class.getSimpleName();

	public static int patch(String nfi, String nfp, String nfo) {
		new File(nfo).delete();
		try {
			// patch dulu
			Process p = new ProcessBuilder("patch", "-o", nfo, new File(nfi).getCanonicalPath(), nfp).redirectErrorStream(true).start();
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
