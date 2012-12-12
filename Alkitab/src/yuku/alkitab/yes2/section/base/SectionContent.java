package yuku.alkitab.yes2.section.base;

import yuku.alkitab.yes2.io.RandomInputStream;
import yuku.bintex.BintexWriter;

public interface SectionContent {
	public interface Writer {
		void toBytes(BintexWriter writer) throws Exception;
	}
	
	public interface Reader<T extends SectionContent> {
		T toSection(RandomInputStream input) throws Exception;
	}
}
