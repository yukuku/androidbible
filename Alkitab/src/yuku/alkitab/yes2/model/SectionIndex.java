package yuku.alkitab.yes2.model;

import java.util.LinkedHashMap;

import yuku.bintex.BintexReader;

public class SectionIndex {
	public static final String TAG = SectionIndex.class.getSimpleName();
	
	static class SectionIndexEntry {
		String name;
		String offset;
		String attributes_size;
		String content_size;
	}
	
	private LinkedHashMap<String, SectionIndexEntry> entries;
	
	public static SectionIndex read(BintexReader br) {
		
	}
}
