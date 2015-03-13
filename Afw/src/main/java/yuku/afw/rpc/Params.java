package yuku.afw.rpc;

import android.net.Uri;
import android.util.Log;

import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Params {
	public static final String TAG = Params.class.getSimpleName();

	private JSONObject map = new JSONObject();
	
	public void put(String key, String value) {
		try {
			map.put(key, value);
		} catch (JSONException e) {
			Log.e(TAG, "json exception", e); //$NON-NLS-1$
		}
	}
	
	public void put(String key, double value) {
		put(key, String.valueOf(value));
	}
	
	public void put(String key, long value) {
		put(key, String.valueOf(value));
	}
	
	public void put(String key, int value) {
		put(key, String.valueOf(value));
	}
	
	public void put(String key, List<String> value) {
		JSONArray jsonArray = new JSONArray(value);
		try {
			map.put(key, jsonArray);
		} catch (JSONException e) {
			Log.e(TAG, "json exception", e); //$NON-NLS-1$
		}
	}
	
	public void put(String key, JSONArray value) {
		try {
			map.put(key, value);
		} catch (JSONException e) {
			Log.e(TAG, "json exception", e); //$NON-NLS-1$
		}
	}
	
	public String toJsonString() {
		return map.toString();
	}
	
	public void addDebugString(StringBuilder sb) {
		JSONArray names = map.names();
		if (names == null) return;
		
		for (int i = 0, len = names.length(); i < len; i++) {
			String key = names.optString(i);
			String value = map.optString(key);
			if (value.length() > 80) value = "(len=" + value.length() + ")" + value.substring(0, 78) + "..."; //$NON-NLS-1$
			sb.append(' ');
			sb.append(key).append('=').append(value);
		}
	}

	public String toUrlEncodedString() {
		StringBuilder sb = new StringBuilder(256);
		addUrlEncodedParamsTo(sb);
		return sb.toString();
	}
	
	public String toUrlEncodedStringWithOptionalQuestionMark() {
		if (map.length() == 0) {
			return ""; //$NON-NLS-1$
		}
		StringBuilder sb = new StringBuilder(256);
		sb.append('?');
		addUrlEncodedParamsTo(sb);
		return sb.toString();
	}

	private void addUrlEncodedParamsTo(StringBuilder sb) {
		JSONArray names = map.names();
		for (int i = 0, len = names.length(); i < len; i++) {
			String key = names.optString(i);
			String value = map.optString(key);
			if (sb.length() > 1) { // not (empty or contains only '?')
				sb.append('&');
			}
			sb.append(key).append('=').append(Uri.encode(value));
		}
	}
	
	public String getAndRemove(String key) {
		if (map.has(key)) {
			String value = map.optString(key);
			map.remove(key);
			return value;
		}
		return null;
	}

	public String get(String key) {
		if (map.has(key)) {
			return map.optString(key);
		}
		return null;
	}
	
	public JSONArray getJsonArray(String key) {
		if (map.has(key)) {
			return map.optJSONArray(key);
		}
		return null;
	}

	public void addAllTo(List<NameValuePair> list) {
		JSONArray names = map.names();
		for (int i = 0, len = names.length(); i < len; i++) {
			String key = names.optString(i);
			String value = map.optString(key);
			list.add(new BasicNameValuePair(key, value));
		}
	}
}
