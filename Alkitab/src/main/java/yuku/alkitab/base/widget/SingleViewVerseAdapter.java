package yuku.alkitab.base.widget;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.text.SpannableStringBuilder;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.base.util.Appearances;
import yuku.alkitab.base.util.Highlights;
import yuku.alkitab.base.util.TargetDecoder;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.PericopeBlock;
import yuku.alkitab.model.ProgressMark;
import yuku.alkitab.model.SingleChapterVerses;
import yuku.alkitab.model.Version;
import yuku.alkitab.util.Ari;
import yuku.alkitab.util.IntArrayList;

import java.util.Arrays;

import static android.widget.AbsListView.CHOICE_MODE_MULTIPLE;
import static android.widget.AbsListView.CHOICE_MODE_NONE;
import static android.widget.AbsListView.CHOICE_MODE_SINGLE;


public class SingleViewVerseAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
	static final String TAG = SingleViewVerseAdapter.class.getSimpleName();

	public static final int TYPE_VERSE = 0;
	public static final int TYPE_PERICOPE_HEADER = 1;

	final float density_;
	// # field ctor
	CallbackSpan.OnClickListener<Object> parallelListener_;
	VersesView.AttributeListener attributeListener_;
	VerseInlineLinkSpan.Factory inlineLinkSpanFactory_;
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
	 * <p>
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

	private SparseBooleanArray dictionaryModeAris_;

	// Some ListView functionality copied here.
	/**
	 * Controls if/how the user may choose/check items in the list
	 */
	int mChoiceMode = CHOICE_MODE_NONE;

	/**
	 * Running count of how many items are currently checked
	 */
	int mCheckedItemCount;

	/**
	 * Running state of which positions are currently checked
	 */
	SparseBooleanArray mCheckStates;

	public void setChoiceMode(final int choiceMode) {
		mChoiceMode = choiceMode;
		if (mChoiceMode != CHOICE_MODE_NONE) {
			if (mCheckStates == null) {
				mCheckStates = new SparseBooleanArray(0);
			}
		}
	}

	public SparseBooleanArray getCheckedItemPositions() {
		if (mChoiceMode != CHOICE_MODE_NONE) {
			return mCheckStates;
		}
		return null;
	}

	public void setItemChecked(int position, boolean value) {
		if (mChoiceMode == CHOICE_MODE_NONE) {
			return;
		}

		final boolean itemCheckChanged;
		if (mChoiceMode == CHOICE_MODE_MULTIPLE) {
			boolean oldValue = mCheckStates.get(position);
			mCheckStates.put(position, value);
			itemCheckChanged = oldValue != value;
			if (itemCheckChanged) {
				if (value) {
					mCheckedItemCount++;
				} else {
					mCheckedItemCount--;
				}
			}
		} else {
			// Clear all values if we're checking something, or unchecking the currently
			// selected item
			if (value || isItemChecked(position)) {
				mCheckStates.clear();
			}
			// this may end up selecting the value we just cleared but this way
			// we ensure length of mCheckStates is 1, a fact getCheckedItemPosition relies on
			if (value) {
				mCheckStates.put(position, true);
				mCheckedItemCount = 1;
			} else if (mCheckStates.size() == 0 || !mCheckStates.valueAt(0)) {
				mCheckedItemCount = 0;
			}
		}
	}

	/**
	 * Returns the checked state of the specified position. The result is only
	 * valid if the choice mode has been set to {@link android.widget.AbsListView#CHOICE_MODE_SINGLE}
	 * or {@link android.widget.AbsListView#CHOICE_MODE_MULTIPLE}.
	 *
	 * @param position The item whose checked state to return
	 * @return The item's checked state or <code>false</code> if choice mode
	 *         is invalid
	 *
	 * @see #setChoiceMode(int)
	 */
	public boolean isItemChecked(int position) {
		if (mChoiceMode != CHOICE_MODE_NONE && mCheckStates != null) {
			return mCheckStates.get(position);
		}

		return false;
	}

	public int getCheckedItemCount() {
		return mCheckedItemCount;
	}

	public enum VerseSelectionMode {
		none,
		multiple,
		singleClick,
	}

	VerseSelectionMode verseSelectionMode;

	public void setSelectedVersesListener(SelectedVersesListener listener) {
		this.listener = listener;
	}

	public interface SelectedVersesListener {
		void onSomeVersesSelected(final SingleViewVerseAdapter adapter);

		void onNoVersesSelected(final SingleViewVerseAdapter adapter);

		void onVerseSingleClick(final SingleViewVerseAdapter adapter, int verse_1);
	}

	public static abstract class DefaultSelectedVersesListener implements SelectedVersesListener {
		@Override
		public void onSomeVersesSelected(final SingleViewVerseAdapter adapter) {
		}

		@Override
		public void onNoVersesSelected(final SingleViewVerseAdapter adapter) {
		}

		@Override
		public void onVerseSingleClick(final SingleViewVerseAdapter adapter, final int verse_1) {
		}
	}

	SelectedVersesListener listener;

	public void setVerseSelectionMode(VerseSelectionMode mode) {
		this.verseSelectionMode = mode;

		if (mode == VerseSelectionMode.singleClick) {
			// TODO setSelector(originalSelector);
			uncheckAllVerses(false);
			setChoiceMode(ListView.CHOICE_MODE_NONE);
		} else if (mode == VerseSelectionMode.multiple) {
			// TODO setSelector(new ColorDrawable(0x0));
			setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
		} else if (mode == VerseSelectionMode.none) {
			// TODO setSelector(new ColorDrawable(0x0));
			setChoiceMode(ListView.CHOICE_MODE_NONE);
		}
	}

	public void uncheckAllVerses(boolean callListener) {
		final SparseBooleanArray checkedPositions = getCheckedItemPositions();
		if (checkedPositions != null && checkedPositions.size() > 0) {
			for (int i = checkedPositions.size() - 1; i >= 0; i--) {
				if (checkedPositions.valueAt(i)) {
					setItemChecked(checkedPositions.keyAt(i), false);
				}
			}
		}

		if (callListener) {
			if (listener != null) listener.onNoVersesSelected(this);
		}

		notifyDataSetChanged();
	}

	public void checkVerses(IntArrayList verses_1, boolean callListener) {
		uncheckAllVerses(false);

		int checked_count = 0;
		for (int i = 0, len = verses_1.size(); i < len; i++) {
			int verse_1 = verses_1.get(i);
			int count = getItemCount();
			int pos = getPositionIgnoringPericopeFromVerse(verse_1);
			if (pos != -1 && pos < count) {
				setItemChecked(pos, true);
				checked_count++;
			}
		}

		if (callListener) {
			if (checked_count > 0) {
				if (listener != null) listener.onSomeVersesSelected(this);
			} else {
				if (listener != null) listener.onNoVersesSelected(this);
			}
		}
	}

	void hideOrShowContextMenuButton() {
		if (verseSelectionMode != VerseSelectionMode.multiple) return;

		if (getCheckedItemCount() > 0) {
			if (listener != null) listener.onSomeVersesSelected(this);
		} else {
			if (listener != null) listener.onNoVersesSelected(this);
		}
	}

	public IntArrayList getSelectedVerses_1() {
		// count how many are selected
		final SparseBooleanArray positions = getCheckedItemPositions();
		if (positions == null) {
			return new IntArrayList(0);
		}

		IntArrayList res = new IntArrayList(positions.size());
		for (int i = 0, len = positions.size(); i < len; i++) {
			if (positions.valueAt(i)) {
				int position = positions.keyAt(i);
				int verse_1 = getVerseFromPosition(position);
				if (verse_1 >= 1) res.add(verse_1);
			}
		}
		return res;
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

	/* non-public */
	synchronized void setData(int ariBc, SingleChapterVerses verses, int[] pericopeAris, PericopeBlock[] pericopeBlocks, int nblock, @Nullable final Version version, @Nullable final String versionId) {
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

	/* non-public */
	synchronized void setDataEmpty() {
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
		for (final ProgressMark progressMark : S.getDb().listAllProgressMarks()) {
			final int ari = progressMark.ari;
			if (ari < ariMin || ari >= ariMax) {
				continue;
			}

			if (progressMarkBitsMap == null) {
				progressMarkBitsMap = new int[verseCount];
			}

			int mapOffset = Ari.toVerse(ari) - 1;
			if (mapOffset >= progressMarkBitsMap.length) {
				AppLog.e(TAG, "(for progressMarkBitsMap:) mapOffset out of bounds: " + mapOffset + " happened on ari 0x" + Integer.toHexString(ari));
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
									AppLog.e(TAG, "(for hasMapsMap:) mapOffset out of bounds: " + mapOffset + " happened on ari 0x" + Integer.toHexString(ari));
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

	@Override
	public int getItemCount() {
		if (verses_ == null) return 0;

		return itemPointer_.length;
	}

	// TODO @Override
//	public synchronized String getItem(int position) {
//		int id = itemPointer_[position];
//
//		if (id >= 0) {
//			return verses_.getVerse(position);
//		} else {
//			return pericopeBlocks_[-id - 1].toString();
//		}
//	}

	@Override
	public synchronized long getItemId(int position) {
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

		AppLog.w(TAG, "pericope title at the last position? does not make sense.");
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

	@Nullable
	public String getVerseText(int verse_1) {
		if (verses_ == null) return null;
		if (verse_1 < 1 || verse_1 > verses_.getVerseCount()) return null;
		return verses_.getVerse(verse_1 - 1);
	}

	public int getVerseCount() {
		if (verses_ == null) return 0;
		return verses_.getVerseCount();
	}

	// TODO @Override
//	public boolean isEnabled(int position) {
//		final int[] _itemPointer = this.itemPointer_;
//
//		// guard against wild ListView.onInitializeAccessibilityNodeInfoForItem
//		if (_itemPointer == null) return false;
//		if (position >= _itemPointer.length) return false;
//
//		return _itemPointer[position] >= 0;
//	}

	/* non-public */ void calculateTextSizeMult() {
		textSizeMult_ = versionId_ == null ? 1.f : S.getDb().getPerVersionSettings(versionId_).fontSizeMultiplier;
	}

	public static class DictionaryLinkInfo {
		public String orig_text;
		public String key;

		public DictionaryLinkInfo(final String orig_text, final String key) {
			this.orig_text = orig_text;
			this.key = key;
		}
	}

	CallbackSpan.OnClickListener<DictionaryLinkInfo> dictionaryListener_;

	public SingleViewVerseAdapter(Context context) {
		this.density_ = context.getResources().getDisplayMetrics().density;
		this.inflater_ = LayoutInflater.from(context);
	}

	@Override
	public int getItemViewType(final int position) {
		final int id = itemPointer_[position];
		if (id >= 0) {
			return TYPE_VERSE;
		} else {
			return TYPE_PERICOPE_HEADER;
		}
	}

	static class VerseHolder extends RecyclerView.ViewHolder {
		public VerseHolder(final View itemView) {
			super(itemView);
		}

		VerseItem getItemView() {
			return (VerseItem) itemView;
		}
	}

	static class PericopeHeaderHolder extends RecyclerView.ViewHolder {
		public PericopeHeaderHolder(final View itemView) {
			super(itemView);
		}

		PericopeHeaderItem getItemView() {
			return (PericopeHeaderItem) itemView;
		}
	}

	@NonNull
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
		if (viewType == TYPE_VERSE) {
			return new VerseHolder(inflater_.inflate(R.layout.item_verse, parent, false));
		}

		if (viewType == TYPE_PERICOPE_HEADER) {
			return new PericopeHeaderHolder(inflater_.inflate(R.layout.item_pericope_header, parent, false));
		}

		throw new IllegalArgumentException("Invalid type");
	}

	@Override
	public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder _holder_, final int _position_) {
		final int id = itemPointer_[_position_];

		if (_holder_ instanceof VerseHolder) {
			final VerseHolder holder = (VerseHolder) _holder_;
			final int verse_1 = id + 1;

			final boolean checked = isItemChecked(_position_);
			final VerseItem res = holder.getItemView();

			final int ari = Ari.encodeWithBc(ari_bc_, verse_1);
			final String text = verses_.getVerse(id);
			final String verseNumberText = verses_.getVerseNumberText(id);
			final Highlights.Info highlightInfo = highlightInfoMap_ == null ? null : highlightInfoMap_[id];

			final VerseTextView lText = res.lText;
			final TextView lVerseNumber = res.lVerseNumber;

			final int startVerseTextPos = VerseRenderer.render(lText, lVerseNumber, ari, text, verseNumberText, highlightInfo, checked, inlineLinkSpanFactory_, null);

			final float textSizeMult;
			if (verses_ instanceof SingleChapterVerses.WithTextSizeMult) {
				textSizeMult = ((SingleChapterVerses.WithTextSizeMult) verses_).getTextSizeMult(id);
			} else {
				textSizeMult = textSizeMult_;
			}

			Appearances.applyTextAppearance(lText, textSizeMult);
			Appearances.applyVerseNumberAppearance(lVerseNumber, textSizeMult);

			if (checked) { // override text color with black or white!
				final int selectedTextColor = U.getTextColorForSelectedVerse(Preferences.getInt(R.string.pref_selectedVerseBgColor_key, R.integer.pref_selectedVerseBgColor_default));
				lText.setTextColor(selectedTextColor);
				lVerseNumber.setTextColor(selectedTextColor);
			}

			final AttributeView attributeView = res.attributeView;
			attributeView.setScale(scaleForAttributeView(S.applied().fontSize2dp * textSizeMult_));
			attributeView.setBookmarkCount(bookmarkCountMap_ == null ? 0 : bookmarkCountMap_[id]);
			attributeView.setNoteCount(noteCountMap_ == null ? 0 : noteCountMap_[id]);
			attributeView.setProgressMarkBits(progressMarkBitsMap_ == null ? 0 : progressMarkBitsMap_[id]);
			attributeView.setHasMaps(hasMapsMap_ != null && hasMapsMap_[id]);
			attributeView.setAttributeListener(attributeListener_, version_, versionId_, ari);

			res.setCollapsed(text.length() == 0 && !attributeView.isShowingSomething());

			res.setAri(ari);

			/*
			 * Dictionary mode is activated on either of these conditions:
			 * 1. user manually activate dictionary mode after selecting verses
			 * 2. automatic lookup is on and this verse is selected (checked)
			 */
			if ((dictionaryModeAris_ != null && dictionaryModeAris_.get(ari))
				|| (checked && Preferences.getBoolean(res.getContext().getString(R.string.pref_autoDictionaryAnalyze_key), res.getContext().getResources().getBoolean(R.bool.pref_autoDictionaryAnalyze_default)))
				) {
				final ContentResolver cr = res.getContext().getContentResolver();

				final CharSequence renderedText = lText.getText();
				final SpannableStringBuilder verseText = renderedText instanceof SpannableStringBuilder ? (SpannableStringBuilder) renderedText : new SpannableStringBuilder(renderedText);

				// we have to exclude the verse numbers from analyze text
				final String analyzeString = verseText.toString().substring(startVerseTextPos);

				final Uri uri = Uri.parse("content://org.sabda.kamus.provider/analyze").buildUpon().appendQueryParameter("text", analyzeString).build();
				Cursor c = null;
				try {
					c = cr.query(uri, null, null, null, null);
				} catch (Exception e) {
					AppLog.e(TAG, "Error when querying dictionary content provider", e);
				}

				if (c != null) {
					try {
						final int col_offset = c.getColumnIndexOrThrow("offset");
						final int col_len = c.getColumnIndexOrThrow("len");
						final int col_key = c.getColumnIndexOrThrow("key");

						while (c.moveToNext()) {
							final int offset = c.getInt(col_offset);
							final int len = c.getInt(col_len);
							final String key = c.getString(col_key);

							verseText.setSpan(new CallbackSpan<>(new DictionaryLinkInfo(analyzeString.substring(offset, offset + len), key), dictionaryListener_), startVerseTextPos + offset, startVerseTextPos + offset + len, 0);
						}
					} finally {
						c.close();
					}

					lText.setText(verseText);
				}
			}

			// Do we need to call attention?
			if (attentionStart_ != 0 && attentionPositions_ != null && attentionPositions_.contains(_position_)) {
				res.callAttention(attentionStart_);
			} else {
				res.callAttention(0);
			}

			res.setOnClickListener(v -> {
				final int position = holder.getAdapterPosition();
				if (position == -1) return;
				
				if (mChoiceMode != CHOICE_MODE_NONE) {
					boolean checkedStateChanged = false;

					if (mChoiceMode == CHOICE_MODE_MULTIPLE) {
						boolean checked2 = !mCheckStates.get(position, false);
						mCheckStates.put(position, checked2);
						if (checked2) {
							mCheckedItemCount++;
						} else {
							mCheckedItemCount--;
						}
						checkedStateChanged = true;
					} else if (mChoiceMode == CHOICE_MODE_SINGLE) {
						boolean checked2 = !mCheckStates.get(position, false);
						if (checked2) {
							mCheckStates.clear();
							mCheckStates.put(position, true);
							mCheckedItemCount = 1;
						} else if (mCheckStates.size() == 0 || !mCheckStates.valueAt(0)) {
							mCheckedItemCount = 0;
						}
						checkedStateChanged = true;
					}

					if (checkedStateChanged) {
						notifyItemChanged(position);
					}
				}

				if (verseSelectionMode == VerseSelectionMode.singleClick) {
					if (listener != null) {
						listener.onVerseSingleClick(this, getVerseFromPosition(position));
					}

				} else if (verseSelectionMode == VerseSelectionMode.multiple) {
					notifyDataSetChanged();
					hideOrShowContextMenuButton();
				}
			});

		} else if (_holder_ instanceof PericopeHeaderHolder) {
			final PericopeHeaderHolder holder = (PericopeHeaderHolder) _holder_;

			final PericopeHeaderItem res = holder.getItemView();

			PericopeBlock pericopeBlock = pericopeBlocks_[-id - 1];

			TextView lCaption = res.findViewById(R.id.lCaption);
			TextView lParallels = res.findViewById(R.id.lParallels);

			lCaption.setText(FormattedTextRenderer.render(pericopeBlock.title));

			int paddingTop;
			// turn off top padding if the position == 0 OR before this is also a pericope title
			if (_position_ == 0 || itemPointer_[_position_ - 1] < 0) {
				paddingTop = 0;
			} else {
				paddingTop = S.applied().pericopeSpacingTop;
			}

			res.setPadding(0, paddingTop, 0, S.applied().pericopeSpacingBottom);

			Appearances.applyPericopeTitleAppearance(lCaption, textSizeMult_);

			// make parallel gone if not exist
			if (pericopeBlock.parallels.length == 0) {
				lParallels.setVisibility(View.GONE);
			} else {
				lParallels.setVisibility(View.VISIBLE);

				SpannableStringBuilder sb = new SpannableStringBuilder("(");

				int total = pericopeBlock.parallels.length;
				for (int i = 0; i < total; i++) {
					String parallel = pericopeBlock.parallels[i];

					if (i > 0) {
						// force new line for certain parallel patterns
						if ((total == 6 && i == 3) || (total == 4 && i == 2) || (total == 5 && i == 3)) {
							sb.append("; \n");
						} else {
							sb.append("; ");
						}
					}

					appendParallel(sb, parallel);
				}
				sb.append(')');

				lParallels.setText(sb, TextView.BufferType.SPANNABLE);
				Appearances.applyPericopeParallelTextAppearance(lParallels, textSizeMult_);
			}
		}
	}

	static float scaleForAttributeView(final float fontSizeDp) {
		if (fontSizeDp >= 13 /* 72% */ && fontSizeDp < 24 /* 133% */) {
			return 1.f;
		}

		if (fontSizeDp < 8) return 0.5f; // 0 ~ 44%
		if (fontSizeDp < 18) return 0.75f; // 44% ~ 72%
		if (fontSizeDp >= 36) return 2.f; // 200% ~
		return 1.5f; // 24 to 36 // 133% ~ 200%
	}

	private void appendParallel(SpannableStringBuilder sb, String parallel) {
		int sb_len = sb.length();

		linked:
		{
			if (parallel.startsWith("@")) {
				// look for the end
				int targetEndPos = parallel.indexOf(' ', 1);
				if (targetEndPos == -1) {
					break linked;
				}

				final String target = parallel.substring(1, targetEndPos);
				final IntArrayList ariRanges = TargetDecoder.decode(target);
				if (ariRanges == null || ariRanges.size() == 0) {
					break linked;
				}

				final String display = parallel.substring(targetEndPos + 1);

				// if we reach this, data and display should have values, and we must not go to fallback below
				sb.append(display);
				sb.setSpan(new CallbackSpan<>(ariRanges.get(0), parallelListener_), sb_len, sb.length(), 0);
				return; // do not remove this
			}
		}

		// fallback if the above code fails
		sb.append(parallel);
		sb.setSpan(new CallbackSpan<>(parallel, parallelListener_), sb_len, sb.length(), 0);
	}

	public void setDictionaryModeAris(final SparseBooleanArray aris) {
		this.dictionaryModeAris_ = aris;
		notifyDataSetChanged();
	}

	public void setDictionaryListener(final CallbackSpan.OnClickListener<DictionaryLinkInfo> listener) {
		this.dictionaryListener_ = listener;
		notifyDataSetChanged();
	}
}
