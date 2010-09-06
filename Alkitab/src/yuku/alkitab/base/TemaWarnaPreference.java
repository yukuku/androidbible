package yuku.alkitab.base;

import yuku.alkitab.R;
import android.app.AlertDialog.Builder;
import android.content.*;
import android.preference.*;
import android.text.*;
import android.text.style.*;
import android.util.*;
import android.view.*;
import android.widget.*;

public class TemaWarnaPreference extends ListPreference {
	private static final String TAG = TemaWarnaPreference.class.getSimpleName();
	
	private int mClickedDialogEntryIndex;
	private String mValue;
	
	public TemaWarnaPreference(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public TemaWarnaPreference(Context context) {
		this(context, null);
	}

	@Override
	protected void onPrepareDialogBuilder(Builder builder) {
		final CharSequence[] entryValues = getEntryValues();
		final CharSequence[] entries = getEntries();

		if (entries == null || entryValues == null) {
			throw new IllegalStateException("TemaWarnaPreference requires an entries array and an entryValues array.");
		}

		mClickedDialogEntryIndex = getValueIndex();

		builder.setAdapter(new BaseAdapter() {
			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				CheckedTextView res = (CheckedTextView) convertView;
				if (convertView == null) {
					res = (CheckedTextView) LayoutInflater.from(getContext()).inflate(android.R.layout.select_dialog_singlechoice, null);
				}

				CharSequence value = entryValues[position];
				int[] w = pisahWarna(value);

				SpannableStringBuilder sb = new SpannableStringBuilder();

				// no ayat
				String s1 = String.valueOf(position + 1) + " ";
				sb.append(s1);
				sb.setSpan(new ForegroundColorSpan(w[2]), 0, s1.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

				// nama
				String s2 = entries[position].toString();
				sb.append(s2);
				sb.setSpan(new ForegroundColorSpan(w[0]), s1.length(), s1.length() + s2.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

				res.setText(sb);
				res.setBackgroundColor(w[1]);

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
			public void onClick(DialogInterface dialog, int which) {
				mClickedDialogEntryIndex = which;

				/*
				 * Clicking on an item simulates the positive button
				 * click, and dismisses the dialog.
				 */
				TemaWarnaPreference.this.onClick(dialog, DialogInterface.BUTTON_POSITIVE);
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
				notifyChanged();
			}
		}
	}

	@Override
	public String getValue() {
		Log.d(TAG, "getValue");
		
		SharedPreferences pref = getPreferenceManager().getSharedPreferences();
		String w1 = String.format("%08x", pref.getInt(getContext().getString(R.string.pref_warnaHuruf_int_key), 0xff000000));
		String w2 = String.format("%08x", pref.getInt(getContext().getString(R.string.pref_warnaLatar_int_key), 0xff000000));
		String w3 = String.format("%08x", pref.getInt(getContext().getString(R.string.pref_warnaNomerAyat_int_key), 0xff000000));
		return gabungWarna(w1, w2, w3);
	}

	private int getValueIndex() {
		return findIndexOfValue(getValue());
	}

	@Override
	protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
		Log.d(TAG, "onSetInitialValue " + restoreValue + " " + defaultValue);

		setValue(restoreValue ? getPersistedString(mValue) : (String) defaultValue);
	}

	@Override
	public void setValue(String value) {
		Log.d(TAG, "setValue " + value);
		
		mValue = value;

		int[] w = pisahWarna(value);
//		SharedPreferences pref = getPreferenceManager().getSharedPreferences();
//		Editor editor = pref.edit();
//		editor.putInt(getContext().getString(R.string.pref_warnaHuruf_int_key), w[0]);
//		editor.putInt(getContext().getString(R.string.pref_warnaLatar_int_key), w[1]);
//		editor.putInt(getContext().getString(R.string.pref_warnaNomerAyat_int_key), w[2]);
//		editor.commit();
		
		int[] xresId = {R.string.pref_warnaHuruf_int_key, R.string.pref_warnaLatar_int_key, R.string.pref_warnaNomerAyat_int_key};
		for (int i = 0; i < xresId.length; i++) {
			AmbilWarnaPreference p = (AmbilWarnaPreference) findPreferenceInHierarchy(getContext().getString(xresId[i]));
			p.paksaSetWarna(w[i]);
		}
	}
	
	private static int[] pisahWarna(CharSequence value) {
		int[] w = new int[3];
		w[0] = (int) Long.parseLong(value.subSequence(0, 8).toString(), 16);
		w[1] = (int) Long.parseLong(value.subSequence(9, 17).toString(), 16);
		w[2] = (int) Long.parseLong(value.subSequence(18, 26).toString(), 16);
		return w;
	}

	private static String gabungWarna(String w1, String w2, String w3) {
		return w1 + " " + w2 + " " + w3;
	}
}
