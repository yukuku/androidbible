package yuku.alkitab.base.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.FontMetricsInt;
import android.os.Build;
import android.text.Layout;
import android.text.TextPaint;
import android.text.style.LeadingMarginSpan;
import android.text.style.LineHeightSpan;
import android.text.style.MetricAffectingSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.BaseAdapter;

import java.util.Arrays;

import yuku.afw.storage.Preferences;
import yuku.alkitab.base.IsiActivity;
import yuku.alkitab.base.IsiActivity.AttributeListener;
import yuku.alkitab.base.S;
import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.model.Book;
import yuku.alkitab.base.model.PericopeBlock;
import yuku.alkitab.base.model.SingleChapterVerses;
import yuku.alkitab.base.storage.Db.Bukmak2;
import yuku.alkitab.base.widget.CallbackSpan.OnClickListener;

public abstract class VerseAdapter extends BaseAdapter {
	public static final String TAG = VerseAdapter.class.getSimpleName();

	public static class Factory {
		int impl = 0; // 0 need check, 1 new (single view), 2 legacy
		
		public VerseAdapter create(Context context, OnClickListener paralelListener, AttributeListener attributeListener) {
			if (impl == 0) {
				String useLegacyVerseRenderer = Preferences.getString("useLegacyVerseRenderer", "auto");
				if ("auto".equals(useLegacyVerseRenderer)) { // determine based on device
					if ("SEMC".equals(Build.BRAND) && Build.VERSION.SDK_INT >= 9 && Build.VERSION.SDK_INT <= 10) {
						impl = 2;
					} else {
						impl = 1;
					}
				} else if ("never".equals(useLegacyVerseRenderer)) {
					impl = 1;
				} else if ("always".equals(useLegacyVerseRenderer)) {
					impl = 2;
				} else { // just in case
					impl = 1;
				}
			}
			
			if (impl == 1) {
				return new SingleViewVerseAdapter(context, paralelListener, attributeListener);
			} else if (impl == 2) {
				return new LegacyVerseAdapter(context, paralelListener, attributeListener);
			}
			
			return null;
		}
	}
	
	static class ParagraphSpacingBefore implements LineHeightSpan {
		private final int before;
		
		ParagraphSpacingBefore(int before) {
			this.before = before;
		}
		
		@Override public void chooseHeight(CharSequence text, int start, int end, int spanstartv, int v, FontMetricsInt fm) {
			if (spanstartv == v) {
				fm.top -= before;
				fm.ascent -= before;
			}
		}
	}
	
	/**
	 * This is used instead of {@link LeadingMarginSpan.Standard} to overcome
	 * a bug in CyanogenMod 7.x. If we don't support CM 7 anymore, we 
	 * can use that instead of this, which seemingly *a bit* more efficient. 
	 */
	static class LeadingMarginSpanFixed implements LeadingMarginSpan.LeadingMarginSpan2 {
		private final int first;
		private final int rest;

		@Override public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int baseline, int bottom, CharSequence text, int start, int end, boolean first, Layout layout) {}
		
		public LeadingMarginSpanFixed(int all) {
			this.first = all;
			this.rest = all;
		}

		public LeadingMarginSpanFixed(int first, int rest) {
			this.first = first;
			this.rest = rest;
		}
		
		@Override public int getLeadingMargin(boolean first) {
			return first? this.first: this.rest;
		}

