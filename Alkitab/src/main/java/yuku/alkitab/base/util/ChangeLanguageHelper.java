package yuku.alkitab.base.util;

import java.util.concurrent.atomic.AtomicInteger;

public class ChangeLanguageHelper {
	private static AtomicInteger localeSerialCounter = new AtomicInteger();

	public static int getLocaleSerialCounter() {
		return localeSerialCounter.get();
	}

	public static void notifyLocaleChanged() {
		localeSerialCounter.incrementAndGet();
	}
}
