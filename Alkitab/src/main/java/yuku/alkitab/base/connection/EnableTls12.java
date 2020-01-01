package yuku.alkitab.base.connection;

import android.os.Build;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.SSLContext;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
import okhttp3.TlsVersion;

// From https://github.com/square/okhttp/issues/2372#issuecomment-244807676

public class EnableTls12 {
	public static OkHttpClient.Builder enableTls12OnPreLollipop(OkHttpClient.Builder client) {
		if (Build.VERSION.SDK_INT <= 21) {
			try {
				SSLContext sc = SSLContext.getInstance("TLSv1.2");
				sc.init(null, null, null);
				client.sslSocketFactory(new Tls12SocketFactory(sc.getSocketFactory()));

				ConnectionSpec cs = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
					.tlsVersions(TlsVersion.TLS_1_2)
					.build();

				List<ConnectionSpec> specs = new ArrayList<>();
				specs.add(cs);
				specs.add(ConnectionSpec.COMPATIBLE_TLS);
				specs.add(ConnectionSpec.CLEARTEXT);

				client.connectionSpecs(specs);
			} catch (Exception exc) {
				Log.e("OkHttpTLSCompat", "Error while setting TLS 1.2", exc);
			}
		}

		return client;
	}
}
