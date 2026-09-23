package yuku.alkitabconverter.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DesktopVerseParserTest {
	@Test
	public void testVerseStringToAri() {
		test("John 3:16", "0x2a0310 .");
		test("John 3.16", "0x2a0310 .");
		test("John 3:16-18", "0x2a0310 0x2a0312");
		test("John 3.16-18", "0x2a0310 0x2a0312");
		test("John 3:16-3:18", "0x2a0310 0x2a0312");
		test("John 3:16-4:18", "0x2a0310 0x2a0412");
		test("Kejadian 1:1", "0x000101 0x000101");
		test("Kej 1:1 dan 2", "0x000101 . 0x000102 .");
		test("Gen 1:1,3,5", "0x000101 . 0x000103 . 0x000105 .");

		// one chapter books
		test("jud 9", "0x400109 .");
		test("jud 9-12", "0x400109 0x40010c");
		test("jud 1:9", "0x400109 .");
		test("jud 1:9-12", "0x400109 0x40010c");

		// not one chapter books
		test("ps 9", "0x120900 .");
		test("ps 9-12", "0x120900 0x120c00");
		test("ps 9–12", "0x120900 0x120c00"); // with endash
		test("ps 9—12", "0x120900 0x120c00"); // with emdash
		test("ps 1:9", "0x120109 .");
		test("ps 1:9-12", "0x120109 0x12010c");
		test("ps 1:9–12", "0x120109 0x12010c"); // with endash
		test("ps 1:9—12", "0x120109 0x12010c"); // with emdash

		// multiple
		test("gn 2:2, 5, 8-32", "0x000202 . 0x000205 . 0x000208 0x000220");
		test("gn 2:2, 5, 8—32", "0x000202 . 0x000205 . 0x000208 0x000220"); // with emdash

		// new abbr
		test("wah 9:9", "0x410909 .");
		test("zak 9:9", "0x250909 .");

		// single chapter book whole book
		test("oba 0", "0x1E0100 .");
		test("philemon 0", "0x380100 .");
		test("2 john 0", "0x3E0100 .");
		test("3 john 0", "0x3F0100 .");
		test("jud 0", "0x400100 .");

		// bug
		test("Yl. 2:10, 31, 3:15", "0x1c020a . 0x1c021f . 0x1c030f .");
	}

	private void test(final String ref, final String s) {
		final IntArrayList parsed = DesktopVerseParser.verseStringToAri(ref);

		final String[] aris2 = s.split(" ");
		final IntArrayList expected = new IntArrayList();
		for (final String aris : aris2) {
			if (aris.equals(".")) { // repeat last
				expected.add(expected.get(expected.size() - 1));
			} else {
				expected.add(Integer.decode(aris));
			}
		}

		assertEquals(expected, parsed);
	}
}
