package yuku.kirimfidbek;

import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import com.squareup.okhttp.FormEncodingBuilder;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import yuku.afw.App;
import yuku.afw.storage.Preferences;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class CrashReporter {
	public static final String TAG = CrashReporter.class.getSimpleName();

	static class Entry {
		String body;
		int versionCode;
		int timestamp;
		int versionSdk;
		String capjempol;

		public Entry(String body, int versionCode, int timestamp, int versionSdk, String capjempol) {
			this.body = body;
			this.versionCode = versionCode;
			this.timestamp = timestamp;
			this.versionSdk = versionSdk;
			this.capjempol = capjempol;
		}
	}

	final AtomicBoolean isSending = new AtomicBoolean();
	final CatchAllExceptions catchAllExceptions;
	List<Entry> entries;

	public CrashReporter() {
		getUniqueId(); // trigger creation of uniqueId.
		catchAllExceptions = new CatchAllExceptions();
	}

	public void activateDefaultUncaughtExceptionHandler() {
		catchAllExceptions.activate();
	}

	public void add(String body) {
		load();

		entries.add(new Entry(body, App.getVersionCode(), getTimestamp(), Build.VERSION.SDK_INT, null));

		save();
	}

	int getTimestamp() {
		return (int) (new Date().getTime() / 1000L);
	}

	synchronized void save() {
		if (entries == null) return;

		Preferences.hold();
		try {
			final int sz = entries.size();
			Preferences.setInt("crash_report/size", sz);

			for (int i = 0; i < sz; i++) {
				final Entry entry = entries.get(i);
				Preferences.setString("crash_report/" + i + "/body", entry.body);
				Preferences.setInt("crash_report/" + i + "/versionCode", entry.versionCode);
				Preferences.setInt("crash_report/" + i + "/timestamp", entry.timestamp);
				Preferences.setInt("crash_report/" + i + "/versionSdk", entry.versionSdk);
				Preferences.setString("crash_report/" + i + "/capjempol", entry.capjempol);
			}

			// remove old entries (save space and xml processing time)
			for (int i = sz; ; i++) {
				if (Preferences.contains("crash_report/" + i + "/body")) {
					Preferences.remove("crash_report/" + i + "/body");
					Preferences.remove("crash_report/" + i + "/versionCode");
					Preferences.remove("crash_report/" + i + "/timestamp");
					Preferences.remove("crash_report/" + i + "/versionSdk");
					Preferences.remove("crash_report/" + i + "/capjempol");
				} else {
					break;
				}
			}
		} finally {
			Preferences.unhold();
		}
	}

	public synchronized void trySend() {
		load();

		if (entries.size() == 0) {
			return;
		}

		if (!isSending.compareAndSet(false, true)) {
			return;
		}

		new Sender().start();
	}

	synchronized void load() {
		if (entries != null) {
			return;
		}

		entries = new ArrayList<>();
		final int size = Preferences.getInt("crash_report/size", 0);

		for (int i = 0; i < size; i++) {
			String body = Preferences.getString("crash_report/" + i + "/body", null);
			int versionCode = Preferences.getInt("crash_report/" + i + "/versionCode", 0);
			int timestamp = Preferences.getInt("crash_report/" + i + "/timestamp", 0);
			int versionSdk = Preferences.getInt("crash_report/" + i + "/versionSdk", 0);
			String capjempol = Preferences.getString("crash_report/" + i + "/capjempol", null);

			entries.add(new Entry(body, versionCode, timestamp, versionSdk, capjempol));
		}
	}

	class Sender extends Thread {
		public Sender() {
		}

		@Override
		public void run() {
			boolean success = false;

			Log.d(TAG, "tred pengirim dimulai. thread id = " + getId());

			try {
				final OkHttpClient client = new OkHttpClient();

				final Request.Builder request = new Request.Builder();
				request.url("http://www.kejut.com/prog/android/fidbek/kirim3.php");

				final FormEncodingBuilder form = new FormEncodingBuilder();
				for (Entry entry : entries) {
					if (entry.body.length() > 100000) {
						entry.body = entry.body.substring(0, 100000);
					}

					form
						.add("uniqueId[]", getUniqueId())
						.add("package_name[]", App.context.getPackageName())
						.add("fidbek_isi[]", entry.body)
						.add("package_versionCode[]", String.valueOf(entry.versionCode))
						.add("timestamp[]", String.valueOf(entry.timestamp))
						.add("build_product[]", Build.PRODUCT)
						.add("build_device[]", Build.DEVICE)
						.add("version_sdk[]", String.valueOf(entry.versionSdk))
						.add("capjempol[]", entry.capjempol);
				}

				request.post(form.build());

				final Response response = client.newCall(request.build()).execute();

				InputStream content = response.body().byteStream();
				ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);

				while (true) {
					byte[] b = new byte[4096];
					int read = content.read(b);

					if (read <= 0) break;
					baos.write(b, 0, read);
				}

				byte[] out = baos.toByteArray();

				if (out.length >= 2 && out[0] == 'O' && out[1] == 'K') {
					success = true;
				}
			} catch (IOException e) {
				Log.w(TAG, "when posting", e);
			}

			if (success) {
				synchronized (CrashReporter.this) {
					entries.clear();
				}

				save();
			}

			Log.d(TAG, "Sender thread finished. success: " + success);

			isSending.set(false);
		}
	}

	String getUniqueId() {
		String uniqueId = Preferences.getString("fidbek_uniqueId", null);
		if (uniqueId == null) {
			uniqueId = "u2:" + UUID.randomUUID().toString();
			Preferences.setString("fidbek_uniqueId", uniqueId);
		}
		return uniqueId;
	}

	public class CatchAllExceptions {
		public final String TAG = CatchAllExceptions.class.getSimpleName();

		private Thread.UncaughtExceptionHandler originalHandler;

		private Thread.UncaughtExceptionHandler handler = new Thread.UncaughtExceptionHandler() {
			@Override public void uncaughtException(Thread t, Throwable e) {
				final StringWriter sw = new StringWriter(4000);
				e.printStackTrace(new PrintWriter(sw, true));

				add("[DUEH2] thread: " + t.getName() + " (" + t.getId() + ") " + e.getClass().getName() + ": " + e.getMessage() + "\n" + sw.toString());
				trySend();

				// Try waiting for 3 seconds before letting the app completely crash
				SystemClock.sleep(3000);

				Log.w(TAG, "UncaughtExceptionHandler finished.");

				// call the original exception handler (force close dialog)
				if (originalHandler != null) {
					originalHandler.uncaughtException(t, e);
				}
			}
		};

		public void activate() {
			originalHandler = Thread.getDefaultUncaughtExceptionHandler();
			Thread.setDefaultUncaughtExceptionHandler(handler);
		}
	}
}
