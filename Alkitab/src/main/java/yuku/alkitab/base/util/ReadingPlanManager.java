package yuku.alkitab.base.util;

import android.util.Log;
import yuku.alkitab.base.S;
import yuku.alkitab.base.model.ReadingPlan;
import yuku.alkitab.util.IntArrayList;
import yuku.bintex.BintexReader;
import yuku.bintex.ValueMap;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

public class ReadingPlanManager {
	public static final String TAG = ReadingPlanManager.class.getSimpleName();

	private static final byte[] RPB_HEADER = {0x52, (byte) 0x8a, 0x61, 0x34, 0x00, (byte) 0xe0, (byte) 0xea};

	public static long insertReadingPlanToDb(final byte[] data) {
		final ReadingPlan.ReadingPlanInfo info = new ReadingPlan.ReadingPlanInfo();

		try {
			// check the file has correct header and infos
			final BintexReader reader = new BintexReader(new ByteArrayInputStream(data));
			final boolean ok = readInfo(info, reader);
			reader.close();

			if (!ok) {
				Log.e(TAG, "Error parsing reading plan data");
				return 0;
			}

			info.startTime = new Date().getTime();

			return S.getDb().insertReadingPlan(info, data);
		} catch (IOException e) {
			Log.e(TAG, "Error reading reading plan, should not happen", e);
			return 0;
		}
	}

	public static void updateReadingPlanProgress(final long readingPlanId, final int dayNumber, final int readingSequence, final boolean checked) {
		int readingCode = toReadingCode(dayNumber, readingSequence);

		if (checked) {
			IntArrayList ids = S.getDb().getReadingPlanProgressId(readingPlanId, readingCode);
			if (ids.size() > 0) {
				S.getDb().deleteReadingPlanProgress(readingPlanId, readingCode);
			}
			S.getDb().insertReadingPlanProgress(readingPlanId, readingCode, new Date().getTime());
		} else {
			S.getDb().deleteReadingPlanProgress(readingPlanId, readingCode);
		}
	}

	public static ReadingPlan readVersion1(InputStream inputStream) {
		ReadingPlan readingPlan = new ReadingPlan();
		try {
			final BintexReader reader = new BintexReader(inputStream);
			try {
				if (!readInfo(readingPlan.info, reader)) return null;
				if (readingPlan.info.version != 1) return null;

				int[][] dailyVerses = new int[readingPlan.info.duration][];
				int counter = 0;

				while (counter < readingPlan.info.duration) {
					int count = reader.readUint8();
					if (count == -1) {
						Log.d(TAG, "Error reading.");
						return null;
					}

					int[] aris = new int[count];
					for (int j = 0; j < count; j++) {
						aris[j] = reader.readInt();
					}
					dailyVerses[counter] = aris;
					counter++;
				}

				readingPlan.dailyVerses = dailyVerses;
			} finally {
				reader.close();
			}

			if (reader.readUint8() != 0) {
				Log.d(TAG, "No footer.");
				return null;
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

		return readingPlan;
	}

	/**
	 * @return false if reading plan data is not in a valid format.
	 */
	public static boolean readInfo(final ReadingPlan.ReadingPlanInfo readingPlanInfo, final BintexReader reader) throws IOException {
		byte[] headers = new byte[8];
		reader.readRaw(headers);
		for (int i = 0; i < 7; i++) {
			if (RPB_HEADER[i] != headers[i]) {
				return false;
			}
		}
		readingPlanInfo.version = headers[7];

		ValueMap map = reader.readValueSimpleMap();
		readingPlanInfo.name = map.getString("name");
		readingPlanInfo.title = map.getString("title");
		readingPlanInfo.description = map.getString("description");
		readingPlanInfo.duration = map.getInt("duration");

		return true;
	}

	public static int toReadingCode(int dayNumber, int readingSequence) {
		return dayNumber << 8 | readingSequence;
	}

	public static int toSequence(int readingCode) {
		return (readingCode & 0x000000ff);
	}

	public static IntArrayList filterReadingCodesByDayStartEnd(IntArrayList readingCodes, int dayStart, int dayEnd) {
		IntArrayList res = new IntArrayList();
		int start = dayStart << 8;
		int end = (dayEnd + 1) << 8;
		for (int i = 0; i < readingCodes.size(); i++) {
			final int readingCode = readingCodes.get(i);
			if (readingCode >= start && readingCode < end) {
				res.add(readingCode);
			}
		}
		return res;
	}

	public static void writeReadMarksByDay(IntArrayList readingCodes, boolean[] readMarks, int dayNumber) {
		readingCodes = filterReadingCodesByDayStartEnd(readingCodes, dayNumber, dayNumber);
		for (int i = 0; i < readingCodes.size(); i++) {
			final int readingCode = readingCodes.get(i);
			final int sequence = toSequence(readingCode);
			readMarks[sequence] = true;
		}
	}

}
