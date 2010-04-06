package yuku.alkitab;

import java.io.*;
import java.util.*;

import yuku.alkitab.model.*;
import yuku.bintex.BintexReader;
import yuku.kirimfidbek.PengirimFidbek;
import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import android.widget.*;

public class S {
	public static final String NAMA_PREFERENCES = "yuku.alkitab";
	
	//# 33nya harus siap di siapinEdisi
	public static Edisi[] xedisi;
	public static Edisi edisi;
	public static IndexPerikop indexPerikop;
	
	//# 22nya harus siap di siapinKitab
	public static Kitab[] xkitab;
	public static Kitab kitab;

	public static PengirimFidbek pengirimFidbek;
	
	private static int getRawInt(Resources resources, String rid) {
		return resources.getIdentifier(rid, "raw", "yuku.alkitab");
	}

	public static void siapinEdisi(Resources resources) {
		if (xedisi != null) return;

		Scanner sc = new Scanner(resources.openRawResource(R.raw.edisi_index));

		ArrayList<Edisi> xedisi = new ArrayList<Edisi>();

		while (sc.hasNext()) {
			Edisi e = Edisi.baca(sc);
			xedisi.add(e);
		}

		S.xedisi = xedisi.toArray(new Edisi[xedisi.size()]);
		S.edisi = S.xedisi[0]; // TODO selalu pilih edisi pertama
		
		if (S.edisi.perikopAda) {
			if (S.indexPerikop != null && S.edisi.nama.equals(S.indexPerikop)) {
				// indexPerikop uda kemuat dan uda betul
			} else {
				long wmulai = System.currentTimeMillis();
				
				InputStream is = resources.openRawResource(getRawInt(resources, S.edisi.nama + "_perikop_index_bt"));
				BintexReader in = new BintexReader(is);
				try {
					S.indexPerikop = IndexPerikop.baca(in, S.edisi.nama);
				} catch (IOException e) {
					Log.e("alki", "baca perikop index ngaco", e);
				} finally {
					in.close();
				}
				
				Log.d("alki", "Muat index perikop butuh ms: " + (System.currentTimeMillis() - wmulai));
			}
		} else {
			S.indexPerikop = null;
		}
	}

	public static void siapinKitab(Resources resources) {
		if (xedisi == null || edisi == null) {
			siapinEdisi(resources);
		}
		if (xkitab != null) return;

		InputStream is = resources.openRawResource(getRawInt(resources, edisi.nama + "_index_bt"));
		BintexReader in = new BintexReader(is);
		try {
			//Debug.startMethodTracing("siapinKitab");
	
			ArrayList<Kitab> xkitab = new ArrayList<Kitab>();
	
			try {
				int pos = 0;
				while (true) {
					Kitab k = Kitab.baca(in, pos++);
					xkitab.add(k);
					
					Log.d("alkitab", "siapinKitab memuat " + k.judul);
				}
			} catch (IOException e) {
				Log.d("alkitab", "siapinKitab selesai memuat");
			}
	
			S.xkitab = xkitab.toArray(new Kitab[xkitab.size()]);
			S.kitab = S.xkitab[0]; // TODO selalu pilih edisi pertama
		} finally {
			in.close();
			
			//Debug.stopMethodTracing();
		}
		
	}

	/**
	 * @param pasal
	 *            harus betul! antara 1 sampe npasal, 0 ga boleh
	 */
	public static String[] muatTeks(Resources resources, Kitab kitab, int pasal) {
		int offset = kitab.pasal_offset[pasal - 1];
		int length = 0;

		try {
			InputStream in = resources.openRawResource(getRawInt(resources, kitab.file));
			in.skip(offset);

			if (pasal == kitab.npasal) {
				length = in.available();
			} else {
				length = kitab.pasal_offset[pasal] - offset;
			}

			ByteArrayInputStream bais = null;

			byte[] ba = new byte[length];
			in.read(ba);
			in.close();
			
			bais = new ByteArrayInputStream(ba);

			Utf8Reader reader = new Utf8Reader(bais);
			char[] ayatBuf = new char[4000];
			int i = 0;

			ArrayList<String> res = new ArrayList<String>();
			
			while (true) {
				int c = reader.read();
				if (c == -1) {
					break;
				} else if (c == '\n') {
					String satu = new String(ayatBuf, 0, i);
					res.add(satu);
					i = 0;
				} else {
					ayatBuf[i++] = (char) c;
				}
			}

			return res.toArray(new String[res.size()]);
		} catch (IOException e) {
			return new String[] { e.getMessage() };
		}
	}

	public static ArrayAdapter<String> getKitabAdapter(Context context) {
		String[] content = new String[xkitab.length];
		
		for (int i = 0; i < xkitab.length; i++) {
			content[i] = xkitab[i].judul;
		}
		
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, content);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		return adapter;
	}

	public static synchronized void siapinPengirimFidbek(final IsiActivity activity) {
		if (pengirimFidbek == null) {
			pengirimFidbek = new PengirimFidbek(activity, activity.getSharedPreferences(S.NAMA_PREFERENCES, 0));
			pengirimFidbek.setOnSuccessListener(new PengirimFidbek.OnSuccessListener() {
				@Override
				public void onSuccess(final byte[] response) {
					activity.handler.post(new Runnable() {
						@Override
						public void run() {
							Log.d("alki", "KirimFidbek respon: " + new String(response, 0, response.length));
							Toast.makeText(activity, R.string.fidbekMakasih_s, Toast.LENGTH_SHORT).show();
						}
					});
				}
			});
		}
	}
}
