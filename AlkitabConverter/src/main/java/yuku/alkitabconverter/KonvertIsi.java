package yuku.alkitabconverter;

import yuku.bintex.BintexWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Scanner;

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

			label:
			while (true) {
				String key = sc.next();

				writer.writeShortString(key);

				switch (key) {
					case "npasal":  // value: int
						npasal = sc.nextInt();

						writer.writeInt(npasal);
						break;
					case "nayat":  // value: uint8[]
						for (int i = 0; i < npasal; i++) {
							writer.writeUint8(sc.nextInt());
						}
						break;
					case "pasal_offset":  // value: int[]
						for (int i = 0; i < npasal; i++) {
							writer.writeInt(sc.nextInt());
						}
						break;
					case "uda":  // value: ga ada
						break label;
					default:  // value: String
						String value = sc.next();
						if (key.equals("judul") || key.equals("nama")) {
							value = value.replace('_', ' ');
						}
						writer.writeShortString(value);
						break;
				}
			}
			
		}
		
		writer.close();
	}
}
