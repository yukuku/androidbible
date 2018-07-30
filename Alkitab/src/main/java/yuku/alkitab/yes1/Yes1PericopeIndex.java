package yuku.alkitab.yes1;

import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.model.PericopeIndex;
import yuku.bintex.BintexReader;

import java.io.IOException;

public class Yes1PericopeIndex extends PericopeIndex {
	public static final String TAG = Yes1PericopeIndex.class.getSimpleName();

	public static Yes1PericopeIndex read(BintexReader in) throws IOException {
		Yes1PericopeIndex pi = new Yes1PericopeIndex();

		int entry_count = in.readInt();

		pi.aris = new int[entry_count];
		pi.offsets = new int[entry_count];

		for (int i = 0; i < entry_count; i++) {
			pi.aris[i] = in.readInt();
			pi.offsets[i] = in.readInt();
		}

		return pi;
	}

	public Yes1PericopeBlock getBlock(BintexReader in, int index) {
		try {
			int offset = offsets[index];
			int currentPos = in.getPos();

			if (currentPos > offset) {
				throw new RuntimeException("currentPos " + currentPos + " > offset " + offset + ", is invalid!");
			}

			in.skip(offset - currentPos);

			return Yes1PericopeBlock.read(in);
		} catch (IOException e) {
			AppLog.e(TAG, "getBlock error", e);

			return null;
		}
	}
}
