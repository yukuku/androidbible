package yuku.readingplanconverter;

import yuku.alkitab.yes2.io.RandomOutputStream;
import yuku.bintex.BintexWriter;
import yuku.bintex.ValueMap;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.Map;

public class RpaToRpb {
	private static final String INPUT_FILE = System.getProperty("user.dir") + "/RpaToRpb/file/wsts_ver2.rpa";
	private static final String OUTPUT_FILE = System.getProperty("user.dir") + "/RpaToRpb/file/wsts_ver2.rpb";
	private static RpaInput.Rpa rpa;

	private static final byte ARP_VERSION = 0x01;
	private static final byte[] ARP_HEADER = { 0x52, (byte) 0x8a, 0x61, 0x34, 0x00, (byte) 0xe0, (byte) 0xea, ARP_VERSION};


	public static void main(String[] args) {
		rpa = new RpaInput().parse(INPUT_FILE);
		createRpbFile(new File(OUTPUT_FILE));
	}

	private static void createRpbFile(final File file) {
		try {
			RandomAccessFile raf = new RandomAccessFile(file, "rw");
			raf.setLength(0);
			RandomOutputStream output = new RandomOutputStream(raf);
			BintexWriter writer = new BintexWriter(output);

			//Write Header
			writer.writeRaw(ARP_HEADER);

			//Write info
			Map<String, String> infos = rpa.infos;
			ValueMap map = new ValueMap();
			map.put("title", infos.get("title"));
			map.put("description", infos.get("description"));
			map.put("duration", Integer.parseInt(infos.get("duration")));
			writer.writeValueSimpleMap(map);

			//Write plans
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
