package yuku.alkitab.base.util;

import android.support.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public abstract class Background {
	static final Executor executor = Executors.newCachedThreadPool();

	public static void run(@NonNull Runnable r) {
		executor.execute(r);
	}
}
