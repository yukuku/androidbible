package yuku.stethoshim;

import android.app.Application;
import com.squareup.okhttp.OkHttpClient;

public class StethoShim {
	public static void initializeWithDefaults(final Application application) {
		// no-op
	}

	public static void addNetworkInterceptor(final OkHttpClient client) {
		// no-op
	}
}
