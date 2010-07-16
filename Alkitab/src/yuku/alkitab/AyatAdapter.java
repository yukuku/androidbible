package yuku.alkitab;

import yuku.alkitab.R.id;
import yuku.alkitab.S.penerapan;
import yuku.alkitab.model.*;
import android.content.*;
import android.graphics.*;
import android.text.*;
import android.text.method.*;
import android.text.style.*;
import android.util.*;
import android.view.*;
import android.widget.*;
import android.widget.TextView.BufferType;

class AyatAdapter extends BaseAdapter {
	//# field ctor
	private final Context appContext_;
	private final AlkitabDb db_;
	private final CallbackSpan.OnClickListener paralelListener_;
	private final IsiActivity.GelembungListener gelembungListener_;
	
	//# field setData
	private Kitab kitab_;
	private int pasal_1_;
	private String[] dataAyat_;
	private Blok[] perikop_xblok_;
	/**
	 * Tiap elemen, kalo 0 sampe positif, berarti menunjuk ke AYAT di rendered_
	 * kalo negatif, -1 berarti index 0 di perikop_*, -2 (a) berarti index 1 (b) di perikop_*
	 * 
	 * Konvert a ke b: -(a+1); // -a-1 juga sih sebetulnya. gubrak.
	 * Konvert b ke a: -b-1;
	 */
	private int[] penunjukKotak_;
	private boolean[] catatanMap_;
	
	public AyatAdapter(Context appContext, AlkitabDb db, CallbackSpan.OnClickListener paralelListener, IsiActivity.GelembungListener gelembungListener) {
		appContext_ = appContext;
		db_ = db;
		paralelListener_ = paralelListener;
		gelembungListener_ = gelembungListener;
	}
	
	synchronized void setData(Kitab kitab, int pasal_1, String[] xayat, int[] perikop_xari, Blok[] perikop_xblok, int nblok) {
		kitab_ = kitab;
		pasal_1_ = pasal_1;
		dataAyat_ = xayat.clone();
		perikop_xblok_ = perikop_xblok;
		penunjukKotak_ = U.bikinPenunjukKotak(dataAyat_.length, perikop_xari, perikop_xblok, nblok);
	}
	
	private int[] catatan_xari_buf = new int[256]; // ga akan dalam 1 pasal sampe segini ayat
	synchronized void muatCatatanMap() {
		//# catatankah?
		boolean[] catatanMap = null;

		int ariKp = Ari.encode(kitab_.pos, pasal_1_, 0x00);
		Log.d("alki", "ariKp = " + Integer.toHexString(ariKp));
		if (db_.countCatatan(ariKp) > 0) {
			catatanMap = new boolean[dataAyat_.length];
			int c = db_.getCatatan(ariKp, catatan_xari_buf);
			for (int i = 0; i < c; i++) {
				int ari = catatan_xari_buf[i];
				catatanMap[Ari.toAyat(ari) - 1] = true;
			}
		}

		catatanMap_ = catatanMap;
	}

	@Override
	public synchronized int getCount() {
		if (dataAyat_ == null) return 0;

		return penunjukKotak_.length;
	}

	@Override
	public synchronized String getItem(int position) {
		int id = penunjukKotak_[position];
		
		if (id >= 0) {
			return dataAyat_[position].toString();
		} else {
			return perikop_xblok_[-id-1].toString();
		}
	}

	@Override
	public synchronized long getItemId(int position) {
		 return penunjukKotak_[position];
	}
	
