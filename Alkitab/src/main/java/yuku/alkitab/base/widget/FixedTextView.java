package yuku.alkitab.base.widget;

import android.content.Context;
import android.os.Build;
import android.support.v7.widget.AppCompatTextView;
import android.util.AttributeSet;
import android.view.MotionEvent;
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
		if (Build.VERSION.SDK_INT >= 24) {
			// passthrough
			// but can still actually throws error.
			try {
				return super.dispatchTouchEvent(event);
			} catch (Exception e) {
				AppLog.e(TAG, "Still error in dispatchTouchEvent", e);
			}
		}

		// simple workaround to https://code.google.com/p/android/issues/detail?id=191430
		int startSelection = getSelectionStart();
		int endSelection = getSelectionEnd();
		if (startSelection != endSelection) {
			if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
				final CharSequence text = getText();
				setText(null);
				setText(text);
			}
		}
		return super.dispatchTouchEvent(event);
	}
}
