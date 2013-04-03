package yuku.alkitab.base.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;

import yuku.afw.V;
import yuku.alkitab.R;
import yuku.alkitab.base.S;
import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.util.IntArrayList;

public class TypeHighlightDialog {
	final AlertDialog alert;
	final Listener listener;
	
	View dialogView;
	
	static final int[] xid = {
		R.id.c01, R.id.c02, R.id.c03, R.id.c04, R.id.c05, R.id.c06,
		R.id.c07, R.id.c08, R.id.c09, R.id.c10, R.id.c11, R.id.c12,
	};
	static final int[] xrgb = {
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
	 * Buka dialog buat 1 ayat
	 * @param colorRgb -1 kalo ga terpilih. #rrggbb ga pake alpha
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
	 * Buka dialog buat lebih dari 1 ayat (atau 1 ayat juga boleh).
	 * @param colorRgb -1 kalo ga terpilih. #rrggbb ga pake alpha
	 * @param selectedVerses ayat2 yang dipilih.
	 */
	public TypeHighlightDialog(Context context, int ari_bookchapter, IntArrayList selectedVerses, Listener listener, int colorRgb, CharSequence title) {
		this.ari_bookchapter = ari_bookchapter;
		this.selectedVerses = selectedVerses;
		this.listener = listener;
		this.dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_highlight, null);
		
		this.alert = new AlertDialog.Builder(context)
		.setView(dialogView)
		.setNegativeButton(R.string.cancel, null)
		.create();
		
		dialogView.setBackgroundColor(S.applied.backgroundColor);
		
		if (title != null) {
			this.alert.setTitle(title);
		}
		
		for (int i = 0; i < xid.length; i++) {
			CheckBox cb = V.get(dialogView, xid[i]);
			if (colorRgb == xrgb[i]) {
				cb.setChecked(true);
			}
			cb.setOnClickListener(cb_click);
		}
		
		CheckBox cb = V.get(dialogView, R.id.c00);
		if (colorRgb == -1) {
			cb.setChecked(true);
		}
		cb.setOnClickListener(cb_click);
	}

	private OnClickListener cb_click = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			for (int i = 0; i < xid.length; i++) {
				if (v.getId() == xid[i]) {
					select(xrgb[i]);
				} else {
					V.<CheckBox>get(dialogView, xid[i]).setChecked(false);
				}
			}
			if (v.getId() == R.id.c00) {
				select(-1);
			} else {
				V.<CheckBox>get(dialogView, R.id.c00).setChecked(false);
			}
		}

		private void select(int warnaRgb) {
			S.getDb().updateOrInsertHighlights(ari_bookchapter, selectedVerses, warnaRgb);
			if (listener != null) listener.onOk(warnaRgb);
			alert.dismiss();
		}
	};

	public void show() {
		alert.show();
	}
}
