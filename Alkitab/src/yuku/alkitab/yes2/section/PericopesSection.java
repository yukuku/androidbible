package yuku.alkitab.yes2.section;

import yuku.alkitab.base.model.PericopeIndex;
import yuku.alkitab.yes2.io.RandomInputStream;
import yuku.alkitab.yes2.io.RandomOutputStream;
import yuku.alkitab.yes2.model.PericopeData;
import yuku.alkitab.yes2.model.PericopeData.Entry;
import yuku.alkitab.yes2.section.base.SectionContent;
import yuku.bintex.BintexReader;
import yuku.bintex.BintexWriter;

public class PericopesSection extends SectionContent implements SectionContent.Writer {
	public static final String SECTION_NAME = "pericopes";
	
	private final PericopeData data;
	int data_offset = 0;

	public PericopesSection(PericopeData data) {
		super(SECTION_NAME);
		this.data = data;
	}
	

	@Override public void write(RandomOutputStream output) throws Exception {
		BintexWriter bw = new BintexWriter(output);
		
		long savedpos_sectionBegin = output.getFilePointer();
		long savedpos_indexSize;
		long[] savedpos_entryOffsets;
		int[] savedoffset_entryOffsets;
		
		// uint8 version: 2
		bw.writeUint8(2);
		
		// int index_size
		savedpos_indexSize = output.getFilePointer();
		bw.writeInt(-1);
		
		// int entry_count
		int entry_count = data.entries.size();
		bw.writeInt(entry_count); 
		
		// Entry[entry_count]
		savedpos_entryOffsets = new long[entry_count];
		for (int i = 0; i < entry_count; i++) {
			Entry entry = data.entries.get(i);
		
			bw.writeInt(entry.ari);
			savedpos_entryOffsets[i] = output.getFilePointer();
			bw.writeInt(-1); // placeholder for later
		}
		
		long dataBeginOffset = output.getFilePointer();
		
		savedoffset_entryOffsets = new int[entry_count];
		for (int i = 0; i < entry_count; i++) {
			Entry entry = data.entries.get(i);
			savedoffset_entryOffsets[i] = (int) (output.getFilePointer() - dataBeginOffset);
			
			/* Blok {
			 * uint8 version = 4
			 * value title
			 * uint8 parallel_count
			 * value[parallel_count] parallels
			 * }
			 */				
			
			bw.writeUint8(4); // version
			
			bw.writeValueString(entry.block.title); // title
			bw.writeUint8(entry.block.parallels == null? 0: entry.block.parallels.size()); // parallel_count
			if (entry.block.parallels != null) { // xparalel
				for (String parallel: entry.block.parallels) {
					bw.writeValueString(parallel);
				}
			}
		}
		
		int section_size = (int) (output.getFilePointer() - savedpos_sectionBegin);
		
		{ // patches
			output.seek(savedpos_indexSize);
			bw.writeInt(section_size);
			
			for (int i = 0; i < entry_count; i++) {
				output.seek(savedpos_entryOffsets[i]);
				bw.writeInt(savedoffset_entryOffsets[i]);
			}
		}
	}
	

	public static class Reader {
		public PericopeIndex read(RandomInputStream input) throws Exception {
			BintexReader br = new BintexReader(input);
			int version = br.readUint8();
			if (version != 2) {
				throw new RuntimeException("PericopeIndex version not supported: " + version);
			}
			
			PericopeIndex res = new PericopeIndex();
			
			int entry_count = br.readInt();
			
			res.aris = new int[entry_count];
			res.offsets = new int[entry_count];
			
			for (int i = 0; i < entry_count; i++) {
				res.aris[i] = br.readInt();
				res.offsets[i] = br.readInt();
			}
			
			return res;
		}
	}
}

