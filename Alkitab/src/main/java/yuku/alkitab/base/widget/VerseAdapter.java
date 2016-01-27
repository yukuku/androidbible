package yuku.alkitab.base.widget;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.BaseAdapter;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.util.Highlights;
import yuku.alkitab.model.PericopeBlock;
import yuku.alkitab.model.ProgressMark;
import yuku.alkitab.model.SingleChapterVerses;
import yuku.alkitab.model.Version;
import yuku.alkitab.util.Ari;

import java.util.Arrays;

public abstract class VerseAdapter extends BaseAdapter {
	public static final String TAG = VerseAdapter.class.getSimpleName();

	// # field ctor
	CallbackSpan.OnClickListener<Object> parallelListener_;
	VersesView.AttributeListener attributeListener_;
	VerseInlineLinkSpan.Factory inlineLinkSpanFactory_;
	final float density_;

	// # field setData
	int ari_bc_;
	SingleChapterVerses verses_;
	PericopeBlock[] pericopeBlocks_;
	Version version_;
	String versionId_;
	float textSizeMult_;

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
	Highlights.Info[] highlightInfoMap_;
	int[] progressMarkBitsMap_;
	boolean[] hasMapsMap_;

	LayoutInflater inflater_;

	// For calling attention. All attentioned verses have the same start time.
	// The last call to callAttentionForVerse() decides as when the animation starts.
	// Calling setData* methods clears the attentioned verses.
	long attentionStart_;
	TIntSet attentionPositions_;

	public VerseAdapter(Context context) {
		density_ = context.getResources().getDisplayMetrics().density;
		inflater_ = LayoutInflater.from(context);
	}

	/* non-public */ synchronized void setData(int ariBc, SingleChapterVerses verses, int[] pericopeAris, PericopeBlock[] pericopeBlocks, int nblock, @Nullable final Version version, @Nullable final String versionId) {
		ari_bc_ = ariBc;
		verses_ = verses;
		pericopeBlocks_ = pericopeBlocks;
		itemPointer_ = makeItemPointer(verses_.getVerseCount(), pericopeAris, pericopeBlocks, nblock);
		version_ = version;
		versionId_ = versionId;
		calculateTextSizeMult();
		attentionStart_ = 0;
		if (attentionPositions_ != null) {
			attentionPositions_.clear();
		}

		notifyDataSetChanged();
	}

	/* non-public */ synchronized void setDataEmpty() {
		ari_bc_ = 0;
		verses_ = null;
		pericopeBlocks_ = null;
		itemPointer_ = null;
		attentionStart_ = 0;
		if (attentionPositions_ != null) {
			attentionPositions_.clear();
		}

		notifyDataSetChanged();
	}

