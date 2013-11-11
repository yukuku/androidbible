package yuku.alkitabconverter.util;

import yuku.alkitab.model.FootnoteEntry;
import yuku.bintex.BintexWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class FootnoteDb {
	public static interface FootnoteProcessor {
		void process(FootnoteEntry fe, int ari, int entryIndex);
	}

	final Map<Integer, List<FootnoteEntry>> map = new TreeMap<Integer, List<FootnoteEntry>>();

	public LinkedHashMap<Integer, FootnoteEntry> toEntries() {
		final LinkedHashMap<Integer, FootnoteEntry> res = new LinkedHashMap<Integer, FootnoteEntry>();
		processEach(new FootnoteProcessor() {
			@Override
			public void process(final FootnoteEntry fe, final int ari, final int entryIndex) {
				res.put(ari << 8 | (entryIndex + 1), fe);
			}
		});
		return res;
	}

	/**
	 * @return index of Footnote for this ari, starts from 0.
	 */
	public int addBegin(int ari) {
		List<FootnoteEntry> list = map.get(ari);
		if (list == null) {
			list = new ArrayList<FootnoteEntry>();
			map.put(ari, list);
		}

		FootnoteEntry fe = new FootnoteEntry();
		list.add(fe);

		return list.size() - 1;
	}

	/** must be after addBegin */
	public void appendText(int ari, String text) {
		List<FootnoteEntry> list = map.get(ari);
		if (list == null) {
			throw new RuntimeException("Must be after addBegin (1)");
		}

		final FootnoteEntry fe = list.get(list.size() - 1);
		if (fe.content != null) {
			fe.content += text;
		} else {
			fe.content = text;
		}
	}

	public void dump() {
		for (final Map.Entry<Integer, List<FootnoteEntry>> e: map.entrySet()) {
			final List<FootnoteEntry> fes = e.getValue();
			for (int i = 0; i < fes.size(); i++) {
				FootnoteEntry fe = fes.get(i);
				System.out.printf("Footnote 0x%06x(%d): [%s]%n", e.getKey(), i + 1, fe.content);
			}
		}
	}

	public void processEach(FootnoteProcessor processor) {
		for (Map.Entry<Integer, List<FootnoteEntry>> e: map.entrySet()) {
			List<FootnoteEntry> fes = e.getValue();
			for (int i = 0; i < fes.size(); i++) {
				FootnoteEntry fe = fes.get(i);
				processor.process(fe, e.getKey(), i);
			}
		}
	}

	public static void writeFootnoteEntriesTo(final LinkedHashMap<Integer, FootnoteEntry> footnoteEntries, final BintexWriter bw) throws IOException {
		// version
		bw.writeUint8(1);

		// entry_count
		bw.writeInt(footnoteEntries.size());

		// int arif[entry_count]
		for (final Map.Entry<Integer, FootnoteEntry> entry: footnoteEntries.entrySet()) {
			bw.writeInt(entry.getKey());
		}

		// try to calculate offset for each content. So we do the following
		ByteArrayOutputStream contents = new ByteArrayOutputStream();
		BintexWriter contentsBw = new BintexWriter(contents);

		// int offsets[entry_count]
		for (final Map.Entry<Integer, FootnoteEntry> entry: footnoteEntries.entrySet()) {
			bw.writeInt(contentsBw.getPos());
			contentsBw.writeValueString(entry.getValue().content);
		}

		// value<string> footnote_entry_contents[entry_count]
		bw.writeRaw(contents.toByteArray());
	}
}
