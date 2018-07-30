package yuku.snappy.codec;

import de.jarnbjo.jsnappy.Buffer;
import de.jarnbjo.jsnappy.SnappyCompressor;
import de.jarnbjo.jsnappy.SnappyDecompressor;


class SnappyImplJava extends Snappy {
	public SnappyImplJava() {
	}

	@Override
	public String getImplementationName() {
		return "java";
	}

	@Override
	public int compress(byte[] in, int inOffset, byte[] out, int outOffset, int len) {
		Buffer buffer = SnappyCompressor.compress(in, inOffset, len, null, 100);
		if (out.length - outOffset < buffer.getLength()) {
			throw new IllegalArgumentException("SNAPPY_BUFFER_TOO_SMALL");
		}

		byte[] data = buffer.getData();
		System.arraycopy(data, 0, out, outOffset, buffer.getLength());
		return buffer.getLength();
	}

	@Override
	public int decompress(byte[] in, int inOffset, byte[] out, int outOffset, int len) {
		Buffer buffer = SnappyDecompressor.decompress(in, inOffset, len);
		if (out.length - outOffset < buffer.getLength()) {
			throw new IllegalArgumentException("SNAPPY_BUFFER_TOO_SMALL");
		}

		byte[] data = buffer.getData();
		System.arraycopy(data, 0, out, outOffset, buffer.getLength());
		return buffer.getLength();
	}
}
