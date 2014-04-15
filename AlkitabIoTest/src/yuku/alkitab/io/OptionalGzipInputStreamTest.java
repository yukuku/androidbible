package yuku.alkitab.io;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class OptionalGzipInputStreamTest {
	byte[] zero;
	byte[] one;
	byte[] onefakegzip;
	byte[] twonon;
	byte[] twogzip;
	byte[] realgzip;

	void init() throws Exception {
		zero = new byte[0];
		one = new byte[] {0x01};
		onefakegzip = new byte[] {0x1f};
		twonon = new byte[] {0x1f, 0x55};
		twogzip = new byte[] {0x1f, (byte) 0x8b};
		realgzip = new byte[] {
		0x1f, (byte) 0x8b, 0x08, 0x00, 0x3b, (byte) 0xaa, 0x46, 0x53, 0x00, 0x03, (byte) 0xe3, 0x02, 0x00, (byte) 0x93, 0x06, (byte) 0xd7,
		0x32, 0x01, 0x00, 0x00, 0x00};
	}

	public void testReadSingle(byte[] bytes) throws Exception {
		final InputStream orig = new ByteArrayInputStream(bytes.clone());
		final OptionalGzipInputStream.PeekInputStream peek = new OptionalGzipInputStream.PeekInputStream(new ByteArrayInputStream(bytes.clone()));

		assertEquals(orig.read(), peek.read());
		assertEquals(orig.read(), peek.read());
		assertEquals(orig.read(), peek.read());
	}

	public void testPeek(byte[] bytes) throws Exception {
		final OptionalGzipInputStream.PeekInputStream peek = new OptionalGzipInputStream.PeekInputStream(new ByteArrayInputStream(bytes.clone()));

		if (bytes.length >= 1) {
			assertEquals(peek.getFirstByte(), bytes[0] & 0xff);
		}
		if (bytes.length >= 2) {
			assertEquals(peek.getSecondByte(), bytes[1] & 0xff);
		}
	}

	public void testReadBuf(byte[] bytes) throws Exception {
		final InputStream orig = new ByteArrayInputStream(bytes.clone());
		final OptionalGzipInputStream.PeekInputStream peek = new OptionalGzipInputStream.PeekInputStream(new ByteArrayInputStream(bytes.clone()));

		byte[] buf1 = new byte[5];
		byte[] buf2 = new byte[5];

		int ret1 = orig.read(buf1);
		int ret2 = peek.read(buf2);

		assertEquals(ret1, ret2);
		assertArrayEquals(buf1, buf2);
	}

	public void testReadBufAfter1(byte[] bytes) throws Exception {
		final InputStream orig = new ByteArrayInputStream(bytes.clone());
		final OptionalGzipInputStream.PeekInputStream peek = new OptionalGzipInputStream.PeekInputStream(new ByteArrayInputStream(bytes.clone()));

		int b1 = orig.read();
		int b2 = peek.read();

		byte[] buf1 = new byte[5];
		byte[] buf2 = new byte[5];

		int ret1 = orig.read(buf1);
		int ret2 = peek.read(buf2);

		assertEquals(b1, b2);
		assertEquals(ret1, ret2);
		assertArrayEquals(buf1, buf2);
	}

	public void testReadBufLen(byte[] bytes) throws Exception {
		final InputStream orig = new ByteArrayInputStream(bytes.clone());
		final OptionalGzipInputStream.PeekInputStream peek = new OptionalGzipInputStream.PeekInputStream(new ByteArrayInputStream(bytes.clone()));

		byte[] buf1 = new byte[50];
		byte[] buf2 = new byte[50];

		int ret01 = orig.read(buf1, 22, 0);
		if (ret01 == 0) ret01 = -1; // this one is so special. reading 0 bytes may not check for EOF.
		int ret02 = peek.read(buf2, 22, 0);
		if (ret02 == 0) ret02 = -1; // this one is so special. reading 0 bytes may not check for EOF.

		int reta1 = orig.read(buf1, 1, 1);
		int reta2 = peek.read(buf2, 1, 1);

		int retb1 = orig.read(buf1, 10, 11);
		int retb2 = peek.read(buf2, 10, 11);

		assertEquals(ret01, ret02);
		assertEquals(reta1, reta2);
		assertEquals(retb1, retb2);
		assertArrayEquals(buf1, buf2);
	}

	public void testReadBufLenAfter1(byte[] bytes) throws Exception {
		final InputStream orig = new ByteArrayInputStream(bytes.clone());
		final OptionalGzipInputStream.PeekInputStream peek = new OptionalGzipInputStream.PeekInputStream(new ByteArrayInputStream(bytes.clone()));

		int b1 = orig.read();
		int b2 = peek.read();

		byte[] buf1 = new byte[50];
		byte[] buf2 = new byte[50];

		int ret1 = orig.read(buf1, 1, 6);
		int ret2 = peek.read(buf2, 1, 6);

		// over-read
		if (bytes.length > 6) {
			int retz1 = orig.read(buf1, 1, 40);
			int retz2 = peek.read(buf2, 1, 40);
			assertEquals(retz1, retz2);
		}

		assertEquals(b1, b2);
		assertEquals(ret1, ret2);
		assertArrayEquals(buf1, buf2);

		orig.close();
		peek.close();
	}

	public void testOptionalGzipReadSingle(int dataindex, byte[] bytes) throws Exception {
		final OptionalGzipInputStream ogis = new OptionalGzipInputStream(new ByteArrayInputStream(bytes.clone()));

		final int b = ogis.read();
		System.out.println("data index " + dataindex + " after ogis read single: " + b);
	}

	public void testOptionalGzipReadBuf(int dataindex, byte[] bytes) throws Exception {
		final OptionalGzipInputStream ogis = new OptionalGzipInputStream(new ByteArrayInputStream(bytes.clone()));

		try {
			byte[] buf = new byte[100];
			final int read = ogis.read(buf);
			System.out.println("data index " + dataindex + " after ogis read buf (" + read + "): " + Arrays.toString(buf));
		} catch (Exception e) {
			System.out.println("data index " + dataindex + " got exception read buf " + e.getMessage());
		}
	}

	public void testOptionalGzipReadBufLen(int dataindex, byte[] bytes) throws Exception {
		final OptionalGzipInputStream ogis = new OptionalGzipInputStream(new ByteArrayInputStream(bytes.clone()));

		try {
			byte[] buf = new byte[100];
			final int read = ogis.read(buf, 1, 40);
			System.out.println("data index " + dataindex + " after ogis read buf len (" + read + "): " + Arrays.toString(buf));
		} catch (Exception e) {
			System.out.println("data index " + dataindex + " got exception read buf len " + e.getMessage());
		}

		ogis.close();
	}

	@Test
	public void testAll() throws Exception {
		init();
		final byte[][] bytes1 = new byte[][] {zero, one, onefakegzip, twonon, twogzip, realgzip};
		for (int i = 0; i < bytes1.length; i++) {
			final byte[] bytes = bytes1[i];
			System.out.println("trying test data index " + i);
			testReadSingle(bytes);
			testPeek(bytes);
			testReadBuf(bytes);
			testReadBufAfter1(bytes);
			testReadBufLen(bytes);
			testReadBufLenAfter1(bytes);
			testOptionalGzipReadSingle(i, bytes);
			testOptionalGzipReadBuf(i, bytes);
			testOptionalGzipReadBufLen(i, bytes);
		}
	}
}