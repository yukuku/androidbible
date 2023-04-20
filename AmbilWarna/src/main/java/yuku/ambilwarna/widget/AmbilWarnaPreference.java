package yuku.ambilwarna.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import yuku.ambilwarna.AmbilWarnaDialog;
import yuku.ambilwarna.R;

public class AmbilWarnaPreference extends Preference {
	private final boolean supportsAlpha;
	int value;

	public AmbilWarnaPreference(Context context, AttributeSet attrs) {
		super(context, attrs);

		final TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.AmbilWarnaPreference);
		supportsAlpha = ta.getBoolean(R.styleable.AmbilWarnaPreference_supportsAlpha, false);

		setWidgetLayoutResource(R.layout.ambilwarna_pref_widget);
	}

	@Override
	public void onBindViewHolder(final PreferenceViewHolder holder) {
		super.onBindViewHolder(holder);

		// Set our custom views inside the layout
		final View box = holder.itemView.findViewById(R.id.ambilwarna_pref_widget_box);
		if (box != null) {
			box.setBackgroundColor(value);
		}
	}

	@Override protected void onClick() {
		new AmbilWarnaDialog(getContext(), value, supportsAlpha, new AmbilWarnaDialog.OnAmbilWarnaListener() {
			@Override public void onOk(AmbilWarnaDialog dialog, int color) {
				if (!callChangeListener(color)) return; // They don't want the value to be set
				value = color;
				persistInt(value);
				notifyChanged();
			}

			@Override public void onCancel(AmbilWarnaDialog dialog) {
				// nothing to do
			}
		}).show();
	}

	public void forceSetValue(int value) {
		this.value = value;
		persistInt(value);
		notifyChanged();
	}

	@Override protected Object onGetDefaultValue(TypedArray a, int index) {
		// This preference type's value type is Integer, so we read the default value from the attributes as an Integer.
		return a.getInteger(index, 0);
	}

	@Override protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
		if (restoreValue) { // Restore state
			value = getPersistedInt(value);
		} else { // Set state
			int value = (Integer) defaultValue;
			this.value = value;
			persistInt(value);
		}
	}

	/*
	 * Suppose a client uses this preference type without persisting. We
	 * must save the instance state so it is able to, for example, survive
	 * orientation changes.
	 */
	@Override protected Parcelable onSaveInstanceState() {
		final Parcelable superState = super.onSaveInstanceState();
		if (isPersistent()) return superState; // No need to save instance state since it's persistent

		final SavedState myState = new SavedState(superState);
		myState.value = value;
		return myState;
	}

	@Override protected void onRestoreInstanceState(Parcelable state) {
		if (!state.getClass().equals(SavedState.class)) {
			// Didn't save state for us in onSaveInstanceState
			super.onRestoreInstanceState(state);
			return;
		}

		// Restore the instance state
		SavedState myState = (SavedState) state;
		super.onRestoreInstanceState(myState.getSuperState());
		this.value = myState.value;
		notifyChanged();
	}

	/**
	 * SavedState, a subclass of {@link androidx.preference.Preference.BaseSavedState}, will store the state
	 * of MyPreference, a subclass of Preference.
	 * <p>
	 * It is important to always call through to super methods.
	 */
	private static class SavedState extends BaseSavedState {
		int value;

		public SavedState(Parcel source) {
			super(source);
			value = source.readInt();
		}

		@Override public void writeToParcel(Parcel dest, int flags) {
			super.writeToParcel(dest, flags);
			dest.writeInt(value);
		}

		public SavedState(Parcelable superState) {
			super(superState);
		}

		@SuppressWarnings("unused") public static final Creator<SavedState> CREATOR = new Creator<SavedState>() {
			public SavedState createFromParcel(Parcel in) {
				return new SavedState(in);
			}

			public SavedState[] newArray(int size) {
				return new SavedState[size];
			}
		};
	}
}
