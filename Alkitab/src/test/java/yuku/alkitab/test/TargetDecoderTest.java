package yuku.alkitab.test;

import junit.framework.TestCase;
import yuku.alkitab.base.util.TargetDecoder;
import yuku.alkitab.util.IntArrayList;

public class TargetDecoderTest extends TestCase {
	void testDecode(String encoded, IntArrayList ariRanges) {
		assertEquals(ariRanges, TargetDecoder.decode(encoded));
	}

	public void testDecode() throws Throwable {
		IntArrayList ariRanges = new IntArrayList();
		ariRanges.clear();
		ariRanges.add(0x123);
		ariRanges.add(777);
		ariRanges.add(888888);
		ariRanges.add(888888);
		testDecode("a:0x123-777,888888", ariRanges);

		ariRanges.clear();
		ariRanges.add(0x000101);
		ariRanges.add(0x000201);
		ariRanges.add(0x000202);
		ariRanges.add(0x000202);
		testDecode("lid:1-32,33", ariRanges);

		ariRanges.clear();
		ariRanges.add(0x000105);
		ariRanges.add(0x000200);
		ariRanges.add(0x000404);
		ariRanges.add(0x000404);
		ariRanges.add(0x000505);
		ariRanges.add(0x000505);
		testDecode("o:Gen.1.5-Gen.2,Gen.4.4,Gen.5.5", ariRanges);
	}
}
