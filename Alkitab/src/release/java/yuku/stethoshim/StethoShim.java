package yuku.stethoshim;

import android.app.Application;
import okhttp3.OkHttpClient;

public class StethoShim {
	public static void initializeWithDefaults(final Application application) {
		// no-op
	}

	public static void addNetworkInterceptor(final OkHttpClient.Builder builder) {
		// no-op
	}
}
