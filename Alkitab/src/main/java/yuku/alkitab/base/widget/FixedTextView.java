package yuku.alkitab.base.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import androidx.appcompat.widget.AppCompatTextView;
import yuku.alkitab.base.util.AppLog;

/**
 * From https://stackoverflow.com/questions/33821008/illegalargumentexception-while-selecting-text-in-android-textview
 */
public class FixedTextView extends AppCompatTextView {
	static final String TAG = FixedTextView.class.getSimpleName();

	public FixedTextView(final Context context) {
		super(context);
	}

	public FixedTextView(final Context context, final AttributeSet attrs) {
		super(context, attrs);
	}

	public FixedTextView(final Context context, final AttributeSet attrs, final int defStyle) {
		super(context, attrs, defStyle);
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent event) {
		// passthrough
		// but can still actually throws error.
		try {
			return super.dispatchTouchEvent(event);
		} catch (Exception e) {
			AppLog.e(TAG, "Still error in dispatchTouchEvent", e);
		}

		return false;
	}
}
