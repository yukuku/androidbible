package yuku.alkitab.base.widget;

import android.app.AlertDialog.Builder;
import android.content.*;
import android.preference.*;
import android.util.*;
import android.view.*;
import android.widget.*;

import yuku.alkitab.base.*;

public class JenisHurufPreference extends ListPreference {
	int mClickedDialogEntryIndex;
	
    public JenisHurufPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public JenisHurufPreference(Context context) {
        super(context);
    }
    
	@Override
	protected void onPrepareDialogBuilder(Builder builder) {
		final CharSequence[] entryValues = getEntryValues();
		final CharSequence[] entries = getEntries();
		
		if (entries == null || entryValues == null) {
			throw new IllegalStateException("JenisHurufPreference requires an entries array and an entryValues array."); //$NON-NLS-1$
		}

		mClickedDialogEntryIndex = getValueIndex();
		
		builder.setAdapter(new BaseAdapter() {
			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				CheckedTextView res = (CheckedTextView) convertView;
				if (convertView == null) {
					res = (CheckedTextView) LayoutInflater.from(getContext()).inflate(android.R.layout.select_dialog_singlechoice, null);
				}
				
				res.setText(entries[position]);
				res.setTypeface(U.typeface(entryValues[position].toString()));
				
				res.setChecked(position == mClickedDialogEntryIndex);
				
				return res;
			}
			
			@Override
			public long getItemId(int position) {
				return position;
			}
			
			@Override
			public Object getItem(int position) {
				return null;
			}
			
			@Override
			public int getCount() {
				return entryValues.length;
			}
		}, new DialogInterface.OnClickListener() {
			@Override public void onClick(DialogInterface dialog, int which) {
				mClickedDialogEntryIndex = which;

				/*
				 * Clicking on an item simulates the positive button
				 * click, and dismisses the dialog.
				 */
				JenisHurufPreference.this.onClick(dialog, DialogInterface.BUTTON_POSITIVE);
				dialog.dismiss();
			}
		});

		/*
		 * The typical interaction for list-based dialogs is to have
		 * click-on-an-item dismiss the dialog instead of the user having to
		 * press 'Ok'.
		 */
		builder.setPositiveButton(null, null);
	}

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        
        final CharSequence[] entryValues = getEntryValues();
        
        if (positiveResult && mClickedDialogEntryIndex >= 0 && entryValues != null) {
            String value = entryValues[mClickedDialogEntryIndex].toString();
            if (callChangeListener(value)) {
                setValue(value);
            }
        }
    }
    
	private int getValueIndex() {
		return findIndexOfValue(getValue());
	}
}
