package yuku.alkitab.model;

import yuku.alkitab.S;
import yuku.andoutil.Sqlitil;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public class PembuatIndex {
	public static interface OnProgressListener {
		void onProgress(String msg);
	}
	
	public void buatIndex(Context context, Edisi edisi, Kitab[] xkitab, OnProgressListener listener) {
		try {
			listener.onProgress("Tahap 1: Membuat tabel");
	
			SQLiteDatabase db = SearchDb.getInstance(context).getDatabase();
			
			String edisi_nama = edisi.nama;
			int nkitab = edisi.nkitab;
			String sql = String.format("insert into %s (%s, %s, %s) values (?, ?, ?)", SearchDb.TABEL_Fts, SearchDb.KOLOM_content, SearchDb.KOLOM_ari, SearchDb.KOLOM_edisi_nama);
	
			Object[] values = new Object[3];
			values[2] = edisi_nama;
	
			// Debug.startMethodTracing("alki");
			listener.onProgress("Tahap 2: Menghapus index yang sudah ada (agak lama)"); // 1%
			db.execSQL(String.format("delete from %s where %s = ?", SearchDb.TABEL_Fts, SearchDb.KOLOM_edisi_nama), new Object[] {edisi_nama});
	
			//# hitung total ayat
			listener.onProgress("Tahap 3: Menghitung jumlah ayat"); // 10%
			int totalAyat = 0;
			for (int i = 0; i < nkitab; i++) {
				Kitab k = xkitab[i];
				int npasal = k.npasal;
				
				for (int j = 0; j < npasal; j++) {
					totalAyat += k.nayat[j];
				}
			}
			
			// transaksi dikomit tiap 500 ayat masuk
			int nbuffer = 0;
			
			int udah = 0;
	
			listener.onProgress("Tahap 4: Memasukkan isi ayat-ayat ke dalam index"); // 12%
			db.beginTransaction();
			try {
				for (int i = 0; i < nkitab; i++) {
					Kitab kitab = xkitab[i];
	
					// kitab uda diatur... lanjut
					int npasal = kitab.npasal;
	
					for (int j = 0; j < npasal; j++) {
						int pasal = j + 1; // pasal 1..npasal
	
						String[] xayat = S.muatTeks(context.getResources(), kitab, pasal);
						int ariPasal = Ari.encode(i, pasal, 0); // semua ayat di pasal
	
						// pasal uda diatur... lanjut
						for (int k = 0; k < xayat.length; k++) {
							String content = xayat[k];
	
							int ayat = k + 1; // ayat 1..nayat
	
							int ari = ariPasal + ayat;
							values[1] = ari;
							values[0] = content;
	
							db.execSQL(sql, values);
	
							nbuffer++;
							udah++;
							if (nbuffer >= 500) {
								db.setTransactionSuccessful();
								db.endTransaction();
								Log.d("alki", "transaksi selesai");
								
								listener.onProgress(String.format("Tahap 4: Memasukkan %d dari %d ayat", udah, totalAyat));
								nbuffer = 0;
								db.beginTransaction();
							}
						}
	
						Log.d("alki", String.format("index %s %d (0x%08x) OK", kitab.judul, pasal, ariPasal));
					}
				}
				db.setTransactionSuccessful();
			} finally {
				db.endTransaction();
			}
	
			listener.onProgress("Tahap 5: Mengoptimalkan indeks"); // 92%
			{
				db.execSQL(String.format("insert into %s (%s) values ('optimize')", SearchDb.TABEL_Fts, SearchDb.TABEL_Fts));
			}
			
			listener.onProgress("Tahap 6: Memberi tanda bahwa indeks sudah jadi");
			{
				db.delete(SearchDb.TABEL_UdahIndex, String.format("%s = ?", SearchDb.KOLOM_edisi_nama), new String[] {edisi_nama});
				db.execSQL(String.format("insert into %s (%s, %s) values (?, ?)", SearchDb.TABEL_UdahIndex, SearchDb.KOLOM_edisi_nama, SearchDb.KOLOM_waktuJadi), new Object[] {edisi_nama, Sqlitil.nowDateTime()});
			}
			
			// Debug.stopMethodTracing();
		} finally {
			listener.onProgress(null);
		}
	}
}
