package yuku.alkitab.base.widget;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.util.List;

import yuku.afw.App;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.R;
import yuku.alkitab.base.ac.FontManagerActivity;
import yuku.alkitab.base.util.FontManager;

public class TextAppearancePanel {
	public static final String TAG = TextAppearancePanel.class.getSimpleName();
	
	private static final int REQCODE_fontManager = 1;
	
	public interface Listener {
		void onValueChanged();
	}
	
	final Activity activity;
	final LayoutInflater inflater;
	final FrameLayout parent;
	final Listener listener;
	final View content;
	
	Spinner cbTypeface;
	TextView lTextSize;
	SeekBar sbTextSize;
	TextView lLineSpacing;
	SeekBar sbLineSpacing;
	ToggleButton cBold;
	ColorSelectButton bColors;

	TypefaceAdapter typefaceAdapter;
	boolean shown = false;
	

	public TextAppearancePanel(Activity activity, LayoutInflater inflater, FrameLayout parent, Listener listener) {
		this.activity = activity;
		this.inflater = inflater;
		this.parent = parent;
		this.listener = listener;
		this.content = inflater.inflate(R.layout.panel_text_appearance, parent, false);
	    
	    cbTypeface = V.get(content, R.id.cbTypeface);
	    cBold = V.get(content, R.id.cBold);
	    lTextSize = V.get(content, R.id.lTextSize);
	    sbTextSize = V.get(content, R.id.sbTextSize);
	    lLineSpacing = V.get(content, R.id.lLineSpacing);
	    sbLineSpacing = V.get(content, R.id.sbLineSpacing);
	    bColors = V.get(content, R.id.bColors);
	    
	    cbTypeface.setAdapter(typefaceAdapter = new TypefaceAdapter());
	    cbTypeface.setOnItemSelectedListener(cbTypeface_itemSelected);
	    sbTextSize.setOnSeekBarChangeListener(sbTextSize_seekBarChange);
	    sbLineSpacing.setOnSeekBarChangeListener(sbLineSpacing_seekBarChange);
	    cBold.setOnCheckedChangeListener(cBold_checkedChange);
	    
	    displayValues();
	}
	
	void displayValues() {
		int selectedPosition = typefaceAdapter.getPositionByName(Preferences.getString(App.context.getString(R.string.pref_jenisHuruf_key)));
		if (selectedPosition >= 0) {
			cbTypeface.setSelection(selectedPosition);
		}
		
		boolean bold = Preferences.getBoolean(App.context.getString(R.string.pref_boldHuruf_key), false);
		cBold.setChecked(bold);
		
		float textSize = Preferences.getFloat(App.context.getString(R.string.pref_ukuranHuruf2_key), 17.f);
		sbTextSize.setProgress((int) ((textSize - 2.f) * 2));
		
		float lineSpacing = Preferences.getFloat(App.context.getString(R.string.pref_lineSpacingMult_key), 1.2f);
		sbLineSpacing.setProgress(Math.round((lineSpacing - 1.f) * 20.f));
		
		int[] colors = {
			Preferences.getInt(App.context.getString(R.string.pref_warnaHuruf_int_key), App.context.getResources().getInteger(R.integer.pref_warnaHuruf_int_default)),
			Preferences.getInt(App.context.getString(R.string.pref_warnaLatar_int_key), App.context.getResources().getInteger(R.integer.pref_warnaLatar_int_default)),
			Preferences.getInt(App.context.getString(R.string.pref_warnaNomerAyat_int_key), App.context.getResources().getInteger(R.integer.pref_warnaNomerAyat_int_default)),
			Preferences.getInt(App.context.getString(R.string.pref_redTextColor_key), App.context.getResources().getInteger(R.integer.pref_redTextColor_default)),
		};
		bColors.setBgColor(0xff000000);
		bColors.setColors(colors);
	}

	public void show() {
		if (shown) return;
		parent.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
		shown = true;
	}
	
	public void hide() {
		if (!shown) return;
		parent.removeView(content);
		shown = false;
	}
	
