package yuku.alkitab.base.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;

import yuku.alkitab.R;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.util.IntArrayList;

public class JenisStabiloDialog {
	final AlertDialog alert;
	final JenisStabiloCallback callback;
	
	View dialogView;
	
	static final int[] xid = {
		R.id.c01, R.id.c02, R.id.c03, R.id.c04, R.id.c05, R.id.c06,
		R.id.c07, R.id.c08, R.id.c09, R.id.c10, R.id.c11, R.id.c12,
	};
	static final int[] xrgb = {
		0xff0000, 0xff8000, 0xffff00, 0x80ff00, 0x00ff00, 0x00ff80, 
		0x00ffff, 0x0080ff, 0x0000ff, 0x8000ff, 0xff00ff, 0xff0080,
	};
	
	final int ariKp;
	final IntArrayList ayatTerpilih;

	public interface JenisStabiloCallback {
		/**
		 * @param warnaRgb -1 untuk ga terpilih
		 */
		void onOk(int warnaRgb);
	}
	
	/**
	 * Buka dialog buat 1 ayat
	 * @param warnaRgb -1 kalo ga terpilih. #rrggbb ga pake alpha
	 */
	public JenisStabiloDialog(Context context, int ari, JenisStabiloCallback callback, int warnaRgb, CharSequence judul) {
		this(context, Ari.toKitabPasal(ari), satuSaja(Ari.toVerse(ari)), callback, warnaRgb, judul);
	}
	
	private static IntArrayList satuSaja(int ayat_1) {
		IntArrayList res = new IntArrayList(1);
		res.add(ayat_1);
		return res;
	}

	/**
	 * Buka dialog buat lebih dari 1 ayat (atau 1 ayat juga boleh).
	 * @param warnaRgb -1 kalo ga terpilih. #rrggbb ga pake alpha
	 * @param ayatTerpilih ayat2 yang dipilih.
	 */
	public JenisStabiloDialog(Context context, int ariKp, IntArrayList ayatTerpilih, JenisStabiloCallback callback, int warnaRgb, CharSequence judul) {
		this.ariKp = ariKp;
		this.ayatTerpilih = ayatTerpilih;
		this.callback = callback;
		this.dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_highlight, null);
		
		this.alert = new AlertDialog.Builder(context)
		.setView(dialogView)
		.setNegativeButton(R.string.cancel, null)
		.create();
		
		dialogView.setBackgroundColor(S.penerapan.backgroundColor);
		
		if (judul != null) {
			this.alert.setTitle(judul);
		}
		
		for (int i = 0; i < xid.length; i++) {
			CheckBox cb = U.getView(dialogView, xid[i]);
			if (warnaRgb == xrgb[i]) {
				cb.setChecked(true);
			}
			cb.setOnClickListener(cb_click);
		}
		
		CheckBox cb = U.getView(dialogView, R.id.c00);
		if (warnaRgb == -1) {
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
					U.<CheckBox>getView(dialogView, xid[i]).setChecked(false);
				}
			}
			if (v.getId() == R.id.c00) {
				select(-1);
			} else {
				U.<CheckBox>getView(dialogView, R.id.c00).setChecked(false);
			}
		}

		private void select(int warnaRgb) {
			S.getDb().updateAtauInsertStabilo(ariKp, ayatTerpilih, warnaRgb);
			if (callback != null) callback.onOk(warnaRgb);
			alert.dismiss();
		}
	};

	public void bukaDialog() {
		alert.show();
	}
}
