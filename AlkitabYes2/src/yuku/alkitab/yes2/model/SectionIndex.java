package yuku.alkitab.yes2.model;

import android.util.Log;
import yuku.alkitab.yes2.BuildConfig;
import yuku.alkitab.yes2.io.RandomInputStream;
import yuku.bintex.BintexReader;
import yuku.bintex.ValueMap;

import java.io.IOException;
import java.util.LinkedHashMap;

public class SectionIndex {
	public static final String TAG = SectionIndex.class.getSimpleName();
	
	static class Entry {
		String name;
		int offset;
		int attributes_size;
		int content_size;
	}
	
	private LinkedHashMap<String, Entry> entries;
	private int sectionDataStartOffset;
	
	@SuppressWarnings("deprecation") public static SectionIndex read(RandomInputStream input) throws IOException {
		BintexReader br = new BintexReader(input);
		
		int version = br.readUint8();
		if (version != 1) {
			Log.d(TAG, "Unsupported section index version: " + version);
			return null;
		}
		
		SectionIndex res = new SectionIndex();
		res.entries = new LinkedHashMap<String, Entry>();
		
		int section_count = br.readInt();
		
		for (int i = 0; i < section_count; i++) {
			Entry e = new Entry();
			int name_len = br.readUint8();
			byte[] name_buf = new byte[name_len];
			br.readRaw(name_buf);
			e.name = new String(name_buf, 0);
			e.offset = br.readInt();
			e.attributes_size = br.readInt();
			e.content_size = br.readInt();
			br.skip(4); // reserved
			res.entries.put(e.name, e);
			
			if (BuildConfig.DEBUG) {
				Log.d(TAG, "@@read: " + e.name + " offset=" + e.offset + " attributes_size=" + e.attributes_size + " content_size=" + e.content_size);
			}
		}
		
		res.sectionDataStartOffset = (int) input.getFilePointer();
		
		if (BuildConfig.DEBUG) {
			Log.d(TAG, "@@read start of section data offset: " + res.sectionDataStartOffset);
		}
		
		return res;
	}

	public ValueMap getSectionAttributes(String sectionName, RandomInputStream file_) throws IOException {
		Entry e = entries.get(sectionName);
		if (e == null) {
			return null;
		} else {
			file_.seek(this.sectionDataStartOffset + e.offset);
			return new BintexReader(file_).readValueSimpleMap();
		}
	}
	
	public boolean seekToSectionContent(String sectionName, RandomInputStream file_) throws IOException {
		Entry e = entries.get(sectionName);
		if (e == null) {
			return false;
		} else {
			file_.seek(this.sectionDataStartOffset + e.offset + e.attributes_size);
			return true;
		}
	}
	
	public long getAbsoluteOffsetForSectionContent(String name) {
		Entry e = entries.get(name);
		if (e == null) {
			return -1;
		} else {
			return this.sectionDataStartOffset + e.offset + e.attributes_size;
		}
	}
}
