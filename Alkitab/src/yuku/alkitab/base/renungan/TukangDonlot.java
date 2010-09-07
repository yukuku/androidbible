package yuku.alkitab.base.renungan;

import static yuku.alkitab.base.model.AlkitabDb.*;

import java.io.*;
import java.util.LinkedList;

import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import yuku.alkitab.R;
import yuku.alkitab.base.model.AlkitabDb;
import yuku.andoutil.*;
import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

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
		if (nganggur_ == true) {
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
					{
						SQLiteDatabase db = AlkitabDb.getInstance(context_).getDatabase();
						db.beginTransaction();

						try {
							// hapus dulu yang lama.
							db.delete(TABEL_Renungan, KOLOM_Renungan_nama + "=? and " + KOLOM_Renungan_tgl + "=?", new String[] {artikel.getNama(), artikel.getTgl()}); //$NON-NLS-1$ //$NON-NLS-2$

							ContentValues values = new ContentValues();
							values.put(KOLOM_Renungan_nama, artikel.getNama());
							values.put(KOLOM_Renungan_tgl, artikel.getTgl());
							values.put(KOLOM_Renungan_siapPakai, artikel.getSiapPakai()? 1: 0);
							
							if (artikel.getSiapPakai()) {
								values.put(KOLOM_Renungan_judul, artikel.getJudul().toString());
								values.put(KOLOM_Renungan_isi, artikel.getIsiHtml());
								values.put(KOLOM_Renungan_header, artikel.getHeaderHtml());
							} else {
								values.put(KOLOM_Renungan_judul, (String)null);
								values.put(KOLOM_Renungan_isi, (String)null);
								values.put(KOLOM_Renungan_header, (String)null);
							}
							
							values.put(KOLOM_Renungan_waktuSentuh, Sqlitil.nowDateTime());
							
							db.insert(TABEL_Renungan, null, values);
							
							db.setTransactionSuccessful();
							
							Log.d(TAG, "TukangDonlot donlot selesai dengan sukses dan uda masuk ke db"); //$NON-NLS-1$
						} catch (Exception e) {
							Log.w(TAG, "TukangDonlot pas mau masukin ke db", e); //$NON-NLS-1$
						} finally {
							db.endTransaction();
						}
					}
				} else {
					listener_.onStatusDonlot(context_.getString(R.string.gagal_mengunduh_namaumum_tgl_tgl, artikel.getNamaUmum(), artikel.getTgl()));
					Log.d(TAG, "TukangDonlot gagal donlot"); //$NON-NLS-1$
				}
			}
			
			ThreadSleep.giveUpOnInterrupt(1000);
		}
	}
}
