package yuku.alkitab.base.widget;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.BufferType;

import yuku.alkitab.R;
import yuku.alkitab.base.IsiActivity;
import yuku.alkitab.base.S;
import yuku.alkitab.base.S.penerapan;
import yuku.alkitab.base.U;
import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.model.Blok;
import yuku.alkitab.base.model.Book;
import yuku.alkitab.base.storage.Db.Bukmak2;
import yuku.alkitab.base.util.PengaturTampilan;

public class VerseAdapter extends BaseAdapter {
	public static final String TAG = VerseAdapter.class.getSimpleName();
	
	//# field ctor
	final Context context_;
	final CallbackSpan.OnClickListener paralelListener_;
	final IsiActivity.AttributeListener atributListener_;
	final float density_;
	
	//# field setData
	Book kitab_;
	int pasal_1_;
	String[] dataAyat_;
	Blok[] perikop_xblok_;
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

	public VerseAdapter(Context context, CallbackSpan.OnClickListener paralelListener, IsiActivity.AttributeListener attributeListener) {
		context_ = context;
		paralelListener_ = paralelListener;
		atributListener_ = attributeListener;
		density_ = context.getResources().getDisplayMetrics().density;
	}
	
	public synchronized void setData(Book book, int pasal_1, String[] xayat, int[] perikop_xari, Blok[] perikop_xblok, int nblok) {
		kitab_ = book;
		pasal_1_ = pasal_1;
		dataAyat_ = xayat.clone();
		perikop_xblok_ = perikop_xblok;
		penunjukKotak_ = U.bikinPenunjukKotak(dataAyat_.length, perikop_xari, perikop_xblok, nblok);
	}
	
	public synchronized void loadAttributeMap() {
		int[] atributMap = null;
		int[] stabiloMap = null;
		
		int ariKp = Ari.encode(kitab_.pos, pasal_1_, 0x00);
		if (S.getDb().countAtribut(ariKp) > 0) {
			atributMap = new int[dataAyat_.length];
			stabiloMap = S.getDb().putAtribut(ariKp, atributMap);
		}

		atributMap_ = atributMap;
		stabiloMap_ = stabiloMap;
		
		notifyDataSetChanged();
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
	
	@Override public synchronized View getView(int position, View convertView, ViewGroup parent) {
		// Harus tentukan apakah ini perikop ato ayat.
		int id = penunjukKotak_[position];
		
		if (id >= 0) {
			// AYAT. bukan judul perikop.
			
			AyatItem res;
			
			String isi = dataAyat_[id];
			boolean pakeBukmak = atributMap_ == null? false: (atributMap_[id] & 0x1) != 0;
			boolean pakeCatatan = atributMap_ == null? false: (atributMap_[id] & 0x2) != 0;
			boolean pakeStabilo = atributMap_ == null? false: (atributMap_[id] & 0x4) != 0;
			int warnaStabilo = pakeStabilo? (stabiloMap_ == null? 0: U.alphaMixStabilo(stabiloMap_[id])): 0;
			
			boolean checked = false;
			if (parent instanceof ListView) {
				checked = ((ListView)parent).isItemChecked(position);
			}
			
			// Udah ditentukan bahwa ini ayat dan bukan perikop, sekarang tinggal tentukan
			// apakah ayat ini pake formating biasa (tanpa menjorok dsb) atau ada formating
			if (isi.length() > 0 && isi.charAt(0) == '@') {
				// karakter kedua harus '@' juga, kalo bukan ada ngaco
				if (isi.charAt(1) != '@') {
					throw new RuntimeException("Karakter kedua bukan @. Isi ayat: " + isi); //$NON-NLS-1$
				}
				
				if (convertView == null || convertView.getId() != R.layout.item_ayat_tehel) {
					res = (AyatItem) LayoutInflater.from(context_).inflate(R.layout.item_ayat_tehel, null);
					res.setId(R.layout.item_ayat_tehel);
				} else {
					res = (AyatItem) convertView;
				}
				
				tampilanAyatTehel(res.findViewById(R.id.sebelahKiri), id + 1, isi, warnaStabilo, checked);
				
			} else {
				if (convertView == null || convertView.getId() != R.layout.item_ayat_sederhana) {
					res = (AyatItem) LayoutInflater.from(context_).inflate(R.layout.item_ayat_sederhana, null);
					res.setId(R.layout.item_ayat_sederhana);
				} else {
					res = (AyatItem) convertView;
				}
				
				TextView lIsiAyat = (TextView) res.findViewById(R.id.lIsiAyat);
				tampilanAyatSederhana(lIsiAyat, id + 1, isi, warnaStabilo, checked);
				
				PengaturTampilan.aturTampilanTeksIsi(lIsiAyat);
				if (checked) lIsiAyat.setTextColor(0xff000000); // override with black!
			}

			View imgAtributBukmak = res.findViewById(R.id.imgAtributBukmak);
			imgAtributBukmak.setVisibility(pakeBukmak? View.VISIBLE: View.GONE);
			if (pakeBukmak) {
				pasangClickHandlerUntukBukmak(imgAtributBukmak, pasal_1_, id + 1);
			}
			View imgAtributCatatan = res.findViewById(R.id.imgAtributCatatan);
			imgAtributCatatan.setVisibility(pakeCatatan? View.VISIBLE: View.GONE);
			if (pakeCatatan) {
				pasangClickHandlerUntukCatatan(imgAtributCatatan, pasal_1_, id + 1);
			}
			
			return res;
		} else {
			// JUDUL PERIKOP. bukan ayat.

			View res;
			if (convertView == null || convertView.getId() != R.layout.header_perikop) {
				res = LayoutInflater.from(context_).inflate(R.layout.header_perikop, null);
				res.setId(R.layout.header_perikop);
			} else {
				res = convertView;
			}
			
			Blok blok = perikop_xblok_[-id-1];
			
			TextView lJudul = (TextView) res.findViewById(R.id.lJudul);
			TextView lXparalel = (TextView) res.findViewById(R.id.lXparalel);
			
			lJudul.setText(blok.judul);
			
			// matikan padding atas kalau position == 0 ATAU sebelum ini juga judul perikop
			if (position == 0 || penunjukKotak_[position-1] < 0) {
				lJudul.setPadding(0, 0, 0, 0);
			} else {
				lJudul.setPadding(0, (int) (S.penerapan.ukuranHuruf2dp * density_), 0, 0);
			}
			
			PengaturTampilan.aturTampilanTeksJudulPerikop(lJudul);
			
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
				
				lXparalel.setText(sb, BufferType.SPANNABLE);
				PengaturTampilan.aturTampilanTeksParalelPerikop(lXparalel);
			}
			
			return res;
		}
	}

