package yuku.alkitab.base.widget;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.view.KeyEvent;
import android.view.View;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.U;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.PericopeBlock;
import yuku.alkitab.model.SingleChapterVerses;
import yuku.alkitab.model.Version;
import yuku.alkitab.util.IntArrayList;

import java.util.concurrent.atomic.AtomicInteger;

public class VersesView extends RecyclerView {
	public static final String TAG = VersesView.class.getSimpleName();

	public interface AttributeListener {
		void onBookmarkAttributeClick(Version version, String versionId, int ari);

		void onNoteAttributeClick(Version version, String versionId, int ari);

		void onProgressMarkAttributeClick(Version version, String versionId, int preset_id);

		void onHasMapsAttributeClick(Version version, String versionId, int ari);
	}

	public interface OnVerseScrollListener {
		void onVerseScroll(VersesView v, boolean isPericope, int verse_1, float prop);

		void onScrollToTop(VersesView v);
	}

	public enum PressKind {
		left,
		right,
		consumed,
		nop,
	}

	public static class PressResult {
		public final PressKind kind;
		public final int targetVerse_1;

		public static PressResult LEFT = new PressResult(PressKind.left);
		public static PressResult RIGHT = new PressResult(PressKind.right);
		public static PressResult NOP = new PressResult(PressKind.nop);

		private PressResult(final PressKind kind) {
			this.kind = kind;
			this.targetVerse_1 = 0;
		}

		public PressResult(final PressKind kind, final int targetVerse_1) {
			this.kind = kind;
			this.targetVerse_1 = targetVerse_1;
		}
	}

	SingleViewVerseAdapter adapter;
	LinearLayoutManager manager;
	OnVerseScrollListener onVerseScrollListener;
	/**
	 * Used as a cache, storing views to be fed to convertView parameter
	 * when measuring items manually at {@link #getMeasuredItemHeight(int)}.
	 */
	private SparseArray<View> scrollToVerseConvertViews;
	private String name;
	private boolean firstTimeScroll = true;
	/**
	 * Updated every time {@link #setData(int, SingleChapterVerses, int[], PericopeBlock[], int, Version, String)}
	 * or {@link #setDataEmpty()} is called. Used to track data changes, so delayed scroll, etc can be prevented from happening if the data has changed.
	 */
	private AtomicInteger dataVersionNumber = new AtomicInteger();

	public VersesView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	/**
	 * Set the name of this VersesView for debugging.
	 */
	public void setName(final String name) {
		this.name = name;
	}

	private void init() {
		if (isInEditMode()) return;

		setAdapter(adapter = new SingleViewVerseAdapter(getContext()));
		setLayoutManager(manager = new LinearLayoutManager(getContext()));
		adapter.setVerseSelectionMode(SingleViewVerseAdapter.VerseSelectionMode.multiple);

		addOnScrollListener(new OnScrollListener() {
			public int scrollState;

			@Override
			public void onScrollStateChanged(final RecyclerView recyclerView, final int newState) {
				this.scrollState = newState;
			}

			@Override
			public void onScrolled(final RecyclerView view, final int dx, final int dy) {
				if (onVerseScrollListener == null) return;

				if (view.getChildCount() == 0) return;

				float prop = 0.f;
				int position = -1;

				final View firstChild = view.getChildAt(0);
				final int firstVisibleItem = manager.findFirstVisibleItemPosition();
				final int remaining = firstChild.getBottom(); // padding top is ignored
				if (remaining >= 0) { // bottom of first child is lower than top padding
					position = firstVisibleItem;
					prop = 1.f - (float) remaining / firstChild.getHeight();
				} else { // we should have a second child
					if (view.getChildCount() > 1) {
						final View secondChild = view.getChildAt(1);
						position = firstVisibleItem + 1;
						prop = (float) -remaining / secondChild.getHeight();
					}
				}

				final int verse_1 = adapter.getVerseOrPericopeFromPosition(position);

				if (scrollState != RecyclerView.SCROLL_STATE_IDLE) {
					if (verse_1 > 0) {
						onVerseScrollListener.onVerseScroll(VersesView.this, false, verse_1, prop);
					} else {
						onVerseScrollListener.onVerseScroll(VersesView.this, true, 0, 0);
					}

					if (position == 0 && firstChild.getTop() == getPaddingTop()) {
						// we are really at the top
						onVerseScrollListener.onScrollToTop(VersesView.this);
					}
				}
			}
		});
	}

