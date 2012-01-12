package yuku.alkitabconverter.util;

import java.io.File;

import java.io.File;

public class Tmp {
	public static final String TAG = Tmp.class.getSimpleName();
	
	public static String getTmpFilename(String name) {
		return new File(System.getProperty("java.io.tmpdir"), name).getAbsolutePath();
	}
}
