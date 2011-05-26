package yuku.alkitab.base;

import android.content.*;
import android.os.*;
import android.os.PowerManager.WakeLock;
import android.util.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

import org.apache.http.*;
import org.apache.http.client.*;
import org.apache.http.client.methods.*;
import org.apache.http.impl.client.*;

import yuku.alkitab.*;

public class AddonManager {
	public static final String TAG = AddonManager.class.getSimpleName();
	public static class Elemen {
		public String url;
		public String tujuan;
		public int selesai;
		public boolean beres;
		public DonlotListener listener;
		public boolean hentikan;
	}
	
	public interface DonlotListener {
		void onSelesaiDonlot(Elemen e);
		void onGagalDonlot(Elemen e, String keterangan, Throwable t);
		void onProgress(Elemen e, int sampe, int total);
		void onBatalDonlot(Elemen e);
	}

	public static String getYesPath() {
		return new File(Environment.getExternalStorageDirectory(), "bible/yes").getAbsolutePath(); //$NON-NLS-1$
	}
	
	public static String getEdisiPath(String namayes) {
		String yesPath = getYesPath();
		File yes = new File(yesPath, namayes); //$NON-NLS-1$
		return yes.getAbsolutePath();
	}
	
	public static boolean cekAdaEdisi(String namayes) {
		File f = new File(getEdisiPath(namayes));
		return f.exists() && f.canRead();
	}
	
	static class DonlotThread extends Thread {
		final Context appContext;
		Semaphore sema = new Semaphore(0);
		LinkedList<Elemen> antrian = new LinkedList<Elemen>();
		
		public DonlotThread(Context appContext) {
			this.appContext = appContext;
		}
		
		@Override
		public void run() {
			while (true) {
				sema.acquireUninterruptibly();
				Log.d(TAG, "DonlotThread sema count: " + sema.availablePermits()); //$NON-NLS-1$
				
				while (true) {
					Elemen e;
					synchronized (this) {
						if (antrian.size() == 0) {
							Log.d(TAG, "tiada lagi antrian donlot"); //$NON-NLS-1$
							break;
						}
						
						e = antrian.poll();
					}
					
					donlot(e);
				}
			}
		}
		
		private void donlot(Elemen e) {
			new File(e.tujuan).delete(); // hapus dulu.. jangan2 kacau
			
			String tmpfile = e.tujuan + "-" + (int)(Math.random() * 100000) + ".tmp";  //$NON-NLS-1$//$NON-NLS-2$
			
			PowerManager pm = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
			WakeLock wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "donlot");
			wakelock.setReferenceCounted(false);
			wakelock.acquire();
			try {
				{
					File dir = new File(getYesPath());
					if (!dir.exists()) {
						boolean mkdirOk = new File(getYesPath()).mkdirs();
						if (!mkdirOk) {
							if (e.listener != null) e.listener.onGagalDonlot(e, appContext.getString(R.string.tidak_bisa_membuat_folder, dir.getAbsolutePath()), null);
						}
					}
				}
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
					
					e.selesai += read;
					if (e.listener != null) e.listener.onProgress(e, e.selesai, length);
					
					if (e.hentikan) {
						get.abort();
						if (e.listener != null) e.listener.onBatalDonlot(e);
						os.close();
						return;
					}
				}
				
				os.close();

				if (e.url.endsWith(".gz")) { //$NON-NLS-1$
					if (e.listener != null) e.listener.onProgress(e, -1, length); // tanda lagi dekompres
					
					GZIPInputStream in = new GZIPInputStream(new FileInputStream(tmpfile));
					String tmpfile2 = e.tujuan + (int)(Math.random() * 100000) + ".tmp2"; //$NON-NLS-1$
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
				        
						boolean renameOk = new File(tmpfile2).renameTo(new File(e.tujuan));
						if (!renameOk) {
							Log.d(TAG, "Gagal rename!"); //$NON-NLS-1$
						}
					} finally {
						Log.d(TAG, "menghapus tmpfile2: " + tmpfile); //$NON-NLS-1$
						new File(tmpfile2).delete();
					}
			        in.close();
				} else {
					boolean renameOk = new File(tmpfile).renameTo(new File(e.tujuan));
					if (!renameOk) {
						Log.d(TAG, "Gagal rename!"); //$NON-NLS-1$
					}
				}
				
				if (e.listener != null) e.listener.onSelesaiDonlot(e);
				e.beres = true;
			} catch (IOException ex) {
				Log.w(TAG, "Gagal donlot", ex); //$NON-NLS-1$
				if (e.listener != null) e.listener.onGagalDonlot(e, null, ex);
			} finally {
				if (wakelock != null) {
					wakelock.release();
				}
				
				Log.d(TAG, "menghapus tmpfile: " + tmpfile); //$NON-NLS-1$
				new File(tmpfile).delete();
			}
		}

		public synchronized Elemen antrikan(String url, String tujuan, DonlotListener listener) {
			Elemen e = new Elemen();
			e.url = url;
			e.tujuan = tujuan;
			e.listener = listener;
			e.beres = false;
			
			antrian.offer(e);
			sema.release(); // ijinkan jalan
			
			return e;
		}
	}
	
	private static DonlotThread donlotThread;
	
	public synchronized static DonlotThread getDonlotThread(Context appContext) {
		if (donlotThread == null) {
			donlotThread = new DonlotThread(appContext);
			donlotThread.start();
		}
		return donlotThread;
	}
}
