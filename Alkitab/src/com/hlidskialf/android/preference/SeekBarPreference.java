/* The following code was written by Matthew Wiggins 
 * and is released under the APACHE 2.0 license 
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package com.hlidskialf.android.preference;

import android.content.*;
import android.preference.*;
import android.util.*;
import android.view.*;
import android.widget.*;

public class SeekBarPreference extends DialogPreference implements SeekBar.OnSeekBarChangeListener {
	private static final String androidns = "http://schemas.android.com/apk/res/android";

	private SeekBar mSeekBar;
	private TextView mSplashText, mValueText;
	private Context context_;

	private String mDialogMessage, mSuffix;
	private int mDefault, max_, value_ = 0;

	public SeekBarPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
		context_ = context;

		mDialogMessage = attrs.getAttributeValue(androidns, "dialogMessage");
		mSuffix = attrs.getAttributeValue(androidns, "text");
		mDefault = attrs.getAttributeIntValue(androidns, "defaultValue", 0);
		max_ = attrs.getAttributeIntValue(androidns, "max", 100);
	}

	@Override
	protected View onCreateDialogView() {
		LinearLayout.LayoutParams params;
		LinearLayout layout = new LinearLayout(context_);
		layout.setOrientation(LinearLayout.VERTICAL);
		layout.setPadding(6, 6, 6, 6);

		mSplashText = new TextView(context_);
		if (mDialogMessage != null) mSplashText.setText(mDialogMessage);
		layout.addView(mSplashText);

		mValueText = new TextView(context_);
		mValueText.setGravity(Gravity.CENTER_HORIZONTAL);
		mValueText.setTextSize(32);
		params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		layout.addView(mValueText, params);

		mSeekBar = new SeekBar(context_);
		mSeekBar.setOnSeekBarChangeListener(this);
		layout.addView(mSeekBar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

		value_ = getPersistedInt(mDefault);

		mSeekBar.setMax(max_);
		mSeekBar.setProgress(value_);
		return layout;
	}

	@Override
	protected void onBindDialogView(View v) {
		super.onBindDialogView(v);
		mSeekBar.setMax(max_);
		mSeekBar.setProgress(value_);
	}

	public void onProgressChanged(SeekBar seek, int value, boolean fromTouch) {
		value_ = value;
		
		String t = String.valueOf(value);
		mValueText.setText(mSuffix == null ? t : t.concat(mSuffix));
		
		mValueText.setTextColor(0xff000000 | (255 * value / 100 * 0x010101));
	}

	public void onStartTrackingTouch(SeekBar seek) {
	}

	public void onStopTrackingTouch(SeekBar seek) {
	}

	public void setMax(int max) {
		max_ = max;
	}

	public int getMax() {
		return max_;
	}

	public void setProgress(int progress) {
		value_ = progress;
		if (mSeekBar != null) mSeekBar.setProgress(progress);
	}

	public int getProgress() {
		return value_;
	}
	
	@Override
	protected void onDialogClosed(boolean positiveResult) {
		if (positiveResult) {
			if (shouldPersist()) {
				persistInt(value_);
			}
		}
	}
}