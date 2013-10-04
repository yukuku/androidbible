package yuku.alkitabconverter.util;

import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.model.XrefEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class XrefDb {
	public static interface XrefProcessor {
		void process(XrefEntry xe, int ari, int entryIndex);
	}
	
	Map<Integer, List<XrefEntry>> map = new TreeMap<Integer, List<XrefEntry>>();

	/**
	 * @return index of xref for this ari, starts from 0.
	 */
	public int addBegin(int ari) {
		List<XrefEntry> list = map.get(ari);
		if (list == null) {
			list = new ArrayList<XrefEntry>();
			map.put(ari, list);
		}
		
		XrefEntry xe = new XrefEntry();
		list.add(xe);

		return list.size() - 1;
	}
	
	/** must be after addBegin */
	public void appendText(int ari, String text) {
		List<XrefEntry> list = map.get(ari);
		if (list == null) {
			throw new RuntimeException("Must be after addBegin (1)");
		}
		
		XrefEntry xe = list.get(list.size() - 1);
		if (xe.content == null) {
			xe.content = text;
		} else {
			xe.content += text;
		}
	}
	
	/**
	 * Combines addBegin and appendText into a single call.
	 * @param content The complete xref between \x and \x*. e.g. "+ Joh 3:16; Joh 16:29"
	 * @return index of xref for this ari, starts from 0.
	 */
	public int addComplete(final int ari, final String content) {
		final String target;

		// remove marker [a-zA-Z+-]<space> at the beginning
		if (content.matches("[a-zA-Z+-]\\s.*")) {
			// remove that first 2 characters
			target = content.substring(2);
		} else {
			target = content;
		}

		List<XrefEntry> list = map.get(ari);
		if (list == null) {
			list = new ArrayList<XrefEntry>();
			map.put(ari, list);
		}

		XrefEntry xe = new XrefEntry();
		list.add(xe);

		xe.content = target;

		return list.size() - 1;
	}

	public void dump() {
		for (Map.Entry<Integer, List<XrefEntry>> e: map.entrySet()) {
			List<XrefEntry> xes = e.getValue();
			for (int i = 0; i < xes.size(); i++) {
				XrefEntry xe = xes.get(i);
				System.out.printf("xref 0x%06x(%d): [%s]%n", e.getKey(), i + 1, xe.content);
			}
		}
	}
	
	public void processEach(XrefProcessor processor) {
		for (Map.Entry<Integer, List<XrefEntry>> e: map.entrySet()) {
			List<XrefEntry> xes = e.getValue();
			for (int i = 0; i < xes.size(); i++) {
				XrefEntry xe = xes.get(i);
				processor.process(xe, e.getKey(), i);
			}
		}
	}

	public static XrefProcessor defaultShiftTbProcessor = new XrefDb.XrefProcessor() {
		@Override public void process(XrefEntry xe, int ari_location, int entryIndex) {
			final List<int[]> pairs = new ArrayList<int[]>();
			DesktopVerseFinder.findInText(xe.content, new DesktopVerseFinder.DetectorListener() {
				@Override public boolean onVerseDetected(int start, int end, String verse) {
					pairs.add(new int[] {start, end});
					return true;
				}

				@Override public void onNoMoreDetected() {
				}
			});

			String target = xe.content;
			for (int i = pairs.size() - 1; i >= 0; i--) {
				int[] pair = pairs.get(i);
				String verse = target.substring(pair[0], pair[1]);

				IntArrayList ariRanges = DesktopVerseParser.verseStringToAriWithShiftTb(verse);
				if (ariRanges == null || ariRanges.size() == 0) {
					throw new RuntimeException("verse cannot be parsed: " + verse);
				}

				{ // we need to process 00 verses (entire chapter) to 1 for start and the last verse for end.
					boolean isStart = true;
					for (int j = 0; j < ariRanges.size(); j++, isStart = !isStart) {
						int ari2 = ariRanges.get(j);
						if (Ari.toVerse(ari2) == 0) {
							if (isStart) {
								ari2 = Ari.encodeWithBc(Ari.toBookChapter(ari2), 1);
							} else {
								int lastVerse_1 = KjvUtils.getVerseCount(Ari.toBook(ari2), Ari.toChapter(ari2));
								ari2 = Ari.encodeWithBc(Ari.toBookChapter(ari2), lastVerse_1);
							}
						}
						ariRanges.set(j, ari2);
					}
				}

				{
					StringBuilder ariRanges_s = new StringBuilder();
					boolean isStart = true;
					boolean endWritten = false; // this can be set to true in isstart portion if the end verse does not need to be written

					for (int j = 0; j < ariRanges.size(); j++, isStart = !isStart) {
						final int ari_target = ariRanges.get(j);

						{ // just for checking
							int lid = KjvUtils.ariToLid(ari_target);
							if (lid <= 0) throw new RuntimeException(String.format("invalid ari found 0x%06x", ari_target));
						}

						final String enc_value;
						if (isStart) {
							endWritten = false;
							final int ari_end = ariRanges.get(j + 1);

							enc_value = ariToString(ari_target);

							if (ari_end == ari_target) {
								endWritten = true;
							}
						} else {
							if (endWritten) { // already mentioned by start, no need to do anything
								enc_value = null;
							} else {
								enc_value = ariToString(ari_target);
							}
						}

						if (isStart) {
							if (ariRanges_s.length() != 0) {
								ariRanges_s.append(",");
							}
							ariRanges_s.append(enc_value);
						} else {
							if (enc_value != null) {
								ariRanges_s.append("-").append(enc_value);
							}
						}
					}

					target = target.substring(0, pair[0]) + "@<ta:" + ariRanges_s + "@>" + verse + "@/" + target.substring(pair[1]);
				}
			}

			xe.content = target;
		}

		private String ariToString(final int ari) {
			final String ari_target_hex = "0x" + Integer.toHexString(ari);
			final String ari_target_dec = Integer.toString(ari);

			return ari_target_hex.length() <= ari_target_dec.length()? ari_target_hex: ari_target_dec;
		}
	};
}
