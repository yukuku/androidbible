package yuku.alkitab.base;

import yuku.alkitab.R;
import yuku.alkitab.base.S.penerapan;
import yuku.alkitab.base.model.*;
import yuku.alkitab.base.storage.Db.Bukmak2;
import android.content.Context;
import android.graphics.Typeface;
import android.text.*;
import android.text.method.LinkMovementMethod;
import android.text.style.*;
import android.util.*;
import android.view.*;
import android.widget.*;
import android.widget.TextView.BufferType;

class AyatAdapter extends BaseAdapter {
	public static final String TAG = AyatAdapter.class.getSimpleName();
	
	//# field ctor
	private final Context appContext_;
	private final CallbackSpan.OnClickListener paralelListener_;
	private final IsiActivity.AtributListener atributListener_;
	
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
	private int[] atributMap_; // bit 0(0x1) = bukmak; bit 1(0x2) = catatan; bit 2(0x4) = stabilo; 
	private int[] stabiloMap_; // null atau warna stabilo

	
	public AyatAdapter(Context appContext, CallbackSpan.OnClickListener paralelListener, IsiActivity.AtributListener gelembungListener) {
		appContext_ = appContext;
		paralelListener_ = paralelListener;
		atributListener_ = gelembungListener;
	}
	
	synchronized void setData(Kitab kitab, int pasal_1, String[] xayat, int[] perikop_xari, Blok[] perikop_xblok, int nblok) {
		kitab_ = kitab;
		pasal_1_ = pasal_1;
		dataAyat_ = xayat.clone();
		perikop_xblok_ = perikop_xblok;
		penunjukKotak_ = U.bikinPenunjukKotak(dataAyat_.length, perikop_xari, perikop_xblok, nblok);
	}
	
	synchronized void muatAtributMap() {
		int[] atributMap = null;
		int[] stabiloMap = null;
		
		int ariKp = Ari.encode(kitab_.pos, pasal_1_, 0x00);
		if (S.getDb().countAtribut(ariKp) > 0) {
			atributMap = new int[dataAyat_.length];
			stabiloMap = S.getDb().putAtribut(ariKp, atributMap);
		}

		atributMap_ = atributMap;
		stabiloMap_ = stabiloMap;
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
			boolean pakeBukmak = atributMap_ == null? false: (atributMap_[id] & 0x1) != 0;
			boolean pakeCatatan = atributMap_ == null? false: (atributMap_[id] & 0x2) != 0;
			boolean pakeStabilo = atributMap_ == null? false: (atributMap_[id] & 0x4) != 0;
			int warnaStabilo = pakeStabilo? (stabiloMap_ == null? 0: (0x80000000 | stabiloMap_[id])): 0;
			
			View res;
			// Udah ditentukan bahwa ini ayat dan bukan perikop, sekarang tinggal tentukan
			// apakah ayat ini pake formating biasa (tanpa menjorok dsb) atau ada formating
			if (isi.charAt(0) == '@') {
				// karakter kedua harus '@' juga, kalo bukan ada ngaco
				if (isi.charAt(1) != '@') {
					throw new RuntimeException("Karakter kedua bukan @. Isi ayat: " + isi); //$NON-NLS-1$
				}
				
				if (convertView == null || convertView.getId() != R.layout.item_ayat_tehel) {
					res = LayoutInflater.from(appContext_).inflate(R.layout.item_ayat_tehel, null);
					res.setId(R.layout.item_ayat_tehel);
				} else {
					res = convertView;
				}
				
				RelativeLayout sebelahKiri = (RelativeLayout) res.findViewById(R.id.sebelahKiri);
				tampilanAyatTehel(appContext_, sebelahKiri, id + 1, isi, warnaStabilo);
				
			} else {
				if (convertView == null || convertView.getId() != R.layout.item_ayat_sederhana) {
					res = LayoutInflater.from(appContext_).inflate(R.layout.item_ayat_sederhana, null);
					res.setId(R.layout.item_ayat_sederhana);
				} else {
					res = convertView;
				}
				
				TextView lIsiAyat = (TextView) res.findViewById(R.id.lIsiAyat);
				lIsiAyat.setText(AyatAdapter.tampilanAyatSederhana(id + 1, isi, warnaStabilo), BufferType.SPANNABLE);
				
				IsiActivity.aturTampilanTeksIsi(lIsiAyat);
			}

			ImageButton imgAtributBukmak = (ImageButton) res.findViewById(R.id.imgAtributBukmak);
			imgAtributBukmak.setVisibility(pakeBukmak? View.VISIBLE: View.GONE);
			if (pakeBukmak) {
				pasangClickHandlerUntukBukmak(imgAtributBukmak, pasal_1_, id + 1);
			}
			ImageButton imgAtributCatatan = (ImageButton) res.findViewById(R.id.imgAtributCatatan);
			imgAtributCatatan.setVisibility(pakeCatatan? View.VISIBLE: View.GONE);
			if (pakeCatatan) {
				pasangClickHandlerUntukCatatan(imgAtributCatatan, pasal_1_, id + 1);
			}
			
			return res;
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
				
				SpannableStringBuilder sb = new SpannableStringBuilder("("); //$NON-NLS-1$
				int len = 1;

				int total = blok.xparalel.length;
				for (int i = 0; i < total; i++) {
					String paralel = blok.xparalel[i];
					
					if (i > 0) {
						// paksa new line untuk pola2 paralel tertentu
						if ( (total == 6 && i == 3) || (total == 4 && i == 2) || (total == 5 && i == 3) ) {
							sb.append("; \n"); //$NON-NLS-1$
							len += 3;
						} else {
							sb.append("; "); //$NON-NLS-1$
							len += 2;
						}
					}
					
					sb.append(paralel);
					sb.setSpan(new CallbackSpan(paralel, paralelListener_), len, len + paralel.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
					len += paralel.length();
				}
				sb.append(')');
				len += 1;
				
				lXparalel.setTypeface(S.penerapan.jenisHuruf);
				lXparalel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, S.penerapan.ukuranHuruf2dp * (14.f / 17.f));
				lXparalel.setText(sb, BufferType.SPANNABLE);
				lXparalel.setMovementMethod(LinkMovementMethod.getInstance());
				lXparalel.setTextColor(S.penerapan.warnaHuruf);
				lXparalel.setLinkTextColor(S.penerapan.warnaHuruf);
			}
			
