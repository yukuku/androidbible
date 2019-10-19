package yuku.alkitab.base.widget;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.SparseBooleanArray;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.U;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.PericopeBlock;
import yuku.alkitab.model.SingleChapterVerses;
import yuku.alkitab.model.Version;
import yuku.alkitab.util.IntArrayList;

public class OldVersesView extends ListView implements AbsListView.OnScrollListener {
	public static final String TAG = OldVersesView.class.getSimpleName();

	// http://stackoverflow.com/questions/6369491/stop-listview-scroll-animation
	static class StopListFling {

		private static Field mFlingEndField = null;
		private static Method mFlingEndMethod = null;

		static {
			try {
				mFlingEndField = AbsListView.class.getDeclaredField("mFlingRunnable");
				mFlingEndField.setAccessible(true);
				mFlingEndMethod = mFlingEndField.getType().getDeclaredMethod("endFling");
				mFlingEndMethod.setAccessible(true);
			} catch (Exception e) {
				mFlingEndMethod = null;
			}
		}

		public static void stop(ListView list) {
			if (mFlingEndMethod != null) {
				try {
					mFlingEndMethod.invoke(mFlingEndField.get(list));
				} catch (Exception ignored) {
				}
			}
		}
	}

	public enum VerseSelectionMode {
		none,
		multiple,
		singleClick,
	}

	public interface SelectedVersesListener {
		void onSomeVersesSelected(OldVersesView v);

		void onNoVersesSelected(OldVersesView v);

		void onVerseSingleClick(OldVersesView v, int verse_1);
	}

	public static abstract class DefaultSelectedVersesListener implements SelectedVersesListener {
		@Override
		public void onSomeVersesSelected(final OldVersesView v) {
		}

		@Override
		public void onNoVersesSelected(final OldVersesView v) {
		}

		@Override
		public void onVerseSingleClick(final OldVersesView v, final int verse_1) {
		}
	}

	public interface AttributeListener {
		void onBookmarkAttributeClick(Version version, String versionId, int ari);

		void onNoteAttributeClick(Version version, String versionId, int ari);

		void onProgressMarkAttributeClick(Version version, String versionId, int preset_id);

		void onHasMapsAttributeClick(Version version, String versionId, int ari);
	}

	public interface OnVerseScrollListener {
		void onVerseScroll(OldVersesView v, boolean isPericope, int verse_1, float prop);

		void onScrollToTop(OldVersesView v);
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

	public AttributeListener getAttributeListener() {
		return adapter.getAttributeListener();
	}

	public void setAttributeListener(final AttributeListener attributeListener) {
		adapter.setAttributeListener(attributeListener);
	}

	private String name;

	/**
	 * Set the name of this VersesView for debugging.
	 */
	public void setName(final String name) {
		this.name = name;
	}

	VerseSelectionMode verseSelectionMode;

	public void setVerseSelectionMode(VerseSelectionMode mode) {
		this.verseSelectionMode = mode;

		if (mode == VerseSelectionMode.singleClick) {
			setSelector(originalSelector);
			uncheckAllVerses(false);
			setChoiceMode(ListView.CHOICE_MODE_NONE);
		} else if (mode == VerseSelectionMode.multiple) {
			setSelector(new ColorDrawable(0x0));
			setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
		} else if (mode == VerseSelectionMode.none) {
			setSelector(new ColorDrawable(0x0));
			setChoiceMode(ListView.CHOICE_MODE_NONE);
		}
	}

	SelectedVersesListener listener;

	public void setSelectedVersesListener(SelectedVersesListener listener) {
		this.listener = listener;
	}

	// no longer used in new verseview
	private Drawable originalSelector;

	private OnVerseScrollListener onVerseScrollListener;

	public void setOnVerseScrollListener(OnVerseScrollListener onVerseScrollListener) {
		this.onVerseScrollListener = onVerseScrollListener;
	}

	// no longer used in new verseview
	private AbsListView.OnScrollListener userOnScrollListener;
	// no longer used in new verseview
	private boolean firstTimeScroll = true;
	// no longer used in new verseview
	private int scrollState = 0;

	/**
	 * Updated every time {@link #setData(int, SingleChapterVerses, int[], PericopeBlock[], int, Version, String)}
	 * or {@link #setDataEmpty()} is called. Used to track data changes, so delayed scroll, etc can be prevented from happening if the data has changed.
	 */
	private AtomicInteger dataVersionNumber = new AtomicInteger();

