package yuku.alkitab.base.widget;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.support.annotation.NonNull;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.widget.RecyclerView;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CheckedTextView;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import com.afollestad.materialdialogs.MaterialDialog;
import yuku.afw.App;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.base.S;
import yuku.alkitab.base.ac.ColorSettingsActivity;
import yuku.alkitab.base.ac.FontManagerActivity;
import yuku.alkitab.base.model.PerVersionSettings;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.FontManager;
import yuku.alkitab.debug.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class TextAppearancePanel {
	public static final String TAG = TextAppearancePanel.class.getSimpleName();

	public interface Listener {
		void onValueChanged();
		void onCloseButtonClick();
	}
	
	final Activity activity;
	final FrameLayout parent;
	final Listener listener;
	final View content;
	final int reqcodeGetFonts;
	final int reqcodeCustomColors;
	
	Spinner cbTypeface;
	TextView lTextSize;
	SeekBar sbTextSize;
	View panelPerVersionTextSize;
	TextView lTextSizeLabel;
	TextView lTextSizePerVersion;
	SeekBar sbTextSizePerVersion;
	TextView lLineSpacing;
	SeekBar sbLineSpacing;
	CheckBox cBold;
	MultiColorView bColorTheme;
	View bClose;

	TypefaceAdapter typefaceAdapter;
	boolean shown = false;
	String splitVersionId;
	String splitVersionLongName;

	public TextAppearancePanel(Activity activity, FrameLayout parent, Listener listener, int reqcodeGetFonts, int reqcodeCustomColors) {
		this.activity = activity;
		this.parent = parent;
		this.listener = listener;
		this.reqcodeGetFonts = reqcodeGetFonts;
		this.reqcodeCustomColors = reqcodeCustomColors;
		this.content = activity.getLayoutInflater().inflate(R.layout.panel_text_appearance, parent, false);

		this.content.setOnTouchListener((v, event) -> true); // prevent click-through
	    
	    cbTypeface = V.get(content, R.id.cbTypeface);
	    cBold = V.get(content, R.id.cBold);
	    lTextSize = V.get(content, R.id.lTextSize);
	    sbTextSize = V.get(content, R.id.sbTextSize);
		panelPerVersionTextSize = V.get(content, R.id.panelPerVersionTextSize);
		lTextSizeLabel = V.get(content, R.id.lTextSizeLabel);
		lTextSizePerVersion = V.get(content, R.id.lTextSizePerVersion);
		sbTextSizePerVersion = V.get(content, R.id.sbTextSizePerVersion);
	    lLineSpacing = V.get(content, R.id.lLineSpacing);
	    sbLineSpacing = V.get(content, R.id.sbLineSpacing);
	    bColorTheme = V.get(content, R.id.bColorTheme);
		bClose = V.get(content, R.id.bClose);

		cbTypeface.setAdapter(typefaceAdapter = new TypefaceAdapter());
		bColorTheme.setOnClickListener(bColorTheme_click);

		displayValues();

		cbTypeface.setOnItemSelectedListener(cbTypeface_itemSelected);
		sbTextSize.setOnSeekBarChangeListener(sbTextSize_seekBarChange);
		sbTextSizePerVersion.setOnSeekBarChangeListener(sbTextSizePerVersion_seekBarChange);
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

		if (splitVersionId == null) {
			panelPerVersionTextSize.setVisibility(View.GONE);
		} else {
			panelPerVersionTextSize.setVisibility(View.VISIBLE);

			lTextSizeLabel.setText(TextUtils.expandTemplate(activity.getText(R.string.text_appearance_text_size_for_version), splitVersionLongName));

			final PerVersionSettings settings = S.getDb().getPerVersionSettings(splitVersionId);
			sbTextSizePerVersion.setProgress(Math.round((settings.fontSizeMultiplier - 0.5f) * 20.f));
			displayTextSizePerVersionText(settings.fontSizeMultiplier);
		}

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

			final MaterialDialog dialog = MaterialDialogAdapterHelper.show(new MaterialDialog.Builder(activity), adapter);

			final RecyclerView recyclerView = dialog.getRecyclerView();

			{ // scroll to the selected one
				final int[] currentColors = ColorThemes.getCurrentColors(Preferences.getBoolean(Prefkey.is_night_mode, false));
				final int position = adapter.getPositionByColors(currentColors);
				recyclerView.getLayoutManager().scrollToPosition(position != -1 ? position : adapter.getPositionOfCustomColors());
			}
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
	
	SeekBar.OnSeekBarChangeListener sbTextSizePerVersion_seekBarChange = new SeekBar.OnSeekBarChangeListener() {
		@Override public void onStopTrackingTouch(SeekBar seekBar) {}

		@Override public void onStartTrackingTouch(SeekBar seekBar) {}

		@Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
			if (splitVersionId == null) return;

			float textSizeMult = progress * 0.05f + 0.5f;
			final PerVersionSettings settings = S.getDb().getPerVersionSettings(splitVersionId);
			settings.fontSizeMultiplier = textSizeMult;
			S.getDb().storePerVersionSettings(splitVersionId, settings);

			displayTextSizePerVersionText(textSizeMult);
			listener.onValueChanged();
		}
	};

	void displayTextSizeText(float textSize) {
		lTextSize.setText(String.format(Locale.US, "%.1f", textSize));
	}
	
	void displayTextSizePerVersionText(float textSizeMult) {
		lTextSizePerVersion.setText(Math.round(textSizeMult * 100) + "%");
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
		lLineSpacing.setText(String.format(Locale.US, "%.2f", lineSpacing));
	}
	
	CompoundButton.OnCheckedChangeListener cBold_checkedChange = new CompoundButton.OnCheckedChangeListener() {
		@Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			Preferences.setBoolean(Prefkey.boldHuruf, isChecked);
			listener.onValueChanged();
		}
	};

	public void setSplitVersion(@NonNull final String splitVersionId, @NonNull final String splitVersionLongName) {
		this.splitVersionId = splitVersionId;
		this.splitVersionLongName = splitVersionLongName;
		displayValues();
	}

	public void clearSplitVersion() {
		this.splitVersionId = this.splitVersionLongName = null;
		displayValues();
	}
	
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
			return activity.getLayoutInflater().inflate(android.R.layout.simple_list_item_1, parent, false);
		}

		@Override public void bindView(View view, int position, ViewGroup parent) {
			final TextView text1 = V.get(view, android.R.id.text1);
			text1.setLines(1); // do not wrap long font names
			text1.setEllipsize(TextUtils.TruncateAt.END);
			
			if (position < 3) {
				final String[] defaultFontNames = {"Roboto", "Droid Serif", "Droid Mono"};

				text1.setText(defaultFontNames[position]);
				text1.setTypeface(new Typeface[] {Typeface.SANS_SERIF, Typeface.SERIF, Typeface.MONOSPACE}[position]);
			} else if (position == getCount() - 1) {
				final SpannableStringBuilder sb = new SpannableStringBuilder(activity.getText(R.string.get_more_fonts));
				sb.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(activity.getResources(), R.color.escape, activity.getTheme())), 0, sb.length(), 0);
				text1.setText(sb);
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

	static class ColorThemeHolder extends RecyclerView.ViewHolder {
		final CheckedTextView text1;

		public ColorThemeHolder(final View itemView) {
			super(itemView);

			text1 = V.get(itemView, android.R.id.text1);
		}
	}

	class ColorThemeAdapter extends MaterialDialogAdapterHelper.Adapter {
		List<int[]> themes;
		List<String> themeNames;

		public ColorThemeAdapter() {
			themes = new ArrayList<>();
			for (String themeString : activity.getResources().getStringArray(R.array.pref_colorTheme_values)) {
				themes.add(ColorThemes.themeStringToColors(themeString));
			}
			themeNames = Arrays.asList(activity.getResources().getStringArray(R.array.pref_colorTheme_labels));
		}

		@Override
		public int getItemCount() {
			return themes.size() + 1;
		}

		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
			return new ColorThemeHolder(activity.getLayoutInflater().inflate(android.R.layout.simple_list_item_single_choice, parent, false));
		}

		@Override
		public void onBindViewHolder(final RecyclerView.ViewHolder _holder_, final int position) {
			final ColorThemeHolder holder = (ColorThemeHolder) _holder_;

			final int[] currentColors = ColorThemes.getCurrentColors(Preferences.getBoolean(Prefkey.is_night_mode, false));
			final int selectedPosition = getPositionByColors(currentColors);

			if (position != getPositionOfCustomColors()) {
				final int colors[] = themes.get(position);
				final SpannableStringBuilder sb = new SpannableStringBuilder();
				sb.append(String.valueOf(position + 1));
				sb.setSpan(new ForegroundColorSpan(colors[2]), 0, sb.length(), 0);
				sb.setSpan(new VerseRenderer.VerseNumberSpan(false), 0, sb.length(), 0);
				int sb_len = sb.length();
				sb.append(" ").append(themeNames.get(position));
				sb.setSpan(new ForegroundColorSpan(colors[0]), sb_len, sb.length(), 0);
				holder.text1.setText(sb);
				holder.text1.setBackgroundColor(colors[1]);
				holder.text1.setChecked(selectedPosition == position);
			} else {
				holder.text1.setText(R.string.text_appearance_theme_custom);
				holder.text1.setBackgroundColor(0x0);
				holder.text1.setChecked(selectedPosition == -1);
			}

			holder.itemView.setOnClickListener(v -> {
				dismissDialog();

				final int which = holder.getAdapterPosition();

				if (which == getPositionOfCustomColors()) {
					activity.startActivityForResult(ColorSettingsActivity.createIntent(Preferences.getBoolean(Prefkey.is_night_mode, false)), reqcodeCustomColors);
					return;
				}

				final int[] colors = getColorsAtPosition(which);
				ColorThemes.setCurrentColors(colors, Preferences.getBoolean(Prefkey.is_night_mode, false));
				listener.onValueChanged();
				notifyDataSetChanged();
				displayValues();
			});
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
			if (forNightMode) {
				return new int[]{
					Preferences.getInt(R.string.pref_textColor_night_key, R.integer.pref_textColor_night_default),
					Preferences.getInt(R.string.pref_backgroundColor_night_key, R.integer.pref_backgroundColor_night_default),
					Preferences.getInt(R.string.pref_verseNumberColor_night_key, R.integer.pref_verseNumberColor_night_default),
					Preferences.getInt(R.string.pref_redTextColor_night_key, R.integer.pref_redTextColor_night_default),
				};
			} else {
				return new int[]{
					Preferences.getInt(R.string.pref_textColor_key, R.integer.pref_textColor_default),
					Preferences.getInt(R.string.pref_backgroundColor_key, R.integer.pref_backgroundColor_default),
					Preferences.getInt(R.string.pref_verseNumberColor_key, R.integer.pref_verseNumberColor_default),
					Preferences.getInt(R.string.pref_redTextColor_key, R.integer.pref_redTextColor_default),
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
