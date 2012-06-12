package yuku.alkitab.base.widget;

import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.preference.ListPreference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;

import java.util.ArrayList;
import java.util.List;

import yuku.alkitab.R;
import yuku.alkitab.base.App;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.FontManagerActivity;
import yuku.alkitab.base.util.FontManager;
import yuku.alkitab.base.util.FontManager.FontEntry;

public class JenisHurufPreference extends ListPreference {
	public static final String TAG = JenisHurufPreference.class.getSimpleName();
	
	List<FontManager.FontEntry> fontEntries = new ArrayList<FontManager.FontEntry>();
	int originalNumberOfEntries;
	int mClickedDialogEntryIndex;
	
    public JenisHurufPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public JenisHurufPreference(Context context) {
        super(context);
        init();
    }
    
    private void init() {
    }
    
    private void initAvailableFonts() {
    	fontEntries.clear();
    	fontEntries.addAll(FontManager.getInstalledFonts());
	}


	@Override public CharSequence[] getEntryValues() {
    	// get super first
    	CharSequence[] preset = super.getEntryValues();
    	originalNumberOfEntries = preset.length;
    	
    	// additional...
    	CharSequence[] res = new CharSequence[preset.length + fontEntries.size() + 1];
    	System.arraycopy(preset, 0, res, 0, preset.length);
    	
    	for (int i = 0; i < fontEntries.size(); i++) {
    		res[preset.length + i] = fontEntries.get(i).name;
    	}
    	
    	res[res.length - 1] = "<ADD>";
    	return res;
    }
    
    @Override public CharSequence[] getEntries() {
    	CharSequence[] preset = super.getEntries();
    	originalNumberOfEntries = preset.length;
    	
    	// additional...
    	CharSequence[] res = new CharSequence[preset.length + fontEntries.size() + 1];
    	System.arraycopy(preset, 0, res, 0, preset.length);
    	
    	for (int i = 0; i < fontEntries.size(); i++) {
    		res[preset.length + i] = fontEntries.get(i).title;
    	}
    	
    	res[res.length - 1] = App.context.getString(R.string.get_more_fonts);
    	return res;
    }
    
	@Override protected void onPrepareDialogBuilder(Builder builder) {
		initAvailableFonts();
		
		final CharSequence[] entryValues = getEntryValues();
		final CharSequence[] entries = getEntries();
		
		if (entries == null || entryValues == null) {
			throw new IllegalStateException("JenisHurufPreference requires an entries array and an entryValues array."); //$NON-NLS-1$
		}

		String prevValue = getValue();
		// find position
		mClickedDialogEntryIndex = -1;
		for (int i = 0; i < entryValues.length; i++) {
			if (U.equals(prevValue, entryValues[i])) {
				mClickedDialogEntryIndex = i;
				break;
			}
		}
		
		builder.setAdapter(new BaseAdapter() {
			public FontManager.FontEntry getFontEntryFromPosition(int position) {
				if (position < originalNumberOfEntries || position == entryValues.length-1 /* "add fonts" */) {
					return null;
				} else {
					return fontEntries.get(position - originalNumberOfEntries);
				}
			}
			
			@Override public View getView(int position, View convertView, ViewGroup parent) {
				CheckedTextView res = (CheckedTextView) convertView;
				if (convertView == null) {
					res = (CheckedTextView) LayoutInflater.from(getContext()).inflate(android.R.layout.select_dialog_singlechoice, null);
				}
				
				res.setText(entries[position]);
				
				FontEntry fontEntry = getFontEntryFromPosition(position);
				if (fontEntry == null) {
					res.setTypeface(FontManager.typeface(entryValues[position].toString()));
				} else {
					res.setTypeface(FontManager.typeface(fontEntry.name));
				}
				
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
				if (which != entryValues.length - 1) {
					JenisHurufPreference.this.onClick(dialog, DialogInterface.BUTTON_POSITIVE);
					dialog.dismiss();
				} else {
					getContext().startActivity(FontManagerActivity.createIntent());
				}
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
}
