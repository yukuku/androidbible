package yuku.alkitab;

import java.io.*;
import java.util.*;

import org.apache.http.*;
import org.apache.http.client.*;
import org.apache.http.client.entity.*;
import org.apache.http.client.methods.*;
import org.apache.http.impl.client.*;
import org.apache.http.message.*;

import android.content.*;
import android.content.SharedPreferences.*;
import android.content.pm.PackageManager.*;
import android.os.*;
import android.provider.*;
import android.util.*;
import android.widget.*;

public class PengirimFidbek {
	private final IsiActivity activity;
	private ArrayList<String> xisi;
	private boolean lagiKirim = false;

	public PengirimFidbek(IsiActivity activity) {
		this.activity = activity;
	}

	public void tambah(String isi) {
		muat();

		xisi.add(isi);
		simpan();
	}

	private synchronized void simpan() {
		if (xisi == null) return;

		SharedPreferences preferences = activity.getSharedPreferences(S.NAMA_PREFERENCES, 0);
		Editor editor = preferences.edit();

		editor.putInt("nfidbek", xisi.size());

		for (int i = 0; i < xisi.size(); i++) {
			editor.putString("fidbek/" + i + "/isi", xisi.get(i));
		}
		editor.commit();
	}

	public synchronized void cobaKirim() {
		muat();

		if (lagiKirim || xisi.size() == 0) return;
		lagiKirim = true;

		new Pengirim().start();
	}

	private synchronized void muat() {
		if (xisi == null) {
			xisi = new ArrayList<String>();
			SharedPreferences preferences = activity.getSharedPreferences(S.NAMA_PREFERENCES, 0);

			int nfidbek = preferences.getInt("nfidbek", 0);

			for (int i = 0; i < nfidbek; i++) {
				String isi = preferences.getString("fidbek/" + i + "/isi", null);
				if (isi != null) {
					xisi.add(isi);
				}
			}
		}
	}

	private class Pengirim extends Thread {
		@Override
		public void run() {
			boolean berhasil = false;

			try {
				HttpClient client = new DefaultHttpClient();
				HttpPost post = new HttpPost("http://www.kejut.com/prog/android/fidbek/kirim.php");
				ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
				params.add(new BasicNameValuePair("package_name", activity.getPackageName()));
				
				int versionCode = 0;
				try {
					versionCode = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionCode;
				} catch (NameNotFoundException e) {
					Log.w("alki", "package get versioncode", e);
				}
				params.add(new BasicNameValuePair("package_versionCode", String.valueOf(versionCode)));

				for (String isi : xisi) {
					params.add(new BasicNameValuePair("fidbek_isi[]", isi));
				}
				
				String uniqueId = Settings.Secure.getString(activity.getContentResolver(), Settings.Secure.ANDROID_ID);
				if (uniqueId == null) {
					uniqueId = "null;FINGERPRINT=" + Build.FINGERPRINT;
				}
				params.add(new BasicNameValuePair("uniqueId", uniqueId));
				
				post.setEntity(new UrlEncodedFormEntity(params, "utf-8"));
				HttpResponse response = client.execute(post);

				HttpEntity entity = response.getEntity();
				InputStream content = entity.getContent();

				while (true) {
					byte[] b = new byte[4096];
					int read = content.read(b);

					if (read <= 0) break;
					Log.i("PengirimFidbek", new String(b, 0, read));
				}

				berhasil = true;
				activity.handler.post(new Runnable() {
					
					@Override
					public void run() {
						Toast.makeText(activity, R.string.fidbekMakasih_s, Toast.LENGTH_SHORT).show();
					}
				});
			} catch (IOException e) {
				Log.w("PengirimFidbek", "waktu post", e);
			}

			if (berhasil) {
				synchronized (PengirimFidbek.this) {
					xisi.clear();
				}

				simpan();
			}

			lagiKirim = false;
		}
	}
}
