package yuku.alkitabintegration.test;

import android.content.ContentResolver;
import android.test.AndroidTestCase;
import yuku.alkitabintegration.AlkitabIntegrationUtil;
import yuku.alkitabintegration.ConnectionResult;
import yuku.alkitabintegration.provider.VerseProvider;
import yuku.alkitabintegration.provider.VerseProvider.Verse;
import yuku.alkitabintegration.provider.VerseProvider.VerseRanges;

import java.util.List;

public class VerseProviderTest extends AndroidTestCase {

	@Override
	public void setUp() throws Exception {
		AlkitabIntegrationUtil.setOverridenProviderAuthority("yuku.alkitab.debug.provider");
	}

	public void testAvailable() throws Exception {
		assertEquals(ConnectionResult.SUCCESS, AlkitabIntegrationUtil.isIntegrationAvailable(getContext()));
	}

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

		// Gen 1:31 should have text
		assertNotNull(vp.getVerse(0x00011f));

		// Gen 1:32 should be null
		assertNull(vp.getVerse(0x000120));
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
			for (Verse verse : verses) {
				assertFalse(verse.text.contains("@"));
			}
		}
	}
}
