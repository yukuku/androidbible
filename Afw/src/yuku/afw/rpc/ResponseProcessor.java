package yuku.afw.rpc;


public interface ResponseProcessor {
	public static final String TAG = ResponseProcessor.class.getSimpleName();

	void process(byte[] raw) throws Exception;
}
