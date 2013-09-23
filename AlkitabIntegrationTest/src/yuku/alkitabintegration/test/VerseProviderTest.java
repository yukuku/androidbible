package yuku.alkitabintegration.test;

import android.content.ContentResolver;
import android.test.AndroidTestCase;
import yuku.alkitabintegration.provider.VerseProvider;
import yuku.alkitabintegration.provider.VerseProvider.Verse;
import yuku.alkitabintegration.provider.VerseProvider.VerseRanges;

import java.util.List;

import static junit.framework.Assert.*;

public class VerseProviderTest extends AndroidTestCase {
	public void testSingleVerse() throws Throwable {
		ContentResolver cr = getContext().getContentResolver();
		VerseProvider vp = new VerseProvider(cr);
		
		int ari = 0x000103;
		Verse v = vp.getVerse(0, 1, 3);
		assertNotNull(v);
		
		assertEquals(v.ari, ari);
		assertNotNull(v.bookName);
		assertNotNull(v.text);
		assertTrue(v.text.length() > 0);
		
		assertNotNull(v.toString());
		assertEquals(v.toString().substring(0, v.bookName.length()), v.bookName);
		
		assertNotNull(vp.getVerse(0x000132));
		
		// TODO this should return null instead of "[?]" 
		// assertNull(vp.getVerse(0x000133));
	}
	
	public void testVerseRanges() throws Throwable {
		ContentResolver cr = getContext().getContentResolver();
		VerseProvider vp = new VerseProvider(cr);
		
		VerseRanges ranges = new VerseRanges();
		
		{
			ranges.addRange(0, 1, 1, 0, 1, 1);
			List<Verse> verses = vp.getVerses(ranges);
			assertEquals(1, verses.size());
		}
		
		{
			ranges.addRange(0, 1, 1, 0, 1, 10);
			List<Verse> verses = vp.getVerses(ranges);
			assertEquals(11, verses.size());
		}
		
		{
			ranges.addRange(1, 2, 25, 1, 3, 1);
			List<Verse> verses = vp.getVerses(ranges);
			assertEquals(13, verses.size());
			assertEquals(0x010301, verses.get(12).ari);
			assertFalse(verses.get(0).bookName.equals(verses.get(11).bookName));
		}
		
		{ // should not have formatting codes
			ranges.addRange(2, 1, 1, 3, 1, 1);
			ranges.addRange(4, 1, 1, 4, 1, 1);
			ranges.addRange(60, 1, 1, 60, 1, 1);
			ranges.addRange(61, 1, 1, 61, 1, 1);
			ranges.addRange(62, 1, 1, 62, 1, 1);
			ranges.addRange(63, 1, 1, 63, 1, 1);
			ranges.addRange(64, 1, 1, 64, 1, 1);
			ranges.addRange(65, 1, 1, 65, 1, 1);
			List<Verse> verses = vp.getVerses(ranges);
			for (int i = 0, len = verses.size(); i < len; i++) {
				assertFalse(verses.get(i).text.contains("@"));
			}
		}
	}
}
