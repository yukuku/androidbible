package yuku.alkitabconverter.util;

import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.model.XrefEntry;
import yuku.alkitab.base.util.Base64Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class XrefDb {
	public static interface XrefProcessor {
		void process(XrefEntry xe, int ari, int entryIndex);
	}
	
	Map<Integer, List<XrefEntry>> map = new TreeMap<Integer, List<XrefEntry>>();
	
	public void addBegin(int ari) {
		List<XrefEntry> list = map.get(ari);
		if (list == null) {
			list = new ArrayList<XrefEntry>();
			map.put(ari, list);
		}
		
		XrefEntry xe = new XrefEntry();
		list.add(xe);
	}
	
	/** must be after addBegin */
	public void addSource(int ari, String source) {
		List<XrefEntry> list = map.get(ari);
		if (list == null) {
			throw new RuntimeException("Must be after addBegin (1)");
		}
		
		XrefEntry xe = list.get(list.size() - 1);
		if (xe.source != null || xe.target != null) {
			throw new RuntimeException("Must be directly after addBegin (2)");
		}
		
		xe.source = source.trim();
	}
	
	/** must be after addSource */
	public void addTarget(int ari, String target) {
		List<XrefEntry> list = map.get(ari);
		if (list == null) {
			throw new RuntimeException("Must be after addSource (1)");
		}
		
		XrefEntry xe = list.get(list.size() - 1);
		if (xe.source == null || xe.target != null) {
			throw new RuntimeException("Must be directly after addSource (2)");
		}
		
		xe.target = target.trim();
	}

	/** Combines addBegin, addSource and addTarget into a single call.
	 * @param content The complete xref between \x and \x*. e.g. "+ Joh 3:16; Joh 16:29"
	 */
	public void addComplete(final int ari, final String content) {
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

		xe.source = null;
		xe.target = target;
	}

	public void dump() {
		for (Map.Entry<Integer, List<XrefEntry>> e: map.entrySet()) {
			List<XrefEntry> xes = e.getValue();
			for (int i = 0; i < xes.size(); i++) {
				XrefEntry xe = xes.get(i);
				System.out.printf("xref 0x%06x(%d): [%s] [%s]%n", e.getKey(), i, xe.source, xe.target);
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
		@Override public void process(XrefEntry xe, int ari, int entryIndex) {
			final List<int[]> pairs = new ArrayList<int[]>();
			DesktopVerseFinder.findInText(xe.target, new DesktopVerseFinder.DetectorListener() {
				@Override public boolean onVerseDetected(int start, int end, String verse) {
					pairs.add(new int[] {start, end});
					return true;
				}

				@Override public void onNoMoreDetected() {
				}
			});

			String target = xe.target;
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
					StringBuilder lid_s = new StringBuilder();
					int last_lid = 0;
					boolean isStart = true;
					boolean endWritten = false; // this can be set to true in isstart portion if the end verse does not need to be written

					for (int j = 0; j < ariRanges.size(); j++, isStart = !isStart) {
						int lid = KjvUtils.ariToLid(ariRanges.get(j));
						if (lid <= 0) throw new RuntimeException(String.format("invalid ari found 0x%06x", ariRanges.get(j)));

						int enc_value = -1; // just to prevent forgetting
						char[] chars;
						if (isStart) {
							endWritten = false;
							// 00 <addr 4bit> = 1char positive delta from last lid (max 16)
							// 01 <addr 4bit> = 1char positive delta from last lid (max 16), end == start
							// 100 <addr 9bit> = 2char positive delta from last lid (max 512)
							// 101 <addr 9bit> = 2char positive delta from last lid (max 512), end == start
							// 110 <addr 15bit> = 3char absolute
							// 111 <addr 15bit> = 3char absolute, end == start
							int lid_end = KjvUtils.ariToLid(ariRanges.get(j + 1));
							if (last_lid == 0 || (lid - last_lid) > 512 || (lid - last_lid) < 0) { // use 3 6-bit chars
								if (lid_end == lid) {
									enc_value = 0x38000 | lid;
									endWritten = true;
								} else {
									enc_value = 0x30000 | lid;
								}
								chars = Base64Mod.encodeToThreeChars(enc_value);
							} else if ((lid - last_lid) > 16) { // use 2 6-bit chars
								if (lid_end == lid) {
									enc_value = 0xa00 | (lid - last_lid);
									endWritten = true;
								} else {
									enc_value = 0x800 | (lid - last_lid);
								}
								chars = Base64Mod.encodeToTwoChars(enc_value);
							} else { // use 1 6-bit char
								if (lid_end == lid) {
									enc_value = 0x10 | (lid - last_lid);
									endWritten = true;
								} else {
									enc_value = (lid - last_lid);
								}
								chars = Base64Mod.encodeToOneChar(enc_value);
							}
						} else {
							// 0 <delta 5bit> = 1char positive delta from start (min 1, max 32)
							// 10 <delta 10bit> = 2char positive delta from start (max 1024)
							// 110 <addr 15bit> = 3char absolute
							if (endWritten) { // already mentioned by start, no need to do anything
								chars = new char[0];
							} else if ((lid - last_lid) > 1024) { // use 3 6-bit chars
								enc_value = 0x30000 | lid;
								chars = Base64Mod.encodeToThreeChars(enc_value);
							} else if ((lid - last_lid) > 32) { // use 2 6-bit chars
								enc_value = 0x800 | (lid - last_lid);
								chars = Base64Mod.encodeToTwoChars(enc_value);
							} else { // use 1 6-bit char
								enc_value = (lid - last_lid);
								chars = Base64Mod.encodeToOneChar(enc_value);
							}
						}
						lid_s.append(chars);
						// for debug lid_s.append("(").append(String.format("0x%x", enc_value)).append(")");

						last_lid = lid;
					}

					target = target.substring(0, pair[0]) + "@<t" + lid_s + "@>" + verse + "@/" + target.substring(pair[1]);
				}
			}

			xe.target = target;
		}
	};
}
