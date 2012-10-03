package yuku.alkitab.base.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;

import java.util.List;

import yuku.afw.V;
import yuku.alkitab.base.S;
import yuku.alkitab.base.model.Label;
import yuku.devoxx.flowlayout.R;

public class LabelEditorDialog {
	public static final String TAG = LabelEditorDialog.class.getSimpleName();
	
	public interface OkListener {
		void onOk(String judul);
	}
	
	public static void show(Context context, String initialText, String title, final OkListener okListener) {
		View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_label, null);
		final EditText tJudul = V.get(dialogView, R.id.tJudul);
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
		
		dialog.getWindow().setLayout(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
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
