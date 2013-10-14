package yuku.alkitab.base.widget;

import android.content.Context;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupWindow.OnDismissListener;
import android.widget.TextView;

import net.londatiga.android.PopupWindows;
import yuku.afw.V;
import yuku.alkitab.debug.R;

public class DevotionSelectPopup extends PopupWindows implements OnDismissListener {
	public interface DevotionSelectPopupListener {
		void onDismiss(DevotionSelectPopup popup);
		void onButtonClick(DevotionSelectPopup popup, View v);
	}

	private final Context context;
	
	Button bChange;
	View bPrev;
	View bNext;
	TextView tCurrentDate;
	ImageView mArrowUp;
	
	DevotionSelectPopupListener listener;
	int rootWidth = 0;

	public DevotionSelectPopup(Context context) {
		super(context);
		this.context = context;

		setRootViewId(R.layout.popup_devotion_select);
	}

	public void setRootViewId(int id) {
		mRootView = LayoutInflater.from(context).inflate(id, null);
		mArrowUp = (ImageView) mRootView.findViewById(R.id.arrow_up);
		bChange = V.get(mRootView, R.id.bChange);
		bPrev = V.get(mRootView, R.id.bPrev);
		bNext = V.get(mRootView, R.id.bNext);
		tCurrentDate = V.get(mRootView, R.id.tCurrentDate);

		bChange.setOnClickListener(button_click);
		bPrev.setOnClickListener(button_click);
		bNext.setOnClickListener(button_click);
		
		// This was previously defined on show() method, moved here to prevent force close that occured
		// when tapping fastly on a view to show quickaction dialog.
		// Thanx to zammbi (github.com/zammbi)
		mRootView.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

		setContentView(mRootView);
	}

	public void show(View anchor) {
		preShow();

		int[] location = new int[2];
		
		Rect anchorRect;
		if (anchor != null) {
			anchor.getLocationOnScreen(location);
			anchorRect = new Rect(location[0], location[1], location[0] + anchor.getWidth(), location[1] + anchor.getHeight());
		} else {
			float d = context.getResources().getDisplayMetrics().density;
			location[0] = (int) (d * 50.f);
			location[1] = (int) (d * 50.f);
			anchorRect = new Rect(location[0], location[1], location[0] + (int)(d * 50.f), location[1] + (int)(d * 50.f));
		}

		mRootView.measure(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

		if (rootWidth == 0) {
			rootWidth = mRootView.getMeasuredWidth();
		}

		int screenWidth = mWindowManager.getDefaultDisplay().getWidth();

		int xPos, yPos, arrowPos;
		// automatically get X coord of popup (top left)
		if ((anchorRect.left + rootWidth) > screenWidth) {
			xPos = anchorRect.left - (rootWidth - anchor.getWidth());
			xPos = (xPos < 0) ? 0 : xPos;

			arrowPos = anchorRect.centerX() - xPos;

		} else {
			if (anchor.getWidth() > rootWidth) {
				xPos = anchorRect.centerX() - (rootWidth / 2);
			} else {
				xPos = anchorRect.left;
			}

			arrowPos = anchorRect.centerX() - xPos;
		}

		yPos = anchorRect.bottom;

		showArrow(R.id.arrow_up, arrowPos);

		setAnimationStyle(screenWidth, anchorRect.centerX(), false);

		mWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
		mWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, xPos, yPos);
	}

	private void setAnimationStyle(int screenWidth, int requestedX, boolean onTop) {
		int arrowPos = requestedX - mArrowUp.getMeasuredWidth() / 2;

		if (arrowPos <= screenWidth / 4) {
			mWindow.setAnimationStyle((onTop) ? R.style.Animations_PopUpMenu_Left : R.style.Animations_PopDownMenu_Left);
		} else if (arrowPos > screenWidth / 4 && arrowPos < 3 * (screenWidth / 4)) {
			mWindow.setAnimationStyle((onTop) ? R.style.Animations_PopUpMenu_Center : R.style.Animations_PopDownMenu_Center);
		} else {
			mWindow.setAnimationStyle((onTop) ? R.style.Animations_PopUpMenu_Right : R.style.Animations_PopDownMenu_Right);
		}
	}

	private void showArrow(int whichArrow, int requestedX) {
		final View showArrow = mArrowUp;
		final int arrowWidth = mArrowUp.getMeasuredWidth();

		ViewGroup.MarginLayoutParams param = (ViewGroup.MarginLayoutParams) showArrow.getLayoutParams();

		param.leftMargin = requestedX - arrowWidth / 2;
	}

	public void setDevotionSelectListener(DevotionSelectPopupListener listener) {
		setOnDismissListener(this);
		
		this.listener = listener;
	}
	
	public void setDevotionDate(CharSequence date) {
		tCurrentDate.setText(date);
	}
	
	public void setDevotionName(CharSequence name) {
		bChange.setText(name);
	}

	@Override public void onDismiss() {
		if (listener != null) {
			listener.onDismiss(this);
		}
	}
	
	OnClickListener button_click = new OnClickListener() {
		@Override public void onClick(View v) {
			if (listener != null) {
				listener.onButtonClick(DevotionSelectPopup.this, v);
			}
		}
	};
}
