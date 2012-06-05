package yuku.alkitab.base.widget;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import yuku.alkitab.R;
import yuku.alkitab.base.U;

public class LineSpacingMultPreference extends DialogPreference implements OnSeekBarChangeListener {
	private SeekBar seekbar;
	private float value;
	private TextView lContoh;
	private TextView lValue;

	public LineSpacingMultPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override protected View onCreateDialogView() {
		View res = LayoutInflater.from(getContext()).inflate(R.layout.dialog_linespacingmult_pref, null);

		seekbar = (SeekBar) res.findViewById(R.id.seekbar);
		lContoh = (TextView) res.findViewById(R.id.lContoh);
		lValue = (TextView) res.findViewById(R.id.lValue);

		seekbar.setOnSeekBarChangeListener(this);

		return res;
	}

	public void setValue(float value) {
		final boolean wasBlocking = shouldDisableDependents();

		this.value = value;
		persistFloat(value);

		final boolean isBlocking = shouldDisableDependents();
		if (isBlocking != wasBlocking) {
			notifyDependencyChange(isBlocking);
		}
	}

	@Override protected void onBindDialogView(View view) {
		super.onBindDialogView(view);
		seekbar.setProgress((int) ((value - 1.f) * 20.f));
	}

	@Override protected void onDialogClosed(boolean positiveResult) {
		super.onDialogClosed(positiveResult);

		if (positiveResult) {
			float value = (float) seekbar.getProgress() * 0.05f + 1.f;
			if (callChangeListener(value)) setValue(value);
		}
	}

	@Override protected Object onGetDefaultValue(TypedArray a, int index) {
		return a.getFloat(index, 1.0f);
	}

	@Override protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
		setValue(restoreValue ? getPersistedFloat(value) : (Float) defaultValue);
	}

	@Override protected Parcelable onSaveInstanceState() {
		final Parcelable superState = super.onSaveInstanceState();
		if (isPersistent()) {
			// No need to save instance state since it's persistent
			return superState;
		}

		final SavedState myState = new SavedState(superState);
		myState.value = value;
		return myState;
	}

	@Override protected void onRestoreInstanceState(Parcelable state) {
		if (state == null || !state.getClass().equals(SavedState.class)) {
			// Didn't save state for us in onSaveInstanceState
			super.onRestoreInstanceState(state);
			return;
		}

		SavedState myState = (SavedState) state;
		super.onRestoreInstanceState(myState.getSuperState());
		setValue(myState.value);
	}

	@Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		float realValue = progress * 0.05f + 1.0f;

		lValue.setText(String.format("%.2f", realValue));
		lContoh.setLineSpacing(0.f, realValue);

		// # atur jenis huruf dan tebal huruf
		SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
		String jenisHuruf = sharedPreferences.getString(getContext().getString(R.string.pref_jenisHuruf_key), null);
		boolean tebalHuruf = sharedPreferences.getBoolean(getContext().getString(R.string.pref_boldHuruf_key), false);
		if (jenisHuruf != null) {
			lContoh.setTypeface(U.typeface(jenisHuruf), tebalHuruf ? Typeface.BOLD : Typeface.NORMAL);
		}
	}

	@Override public void onStartTrackingTouch(SeekBar seekBar) {
		// diam
	}

	@Override public void onStopTrackingTouch(SeekBar seekBar) {
		// diam
	}

	private static class SavedState extends BaseSavedState {
		float value;

		public SavedState(Parcel source) {
			super(source);
			value = source.readFloat();
		}

		@Override public void writeToParcel(Parcel dest, int flags) {
			super.writeToParcel(dest, flags);
			dest.writeFloat(value);
		}

		public SavedState(Parcelable superState) {
			super(superState);
		}

		@SuppressWarnings("unused") public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
			@Override public SavedState createFromParcel(Parcel in) {
				return new SavedState(in);
			}

			@Override public SavedState[] newArray(int size) {
				return new SavedState[size];
			}
		};
	}
}