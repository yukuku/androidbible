/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package yuku.alkitab.reminder.widget;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.TimePicker;
import androidx.annotation.NonNull;
import yuku.alkitab.debug.R;

/**
 * A dialog that prompts the user for the time of day using a {@link android.widget.TimePicker}.
 *
 * <p>See the <a href="{@docRoot}guide/topics/ui/controls/pickers.html">Pickers</a>
 * guide.</p>
 */
public class HackedTimePickerDialog extends AlertDialog
	implements DialogInterface.OnClickListener, TimePicker.OnTimeChangedListener {

	/**
	 * The callback interface used to indicate the user is done filling in
	 * the time (they clicked on the 'Set' button).
	 */
	public interface HackedTimePickerListener {

		/**
		 * @param hourOfDay The hour that was set.
		 * @param minute    The minute that was set.
		 */
		void onTimeSet(int hourOfDay, int minute);

		void onTimeOff();
	}

	private static final String HOUR = "hour";
	private static final String MINUTE = "minute";
	private static final String IS_24_HOUR = "is24hour";

	private final TimePicker mTimePicker;
	private final HackedTimePickerListener mCallback;

	int mInitialHourOfDay;
	int mInitialMinute;
	boolean mIs24HourView;

	/**
	 * @param context      Parent.
	 * @param callBack     How parent is notified.
	 * @param hourOfDay    The initial hour.
	 * @param minute       The initial minute.
	 * @param is24HourView Whether this is a 24 hour view, or AM/PM.
	 */
	public HackedTimePickerDialog(Context context,
								  CharSequence dialogTitle, CharSequence setButtonTitle, CharSequence offButtonTitle,
								  HackedTimePickerListener callBack,
								  int hourOfDay, int minute, boolean is24HourView) {
		super(context, 0);
		mCallback = callBack;
		mInitialHourOfDay = hourOfDay;
		mInitialMinute = minute;
		mIs24HourView = is24HourView;

		setIcon(0);
		setTitle(dialogTitle);

		Context themeContext = getContext();
		setButton(BUTTON_POSITIVE, setButtonTitle, this);
		setButton(BUTTON_NEGATIVE, offButtonTitle, this);

		final View view = View.inflate(themeContext, R.layout.dialog_hacked_time_picker, null);
		setView(view);
		mTimePicker = view.findViewById(R.id.timePicker);

		// initialize state
		mTimePicker.setIs24HourView(mIs24HourView);
		mTimePicker.setCurrentHour(mInitialHourOfDay);
		mTimePicker.setCurrentMinute(mInitialMinute);
		mTimePicker.setOnTimeChangedListener(this);
	}

	@Override
	public void onClick(DialogInterface dialog, int which) {
		if (mCallback != null) {
			mTimePicker.clearFocus();
			if (which == AlertDialog.BUTTON_POSITIVE) {
				mCallback.onTimeSet(mTimePicker.getCurrentHour(), mTimePicker.getCurrentMinute());
			} else if (which == AlertDialog.BUTTON_NEGATIVE) {
				mCallback.onTimeOff();
			}
		}
	}

	@Override
	public void onTimeChanged(TimePicker view, int hourOfDay, int minute) {
		/* do nothing */
	}

    /* Removed on this hacked version
    protected void onStop() {
        tryNotifyTimeSet();
        super.onStop();
    }
    */

	@NonNull
	@Override
	public Bundle onSaveInstanceState() {
		Bundle state = super.onSaveInstanceState();
		state.putInt(HOUR, mTimePicker.getCurrentHour());
		state.putInt(MINUTE, mTimePicker.getCurrentMinute());
		state.putBoolean(IS_24_HOUR, mTimePicker.is24HourView());
		return state;
	}

	@Override
	public void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		int hour = savedInstanceState.getInt(HOUR);
		int minute = savedInstanceState.getInt(MINUTE);
		mTimePicker.setIs24HourView(savedInstanceState.getBoolean(IS_24_HOUR));
		mTimePicker.setCurrentHour(hour);
		mTimePicker.setCurrentMinute(minute);
	}
}