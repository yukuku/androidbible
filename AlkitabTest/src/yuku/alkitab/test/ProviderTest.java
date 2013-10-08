package yuku.alkitab.test;

import android.test.AndroidTestCase;
import yuku.alkitabintegration.AlkitabIntegrationUtil;
import yuku.alkitabintegration.ConnectionResult;
import yuku.alkitabintegration.provider.VerseProvider;

import java.util.List;

public class ProviderTest extends AndroidTestCase {
	public void test() {
		assertEquals(ConnectionResult.SUCCESS, AlkitabIntegrationUtil.isIntegrationAvailable(getContext()));

		final VerseProvider vp = new VerseProvider(getContext().getContentResolver());
		assertNotNull(vp.getVerse(0x00000101));

		final VerseProvider.VerseRanges vr = new VerseProvider.VerseRanges();
		vr.addRange(0x000201, 0x000220);
		final List<VerseProvider.Verse> verses = vp.getVerses(vr);

		assertEquals(25 /* num of verses in Gen 2 */, verses.size());
	}
}
