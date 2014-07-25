package yuku.alkitab.model.util;

import java.util.UUID;

public class Gid {
	public static String newGid() {
		return "g1:" + UUID.randomUUID().toString();
	}
}
