package yuku.alkitab.base.util;

import android.util.Log;
import yuku.alkitab.base.model.Ari;

import java.util.regex.Pattern;

public class TargetDecoder {
	public static final String TAG = TargetDecoder.class.getSimpleName();

	static final Pattern rangeSplitter = Pattern.compile(",");
	static final Pattern startEndSplitter = Pattern.compile("-");

	/**
	 * Returns ari ranges for encoded target. Targets can be encoded using any of the following (using examples):
	 * a:[ari start]-[ari end],[ari single verse]
	 * o:[osis start]-[osis end],[osis single verse]
	 * lid:[lid start]-[lid end],[lid single verse]
	 *
	 * @return [start, end, ..., start, end] or null if can't decode.
	 */
	public static IntArrayList decode(final String encoded) {
		final int type; // 1=osis 2=ari 3=lid
		final String rangesJoined;
		if (encoded.startsWith("o:")) { // osis ref
			type = 1;
			rangesJoined = encoded.substring(2);
		} else if (encoded.startsWith("a:")) { // ari ref
			type = 2;
			rangesJoined = encoded.substring(2);
		} else if (encoded.startsWith("lid:")) { // lid ref
			type = 3;
			rangesJoined = encoded.substring(4);
		} else {
			Log.e(TAG, "Unknown target format: " + encoded);
			return null;
		}

		final String[] ranges = rangeSplitter.split(rangesJoined, -1);
		final IntArrayList res = new IntArrayList(ranges.length * 2);
		for (final String range : ranges) {
			final String[] startEnd = startEndSplitter.split(range, 2);
			if (startEnd.length == 1) {
				final int ari = decodeSingle(type, startEnd[0]);
				if (ari != 0) {
					res.add(ari);
					res.add(ari);
				}
			} else {
				final int ariStart = decodeSingle(type, startEnd[0]);
				final int ariEnd = decodeSingle(type, startEnd[1]);
				if (ariStart != 0 && ariEnd != 0) {
					res.add(ariStart);
					res.add(ariEnd);
				}
			}
		}

		return res;
	}

	private static int decodeSingle(final int type, final String single) {
		if (type == 1) {
			return OsisBookNames.osisToAri(single);
		} else if (type == 2) {
			return Ari.parseInt(single, 0);
		} else if (type == 3) {
			return LidToAri.lidToAri(Ari.parseInt(single, 0));
		}
		return 0;
	}
}
