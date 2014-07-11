package yuku.alkitab.base.util;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.v4.util.AtomicFile;
import android.util.Log;
import com.squareup.okhttp.Call;
import yuku.alkitab.base.App;
import yuku.alkitab.debug.R;
import yuku.alkitab.io.OptionalGzipInputStream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.concurrent.Semaphore;

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
		void onDownloadProgress(Element e, int progress);
		void onDownloadCancelled(Element e);
	}

	public static String getYesPath() {
		return new File(Environment.getExternalStorageDirectory(), "bible/yes").getAbsolutePath(); //$NON-NLS-1$
	}

	/**
	 * @param yesName an added yes filename without the path.
	 */
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
		Semaphore sema = new Semaphore(0);
		LinkedList<Element> queue = new LinkedList<>();
		boolean isFinished = false;
		Handler mainHandler = new Handler(Looper.getMainLooper());

		public boolean isFinished() {
			return isFinished;
		}

		@Override
		public void run() {
			sema.acquireUninterruptibly();
			Log.d(TAG, "DownloadThread sema count: " + sema.availablePermits()); //$NON-NLS-1$

			while (true) {
				Element e;
				synchronized (this) {
					if (queue.size() == 0) {
						Log.d(TAG, "No more to download");
						break;
					}

					e = queue.poll();
				}

				download(e);
			}

			isFinished = true;
		}
		
		private void download(final Element e) {
			final AtomicFile atomicFile = new AtomicFile(new File(e.dest));
			atomicFile.delete(); // delete it first, just in case

			boolean mkdirOk = mkYesDir();
			if (!mkdirOk) {
				if (e.listener != null) {
					mainHandler.post(new Runnable() {
						@Override
						public void run() {
							e.listener.onDownloadFailed(e, App.context.getString(R.string.tidak_bisa_membuat_folder, getYesPath()), null);
						}
					});
				}
				return;
			}

			PowerManager pm = (PowerManager) App.context.getSystemService(Context.POWER_SERVICE);
			WakeLock wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "donlot"); //$NON-NLS-1$
			wakelock.setReferenceCounted(false);
			wakelock.acquire();

			FileOutputStream os = null;
			try {
				os = atomicFile.startWrite();

				final Call call = App.downloadCall(e.url);
				Log.d(TAG, "Start downloading " + e.url);

				final InputStream content = new OptionalGzipInputStream(call.execute().body().byteStream());
				try {
					byte[] b = new byte[4096 * 4];
					while (true) {
						int read = content.read(b);

						if (read <= 0) break;
						os.write(b, 0, read);

						e.downloaded += read;

						if (e.listener != null) {
							mainHandler.post(new Runnable() {
								@Override
								public void run() {
									e.listener.onDownloadProgress(e, e.downloaded);
								}
							});
						}

						if (e.cancelled) {
							call.cancel();
							atomicFile.failWrite(os);
							if (e.listener != null) {
								mainHandler.post(new Runnable() {
									@Override
									public void run() {
										e.listener.onDownloadCancelled(e);
									}
								});
							}
							os.close();
							return;
						}
					}
				} finally {
					content.close();
				}

				atomicFile.finishWrite(os);

				if (e.listener != null) {
					mainHandler.post(new Runnable() {
						@Override
						public void run() {
							e.listener.onDownloadFinished(e);
						}
					});
				}
				e.finished = true;
			} catch (final IOException ex) {
				atomicFile.failWrite(os);

				Log.w(TAG, "Error downloading", ex);
				if (e.listener != null) {
					mainHandler.post(new Runnable() {
						@Override
						public void run() {
							e.listener.onDownloadFailed(e, null, ex);
						}
					});
				}
			} finally {
				wakelock.release();
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
	
	public synchronized static DownloadThread getDownloadThread() {
		if (downloadThread == null || downloadThread.isFinished()) {
			downloadThread = new DownloadThread();
		}
		return downloadThread;
	}
}
