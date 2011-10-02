package yuku.alkitab.base.renungan;

import android.content.*;
import android.util.*;

import java.io.*;
import java.util.*;

import org.apache.http.*;
import org.apache.http.client.*;
import org.apache.http.client.methods.*;
import org.apache.http.impl.client.*;

import yuku.alkitab.*;
import yuku.alkitab.base.*;
import yuku.andoutil.*;

public class TukangDonlot extends Thread {
	public interface OnStatusDonlotListener {
		void onStatusDonlot(String s);
	}

	private static final String TAG = null;
	
	private Context context_;
	private OnStatusDonlotListener listener_;
	private LinkedList<IArtikel> antrian_ = new LinkedList<IArtikel>();
	private boolean nganggur_;
	
	public TukangDonlot(Context context, OnStatusDonlotListener listener) {
		context_ = context;
		listener_ = listener;
	}
	
	public void setListener(OnStatusDonlotListener listener) {
		this.listener_ = listener;
	}
	
	public synchronized boolean tambah(IArtikel artikel, boolean penting) {
		for (IArtikel a: antrian_) {
			if (a.equals(artikel)) {
				return false;
			}
		}
		
		if (penting) {
			antrian_.addFirst(artikel);
		} else {
			antrian_.add(artikel);
		}
		
		return true;
	}
	
	private synchronized IArtikel dequeue() {
		while (true) {
			if (antrian_.size() == 0) {
				return null;
			}
			
			IArtikel artikel = antrian_.getFirst();
			antrian_.removeFirst();
			
			if (artikel.getSiapPakai()) {
				continue;
			}
			
			return artikel;
		}
	}
	
	public void interruptKaloNganggur() {
		if (nganggur_) {
			this.interrupt();
		}
	}
	
	@Override
	public void run() {
		while (true) {
			IArtikel artikel = dequeue();
			
			if (artikel == null) {
				try {
					nganggur_ = true;
					Thread.sleep(60000);
				} catch (InterruptedException e) {
					Log.d(TAG, "TukangDonlot dibangunin dari tidur nyenyak"); //$NON-NLS-1$
				} finally {
					nganggur_ = false;
				}
			} else {
				//# donlot!?
				String url = artikel.getUrl();
				boolean berhasil = false;
				String output = null;
				
				Log.d(TAG, "TukangDonlot mulai donlot nama=" + artikel.getNama() + " tgl=" + artikel.getTgl()); //$NON-NLS-1$ //$NON-NLS-2$
				listener_.onStatusDonlot(context_.getString(R.string.mengunduh_namaumum_tgl_tgl, artikel.getNamaUmum(), artikel.getTgl()));
				
				try {
					HttpClient client = new DefaultHttpClient();
					HttpGet get = new HttpGet(url);
					
					HttpResponse response = client.execute(get);
					HttpEntity entity = response.getEntity();
					
					InputStream content = entity.getContent();
					ByteArrayOutputStream baos = new ByteArrayOutputStream(4096 * 4);
					
					while (true) {
						byte[] b = new byte[4096];
						int read = content.read(b);
	
						if (read <= 0) break;
						baos.write(b, 0, read);
					}
					
					output = new String(baos.toByteArray(), artikel.getMentahEncoding());
					berhasil = true;
				} catch (IOException e) {
					Log.w(TAG, "TukangDonlot", e); //$NON-NLS-1$
				}
				
				if (berhasil) {
					listener_.onStatusDonlot(context_.getString(R.string.berhasil_mengunduh_namaumum_tgl_tgl, artikel.getNamaUmum(), artikel.getTgl()));
					
					artikel.isikan(output);
					if (output.startsWith("NG")) { //$NON-NLS-1$
						listener_.onStatusDonlot(context_.getString(R.string.kesalahan_dalam_mengunduh_namaumum_tgl_tgl_output, artikel.getNamaUmum(), artikel.getTgl(), output));
					}
					
					//# mari masukin ke db.
					S.getDb().simpanArtikelKeRenungan(artikel);
				} else {
					listener_.onStatusDonlot(context_.getString(R.string.gagal_mengunduh_namaumum_tgl_tgl, artikel.getNamaUmum(), artikel.getTgl()));
					Log.d(TAG, "TukangDonlot gagal donlot"); //$NON-NLS-1$
				}
			}
			
			ThreadSleep.giveUpOnInterrupt(1000);
		}
	}
}
