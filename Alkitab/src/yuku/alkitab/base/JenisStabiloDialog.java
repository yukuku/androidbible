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
	
	View dialogLayout;
	
	static final int[] xid = {R.id.c1, R.id.c2, R.id.c3, R.id.c4, R.id.c5, R.id.c6};
	static final int[] xrgb = {0xff0000, 0xffff00, 0x00ff00, 0x00ffff, 0x0000ff, 0xff00ff};

	public interface JenisStabiloCallback {
		/**
		 * @param warnaRgb -1 untuk ga terpilih
		 */
		void dipilih(int warnaRgb);
		void batal();
	}

	/**
	 * @param colorRgb -1 kalo ga terpilih. #rrggbb ga pake alpha
	 */
	public JenisStabiloDialog(Context context, JenisStabiloCallback jenisStabiloCallback, int colorRgb) {
		this.jenisStabiloCallback = jenisStabiloCallback;
		this.dialogLayout = LayoutInflater.from(context).inflate(R.layout.dialog_stabilo_ubah, null);
		
		this.alert = new AlertDialog.Builder(context)
		.setView(dialogLayout)
		.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				bCancel_click();
			}
		})
		.create();

		for (int i = 0; i < 6; i++) {
			CheckBox cb = (CheckBox) dialogLayout.findViewById(xid[i]);
			if (colorRgb == xrgb[i]) {
				cb.setChecked(true);
			}
			cb.setOnClickListener(cb_click);
		}
		
		CheckBox cb = (CheckBox) dialogLayout.findViewById(R.id.c0);
		if (colorRgb == -1) {
			cb.setChecked(true);
		}
		cb.setOnClickListener(cb_click);
	}
	
	void bCancel_click() {
		if (jenisStabiloCallback != null) jenisStabiloCallback.batal();
	}

	private OnClickListener cb_click = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			for (int i = 0; i < 6; i++) {
				if (v.getId() == xid[i]) {
					if (jenisStabiloCallback != null) jenisStabiloCallback.dipilih(xrgb[i]);
					alert.dismiss();
				} else {
					((CheckBox) dialogLayout.findViewById(xid[i])).setChecked(false);
				}
			}
			if (v.getId() == R.id.c0) {
				if (jenisStabiloCallback != null) jenisStabiloCallback.dipilih(-1);
				alert.dismiss();
			} else {
				((CheckBox) dialogLayout.findViewById(R.id.c0)).setChecked(false);
			}
		}
	};

	public void show() {
		alert.show();
	}
}
