package yuku.kirimfidbek;

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

public class PengirimFidbek {
	public static final String TAG = "KirimFidbek"; //$NON-NLS-1$

	public static interface OnSuccessListener {
		void onSuccess(byte[] response);
	}
	
	static class Entri {
		String isi;
		int versionCode;
		int timestamp;
		String versionSdk;
		String capjempol;

		public Entri(String isi, int versionCode, int timestamp, String versionSdk, String capjempol) {
			this.isi = isi;
			this.versionCode = versionCode;
			this.timestamp = timestamp;
			this.versionSdk = versionSdk;
			this.capjempol = capjempol;
		}
	}
	
	final Context context_;
	final SharedPreferences offlineBuffer_;
	TangkapSemuaEror tangkapSemuaEror_;
	List<Entri> xentri_;
	OnSuccessListener onSuccessListener_ = null;
	boolean lagiKirim_ = false;
	
	public PengirimFidbek(Context context, SharedPreferences offlineBuffer) {
		context_ = context;
		offlineBuffer_ = offlineBuffer;
		
		getUniqueId(); // picu pembuatan uniqueId.
	}
	
	public void setOnSuccessListener(OnSuccessListener onSuccessListener) {
		onSuccessListener_ = onSuccessListener;
	}
	
	public void activateDefaultUncaughtExceptionHandler() {
		tangkapSemuaEror_ = new TangkapSemuaEror(this);
		tangkapSemuaEror_.aktifkan();
	}

	public void tambah(String isi) {
		muat();

		xentri_.add(new Entri(isi, getVersionCode(), getTimestamp(), getVersionSdk(), HitungCapJempol.hitung(context_)));
		
		simpan();
	}

	int getTimestamp() {
		return (int)(new Date().getTime() / 1000L);
	}

	synchronized void simpan() {
		if (xentri_ == null) return;

		Editor editor = offlineBuffer_.edit();
		{
			editor.putInt("nfidbek", xentri_.size()); //$NON-NLS-1$

			for (int i = 0; i < xentri_.size(); i++) {
				Entri entri = xentri_.get(i);
				editor.putString("fidbek/" + i + "/isi", entri.isi); //$NON-NLS-1$ //$NON-NLS-2$
				editor.putInt("fidbek/" + i + "/versionCode", entri.versionCode); //$NON-NLS-1$ //$NON-NLS-2$
				editor.putInt("fidbek/" + i + "/timestamp", entri.timestamp); //$NON-NLS-1$ //$NON-NLS-2$
				editor.putString("fidbek/" + i + "/versionSdk", entri.versionSdk); //$NON-NLS-1$ //$NON-NLS-2$
				editor.putString("fidbek/" + i + "/capjempol", entri.capjempol); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		editor.commit();
	}

	public synchronized void cobaKirim() {
		muat();

		if (lagiKirim_ || xentri_.size() == 0) return;
		lagiKirim_ = true;

		new Pengirim().start();
	}

	synchronized void muat() {
		if (xentri_ == null) {
			xentri_ = new ArrayList<Entri>();
			int nfidbek = offlineBuffer_.getInt("nfidbek", 0); //$NON-NLS-1$

			for (int i = 0; i < nfidbek; i++) {
				String isi = offlineBuffer_.getString("fidbek/" + i + "/isi", null); //$NON-NLS-1$ //$NON-NLS-2$
				int versionCode = offlineBuffer_.getInt("fidbek/" + i + "/versionCode", 0); //$NON-NLS-1$ //$NON-NLS-2$
				int timestamp = offlineBuffer_.getInt("fidbek/" + i + "/timestamp", 0); //$NON-NLS-1$ //$NON-NLS-2$
				String versionSdk = offlineBuffer_.getString("fidbek/" + i + "/versionSdk", "0"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				String capjempol = offlineBuffer_.getString("fidbek/" + i + "/capjempol", null); //$NON-NLS-1$ //$NON-NLS-2$
				
				xentri_.add(new Entri(isi, versionCode, timestamp, versionSdk, capjempol));
			}
		}
	}

	class Pengirim extends Thread {
		public Pengirim() {
		}

		@Override
		public void run() {
			boolean berhasil = false;

			Log.d(TAG, "tred pengirim dimulai. thread id = " + getId()); //$NON-NLS-1$
			
			try {
				HttpClient client = new DefaultHttpClient();
				HttpPost post = new HttpPost("http://www.kejut.com/prog/android/fidbek/kirim3.php"); //$NON-NLS-1$
				ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();

				for (Entri entri: xentri_) {
					params.add(new BasicNameValuePair("uniqueId[]", getUniqueId())); //$NON-NLS-1$
					params.add(new BasicNameValuePair("package_name[]", context_.getPackageName())); //$NON-NLS-1$
					params.add(new BasicNameValuePair("fidbek_isi[]", entri.isi)); //$NON-NLS-1$
					params.add(new BasicNameValuePair("package_versionCode[]", String.valueOf(entri.versionCode))); //$NON-NLS-1$
					params.add(new BasicNameValuePair("timestamp[]", String.valueOf(entri.timestamp))); //$NON-NLS-1$
					params.add(new BasicNameValuePair("build_product[]", getBuildProduct())); //$NON-NLS-1$
					params.add(new BasicNameValuePair("build_device[]", getBuildDevice())); //$NON-NLS-1$
					params.add(new BasicNameValuePair("version_sdk[]", entri.versionSdk)); //$NON-NLS-1$
					params.add(new BasicNameValuePair("capjempol[]", entri.capjempol)); //$NON-NLS-1$
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
					berhasil = true;
				}

				if (onSuccessListener_ != null) {
					onSuccessListener_.onSuccess(out);
				}
			} catch (IOException e) {
				Log.w(TAG, "waktu post", e); //$NON-NLS-1$
			}
			
			if (berhasil) {
				synchronized (PengirimFidbek.this) {
					xentri_.clear();
				}

				simpan();
			}

			Log.d(TAG, "tred pengirim selesai. berhasil = " + berhasil); //$NON-NLS-1$

			lagiKirim_ = false;
		}
	}
	
	int getVersionCode() {
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

	public String getUniqueId() {
		String uniqueId = offlineBuffer_.getString("fidbek_uniqueId", null); //$NON-NLS-1$
		if (uniqueId == null) {
			uniqueId = "u2:" + UUID.randomUUID().toString(); //$NON-NLS-1$
			offlineBuffer_.edit().putString("fidbek_uniqueId", uniqueId).commit(); //$NON-NLS-1$
		}
		return uniqueId;
	}
	
	String getVersionSdk() {
		return VERSION.SDK;
	}

}
