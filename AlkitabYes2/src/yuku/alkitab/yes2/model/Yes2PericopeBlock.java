package yuku.alkitab.yes2.model;

import java.io.IOException;

import yuku.alkitab.base.model.PericopeBlock;
import yuku.alkitab.yes2.io.RandomInputStream;
import yuku.bintex.BintexReader;

public class Yes2PericopeBlock extends PericopeBlock {

	/* Blok {
	 * uint8 version = 4
	 * value title
	 * uint8 parallel_count
	 * value[parallel_count] parallels
	 * }
	 */				
	
	public static Yes2PericopeBlock read(RandomInputStream input) throws IOException {
		BintexReader br = new BintexReader(input);
		
		int version = br.readUint8();
		if (version != 4) {
			throw new RuntimeException("Pericope block version not supported: " + version); //$NON-NLS-1$
		}
		
		Yes2PericopeBlock res = new Yes2PericopeBlock();
		res.title = br.readValueString();
		
		int parallel_count = br.readUint8();
		String[] parallels = res.parallels = new String[parallel_count];
		for (int i = 0; i < parallel_count; i++) {
			parallels[i] = br.readValueString();
		}
		
		return res;
	}
}
