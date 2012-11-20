package yuku.alkitabfeedback;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Build.VERSION;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import yuku.capjempol.HitungCapJempol;

public class FeedbackSender {
	public static final String TAG = FeedbackSender.class.getSimpleName();

	public static interface OnSuccessListener {
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
		String capjempol;
	}

	static String presetInstallationId_;

	final Context context_;
	final SharedPreferences pref_;
	List<Entry> entries_;
	OnSuccessListener onSuccessListener_ = null;
	boolean sending_ = false;

	private static FeedbackSender instance;
	
	public static FeedbackSender getInstance(Context context) {
		if (instance != null) {
			return instance;
		}
		return new FeedbackSender(context);
	}
	
	public FeedbackSender(Context context) {
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
		e.capjempol = HitungCapJempol.hitung(context_);

		entries_.add(e);

		save();
	}

	synchronized void load() {
		if (entries_ == null) {
			final String base = "feedback/";

			entries_ = new ArrayList<Entry>();
			int nfeedback = pref_.getInt(base + "n", 0); //$NON-NLS-1$

			for (int i = 0; i < nfeedback; i++) {
				Entry e = new Entry();

				e.timestamp = pref_.getInt(base + i + "/timestamp", 0);
				e.feedback_id = pref_.getString(base + i + "/feedback_id", null);
				e.feedback_from_name = pref_.getString(base + i + "/feedback_from_name", null);
				e.feedback_from_email = pref_.getString(base + i + "/feedback_from_email", null);
				e.feedback_body = pref_.getString(base + i + "/feedback_body", null);
				e.package_versionCode = pref_.getInt(base + i + "/package_versionCode", 0);
				e.build_version_sdk = pref_.getInt(base + i + "/build_version_sdk", 0);
				e.capjempol = pref_.getString(base + i + "/capjempol", null);

				entries_.add(e);
			}
		}
	}

	synchronized void save() {
		if (entries_ == null) return;

		final String base = "feedback/";

		Editor editor = pref_.edit();
		{
			editor.putInt(base + "n", entries_.size()); //$NON-NLS-1$

			for (int i = 0; i < entries_.size(); i++) {
				Entry entry = entries_.get(i);
				editor.putInt(base + i + "/timestamp", entry.timestamp);
				editor.putString(base + i + "/feedback_id", entry.feedback_id);
				editor.putString(base + i + "/feedback_from_name", entry.feedback_from_name);
				editor.putString(base + i + "/feedback_from_email", entry.feedback_from_email);
				editor.putString(base + i + "/feedback_body", entry.feedback_body);
				editor.putInt(base + i + "/package_versionCode", entry.package_versionCode);
				editor.putInt(base + i + "/build_version_sdk", entry.build_version_sdk);
				editor.putString(base + i + "/capjempol", entry.capjempol);
			}
		}

		editor.commit();
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
				HttpClient client = new DefaultHttpClient();
				HttpPost post = new HttpPost("http://udayuku.appspot.com/laban/submit"); //$NON-NLS-1$
				List<NameValuePair> params = new ArrayList<NameValuePair>();

				for (Entry e : entries_) {
					params.add(new BasicNameValuePair("timestamp[]", "" + e.timestamp));
					params.add(new BasicNameValuePair("installationId[]", "" + getInstallationId()));
					params.add(new BasicNameValuePair("feedback_id[]", "" + e.feedback_id));
					params.add(new BasicNameValuePair("feedback_from_name[]", "" + e.feedback_from_name));
					params.add(new BasicNameValuePair("feedback_from_email[]", "" + e.feedback_from_email));
					params.add(new BasicNameValuePair("feedback_body[]", "" + e.feedback_body));
					params.add(new BasicNameValuePair("package_name[]", "" + context_.getPackageName()));
					params.add(new BasicNameValuePair("package_versionCode[]", "" + e.package_versionCode));
					params.add(new BasicNameValuePair("build_product[]", "" + getBuildProduct()));
					params.add(new BasicNameValuePair("build_device[]", "" + getBuildDevice()));
					params.add(new BasicNameValuePair("build_model[]", "" + getBuildModel()));
					params.add(new BasicNameValuePair("build_version_sdk[]", "" + e.build_version_sdk));
					params.add(new BasicNameValuePair("capjempol[]", "" + e.capjempol));
				}

				post.setEntity(new UrlEncodedFormEntity(params, "utf-8")); //$NON-NLS-1$
				HttpResponse response = client.execute(post);

				HttpEntity entity = response.getEntity();
				InputStream content = entity.getContent();
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
				Log.w(TAG, "when posting feedback", e); //$NON-NLS-1$
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
			Log.w(TAG, "package get versioncode", e); //$NON-NLS-1$
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
		if (presetInstallationId_ != null) return presetInstallationId_;

		String installationId = pref_.getString("installationId", null); //$NON-NLS-1$
		if (installationId == null) {
			installationId = "u2:" + UUID.randomUUID().toString(); //$NON-NLS-1$
			pref_.edit().putString("installationId", installationId).commit(); //$NON-NLS-1$
		}
		return installationId;
	}

	/**
	 * Set the uniqueId to use rather than the generated one
	 * 
	 * @param installationId
	 */
	public static void setPresetInstallationId(String installationId) {
		FeedbackSender.presetInstallationId_ = installationId;
	}
}
