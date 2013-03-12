package yuku.alkitabintegration.test;

import android.content.Intent;
import android.net.Uri;
import android.test.AndroidTestCase;

import yuku.alkitabintegration.display.Launcher;
import yuku.alkitabintegration.display.Launcher.Product;

public class LauncherTest extends AndroidTestCase {
	public void testOpenApp() {
		{
			Intent intent = Launcher.openAppAtBibleLocation(0x22, 0x01, 0x02);
			assertEquals("yuku.alkitab.action.VIEW", intent.getAction());
			assertEquals(0x220102, intent.getIntExtra("ari", -1));
		}
		
		{
			Intent intent = Launcher.openAppAtBibleLocation(0x121110);
			assertEquals("yuku.alkitab.action.VIEW", intent.getAction());
			assertEquals(0x121110, intent.getIntExtra("ari", -1));
		}
		
		{
			Intent intent = Launcher.openGooglePlayDownloadPage(getContext(), Product.ALKITAB);
			assertEquals("market", intent.getData().getScheme());
			assertEquals(Product.ALKITAB.getPackageName(), intent.getData().getQueryParameter("id"));
			assertEquals(getContext().getPackageName(), Uri.parse("whatever://whatever?" + intent.getData().getQueryParameter("referrer")).getQueryParameter("utm_medium"));
		}
	}
}
