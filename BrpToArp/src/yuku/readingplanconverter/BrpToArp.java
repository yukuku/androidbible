package yuku.readingplanconverter;

import yuku.alkitab.yes2.io.RandomOutputStream;
import yuku.bintex.BintexWriter;
import yuku.bintex.ValueMap;
import yuku.readingplanconverter.BrpInput.Brp;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.Map;

public class BrpToArp {
	private static final String INPUT_FILE = System.getProperty("user.dir") + "/BrpToArp/file/wsts.brp";
	private static final String OUTPUT_FILE = System.getProperty("user.dir") + "/BrpToArp/file/wsts.arp";
	private static Brp brp;

	private static final byte ARP_VERSION = 0x01;
	private static final byte[] ARP_HEADER = { 0x52, (byte) 0x8a, 0x61, 0x34, 0x00, (byte) 0xe0, (byte) 0xea, ARP_VERSION};


	public static void main(String[] args) {
		brp = new BrpInput().parse(INPUT_FILE);
		createYrpFile(new File(OUTPUT_FILE));
	}

	private static void createYrpFile(final File file) {
		try {
			RandomAccessFile raf = new RandomAccessFile(file, "rw");
			raf.setLength(0);
			RandomOutputStream output = new RandomOutputStream(raf);
			BintexWriter writer = new BintexWriter(output);

			//Write Header
			writer.writeRaw(ARP_HEADER);

			//Write info
			Map<String, String> infos = brp.infos;
			ValueMap map = new ValueMap();
			map.put("version", 1);
			map.put("shortName", infos.get("shortName"));
			map.put("longName", infos.get("longName"));
			map.put("durationDay", Integer.parseInt(infos.get("durationDay")));
			writer.writeValueSimpleMap(map);

			//Write plans
			List<int[]> plans = brp.plans;
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
