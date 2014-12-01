package yuku.afw.rpc;

import yuku.afw.rpc.Request.Method;

public class Response {
	public static final String TAG = Response.class.getSimpleName();

	public enum Validity {
		Ok,
		Cancelled,
		JsonError,
		IoError, 
		ProcessError,
	}
	
	public Validity validity;
	public final int code;
	public final Request request;
	public final String message;
	public final byte[] data;
	
	public Response(Request request, Validity validity, String message) {
		this.request = request;
		this.validity = validity;
		this.code = 0;
		this.message = message;
		this.data = null;
	}
	
	/** for {@link Method#GET_RAW} */
	public Response(Request request, byte[] raw, int code) {
		this.request = request;
		this.validity = Validity.Ok;
		this.code = 0;
		this.message = null;
		this.data = raw;
	}
	
	@SuppressWarnings("unchecked") public <T> T getData() {
		return (T) data;
	}

	@Override public String toString() {
		return "Response{" + validity + " " + code + " message=" + message + " data=" + (data == null? "null": "len " + data.length) + "}"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	}
}
