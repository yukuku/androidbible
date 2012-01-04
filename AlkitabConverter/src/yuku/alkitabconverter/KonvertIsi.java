package yuku.alkitabconverter;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Scanner;

import yuku.bintex.BintexWriter;

public class KonvertIsi {
	public static void main(String[] args) throws Exception {
		String input_index = args[0]; // contoh: "bahan/en-kjv-thml/kjv_index.txt"
		String output_index = args[1]; // contoh: "bahan/en-kjv-thml/kjv_raw/kjv_index_bt.bt"
		
		new KonvertIsi().convert(input_index, output_index);
	}

	private void convert(String nfi, String nfo) throws Exception {
		Scanner sc = new Scanner(new File(nfi), "utf-8");
		FileOutputStream os = new FileOutputStream(nfo);
		
		BintexWriter writer = new BintexWriter(os);
		
		while (sc.hasNext()) {
			String kitab = sc.next();
			
			writer.writeShortString(kitab);
			int npasal = 0;
			
			while (true) {
				String key = sc.next();
				
				writer.writeShortString(key);
				
				if (key.equals("npasal")) { // value: int
					npasal = sc.nextInt();
					
					writer.writeInt(npasal);
				} else if (key.equals("nayat")) { // value: uint8[]
					for (int i = 0; i < npasal; i++) {
						writer.writeUint8(sc.nextInt());
					}
				} else if (key.equals("pasal_offset")) { // value: int[]
					for (int i = 0; i < npasal; i++) {
						writer.writeInt(sc.nextInt());
					}
				} else if (key.equals("uda")) { // value: ga ada
					break;
				} else { // value: String
					String value = sc.next();
					if (key.equals("judul") || key.equals("nama")) {
						value = value.replace('_', ' ');
					}
					writer.writeShortString(value);
				}
			}
			
		}
		
		writer.close();
	}
}
