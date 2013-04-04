package yuku.alkitab.base.widget;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.PopupWindow;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.util.List;

import yuku.afw.App;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.R;
import yuku.alkitab.base.util.FontManager;

public class TextAppearancePopup {
	public static final String TAG = TextAppearancePopup.class.getSimpleName();
	
	final LayoutInflater inflater;
	final PopupWindow pw;
	final View root;
	
	Spinner cbTypeface;
	Button bTextSize;
	ToggleButton cBold;
	View bLineSpacing;
	ColorSelectButton bColors;

	TypefaceAdapter typefaceAdapter;

	public TextAppearancePopup(Context context, LayoutInflater inflater, View root) {
		this.inflater = inflater;
		this.root = root;
		
		View content = inflater.inflate(R.layout.popup_text_appearance, null);
		pw = new PopupWindow(content, root.getWidth(), 200, true);
	    pw.setBackgroundDrawable(new BitmapDrawable(context.getResources()));
	    
	    content.setOnKeyListener(content_key);
	    
	    cbTypeface = V.get(content, R.id.cbTypeface);
	    bTextSize = V.get(content, R.id.bTextSize);
	    cBold = V.get(content, R.id.cBold);
	    bLineSpacing = V.get(content, R.id.bLineSpacing);
	    bColors = V.get(content, R.id.bColors);
	    
	    cbTypeface.setAdapter(typefaceAdapter = new TypefaceAdapter());
	    bTextSize.setOnClickListener(bTextSize_click);
	    
	    showValues();
	}
	
	void showValues() {
		int selectedPosition = typefaceAdapter.getSelectedPositionByName(Preferences.getString(App.context.getString(R.string.pref_jenisHuruf_key)));
		if (selectedPosition >= 0) {
			cbTypeface.setSelection(selectedPosition);
		}
		float textSize = Preferences.getFloat(App.context.getString(R.string.pref_ukuranHuruf2_key), 17.f);
		bTextSize.setText(String.format("%.1f", textSize));
		boolean bold = Preferences.getBoolean(App.context.getString(R.string.pref_boldHuruf_key), false);
		cBold.setChecked(bold);
		
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
		pw.showAtLocation(root, Gravity.NO_GRAVITY, 0, root.getHeight() - 200);
	}
	
	View.OnKeyListener content_key = new View.OnKeyListener() {
		@Override public boolean onKey(View v, int keyCode, KeyEvent event) {
			if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
				pw.dismiss();
				return true;
			}
			return false;
		}
	};
	
	View.OnClickListener bTextSize_click = new View.OnClickListener() {
		@Override public void onClick(View v) {
			
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
		
		public int getSelectedPositionByName(String name) {
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
