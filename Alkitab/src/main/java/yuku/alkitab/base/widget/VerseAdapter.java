package yuku.alkitab.base.widget;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.BaseAdapter;
import yuku.alkitab.base.S;
import yuku.alkitab.base.storage.InternalDb;
import yuku.alkitab.model.Book;
import yuku.alkitab.model.PericopeBlock;
import yuku.alkitab.model.ProgressMark;
import yuku.alkitab.model.SingleChapterVerses;
import yuku.alkitab.util.Ari;

import java.util.Arrays;

public abstract class VerseAdapter extends BaseAdapter {
	public static final String TAG = VerseAdapter.class.getSimpleName();

	public static class Factory {
		public VerseAdapter create(Context context) {
			return new SingleViewVerseAdapter(context);
		}
	}

	// # field ctor
	final Context context_;
	CallbackSpan.OnClickListener<Object> parallelListener_;
	VersesView.AttributeListener attributeListener_;
	VerseInlineLinkSpan.Factory inlineLinkSpanFactory_;
	final float density_;

	// # field setData
	Book book_;
	int chapter_1_;
	SingleChapterVerses verses_;
	PericopeBlock[] pericopeBlocks_;

	/**
	 * For each element, if 0 or more, it refers to the 0-based verse number.
	 * If negative, -1 is the index 0 of pericope, -2 (a) is index 1 (b) of pericope, etc.
	 *
	 * Convert a to b: b = -a-1;
	 * Convert b to a: a = -b-1;
	 */
	int[] itemPointer_;

	int[] bookmarkCountMap_;
	int[] noteCountMap_;
	int[] highlightColorMap_;
	int[] progressMarkBitsMap_;

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
		// book_ can be empty when the selected (book, chapter) is not available in this version
		if (book_ == null) return;

		// 1/2: Attributes
		final int[] bookmarkCountMap;
		final int[] noteCountMap;
		final int[] highlightColorMap;

		final int verseCount = verses_.getVerseCount();
		final int ariBc = Ari.encode(book_.bookId, chapter_1_, 0x00);
		if (S.getDb().countMarkersForBookChapter(ariBc) > 0) {
			bookmarkCountMap = new int[verseCount];
			noteCountMap = new int[verseCount];
			highlightColorMap = new int[verseCount];

			S.getDb().putAttributes(ariBc, bookmarkCountMap, noteCountMap, highlightColorMap);
		} else {
			bookmarkCountMap = noteCountMap = highlightColorMap = null;
		}

		final int ariMin = ariBc & 0x00ffff00;
		final int ariMax = ariBc | 0x000000ff;

		// 2/2: Progress marks
		int[] progressMarkBitsMap = null;
		for (final ProgressMark progressMark: S.getDb().listAllProgressMarks()) {
			final int ari = progressMark.ari;
			if (ari < ariMin || ari >= ariMax) {
				continue;
			}

			if (progressMarkBitsMap == null) {
				progressMarkBitsMap = new int[verseCount];
			}

			int mapOffset = Ari.toVerse(ari) - 1;
			if (mapOffset >= progressMarkBitsMap.length) {
				Log.e(InternalDb.TAG, "mapOffset out of bounds: " + mapOffset + " happened on ari 0x" + Integer.toHexString(ari));
			} else {
				progressMarkBitsMap[mapOffset] |= 1 << (progressMark.preset_id + AttributeView.PROGRESS_MARK_BITS_START);
			}
		}

		// Finish calculating
		bookmarkCountMap_ = bookmarkCountMap;
		noteCountMap_ = noteCountMap;
		highlightColorMap_ = highlightColorMap;
		progressMarkBitsMap_ = progressMarkBitsMap;

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

	public void setParallelListener(CallbackSpan.OnClickListener<Object> parallelListener) {
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

		Log.w(TAG, "pericope title at the last position? does not make sense.");
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

	public int getVerseCount() {
		if (verses_ == null) return 0;
		return verses_.getVerseCount();
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
		}

		if (res.length != pos_itemPointer) {
			// ada yang ngaco! di algo di atas
			throw new RuntimeException("Algorithm to insert pericopes error!! pos_itemPointer=" + pos_itemPointer + " pos_verse=" + pos_verse + " pos_block=" + pos_block + " nverse=" + nverse + " nblock=" + nblock //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
					+ " xari:" + Arrays.toString(perikop_xari) + " xblok:" + Arrays.toString(perikop_xblok));  //$NON-NLS-1$//$NON-NLS-2$
		}

		return res;
	}
}