package yuku.alkitab.yes2.section.base;

import yuku.alkitab.yes2.io.RandomInputStream;
import yuku.bintex.BintexWriter;

public class SectionContent {
	private final String name;
	
	public SectionContent(String name) {
		this.name = name;
	}
	
	public String getName() {
		return name;
	}

	public interface Writer {
		void toBytes(BintexWriter writer) throws Exception;
	}
	
	public interface Reader<T extends SectionContent> {
		T toSection(RandomInputStream input) throws Exception;
	}
}
