package yuku.alkitab.base.dialog;

import android.content.Context;
import android.support.annotation.Nullable;
import android.text.InputFilter;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import com.afollestad.materialdialogs.MaterialDialog;
import yuku.afw.V;
import yuku.alkitab.base.S;
import yuku.alkitab.debug.R;
import yuku.alkitab.util.Ari;
import yuku.alkitab.util.IntArrayList;
import yuku.ambilwarna.AmbilWarnaDialog;

public class TypeHighlightDialog {
	final MaterialDialog dialog;
	final Listener listener;

	@Nullable
	private final CharSequence verseText;

	View dialogView;
	EditText tVerseText;

	static final int[] ids = {
		R.id.c01, R.id.c02, R.id.c03, R.id.c04, R.id.c05, R.id.c06,
		R.id.c07, R.id.c08, R.id.c09, R.id.c10, R.id.c11, R.id.c12,
	};
	static final int[] rgbs = {
		0xff0000, 0xff8000, 0xffff00, 0x80ff00, 0x00ff00, 0x00ff80,
		0x00ffff, 0x0080ff, 0x0000ff, 0x8000ff, 0xff00ff, 0xff0080,
	};

	final int ari_bookchapter;
	final IntArrayList selectedVerses;

	public interface Listener {
		/**
		 * @param colorRgb -1 if not colored
		 */
		void onOk(int colorRgb);
	}
	
	/**
	 * Open dialog for a single verse
	 * @param defaultColorRgb -1 if not selected. #rrggbb without alpha.
	 */
	public TypeHighlightDialog(Context context, int ari, Listener listener, int defaultColorRgb, CharSequence title, @Nullable final CharSequence verseText) {
		this(context, Ari.toBookChapter(ari), onlyOne(Ari.toVerse(ari)), listener, defaultColorRgb, title, verseText);
	}

	private static IntArrayList onlyOne(int verse_1) {
		IntArrayList res = new IntArrayList(1);
		res.add(verse_1);
		return res;
	}

	/**
	 * Open dialog for more than one verse (or one verse only).
	 * @param defaultColorRgb -1 if not selected. #rrggbb without alpha.
	 * @param selectedVerses selected verses.
	 */
	public TypeHighlightDialog(final Context context, int ari_bookchapter, IntArrayList selectedVerses, Listener listener, final int defaultColorRgb, CharSequence title, @Nullable final CharSequence verseText) {
		this.ari_bookchapter = ari_bookchapter;
		this.selectedVerses = selectedVerses;
		this.listener = listener;
		this.verseText = verseText;

		final MaterialDialog.Builder builder = new MaterialDialog.Builder(context)
			.customView(R.layout.dialog_edit_highlight, false)
			.iconRes(R.drawable.ic_attr_highlight)
			.positiveText(R.string.ok) // this does not actually do anything except closing the dialog.
			.neutralText(R.string.delete)
			.callback(new MaterialDialog.ButtonCallback() {
				@Override
				public void onNeutral(final MaterialDialog dialog) {
					select(-1);
				}
			});

		if (title != null) {
			builder.title(title);
		}

		dialog = builder.show();
		dialogView = dialog.getCustomView();
		dialogView.setBackgroundColor(S.applied.backgroundColor);

		for (int i = 0; i < ids.length; i++) {
			CheckBox cb = V.get(dialogView, ids[i]);
			if (defaultColorRgb == rgbs[i]) {
				cb.setChecked(true);
			}
			cb.setOnClickListener(cb_click);
		}
		
		final Button bOtherColors = V.get(dialogView, R.id.bOtherColors);
		bOtherColors.setOnClickListener(v -> new AmbilWarnaDialog(context, defaultColorRgb == -1 ? 0xff000000 : defaultColorRgb, new AmbilWarnaDialog.OnAmbilWarnaListener() {
			@Override
			public void onCancel(final AmbilWarnaDialog dialog) {
			}

			@Override
			public void onOk(final AmbilWarnaDialog dialog, final int color) {
				select(0x00ffffff & color);
			}
		}).show());

		tVerseText = V.get(dialogView, R.id.tVerseText);

		if (selectedVerses.size() > 1 || verseText == null) {
			tVerseText.setVisibility(View.GONE);
		} else {
			tVerseText.setVisibility(View.VISIBLE);
			tVerseText.setText(verseText);
			tVerseText.setTextColor(S.applied.fontColor);
			tVerseText.setSelection(0, tVerseText.length());
			tVerseText.postDelayed(() -> tVerseText.scrollTo(0, 0), 100);

			// prevent typing
			final InputFilter[] originalFilters = tVerseText.getFilters();
			final InputFilter[] filters = new InputFilter[originalFilters.length + 1];
			System.arraycopy(originalFilters, 0, filters, 0, originalFilters.length);
			filters[originalFilters.length] = (source, start, end, dest, dstart, dend) -> dest.subSequence(dstart, dend);
			tVerseText.setFilters(filters);
		}
	}

	View.OnClickListener cb_click = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			final int id = v.getId();
			for (int i = 0; i < ids.length; i++) {
				if (id == ids[i]) {
					select(rgbs[i]);
				} else {
					V.<CheckBox>get(dialogView, ids[i]).setChecked(false);
				}
			}
		}
	};

	void select(int colorRgb) {
		if (selectedVerses.size() == 1 && verseText != null && colorRgb != -1 && (tVerseText.getSelectionStart() != 0 || tVerseText.getSelectionEnd() != verseText.length())) {
			S.getDb().updateOrInsertPartialHighlight(Ari.encodeWithBc(ari_bookchapter, selectedVerses.get(0)), colorRgb, verseText, tVerseText.getSelectionStart(), tVerseText.getSelectionEnd());
		} else { // not partial, or deleting
			S.getDb().updateOrInsertHighlights(ari_bookchapter, selectedVerses, colorRgb);
		}

		if (listener != null) listener.onOk(colorRgb);
		dialog.dismiss();
	}
}
