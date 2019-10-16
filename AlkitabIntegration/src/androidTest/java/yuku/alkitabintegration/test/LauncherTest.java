package yuku.alkitabintegration.test;

import android.content.Intent;
import android.test.AndroidTestCase;

import yuku.alkitabintegration.display.Launcher;


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
	}
}
