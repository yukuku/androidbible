package yuku.alkitab.yes2.section;

import android.util.Log;
import java.io.IOException;
import yuku.alkitab.model.FootnoteEntry;
import yuku.alkitab.yes2.io.RandomInputStream;
import yuku.alkitab.yes2.section.base.SectionContent;
import yuku.alkitab.yes2.util.UnsignedBinarySearchKt;
import yuku.bintex.BintexReader;

// The writer is in another class (not here to save code amount for Alkitab app)
// Section format:
// {
//   uint8 data_format_version = 1
//   int footnote_entry_count
//   int arifs[footnote_entry_count] // arif: 8bit LSB is index within one ari (starts from 1). 24bit MSB is the ari itself. (They must be already sorted).
//   int offsets[footnote_entry_count]
//   value<string> footnote_entry_contents[footnote_entry_count]
// }
public class FootnotesSection extends SectionContent {
	static final String TAG = FootnotesSection.class.getSimpleName();

	public static final String SECTION_NAME = "footnotes";

	// for reading:
	RandomInputStream input_;
	int entry_count;
	int[] index_arifs; // ari to pos
	int[] index_offset; // pos to content offset
	int content_start_offset; // file offset of the start of content

	FootnotesSection(final RandomInputStream input) throws Exception {
		super(SECTION_NAME);

		final BintexReader br = new BintexReader(input);

		final int version = br.readUint8();
		if (version != 1) {
			throw new RuntimeException("Footnotes section version not supported: " + version);
		}

		this.entry_count = br.readInt();

		this.index_arifs = new int[entry_count];
		for (int i = 0, len = entry_count; i < len; i++) {
			this.index_arifs[i] = br.readInt();
		}
		this.index_offset = new int[entry_count];
		for (int i = 0, len = entry_count; i < len; i++) {
			this.index_offset[i] = br.readInt();
		}

		this.content_start_offset = (int) input.getFilePointer();
		this.input_ = input;
	}

	public FootnoteEntry getFootnoteEntry(final int arif) {
		final int pos = UnsignedBinarySearchKt.unsignedIntBinarySearch(index_arifs, arif);
		if (pos < 0) {
			return null;
		}

		final int offset = index_offset[pos];
		final int abs_offset = content_start_offset + offset;
		try {
			final FootnoteEntry res = new FootnoteEntry();
			input_.seek(abs_offset);
			final BintexReader br = new BintexReader(input_);
			res.content = br.readValueString();
			// do not close br, input is still needed elsewhere
			return res;
		} catch (IOException e) {
			Log.e(TAG, "load footnote failed", e);
			return null;
		}
	}

	public static class Reader implements SectionContent.Reader<FootnotesSection> {
		@Override public FootnotesSection read(RandomInputStream input) throws Exception {
			return new FootnotesSection(input);
		}
	}
}

