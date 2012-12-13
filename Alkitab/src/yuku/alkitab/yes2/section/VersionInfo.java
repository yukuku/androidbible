package yuku.alkitab.yes2.section;

import java.util.LinkedHashMap;
import java.util.Map;

import yuku.alkitab.yes2.io.RandomInputStream;
import yuku.alkitab.yes2.section.base.SectionContent;
import yuku.bintex.BintexReader;
import yuku.bintex.BintexWriter;
import yuku.bintex.ValueMap;

public class VersionInfo extends SectionContent implements SectionContent.Writer {
	public String shortName;
	public String longName;
	public String description;
	public String locale;
	public int book_count;
	public int hasPericopes;
	public int textEncoding; // 1 = ascii; 2 = utf-8 (default)
	
	public VersionInfo() {
		super("versionInfo_");
	}

	@Override public void write(BintexWriter writer) throws Exception {
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
	
	public static class Reader implements SectionContent.Reader<VersionInfo> {
		@Override public VersionInfo read(RandomInputStream input) throws Exception {
			BintexReader br = new BintexReader(input);
			ValueMap map = br.readValueSimpleMap();
			VersionInfo res = new VersionInfo();
			res.shortName = map.getString("shortName");
			res.longName = map.getString("longName");
			res.description = map.getString("description");
			res.locale = map.getString("locale");
			res.book_count = map.getInt("book_count");
			res.hasPericopes = map.getInt("hasPericopes", 0);
			res.textEncoding = map.getInt("textEncoding", 2);
			return res;
		}
	}
}

