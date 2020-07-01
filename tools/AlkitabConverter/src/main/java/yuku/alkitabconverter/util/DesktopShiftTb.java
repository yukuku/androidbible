package yuku.alkitabconverter.util;

import yuku.bintex.BintexReader;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class DesktopShiftTb {
	static final String TAG = DesktopShiftTb.class.getSimpleName();

	/**
	 * Map from ari of tb to ari of kjv, but the most significant byte is used for flag
	 */
	static Map<Integer, Integer> tbToFkjv = new HashMap<>(2048);

	static {
		try {
			BintexReader br = new BintexReader(DesktopShiftTb.class.getResourceAsStream("shift_tb_bt.bt"));
			int n = br.readInt();

			int from = 0;
			int to = 0;
			int flag = 0;
			for (int i = 0; i < n; i++) {
				int mode = br.readUint8();
				if (mode == 2) { // both from and to increase by 1 and flag is 0
					from++;
					to++;
					flag = 0;
				} else if (mode == 1) { // absolute
					from = br.readInt();
					to = br.readInt();
					flag = br.readUint8();
				} else if (mode == 0) { // relative with flag
					from += br.readUint8();
					to += br.readUint8();
					flag = br.readUint8();
				}

				tbToFkjv.put(from, (flag << 24) | to);
			}

			br.close();
		} catch (IOException e) {
			throw new RuntimeException("Failed to read shift_tb_bt");
		}
	}

	/**
	 * Convert tb ari to kjv ari.
	 * Impl note: only kjv ari with flag 0x0 or 0x1. With other flags, the original ari is returned.
	 */
	public static int shiftFromTb(int ari) {
		if (!tbToFkjv.containsKey(ari)) return ari; // original ari
		int fkjv = tbToFkjv.get(ari);

		int flag = fkjv >> 24;
		if (flag != 0 && flag != 1) return ari; // original ari

		return fkjv & 0xffffff;
	}

	public static IntArrayList shiftFromTb(IntArrayList aris) {
		if (aris == null) return null;
		IntArrayList res = new IntArrayList(aris.size());

		for (int i = 0, len = aris.size(); i < len; i++) {
			res.add(shiftFromTb(aris.get(i)));
		}

		return res;
	}
}
