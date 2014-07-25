package yuku.alkitab.yes2.section;

import yuku.alkitab.model.PericopeBlock;
import yuku.alkitab.model.PericopeIndex;
import yuku.alkitab.yes2.io.RandomInputStream;
import yuku.alkitab.yes2.io.RandomOutputStream;
import yuku.alkitab.yes2.model.PericopeData;
import yuku.alkitab.yes2.model.PericopeData.Entry;
import yuku.alkitab.yes2.model.Yes2PericopeBlock;
import yuku.alkitab.yes2.section.base.SectionContent;
import yuku.bintex.BintexReader;
import yuku.bintex.BintexWriter;

import java.io.IOException;

public class PericopesSection extends SectionContent implements SectionContent.Writer {
	public static final String SECTION_NAME = "pericopes";
	
	// for writing:
	PericopeData data_;
	
	// for reading:
	RandomInputStream input_;
	int data_offset_ = 0;
	PericopeIndex index_;

	private PericopesSection() {
		super(SECTION_NAME);
	}
	
	public PericopesSection(PericopeData data) {
		super(SECTION_NAME);
		this.data_ = data;
	}
	
	public Yes2PericopeBlock readBlock(int position) throws IOException {
		int offset = index_.offsets[position];
		input_.seek(data_offset_ + offset);
		return Yes2PericopeBlock.read(input_);
	}
	
	@Override public void write(RandomOutputStream output) throws IOException {
		BintexWriter bw = new BintexWriter(output);
		
		long savedpos_sectionBegin = output.getFilePointer();
		long savedpos_indexSize;
		long[] savedpos_entryOffsets;
		int[] savedoffset_entryOffsets;
		long savedpos_sectionEnd;
		
		// uint8 data_format_version: 3
		bw.writeUint8(3);
		
		// int index_size
		savedpos_indexSize = output.getFilePointer();
		bw.writeInt(-1); // placeholder
		
		// int entry_count
		int entry_count = data_.entries.size();
		bw.writeInt(entry_count); 
		
		// Entry[entry_count]
		savedpos_entryOffsets = new long[entry_count];
		int last_entry_ari = 0;
		for (int i = 0; i < entry_count; i++) {
			Entry entry = data_.entries.get(i);
		
			// entry.ari is written as follows:
			// if difference to existing ari is <= 0x7fff, it's written in 2bytes: (0x8000 | difference)
			// otherwise it's written in 4 bytes: (0x00000000 | (0x00ffffff & ari))
			int diff_ari = entry.ari - last_entry_ari;
			if (last_entry_ari == 0 || (diff_ari < 0 || diff_ari > 0x7fff)) {
				bw.writeInt(0x00000000 | (0x00ffffff & entry.ari));
			} else {
				bw.writeUint16(0x8000 | diff_ari);
			}
			last_entry_ari = entry.ari;
			
			// entry.offset is always written as uint16 delta to the last one (with the first one considered offset 0), 
			// so max size in bytes for a pericope entry is 65536 bytes.
			savedpos_entryOffsets[i] = output.getFilePointer();
			bw.writeUint16(0xffff); // placeholder for later
		}
		
		long dataBeginOffset = output.getFilePointer();
		
		savedoffset_entryOffsets = new int[entry_count];
		for (int i = 0; i < entry_count; i++) {
			Entry entry = data_.entries.get(i);
			savedoffset_entryOffsets[i] = (int) (output.getFilePointer() - dataBeginOffset);
			
			/* Blok {
			 * uint8 data_format_version = 4
			 * value title
			 * uint8 parallel_count
			 * value[parallel_count] parallels
			 * }
			 */				
			
			bw.writeUint8(4); // data_format_version
			
			bw.writeValueString(entry.block.title); // title
			bw.writeUint8(entry.block.parallels == null? 0: entry.block.parallels.size()); // parallel_count
			if (entry.block.parallels != null) { // parallels
				for (String parallel: entry.block.parallels) {
					bw.writeValueString(parallel);
				}
			}
		}
		savedpos_sectionEnd = output.getFilePointer();
		int section_size = (int) (savedpos_sectionEnd - savedpos_sectionBegin);
		
		{ // patches
			output.seek(savedpos_indexSize);
			bw.writeInt(section_size);
			
			int last_offset = 0;
			for (int i = 0; i < entry_count; i++) {
				int diff_offset = savedoffset_entryOffsets[i] - last_offset;
				if (diff_offset > 0xffff) {
					throw new RuntimeException("a pericope entry can't be larger than 65535 bytes");
				}
				output.seek(savedpos_entryOffsets[i]);
				bw.writeUint16(diff_offset);
				last_offset = savedoffset_entryOffsets[i];
			}
		}
		
		output.seek(savedpos_sectionEnd);
	}
	
	
	public static class Reader implements SectionContent.Reader<PericopesSection> {
		@Override public PericopesSection read(RandomInputStream input) throws Exception {
			BintexReader br = new BintexReader(input);
			
			int version = br.readUint8();
			if (version != 2 && version != 3) {
				throw new RuntimeException("PericopeIndex version not supported: " + version);
			}
			
			/* int index_size = */ br.readInt();
			
			int entry_count = br.readInt();
			
			PericopesSection res = new PericopesSection();
			res.index_ = new PericopeIndex();
			int[] aris = new int[entry_count];
			res.index_.aris = aris;
			int[] offsets = new int[entry_count];
			res.index_.offsets = offsets;
			
			if (version == 2) {
				for (int i = 0; i < entry_count; i++) {
					aris[i] = br.readInt();
					offsets[i] = br.readInt();
				}
			} else if (version == 3) {
				int last_entry_ari = 0;
				int last_entry_offset = 0;
				for (int i = 0; i < entry_count; i++) {
					// entry.ari is written as follows:
					// if difference to existing ari is <= 0x7fff, it's written in 2bytes: (0x8000 | difference)
					// otherwise it's written in 4 bytes: (0x00000000 | (0x00ffffff & ari))
					int data_ari = br.readUint16();
					int ari;
					if ((data_ari & 0x8000) == 0) { // absolute
						ari = (data_ari << 16) | br.readUint16();
					} else { // relative
						ari = last_entry_ari + (data_ari & 0x7fff);
					}
					aris[i] = last_entry_ari = ari;
					
					// entry.offset is always written as uint16 delta to the last one (with the first one considered offset 0), 
					// so max size in bytes for a pericope entry is 65536 bytes.
					int data_offset = br.readUint16();
					int offset = last_entry_offset + data_offset;
					offsets[i] = last_entry_offset = offset;
				}
			}
			
			res.input_ = input;
			res.data_offset_ = (int) input.getFilePointer();
			
			return res;
		}
	}

	/**
	 * @param aris (result param) the actual aris of the pericopes 
	 * @param blocks (result param) the pericope blocks found
	 * @param max maximum number of results to return, must be less than or equal to min(aris.length, blocks.length)
	 * @return number of pericopes loaded by this method
	 */
	public int getPericopesForAris(int ari_from, int ari_to, int[] aris, PericopeBlock[] blocks, int max) throws IOException {
		int first = index_.findFirst(ari_from, ari_to);
		if (first == -1) {
			return 0;
		}

		int cur = first;
		int res = 0;

		while (true) {
			int ari = index_.getAri(cur);
			if (ari >= ari_to) { // no more
				break;
			}
			
			Yes2PericopeBlock block = readBlock(cur);
			cur++;
			
			if (res < max) {
				aris[res] = ari;
				blocks[res] = block;
				res++;
			} else {
				break;
			}
		}
		
		return res;
	}
}

