package yuku.alkitab.yes2;

import android.util.Log;

import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import yuku.alkitab.yes2.io.RandomOutputStream;
import yuku.alkitab.yes2.section.base.SectionContent;
import yuku.bintex.BintexWriter;
import yuku.bintex.ValueMap;

/**
 * YES version 2 file format
 * 
 * Note: Below, uint8, char8, and byte are the same thing, only interpreted differently.
 * Note: value is Bintex value type
 * Note: int is 32-bit integer
 * 
 * {
 * uint8[8] header = 0x98 0x58 0x0d 0x0a 0x00 0x5d 0xe0 0x02 // version 2
 * int section_count
 * uint8 sectionIndexVersion = 1
 * int sectionIndex.size
 * {
 * uint8 sectionName.length
 * char8[sectionName.length] sectionName
 * int offset // from start of sections
 * int attributes_size
 * int content_size
 * byte[4] reserved
 * }[section_count] sectionIndex
 * {
 * value section.attributes
 * byte[section.content.size] section.content
 * }[section_count] sections
 * }
 * uint8 footer = 0
 */
public class Yes2Writer {
	private static final String TAG = Yes2Writer.class.getSimpleName();

	private static final byte YES_VERSION = 0x02;
	private static final byte[] YES_HEADER = { (byte) 0x98, 0x58, 0x0d, 0x0a, 0x00, 0x5d, (byte) 0xe0, YES_VERSION };

	private static Boolean androidLogPossible = null;

	private static void alog(long pos, String msg) {
		alog("[pos=" + pos + "] " + msg, null);
	}

	private static void alog(String msg, Throwable t) {
		if (androidLogPossible == null) {
			try {
				Class.forName("android.util.Log");
				androidLogPossible = true;
			} catch (Exception e) {
				androidLogPossible = false;
			}
		}

		if (androidLogPossible) {
			if (t != null) {
				Log.e(TAG, msg, t);
			} else {
				Log.d(TAG, msg);
			}
		} else {
			System.err.println(msg);
			if (t != null) {
				t.printStackTrace();
			}
		}
	}

	public List<SectionContent> sections = new ArrayList<SectionContent>();

	public void writeToFile(RandomAccessFile file) throws Exception {
		BintexWriter bw = new BintexWriter(new RandomOutputStream(file));
		
		int section_count = sections.size();
		
		////////// HEADERS //////////
		
		alog(file.getFilePointer(), "write yes header");
		bw.writeRaw(YES_HEADER);
		
		alog(file.getFilePointer(), "write section_count: " + section_count);
		bw.writeInt(section_count);

		///////// SECTION INDEX ///////////
		
		long savedpos_sectionIndexSize = -1;
		long savedpos_startOfSectionIndex = -1;
		long[] savedpos_sectionIndexSizeEntries = new long[section_count];
		long savedpos_startOfSections = -1;
		int[] saved_sectionOffset = new int[section_count];
		int[] savedsizes_sectionAttributes = new int[section_count];
		int[] savedsizes_sectionContent = new int[section_count];
		
		alog(file.getFilePointer(), "write sectionIndexVersion: 1");
		bw.writeUint8(1);
		
		{ // section index size is not known, just save position and reserve bytes
			savedpos_sectionIndexSize = file.getFilePointer();
			bw.writeInt(-1); // for sectionIndex size
		}
		
		savedpos_startOfSectionIndex = file.getFilePointer();
		
		for (int i = 0; i < section_count; i++) {
			SectionContent section = sections.get(i);
			
			{ // section name
				String name = section.getName();
				byte[] name_bytes = section.getNameAsBytesWithLength();
				alog(file.getFilePointer(), "write sectionName: " + name);
				bw.writeRaw(name_bytes);
			}
			
			{ // sizes are not known, just save position and reserve bytes
				savedpos_sectionIndexSizeEntries[i] = file.getFilePointer();
				bw.writeInt(-1); // for offset
				bw.writeInt(-1); // for attributes_size
				bw.writeInt(-1); // for content_size
				bw.writeInt(0); // for reserved
			}
		}
		
		{ // now we know the size of the section index, write it into the placeholder
			long lastPos = file.getFilePointer();
			file.seek(savedpos_sectionIndexSize);
			int size = (int) (lastPos - savedpos_startOfSectionIndex);
			alog(file.getFilePointer(), "write section index size: " + size);
			bw.writeInt(size);
			file.seek(lastPos);
		}
		
		//////////////// SECTION ATTRIBUTES AND CONTENT /////////////////
		
		savedpos_startOfSections = file.getFilePointer();
		
		for (int i = 0; i < section_count; i++) {
			SectionContent section = sections.get(i);
			String section_name = section.getName();
			
			saved_sectionOffset[i] = (int) (file.getFilePointer() - savedpos_startOfSections);

			{ // attributes
				int posBeforeAttributes = (int) file.getFilePointer();
				alog(file.getFilePointer(), "write section attributes: " + section_name);
				ValueMap attributes = section.getAttributes();
				bw.writeValueSimpleMap(attributes == null? new ValueMap(): attributes);
				savedsizes_sectionAttributes[i] = (int) file.getFilePointer() - posBeforeAttributes;
			}
			
			{ // contents
				int posBeforeContent = (int) file.getFilePointer();
				alog(file.getFilePointer(), "write section content: " + section_name);
				((SectionContent.Writer) section).write(bw);
				savedsizes_sectionContent[i] = (int) file.getFilePointer() - posBeforeContent;
			}
		}
		
		//////////// WRITE INTO PLACEHOLDERS ////////////////
		
		long lastPos = file.getFilePointer();

		{ // offsets and sizes in the section index
			for (int i = 0; i < section_count; i++) {
				SectionContent section = sections.get(i);
				String section_name = section.getName();
				
				int offset = saved_sectionOffset[i];
				int attributes_size = savedsizes_sectionAttributes[i];
				int content_size = savedsizes_sectionContent[i];
				
				file.seek(savedpos_sectionIndexSizeEntries[i]);
				alog(file.getFilePointer(), "write offset, attributes_size, content_size of section " + section_name + ": " + offset + ", " + attributes_size + ", " + content_size); 
				bw.writeInt(offset);
				bw.writeInt(attributes_size);
				bw.writeInt(content_size);
			}
		}
		
		file.seek(lastPos);

		alog(file.getFilePointer(), "write footer");
		bw.writeUint8(0);
		bw.close();
		
		alog(file.getFilePointer(), "done");
	}

}