			return res;
		}
	}
	
	void pasangClickHandlerUntukBukmak(ImageButton imgBukmak, final int pasal_1, final int ayat_1) {
		imgBukmak.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				atributListener_.onClick(kitab_, pasal_1, ayat_1, Bukmak2.jenis_bukmak);
			}
		});
	}
	
	void pasangClickHandlerUntukCatatan(ImageButton imgGelembung, final int pasal_1, final int ayat_1) {
		imgGelembung.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				atributListener_.onClick(kitab_, pasal_1, ayat_1, Bukmak2.jenis_catatan);
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
		
		Log.w(TAG, "masa judul perikop di paling bawah? Ga masuk akal."); //$NON-NLS-1$
		return 0; 
	}

	public void tampilanAyatTehel(Context context, RelativeLayout res, int ayat, String isi, int warnaStabilo) {
		// @@ = mulai ayat dengan tehel atau ayat dengan teks merah
		// @0 = mulai menjorok 0 [kategori: penehel]
		// @1 = mulai menjorok 1 [kategori: penehel]
		// @2 = mulai menjorok 2 [kategori: penehel]
		// @6 = mulai teks merah [kategori: format]
		// @5 = akhir teks merah [kategori: format]
		// @8 = tanda kasi jarak ke ayat berikutnya  [kategori: format]
		int posParse = 2; // mulai setelah @@
		int menjorok = 0;
		boolean keluarlah = false;
		boolean belumAdaTehel = true;
		boolean nomerAyatUdaDitulis = false;
		
		LinearLayout tempatTehel = (LinearLayout) res.findViewById(R.id.tempatTehel);
		tempatTehel.removeAllViews();
		
		while (true) {
			// cari posisi penehel berikutnya TODO
			int posSampe = indexOfPenehel(isi, posParse);
	
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
						tehel.setPadding(S.getMenjorokSatu(), 0, 0, 0);
					} else if (menjorok == 2) {
						tehel.setPadding(S.getMenjorokDua(), 0, 0, 0);
					}
					
					// kasus: belum ada tehel dan tehel pertama menjorok 0
					if (belumAdaTehel && menjorok == 0) {
						//# kasih no ayat di depannya
						SpannableStringBuilder s = new SpannableStringBuilder();
						String ayat_s = String.valueOf(ayat);
						s.append(ayat_s).append(' ');
						appendFormattedText(s, isi, posParse, posSampe);
						s.setSpan(new ForegroundColorSpan(penerapan.warnaNomerAyat), 0, ayat_s.length(), 0);
						s.setSpan(new LeadingMarginSpan.Standard(0, S.getIndenParagraf()), 0, s.length(), 0);
						if (warnaStabilo != 0) {
							s.setSpan(new BackgroundColorSpan(warnaStabilo), ayat_s.length() + 1, s.length(), 0);
						}
						tehel.setText(s, BufferType.SPANNABLE);
						
						// kasi tanda biar nanti ga tulis nomer ayat lagi
						nomerAyatUdaDitulis = true;
					} else {
						SpannableStringBuilder s = new SpannableStringBuilder();
						appendFormattedText(s, isi, posParse, posSampe);
						if (warnaStabilo != 0) {
							s.setSpan(new BackgroundColorSpan(warnaStabilo), 0, s.length(), 0);
						}
						tehel.setText(s);
					}
					
					IsiActivity.aturTampilanTeksIsi(tehel);
					tempatTehel.addView(tehel);
				}
				
				belumAdaTehel = false;
			}
			
			if (keluarlah) break;
	
			char jenisTanda = isi.charAt(posSampe + 1);
			if (jenisTanda == '1') {
				menjorok = 1;
			} else if (jenisTanda == '2') {
				menjorok = 2;
			}
			
			posParse = posSampe+2;
		}
		
		TextView lAyat = (TextView) res.findViewById(R.id.lAyat);
		if (nomerAyatUdaDitulis) {
			lAyat.setText(""); //$NON-NLS-1$
		} else {
			lAyat.setText(String.valueOf(ayat));
			IsiActivity.aturTampilanTeksNomerAyat(lAyat);
		}
	}

	/**
	 * taro teks dari isi[posDari..posSampe] dengan format 6 atau 5 atau 8 ke s
	 * @param isi  string yang dari posDari sampe sebelum posSampe hanya berisi 6 atau 5 atau 8 tanpa mengandung @ lain.
	 */
	private void appendFormattedText(SpannableStringBuilder s, String isi, int posDari, int posSampe) {
		int posAwal = posDari;
		boolean merah = false;
		boolean berakhir = false;
		while (true) {
			// cari @
			int posAkhir = isi.indexOf('@', posAwal);
			if (posAkhir == -1 || posAkhir >= posSampe - 1) {
				berakhir = true;
				posAkhir = posSampe;
			}
			
			// masukin dari posAwal sampe posAkhir
			if (posAkhir - posAwal > 0) {
				int format_dari = s.length();
				s.append(isi, posAwal, posAkhir);
				if (merah) {
					s.setSpan(new ForegroundColorSpan(S.penerapan.warnaHurufMerah), format_dari, format_dari + posAkhir - posAwal, 0);
				}
			}
			
			// siapkan merah atau hentian merah
			if (! berakhir) {
				char jenisTanda = isi.charAt(posAkhir + 1);
				if (jenisTanda == '6') {
					merah = true;
				} else if (jenisTanda == '5') {
					merah = false;
				} else if (jenisTanda == '8') {
					s.append('\n'); 
				}
			}
			
			if (berakhir) {
				break;
			}
			
			posAwal = posAkhir + 2;
		}
	}

	/** index of penehel berikutnya */
	private int indexOfPenehel(String isi, int mulai) {
		int length = isi.length();
		while (true) {
			int pos = isi.indexOf('@', mulai);
			if (pos == -1) {
				return -1;
			} else if (pos >= length - 1) {
				return -1; // tepat di akhir string, maka anggaplah tidak ada
			} else {
				char c = isi.charAt(pos + 1);
				if (c == '0' || c == '1' || c == '2') {
					return pos;
				} else {
					mulai = pos+2;
				}
			}
		}
	}

	/**
	 * @param ayat mulai dari 1
	 */
	static SpannableStringBuilder tampilanAyatSederhana(int ayat, String isi, int warnaStabilo) {
		SpannableStringBuilder seayat = new SpannableStringBuilder();
		
		String ayat_s = String.valueOf(ayat);
		
		seayat.append(ayat_s).append(' ').append(isi);
		seayat.setSpan(new ForegroundColorSpan(S.penerapan.warnaNomerAyat), 0, ayat_s.length(), 0);
		
		seayat.setSpan(new LeadingMarginSpan.Standard(0, S.getIndenParagraf()), 0, seayat.length(), 0);
		
		if (warnaStabilo != 0) {
			seayat.setSpan(new BackgroundColorSpan(warnaStabilo), ayat_s.length() + 1, seayat.length(), 0);
		}
		
		return seayat;
	}
}