	@NotNull
	@Override
	public String toString() {
		return name != null ? ("VersesView{name=" + name + "}") : "VersesView";
	}

	SingleViewVerseAdapter adapter;

	// ############################# migrate marker

	/**
	 * Used as a cache, storing views to be fed to convertView parameter
	 * when measuring items manually at {@link #getMeasuredItemHeight(int)}.
	 */
	private View[] scrollToVerseConvertViews;

	public OldVersesView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	private void init() {
		if (isInEditMode()) return;

		originalSelector = getSelector();

		setDivider(null);
		setFocusable(false);

		setAdapter(adapter = new SingleViewVerseAdapter(getContext()));
		setOnItemClickListener(itemClick);
		setVerseSelectionMode(VerseSelectionMode.multiple);

		super.setOnScrollListener(this);
	}

	@Override
	public final void setOnScrollListener(AbsListView.OnScrollListener l) {
		userOnScrollListener = l;
	}

	public void setParallelListener(CallbackSpan.OnClickListener<Object> parallelListener) {
		adapter.setParallelListener(parallelListener);
	}

	public void setInlineLinkSpanFactory(final VerseInlineLinkSpan.Factory inlineLinkSpanFactory) {
		adapter.setInlineLinkSpanFactory(inlineLinkSpanFactory);
	}

	public void setDictionaryListener(CallbackSpan.OnClickListener<SingleViewVerseAdapter.DictionaryLinkInfo> listener) {
		adapter.setDictionaryListener(listener);
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
		int pos = getFirstVisiblePosition();

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

	@Override
	public void invalidateViews() {
		adapter.calculateTextSizeMult();
		super.invalidateViews();
	}

	private OnItemClickListener itemClick = new OnItemClickListener() {
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			if (verseSelectionMode == VerseSelectionMode.singleClick) {
				if (listener != null) listener.onVerseSingleClick(OldVersesView.this, adapter.getVerseFromPosition(position));
			} else if (verseSelectionMode == VerseSelectionMode.multiple) {
				adapter.notifyDataSetChanged();
				hideOrShowContextMenuButton();
			}
		}
	};

	public void uncheckAllVerses(boolean callListener) {
		SparseBooleanArray checkedPositions = getCheckedItemPositions();
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
	}

