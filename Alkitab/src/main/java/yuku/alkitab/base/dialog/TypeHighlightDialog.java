package yuku.alkitab.base.dialog;

import android.content.Context;
import androidx.annotation.Nullable;
import android.text.InputFilter;
import android.text.Selection;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import com.afollestad.materialdialogs.MaterialDialog;
import yuku.alkitab.base.S;
import yuku.alkitab.base.util.Highlights;
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
	TextView tVerseText;

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
	public TypeHighlightDialog(Context context, int ari, Listener listener, int defaultColorRgb, @Nullable Highlights.Info info, CharSequence title, @Nullable final CharSequence verseText) {
		this(context, Ari.toBookChapter(ari), onlyOne(Ari.toVerse(ari)), listener, defaultColorRgb, info, title, verseText);
	}

	private static IntArrayList onlyOne(int verse_1) {
		IntArrayList res = new IntArrayList(1);
		res.add(verse_1);
		return res;
	}

	/**
	 * Open dialog for more than one verse (no partial highlight support)
	 * @param defaultColorRgb -1 if not selected. #rrggbb without alpha.
	 * @param selectedVerses selected verses.
	 */
	public TypeHighlightDialog(final Context context, int ari_bookchapter, IntArrayList selectedVerses, Listener listener, final int defaultColorRgb, CharSequence title) {
		this(context, ari_bookchapter, selectedVerses, listener, defaultColorRgb, null, title, null);
	}

	private TypeHighlightDialog(final Context context, int ari_bookchapter, IntArrayList selectedVerses, Listener listener, final int defaultColorRgb, @Nullable final Highlights.Info info, CharSequence title, @Nullable final CharSequence verseText) {
		this.ari_bookchapter = ari_bookchapter;
		this.selectedVerses = selectedVerses;
		this.listener = listener;
		this.verseText = verseText;

		final MaterialDialog.Builder builder = new MaterialDialog.Builder(context)
			.customView(R.layout.dialog_edit_highlight, false)
			.iconRes(R.drawable.ic_attr_highlight)
			positiveButton(R.string.ok) // this does not actually do anything except closing the dialog.
			.neutralText(R.string.delete)
			.onPositive((dialog1, which) -> {
				// only relevant when we edit partial highlight
				if (verseText == null || info == null) {
					return;
				}

				final int[] offsets = getSelectionOffsets();
				assert offsets != null;

				// check for changes
				if ((info.partial == null && (offsets[0] != 0 || offsets[1] != verseText.length()))
					||
					(info.partial != null && (info.partial.startOffset != offsets[0] || info.partial.endOffset != offsets[1]))) {
					select(defaultColorRgb, offsets);
				}
			})
			.onNeutral((dialog1, which) -> select(-1, null));

		if (title != null) {
			builder.title(title);
		}

		dialog = builder.show();
		dialogView = dialog.getCustomView();
		dialogView.setBackgroundColor(S.applied().backgroundColor);

		for (int i = 0; i < ids.length; i++) {
			CheckBox cb = dialogView.findViewById(ids[i]);
			if (defaultColorRgb == rgbs[i]) {
				cb.setChecked(true);
			}
			cb.setOnClickListener(cb_click);
		}

		final Button bOtherColors = dialogView.findViewById(R.id.bOtherColors);
		bOtherColors.setOnClickListener(v -> {
			// save the selection first
			final int[] offsets = getSelectionOffsets();

			new AmbilWarnaDialog(context, defaultColorRgb == -1 ? 0xff000000 : defaultColorRgb, new AmbilWarnaDialog.OnAmbilWarnaListener() {
				@Override
				public void onCancel(final AmbilWarnaDialog dialog) {
				}

				@Override
				public void onOk(final AmbilWarnaDialog dialog, final int color) {
					select(0x00ffffff & color, offsets);
				}
			}).show();
		});

		tVerseText = dialogView.findViewById(R.id.tVerseText);

		if (selectedVerses.size() > 1 || verseText == null) {
			tVerseText.setVisibility(View.GONE);
		} else {
			tVerseText.setVisibility(View.VISIBLE);
			tVerseText.setText(verseText, TextView.BufferType.EDITABLE);
			tVerseText.setTextColor(S.applied().fontColor);

			if (info == null || !info.shouldRenderAsPartialForVerseText(verseText)) {
				Selection.setSelection(tVerseText.getEditableText(), 0, tVerseText.length());
			} else {
				Selection.setSelection(tVerseText.getEditableText(), info.partial.startOffset, info.partial.endOffset);
			}

			tVerseText.postDelayed(() -> tVerseText.scrollTo(0, 0), 100);

			// prevent typing
			final InputFilter[] originalFilters = tVerseText.getFilters();
			final InputFilter[] filters = new InputFilter[originalFilters.length + 1];
			System.arraycopy(originalFilters, 0, filters, 0, originalFilters.length);
			filters[originalFilters.length] = (source, start, end, dest, dstart, dend) -> dest.subSequence(dstart, dend);
			tVerseText.setFilters(filters);
		}
	}

	private int[] getSelectionOffsets() {
		if (verseText == null) {
			return null;
		} else {
			return new int[]{tVerseText.getSelectionStart(), tVerseText.getSelectionEnd()};
		}
	}

	View.OnClickListener cb_click = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			final int id = v.getId();
			for (int i = 0; i < ids.length; i++) {
				if (id == ids[i]) {
					select(rgbs[i], getSelectionOffsets());
				} else {
					((CheckBox) dialogView.findViewById(ids[i])).setChecked(false);
				}
			}
		}
	};

	void select(final int colorRgb, final int[] offsets) {
		if (selectedVerses.size() == 1 && verseText != null && colorRgb != -1 && offsets != null && (offsets[0] != 0 || offsets[1] != verseText.length()) && offsets[0] != offsets[1]) {
			// On some devices, startOffset can be > endOffset.
			final int startOffset, endOffset;
			if (offsets[0] > offsets[1]) {
				startOffset = offsets[1];
				endOffset = offsets[0];
			} else {
				startOffset = offsets[0];
				endOffset = offsets[1];
			}

			S.getDb().updateOrInsertPartialHighlight(Ari.encodeWithBc(ari_bookchapter, selectedVerses.get(0)), colorRgb, verseText, startOffset, endOffset);
		} else { // not partial, or deleting
			S.getDb().updateOrInsertHighlights(ari_bookchapter, selectedVerses, colorRgb);
		}

		if (listener != null) listener.onOk(colorRgb);
		dialog.dismiss();
	}
}
