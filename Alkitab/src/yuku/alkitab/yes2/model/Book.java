package yuku.alkitab.yes2.model;

import java.util.LinkedHashMap;
import java.util.Map;

import yuku.bintex.BintexWriter;

public class Book {
	public int bookId;
	public String shortName;
	public int offset;
	public int chapter_count;
	public int[] verse_count;
	public int[] chapter_offsets;
	
	public void toBytes(BintexWriter writer) throws Exception {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("version", 3);
		map.put("bookId", bookId);
		map.put("shortName", shortName);
		map.put("offset", offset);
		map.put("chapter_count", chapter_count);
		map.put("verse_count", verse_count);
		map.put("chapter_offsets", chapter_offsets);
		writer.writeValueSimpleMap(map);
	}
}
