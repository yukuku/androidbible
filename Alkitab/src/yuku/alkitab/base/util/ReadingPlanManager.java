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

	private static final byte[] ARP_HEADER = { 0x52, (byte) 0x8a, 0x61, 0x34, 0x00, (byte) 0xe0, (byte) 0xea};

	public static long copyReadingPlanToDb(final int resId) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buffer = new byte[256];

		String title = "";
		InputStream stream = new RawResourceRandomInputStream(resId);
		BintexReader reader = new BintexReader(stream);
		byte[] headers = new byte[8];

		try {
			reader.readRaw(headers);

			ValueMap map = reader.readValueSimpleMap();
			for (Map.Entry<String, Object> entry : map.entrySet()) {
				if (entry.getKey().equals("title")) {
					title = (String) entry.getValue();
				}
			}
			stream.close();

			InputStream is = new RawResourceRandomInputStream(resId);
			while (is.read(buffer) != -1) {
				baos.write(buffer);
			}
			baos.close();

		} catch (IOException e) {
			e.printStackTrace();
		}

		ReadingPlanBinary readingPlanBinary = new ReadingPlanBinary();
		readingPlanBinary.title = title;
		readingPlanBinary.startDate = new Date().getTime();
		readingPlanBinary.binaryReadingPlan = baos.toByteArray();
		return S.getDb().insertReadingPlan(readingPlanBinary);
	}

	public static void createReadingPlanProgress(final int readingPlanId, final int dayNumber, final int readingSequence) {
		int readingCode = (dayNumber & 0xff) << 8 | (readingSequence & 0xff);
		S.getDb().insertReadingPlanProgress(readingPlanId, readingCode);
	}

	public static ReadingPlan readVersion1(InputStream inputStream) {
		ReadingPlan readingPlan = new ReadingPlan();
		try {
			BintexReader reader = new BintexReader(inputStream);
			byte[] headers = new byte[8];
			reader.readRaw(headers);
			for (int i = 0; i < 7; i++) {
				if (ARP_HEADER[i] != headers[i]) {
					return null;
				}
			}
			if (headers[7] != 1) {
				Log.d(TAG, "It is not version 1.");
				return null;
			}
			readingPlan.version = headers[7];

			ValueMap map = reader.readValueSimpleMap();
			for (Map.Entry<String, Object> entry : map.entrySet()) {
				final String key = entry.getKey();
				if (key.equals("title")) {
					readingPlan.title = (String) entry.getValue();
				} else if (key.equals("description")) {
					readingPlan.description = (String) entry.getValue();
				} else if (key.equals("duration")) {
					readingPlan.duration = (Integer) entry.getValue();
				} else {
					Log.d(TAG, "Info not recognized: " + key);
					return null;
				}
			}

			int counter = 0;
			while (counter < readingPlan.duration) {
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

	public static class ReadingPlanBinary {
		public String title;
		public long startDate;
		public byte[] binaryReadingPlan;
	}
}
