package yuku.alkitab.base.util;

import android.content.Context;
import android.os.Environment;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.concurrent.Semaphore;
import java.util.zip.GZIPInputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import yuku.alkitab.R;

public class AddonManager {
	public static final String TAG = AddonManager.class.getSimpleName();
	public static class Element {
		public String url;
		public String dest;
		public int downloaded;
		public boolean finished;
		public DownloadListener listener;
		public boolean cancelled;
	}
	
	public interface DownloadListener {
		void onDownloadFinished(Element e);
		void onDownloadFailed(Element e, String caption, Throwable t);
		void onDownloadProgress(Element e, int progress, int total);
		void onDownloadCancelled(Element e);
	}

	public static String getYesPath() {
		return new File(Environment.getExternalStorageDirectory(), "bible/yes").getAbsolutePath(); //$NON-NLS-1$
	}
	
	public static String getVersionPath(String yesName) {
		String yesPath = getYesPath();
		File yes = new File(yesPath, yesName);
		return yes.getAbsolutePath();
	}
	
	public static boolean hasVersion(String yesName) {
		File f = new File(getVersionPath(yesName));
		return f.exists() && f.canRead();
	}
	
	public static class DownloadThread extends Thread {
		final Context appContext;
		Semaphore sema = new Semaphore(0);
		LinkedList<Element> queue = new LinkedList<Element>();
		
		public DownloadThread(Context appContext) {
			this.appContext = appContext;
		}
		
		@Override
		public void run() {
			while (true) {
				sema.acquireUninterruptibly();
				Log.d(TAG, "DownloadThread sema count: " + sema.availablePermits()); //$NON-NLS-1$
				
				while (true) {
					Element e;
					synchronized (this) {
						if (queue.size() == 0) {
							Log.d(TAG, "tiada lagi antrian donlot"); //$NON-NLS-1$
							break;
						}
						
						e = queue.poll();
					}
					
					download(e);
				}
			}
		}
		
		private void download(Element e) {
			new File(e.dest).delete(); // hapus dulu.. jangan2 kacau
			
			String tmpfile = e.dest + "-" + (int)(Math.random() * 100000) + ".tmp";  //$NON-NLS-1$//$NON-NLS-2$
			
			boolean mkdirOk = mkYesDir();
			if (!mkdirOk) {
				if (e.listener != null) e.listener.onDownloadFailed(e, appContext.getString(R.string.tidak_bisa_membuat_folder, getYesPath()), null);
				return;
			}
			
			PowerManager pm = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
			WakeLock wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "donlot"); //$NON-NLS-1$
			wakelock.setReferenceCounted(false);
			wakelock.acquire();
			
			try {
				FileOutputStream os = new FileOutputStream(tmpfile);
				
				HttpClient client = new DefaultHttpClient();
				HttpGet get = new HttpGet(e.url);
				
				Log.d(TAG, "Mulai donlot " + e.url); //$NON-NLS-1$
				HttpResponse response = client.execute(get);
				HttpEntity entity = response.getEntity();
				int length = (int) entity.getContentLength();
				
				Log.d(TAG, "Donlot sudah berjalan. Length: " + length); //$NON-NLS-1$
				InputStream content = entity.getContent();
				
				byte[] b = new byte[4096 * 4];
				while (true) {
					int read = content.read(b);

					if (read <= 0) break;
					os.write(b, 0, read);
					
					e.downloaded += read;
					if (e.listener != null) e.listener.onDownloadProgress(e, e.downloaded, length);
					
					if (e.cancelled) {
						get.abort();
						if (e.listener != null) e.listener.onDownloadCancelled(e);
						os.close();
						return;
					}
				}
				
				os.close();

				if (e.url.endsWith(".gz")) { //$NON-NLS-1$
					if (e.listener != null) e.listener.onDownloadProgress(e, -1, length); // tanda lagi dekompres
					
					GZIPInputStream in = new GZIPInputStream(new FileInputStream(tmpfile));
					String tmpfile2 = e.dest + (int)(Math.random() * 100000) + ".tmp2"; //$NON-NLS-1$
					FileOutputStream out = new FileOutputStream(tmpfile2);
			    
					try {
				        // Transfer bytes from the compressed file to the output file
				        byte[] buf = new byte[4096 * 4];
				        while (true) {
					        int len = in.read(buf);
					        if (len <= 0) break;
				            out.write(buf, 0, len);
				        }
				        
				        out.close();
				        
						boolean renameOk = new File(tmpfile2).renameTo(new File(e.dest));
						if (!renameOk) {
							Log.d(TAG, "Gagal rename!"); //$NON-NLS-1$
						}
					} finally {
						Log.d(TAG, "menghapus tmpfile2: " + tmpfile); //$NON-NLS-1$
						new File(tmpfile2).delete();
					}
			        in.close();
				} else {
					boolean renameOk = new File(tmpfile).renameTo(new File(e.dest));
					if (!renameOk) {
						Log.d(TAG, "Gagal rename!"); //$NON-NLS-1$
					}
				}
				
				if (e.listener != null) e.listener.onDownloadFinished(e);
				e.finished = true;
			} catch (IOException ex) {
				Log.w(TAG, "Gagal donlot", ex); //$NON-NLS-1$
				if (e.listener != null) e.listener.onDownloadFailed(e, null, ex);
			} finally {
				wakelock.release();
				
				Log.d(TAG, "deleting tmpfile: " + tmpfile); //$NON-NLS-1$
				new File(tmpfile).delete();
			}
		}

		public synchronized Element enqueue(String url, String dest, DownloadListener listener) {
			Element e = new Element();
			e.url = url;
			e.dest = dest;
			e.listener = listener;
			e.finished = false;
			
			queue.offer(e);
			sema.release(); // ijinkan jalan
			
			return e;
		}
	}
	
	public static boolean mkYesDir() {
		File dir = new File(getYesPath());
		if (!dir.exists()) {
			return new File(getYesPath()).mkdirs();
		} else {
			return true;
		}
	}
	
	private static DownloadThread downloadThread;
	
	public synchronized static DownloadThread getDownloadThread(Context appContext) {
		if (downloadThread == null) {
			downloadThread = new DownloadThread(appContext);
			downloadThread.start();
		}
		return downloadThread;
	}
}
