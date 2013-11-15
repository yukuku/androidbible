package yuku.readingplanconverter;

import yuku.bintex.BintexWriter;
import yuku.bintex.ValueMap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class RpaToRpb {
	private static final String FILE_NAME = "blueletter_one_year_historical";
	private static final String INPUT_FILE = System.getProperty("user.dir") + "/RpaToRpb/file/" + FILE_NAME + ".rpa";
	private static final String OUTPUT_FILE = System.getProperty("user.dir") + "/RpaToRpb/file/" + FILE_NAME + ".rpb";
	private static RpaInput.Rpa rpa;

	private static final byte RPB_VERSION = 0x01;
	private static final byte[] RPB_HEADER = { 0x52, (byte) 0x8a, 0x61, 0x34, 0x00, (byte) 0xe0, (byte) 0xea, RPB_VERSION};


	public static void main(String[] args) {
		rpa = new RpaInput().parse(INPUT_FILE);
		createRpbFile(new File(OUTPUT_FILE));
	}

	private static void createRpbFile(final File file) {
		try {
			BintexWriter writer = new BintexWriter(new FileOutputStream(file));

			//Write Header
			writer.writeRaw(RPB_HEADER);

			//Write info
			Map<String, String> infos = rpa.infos;
			ValueMap map = new ValueMap();
			map.put("name", FILE_NAME);
			map.put("title", infos.get("title"));
			map.put("description", infos.get("description"));
			map.put("duration", Integer.parseInt(infos.get("duration")));
			writer.writeValueSimpleMap(map);

			//Write data
			List<int[]> plans = rpa.plans;
			for (int[] aris : plans) {
				writer.writeUint8(aris.length);
				for (int ari : aris) {
					writer.writeInt(ari);
				}
			}

			//Write footer
			writer.writeUint8(0);

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
