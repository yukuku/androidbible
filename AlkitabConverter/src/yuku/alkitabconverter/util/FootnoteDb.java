package yuku.alkitabconverter.util;

import yuku.alkitab.base.model.FootnoteEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class FootnoteDb {
	public static interface FootnoteProcessor {
		void process(FootnoteEntry fe, int ari, int entryIndex);
	}

	final Map<Integer, List<FootnoteEntry>> map = new TreeMap<Integer, List<FootnoteEntry>>();

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
}
