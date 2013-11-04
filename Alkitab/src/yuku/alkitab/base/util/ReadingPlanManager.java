package yuku.alkitab.base.util;

import android.util.Log;
import yuku.alkitab.base.S;
import yuku.alkitab.base.model.ReadingPlan;
import yuku.alkitab.yes2.io.RawResourceRandomInputStream;
import yuku.bintex.BintexReader;
import yuku.bintex.ValueMap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Map;

public class ReadingPlanManager {
	public static final String TAG = ReadingPlanManager.class.getSimpleName();

	private static final byte[] ARP_HEADER = {0x52, (byte) 0x8a, 0x61, 0x34, 0x00, (byte) 0xe0, (byte) 0xea};

	public static long copyReadingPlanToDb(final int resId) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buffer = new byte[256];

		InputStream stream = new RawResourceRandomInputStream(resId);
		BintexReader reader = new BintexReader(stream);
		ReadingPlanBinary readingPlanBinary = new ReadingPlanBinary();

		try {
			readInfo(readingPlanBinary.info, reader);
			stream.close();

			InputStream is = new RawResourceRandomInputStream(resId);
			while (is.read(buffer) != -1) {
				baos.write(buffer);
			}
			baos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		readingPlanBinary.info.startDate = new Date().getTime();
		readingPlanBinary.binaryReadingPlan = baos.toByteArray();
		return S.getDb().insertReadingPlan(readingPlanBinary);
	}

	public static void updateReadingPlanProgress(final long readingPlanId, final int dayNumber, final int readingSequence, final boolean ticked) {
		int readingCode = ReadingPlan.ReadingPlanProgress.toReadingCode(dayNumber, readingSequence);
		if (ticked) {
			S.getDb().insertReadingPlanProgress(readingPlanId, readingCode);
		} else {
			S.getDb().deleteReadingPlanProgress(readingPlanId, readingCode);
		}
	}

	public static ReadingPlan readVersion1(InputStream inputStream) {
		ReadingPlan readingPlan = new ReadingPlan();
		try {
			BintexReader reader = new BintexReader(inputStream);
			if (readInfo(readingPlan.info, reader)) return null;
			if (readingPlan.info.version != 1) return null;

			int counter = 0;
			while (counter < readingPlan.info.duration) {
				int count = reader.readUint8();
				if (count == -1) {
					Log.d(TAG, "Error reading.");
					return null;
				}
				counter++;

				int[] aris = new int[count];
				for (int j = 0; j < count; j++) {
					aris[j] = reader.readInt();
				}
				readingPlan.dailyVerses.add(aris);
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

	public static boolean readInfo(final ReadingPlan.ReadingPlanInfo readingPlanInfo, final BintexReader reader) throws IOException {
		byte[] headers = new byte[8];
		reader.readRaw(headers);
		for (int i = 0; i < 7; i++) {
			if (ARP_HEADER[i] != headers[i]) {
				return true;
			}
		}
		readingPlanInfo.version = headers[7];

		ValueMap map = reader.readValueSimpleMap();
		for (Map.Entry<String, Object> entry : map.entrySet()) {
			final String key = entry.getKey();
			if (key.equals("title")) {
				readingPlanInfo.title = (String) entry.getValue();
			} else if (key.equals("description")) {
				readingPlanInfo.description = (String) entry.getValue();
			} else if (key.equals("duration")) {
				readingPlanInfo.duration = (Integer) entry.getValue();
			} else {
				Log.d(TAG, "Info not recognized: " + key);
				return true;
			}
		}
		return false;
	}

	public static class ReadingPlanBinary {
		public ReadingPlan.ReadingPlanInfo info = new ReadingPlan.ReadingPlanInfo();
		public byte[] binaryReadingPlan;
	}

	public static void writeReadMarksByDay(IntArrayList readingCodes, boolean[] readMarks, int dayNumber) {
		int start = dayNumber << 8;
		int end = (dayNumber + 1) << 8;
		for (int i = 0; i < readingCodes.size(); i++) {
			final int readingCode = readingCodes.get(i);
			if (readingCode >= start && readingCode < end) {
				final int sequence = ReadingPlan.ReadingPlanProgress.toSequence(readingCode) * 2;
				readMarks[sequence] = true;
				readMarks[sequence + 1] = true;
			}
		}
	}
}
