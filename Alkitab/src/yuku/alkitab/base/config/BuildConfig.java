package yuku.alkitab.base.config;

import java.util.HashMap;

public class BuildConfig {
	static HashMap<String, Config> map = new HashMap<String, Config>();
	
	static {
		map.put("yuku.alkitab", new Config(false, false, true, true));
		//map.put("yuku.alkitab", new Config(true, true));
		map.put("yuku.alkitab.kjv", new Config(false, false, false, false));
	}
	
	public static Config get(String pkgName) {
		Config c = map.get(pkgName);
		if (c == null) {
			return new Config(true, true, true, true);
		}
		return c;
	}
}