	@Override
	public SingleViewVerseAdapter getAdapter() {
		return (SingleViewVerseAdapter) super.getAdapter();
	}

	public void setParallelListener(CallbackSpan.OnClickListener<Object> parallelListener) {
		adapter.setParallelListener(parallelListener);
	}

	public AttributeListener getAttributeListener() {
		return adapter.getAttributeListener();
	}

	public void setAttributeListener(final AttributeListener attributeListener) {
		adapter.setAttributeListener(attributeListener);
	}

	public void setInlineLinkSpanFactory(final VerseInlineLinkSpan.Factory inlineLinkSpanFactory) {
		adapter.setInlineLinkSpanFactory(inlineLinkSpanFactory);
	}

	public void setDictionaryListener(CallbackSpan.OnClickListener<SingleViewVerseAdapter.DictionaryLinkInfo> listener) {
		adapter.setDictionaryListener(listener);
	}

	public void setOnVerseScrollListener(OnVerseScrollListener onVerseScrollListener) {
		this.onVerseScrollListener = onVerseScrollListener;
	}

	public void reloadAttributeMap() {
		adapter.reloadAttributeMap();
	}

	@Nullable
	public String getVerseText(int verse_1) {
		return adapter.getVerseText(verse_1);
	}

	/**
	 * @return 1-based verse
	 */
	public int getVerseBasedOnScroll() {
		return adapter.getVerseFromPosition(getPositionBasedOnScroll());
	}

	public int getPositionBasedOnScroll() {
		int pos = manager.findFirstVisibleItemPosition();

		// check if the top one has been scrolled
		View child = getChildAt(0);
		if (child != null) {
			int top = child.getTop();
			if (top == 0) {
				return pos;
			}
			int bottom = child.getBottom();
			if (bottom > 0) {
				return pos;
			} else {
				return pos + 1;
			}
		}

		return pos;
	}

	/**
	 * @param version   can be null if no text size multiplier is to be used
	 * @param versionId can be null if no text size multiplier is to be used
	 */
	public void setData(int ariBc, SingleChapterVerses verses, int[] pericopeAris, PericopeBlock[] pericopeBlocks, int nblock, @Nullable Version version, @Nullable String versionId) {
		dataVersionNumber.incrementAndGet();
		adapter.setData(ariBc, verses, pericopeAris, pericopeBlocks, nblock, version, versionId);
		stopFling();
	}

	// TODO @Override
//	public void invalidateViews() {
//		adapter.calculateTextSizeMult();
//		super.invalidateViews();
//	}

	@Override
	public Parcelable onSaveInstanceState() {
		Bundle b = new Bundle();
		Parcelable superState = super.onSaveInstanceState();
		b.putParcelable("superState", superState);
		b.putInt("verseSelectionMode", adapter.verseSelectionMode.ordinal());
		return b;
	}

	@Override
	public void onRestoreInstanceState(Parcelable state) {
		if (state instanceof Bundle) {
			Bundle b = (Bundle) state;
			super.onRestoreInstanceState(b.getParcelable("superState"));
			adapter.setVerseSelectionMode(SingleViewVerseAdapter.VerseSelectionMode.values()[b.getInt("verseSelectionMode")]);
		}

		adapter.hideOrShowContextMenuButton();
	}

