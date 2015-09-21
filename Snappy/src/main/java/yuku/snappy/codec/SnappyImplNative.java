package yuku.snappy.codec;


class SnappyImplNative extends Snappy {
	private long nativeObj = 0;
	
	static {
		System.loadLibrary("snappy");
	}

	public SnappyImplNative() {
		nativeObj = nativeSetup();
	}

	@Override public String getImplementationName() {
		return "native";
	}
	
	private static native long nativeSetup();
	private static native int nativeCompress(long obj, byte[] in, int inOffset, byte[] out, int outOffset, int len);
	private static native int nativeDecompress(long obj, byte[] in, int inOffset, byte[] out, int outOffset, int len);

	@Override public int compress(byte[] in, int inOffset, byte[] out, int outOffset, int len) {
		int ret = nativeCompress(nativeObj, in, inOffset, out, outOffset, len);
		if (ret == -2) throw new IllegalArgumentException("SNAPPY_BUFFER_TOO_SMALL");
		return ret;
	}

	@Override public int decompress(byte[] in, int inOffset, byte[] out, int outOffset, int len) {
		int ret = nativeDecompress(nativeObj, in, inOffset, out, outOffset, len);
		if (ret == -1) throw new IllegalArgumentException("SNAPPY_INVALID_INPUT");
		if (ret == -2) throw new IllegalArgumentException("SNAPPY_BUFFER_TOO_SMALL");
		// OK
		int res = uncompressedLength(in, inOffset, len);
		return res;
	}
}
