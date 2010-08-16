package yuku.alkitab;

import java.io.*;
import java.util.Scanner;

import yuku.bintex.BintexWriter;

public class KonvertEdisi {
	public static void main(String[] args) throws Exception {
		new KonvertEdisi().convert("../Alkitab/publikasi/edisi_index.txt", "../Alkitab/res/raw/edisi_index_bt.bt");
	}
	
	int bolong = 0;

	private void convert(String nfi, String nfo) throws Exception {
		Scanner sc = new Scanner(new File(nfi));
		BintexWriter out = new BintexWriter(new FileOutputStream(nfo));

		while (sc.hasNext()) {
			String s = sc.next();
			
			if (s.equals("Edisi")) {
				out.writeShortString(s);
			} else if (s.equals("nama") || s.equals("pembaca") || s.equals("url")) {
				out.writeShortString(s);
				out.writeShortString(sc.next()); // sstring
			} else if (s.equals("nkitab") || s.equals("perikopAda")) {
				out.writeShortString(s);
				out.writeInt(sc.nextInt()); // int
			} else if (s.equals("judul")) {
				out.writeShortString(s);
				out.writeShortString(sc.next().replace('_', ' '));
			} else if (s.equals("end")) {
				out.writeShortString("end");
			} else {
				throw new RuntimeException("apaan nih? " + s);
			}
		}
		
		out.close();
	}
}
