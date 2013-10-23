package yuku.alkitab.yes2.model;

import yuku.alkitab.model.Book;
import yuku.bintex.BintexReader;
import yuku.bintex.BintexWriter;
import yuku.bintex.ValueMap;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class Yes2Book extends Book {
	public int offset = -1;
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
		if (abbreviation != null) map.put("abbreviation", abbreviation);
		bw.writeValueSimpleMap(map);
	}
	
	public static Yes2Book fromBytes(BintexReader br) throws IOException {
		ValueMap map = br.readValueSimpleMap();
		
		Yes2Book res = new Yes2Book();
		res.bookId = map.getInt("bookId", -1);
		res.shortName = map.getString("shortName");
		res.offset = map.getInt("offset");
		res.chapter_count = map.getInt("chapter_count");
		res.verse_counts = map.getIntArray("verse_counts");
		res.chapter_offsets = map.getIntArray("chapter_offsets");
		res.abbreviation = map.getString("abbreviation");
		return res;
	}
}