	public void checkVerses(IntArrayList verses_1, boolean callListener) {
		uncheckAllVerses(false);

		int checked_count = 0;
		for (int i = 0, len = verses_1.size(); i < len; i++) {
			int verse_1 = verses_1.get(i);
			int count = adapter.getCount();
			int pos = adapter.getPositionIgnoringPericopeFromVerse(verse_1);
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
		SparseBooleanArray positions = getCheckedItemPositions();
		if (positions == null) {
			return new IntArrayList(0);
		}

		IntArrayList res = new IntArrayList(positions.size());
		for (int i = 0, len = positions.size(); i < len; i++) {
			if (positions.valueAt(i)) {
				int position = positions.keyAt(i);
				int verse_1 = adapter.getVerseFromPosition(position);
				if (verse_1 >= 1) res.add(verse_1);
			}
		}
		return res;
	}

	@Override
	public Parcelable onSaveInstanceState() {
		Bundle b = new Bundle();
		Parcelable superState = super.onSaveInstanceState();
		b.putParcelable("superState", superState);
		b.putInt("verseSelectionMode", verseSelectionMode.ordinal());
		return b;
	}

	@Override
	public void onRestoreInstanceState(Parcelable state) {
		if (state instanceof Bundle) {
			Bundle b = (Bundle) state;
			super.onRestoreInstanceState(b.getParcelable("superState"));
			setVerseSelectionMode(VerseSelectionMode.values()[b.getInt("verseSelectionMode")]);
		}

		hideOrShowContextMenuButton();
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
				final int oldPos = getFirstVisiblePosition();
				int newPos = getLastVisiblePosition();

				if (oldPos == newPos && oldPos < adapter.getCount() - 1) { // in case of very long item
					newPos = oldPos + 1;
				}

				// negate padding offset, unless this is the first item
				final int paddingNegator = newPos == 0 ? 0 : -this.getPaddingTop();
				smoothScrollFixed(newPos, paddingNegator);

				return new PressResult(PressKind.consumed, adapter.getVerseFromPosition(newPos));
			}
			if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
				final int oldPos = getFirstVisiblePosition();
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

					totalHeight += getMeasuredItemHeight(curPos);

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
				smoothScrollFixed(newPos, paddingNegator);

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

	/**
	 * Fixed version of smoothScrollToPositionFromTop.
	 */
	private void smoothScrollFixed(final int newPos, final int offset) {
		final int vn = dataVersionNumber.get();
		final int smoothScrollDuration = 200;  // default value (Issue 78030)
		smoothScrollToPositionFromTop(newPos, offset, smoothScrollDuration);
		postDelayed(() -> {
			// possible that data has changed
			if (vn != dataVersionNumber.get()) return;
			setSelectionFromTop(newPos, offset);
		}, smoothScrollDuration + 17);
	}

	public void setDataWithRetainSelectedVerses(boolean retainSelectedVerses, int ariBc, int[] pericope_aris, PericopeBlock[] pericope_blocks, int nblock, SingleChapterVerses verses, @NonNull Version version, @NonNull String versionId) {
		IntArrayList selectedVerses_1 = null;
		if (retainSelectedVerses) {
			selectedVerses_1 = getSelectedVerses_1();
		}

		//# fill adapter with new data. make sure all checked states are reset
		uncheckAllVerses(true);
		setData(ariBc, verses, pericope_aris, pericope_blocks, nblock, version, versionId);
		reloadAttributeMap();

		boolean anySelected = false;
		if (selectedVerses_1 != null) {
			for (int i = 0, len = selectedVerses_1.size(); i < len; i++) {
				int pos = adapter.getPositionIgnoringPericopeFromVerse(selectedVerses_1.get(i));
				if (pos != -1) {
					setItemChecked(pos, true);
					anySelected = true;
				}
			}
		}

		if (anySelected) {
			if (listener != null) listener.onSomeVersesSelected(this);
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
				setSelectionFromTop(position, paddingNegator);

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
			if (position >= getCount()) return;

			// negate padding offset, unless this is the first verse
			final int paddingNegator = position == 0 ? 0 : -this.getPaddingTop();

			final int firstPos = getFirstVisiblePosition();
			final int lastPos = getLastVisiblePosition();
			if (position >= firstPos && position <= lastPos) {
				// we have the child on screen, no need to measure
				View child = getChildAt(position - firstPos);
				stopFling();
				setSelectionFromTop(position, -(int) (prop * child.getHeight()) + paddingNegator);
				return;
			}

			final int measuredHeight = getMeasuredItemHeight(position);

			stopFling();
			setSelectionFromTop(position, -(int) (prop * measuredHeight) + paddingNegator);
		});
	}

	private int getMeasuredItemHeight(final int position) {
		// child needed is not on screen, we need to measure
		if (scrollToVerseConvertViews == null) {
			// initialize scrollToVerseConvertViews if needed
			scrollToVerseConvertViews = new View[adapter.getViewTypeCount()];
		}
		final int itemType = adapter.getItemViewType(position);
		final View convertView = scrollToVerseConvertViews[itemType];
		final View child = adapter.getView(position, convertView, this);
		child.measure(MeasureSpec.makeMeasureSpec(this.getWidth() - this.getPaddingLeft() - this.getPaddingRight(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
		scrollToVerseConvertViews[itemType] = child;
		return child.getMeasuredHeight();
	}

	public void scrollToTop() {
		post(() -> setSelectionFromTop(0, 0));
	}

	@Override
	public void onScrollStateChanged(AbsListView view, int scrollState) {
		if (userOnScrollListener != null) userOnScrollListener.onScrollStateChanged(view, scrollState);

		this.scrollState = scrollState;
	}

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
		if (userOnScrollListener != null) userOnScrollListener.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);

		if (onVerseScrollListener == null) return;

		if (view.getChildCount() == 0) return;

		float prop = 0.f;
		int position = -1;

		final View firstChild = view.getChildAt(0);
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

		if (scrollState != AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
			if (verse_1 > 0) {
				onVerseScrollListener.onVerseScroll(this, false, verse_1, prop);
			} else {
				onVerseScrollListener.onVerseScroll(this, true, 0, 0);
			}

			if (position == 0 && firstChild.getTop() == this.getPaddingTop()) {
				// we are really at the top
				onVerseScrollListener.onScrollToTop(this);
			}
		}
	}

	public void setDataEmpty() {
		dataVersionNumber.incrementAndGet();
		adapter.setDataEmpty();
	}

	public void stopFling() {
		StopListFling.stop(this);
	}

	public void setDictionaryModeAris(@Nullable final SparseBooleanArray aris) {
		adapter.setDictionaryModeAris(aris);
	}
}
