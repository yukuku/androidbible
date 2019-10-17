package yuku.alkitab.base.util;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;

public abstract class Foreground {
	static final Handler handler = new Handler(Looper.getMainLooper());

	public static void run(@NonNull Runnable r) {
		handler.post(r);
	}
}
