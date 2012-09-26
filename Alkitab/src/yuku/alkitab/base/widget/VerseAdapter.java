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

import java.util.Arrays;

import yuku.alkitab.R;
import yuku.alkitab.base.IsiActivity;
import yuku.alkitab.base.S;
import yuku.alkitab.base.S.applied;
import yuku.alkitab.base.U;
import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.model.Book;
import yuku.alkitab.base.model.PericopeBlock;
import yuku.alkitab.base.storage.Db.Bukmak2;
import yuku.alkitab.base.util.Appearances;

public class VerseAdapter extends BaseAdapter {
	public static final String TAG = VerseAdapter.class.getSimpleName();

	// # field ctor
	final Context context_;
	final CallbackSpan.OnClickListener parallelListener_;
	final IsiActivity.AttributeListener attributeListener_;
	final float density_;

	// # field setData
	Book book_;
	int chapter_1_;
	String[] verseTextData_;
	PericopeBlock[] pericopeBlocks_;
	/**
	 * Tiap elemen, kalo 0 sampe positif, berarti menunjuk ke AYAT di rendered_
	 * kalo negatif, -1 berarti index 0 di perikop_*, -2 (a) berarti index 1 (b) di perikop_*
	 * 
	 * Konvert a ke b: -(a+1); // -a-1 juga sih sebetulnya. gubrak.
	 * Konvert b ke a: -b-1;
	 */
	private int[] itemPointer_;
	private int[] attributeMap_; // bit 0(0x1) = bukmak; bit 1(0x2) = catatan; bit 2(0x4) = stabilo;
	private int[] highlightMap_; // null atau warna stabilo

	public VerseAdapter(Context context, CallbackSpan.OnClickListener paralelListener, IsiActivity.AttributeListener attributeListener) {
		context_ = context;
		parallelListener_ = paralelListener;
		attributeListener_ = attributeListener;
		density_ = context.getResources().getDisplayMetrics().density;
	}

	public synchronized void setData(Book book, int chapter_1, String[] verseTextData, int[] pericopeAris, PericopeBlock[] pericopeBlocks, int nblock) {
		book_ = book;
		chapter_1_ = chapter_1;
		verseTextData_ = verseTextData.clone();
		pericopeBlocks_ = pericopeBlocks;
		itemPointer_ = makeItemPointer(verseTextData_.length, pericopeAris, pericopeBlocks, nblock);
	}

	public synchronized void loadAttributeMap() {
		int[] attributeMap = null;
		int[] highlightMap = null;

		int ariBc = Ari.encode(book_.bookId, chapter_1_, 0x00);
		if (S.getDb().countAtribut(ariBc) > 0) {
			attributeMap = new int[verseTextData_.length];
			highlightMap = S.getDb().putAtribut(ariBc, attributeMap);
		}

		attributeMap_ = attributeMap;
		highlightMap_ = highlightMap;

		notifyDataSetChanged();
	}

	@Override public synchronized int getCount() {
		if (verseTextData_ == null) return 0;

		return itemPointer_.length;
	}

	@Override public synchronized String getItem(int position) {
		int id = itemPointer_[position];

		if (id >= 0) {
			return verseTextData_[position].toString();
		} else {
			return pericopeBlocks_[-id - 1].toString();
		}
	}

	@Override public synchronized long getItemId(int position) {
		return itemPointer_[position];
	}