	public PressResult press(int keyCode) {
		String volumeButtonsForNavigation = Preferences.getString(R.string.pref_volumeButtonNavigation_key, R.string.pref_volumeButtonNavigation_default);
		if (U.equals(volumeButtonsForNavigation, "pasal" /* chapter */)) {
			if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
				return PressResult.LEFT;
			}
			if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
				return PressResult.RIGHT;
			}
		} else if (U.equals(volumeButtonsForNavigation, "ayat" /* verse */)) {
			if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) keyCode = KeyEvent.KEYCODE_DPAD_DOWN;
			if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) keyCode = KeyEvent.KEYCODE_DPAD_UP;
		} else if (U.equals(volumeButtonsForNavigation, "page")) {
			if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
				final int oldPos = manager.findFirstVisibleItemPosition();
				int newPos = manager.findLastVisibleItemPosition();

				if (oldPos == newPos && oldPos < adapter.getItemCount() - 1) { // in case of very long item
					newPos = oldPos + 1;
				}

				// negate padding offset, unless this is the first item
				final int paddingNegator = newPos == 0 ? 0 : -this.getPaddingTop();
				manager.scrollToPositionWithOffset(newPos, paddingNegator);

				return new PressResult(PressKind.consumed, adapter.getVerseFromPosition(newPos));
			}
			if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
				final int oldPos = manager.findFirstVisibleItemPosition();
				final int targetHeight = Math.max(0, getHeight() - getPaddingTop() - getPaddingBottom());

				int totalHeight = 0;

				// consider how long the first child has been scrolled up
				final View firstChild = getChildAt(0);
				if (firstChild != null) {
					totalHeight += -firstChild.getTop();
				}

				int curPos = oldPos;
				// try until totalHeight exceeds targetHeight
				while (true) {
					curPos--;
					if (curPos < 0) {
						break;
					}

					// TODO totalHeight += getMeasuredItemHeight(curPos);

					if (totalHeight > targetHeight) {
						break;
					}
				}

				int newPos = curPos + 1;

				if (oldPos == newPos && oldPos > 0) { // move at least one
					newPos = oldPos - 1;
				}

				// negate padding offset, unless this is the first item
				final int paddingNegator = newPos == 0 ? 0 : -this.getPaddingTop();
				manager.scrollToPositionWithOffset(newPos, paddingNegator);

				return new PressResult(PressKind.consumed, adapter.getVerseFromPosition(newPos));
			}
		}

		if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
			final int oldVerse_1 = getVerseBasedOnScroll();
			final int newVerse_1;

			stopFling();
			if (oldVerse_1 < adapter.getVerseCount()) {
				newVerse_1 = oldVerse_1 + 1;
			} else {
				newVerse_1 = oldVerse_1;
			}
			scrollToVerse(newVerse_1);
			return new PressResult(PressKind.consumed, newVerse_1);
		} else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
			final int oldVerse_1 = getVerseBasedOnScroll();
			final int newVerse_1;

			stopFling();
			if (oldVerse_1 > 1) { // can still go prev
				newVerse_1 = oldVerse_1 - 1;
			} else {
				newVerse_1 = oldVerse_1;
			}
			scrollToVerse(newVerse_1);
			return new PressResult(PressKind.consumed, newVerse_1);
		}

		return PressResult.NOP;
	}

	public void setDataWithRetainSelectedVerses(boolean retainSelectedVerses, int ariBc, int[] pericope_aris, PericopeBlock[] pericope_blocks, int nblock, SingleChapterVerses verses, @NonNull Version version, @NonNull String versionId) {
		IntArrayList selectedVerses_1 = null;
		if (retainSelectedVerses) {
			selectedVerses_1 = adapter.getSelectedVerses_1();
		}

		//# fill adapter with new data. make sure all checked states are reset
		adapter.uncheckAllVerses(true);
		setData(ariBc, verses, pericope_aris, pericope_blocks, nblock, version, versionId);
		reloadAttributeMap();

		boolean anySelected = false;
		if (selectedVerses_1 != null) {
			for (int i = 0, len = selectedVerses_1.size(); i < len; i++) {
				int pos = adapter.getPositionIgnoringPericopeFromVerse(selectedVerses_1.get(i));
				if (pos != -1) {
					adapter.setItemChecked(pos, true);
					anySelected = true;
				}
			}
		}

		if (anySelected) {
			if (adapter.listener != null) adapter.listener.onSomeVersesSelected(adapter);
		}
	}

	public void callAttentionForVerse(final int verse_1) {
		adapter.callAttentionForVerse(verse_1);
	}

	/**
	 * This is different from {@link #scrollToVerse(int, float)} in that if the requested
	 * verse has a pericope header, this will scroll to the top of the pericope header,
	 * not to the top of the verse.
	 */
	public void scrollToVerse(final int verse_1) {
		final int position = adapter.getPositionOfPericopeBeginningFromVerse(verse_1);

		if (position == -1) {
			AppLog.w(TAG, "could not find verse_1=" + verse_1 + ", weird!");
		} else {
			final int delay = firstTimeScroll ? 34 : 0;
			final int vn = dataVersionNumber.get();

			postDelayed(() -> {
				// this may happen async from above, so check data version first
				if (vn != dataVersionNumber.get()) return;

				// negate padding offset, unless this is the first verse
				final int paddingNegator = position == 0 ? 0 : -this.getPaddingTop();

				stopFling();
				manager.scrollToPositionWithOffset(position, paddingNegator);

				firstTimeScroll = false;
			}, delay);
		}
	}

	/**
	 * This is different from {@link #scrollToVerse(int)} in that if the requested
	 * verse has a pericope header, this will scroll to the verse, ignoring the pericope header.
	 */
	public void scrollToVerse(int verse_1, final float prop) {
		final int position = adapter.getPositionIgnoringPericopeFromVerse(verse_1);

		if (position == -1) {
			AppLog.d(TAG, "could not find verse_1: " + verse_1);
			return;
		}

		post(() -> {
			// this may happen async from above, so check first if pos is still valid
			if (position >= adapter.getItemCount()) return;

			// negate padding offset, unless this is the first verse
			final int paddingNegator = position == 0 ? 0 : -this.getPaddingTop();

			final int firstPos = manager.findFirstVisibleItemPosition();
			final int lastPos = manager.findLastVisibleItemPosition();
			if (position >= firstPos && position <= lastPos) {
				// we have the child on screen, no need to measure
				View child = getChildAt(position - firstPos);
				stopFling();
				manager.scrollToPositionWithOffset(position, -(int) (prop * child.getHeight()) + paddingNegator);
				return;
			}

			// TODO final int measuredHeight = getMeasuredItemHeight(position);
//			stopFling();
//			manager.scrollToPositionWithOffset(position, -(int) (prop * measuredHeight) + paddingNegator);
		});
	}

	// TODO private int getMeasuredItemHeight(final int position) {
//		// child needed is not on screen, we need to measure
//		if (scrollToVerseConvertViews == null) {
//			// initialize scrollToVerseConvertViews if needed
//			scrollToVerseConvertViews = new SparseArray<>(2);
//		}
//		final int itemType = adapter.getItemViewType(position);
//		final View convertView = scrollToVerseConvertViews.get(itemType);
//		final View child = adapter.getView(position, convertView, this);
//		child.measure(MeasureSpec.makeMeasureSpec(this.getWidth() - this.getPaddingLeft() - this.getPaddingRight(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
//		scrollToVerseConvertViews.put(itemType, child);
//		return child.getMeasuredHeight();
//	}

	public void scrollToTop() {
		post(() -> manager.scrollToPositionWithOffset(0, 0));
	}

	public void setDataEmpty() {
		dataVersionNumber.incrementAndGet();
		adapter.setDataEmpty();
	}

	public void stopFling() {
		stopScroll();
	}

	@Override
	public String toString() {
		return name != null ? ("VersesView{name=" + name + "}") : "VersesView";
	}

	public void setDictionaryModeAris(@Nullable final SparseBooleanArray aris) {
		adapter.setDictionaryModeAris(aris);
	}
}
