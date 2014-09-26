package yuku.alkitab.base.widget;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.U;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Book;
import yuku.alkitab.model.PericopeBlock;
import yuku.alkitab.model.SingleChapterVerses;
import yuku.alkitab.util.IntArrayList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class VersesView extends ListView implements AbsListView.OnScrollListener {
	public static final String TAG = VersesView.class.getSimpleName();
	
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
		void onSomeVersesSelected(VersesView v);
		void onNoVersesSelected(VersesView v);
		void onVerseSingleClick(VersesView v, int verse_1);
	}

	public interface AttributeListener {
		void onBookmarkAttributeClick(Book book, int chapter_1, int verse_1);
		void onNoteAttributeClick(Book book, int chapter_1, int verse_1);
		void onProgressMarkAttributeClick(int preset_id);
	}

	public interface OnVerseScrollListener {
		void onVerseScroll(VersesView v, boolean isPericope, int verse_1, float prop);
		void onScrollToTop(VersesView v);
	}

	public interface OnVerseScrollStateChangeListener {
		void onVerseScrollStateChange(VersesView versesView, int scrollState);
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

	private VerseAdapter adapter;
	private SelectedVersesListener listener;
	private VerseSelectionMode verseSelectionMode;
	private Drawable originalSelector;
	private OnVerseScrollListener onVerseScrollListener;
	private OnVerseScrollStateChangeListener onVerseScrollStateChangeListener;
	private AbsListView.OnScrollListener userOnScrollListener;
	private int scrollState = 0;
	private View[] scrollToVerseConvertViews;
	private String name;
	private boolean firstTimeScroll = true;

	public VersesView(Context context) {
		super(context);
		init();
	}

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
		
		originalSelector = getSelector();
		
		setDivider(null);
		setFocusable(false);
		
		setAdapter(adapter = new VerseAdapter.Factory().create(getContext()));
		setOnItemClickListener(itemClick);
		setVerseSelectionMode(VerseSelectionMode.multiple);
		
		super.setOnScrollListener(this);
	}
	
	@Override public final void setOnScrollListener(AbsListView.OnScrollListener l) {
		userOnScrollListener = l;
	}
	
	@Override public VerseAdapter getAdapter() {
		return adapter;
	}

	public void setParallelListener(CallbackSpan.OnClickListener<Object> parallelListener) {
		adapter.setParallelListener(parallelListener);
	}

	public void setAttributeListener(VersesView.AttributeListener attributeListener) {
		adapter.setAttributeListener(attributeListener);
	}

	public void setInlineLinkSpanFactory(final VerseInlineLinkSpan.Factory inlineLinkSpanFactory) {
		adapter.setInlineLinkSpanFactory(inlineLinkSpanFactory, this);
	}
	
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

	public void setOnVerseScrollListener(OnVerseScrollListener onVerseScrollListener) {
		this.onVerseScrollListener = onVerseScrollListener;
	}

	// TODO external should provide the attribute map into this widget similar to setData(),
	// instead of this widget itself accessing persistent data.
	// When this is done, we do not need to provide Book and chapter_1 as parameters to setData(),
	// because in reality, VersesViews could contain verses taken from multiple books and chapters.
	public void reloadAttributeMap() {
		adapter.reloadAttributeMap();
	}
	
	public String getVerse(int verse_1) {
		return adapter.getVerse(verse_1);
	}

	public void scrollToShowVerse(int mainVerse_1) {
		int position = adapter.getPositionOfPericopeBeginningFromVerse(mainVerse_1);
		smoothScrollToPosition(position);
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
			if (bottom > getPaddingTop()) {
				return pos;
			} else {
				return pos+1;
			}
		}
		
		return pos;
	}

	public void setData(Book book, int chapter_1, SingleChapterVerses verses, int[] pericopeAris, PericopeBlock[] pericopeBlocks, int nblock) {
		adapter.setData(book, chapter_1, verses, pericopeAris, pericopeBlocks, nblock);
		stopFling();
	}

	private OnItemClickListener itemClick = new OnItemClickListener() {
		@Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			if (verseSelectionMode == VerseSelectionMode.singleClick) {
				if (listener != null) listener.onVerseSingleClick(VersesView.this, adapter.getVerseFromPosition(position));
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
		
		SparseBooleanArray checkedPositions = getCheckedItemPositions();
		boolean anyChecked = false;
		if (checkedPositions != null) for (int i = 0; i < checkedPositions.size(); i++) if (checkedPositions.valueAt(i)) {
			anyChecked = true;
			break;
		}
		
		if (anyChecked) {
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
	
	@Override public Parcelable onSaveInstanceState() {
		Bundle b = new Bundle();
		Parcelable superState = super.onSaveInstanceState();
		b.putParcelable("superState", superState);
		b.putInt("verseSelectionMode", verseSelectionMode.ordinal());
		return b;
	}
	
	@Override public void onRestoreInstanceState(Parcelable state) {
		if (state instanceof Bundle) {
			Bundle b = (Bundle) state;
			super.onRestoreInstanceState(b.getParcelable("superState"));
			setVerseSelectionMode(VerseSelectionMode.values()[b.getInt("verseSelectionMode")]);
		}
		
		hideOrShowContextMenuButton();
	}
	
	public PressResult press(int keyCode) {
		String volumeButtonsForNavigation = Preferences.getString(getContext().getString(R.string.pref_volumeButtonNavigation_key), getContext().getString(R.string.pref_volumeButtonNavigation_default));
		if (U.equals(volumeButtonsForNavigation, "pasal" /* chapter */)) { //$NON-NLS-1$
			if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
				return PressResult.LEFT;
			}
			if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
				return PressResult.RIGHT;
			}
		} else if (U.equals(volumeButtonsForNavigation, "ayat" /* verse */)) { //$NON-NLS-1$
			if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) keyCode = KeyEvent.KEYCODE_DPAD_DOWN;
			if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) keyCode = KeyEvent.KEYCODE_DPAD_UP;
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

	public void setDataWithRetainSelectedVerses(boolean retainSelectedVerses, Book book, int chapter_1, int[] pericope_aris, PericopeBlock[] pericope_blocks, int nblock, SingleChapterVerses verses) {
		IntArrayList selectedVerses_1 = null;
		if (retainSelectedVerses) {
			selectedVerses_1 = getSelectedVerses_1();
		}
		
		//# fill adapter with new data. make sure all checked states are reset
		uncheckAllVerses(true);
		setData(book, chapter_1, verses, pericope_aris, pericope_blocks, nblock);
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

	public void setVerseSelected(final int verse_1, final boolean selected) {
		int pos = adapter.getPositionIgnoringPericopeFromVerse(verse_1);
		if (pos != -1) {
			setItemChecked(pos, selected);
			if (listener != null) {
				final IntArrayList selectedVerses_1 = getSelectedVerses_1();
				if (selectedVerses_1.size() > 0) {
					listener.onSomeVersesSelected(this);
				} else {
					listener.onNoVersesSelected(this);
				}
			}
		}
	}

	public void scrollToVerse(final int verse_1) {
		final int position = adapter.getPositionOfPericopeBeginningFromVerse(verse_1);

		if (BuildConfig.DEBUG) Log.d(TAG, this + " @@scrollToVerse begin verse_1=" + verse_1 + " position=" + position, new Throwable().fillInStackTrace());

		if (position == -1) {
			Log.w(TAG, "could not find verse_1=" + verse_1 + ", weird!");
		} else {
			final int delay = firstTimeScroll? 34: 0;

			postDelayed(new Runnable() {
				@Override
				public void run() {
					// this may happen async from above, so check first if pos is still valid
					if (position >= getCount()) return;

					if (BuildConfig.DEBUG) Log.d(TAG, VersesView.this + " @@scrollToVerse post verse_1=" + verse_1 + " position=" + position, new Throwable().fillInStackTrace());

					stopFling();
					setSelection(position);

					firstTimeScroll = false;
				}
			}, delay);
		}
	}
	
	public void scrollToVerse(int verse_1, final float prop) {
		final int position = adapter.getPositionIgnoringPericopeFromVerse(verse_1);
		
		if (position == -1) {
			Log.d(TAG, "could not find verse_1: " + verse_1);
			return;
		}

		post(new Runnable() {
			@Override public void run() {
				// this may happen async from above, so check first if pos is still valid
				if (position >= getCount()) return;

				final int firstPos = getFirstVisiblePosition();
				final int lastPos = getLastVisiblePosition();
				if (position >= firstPos && position <= lastPos) {
					// we have this on screen, no need to measure again
					View child = getChildAt(position - firstPos);
					stopFling();
					setSelectionFromTop(position, - (int) (prop * child.getHeight()));
					return;
				}

				// initialize scrollToVerseConvertViews if needed
				if (scrollToVerseConvertViews == null) {
					scrollToVerseConvertViews = new View[adapter.getViewTypeCount()];
				}
				int itemType = adapter.getItemViewType(position);
				View convertView = scrollToVerseConvertViews[itemType];
				View child = adapter.getView(position, convertView, VersesView.this);
				child.measure(MeasureSpec.makeMeasureSpec(VersesView.this.getWidth() - VersesView.this.getPaddingLeft() - VersesView.this.getPaddingRight(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
				scrollToVerseConvertViews[itemType] = child;
				stopFling();
				setSelectionFromTop(position, - (int) (prop * child.getMeasuredHeight()));
			}
		});
	}
	
	public void scrollToTop() {
		post(new Runnable() {
			@Override public void run() {
		        setSelectionFromTop(0, 0);
			}
		});
	}

	public void setSelectedVersesListener(SelectedVersesListener listener) {
		this.listener = listener;
	}

	public void setOnVerseScrollStateChangeListener(OnVerseScrollStateChangeListener onVerseScrollStateChangeListener) {
		this.onVerseScrollStateChangeListener = onVerseScrollStateChangeListener;
	}

	@Override public void onScrollStateChanged(AbsListView view, int scrollState) {
		if (userOnScrollListener != null) userOnScrollListener.onScrollStateChanged(view, scrollState);
		
		this.scrollState = scrollState;
		if (onVerseScrollStateChangeListener != null) {
			onVerseScrollStateChangeListener.onVerseScrollStateChange(this, scrollState);
		}
	}
	
	@Override public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
		if (userOnScrollListener != null) userOnScrollListener.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
		
		if (onVerseScrollListener == null) return;

		if (view.getChildCount() == 0) return;

		float prop = 0.f;
		int position = -1;

		final View firstChild = view.getChildAt(0);
		final int bottom = firstChild.getBottom();
		final int remaining = bottom - view.getPaddingTop();
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

			if (position == 0 && prop == 0.f) {
				onVerseScrollListener.onScrollToTop(this);
			}
		}
	}

	public void setDataEmpty() {
		adapter.setDataEmpty();
	}
	
	public void stopFling() {
		StopListFling.stop(this);
	}

	public void updateAdapter() {
		adapter.notifyDataSetChanged();
	}

	@Override
	public String toString() {
		return name != null? ("VersesView{name=" + name + "}"): "VersesView";
	}
}
