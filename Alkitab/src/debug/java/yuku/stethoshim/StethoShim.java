package yuku.stethoshim;

import android.app.Application;
import com.facebook.stetho.Stetho;
import com.facebook.stetho.okhttp.StethoInterceptor;
import com.squareup.okhttp.OkHttpClient;

public class StethoShim {
	public static void initializeWithDefaults(final Application application) {
		Stetho.initializeWithDefaults(application);
	}

	public static void addNetworkInterceptor(final OkHttpClient client) {
		client.networkInterceptors().add(new StethoInterceptor());
	}
}