	void pasangClickHandlerUntukBukmak(View imgBukmak, final int pasal_1, final int ayat_1) {
		imgBukmak.setOnClickListener(new View.OnClickListener() { 
			@Override public void onClick(View v) {
				atributListener_.onClick(kitab_, pasal_1, ayat_1, Bukmak2.jenis_bukmak);
			}
		});
	}
	
	void pasangClickHandlerUntukCatatan(View imgCatatan, final int pasal_1, final int ayat_1) {
		imgCatatan.setOnClickListener(new View.OnClickListener() { 
			@Override public void onClick(View v) {
				atributListener_.onClick(kitab_, pasal_1, ayat_1, Bukmak2.jenis_catatan);
			}
		});
	}
	
	/**
	 * Kalau pos 0: perikop; pos 1: ayat_1 1;
	 * maka fungsi ini (ayat_1: 1) akan return 0. 
	 * @return position di adapter ini atau -1 kalo ga ketemu
	 */
	public int getPositionOfPericopeBeginningFromVerse(int ayat_1) {
		if (penunjukKotak_ == null) return -1;
		
		int ayat_0 = ayat_1 - 1;
		
		for (int i = 0, len = penunjukKotak_.length; i < len; i++) {
			if (penunjukKotak_[i] == ayat_0) {
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
	 * Kalau pos 0: perikop; pos 1: ayat_1 1;
	 * maka fungsi ini (ayat_1: 1) akan return 1. 
	 * @return position di adapter ini atau -1 kalo ga ketemu
	 */
	public int getPositionAbaikanPerikopDariAyat(int ayat_1) {
		if (penunjukKotak_ == null) return -1;
		
		int ayat_0 = ayat_1 - 1;
		
		for (int i = 0, len = penunjukKotak_.length; i < len; i++) {
			if (penunjukKotak_[i] == ayat_0) return i;
		}
		
		return -1;
	}
	
	/**
	 * @return ayat (mulai dari 1). atau 0 kalo ga masuk akal
	 */
	public int getVerseFromPosition(int position) {
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

	public void tampilanAyatTehel(View res, int ayat_1, String isi, int warnaStabilo, boolean checked) {
		// Don't forget to modify indexOfPenehel below too
		// @@ = mulai ayat dengan tehel atau ayat dengan format
		// @0 = mulai menjorok 0 [kategori: penehel]
		// @1 = mulai menjorok 1 [kategori: penehel]
		// @2 = mulai menjorok 2 [kategori: penehel]
		// @3 = mulai menjorok 3 [kategori: penehel]
		// @4 = mulai menjorok 4 [kategori: penehel]
		// @6 = mulai teks merah [kategori: format]
		// @5 = akhir teks merah [kategori: format]
		// @9 = mulai teks miring [kategori: format]
		// @7 = akhir teks miring [kategori: format]
		// @8 = tanda kasi jarak ke ayat berikutnya  [kategori: format]
		int posParse = 2; // mulai setelah @@
		int menjorok = 0;
		boolean keluarlah = false;
		boolean belumAdaTehel = true;
		boolean nomerAyatUdaDitulis = false;
		
		LinearLayout tempatTehel = (LinearLayout) res.findViewById(R.id.tempatTehel);
		tempatTehel.removeAllViews();
		
		char[] isi_c = isi.toCharArray();
		
		while (true) {
			// cari posisi penehel berikutnya
			int posSampe = indexOfPenehel(isi, posParse);
	
			if (posSampe == -1) {
				// abis
				posSampe = isi_c.length;
				keluarlah = true;
			}
			
			if (posParse == posSampe) {
				// di awal, belum ada apa2!
			} else {
				// bikin tehel
				{
					TextView tehel = new TextView(context_);
					if (menjorok == 1) {
						tehel.setPadding(S.penerapan.jarakMenjorokSatu + (ayat_1 >= 100? S.penerapan.jarakMenjorokExtra: 0), 0, 0, 0);
					} else if (menjorok == 2) {
						tehel.setPadding(S.penerapan.jarakMenjorokDua + (ayat_1 >= 100? S.penerapan.jarakMenjorokExtra: 0), 0, 0, 0);
					} else if (menjorok == 3) {
						tehel.setPadding(S.penerapan.jarakMenjorokTiga + (ayat_1 >= 100? S.penerapan.jarakMenjorokExtra: 0), 0, 0, 0);
					} else if (menjorok == 4) {
						tehel.setPadding(S.penerapan.jarakMenjorokEmpat + (ayat_1 >= 100? S.penerapan.jarakMenjorokExtra: 0), 0, 0, 0);
					}
					
					// kasus: belum ada tehel dan tehel pertama menjorok 0
					if (belumAdaTehel && menjorok == 0) {
						//# kasih no ayat di depannya
						SpannableStringBuilder s = new SpannableStringBuilder();
						String ayat_s = String.valueOf(ayat_1);
						s.append(ayat_s).append(' ');
						appendFormattedText2(s, isi, isi_c, posParse, posSampe);
						if (!checked) {
							s.setSpan(new ForegroundColorSpan(penerapan.warnaNomerAyat), 0, ayat_s.length(), 0);
						}
						s.setSpan(new LeadingMarginSpan.Standard(0, S.penerapan.jarakIndenParagraf), 0, s.length(), 0);
						if (warnaStabilo != 0) {
							s.setSpan(new BackgroundColorSpan(warnaStabilo), ayat_s.length() + 1, s.length(), 0);
						}
						tehel.setText(s, BufferType.SPANNABLE);
						
						// kasi tanda biar nanti ga tulis nomer ayat lagi
						nomerAyatUdaDitulis = true;
					} else {
						SpannableStringBuilder s = new SpannableStringBuilder();
						appendFormattedText2(s, isi, isi_c, posParse, posSampe);
						if (warnaStabilo != 0) {
							s.setSpan(new BackgroundColorSpan(warnaStabilo), 0, s.length(), 0);
						}
						tehel.setText(s);
					}
					
					PengaturTampilan.aturTampilanTeksIsi(tehel);
					if (checked) tehel.setTextColor(0xff000000); // override with black!

					tempatTehel.addView(tehel);
				}
				
				belumAdaTehel = false;
			}
			
			if (keluarlah) break;
	
			char jenisTanda = isi_c[posSampe + 1];
			if (jenisTanda == '1') {
				menjorok = 1;
			} else if (jenisTanda == '2') {
				menjorok = 2;
			} else if (jenisTanda == '3') {
				menjorok = 3;
			} else if (jenisTanda == '4') {
				menjorok = 4;
			}
			
			posParse = posSampe+2;
		}
		
		TextView lAyat = (TextView) res.findViewById(R.id.lAyat);
		if (nomerAyatUdaDitulis) {
			lAyat.setText(""); //$NON-NLS-1$
		} else {
			lAyat.setText(String.valueOf(ayat_1));
			PengaturTampilan.aturTampilanTeksNomerAyat(lAyat);
			if (checked) lAyat.setTextColor(0xff000000);
		}
	}

	/**
	 * taro teks dari isi[posDari..posSampe] dengan format 6 atau 5 atau 9 atau 7 atau 8 ke s
	 * @param isi_c  string yang dari posDari sampe sebelum posSampe hanya berisi 6 atau 5 atau 9 atau 7 atau 8 tanpa mengandung @ lain.
	 */
	private void appendFormattedText2(SpannableStringBuilder s, String isi, char[] isi_c, int posDari, int posSampe) {
		int merahStart = -1; // posisi basis s. -1 artinya belum ketemu
		int italicStart = -1; // posisi basis s. -1 artinya belum ketemu
		
		for (int i = posDari; i < posSampe; i++) {
			// coba templok aja sampe ketemu @ berikutnya. Jadi jangan satu2.
			{
				int posAtBerikut = isi.indexOf('@', i);
				if (posAtBerikut == -1) {
					// udah ga ada lagi, tumplekin semua dan keluar dari method ini
					s.append(isi, i, posSampe);
					return;
				} else {
					// tumplekin sampe sebelum @
					if (posAtBerikut != i) { // kalo ga 0 panjangnya
						s.append(isi, i, posAtBerikut);
					}
					i = posAtBerikut;
				}
			}
			
			i++; // satu char setelah @
			if (i >= posSampe) {
				// out of bounds
				break;
			}
			
			char d = isi_c[i];
			if (d == '8') {
				s.append('\n');
				continue;
			}
			
			if (d == '6') { // merah start
				merahStart = s.length();
				continue;
			}
			
			if (d == '9') { // italic start
				italicStart = s.length();
				continue;
			}
			
			if (d == '5') { // merah ends
				if (merahStart != -1) {
					s.setSpan(new ForegroundColorSpan(S.penerapan.warnaHurufMerah), merahStart, s.length(), 0);
					merahStart = -1; // reset
				}
				continue;
			}
			
			if (d == '7') { // italic ends
				if (italicStart != -1) {
					s.setSpan(new StyleSpan(Typeface.ITALIC), italicStart, s.length(), 0);
					italicStart = -1; // reset
				}
				continue;
			}
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
				if (c >= '0' && c <= '4') {
					return pos;
				} else {
					mulai = pos+2;
				}
			}
		}
	}

	static void tampilanAyatSederhana(TextView lIsiAyat, int ayat_1, String isi, int warnaStabilo, boolean checked) {
		SpannableStringBuilder seayat = new SpannableStringBuilder(longPlaceholderString); // pre-allocate
		seayat.clear();

		// nomer ayat
		String ayat_s = String.valueOf(ayat_1);
		seayat.append(ayat_s).append(' ').append(isi);
		if (!checked) {
			seayat.setSpan(new ForegroundColorSpan(S.penerapan.warnaNomerAyat), 0, ayat_s.length(), 0);
		}
		
		// teks
		seayat.setSpan(new LeadingMarginSpan.Standard(0, S.penerapan.jarakIndenParagraf), 0, seayat.length(), 0);
		
		if (warnaStabilo != 0) {
			seayat.setSpan(new BackgroundColorSpan(warnaStabilo), ayat_s.length() + 1, seayat.length(), 0);
		}
		
		lIsiAyat.setText(seayat, BufferType.SPANNABLE);
	}
	
	public String getVerse(int ayat_1) {
		if (dataAyat_ == null) return "[?]"; //$NON-NLS-1$
		if (ayat_1 < 1 || ayat_1 > dataAyat_.length) return "[?]"; //$NON-NLS-1$
		return dataAyat_[ayat_1 - 1];
	}
	
	@Override public boolean areAllItemsEnabled() {
		return false;
	}
	
	@Override public boolean isEnabled(int position) {
		return getItemId(position) >= 0;
	}
	
	static String longPlaceholderString = 
		"1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890" + //$NON-NLS-1$
		"1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890" + //$NON-NLS-1$
		"1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890"; //$NON-NLS-1$
}