	public synchronized void reloadAttributeMap() {
		// book_ can be empty when the selected (book, chapter) is not available in this version
		if (ari_bc_ == 0) return;

		// 1/3: Bookmarks/Notes/Highlights
		final int[] bookmarkCountMap;
		final int[] noteCountMap;
		final Highlights.Info[] highlightColorMap;

		final int verseCount = verses_.getVerseCount();
		final int ariBc = ari_bc_;
		if (S.getDb().countMarkersForBookChapter(ariBc) > 0) {
			bookmarkCountMap = new int[verseCount];
			noteCountMap = new int[verseCount];
			highlightColorMap = new Highlights.Info[verseCount];

			S.getDb().putAttributes(ariBc, bookmarkCountMap, noteCountMap, highlightColorMap);
		} else {
			bookmarkCountMap = null;
			noteCountMap = null;
			highlightColorMap = null;
		}

		final int ariMin = ariBc & 0x00ffff00;
		final int ariMax = ariBc | 0x000000ff;

		// 2/3: Progress marks
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
				Log.e(TAG, "(for progressMarkBitsMap:) mapOffset out of bounds: " + mapOffset + " happened on ari 0x" + Integer.toHexString(ari));
			} else {
				progressMarkBitsMap[mapOffset] |= 1 << (progressMark.preset_id + AttributeView.PROGRESS_MARK_BITS_START);
			}
		}

		// 3/3: Location indicators
		// Look up for maps locations.
		// If the app is installed, query its content provider to see which verses has locations on the map.
		boolean[] hasMapsMap = null;
		{
			final ContentResolver cr = App.context.getContentResolver();
			final Uri uri = Uri.parse("content://palki.maps/exists?ari=" + ariBc);
			try (Cursor c = cr.query(uri, null, null, null, null)) {
				if (c != null) {
					final int col_aris = c.getColumnIndexOrThrow("aris");

					if (c.moveToNext()) {
						final String aris_json = c.getString(col_aris);
						final int[] aris = App.getDefaultGson().fromJson(aris_json, int[].class);

						if (aris != null) {
							hasMapsMap = new boolean[verseCount];

							for (final int ari : aris) {
								int mapOffset = Ari.toVerse(ari) - 1;
								if (mapOffset >= hasMapsMap.length) {
									Log.e(TAG, "(for hasMapsMap:) mapOffset out of bounds: " + mapOffset + " happened on ari 0x" + Integer.toHexString(ari));
								} else {
									hasMapsMap[mapOffset] = true;
								}
							}
						}
					}
				}
			}
		}

		// Finish calculating
		bookmarkCountMap_ = bookmarkCountMap;
		noteCountMap_ = noteCountMap;
		highlightInfoMap_ = highlightColorMap;
		progressMarkBitsMap_ = progressMarkBitsMap;
		hasMapsMap_ = hasMapsMap;

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
		return position;
	}

	public void setParallelListener(CallbackSpan.OnClickListener<Object> parallelListener) {
		parallelListener_ = parallelListener;
		notifyDataSetChanged();
	}

	public VersesView.AttributeListener getAttributeListener() {
		return attributeListener_;
	}
	
	public void setAttributeListener(VersesView.AttributeListener attributeListener) {
		attributeListener_ = attributeListener;
		notifyDataSetChanged();
	}

	public void setInlineLinkSpanFactory(final VerseInlineLinkSpan.Factory inlineLinkSpanFactory) {
		inlineLinkSpanFactory_ = inlineLinkSpanFactory;
		notifyDataSetChanged();
	}

	/**
	 * For example, when pos=0 is a pericope and pos=1 is the first verse,
	 * this method returns 0.
	 * 
	 * @return position on this adapter, or -1 if not found
	 */
	public int getPositionOfPericopeBeginningFromVerse(int verse_1) {
		if (itemPointer_ == null) return -1;

		int verse_0 = verse_1 - 1;

		for (int i = 0, len = itemPointer_.length; i < len; i++) {
			if (itemPointer_[i] == verse_0) {
				// we've found it, but if we can move back to pericopes, it is better.
				for (int j = i - 1; j >= 0; j--) {
					if (itemPointer_[j] < 0) {
						// it's still pericope, so let's continue
						i = j;
					} else {
						// no longer a pericope (means, we are on the previous verse)
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
		
		// it's a pericope. Let's move forward until we get a verse
		for (int i = position + 1; i < itemPointer_.length; i++) {
			id = itemPointer_[i];
			
			if (id >= 0) {
				return id + 1;
			}
		}

		Log.w(TAG, "pericope title at the last position? does not make sense.");
		return 0;
	}

	void callAttentionForVerse(final int verse_1) {
		final int pos = getPositionIgnoringPericopeFromVerse(verse_1);
		if (pos != -1) {
			TIntSet ap = attentionPositions_;
			if (ap == null) {
				attentionPositions_ = ap = new TIntHashSet();
			}
			ap.add(pos);
			attentionStart_ = System.currentTimeMillis();

			notifyDataSetChanged();
		}
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

	@Nullable public String getVerseText(int verse_1) {
		if (verses_ == null) return null;
		if (verse_1 < 1 || verse_1 > verses_.getVerseCount()) return null;
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
		final int[] _itemPointer = this.itemPointer_;

		// guard against wild ListView.onInitializeAccessibilityNodeInfoForItem
		if (_itemPointer == null) return false;
		if (position >= _itemPointer.length) return false;

		return _itemPointer[position] >= 0;
	}
	
	private static int[] makeItemPointer(int nverse, int[] pericopeAris, PericopeBlock[] pericopeBlocks, int nblock) {
		int[] res = new int[nverse + nblock];

		int pos_block = 0;
		int pos_verse = 0;
		int pos_itemPointer = 0;

		while (true) {
			// check if we still have pericopes remaining
			if (pos_block < nblock) {
				// still possible
				if (Ari.toVerse(pericopeAris[pos_block]) - 1 == pos_verse) {
					// We have a pericope.
					res[pos_itemPointer++] = -pos_block - 1;
					pos_block++;
					continue;
				}
			}

			// check if there is no verses remaining
			if (pos_verse >= nverse) {
				break;
			}

			// there is no more pericopes, OR not the time yet for pericopes. So we insert a verse.
			res[pos_itemPointer++] = pos_verse;
			pos_verse++;
		}

		if (res.length != pos_itemPointer) {
			throw new RuntimeException("Algorithm to insert pericopes error!! pos_itemPointer=" + pos_itemPointer + " pos_verse=" + pos_verse + " pos_block=" + pos_block + " nverse=" + nverse + " nblock=" + nblock + " pericopeAris:" + Arrays.toString(pericopeAris) + " pericopeBlocks:" + Arrays.toString(pericopeBlocks));
		}

		return res;
	}

	/* non-public */ void calculateTextSizeMult() {
		textSizeMult_ = versionId_ == null ? 1.f : S.getDb().getPerVersionSettings(versionId_).fontSizeMultiplier;
	}
}