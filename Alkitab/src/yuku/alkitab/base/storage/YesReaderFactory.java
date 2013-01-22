package yuku.alkitab.base.storage;

import android.util.Log;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

import yuku.alkitab.yes1.Yes1Reader;
import yuku.alkitab.yes2.Yes2Reader;
import yuku.alkitab.yes2.io.RandomInputStream;

public class YesReaderFactory {
	public static final String TAG = YesReaderFactory.class.getSimpleName();
	
	/**
	 * @return A {@link Yes1Reader}, {@link Yes2Reader}, or null if there is any error.
	 */
	public static BibleReader createYesReader(String filename) {
		try {
			RandomAccessFile f = new RandomAccessFile(filename, "r");
			byte[] header = new byte[8];
			f.read(header);
			
			if (header[0] != (byte) 0x98 
			|| header[1] != (byte) 0x58 
			|| header[2] != (byte) 0x0d 
			|| header[3] != (byte) 0x0a 
			|| header[4] != (byte) 0x00 
			|| header[5] != (byte) 0x5d 
			|| header[6] != (byte) 0xe0) {
				Log.e(TAG, "Yes file has not a correct header. Header is: " + Arrays.toString(header));
				return null;
			}
			
			if (header[7] == 0x01) { // VERSION 1 YES
				return new Yes1Reader(f);
			} else if (header[7] == 0x02) { // VERSION 2 YES
				return new Yes2Reader(new RandomInputStream(f));
			} else {
				Log.e(TAG, "Yes file version unsupported: " + header[7]);
				return null;
			}
		} catch (IOException e) {
			Log.e(TAG, "@@createYesReader io exception", e);
			return null;
		}
	}
}
