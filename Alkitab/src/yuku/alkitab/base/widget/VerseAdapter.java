package yuku.alkitab.base.widget;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.BaseAdapter;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.S;
import yuku.alkitab.util.Ari;
import yuku.alkitab.model.Book;
import yuku.alkitab.model.PericopeBlock;
import yuku.alkitab.model.ProgressMark;
import yuku.alkitab.model.SingleChapterVerses;
import yuku.alkitab.base.storage.InternalDb;

import java.util.Arrays;
import java.util.List;

public abstract class VerseAdapter extends BaseAdapter {
	public static final String TAG = VerseAdapter.class.getSimpleName();

	public static class Factory {
		int impl = 0; // 0 need check, 1 new (single view), 2 legacy
		
		public VerseAdapter create(Context context) {
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
				return new SingleViewVerseAdapter(context);
			} else if (impl == 2) {
				return new LegacyVerseAdapter(context);
			}
			
			return null;
		}
	}

	// # field ctor
	final Context context_;
	CallbackSpan.OnClickListener parallelListener_;
	VersesView.AttributeListener attributeListener_;
	VerseInlineLinkSpan.Factory inlineLinkSpanFactory_;
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
	int[] attributeMap_; // bit 0(0x1) = bookmark; bit 1(0x2) = notes; bit 2(0x4) = highlight; bit 8-12 = progress mark
	int[] highlightMap_; // null atau warna stabilo
	int[] ariRangesReadingPlan;

	LayoutInflater inflater_;
	VersesView owner_;
	
	public VerseAdapter(Context context) {
		context_ = context;
		density_ = context.getResources().getDisplayMetrics().density;
		inflater_ = LayoutInflater.from(context_);
	}

	public synchronized void setData(Book book, int chapter_1, SingleChapterVerses verses, int[] pericopeAris, PericopeBlock[] pericopeBlocks, int nblock) {
		book_ = book;
		chapter_1_ = chapter_1;
		verses_ = verses;
		pericopeBlocks_ = pericopeBlocks;
		itemPointer_ = makeItemPointer(verses_.getVerseCount(), pericopeAris, pericopeBlocks, nblock);

		notifyDataSetChanged();
	}

	public synchronized void setDataEmpty() {
		book_ = null;
		chapter_1_ = 0;
		verses_ = null;
		pericopeBlocks_ = null;
		itemPointer_ = null;

		notifyDataSetChanged();
	}

	public synchronized void reloadAttributeMap() {
		int[] attributeMap = null;
		int[] highlightMap = null;

		int ariBc = Ari.encode(book_.bookId, chapter_1_, 0x00);
		if (S.getDb().countAttributes(ariBc) > 0) {
			attributeMap = new int[verses_.getVerseCount()];
			highlightMap = S.getDb().putAttributes(ariBc, attributeMap);
		}

		int ariMin = ariBc & 0x00ffff00;
		int ariMax = ariBc | 0x000000ff;

		List<ProgressMark> progressMarks = S.getDb().listAllProgressMarks();

		for (ProgressMark progressMark: progressMarks) {
			final int ari = progressMark.ari;
			if (ari >= ariMin && ari < ariMax) {
				if (attributeMap == null) {
					attributeMap = new int[verses_.getVerseCount()];
				}

				int mapOffset = Ari.toVerse(ari) - 1;
				if (mapOffset >= attributeMap.length) {
					Log.e(InternalDb.TAG, "ofsetMap kebanyakan " + mapOffset + " terjadi pada ari 0x" + Integer.toHexString(ari));
				} else {
					attributeMap[mapOffset] |= 1 << (progressMark.preset_id + 8);
				}
			}
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

	public void setParallelListener(CallbackSpan.OnClickListener parallelListener) {
		parallelListener_ = parallelListener;
		notifyDataSetChanged();
	}
	
	public void setAttributeListener(VersesView.AttributeListener attributeListener) {
		attributeListener_ = attributeListener;
		notifyDataSetChanged();
	}

	public void setInlineLinkSpanFactory(final VerseInlineLinkSpan.Factory inlineLinkSpanFactory, VersesView owner) {
		inlineLinkSpanFactory_ = inlineLinkSpanFactory;
		owner_ = owner;
		notifyDataSetChanged();
	}

	/**
	 * Kalau pos 0: perikop; pos 1: verse_1 1;
	 * maka fungsi ini (verse_1: 1) akan return 0.
	 * 
	 * @return position di adapter ini atau -1 kalo ga ketemu
	 */
	public int getPositionOfPericopeBeginningFromVerse(int verse_1) {
		if (itemPointer_ == null) return -1;

		int verse_0 = verse_1 - 1;

		for (int i = 0, len = itemPointer_.length; i < len; i++) {
			if (itemPointer_[i] == verse_0) {
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
	 * Let's say pos 0 is pericope and pos 1 is verse_1 1;
	 * then this method called with verse_1=1 returns 1.
	 * 
	 * @return position or -1 if not found
	 */
	public int getPositionIgnoringPericopeFromVerse(int verse_1) {
		if (itemPointer_ == null) return -1;

		int verse_0 = verse_1 - 1;

		for (int i = 0, len = itemPointer_.length; i < len; i++) {
			if (itemPointer_[i] == verse_0) return i;
		}

		return -1;
	}

	/**
	 * @return verse_1 or 0 if doesn't make sense
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
	
	/**
	 * Similar to {@link #getVerseFromPosition(int)}, but returns 0 if the specified position is a pericope or doesn't make sense.
	 */
	public int getVerseOrPericopeFromPosition(int position) {
		if (itemPointer_ == null) return 0;

		if (position < 0 || position >= itemPointer_.length) {
			return 0;
		}

		int id = itemPointer_[position];

		if (id >= 0) {
			return id + 1;
		} else {
			return 0;
		}
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
			throw new RuntimeException("Algorithm to insert pericopes error!! pos_itemPointer=" + pos_itemPointer + " pos_verse=" + pos_verse + " pos_block=" + pos_block + " nverse=" + nverse + " nblock=" + nblock //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
					+ " xari:" + Arrays.toString(perikop_xari) + " xblok:" + Arrays.toString(perikop_xblok));  //$NON-NLS-1$//$NON-NLS-2$
		}

		return res;
	}

	protected boolean checkShadedForVerse(final int ari) {
		if (ariRangesReadingPlan != null) {

			int ariStart = ariRangesReadingPlan[0];
			int ariEnd = ariRangesReadingPlan[1];

			int ariEndVerse = Ari.toVerse(ariEnd);
			int ariEndChapter = Ari.toChapter(ariEnd);

			if (Ari.toBook(ari) != Ari.toBook(ariRangesReadingPlan[0])) {
				return true;
			}

			if (ari < ariStart) {
				return true;
			} else if (ariEndVerse == 0) {
				if (Ari.toChapter(ari) > ariEndChapter) {
					return true;
				}
			} else if (ariEndChapter != 0) {
				if (ari > ariEnd) {
					return true;
				}
			}

		}
		return false;
	}

	protected boolean checkShadedForPericopeHeader(final int ari_bc, final int position) {
		// look for first non-pericope
		int testPosition = position + 1;
		while (true) {
			if (testPosition >= itemPointer_.length) return false;
			if (itemPointer_[testPosition] >= 0) { // it's a verse
				return checkShadedForVerse(Ari.encodeWithBc(ari_bc, itemPointer_[testPosition] + 1));
			}
			testPosition++;
		}
	}
}