package yuku.alkitab.yes2.section.base;

import yuku.alkitab.yes2.io.RandomInputStream;
import yuku.alkitab.yes2.io.RandomOutputStream;
import yuku.bintex.ValueMap;

public class SectionContent {
	private final String name;
	private final byte[] nameAsBytesWithLength;
	private final ValueMap attributes;
	
	public SectionContent(String name) {
		this(name, null);
	}
	
	public SectionContent(String name, ValueMap attributes) {
		this.name = name;
		this.attributes = attributes;
		
		int len = name.length();
		if (len > 255) {
			throw new RuntimeException("section name " + name + " is longer than 255 characters");
		}
		
		byte[] bb = new byte[len + 1];
		bb[0] = (byte) len;
		for (int i = 0; i < len; i++) {
			char c = name.charAt(i);
			if (c > 0x00ff) {
				throw new RuntimeException("section name " + name + " is not a 8-bit only string");
			}
			bb[i + 1] = (byte) c; 
		}
		this.nameAsBytesWithLength = bb;
	}
	
	public String getName() {
		return name;
	}
	
	public byte[] getNameAsBytesWithLength() {
		return nameAsBytesWithLength;
	}

	public ValueMap getAttributes() {
		return attributes;
	}

	public interface Writer {
		void write(RandomOutputStream output) throws Exception;
	}
	
	public interface Reader<T extends SectionContent> {
		T read(RandomInputStream input) throws Exception;
	}
}
