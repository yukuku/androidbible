package yuku.alkitab.base.renungan;

import static yuku.alkitab.base.model.AlkitabDb.*;

import java.io.*;
import java.util.*;

import org.apache.http.*;
import org.apache.http.client.*;
import org.apache.http.client.methods.*;
import org.apache.http.impl.client.*;

import yuku.alkitab.base.model.*;
import yuku.andoutil.*;
import android.content.*;
import android.database.sqlite.*;
import android.util.*;

public class TukangDonlot extends Thread {
	public interface OnStatusDonlotListener {
		void onStatusDonlot(String s);
	}
	
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
					Log.d("alki", "TukangDonlot dibangunin dari tidur nyenyak");
				} finally {
					nganggur_ = false;
				}
			} else {
				//# donlot!?
				String url = artikel.getUrl();
				boolean berhasil = false;
				String output = null;
				
				Log.d("alki", "TukangDonlot mulai donlot nama=" + artikel.getNama() + " tgl=" + artikel.getTgl());
				listener_.onStatusDonlot("Mengunduh " + artikel.getNamaUmum() + " tgl " + artikel.getTgl() + "...");
				
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
					Log.w("alki", "TukangDonlot", e);
				}
				
				if (berhasil) {
					listener_.onStatusDonlot("Berhasil mengunduh " + artikel.getNamaUmum() + " tgl " + artikel.getTgl());
					
					artikel.isikan(output);
					if (output.startsWith("NG")) {
						listener_.onStatusDonlot("Kesalahan dalam mengunduh " + artikel.getNamaUmum() + " tgl " + artikel.getTgl() + ": " + output);
					}
					
					//# mari masukin ke db.
					{
						SQLiteDatabase db = AlkitabDb.getInstance(context_).getDatabase();
						db.beginTransaction();

						try {
							// hapus dulu yang lama.
							db.delete(TABEL_Renungan, KOLOM_Renungan_nama + "=? and " + KOLOM_Renungan_tgl + "=?", new String[] {artikel.getNama(), artikel.getTgl()});

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
							
							Log.d("alki", "TukangDonlot donlot selesai dengan sukses dan uda masuk ke db");
						} catch (Exception e) {
							Log.w("alki", "TukangDonlot pas mau masukin ke db", e);
						} finally {
							db.endTransaction();
						}
					}
				} else {
					listener_.onStatusDonlot("Gagal mengunduh " + artikel.getNamaUmum() + " tgl " + artikel.getTgl());
					Log.d("alki", "TukangDonlot gagal donlot");
				}
			}
			
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
			}
		}
	}
}
