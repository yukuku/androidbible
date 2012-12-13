package yuku.alkitab.yes2.section.base;

import yuku.alkitab.yes2.io.RandomInputStream;
import yuku.bintex.BintexWriter;
import yuku.bintex.ValueMap;

public class SectionContent {
	private final String name;
	private final ValueMap attributes;
	
	public SectionContent(String name) {
		this(name, null);
	}
	
	public SectionContent(String name, ValueMap attributes) {
		this.name = name;
		this.attributes = attributes;
	}
	
	public String getName() {
		return name;
	}

	public ValueMap getAttributes() {
		return attributes;
	}

	public interface Writer {
		void write(BintexWriter writer) throws Exception;
	}
	
	public interface Reader<T extends SectionContent> {
		T read(RandomInputStream input) throws Exception;
	}
}
