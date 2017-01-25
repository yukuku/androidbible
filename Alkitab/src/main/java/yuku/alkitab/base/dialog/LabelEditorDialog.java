package yuku.alkitab.base.dialog;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import yuku.afw.V;
import yuku.alkitab.base.S;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Label;

import java.util.List;

public class LabelEditorDialog {
	public static final String TAG = LabelEditorDialog.class.getSimpleName();
	
	public interface OkListener {
		void onOk(String title);
	}
	
	public static void show(Context context, String initialText, String title, final OkListener okListener) {
		View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_label, null);
		final EditText tCaption = V.get(dialogView, R.id.tCaption);
		tCaption.setText(initialText);

		final MaterialDialog dialog = new MaterialDialog.Builder(context)
			.customView(dialogView, false)
			.title(title)
			.positiveText(R.string.ok)
			.negativeText(R.string.cancel)
			.callback(new MaterialDialog.ButtonCallback() {
				@Override
				public void onPositive(final MaterialDialog dialog) {
					if (okListener != null) {
						okListener.onOk(tCaption.getText().toString().trim());
					}
				}
			})
			.build();

		final Window window = dialog.getWindow();
		if (window != null) {
			window.setLayout(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
			window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
		}
		dialog.show();
		
		final View bOk = dialog.getActionButton(DialogAction.POSITIVE);
		bOk.setEnabled(false);
		
		final List<Label> allLabels = S.getDb().listAllLabels();
		
		tCaption.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void afterTextChanged(Editable s) {
				if (s.length() == 0 || s.toString().trim().length() == 0) {
					bOk.setEnabled(false);
					return;
				} else {
					String newTitleTrimmed = s.toString().trim();
					for (Label label : allLabels) {
						if (label.title.trim().equals(newTitleTrimmed)) {
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
