package yuku.alkitabfeedback;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Build.VERSION;
import android.util.Log;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class FeedbackSender {
	public static final String TAG = FeedbackSender.class.getSimpleName();

	public interface OnSuccessListener {
		void onSuccess(byte[] response);
	}

	static class Entry {
		int timestamp;
		String feedback_id;
		String feedback_from_name;
		String feedback_from_email;
		String feedback_body;
		int package_versionCode;
		int build_version_sdk;
	}

	static final OkHttpClient client = new OkHttpClient();

	String overrideInstallationId_;

	final Context context_;
	final SharedPreferences pref_;
	List<Entry> entries_;
	OnSuccessListener onSuccessListener_ = null;
	boolean sending_ = false;

	private static FeedbackSender instance;
	
	public static FeedbackSender getInstance(Context context) {
		if (instance == null) {
			instance = new FeedbackSender(context);
		}
		return instance;
	}
	
	private FeedbackSender(Context context) {
		context_ = context;
		pref_ = context.getSharedPreferences("FeedbackSender", 0);
	}

	public void setOnSuccessListener(OnSuccessListener onSuccessListener) {
		onSuccessListener_ = onSuccessListener;
	}

	public void addEntry(String feedback_from_name, String feedback_from_email, String feedback_body) {
		load();

		Entry e = new Entry();
		e.timestamp = getTimestamp();
		e.feedback_id = "u2:" + UUID.randomUUID().toString();
		e.feedback_from_name = feedback_from_name;
		e.feedback_from_email = feedback_from_email;
		e.feedback_body = feedback_body;
		e.package_versionCode = getPackageVersionCode();
		e.build_version_sdk = getBuildVersionSdk();

		entries_.add(e);

		save();
	}

	synchronized void load() {
		if (entries_ == null) {
			final String base = "feedback/";

			entries_ = new ArrayList<>();
			int nfeedback = pref_.getInt(base + "n", 0);

			for (int i = 0; i < nfeedback; i++) {
				Entry e = new Entry();

				e.timestamp = pref_.getInt(base + i + "/timestamp", 0);
				e.feedback_id = pref_.getString(base + i + "/feedback_id", null);
				e.feedback_from_name = pref_.getString(base + i + "/feedback_from_name", null);
				e.feedback_from_email = pref_.getString(base + i + "/feedback_from_email", null);
				e.feedback_body = pref_.getString(base + i + "/feedback_body", null);
				e.package_versionCode = pref_.getInt(base + i + "/package_versionCode", 0);
				e.build_version_sdk = pref_.getInt(base + i + "/build_version_sdk", 0);

				entries_.add(e);
			}
		}
	}

	synchronized void save() {
		if (entries_ == null) return;

		final String base = "feedback/";

		Editor editor = pref_.edit();
		{
			editor.putInt(base + "n", entries_.size());

			for (int i = 0; i < entries_.size(); i++) {
				Entry entry = entries_.get(i);
				editor.putInt(base + i + "/timestamp", entry.timestamp);
				editor.putString(base + i + "/feedback_id", entry.feedback_id);
				editor.putString(base + i + "/feedback_from_name", entry.feedback_from_name);
				editor.putString(base + i + "/feedback_from_email", entry.feedback_from_email);
				editor.putString(base + i + "/feedback_body", entry.feedback_body);
				editor.putInt(base + i + "/package_versionCode", entry.package_versionCode);
				editor.putInt(base + i + "/build_version_sdk", entry.build_version_sdk);
			}
		}

		editor.apply();
	}

	public synchronized void trySend() {
		load();

		if (sending_ || entries_.size() == 0) return;
		sending_ = true;

		new Sender().start();
	}

	class Sender extends Thread {
		@Override public void run() {
			boolean success = false;

			Log.d(TAG, "feedback sending thread started");

			try {
				final FormBody.Builder form = new FormBody.Builder();
				for (Entry e : entries_) {
					form.add("timestamp[]", "" + e.timestamp);
					form.add("installationId[]", "" + getInstallationId());
					form.add("feedback_id[]", "" + e.feedback_id);
					form.add("feedback_from_name[]", "" + e.feedback_from_name);
					form.add("feedback_from_email[]", "" + e.feedback_from_email);
					form.add("feedback_body[]", "" + e.feedback_body);
					form.add("package_name[]", "" + context_.getPackageName());
					form.add("package_versionCode[]", "" + e.package_versionCode);
					form.add("build_product[]", "" + getBuildProduct());
					form.add("build_device[]", "" + getBuildDevice());
					form.add("build_model[]", "" + getBuildModel());
					form.add("build_version_sdk[]", "" + e.build_version_sdk);
				}


				final Response resp = client.newCall(new Request.Builder().url(BuildConfig.SERVER_HOST + "laban/submit").post(form.build()).build()).execute();
				final byte[] out = resp.body().bytes();

				if (out.length >= 2 && out[0] == 'O' && out[1] == 'K') {
					success = true;
				}

				if (onSuccessListener_ != null) {
					onSuccessListener_.onSuccess(out);
				}

				if (success) {
					synchronized (FeedbackSender.this) {
						entries_.clear();
					}

					save();
				}
			} catch (IOException e) {
				Log.w(TAG, "when posting feedback", e);
			} finally {
				Log.d(TAG, "feedback sending thread ended. success = " + success);
				sending_ = false;
			}
		}
	}

	int getTimestamp() {
		return (int) (new Date().getTime() / 1000L);
	}

	int getPackageVersionCode() {
		int versionCode = 0;
		try {
			versionCode = context_.getPackageManager().getPackageInfo(context_.getPackageName(), 0).versionCode;
		} catch (NameNotFoundException e) {
			Log.w(TAG, "package get versioncode", e);
		}
		return versionCode;
	}

	String getBuildProduct() {
		return Build.PRODUCT;
	}

	String getBuildDevice() {
		return Build.DEVICE;
	}

	String getBuildModel() {
		return Build.MODEL;
	}

	int getBuildVersionSdk() {
		return VERSION.SDK_INT;
	}

	String getInstallationId() {
		if (overrideInstallationId_ != null) return overrideInstallationId_;

		String installationId = pref_.getString("installationId", null);
		if (installationId == null) {
			installationId = "u2:" + UUID.randomUUID().toString();
			pref_.edit().putString("installationId", installationId).apply();
		}
		return installationId;
	}

	/**
	 * Set the uniqueId to use rather than the generated one
	 */
	public void setOverrideInstallationId(String installationId) {
		overrideInstallationId_ = installationId;
	}
}
