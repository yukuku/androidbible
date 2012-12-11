package yuku.alkitab.yes2.section;

import java.util.LinkedHashMap;
import java.util.Map;

import yuku.alkitab.yes2.section.base.SectionContent;
import yuku.bintex.BintexWriter;

public abstract class VersionInfo implements SectionContent.Writer {
	public String shortName;
	public String longName;
	public String description;
	public String locale;
	public int book_count;
	public int hasPericopes;
	public int textEncoding; // 1 = ascii; 2 = utf-8

	@Override public void toBytes(BintexWriter writer) throws Exception {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		map.put("version", 3);
		if (shortName != null) map.put("shortName", shortName);
		if (longName != null) map.put("longName", longName);
		if (description != null) map.put("description", description);
		if (locale != null) map.put("locale", locale);
		map.put("book_count", book_count);
		map.put("hasPericopes", hasPericopes);
		map.put("textEncoding", textEncoding);
		writer.writeValueSimpleMap(map);
	}
}

