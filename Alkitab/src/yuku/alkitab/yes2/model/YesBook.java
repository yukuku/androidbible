package yuku.alkitab.yes2.model;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import yuku.bintex.BintexReader;
import yuku.bintex.BintexWriter;
import yuku.bintex.ValueMap;

public class YesBook {
	public int bookId;
	public String shortName;
	public int offset;
	public int chapter_count;
	public int[] verse_counts;
	public int[] chapter_offsets;
	
	public void toBytes(BintexWriter bw) throws Exception {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("version", 3);
		map.put("bookId", bookId);
		map.put("shortName", shortName);
		map.put("offset", offset);
		map.put("chapter_count", chapter_count);
		map.put("verse_counts", verse_counts);
		map.put("chapter_offsets", chapter_offsets);
		bw.writeValueSimpleMap(map);
	}
	
	public static YesBook fromBytes(BintexReader br) throws IOException {
		ValueMap map = br.readValueSimpleMap();
		
		YesBook res = new YesBook();
		res.bookId = map.getInt("bookId", -1);
		res.shortName = map.getString("shortName");
		res.offset = map.getInt("offset");
		res.chapter_count = map.getInt("chapter_count");
		res.verse_counts = map.getIntArray("verse_counts");
		res.chapter_offsets = map.getIntArray("chapter_offsets");
		return res;
	}
}
