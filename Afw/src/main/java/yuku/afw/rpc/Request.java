package yuku.afw.rpc;

import java.util.EnumMap;

public class Request {
	public static final String TAG = Request.class.getSimpleName();

	public enum Method {
		GET,
		GET_DIGEST,
		GET_RAW, 
		POST,
		DELETE, 
		// no PUT because it can be replaced with POST
	}
	
	public enum OptionsKey {
		/** int, default 10000 (10 sec timeout) */
		connectionTimeout,
		/** int, default 5000 (5 sec timeout) */
		soTimeout,
		/** bool, default true (encapsulate in params) */
		encapsulateParams,
	}
	
	public static class Options extends EnumMap<OptionsKey, Object> {
		public Options() {
			super(OptionsKey.class);
		}
	}
	
	public Method method;
	public String url;
	public Headers headers = new Headers();
	public Params params = new Params();
	public Options options;
	
	public Request(Method method, String path) {
		this.method = method;
		this.url = path;
	}
	
	@Override
	public String toString() {
		StringBuilder debugInfo = new StringBuilder(100);
		debugInfo.append(method.name());
		debugInfo.append(' ').append(url).append(" params:"); //$NON-NLS-1$
		params.addDebugString(debugInfo);
		debugInfo.append(" headers:"); //$NON-NLS-1$
		headers.addDebugString(debugInfo);
		return debugInfo.toString();
	}
}
