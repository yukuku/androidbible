package yuku.alkitab.yes2.section.base;

import yuku.bintex.BintexWriter;

public interface SectionContent {
	public interface Writer {
		void toBytes(BintexWriter writer) throws Exception;
	}
}
