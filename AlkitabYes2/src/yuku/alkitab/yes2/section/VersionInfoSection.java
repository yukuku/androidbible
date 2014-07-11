package yuku.alkitab.yes2.section;

import yuku.alkitab.yes2.io.RandomInputStream;
import yuku.alkitab.yes2.io.RandomOutputStream;
import yuku.alkitab.yes2.section.base.SectionContent;
import yuku.bintex.BintexReader;
import yuku.bintex.BintexWriter;
import yuku.bintex.ValueMap;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class VersionInfoSection extends SectionContent implements SectionContent.Writer {
	public String shortName;
	public String longName;
	public String description;
	public String locale;
	public int book_count;
	public int hasPericopes;
	public int textEncoding; // 1 = ascii; 2 = utf-8 (default)
	public int buildTime; // unix time in seconds
	
	public VersionInfoSection() {
		super("versionInfo");
	}

	@Override public void write(RandomOutputStream output) throws IOException {
		BintexWriter bw = new BintexWriter(output);
		
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("version", 3);
		if (shortName != null) map.put("shortName", shortName);
		if (longName != null) map.put("longName", longName);
		if (description != null) map.put("description", description);
		if (locale != null) map.put("locale", locale);
		if (buildTime != 0) map.put("buildTime", buildTime);
		map.put("book_count", book_count);
		map.put("hasPericopes", hasPericopes);
		map.put("textEncoding", textEncoding);
		bw.writeValueSimpleMap(map);
	}
	
	public static class Reader implements SectionContent.Reader<VersionInfoSection> {
		@Override public VersionInfoSection read(RandomInputStream input) throws Exception {
			BintexReader br = new BintexReader(input);
			ValueMap map = br.readValueSimpleMap();
			VersionInfoSection res = new VersionInfoSection();
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

