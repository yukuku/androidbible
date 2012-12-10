package yuku.alkitab.yes2.model;

import yuku.alkitab.yes2.model.PericopeData.Entry;
import yuku.bintex.BintexWriter;

public class PericopeBlock implements SectionContent {
	private final PericopeData data;

	public PericopeBlock(PericopeData data) {
		this.data = data;
	}

	@Override public void toBytes(BintexWriter writer) throws Exception {
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
