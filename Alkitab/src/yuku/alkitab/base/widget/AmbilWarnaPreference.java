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

package yuku.alkitab.base.widget;

import android.content.*;
import android.content.res.*;
import android.os.*;
import android.preference.*;
import android.util.*;
import android.view.*;

import yuku.alkitab.beta.R;
import yuku.ambilwarna.*;

/**
 * This is an example of a custom preference type. The preference counts the
 * number of clicks it has received and stores/retrieves it from the storage.
 */
public class AmbilWarnaPreference extends Preference {
    int warna;
    
    // This is the constructor called by the inflater
    public AmbilWarnaPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        
        setWidgetLayoutResource(R.layout.ambilwarna_pref_widget);        
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        
        // Set our custom views inside the layout
        final View kotak = view.findViewById(R.id.ambilwarna_pref_widget_kotak);
        if (kotak != null) {
            kotak.setBackgroundColor(warna);
        }
    }

    @Override
    protected void onClick() {
    	AmbilWarnaDialog dialog = new AmbilWarnaDialog(getContext(), warna, new AmbilWarnaDialog.OnAmbilWarnaListener() {
			@Override
			public void onOk(AmbilWarnaDialog dialog, int color) {
				// Give the client a chance to ignore this change if they deem it
				// invalid
				if (!callChangeListener(color)) {
					// They don't want the value to be set
					return;
				}
				
				// Increment counter
				warna = color;
				
				// Save to persistent storage (this method will make sure this
				// preference should be persistent, along with other useful checks)
				persistInt(warna);
				
				// Data has changed, notify so UI can be refreshed!
				notifyChanged();
			}
			
			@Override
			public void onCancel(AmbilWarnaDialog dialog) {
				// ga ngapa2in
			}
		});
    	dialog.show();
    }
    
    public void paksaSetWarna(int warna) {
    	this.warna = warna;
    	persistInt(warna);
    	notifyChanged();
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        // This preference type's value type is Integer, so we read the default
        // value from the attributes as an Integer.
        return a.getInteger(index, 0);
    }

    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        if (restoreValue) {
            // Restore state
            warna = getPersistedInt(warna);
        } else {
            // Set state
            int value = (Integer) defaultValue;
            warna = value;
            persistInt(value);
        }
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        /*
         * Suppose a client uses this preference type without persisting. We
         * must save the instance state so it is able to, for example, survive
         * orientation changes.
         */
        
        final Parcelable superState = super.onSaveInstanceState();
        if (isPersistent()) {
            // No need to save instance state since it's persistent
            return superState;
        }

        // Save the instance state
        final SavedState myState = new SavedState(superState);
        myState.warna = warna;
        return myState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!state.getClass().equals(SavedState.class)) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state);
            return;
        }
     
        // Restore the instance state
        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        warna = myState.warna;
        notifyChanged();
    }
    
    /**
     * SavedState, a subclass of {@link BaseSavedState}, will store the state
     * of MyPreference, a subclass of Preference.
     * <p>
     * It is important to always call through to super methods.
     */
    private static class SavedState extends BaseSavedState {
        int warna;
        
        public SavedState(Parcel source) {
            super(source);
            
            // Restore the click counter
            warna = source.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            
            // Save the click counter
            dest.writeInt(warna);
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        @SuppressWarnings("unused")
		public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }
    
}
