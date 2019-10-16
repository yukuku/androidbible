package yuku.alkitabintegration.test;

import android.content.Intent;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import yuku.alkitabintegration.display.Launcher;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class LauncherTest {
	@Test
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
