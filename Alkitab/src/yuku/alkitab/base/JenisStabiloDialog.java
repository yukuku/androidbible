package yuku.alkitab.base;

import android.app.*;
import android.content.*;
import android.view.*;
import android.view.View.OnClickListener;
import android.widget.*;

import yuku.alkitab.*;
import yuku.alkitab.base.model.*;
import yuku.andoutil.*;

public class JenisStabiloDialog {
	final AlertDialog alert;
	final JenisStabiloCallback callback;
	
	View dialogView;
	
	static final int[] xid = {R.id.c1, R.id.c2, R.id.c3, R.id.c4, R.id.c5, R.id.c6};
	static final int[] xrgb = {0xff0000, 0xffff00, 0x00ff00, 0x00ffff, 0x0000ff, 0xff00ff};
	
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
		this(context, Ari.toKitabPasal(ari), satuSaja(Ari.toAyat(ari)), callback, warnaRgb, judul);
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
		this.dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_stabilo_ubah, null);
		
		this.alert = new AlertDialog.Builder(context)
		.setView(dialogView)
		.setNegativeButton(R.string.cancel, null)
		.create();
		
		if (judul != null) {
			this.alert.setTitle(judul);
		}
		
		for (int i = 0; i < 6; i++) {
			CheckBox cb = U.getView(dialogView, xid[i]);
			if (warnaRgb == xrgb[i]) {
				cb.setChecked(true);
			}
			cb.setOnClickListener(cb_click);
		}
		
		CheckBox cb = U.getView(dialogView, R.id.c0);
		if (warnaRgb == -1) {
			cb.setChecked(true);
		}
		cb.setOnClickListener(cb_click);
	}

	private OnClickListener cb_click = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			for (int i = 0; i < 6; i++) {
				if (v.getId() == xid[i]) {
					select(xrgb[i]);
				} else {
					U.<CheckBox>getView(dialogView, xid[i]).setChecked(false);
				}
			}
			if (v.getId() == R.id.c0) {
				select(-1);
			} else {
				U.<CheckBox>getView(dialogView, R.id.c0).setChecked(false);
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