	@Override public synchronized View getView(int position, View convertView, ViewGroup parent) {
		// Harus tentukan apakah ini perikop ato ayat.
		int id = itemPointer_[position];

		if (id >= 0) {
			// AYAT. bukan judul perikop.

			VerseItem res;

			String text = verseTextData_[id];
			boolean withBookmark = attributeMap_ == null ? false : (attributeMap_[id] & 0x1) != 0;
			boolean withNote = attributeMap_ == null ? false : (attributeMap_[id] & 0x2) != 0;
			boolean withHighlight = attributeMap_ == null ? false : (attributeMap_[id] & 0x4) != 0;
			int highlightColor = withHighlight ? (highlightMap_ == null ? 0 : U.alphaMixHighlight(highlightMap_[id])) : 0;

			boolean checked = false;
			if (parent instanceof ListView) {
				checked = ((ListView) parent).isItemChecked(position);
			}

			// Udah ditentukan bahwa ini ayat dan bukan perikop, sekarang tinggal tentukan
			// apakah ayat ini pake formating biasa (tanpa menjorok dsb) atau ada formating
			if (text.length() > 0 && text.charAt(0) == '@') {
				// karakter kedua harus '@' juga, kalo bukan ada ngaco
				if (text.charAt(1) != '@') {
					throw new RuntimeException("Karakter kedua bukan @. Isi ayat: " + text); //$NON-NLS-1$
				}

				if (convertView == null || convertView.getId() != R.layout.item_verse_tiled) {
					res = (VerseItem) LayoutInflater.from(context_).inflate(R.layout.item_verse_tiled, null);
					res.setId(R.layout.item_verse_tiled);
				} else {
					res = (VerseItem) convertView;
				}

				tiledVerseDisplay(res.findViewById(R.id.sebelahKiri), id + 1, text, highlightColor, checked);

			} else {
				if (convertView == null || convertView.getId() != R.layout.item_verse_simple) {
					res = (VerseItem) LayoutInflater.from(context_).inflate(R.layout.item_verse_simple, null);
					res.setId(R.layout.item_verse_simple);
				} else {
					res = (VerseItem) convertView;
				}

				TextView lIsiAyat = (TextView) res.findViewById(R.id.lIsiAyat);
				simpleVerseDisplay(lIsiAyat, id + 1, text, highlightColor, checked);

				Appearances.applyTextAppearance(lIsiAyat);
				if (checked) lIsiAyat.setTextColor(0xff000000); // override with black!
			}

			View imgAttributeBookmark = res.findViewById(R.id.imgAtributBukmak);
			imgAttributeBookmark.setVisibility(withBookmark ? View.VISIBLE : View.GONE);
			if (withBookmark) {
				setClickListenerForBookmark(imgAttributeBookmark, chapter_1_, id + 1);
			}
			View imgAttributeNote = res.findViewById(R.id.imgAtributCatatan);
			imgAttributeNote.setVisibility(withNote ? View.VISIBLE : View.GONE);
			if (withNote) {
				setClickListenerForNote(imgAttributeNote, chapter_1_, id + 1);
			}

			return res;
		} else {
			// JUDUL PERIKOP. bukan ayat.

			View res;
			if (convertView == null || convertView.getId() != R.layout.pericope_header) {
				res = LayoutInflater.from(context_).inflate(R.layout.pericope_header, null);
				res.setId(R.layout.pericope_header);
			} else {
				res = convertView;
			}

			PericopeBlock pericopeBlock = pericopeBlocks_[-id - 1];

			TextView lJudul = (TextView) res.findViewById(R.id.lJudul);
			TextView lXparalel = (TextView) res.findViewById(R.id.lXparalel);

			lJudul.setText(pericopeBlock.title);

			// matikan padding atas kalau position == 0 ATAU sebelum ini juga judul perikop
			if (position == 0 || itemPointer_[position - 1] < 0) {
				lJudul.setPadding(0, 0, 0, 0);
			} else {
				lJudul.setPadding(0, (int) (S.applied.fontSize2dp * density_), 0, 0);
			}

			Appearances.applyPericopeTitleAppearance(lJudul);

			// gonekan paralel kalo ga ada
			if (pericopeBlock.xparalel.length == 0) {
				lXparalel.setVisibility(View.GONE);
			} else {
				lXparalel.setVisibility(View.VISIBLE);

				SpannableStringBuilder sb = new SpannableStringBuilder("("); //$NON-NLS-1$
				int len = 1;

				int total = pericopeBlock.xparalel.length;
				for (int i = 0; i < total; i++) {
					String parallel = pericopeBlock.xparalel[i];

					if (i > 0) {
						// paksa new line untuk pola2 paralel tertentu
						if ((total == 6 && i == 3) || (total == 4 && i == 2) || (total == 5 && i == 3)) {
							sb.append("; \n"); //$NON-NLS-1$
							len += 3;
						} else {
							sb.append("; "); //$NON-NLS-1$
							len += 2;
						}
					}

					sb.append(parallel);
					sb.setSpan(new CallbackSpan(parallel, parallelListener_), len, len + parallel.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
					len += parallel.length();
				}
				sb.append(')');
				len += 1;

				lXparalel.setText(sb, BufferType.SPANNABLE);
				Appearances.applyPericopeParallelTextAppearance(lXparalel);
			}

			return res;
		}
	}

	void setClickListenerForBookmark(View imgBukmak, final int pasal_1, final int ayat_1) {
		imgBukmak.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) {
				attributeListener_.onClick(book_, pasal_1, ayat_1, Bukmak2.kind_bookmark);
			}
		});
	}

	void setClickListenerForNote(View imgCatatan, final int pasal_1, final int ayat_1) {
		imgCatatan.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) {
				attributeListener_.onClick(book_, pasal_1, ayat_1, Bukmak2.kind_note);
			}
		});
	}

	/**
	 * Kalau pos 0: perikop; pos 1: ayat_1 1;
	 * maka fungsi ini (ayat_1: 1) akan return 0.
	 * 
	 * @return position di adapter ini atau -1 kalo ga ketemu
	 */
	public int getPositionOfPericopeBeginningFromVerse(int ayat_1) {
		if (itemPointer_ == null) return -1;

		int ayat_0 = ayat_1 - 1;

		for (int i = 0, len = itemPointer_.length; i < len; i++) {
			if (itemPointer_[i] == ayat_0) {
				// ketemu, tapi kalo ada judul perikop, akan lebih baik. Coba cek mundur dari sini
				for (int j = i - 1; j >= 0; j--) {
					if (itemPointer_[j] < 0) {
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
	 * 
	 * @return position di adapter ini atau -1 kalo ga ketemu
	 */
	public int getPositionAbaikanPerikopDariAyat(int ayat_1) {
		if (itemPointer_ == null) return -1;

		int ayat_0 = ayat_1 - 1;

		for (int i = 0, len = itemPointer_.length; i < len; i++) {
			if (itemPointer_[i] == ayat_0) return i;
		}

		return -1;
	}

	/**
	 * @return ayat (mulai dari 1). atau 0 kalo ga masuk akal
	 */
	public int getVerseFromPosition(int position) {
		if (itemPointer_ == null) return 0;

		if (position >= itemPointer_.length) {
			position = itemPointer_.length - 1;
		}

		int id = itemPointer_[position];

		if (id >= 0) {
			return id + 1;
		}

		// perikop nih. Susuri sampe abis
		for (int i = position + 1; i < itemPointer_.length; i++) {
			id = itemPointer_[i];

			if (id >= 0) {
				return id + 1;
			}
		}

		Log.w(TAG, "masa judul perikop di paling bawah? Ga masuk akal."); //$NON-NLS-1$
		return 0;
	}

	public void tiledVerseDisplay(View res, int ayat_1, String isi, int warnaStabilo, boolean checked) {
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
		// @8 = tanda kasi jarak ke ayat berikutnya [kategori: format]
		int parsingPos = 2; // mulai setelah @@
		int indent = 0;
		boolean pleaseExit = false;
		boolean noTileYet = true;
		boolean verseNumberWritten = false;

		LinearLayout tileContainer = (LinearLayout) res.findViewById(R.id.tempatTehel);
		tileContainer.removeAllViews();

		char[] isi_c = isi.toCharArray();

		while (true) {
			// cari posisi penehel berikutnya
			int pos_until = indexOfTiler(isi, parsingPos);

			if (pos_until == -1) {
				// abis
				pos_until = isi_c.length;
				pleaseExit = true;
			}

			if (parsingPos == pos_until) {
				// di awal, belum ada apa2!
			} else {
				// bikin tehel
				{
					TextView tile = new TextView(context_);
					if (indent == 1) {
						tile.setPadding(S.applied.indentSpacing1 + (ayat_1 >= 100 ? S.applied.indentSpacingExtra : 0), 0, 0, 0);
					} else if (indent == 2) {
						tile.setPadding(S.applied.indentSpacing2 + (ayat_1 >= 100 ? S.applied.indentSpacingExtra : 0), 0, 0, 0);
					} else if (indent == 3) {
						tile.setPadding(S.applied.indentSpacing3 + (ayat_1 >= 100 ? S.applied.indentSpacingExtra : 0), 0, 0, 0);
					} else if (indent == 4) {
						tile.setPadding(S.applied.indentSpacing4 + (ayat_1 >= 100 ? S.applied.indentSpacingExtra : 0), 0, 0, 0);
					}

					// kasus: belum ada tehel dan tehel pertama menjorok 0
					if (noTileYet && indent == 0) {
						// # kasih no ayat di depannya
						SpannableStringBuilder s = new SpannableStringBuilder();
						String verse_s = String.valueOf(ayat_1);
						s.append(verse_s).append(' ');
						appendFormattedText2(s, isi, isi_c, parsingPos, pos_until);
						if (!checked) {
							s.setSpan(new ForegroundColorSpan(applied.verseNumberColor), 0, verse_s.length(), 0);
						}
						s.setSpan(new LeadingMarginSpan.Standard(0, S.applied.paragraphIndentSpacing), 0, s.length(), 0);
						if (warnaStabilo != 0) {
							s.setSpan(new BackgroundColorSpan(warnaStabilo), verse_s.length() + 1, s.length(), 0);
						}
						tile.setText(s, BufferType.SPANNABLE);

						// kasi tanda biar nanti ga tulis nomer ayat lagi
						verseNumberWritten = true;
					} else {
						SpannableStringBuilder s = new SpannableStringBuilder();
						appendFormattedText2(s, isi, isi_c, parsingPos, pos_until);
						if (warnaStabilo != 0) {
							s.setSpan(new BackgroundColorSpan(warnaStabilo), 0, s.length(), 0);
						}
						tile.setText(s);
					}

					Appearances.applyTextAppearance(tile);
					if (checked) tile.setTextColor(0xff000000); // override with black!

					tileContainer.addView(tile);
				}

				noTileYet = false;
			}

			if (pleaseExit) break;

			char marker = isi_c[pos_until + 1];
			if (marker == '1') {
				indent = 1;
			} else if (marker == '2') {
				indent = 2;
			} else if (marker == '3') {
				indent = 3;
			} else if (marker == '4') {
				indent = 4;
			}

			parsingPos = pos_until + 2;
		}

		TextView lAyat = (TextView) res.findViewById(R.id.lAyat);
		if (verseNumberWritten) {
			lAyat.setText(""); //$NON-NLS-1$
		} else {
			lAyat.setText(String.valueOf(ayat_1));
			Appearances.applyVerseNumberAppearance(lAyat);
			if (checked) lAyat.setTextColor(0xff000000);
		}
	}

	/**
	 * taro teks dari text[pos_from..pos_until] dengan format 6 atau 5 atau 9 atau 7 atau 8 ke s
	 * 
	 * @param text_c
	 *            string yang dari posDari sampe sebelum posSampe hanya berisi 6 atau 5 atau 9 atau 7 atau 8 tanpa mengandung @ lain.
	 */
	private void appendFormattedText2(SpannableStringBuilder s, String text, char[] text_c, int pos_from, int pos_until) {
		int redStart = -1; // posisi basis s. -1 artinya belum ketemu
		int italicStart = -1; // posisi basis s. -1 artinya belum ketemu

		for (int i = pos_from; i < pos_until; i++) {
			// coba templok aja sampe ketemu @ berikutnya. Jadi jangan satu2.
			{
				int nextAtPos = text.indexOf('@', i);
				if (nextAtPos == -1) {
					// udah ga ada lagi, tumplekin semua dan keluar dari method ini
					s.append(text, i, pos_until);
					return;
				} else {
					// tumplekin sampe sebelum @
					if (nextAtPos != i) { // kalo ga 0 panjangnya
						s.append(text, i, nextAtPos);
					}
					i = nextAtPos;
				}
			}

			i++; // satu char setelah @
			if (i >= pos_until) {
				// out of bounds
				break;
			}

			char d = text_c[i];
			if (d == '8') {
				s.append('\n');
				continue;
			}

			if (d == '6') { // merah start
				redStart = s.length();
				continue;
			}

			if (d == '9') { // italic start
				italicStart = s.length();
				continue;
			}

			if (d == '5') { // merah ends
				if (redStart != -1) {
					s.setSpan(new ForegroundColorSpan(S.applied.fontRedColor), redStart, s.length(), 0);
					redStart = -1; // reset
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
	private int indexOfTiler(String text, int start) {
		int length = text.length();
		while (true) {
			int pos = text.indexOf('@', start);
			if (pos == -1) {
				return -1;
			} else if (pos >= length - 1) {
				return -1; // tepat di akhir string, maka anggaplah tidak ada
			} else {
				char c = text.charAt(pos + 1);
				if (c >= '0' && c <= '4') {
					return pos;
				} else {
					start = pos + 2;
				}
			}
		}
	}

	static void simpleVerseDisplay(TextView lIsiAyat, int verse_1, String text, int highlightColor, boolean checked) {
		SpannableStringBuilder verse = new SpannableStringBuilder(
			"1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890" + //$NON-NLS-1$
			"1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890" + //$NON-NLS-1$
			"1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890" //$NON-NLS-1$
		); // pre-allocate
		
		verse.clear();

		// nomer ayat
		String verse_s = String.valueOf(verse_1);
		verse.append(verse_s).append(' ').append(text);
		if (!checked) {
			verse.setSpan(new ForegroundColorSpan(S.applied.verseNumberColor), 0, verse_s.length(), 0);
		}

		// teks
		verse.setSpan(new LeadingMarginSpan.Standard(0, S.applied.paragraphIndentSpacing), 0, verse.length(), 0);

		if (highlightColor != 0) {
			verse.setSpan(new BackgroundColorSpan(highlightColor), verse_s.length() + 1, verse.length(), 0);
		}

		lIsiAyat.setText(verse, BufferType.SPANNABLE);
	}

	public String getVerse(int verse_1) {
		if (verseTextData_ == null) return "[?]"; //$NON-NLS-1$
		if (verse_1 < 1 || verse_1 > verseTextData_.length) return "[?]"; //$NON-NLS-1$
		return verseTextData_[verse_1 - 1];
	}

	@Override public boolean areAllItemsEnabled() {
		return false;
	}

	@Override public boolean isEnabled(int position) {
		return getItemId(position) >= 0;
	}
	
	public static int[] makeItemPointer(int nverse, int[] perikop_xari, PericopeBlock[] perikop_xblok, int nblock) {
		int[] res = new int[nverse + nblock];

		int pos_block = 0;
		int pos_verse = 0;
		int pos_itemPointer = 0;

		while (true) {
			// cek apakah judul perikop, DAN perikop masih ada
			if (pos_block < nblock) {
				// masih memungkinkan
				if (Ari.toVerse(perikop_xari[pos_block]) - 1 == pos_verse) {
					// ADA PERIKOP.
					res[pos_itemPointer++] = -pos_block - 1;
					pos_block++;
					continue;
				}
			}

			// cek apakah ga ada ayat lagi
			if (pos_verse >= nverse) {
				break;
			}

			// uda ga ada perikop, ATAU belom saatnya perikop. Maka masukin ayat.
			res[pos_itemPointer++] = pos_verse;
			pos_verse++;
			continue;
		}

		if (res.length != pos_itemPointer) {
			// ada yang ngaco! di algo di atas
			throw new RuntimeException("Algo selip2an perikop salah! posPK=" + pos_itemPointer + " posAyat=" + pos_verse + " posBlok=" + pos_block + " nayat=" + nverse + " nblok=" + nblock //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
					+ " xari:" + Arrays.toString(perikop_xari) + " xblok:" + Arrays.toString(perikop_xblok));  //$NON-NLS-1$//$NON-NLS-2$
		}

		return res;
	}
}