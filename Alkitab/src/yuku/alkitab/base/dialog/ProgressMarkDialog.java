package yuku.alkitab.base.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.support.v4.app.DialogFragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import yuku.afw.V;
import yuku.alkitab.base.S;
import yuku.alkitab.base.widget.AttributeView;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.ProgressMark;

import java.util.Date;

public class ProgressMarkDialog extends DialogFragment {
	public static final String TAG = ProgressMarkDialog.class.getSimpleName();

	public interface Listener {
		void onOked();
		void onDeleted();
	}

	public static void showRenameDialog(final Activity activity, final ProgressMark progressMark, final Listener listener) {
		final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		final View v = LayoutInflater.from(builder.getContext()).inflate(R.layout.dialog_progress_mark_edit, null);
		final TextView tCaption = V.get(v, R.id.tCaption);

		final String caption;
		if (TextUtils.isEmpty(progressMark.caption)) {
			caption = activity.getString(AttributeView.getDefaultProgressMarkStringResource(progressMark.preset_id));
		} else {
			caption = progressMark.caption;
		}
		tCaption.setText(caption);

		builder
			.setView(v)
			.setNegativeButton(activity.getString(R.string.delete), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(final DialogInterface dialog, final int which) {
					new AlertDialog.Builder(activity)
						.setMessage(activity.getString(R.string.pm_delete_progress_confirm, progressMark.caption))
						.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(final DialogInterface dialog, final int which) {
								progressMark.ari = 0;
								progressMark.caption = null;
								progressMark.modifyTime = new Date();
								S.getDb().updateProgressMark(progressMark);

								if (listener != null) {
									listener.onDeleted();
								}

							}
						})
						.setNegativeButton(R.string.cancel, null)
						.show();
				}
			})
			.setPositiveButton(activity.getString(R.string.ok), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(final DialogInterface dialog, final int which) {
					final String name = String.valueOf(tCaption.getText());
					if (caption != null) {
						if (TextUtils.isEmpty(name.trim())) {
							progressMark.caption = null;
						} else {
							progressMark.caption = name;
						}
						progressMark.modifyTime = new Date();
						S.getDb().updateProgressMark(progressMark);
						if (listener != null) {
							listener.onOked();
						}
					}
				}
			})
			.show();
	}
}
