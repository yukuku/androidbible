package yuku.stethoshim;

import android.app.Application;
import com.facebook.stetho.Stetho;
import com.facebook.stetho.okhttp3.StethoInterceptor;
import okhttp3.OkHttpClient;

public class StethoShim {
	public static void initializeWithDefaults(final Application application) {
		Stetho.initializeWithDefaults(application);
	}

	public static void addNetworkInterceptor(final OkHttpClient.Builder builder) {
		builder.addNetworkInterceptor(new StethoInterceptor());
	}
}
