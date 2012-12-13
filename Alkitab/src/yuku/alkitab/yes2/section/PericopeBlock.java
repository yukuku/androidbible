package yuku.alkitab.yes2.section;

import yuku.alkitab.yes2.model.PericopeData;
import yuku.alkitab.yes2.model.PericopeData.Entry;
import yuku.alkitab.yes2.section.base.SectionContent;
import yuku.bintex.BintexWriter;

public class PericopeBlock extends SectionContent implements SectionContent.Writer {
	private final PericopeData data;

	public PericopeBlock(PericopeData data) {
		super("pericopeData");
		this.data = data;
	}

	@Override public void write(BintexWriter writer) throws Exception {
		int sectionBeginOffset = writer.getPos();
		for (Entry entry: data.entries) {
			int entryBeginOffset = writer.getPos();
			
			/* Blok {
			 * uint8 version = 4
			 * value title
			 * uint8 parallel_count
			 * value[parallel_count] parallels
			 * }
			 */				
			
			writer.writeUint8(4); // version
			
			writer.writeValueString(entry.block.title); // title
			writer.writeUint8(entry.block.parallels == null? 0: entry.block.parallels.size()); // parallel_count
			if (entry.block.parallels != null) { // xparalel
				for (String parallel: entry.block.parallels) {
					writer.writeValueString(parallel);
				}
			}
			
			entry.block._offset = entryBeginOffset - sectionBeginOffset;
		}
	}
}
