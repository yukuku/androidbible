package yuku.alkitab.base;

import yuku.alkitab.R;
import android.app.AlertDialog;
import android.content.*;
import android.view.*;
import android.view.View.OnClickListener;
import android.widget.CheckBox;

public class PemilihStabiloDialog {
	private final AlertDialog alert;
	private PemilihStabiloCallback pemilihStabiloCallback;

	public interface PemilihStabiloCallback {
		/**
		 * @param warnaRgb -1 untuk ga terpilih
		 */
		void dipilih(int warnaRgb);
		void batal();
	}
	
	private static final int[] xid = {R.id.c1, R.id.c2, R.id.c3, R.id.c4, R.id.c5, R.id.c6};
	private static final int[] xrgb = {0xff0000, 0xffff00, 0x00ff00, 0x00ffff, 0x0000ff, 0xff00ff};

	/**
	 * @param colorRgb -1 kalo ga terpilih. #rrggbb ga pake alpha
	 */
	public PemilihStabiloDialog(Context context, PemilihStabiloCallback pemilihStabiloCallback, int colorRgb) {
		this.pemilihStabiloCallback = pemilihStabiloCallback;
		this.dialogLayout = LayoutInflater.from(context).inflate(R.layout.dialog_pemilihstabilo, null);
		
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
	
	private void bCancel_click() {
		if (pemilihStabiloCallback != null) pemilihStabiloCallback.batal();
	}

	private OnClickListener cb_click = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			for (int i = 0; i < 6; i++) {
				if (v.getId() == xid[i]) {
					if (pemilihStabiloCallback != null) pemilihStabiloCallback.dipilih(xrgb[i]);
					alert.dismiss();
				} else {
					((CheckBox) dialogLayout.findViewById(xid[i])).setChecked(false);
				}
			}
			if (v.getId() == R.id.c0) {
				if (pemilihStabiloCallback != null) pemilihStabiloCallback.dipilih(-1);
				alert.dismiss();
			} else {
				((CheckBox) dialogLayout.findViewById(R.id.c0)).setChecked(false);
			}
		}
	};
	private View dialogLayout;

	public void show() {
		alert.show();
	}
}
