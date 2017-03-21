package yuku.bintex;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class ValueMap implements Map<String, Object> {
	LinkedHashMap<String, Object> map;

	public ValueMap() {
		map = new LinkedHashMap<>();
	}

	@Override public void clear() {
		map.clear();
	}

	@Override public boolean containsKey(Object key) {
		return map.containsKey(key);
	}

	@Override public boolean containsValue(Object value) {
		return map.containsValue(value);
	}

	@Override public Set<Entry<String, Object>> entrySet() {
		return map.entrySet();
	}

	@Override public Object get(Object key) {
		return map.get(key);
	}

	@Override public boolean isEmpty() {
		return map.isEmpty();
	}

	@Override public Set<String> keySet() {
		return map.keySet();
	}

	@Override public Object put(String key, Object value) {
		return map.put(key, value);
	}

	@Override public void putAll(Map<? extends String, ? extends Object> arg0) {
		map.putAll(arg0);
	}

	@Override public Object remove(Object key) {
		return map.remove(key);
	}

	@Override public int size() {
		return map.size();
	}

	@Override public Collection<Object> values() {
		return map.values();
	}

	public int getInt(String key) {
		return getInt(key, 0);
	}

	public int getInt(String key, int def) {
		Object v = map.get(key);
		if (v instanceof Number) return ((Number) v).intValue();
		if (v == null) return 0;
		if (v instanceof String) {
			try {
				return Integer.parseInt((String) v);
			} catch (NumberFormatException e) {
				return def;
			}
		}
		return def;
	}

	public String getString(String key) {
		Object v = map.get(key);
		if (v == null) return null;
		if (v instanceof String) return (String) v;
		return v.toString();
	}

	public int[] getIntArray(String key) {
		Object v = map.get(key);
		if (v instanceof int[]) return (int[]) v;
		return null;
	}

	public ValueMap getSimpleMap(String key) {
		Object v = map.get(key);
		if (v instanceof ValueMap) return (ValueMap) v;
		return null;
	}
}
