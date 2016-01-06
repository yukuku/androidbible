package yuku.afw.rpc;

public abstract class BaseData {
	public static final String TAG = BaseData.class.getSimpleName();
	
	public abstract boolean isSuccessResponse(Response response);
	public abstract ResponseProcessor getResponseProcessor(Response response);
}
