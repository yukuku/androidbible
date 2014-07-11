package yuku.alkitab.yes2;

import android.util.Log;
import yuku.alkitab.yes2.io.RandomOutputStream;
import yuku.alkitab.yes2.section.base.SectionContent;
import yuku.bintex.BintexWriter;
import yuku.bintex.ValueMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * YES version 2 file format
 * 
 * Note: Below, uint8, char8, and byte are the same thing, only interpreted differently.
 * Note: value is Bintex value type
 * Note: int is 32-bit integer
 * 
 * {
 * | uint8[8] header = 0x98 0x58 0x0d 0x0a 0x00 0x5d 0xe0 0x02 // version 2
 * | int sectionIndex.size
 * | uint8 sectionIndexVersion = 1
 * | int section_count
 * | {
 * | | uint8 sectionName.length
 * | | char8[sectionName.length] sectionName
 * | | int offset // from start of sections
 * | | int attributes_size
 * | | int content_size
 * | | byte[4] reserved
 * | }[section_count] sectionIndex
 * | {
 * | | value section.attributes
 * | | byte[section.content.size] section.content
 * | }[section_count] sections
 * | uint8 footer = 0
 * }
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

	public List<SectionContent> sections = new ArrayList<>();

	public void writeToFile(RandomOutputStream output) throws IOException {
		BintexWriter bw = new BintexWriter(output);
		
		int section_count = sections.size();
		
		////////// HEADERS //////////
		
		alog(output.getFilePointer(), "write yes header");
		bw.writeRaw(YES_HEADER);
		
		///////// SECTION INDEX ///////////
		
		long savedpos_sectionIndexSize;
		long savedpos_startOfSectionIndex;
		long[] savedpos_sectionIndexSizeEntries = new long[section_count];
		long savedpos_startOfSections;
		int[] saved_sectionOffset = new int[section_count];
		int[] savedsizes_sectionAttributes = new int[section_count];
		int[] savedsizes_sectionContent = new int[section_count];
		
		{ // section index size is not known, just save position and reserve bytes
			savedpos_sectionIndexSize = output.getFilePointer();
			bw.writeInt(-1); // for sectionIndex size
		}
		
		alog(output.getFilePointer(), "write sectionIndexVersion: 1");
		bw.writeUint8(1);
		
		alog(output.getFilePointer(), "write section_count: " + section_count);
		bw.writeInt(section_count);
		
		savedpos_startOfSectionIndex = output.getFilePointer();
		
		for (int i = 0; i < section_count; i++) {
			SectionContent section = sections.get(i);
			
			{ // section name
				String name = section.getName();
				byte[] name_bytes = section.getNameAsBytesWithLength();
				alog(output.getFilePointer(), "write sectionName: " + name);
				bw.writeRaw(name_bytes);
			}
			
			{ // sizes are not known, just save position and reserve bytes
				savedpos_sectionIndexSizeEntries[i] = output.getFilePointer();
				bw.writeInt(-1); // for offset
				bw.writeInt(-1); // for attributes_size
				bw.writeInt(-1); // for content_size
				bw.writeInt(0); // for reserved
			}
		}
		
		{ // now we know the size of the section index, write it into the placeholder
			long lastPos = output.getFilePointer();
			output.seek(savedpos_sectionIndexSize);
			int size = (int) (lastPos - savedpos_startOfSectionIndex);
			alog(output.getFilePointer(), "write section index size: " + size);
			bw.writeInt(size);
			output.seek(lastPos);
		}
		
		//////////////// SECTION ATTRIBUTES AND CONTENT /////////////////
		
		savedpos_startOfSections = output.getFilePointer();
		
		for (int i = 0; i < section_count; i++) {
			SectionContent section = sections.get(i);
			String section_name = section.getName();
			
			saved_sectionOffset[i] = (int) (output.getFilePointer() - savedpos_startOfSections);

			{ // attributes
				int posBeforeAttributes = (int) output.getFilePointer();
				alog(output.getFilePointer(), "write section attributes: " + section_name);
				ValueMap attributes = section.getAttributes();
				bw.writeValueSimpleMap(attributes == null? new ValueMap(): attributes);
				savedsizes_sectionAttributes[i] = (int) output.getFilePointer() - posBeforeAttributes;
			}
			
			{ // contents
				int posBeforeContent = (int) output.getFilePointer();
				alog(output.getFilePointer(), "write section content: " + section_name);
				((SectionContent.Writer) section).write(output);
				savedsizes_sectionContent[i] = (int) output.getFilePointer() - posBeforeContent;
			}
		}
		
		//////////// WRITE INTO PLACEHOLDERS ////////////////
		
		long lastPos = output.getFilePointer();

		{ // offsets and sizes in the section index
			for (int i = 0; i < section_count; i++) {
				SectionContent section = sections.get(i);
				String section_name = section.getName();
				
				int offset = saved_sectionOffset[i];
				int attributes_size = savedsizes_sectionAttributes[i];
				int content_size = savedsizes_sectionContent[i];
				
				output.seek(savedpos_sectionIndexSizeEntries[i]);
				alog(output.getFilePointer(), "write offset, attributes_size, content_size of section " + section_name + ": " + offset + ", " + attributes_size + ", " + content_size); 
				bw.writeInt(offset);
				bw.writeInt(attributes_size);
				bw.writeInt(content_size);
			}
		}
		
		output.seek(lastPos);

		alog(output.getFilePointer(), "write footer");
		bw.writeUint8(0);
		
		alog(output.getFilePointer(), "done");
		bw.close();
	}

}
