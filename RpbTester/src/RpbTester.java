import yuku.bintex.BintexReader;
import yuku.bintex.ValueMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RpbTester {
	private static final String INPUT_FILE = System.getProperty("user.dir") + "/RpbTester/file/rp_esv_chronological.rpb";
	private static final byte[] RPB_HEADER = { 0x52, (byte) 0x8a, 0x61, 0x34, 0x00, (byte) 0xe0, (byte) 0xea};

	public static void main(String[] args) {
		read();
	}

	private static void read() {
		try {
			InputStream is = new FileInputStream(new File(INPUT_FILE));
			BintexReader reader = new BintexReader(is);
			byte[] headers = new byte[8];
			reader.readRaw(headers);

			for (int i = 0; i < 7; i++) {
				if (RPB_HEADER[i] != headers[i]) {
					throw new IOException();
				}
				System.out.print((char)headers[i]);
			}
			if (headers[7] == 1) {
				System.out.println("Ini versi 1");
			}

			ValueMap map = reader.readValueSimpleMap();
			for (Map.Entry<String, Object> entry : map.entrySet()) {
				System.out.println(entry.getKey() + " " + entry.getValue());
			}

			int days =  (Integer) map.get("duration");
			List<int[]> plans = new ArrayList<int[]>();

			int counter = 0;
			while (counter < days) {
				int count = reader.readUint8();
				if (count == -1) {
					break;
				}
				counter++;

				int[] aris = new int[count];
				for (int j = 0; j < count; j++) {
					aris[j] = reader.readInt();
				}
				plans.add(aris);
			}
			if (reader.readUint8() != 0) {
				System.out.println("Kok ga ada footernya?");
			}

			System.out.println("Jumlah hari = " + counter);
			System.out.println("Jumlah plan = " + plans.size());
			for (int[] aris : plans) {
				for (int ari : aris) {
					System.out.print(ari + " ");
				}
				System.out.println("");
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