	@Override
	public synchronized View getView(int position, View convertView, ViewGroup parent) {
		// Harus tentukan apakah ini perikop ato ayat.
		int id = penunjukKotak_[position];
		
		if (id >= 0) {
			// AYAT. bukan judul perikop.
			
			String isi = dataAyat_[id];
			boolean pakeGelembung = catatanMap_ == null? false: catatanMap_[id];
			
			// Udah ditentukan bahwa ini ayat dan bukan perikop, sekarang tinggal tentukan
			// apakah ayat ini pake formating biasa (tanpa menjorok dsb) atau ada formating
			if (isi.charAt(0) == '@') {
				// karakter kedua harus '@' juga, kalo bukan ada ngaco
				if (isi.charAt(1) != '@') {
					throw new RuntimeException("Karakter kedua bukan @. Isi ayat: " + isi);
				}
				
				LinearLayout res;
				if (convertView == null || convertView.getId() != R.layout.satu_ayat_tehel) {
					res = (LinearLayout) LayoutInflater.from(appContext_).inflate(R.layout.satu_ayat_tehel, null);
					res.setId(R.layout.satu_ayat_tehel);
				} else {
					res = (LinearLayout) convertView;
				}
				
				RelativeLayout sebelahKiri = (RelativeLayout) res.findViewById(R.id.sebelahKiri);
				tampilanAyatTehel(appContext_, sebelahKiri, id + 1, isi);
				
				ImageButton imgAtributGelembung = (ImageButton) res.findViewById(R.id.imgAtributGelembung);
				imgAtributGelembung.setVisibility(pakeGelembung? View.VISIBLE: View.GONE);
				if (pakeGelembung) {
					pasangClickHandlerUntukGelembung(imgAtributGelembung, pasal_1_, id + 1);
				}
				
				return res;
			} else {
				LinearLayout res;
				if (convertView == null || convertView.getId() != R.layout.satu_ayat_sederhana) {
					res = (LinearLayout) LayoutInflater.from(appContext_).inflate(R.layout.satu_ayat_sederhana, null);
					res.setId(R.layout.satu_ayat_sederhana);
				} else {
					res = (LinearLayout) convertView;
				}
				
				TextView lIsiAyat = (TextView) res.findViewById(R.id.lIsiAyat);
				lIsiAyat.setText(AyatAdapter.tampilanAyatSederhana(id + 1, isi), BufferType.SPANNABLE);
				
				IsiActivity.aturTampilanTeksIsi(lIsiAyat);

				ImageButton imgAtributGelembung = (ImageButton) res.findViewById(R.id.imgAtributGelembung);
				imgAtributGelembung.setVisibility(pakeGelembung? View.VISIBLE: View.GONE);
				if (pakeGelembung) {
					pasangClickHandlerUntukGelembung(imgAtributGelembung, pasal_1_, id + 1);
				}

				return res;
			}
		} else {
			// JUDUL PERIKOP. bukan ayat.
			View res;
			if (convertView == null || convertView.getId() != R.layout.header_perikop) {
				res = LayoutInflater.from(appContext_).inflate(R.layout.header_perikop, null);
				res.setId(R.layout.header_perikop);
			} else {
				res = convertView;
			}
			
			Blok blok = perikop_xblok_[-id-1];
			
			TextView lJudul = (TextView) res.findViewById(R.id.lJudul);
			TextView lXparalel = (TextView) res.findViewById(R.id.lXparalel);
			
			lJudul.setTypeface(S.penerapan.jenisHuruf, Typeface.BOLD);
			lJudul.setTextSize(TypedValue.COMPLEX_UNIT_DIP, S.penerapan.ukuranHuruf2dp);
			lJudul.setText(blok.judul);
			lJudul.setTextColor(S.penerapan.warnaHuruf);
			
			// matikan padding atas kalau position == 0 ATAU sebelum ini juga judul perikop
			if (position == 0 || penunjukKotak_[position-1] < 0) {
				lJudul.setPadding(0, 0, 0, 0);
			} else {
				lJudul.setPadding(0, appContext_.getResources().getDimensionPixelOffset(R.dimen.marginAtasJudulPerikop), 0, 0);
			}
			
			// gonekan paralel kalo ga ada
			if (blok.xparalel.length == 0) {
				lXparalel.setVisibility(View.GONE);
			} else {
				lXparalel.setVisibility(View.VISIBLE);
				
				SpannableStringBuilder sb = new SpannableStringBuilder("(");
				int len = 1;

				int total = blok.xparalel.length;
				for (int i = 0; i < total; i++) {
					String paralel = blok.xparalel[i];
					
					if (i > 0) {
						// paksa new line untuk pola2 paralel tertentu
						if ( (total == 6 && i == 3) || (total == 4 && i == 2) || (total == 5 && i == 3) ) {
							sb.append("; \n");
							len += 3;
						} else {
							sb.append("; ");
							len += 2;
						}
					}
					
					sb.append(paralel);
					sb.setSpan(new CallbackSpan(paralel, paralelListener_), len, len + paralel.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
					len += paralel.length();
				}
				sb.append(")");
				len += 1;
				
				lXparalel.setText(sb, BufferType.SPANNABLE);
				lXparalel.setMovementMethod(LinkMovementMethod.getInstance());
				lXparalel.setTextColor(S.penerapan.warnaHuruf);
				lXparalel.setLinkTextColor(S.penerapan.warnaHuruf);
			}
			
			return res;
		}
	}
	
	void pasangClickHandlerUntukGelembung(ImageButton imgGelembung, final int pasal_1, final int ayat_1) {
		imgGelembung.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				gelembungListener_.onClick(kitab_, pasal_1, ayat_1);
			}
		});
	}
	
	/**
	 * @param ayat mulai dari 1
	 * @return position di adapter ini atau -1 kalo ga ketemu
	 */
	public int getPositionDariAyat(int ayat) {
		if (penunjukKotak_ == null) return -1;
		
		int ayat0 = ayat - 1;
		
		for (int i = 0; i < penunjukKotak_.length; i++) {
			if (penunjukKotak_[i] == ayat0) {
				// ketemu, tapi kalo ada judul perikop, akan lebih baik. Coba cek mundur dari sini
				for (int j = i-1; j >= 0; j--) {
					if (penunjukKotak_[j] < 0) {
						// masih perikop, yey, kita lanjutkan
						i = j;
					} else {
						// uda bukan perikop. (Berarti uda ayat sebelumnya)
						break;
					}
				}
				return i;
			}
		}
		
		return -1;
	}
	
	/**
	 * @return ayat (mulai dari 1). atau 0 kalo ga masuk akal
	 */
	public int getAyatDariPosition(int position) {
		if (penunjukKotak_ == null) return 0;
		
		if (position >= penunjukKotak_.length) {
			position = penunjukKotak_.length - 1;
		}
		
		int id = penunjukKotak_[position];
		
		if (id >= 0) {
			return id + 1;
		}
		
		// perikop nih. Susuri sampe abis
		for (int i = position + 1; i < penunjukKotak_.length; i++) {
			id = penunjukKotak_[i];
			
			if (id >= 0) {
				return id + 1;
			}
		}
		
		Log.w("alki", "masa judul perikop di paling bawah? Ga masuk akal.");
		return 0; 
	}

	public void tampilanAyatTehel(Context context, RelativeLayout res, int ayat, String isi) {
		// @@ = mulai ayat dengan tehel
		// @0 = mulai menjorok 0
		// @1 = mulai menjorok 1
		// @2 = mulai menjorok 2
		// @8 = tanda kasi jarak ke ayat berikutnya
		int posParse = 2; // mulai setelah @@
		int menjorok = 0;
		char[] isi_cc = isi.toCharArray();
		TextView tehelTerakhir = null;
		boolean keluarlah = false;
		boolean belumAdaTehel = true;
		boolean nomerAyatUdaDitulis = false;
		
		LinearLayout tempatTehel = (LinearLayout) res.findViewById(id.tempatTehel);
		tempatTehel.removeAllViews();
		
		while (true) {
			int posSampe = isi.indexOf('@', posParse);
	
			if (posSampe == -1) {
				// abis
				posSampe = isi.length();
				keluarlah = true;
			}
			
			if (posParse == posSampe) {
				// di awal, belum ada apa2!
			} else {
				//Log.d("alki", "akan masukinTehel menjorok=" + menjorok + " " + isi.substring(posParse, posSampe));
				
				// bikin tehel
				{
					TextView tehel = new TextView(context);
					if (menjorok == 1) {
						tehel.setPadding(penerapan.menjorokSatu, 0, 0, 0);
						if (penerapan.gebug_tehelBewarna) {
							tehel.setBackgroundColor(0xff000066);
						}
					} else if (menjorok == 2) {
						tehel.setPadding(penerapan.menjorokDua, 0, 0, 0);
						if (penerapan.gebug_tehelBewarna) {
							tehel.setBackgroundColor(0xff660000);
						}
					}
					
					// kasus: belum ada tehel dan tehel pertama menjorok 0
					if (belumAdaTehel && menjorok == 0) {
						//# kasih no ayat di depannya
						SpannableStringBuilder s = new SpannableStringBuilder();
						String ayat_s = String.valueOf(ayat);
						s.append(ayat_s).append(" ").append(isi, posParse, posSampe);
						s.setSpan(new ForegroundColorSpan(penerapan.warnaNomerAyat), 0, ayat_s.length(), 0);
						tehel.setText(s, BufferType.SPANNABLE);
						
						// kasi tanda biar nanti ga tulis nomer ayat lagi
						nomerAyatUdaDitulis = true;
					} else {
						tehel.setText(isi_cc, posParse, posSampe - posParse);
					}
					
					IsiActivity.aturTampilanTeksIsi(tehel);
					tempatTehel.addView(tehel);
					
					tehelTerakhir = tehel;
				}
				
				belumAdaTehel = false;
			}
			
			if (keluarlah) break;
	
			char jenisTanda = isi_cc[posSampe + 1];
			if (jenisTanda == '1') {
				menjorok = 1;
			} else if (jenisTanda == '2') {
				menjorok = 2;
			} else if (jenisTanda == '8') {
				if (tehelTerakhir != null) {
					if (penerapan.gebug_tehelBewarna) {
						tehelTerakhir.append("$$$\n");
					} else {
						tehelTerakhir.append("\n");
					}
				}
			}
			
			posParse = posSampe+2;
		}
		
		TextView lAyat = (TextView) res.findViewById(id.lAyat);
		if (nomerAyatUdaDitulis) {
			lAyat.setText("");
		} else {
			lAyat.setText(String.valueOf(ayat));
			IsiActivity.aturTampilanTeksNomerAyat(lAyat);
		}
	}

	/**
	 * @param ayat mulai dari 1
	 */
	static SpannableStringBuilder tampilanAyatSederhana(int ayat, String isi) {
		SpannableStringBuilder seayat = new SpannableStringBuilder();
		
		String ayat_s = String.valueOf(ayat);
		
		seayat.append(ayat_s).append(" ").append(isi);
		seayat.setSpan(new ForegroundColorSpan(S.penerapan.warnaNomerAyat), 0, ayat_s.length(), 0);
		
		seayat.setSpan(new LeadingMarginSpan.Standard(0, S.penerapan.indenParagraf), 0, ayat_s.length() + 1 + isi.length(), 0);
		
		return seayat;
	}
}