package yuku.alkitab.base.config;

import java.util.HashMap;

public class BuildConfig {
	static HashMap<String, Config> map = new HashMap<String, Config>();
	
	static {
		map.put("yuku.alkitab", new Config("tb", "Terjemahan Baru", true, false, true, true, false)); //$NON-NLS-1$
		map.put("yuku.alkitab.kjv", new Config("kjv", "King James", false, false, false, true, true)); //$NON-NLS-1$
	}
	
	public static Config get(String pkgName) {
		Config c = map.get(pkgName);
		if (c == null) {
			throw new RuntimeException("pkgName tidak dikenal: " + pkgName);
		}
		return c;
	}
}
