package yuku.devoxx.flowlayout;

import android.content.*;
import android.content.res.*;
import android.util.*;
import android.view.*;

public class FlowLayout extends ViewGroup {
	public static final String TAG = FlowLayout.class.getSimpleName();
	
	private int mHorizontalSpacing;
	private int mVerticalSpacing;

	public FlowLayout(Context context) {
		super(context);
	}

	public FlowLayout(Context context, AttributeSet attrs) {
		super(context, attrs);
		
		TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.FlowLayout);
		try {
			mHorizontalSpacing = a.getDimensionPixelSize(R.styleable.FlowLayout_horizontalSpacing, 0);
			mVerticalSpacing = a.getDimensionPixelSize(R.styleable.FlowLayout_verticalSpacing, 0);
		} finally {
			a.recycle();
		}
	}

	@Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int widthSize = MeasureSpec.getSize(widthMeasureSpec);
		
		int width = 0;
		int height = getPaddingTop();
		
		int currentWidth = getPaddingLeft();
		int currentHeight = 0;
		
		boolean breakLine = false;
		
		final int count = getChildCount();

		if (BuildConfig.DEBUG) {
			Log.d(TAG, "child count = " + count);
		}

		for (int i = 0; i < count; i++) {
			View child = getChildAt(i);
			LayoutParams lp = (LayoutParams) child.getLayoutParams();
			measureChild(child, widthMeasureSpec, heightMeasureSpec);

			if (BuildConfig.DEBUG) {
				Log.d(TAG, "child " + i + " mw=" + child.getMeasuredWidth() + " mh=" + child.getMeasuredHeight());
			}

			if (breakLine || currentWidth + child.getMeasuredWidth() > widthSize) {
				height += currentHeight + mVerticalSpacing;
				currentHeight = 0;
				if (currentWidth > width) width = currentWidth;
				currentWidth = getPaddingLeft();
			}
			
			int spacing = mHorizontalSpacing;
			if (lp.spacing != Integer.MAX_VALUE) {
				spacing = lp.spacing;
			}
			
			lp.x = currentWidth;
			lp.y = height;

			if (BuildConfig.DEBUG) {
				Log.d(TAG, "child " + i + " x=" + lp.x + " y=" + lp.y);
			}

			currentWidth += child.getMeasuredWidth() + spacing;
			int childHeight = child.getMeasuredHeight();
			if (childHeight > currentHeight) currentHeight = childHeight;
			
			breakLine = lp.breakLine;
		}
		
		// after last row (patched by yuku)
		{
			height += currentHeight;
			if (currentWidth > width) width = currentWidth;
		}
		
		width += getPaddingRight();
		height += getPaddingBottom();

		if (BuildConfig.DEBUG) {
			Log.d(TAG, "onMeasure w=" + width + " h=" + height + " widthMeasureSpec=" + Integer.toHexString(widthMeasureSpec) + " heightMeasureSpec=" + Integer.toHexString(heightMeasureSpec));
		}

		// don't resolve height (patched by yuku)
		setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
	}
	
	@Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
		final int count = getChildCount();
		for (int i = 0; i < count; i++) {
			View child = getChildAt(i);
			LayoutParams lp = (LayoutParams) child.getLayoutParams();
			child.layout(lp.x, lp.y, lp.x + child.getMeasuredWidth(), lp.y + child.getMeasuredHeight());
		}
	}
	
	@Override protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
		return p instanceof LayoutParams;
	}
	
	@Override public LayoutParams generateDefaultLayoutParams() {
		return new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
	}
	
	@Override public LayoutParams generateLayoutParams(AttributeSet attrs) {
		return new LayoutParams(getContext(), attrs);
	}
	
	@Override public LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
		return new LayoutParams(p.width, p.height);
	}
	
	public static class LayoutParams extends ViewGroup.LayoutParams {
		public boolean breakLine;
		public int spacing = Integer.MAX_VALUE;
		
		private int x;
		private int y;
		
		public LayoutParams(int width, int height) {
			super(width, height);
		}
		
		public LayoutParams(Context context, AttributeSet attrs) {
			super(context, attrs);

			TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.FlowLayout_LayoutParams);
			try {
				spacing = a.getDimensionPixelSize(R.styleable.FlowLayout_LayoutParams_layout_spacing, Integer.MAX_VALUE);
				breakLine = a.getBoolean(R.styleable.FlowLayout_LayoutParams_layout_breakLine, false);
			} finally {
				a.recycle();
			}		
		}
	}
}
