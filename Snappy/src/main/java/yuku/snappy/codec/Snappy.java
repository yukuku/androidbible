package yuku.snappy.codec;


public abstract class Snappy {
	/**
	 * @return Compressed data length
	 */
	public abstract int compress(byte[] in, int inOffset, byte[] out, int outOffset, int len);

	/**
	 * @return Decompressed data length
	 */
	public abstract int decompress(byte[] in, int inOffset, byte[] out, int outOffset, int len);

	public int maxCompressedLength(int sourceLength) {
		return 32 + sourceLength + sourceLength / 6;
	}

	public int uncompressedLength(byte[] in, int offset, int len) throws IllegalArgumentException {
		int sourceIndex = offset;
		int max = offset + len;
		int i = 0;
		int targetLength = 0;
		do {
			if (sourceIndex >= max) throw new IllegalArgumentException("no length obtained");
			targetLength += (in[sourceIndex] & 0x7f) << (i++ * 7);
		} while ((in[sourceIndex++] & 0x80) == 0x80);

		return targetLength;
	}

	abstract String getImplementationName();

	public static class Factory {
		private static int nativeAvailable = 0; // 0=unknown, 1=yes, 2=no

		public Snappy newInstanceJava() {
			return new SnappyImplJava();
		}

		public Snappy newInstanceNative() {
			return new SnappyImplNative();
		}

		public Snappy newInstance() {
			if (nativeAvailable == 0) {
				try {
					System.loadLibrary("snappy");
					nativeAvailable = 1;
				} catch (UnsatisfiedLinkError | SecurityException /* occurs when it's not allowed to load native libraries like Google App Engine */ e) {
					nativeAvailable = 2;
				}
			}

			if (nativeAvailable == 1) {
				return newInstanceNative();
			} else if (nativeAvailable == 2) {
				return newInstanceJava();
			}

			return null;
		}
	}
}
