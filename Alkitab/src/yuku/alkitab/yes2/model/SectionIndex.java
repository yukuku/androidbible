package yuku.alkitab.yes2.model;

import android.util.Log;

import java.io.IOException;
import java.util.LinkedHashMap;

import yuku.alkitab.yes2.io.RandomInputStream;
import yuku.bintex.BintexReader;

public class SectionIndex {
	public static final String TAG = SectionIndex.class.getSimpleName();
	
	static class Entry {
		String name;
		int offset;
		int attributes_size;
		int content_size;
	}
	
	private LinkedHashMap<String, Entry> entries;
	
	@SuppressWarnings("deprecation") public static SectionIndex read(BintexReader br) throws IOException {
		int version = br.readUint8();
		if (version != 1) {
			Log.d(TAG, "Unsupported section index version: " + version);
			return null;
		}
		
		SectionIndex res = new SectionIndex();
		res.entries = new LinkedHashMap<String, Entry>();
		
		int section_count = br.readInt();
		br.readInt(); // sectionIndex.size in bytes, we don't actually need this
		
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
		}
		
		return res;
	}

	public boolean seekToSection(String name, RandomInputStream file_) throws IOException {
		Entry e = entries.get(name);
		if (e == null) {
			return false;
		} else {
			file_.seek(e.offset);
			return true;
		}
	}
	
	public long getOffsetForSection(String name) {
		Entry e = entries.get(name);
		if (e == null) {
			return -1;
		} else {
			return e.offset;
		}
	}
}