		@Override public int getLeadingMarginLineCount() {
			return 1;
		}
	}

	static class VerseNumberSpan extends MetricAffectingSpan {
		private final boolean applyColor;

		public VerseNumberSpan(boolean applyColor) {
			this.applyColor = applyColor;
		}
		
		@Override public void updateMeasureState(TextPaint tp) {
			tp.baselineShift += (int) (tp.ascent() * 0.3f + 0.5f);
			tp.setTextSize(tp.getTextSize() * 0.7f);
		}

		@Override public void updateDrawState(TextPaint tp) {
			tp.baselineShift += (int) (tp.ascent() * 0.3f + 0.5f);
			tp.setTextSize(tp.getTextSize() * 0.7f);
			if (applyColor) {
				tp.setColor(S.applied.verseNumberColor);
			}
		}
	}

    public static class ParallelTypeAri {
        public int ariStart;
    }

    public static class ParallelTypeOsis {
        public String osisStart;
    }

    public static class ParallelTypeLid {
        public int lidStart;
    }
	
	// # field ctor
	final Context context_;
	final CallbackSpan.OnClickListener parallelListener_;
	final IsiActivity.AttributeListener attributeListener_;
	final float density_;

	// # field setData
	Book book_;
	int chapter_1_;
	SingleChapterVerses verses_;
	PericopeBlock[] pericopeBlocks_;
	/**
	 * Tiap elemen, kalo 0 sampe positif, berarti menunjuk ke AYAT di rendered_
	 * kalo negatif, -1 berarti index 0 di perikop_*, -2 (a) berarti index 1 (b) di perikop_*
	 * 
	 * Konvert a ke b: -(a+1); // -a-1 juga sih sebetulnya. gubrak.
	 * Konvert b ke a: -b-1;
	 */
	int[] itemPointer_;
	int[] attributeMap_; // bit 0(0x1) = bukmak; bit 1(0x2) = catatan; bit 2(0x4) = stabilo;
	int[] highlightMap_; // null atau warna stabilo

	LayoutInflater inflater_;
	
	public VerseAdapter(Context context, CallbackSpan.OnClickListener paralelListener, IsiActivity.AttributeListener attributeListener) {
		context_ = context;
		parallelListener_ = paralelListener;
		attributeListener_ = attributeListener;
		density_ = context.getResources().getDisplayMetrics().density;
		inflater_ = LayoutInflater.from(context_);
	}

	/** 0 undefined. 1 and 2 based on version. */
	private static int leadingMarginSpanVersion = 0;
	
	/** Creates a leading margin span based on version:
	 * - API 7 or 11 and above: LeadingMarginSpan.Standard
	 * - API 8..10: LeadingMarginSpanFixed, which is based on LeadingMarginSpan.LeadingMarginSpan2
	 */
	static Object createLeadingMarginSpan(int all) {
		return createLeadingMarginSpan(all, all);
	}
	
	/** Creates a leading margin span based on version:
	 * - API 7 or 11 and above: LeadingMarginSpan.Standard
	 * - API 8..10: LeadingMarginSpanFixed, which is based on LeadingMarginSpan.LeadingMarginSpan2
	 */
	static Object createLeadingMarginSpan(int first, int rest) {
		if (leadingMarginSpanVersion == 0) {
			int v = Build.VERSION.SDK_INT;
			leadingMarginSpanVersion = (v == 7 || v >= 11)? 1: 2; 
		}
		
		if (leadingMarginSpanVersion == 1) {
			return new LeadingMarginSpan.Standard(first, rest); 
		} else {
			return new LeadingMarginSpanFixed(first, rest);
		}
	}
	
	public synchronized void setData(Book book, int chapter_1, SingleChapterVerses verses, int[] pericopeAris, PericopeBlock[] pericopeBlocks, int nblock) {
		book_ = book;
		chapter_1_ = chapter_1;
		verses_ = verses;
		pericopeBlocks_ = pericopeBlocks;
		itemPointer_ = makeItemPointer(verses_.getVerseCount(), pericopeAris, pericopeBlocks, nblock);
	}

	public synchronized void loadAttributeMap() {
		int[] attributeMap = null;
		int[] highlightMap = null;

		int ariBc = Ari.encode(book_.bookId, chapter_1_, 0x00);
		if (S.getDb().countAtribut(ariBc) > 0) {
			attributeMap = new int[verses_.getVerseCount()];
			highlightMap = S.getDb().putAtribut(ariBc, attributeMap);
		}

		attributeMap_ = attributeMap;
		highlightMap_ = highlightMap;

		notifyDataSetChanged();
	}

	@Override public synchronized int getCount() {
		if (verses_ == null) return 0;

		return itemPointer_.length;
	}

	@Override public synchronized String getItem(int position) {
		int id = itemPointer_[position];

		if (id >= 0) {
			return verses_.getVerse(position);
		} else {
			return pericopeBlocks_[-id - 1].toString();
		}
	}

	@Override public synchronized long getItemId(int position) {
		return itemPointer_[position];
	}

	protected void setClickListenerForBookmark(View imgBukmak, final int pasal_1, final int ayat_1) {
		imgBukmak.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) {
				attributeListener_.onClick(book_, pasal_1, ayat_1, Bukmak2.kind_bookmark);
			}
		});
	}

	protected void setClickListenerForNote(View imgCatatan, final int pasal_1, final int ayat_1) {
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


	public String getVerse(int verse_1) {
		if (verses_ == null) return "[?]"; //$NON-NLS-1$
		if (verse_1 < 1 || verse_1 > verses_.getVerseCount()) return "[?]"; //$NON-NLS-1$
		return verses_.getVerse(verse_1 - 1);
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