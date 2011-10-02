package yuku.alkitab.base.dialog;

import android.app.*;
import android.content.*;
import android.content.DialogInterface.OnClickListener;
import android.text.*;
import android.view.*;
import android.view.ViewGroup.LayoutParams;
import android.widget.*;

import java.util.*;

import yuku.alkitab.base.*;
import yuku.alkitab.base.model.*;
import yuku.devoxx.flowlayout.*;

public class LabelEditorDialog {
	public static final String TAG = LabelEditorDialog.class.getSimpleName();
	
	public interface OkListener {
		void onOk(String judul);
	}
	
	public static void show(Context context, String initialText, String title, final OkListener okListener) {
		View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_label_ubah, null);
		final EditText tJudul = U.getView(dialogView, R.id.tJudul);
		tJudul.setText(initialText);
		
		final AlertDialog dialog = new AlertDialog.Builder(context)
		.setView(dialogView)
		.setTitle(title)
		.setPositiveButton(R.string.ok, new OnClickListener() {
			@Override public void onClick(DialogInterface dialog, int which) {
				if (okListener != null) {
					okListener.onOk(tJudul.getText().toString().trim());
				}
			}
		})
		.setNegativeButton(R.string.cancel, null)
		.create();
		
		dialog.getWindow().setLayout(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
		dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
		dialog.show();
		
		final Button bOk = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
		bOk.setEnabled(false);
		
		final List<Label> semuaLabel = S.getDb().listSemuaLabel();
		
		tJudul.addTextChangedListener(new TextWatcher() {
			@Override public void onTextChanged(CharSequence s, int start, int before, int count) {
			}
			
			@Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}
			
			@Override public void afterTextChanged(Editable s) {
				if (s.length() == 0 || s.toString().trim().length() == 0) {
					bOk.setEnabled(false);
					return;
				} else {
					String judulBaruTrim = s.toString().trim();
					for (Label label: semuaLabel) {
						if (label.judul.trim().equals(judulBaruTrim)) {
							bOk.setEnabled(false);
							return;
						}
					}
				}
				bOk.setEnabled(true);
			}
		});
	}
}
