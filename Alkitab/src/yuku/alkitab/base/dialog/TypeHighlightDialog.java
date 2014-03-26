package yuku.alkitab.base.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import yuku.afw.V;
import yuku.alkitab.base.S;
import yuku.alkitab.debug.R;
import yuku.alkitab.util.Ari;
import yuku.alkitab.util.IntArrayList;

public class TypeHighlightDialog {
	final AlertDialog alert;
	final Listener listener;
	
	View dialogView;
	
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
	 * @param colorRgb -1 if not selected. #rrggbb without alpha.
	 */
	public TypeHighlightDialog(Context context, int ari, Listener listener, int colorRgb, CharSequence title) {
		this(context, Ari.toBookChapter(ari), onlyOne(Ari.toVerse(ari)), listener, colorRgb, title);
	}

	private static IntArrayList onlyOne(int verse_1) {
		IntArrayList res = new IntArrayList(1);
		res.add(verse_1);
		return res;
	}

	/**
	 * Open dialog for more than one verse (or one verse only).
	 * @param colorRgb -1 if not selected. #rrggbb without alpha.
	 * @param selectedVerses selected verses.
	 */
	public TypeHighlightDialog(Context context, int ari_bookchapter, IntArrayList selectedVerses, Listener listener, int colorRgb, CharSequence title) {
		this.ari_bookchapter = ari_bookchapter;
		this.selectedVerses = selectedVerses;
		this.listener = listener;

		final AlertDialog.Builder builder = new AlertDialog.Builder(context);
		final Context contextForLayout = Build.VERSION.SDK_INT >= 11? builder.getContext(): context;

		this.dialogView = LayoutInflater.from(contextForLayout).inflate(R.layout.dialog_edit_highlight, null);

		this.alert = builder
		.setView(dialogView)
		.setIcon(R.drawable.ic_attr_highlight)
		.setNegativeButton(R.string.cancel, null)
		.create();
		
		dialogView.setBackgroundColor(S.applied.backgroundColor);
		
		if (title != null) {
			this.alert.setTitle(title);
		}
		
		for (int i = 0; i < ids.length; i++) {
			CheckBox cb = V.get(dialogView, ids[i]);
			if (colorRgb == rgbs[i]) {
				cb.setChecked(true);
			}
			cb.setOnClickListener(cb_click);
		}
		
		Button bClear = V.get(dialogView, R.id.c00);
		bClear.setOnClickListener(cb_click);
	}

	private OnClickListener cb_click = new View.OnClickListener() {
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
			if (id == R.id.c00) {
				select(-1);
			}
		}

		private void select(int colorRgb) {
			S.getDb().updateOrInsertHighlights(ari_bookchapter, selectedVerses, colorRgb);
			if (listener != null) listener.onOk(colorRgb);
			alert.dismiss();
		}
	};

	public void show() {
		alert.show();
	}
}