	AdapterView.OnItemSelectedListener cbTypeface_itemSelected = new AdapterView.OnItemSelectedListener() {
		@Override public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
			String name = typefaceAdapter.getNameByPosition(position);
			if (name == null) {
				activity.startActivityForResult(FontManagerActivity.createIntent(), REQCODE_fontManager);
				// TODO refresh font list
				displayValues();
			} else {
				Preferences.setString(App.context.getString(R.string.pref_jenisHuruf_key), name);
				listener.onValueChanged();
			}
		}

		@Override public void onNothingSelected(AdapterView<?> parent) {}
	};
	
	SeekBar.OnSeekBarChangeListener sbTextSize_seekBarChange = new SeekBar.OnSeekBarChangeListener() {
		@Override public void onStopTrackingTouch(SeekBar seekBar) {}
		
		@Override public void onStartTrackingTouch(SeekBar seekBar) {}
		
		@Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
			float textSize = progress * 0.5f + 2.f;
			Preferences.setFloat(App.context.getString(R.string.pref_ukuranHuruf2_key), textSize);
			lTextSize.setText(String.format("%.1f", textSize));
			listener.onValueChanged();
		}
	};
	
	SeekBar.OnSeekBarChangeListener sbLineSpacing_seekBarChange = new SeekBar.OnSeekBarChangeListener() {
		@Override public void onStopTrackingTouch(SeekBar seekBar) {}
		
		@Override public void onStartTrackingTouch(SeekBar seekBar) {}
		
		@Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
			float lineSpacing = 1.f + progress * 0.05f;
			Preferences.setFloat(App.context.getString(R.string.pref_lineSpacingMult_key), lineSpacing);
			lLineSpacing.setText(String.format("%.2f", lineSpacing));
			listener.onValueChanged();
		}
	};
	
	CompoundButton.OnCheckedChangeListener cBold_checkedChange = new CompoundButton.OnCheckedChangeListener() {
		@Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			Preferences.setBoolean(App.context.getString(R.string.pref_boldHuruf_key), isChecked);
			listener.onValueChanged();
		}
	};
	
	class TypefaceAdapter extends EasyAdapter {
		List<FontManager.FontEntry> fontEntries;

		public TypefaceAdapter() {
			fontEntries = FontManager.getInstalledFonts();
		}
		
		@Override public int getCount() {
			return 3 + fontEntries.size() + 1;
		}
		
		@Override public View newView(int position, ViewGroup parent) {
			return inflater.inflate(android.R.layout.simple_list_item_1, parent, false);
		}

		@Override public void bindView(View view, int position, ViewGroup parent) {
			TextView text1 = V.get(view, android.R.id.text1);
			
			if (position < 3) {
				text1.setText(new String[] {"Sans-serif", "Serif", "Monospace"}[position]);
				text1.setTypeface(new Typeface[] {Typeface.SANS_SERIF, Typeface.SERIF, Typeface.MONOSPACE}[position]);
			} else if (position == getCount() - 1) {
				text1.setText(App.context.getString(R.string.get_more_fonts));
				text1.setTypeface(Typeface.DEFAULT);
			} else {
				int idx = position - 3;
				text1.setText(fontEntries.get(idx).name);
				text1.setTypeface(FontManager.typeface(fontEntries.get(idx).name));
			}
		}
		
		public String getNameByPosition(int position) {
			if (position < 3) {
				return new String[] {"DEFAULT", "SERIF", "MONOSPACE"}[position];
			} else if (position < getCount() - 1) {
				int idx = position - 3;
				return fontEntries.get(idx).name;
			} else {
				return null;
			}
		}
		
		public int getPositionByName(String name) {
			if ("DEFAULT".equals(name)) {
				return 0;
			} else if ("SERIF".equals(name)) {
				return 1;
			} else if ("MONOSPACE".equals(name)) {
				return 2;
			} else {
				for (int i = 0; i < fontEntries.size(); i++) {
					if (fontEntries.get(i).name.equals(name)) {
						return i + 3;
					}
				}
			}
			return -1;
		}
	}
}
