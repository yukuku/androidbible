package yuku.afw.rpc;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.zip.GZIPInputStream;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import yuku.afw.rpc.Request.Method;
import yuku.afw.rpc.Request.OptionsKey;
import yuku.afw.rpc.Response.Validity;

public class HttpPerformer {
	public static final String TAG = HttpPerformer.class.getSimpleName();
	private final Request request;
	private final AsyncRequest<?>.Task<?> task;

	/**
	 * @param task can be null if cancel is not supported
	 */
	public HttpPerformer(@NullOk AsyncRequest<?>.Task<?> task, Request request) {
		this.task = task;
		this.request = request;
	}
	
	public Response perform() {
		String url = request.url;
		
		DefaultHttpClient client = new DefaultHttpClient();
		HttpParams httpParams = client.getParams();
		
		if (request.options != null && request.options.get(OptionsKey.connectionTimeout) != null) {
			HttpConnectionParams.setConnectionTimeout(httpParams, (Integer) request.options.get(OptionsKey.connectionTimeout)); 
		} else {
			HttpConnectionParams.setConnectionTimeout(httpParams, 30000); // 30 seconds for connection timeout
		}
		
		if (request.options != null && request.options.get(OptionsKey.soTimeout) != null) {
			HttpConnectionParams.setSoTimeout(httpParams, (Integer) request.options.get(OptionsKey.soTimeout)); 
		} else {
			HttpConnectionParams.setSoTimeout(httpParams, 15000); // 15 seconds for waiting for data
		}	
		
		ByteArrayOutputStream os = new ByteArrayOutputStream(256); // TODO make it more efficient by not buffering byte[]
		int httpResponseCode = 0;
		
		try {
			HttpRequestBase base = null;
			
			if (request.method == Method.GET || request.method == Method.GET_RAW) {
				url += request.params.toUrlEncodedStringWithOptionalQuestionMark();
				
				base = new HttpGet(url);
			} else if (request.method == Method.GET_DIGEST) {
				client.getCredentialsProvider().setCredentials(new AuthScope(null, -1), new UsernamePasswordCredentials(request.params.getAndRemove("email"), request.params.getAndRemove("passwd")));  //$NON-NLS-1$//$NON-NLS-2$

				url += request.params.toUrlEncodedStringWithOptionalQuestionMark();
				
				base = new HttpGet(url);
			} else if (request.method == Method.POST) {
				HttpPost method = new HttpPost(url);
				
				// use params as usual
				ArrayList<NameValuePair> list = new ArrayList<NameValuePair>();
				request.params.addAllTo(list);
				method.setEntity(new UrlEncodedFormEntity(list, "utf-8")); //$NON-NLS-1$
				
				base = method;
			} else if (request.method == Method.DELETE) {
				url += request.params.toUrlEncodedStringWithOptionalQuestionMark();
				
				base = new HttpDelete(url);
			} else {
				throw new RuntimeException("http method " + request.method + " not supported yet"); //$NON-NLS-1$ //$NON-NLS-2$
			}
			
			request.headers.addTo(base);
			base.addHeader("Cache-Control", "no-cache");  //$NON-NLS-1$//$NON-NLS-2$
			base.addHeader("Accept-Encoding", "gzip"); //$NON-NLS-1$ //$NON-NLS-2$
			
			HttpResponse response = client.execute(base);
			httpResponseCode = response.getStatusLine().getStatusCode();
			
			if (task != null && task.isCancelled()) {
				base.abort();
			} else {
				Header encodingHeader = response.getFirstHeader("Content-Encoding"); //$NON-NLS-1$
				HttpEntity entity = response.getEntity();
				InputStream content = entity.getContent();
				
				try {
					if (encodingHeader != null && encodingHeader.getValue().equalsIgnoreCase("gzip")) { //$NON-NLS-1$
						content = new GZIPInputStream(content);
					}
					
					byte[] buf = new byte[4096 * 4];
					while (true) {
						int read = content.read(buf);
						if (read <= 0) break;
						
						os.write(buf, 0, read);
						
						if (task != null && task.isCancelled()) {
							base.abort();
							break;
						}
					}
				} finally {
					if (content != null) content.close();
				}
			}
		} catch (IOException e) {
			Log.d(TAG, "IoError: " + e.getClass().getSimpleName() + ": " + e.getMessage()); //$NON-NLS-1$ //$NON-NLS-2$
			return new Response(this.request, Validity.IoError, e.getClass().getName() + ": " + e.getMessage()); //$NON-NLS-1$
		}
		
		if (task != null && task.isCancelled()) {
			return new Response(this.request, Validity.Cancelled, "cancelled"); //$NON-NLS-1$
		}
		
		return new Response(this.request, os.toByteArray(), httpResponseCode); 
	}
}
