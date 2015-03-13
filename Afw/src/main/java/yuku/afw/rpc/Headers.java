package yuku.afw.rpc;

import java.util.LinkedHashMap;
import java.util.Map.Entry;

import org.apache.http.client.methods.HttpRequestBase;

public class Headers {
	public static final String TAG = Headers.class.getSimpleName();

	LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
	
	public void put(String key, String value) {
		map.put(key, value);
	}
	
	public void put(String key, long value) {
		map.put(key, String.valueOf(value));
	}
	
	public boolean has(String key) {
		return map.containsKey(key);
	}

	public void addTo(HttpRequestBase httpRequestBase) {
		for (Entry<String, String> entry: map.entrySet()) {
			httpRequestBase.addHeader(entry.getKey(), entry.getValue());
		}
	}
	
	public void addDebugString(StringBuilder sb) {
		for (String key: map.keySet()) {
			String value = map.get(key);
			if (value.length() > 80) value = "(len=" + value.length() + ")" + value.substring(0, 78) + "..."; //$NON-NLS-1$
			sb.append(' ');
			sb.append(key).append('=').append(value);
		}
	}
}
