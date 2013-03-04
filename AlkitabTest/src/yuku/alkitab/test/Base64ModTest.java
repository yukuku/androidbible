package yuku.alkitab.test;

import android.test.AndroidTestCase;

import java.util.Random;

import yuku.alkitab.base.util.Base64Mod;

public class Base64ModTest extends AndroidTestCase {

	void testCodec(int value, int len) {
		if (len == 1) {
			char[] encoded = Base64Mod.encodeToOneChar(value);
			assertEquals(value, Base64Mod.decodeFromChars(encoded, 0, len));
			
			char[] buf = new char[100];
			System.arraycopy(Base64Mod.encodeToOneChar(value), 0, buf, 51, len);
			assertEquals(value, Base64Mod.decodeFromChars(buf, 51, len));
		} else if (len == 2) {
			char[] encoded = Base64Mod.encodeToTwoChars(value);
			assertEquals(value, Base64Mod.decodeFromChars(encoded, 0, len));
			
			char[] buf = new char[100];
			System.arraycopy(Base64Mod.encodeToTwoChars(value), 0, buf, 51, len);
			assertEquals(value, Base64Mod.decodeFromChars(buf, 51, len));
		} else if (len == 3) {
			char[] encoded = Base64Mod.encodeToThreeChars(value);
			assertEquals(value, Base64Mod.decodeFromChars(encoded, 0, len));
			
			char[] buf = new char[100];
			System.arraycopy(Base64Mod.encodeToThreeChars(value), 0, buf, 51, len);
			assertEquals(value, Base64Mod.decodeFromChars(buf, 51, len));
		}
	}

	public void testCodec1() throws Throwable {
		Random r = new Random(100019813912913L);
		
		testCodec(0, 1);
		testCodec(63, 1);
		for (int i = 0; i < 10; i++) {
			testCodec(r.nextInt(63), 1);
		}
		testCodec(0, 2);
		testCodec(4095, 2);
		for (int i = 0; i < 100; i++) {
			testCodec(r.nextInt(4095), 2);
		}
		testCodec(0, 3);
		testCodec(262143, 3);
		for (int i = 0; i < 1000; i++) {
			testCodec(r.nextInt(262143), 3);
		}
	}
}
