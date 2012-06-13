package yuku.alkitab.base.sv;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import yuku.afw.App;
import yuku.alkitab.base.rpc.SimpleHttpConnection;

public class DownloadService extends Service {
	public static final String TAG = DownloadService.class.getSimpleName();

	private static final int MSG_progress = 1;
	private static final int MSG_stateChanged = 2;
	private static final int MSG_stopSelf = 3;

	private LinkedHashMap<String, DownloadEntry> db = new LinkedHashMap<String, DownloadEntry>();
	private ExecutorService executor = Executors.newFixedThreadPool(3);
	/** waiting or running */
	private AtomicInteger nwaiting = new AtomicInteger(0);
	private DownloadListener listener;
	
	private Handler handler = new Handler() {
		@Override public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_stopSelf:
				stopSelf();
				return;
			case MSG_progress:
				if (listener != null) {
					listener.onProgress((DownloadEntry) msg.obj);
				}
				return;
			case MSG_stateChanged:
				if (listener != null) {
					listener.onStateChanged((DownloadEntry) msg.obj);
				}
				return;
			}
		}
	};
	
	public interface DownloadListener {
		void onStateChanged(DownloadEntry entry);
		void onProgress(DownloadEntry entry);
	}
	
	public enum State {
		created,
		downloading,
		finished,
		failed,
		;
	}
	
	public static class DownloadEntry {
		public String key;
		public State state;
		public String url;
		public File completeFile;
		public File tempFile;
		public long progress;
		public long length;
		public String errorMsg;
	}
	
	@Override public IBinder onBind(Intent intent) {
		return new DownloadBinder();
	}
	
	@Override public int onStartCommand(Intent intent, int flags, int startId) {
		return START_NOT_STICKY;
	}
	
	public void setDownloadListener(DownloadListener listener) {
		this.listener = listener;
	}
	
	public DownloadEntry getEntry(String key) {
		return db.get(key);
	}
	
	public boolean removeEntry(String key) {
		DownloadEntry entry = db.get(key);
		if (entry == null) {
			return false;
		}
		
		if (entry.state == State.downloading) {
			return false;
		}
		
		db.remove(key);
		// TODO cancel download, delete tmp file, etc.
		return true;
	}
	
	public boolean startDownload(String key, String url, String completeFile) {
		if (db.get(key) != null) {
			return false;
		}
		DownloadEntry e = new DownloadEntry();
		e.key = key;
		e.state = State.created;
		e.url = url;
		e.completeFile = new File(completeFile);
		e.tempFile = new File(completeFile + ".part." + System.nanoTime() + ".tmp");
		e.length = -1;
		e.progress = 0;
		enqueueAndStart(e);
		return true;
	}
	
	private void enqueueAndStart(DownloadEntry e) {
		db.put(e.key, e);
		dispatchStateChanged(e);
		dispatchProgress(e);
		incrementWaiting();
		startService(new Intent(App.context, DownloadService.class)); // start self so it's not killed
		executor.submit(new DownloadTask(e));
	}
	
	class DownloadTask implements Callable<DownloadEntry> {
		DownloadEntry entry;
		
		public DownloadTask(DownloadEntry e) {
			this.entry = e;
		}
		
		void changeState(State newState) {
			entry.state = newState;
			dispatchStateChanged(entry);
		}

		@Override public DownloadEntry call() throws Exception {
			try {
				FileOutputStream tempOut = new FileOutputStream(entry.tempFile);
				
				// download
				SimpleHttpConnection conn = new SimpleHttpConnection(entry.url);
				try {
					InputStream is = conn.load();
					if (is == null) {
						throw conn.getException();
					}
					
					changeState(State.downloading);
					entry.progress = conn.getContentLength();
					dispatchProgress(entry);
					
					byte[] buf = new byte[16384];
					while (true) {
						int read = is.read(buf);
						if (read < 0) break;
						tempOut.write(buf, 0, read);
						entry.progress += read;
						dispatchProgress(entry);
						Log.d(TAG, "Entry " + entry.key + " progress " + entry.progress + "/" + entry.length);
					}
					tempOut.close();
				} finally {
					conn.close();
				}
				
				// move
				entry.completeFile.delete();
				boolean renameOk = entry.tempFile.renameTo(entry.completeFile);
				if (!renameOk) {
					Log.w(TAG, "Failed to rename file from " + entry.tempFile + " to " + entry.completeFile);
					entry.errorMsg = "Failed to rename temporary file";
					changeState(State.failed);
					return entry;
				}
				
				// finished successfully
				changeState(State.finished);
			} catch (Exception e) {
				Log.w(TAG, "Failed download because of exception", e);
				entry.tempFile.delete();
				entry.errorMsg = e.getClass().getSimpleName() + " " + e.getMessage();
				changeState(State.failed);
			} finally {
				decrementWaitingAndCheck();
			}
			return entry;
		}
	}

	public class DownloadBinder extends Binder {
		public DownloadService getService() {
			return DownloadService.this;
		}
	}

	private void incrementWaiting() {
		nwaiting.incrementAndGet();
		Log.d(TAG, "(inc) now nwaiting is " + nwaiting);
	}

	public synchronized void decrementWaitingAndCheck() {
		int newValue = nwaiting.decrementAndGet();
		
		if (newValue == 0) {
			Message.obtain(handler, MSG_stopSelf).sendToTarget();
		}
		
		Log.d(TAG, "(dec) now nwaiting is " + nwaiting);
	}
	
	public void dispatchProgress(DownloadEntry entry) {
		Message.obtain(handler, MSG_progress, entry).sendToTarget();
	}
	
	public void dispatchStateChanged(DownloadEntry entry) {
		Message.obtain(handler, MSG_stateChanged, entry).sendToTarget();
	}
}
