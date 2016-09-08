package yuku.alkitab.base.util;

import java.util.concurrent.atomic.AtomicInteger;

public class ChangeConfigurationHelper {
	private static AtomicInteger serialCounter = new AtomicInteger();

	public static int getSerialCounter() {
		return serialCounter.get();
	}

	public static void notifyConfigurationNeedsUpdate() {
		serialCounter.incrementAndGet();
	}
}
