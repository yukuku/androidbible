package yuku.afw.rpc;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import yuku.afw.D;

public class UrlLoader {
	public static final String TAG = UrlLoader.class.getSimpleName();
	
	static class Data {
		List<Listener> listeners;
		long startTime;
	}
	
	Map<String, Data> running = new LinkedHashMap<String, Data>(); 
	
	public interface Listener {
		void onResponse(String url, Response response, BaseData data, boolean firstTime);
	}
	
	/** must call this from main thread */
	public synchronized boolean load(Context context, final String url, ImageData imageData, Listener listener) {
		Data data = running.get(url);
		if (data == null) {
			// not running, create new
			final Data newData = new Data();
			newData.startTime = SystemClock.uptimeMillis();
			newData.listeners = new ArrayList<Listener>(4);
			newData.listeners.add(listener);
			// save
			running.put(url, newData);
			
			if (D.EBUG) Log.d(TAG, "Loading url: " + url + " creates a new request with 1 listener");
			
			new AsyncRequest<ImageData>(new UrlImage(url), imageData) {
				@Override protected void onResponse(Response response, ImageData imageData) {
					long responseTime = SystemClock.uptimeMillis();
					// call all listeners and remove from map
					try {
						boolean firstTime = true;
						for (int i = 0, len = newData.listeners.size(); i < len; i++) {
							newData.listeners.get(i).onResponse(url, response, imageData, firstTime);
							firstTime = false;
						}
					} finally {
						running.remove(url);
						
						long endTime = SystemClock.uptimeMillis();
						if (D.EBUG) Log.d(UrlLoader.TAG, "Loading url: " + url + " finished. Loaded in " + (responseTime - newData.startTime) + " ms, callback in " + (endTime - responseTime) + " ms");
					}
				};
			}.start();
			
			return true;
		} else {
			// just add listeners
			data.listeners.add(listener);
			
			if (D.EBUG) Log.d(TAG, "Loading url: " + url + " uses existing request, now it has " + data.listeners.size() + " listeners");
			
			return false;
		}
	}
	
	private static class UrlImage extends Request {
		public UrlImage(String url) {
			super(Method.GET_RAW, url);
		}
	}
}
