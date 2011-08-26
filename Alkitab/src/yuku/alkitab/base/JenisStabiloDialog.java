package yuku.alkitab.base;

import android.app.*;
import android.content.*;
import android.view.*;
import android.view.View.OnClickListener;
import android.widget.*;

import yuku.alkitab.*;

public class JenisStabiloDialog {
	final AlertDialog alert;
	final JenisStabiloCallback jenisStabiloCallback;
	
	View dialogView;
	
	static final int[] xid = {R.id.c1, R.id.c2, R.id.c3, R.id.c4, R.id.c5, R.id.c6};
	static final int[] xrgb = {0xff0000, 0xffff00, 0x00ff00, 0x00ffff, 0x0000ff, 0xff00ff};
	
	final int ari;

	public interface JenisStabiloCallback {
		/**
		 * @param warnaRgb -1 untuk ga terpilih
		 */
		void onOk(int ari, int warnaRgb);
	}

	/**
	 * @param warnaRgb -1 kalo ga terpilih. #rrggbb ga pake alpha
	 */
	public JenisStabiloDialog(Context context, int ari, JenisStabiloCallback jenisStabiloCallback, int warnaRgb) {
		this.ari = ari;
		this.jenisStabiloCallback = jenisStabiloCallback;
		this.dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_stabilo_ubah, null);
		
		this.alert = new AlertDialog.Builder(context)
		.setView(dialogView)
		.setNegativeButton(R.string.cancel, null)
		.create();

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
			S.getDb().updateAtauInsertStabilo(ari, warnaRgb);
			if (jenisStabiloCallback != null) jenisStabiloCallback.onOk(ari, warnaRgb);
			alert.dismiss();
		}
	};

	public void bukaDialog() {
		alert.show();
	}
}
