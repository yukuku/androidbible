package yuku.alkitab.base.widget;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CheckedTextView;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import com.afollestad.materialdialogs.MaterialDialog;
import yuku.afw.App;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.base.ac.ColorSettingsActivity;
import yuku.alkitab.base.ac.FontManagerActivity;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.FontManager;
import yuku.alkitab.debug.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TextAppearancePanel {
	public static final String TAG = TextAppearancePanel.class.getSimpleName();

	public interface Listener {
		void onValueChanged();
		void onCloseButtonClick();
	}
	
	final Activity activity;
	final LayoutInflater inflater;
	final FrameLayout parent;
	final Listener listener;
	final View content;
	final int reqcodeGetFonts;
	final int reqcodeCustomColors;
	
	Spinner cbTypeface;
	TextView lTextSize;
	SeekBar sbTextSize;
	TextView lLineSpacing;
	SeekBar sbLineSpacing;
	CheckBox cBold;
	MultiColorView bColorTheme;
	View bClose;

	TypefaceAdapter typefaceAdapter;
	boolean shown = false;

	public TextAppearancePanel(Activity activity, LayoutInflater inflater, FrameLayout parent, Listener listener, int reqcodeGetFonts, int reqcodeCustomColors) {
		this.activity = activity;
		this.inflater = inflater;
		this.parent = parent;
		this.listener = listener;
		this.reqcodeGetFonts = reqcodeGetFonts;
		this.reqcodeCustomColors = reqcodeCustomColors;
		this.content = inflater.inflate(R.layout.panel_text_appearance, parent, false);

		this.content.setOnTouchListener((v, event) -> true); // prevent click-through
	    
	    cbTypeface = V.get(content, R.id.cbTypeface);
	    cBold = V.get(content, R.id.cBold);
	    lTextSize = V.get(content, R.id.lTextSize);
	    sbTextSize = V.get(content, R.id.sbTextSize);
	    lLineSpacing = V.get(content, R.id.lLineSpacing);
	    sbLineSpacing = V.get(content, R.id.sbLineSpacing);
	    bColorTheme = V.get(content, R.id.bColorTheme);
		bClose = V.get(content, R.id.bClose);

		cbTypeface.setAdapter(typefaceAdapter = new TypefaceAdapter());
		bColorTheme.setOnClickListener(bColorTheme_click);

		displayValues();

		cbTypeface.setOnItemSelectedListener(cbTypeface_itemSelected);
		sbTextSize.setOnSeekBarChangeListener(sbTextSize_seekBarChange);
		sbLineSpacing.setOnSeekBarChangeListener(sbLineSpacing_seekBarChange);
		cBold.setOnCheckedChangeListener(cBold_checkedChange);
		bClose.setOnClickListener(bClose_click);
	}
	
	public void displayValues() {
		{
			int selectedPosition = typefaceAdapter.getPositionByName(Preferences.getString(Prefkey.jenisHuruf));
			if (selectedPosition >= 0) {
				cbTypeface.setSelection(selectedPosition);
			}
		}
			
		boolean bold = Preferences.getBoolean(Prefkey.boldHuruf, false);
		cBold.setChecked(bold);
		
		float textSize = Preferences.getFloat(Prefkey.ukuranHuruf2, (float) App.context.getResources().getInteger(R.integer.pref_ukuranHuruf2_default));
		sbTextSize.setProgress((int) ((textSize - 2.f) * 2));
		displayTextSizeText(textSize);
		
		float lineSpacing = Preferences.getFloat(Prefkey.lineSpacingMult, 1.15f);
		sbLineSpacing.setProgress(Math.round((lineSpacing - 1.f) * 20.f));
		displayLineSpacingText(lineSpacing);

		final int[] currentColors = ColorThemes.getCurrentColors(Preferences.getBoolean(Prefkey.is_night_mode, false));
		bColorTheme.setColors(currentColors);
	}

	public void show() {
		if (shown) return;
		parent.addView(content);
		shown = true;
	}
	
	public void hide() {
		if (!shown) return;
		parent.removeView(content);
		shown = false;
	}
	
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == reqcodeGetFonts) {
			typefaceAdapter.reload();
			displayValues();
		} else if (requestCode == reqcodeCustomColors) {
			displayValues();
		}
	}
	
	AdapterView.OnItemSelectedListener cbTypeface_itemSelected = new AdapterView.OnItemSelectedListener() {
		@Override public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
			String name = typefaceAdapter.getNameByPosition(position);
			if (name == null) {
				activity.startActivityForResult(FontManagerActivity.createIntent(), reqcodeGetFonts);
			} else {
				Preferences.setString(Prefkey.jenisHuruf, name);
				listener.onValueChanged();
			}
		}
		
		@Override public void onNothingSelected(AdapterView<?> parent) {}
	};
	
	final View.OnClickListener bColorTheme_click = new View.OnClickListener() {
		@Override
		public void onClick(final View v) {
			final ColorThemeAdapter adapter = new ColorThemeAdapter();

			final MaterialDialog dialog = new MaterialDialog.Builder(activity)
				.adapter(adapter)
				.show();

			final ListView listView = dialog.getListView();
			if (listView == null) {
				throw new RuntimeException("ListView should not be null");
			}

			{ // set listview scrolling to the selected one
				final int[] currentColors = ColorThemes.getCurrentColors(Preferences.getBoolean(Prefkey.is_night_mode, false));
				final int position = adapter.getPositionByColors(currentColors);
				listView.setSelection(position != -1 ? position : adapter.getPositionOfCustomColors());
			}

			listView.setOnItemClickListener((parent, view, position, id) -> {
				dialog.dismiss();

				if (position == adapter.getPositionOfCustomColors()) {
					activity.startActivityForResult(ColorSettingsActivity.createIntent(Preferences.getBoolean(Prefkey.is_night_mode, false)), reqcodeCustomColors);
					return;
				}

				final int[] colors = adapter.getColorsAtPosition(position);
				ColorThemes.setCurrentColors(colors, Preferences.getBoolean(Prefkey.is_night_mode, false));
				listener.onValueChanged();
				adapter.notifyDataSetChanged();
				displayValues();
			});
		}
	};

	View.OnClickListener bClose_click = new View.OnClickListener() {
		@Override
		public void onClick(final View v) {
			listener.onCloseButtonClick();
		}
	};

	SeekBar.OnSeekBarChangeListener sbTextSize_seekBarChange = new SeekBar.OnSeekBarChangeListener() {
		@Override public void onStopTrackingTouch(SeekBar seekBar) {}
		
		@Override public void onStartTrackingTouch(SeekBar seekBar) {}
		
		@Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
			float textSize = progress * 0.5f + 2.f;
			Preferences.setFloat(Prefkey.ukuranHuruf2, textSize);
			displayTextSizeText(textSize);
			listener.onValueChanged();
		}
	};
	
	void displayTextSizeText(float textSize) {
		lTextSize.setText(String.format("%.1f", textSize));
	}
	
	SeekBar.OnSeekBarChangeListener sbLineSpacing_seekBarChange = new SeekBar.OnSeekBarChangeListener() {
		@Override public void onStopTrackingTouch(SeekBar seekBar) {}
		
		@Override public void onStartTrackingTouch(SeekBar seekBar) {}
		
		@Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
			float lineSpacing = 1.f + progress * 0.05f;
			Preferences.setFloat(Prefkey.lineSpacingMult, lineSpacing);
			displayLineSpacingText(lineSpacing);
			listener.onValueChanged();
		}
	};
	
	void displayLineSpacingText(float lineSpacing) {
		lLineSpacing.setText(String.format("%.2f", lineSpacing));
	}
	
	CompoundButton.OnCheckedChangeListener cBold_checkedChange = new CompoundButton.OnCheckedChangeListener() {
		@Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			Preferences.setBoolean(Prefkey.boldHuruf, isChecked);
			listener.onValueChanged();
		}
	};
	
	class TypefaceAdapter extends EasyAdapter {
		List<FontManager.FontEntry> fontEntries;

		public TypefaceAdapter() {
			reload();
		}
		
		public void reload() {
			fontEntries = FontManager.getInstalledFonts();
			notifyDataSetChanged();
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
				final String[] defaultFontNames = {"Roboto", "Droid Serif", "Droid Mono"};
				if (Build.VERSION.SDK_INT < 14) {
					defaultFontNames[0] = "Droid Sans";
				}

				text1.setText(defaultFontNames[position]);
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
			if (name == null) return -1;
			switch (name) {
				case "DEFAULT":
					return 0;
				case "SERIF":
					return 1;
				case "MONOSPACE":
					return 2;
				default:
					for (int i = 0; i < fontEntries.size(); i++) {
						if (fontEntries.get(i).name.equals(name)) {
							return i + 3;
						}
					}
					break;
			}
			return -1;
		}
	}

	class ColorThemeAdapter extends EasyAdapter {
		List<int[]> themes;
		List<String> themeNames;

		public ColorThemeAdapter() {
			themes = new ArrayList<>();
			for (String themeString : activity.getResources().getStringArray(R.array.pref_colorTheme_values)) {
				themes.add(ColorThemes.themeStringToColors(themeString));
			}
			themeNames = Arrays.asList(activity.getResources().getStringArray(R.array.pref_colorTheme_labels));
		}

		@Override public int getCount() {
			return themes.size() + 1;
		}
		
		@Override public View newView(int position, ViewGroup parent) {
			return inflater.inflate(android.R.layout.simple_list_item_single_choice, parent, false);
		}

		@Override public void bindView(View view, int position, ViewGroup parent) {
			final int[] currentColors = ColorThemes.getCurrentColors(Preferences.getBoolean(Prefkey.is_night_mode, false));
			final int selectedPosition = getPositionByColors(currentColors);

			final CheckedTextView text1 = (CheckedTextView) view;
			if (position != getPositionOfCustomColors()) {
				final int colors[] = themes.get(position);
				final SpannableStringBuilder sb = new SpannableStringBuilder();
				sb.append("" + (position+1));
				sb.setSpan(new ForegroundColorSpan(colors[2]), 0, sb.length(), 0);
				sb.setSpan(new VerseRenderer.VerseNumberSpan(false), 0, sb.length(), 0);
				int sb_len = sb.length();
				sb.append(" ").append(themeNames.get(position));
				sb.setSpan(new ForegroundColorSpan(colors[0]), sb_len, sb.length(), 0);
				text1.setText(sb);
				text1.setBackgroundColor(colors[1]);
				text1.setChecked(selectedPosition == position);
			} else {
				text1.setText(R.string.text_appearance_theme_custom);
				text1.setBackgroundColor(0x0);
				text1.setChecked(selectedPosition == -1);
			}
		}
		
		public int[] getColorsAtPosition(int position) {
			return themes.get(position);
		}
		
		public int getPositionByColors(int[] colors) {
			for (int i = 0; i < themes.size(); i++) {
				if (Arrays.equals(colors, themes.get(i))) {
					return i;
				}
			}
			return -1;
		}
		
		public int getPositionOfCustomColors() {
			return themes.size();
		}
	}

	static class ColorThemes {
		// text color, bg color, verse number color, red text color
		static int[] themeStringToColors(String themeString) {
			return new int[] {
			(int) Long.parseLong(themeString.substring(0, 8), 16),
			(int) Long.parseLong(themeString.substring(9, 17), 16),
			(int) Long.parseLong(themeString.substring(18, 26), 16),
			(int) Long.parseLong(themeString.substring(27, 35), 16)
			};
		}

		static int[] getCurrentColors(final boolean forNightMode) {
			final Context c = App.context;
			final Resources r = c.getResources();

			if (forNightMode) {
				return new int[] {
				Preferences.getInt(c.getString(R.string.pref_textColor_night_key), r.getInteger(R.integer.pref_textColor_night_default)),
				Preferences.getInt(c.getString(R.string.pref_backgroundColor_night_key), r.getInteger(R.integer.pref_backgroundColor_night_default)),
				Preferences.getInt(c.getString(R.string.pref_verseNumberColor_night_key), r.getInteger(R.integer.pref_verseNumberColor_night_default)),
				Preferences.getInt(c.getString(R.string.pref_redTextColor_night_key), r.getInteger(R.integer.pref_redTextColor_night_default)),
				};
			} else {
				return new int[] {
				Preferences.getInt(c.getString(R.string.pref_textColor_key), r.getInteger(R.integer.pref_textColor_default)),
				Preferences.getInt(c.getString(R.string.pref_backgroundColor_key), r.getInteger(R.integer.pref_backgroundColor_default)),
				Preferences.getInt(c.getString(R.string.pref_verseNumberColor_key), r.getInteger(R.integer.pref_verseNumberColor_default)),
				Preferences.getInt(c.getString(R.string.pref_redTextColor_key), r.getInteger(R.integer.pref_redTextColor_default)),
				};
			}
		}

		static void setCurrentColors(int[] colors, final boolean forNightMode) {
			final Context c = App.context;

			if (forNightMode) {
				Preferences.setInt(c.getString(R.string.pref_textColor_night_key), colors[0]);
				Preferences.setInt(c.getString(R.string.pref_backgroundColor_night_key), colors[1]);
				Preferences.setInt(c.getString(R.string.pref_verseNumberColor_night_key), colors[2]);
				Preferences.setInt(c.getString(R.string.pref_redTextColor_night_key), colors[3]);
			} else {
				Preferences.setInt(c.getString(R.string.pref_textColor_key), colors[0]);
				Preferences.setInt(c.getString(R.string.pref_backgroundColor_key), colors[1]);
				Preferences.setInt(c.getString(R.string.pref_verseNumberColor_key), colors[2]);
				Preferences.setInt(c.getString(R.string.pref_redTextColor_key), colors[3]);
			}
		}
	}

}
