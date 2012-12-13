package yuku.alkitab.yes2.section;

import yuku.alkitab.yes2.model.PericopeData;
import yuku.alkitab.yes2.model.PericopeData.Entry;
import yuku.alkitab.yes2.section.base.SectionContent;
import yuku.bintex.BintexWriter;

public class PericopeIndex extends SectionContent implements SectionContent.Writer {
	private final PericopeData data;

	public PericopeIndex(PericopeData data) {
		super("pericopeData");
		this.data = data;
	}

	@Override public void write(BintexWriter writer) throws Exception {
		// uint8 version: 2
		writer.writeUint8(2);
		
		// int entry_count
		writer.writeInt(data.entries.size()); 
		
		// Entry[entry_count]
		for (Entry entry: data.entries) {
			if (entry.block._offset == -1) {
				throw new RuntimeException("offset of pericope entry has not been calculated"); // $NON-NLS-1$
			}
			
			writer.writeInt(entry.ari);
			writer.writeInt(entry.block._offset);
		}
	}
}

