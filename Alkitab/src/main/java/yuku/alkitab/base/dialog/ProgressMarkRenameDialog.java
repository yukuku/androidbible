package yuku.alkitab.base.dialog;

import android.app.Activity;
import android.content.Intent;
import android.support.v4.app.DialogFragment;
import android.text.TextUtils;
import android.widget.TextView;
import com.afollestad.materialdialogs.AlertDialogWrapper;
import com.afollestad.materialdialogs.MaterialDialog;
import yuku.afw.V;
import yuku.alkitab.base.App;
import yuku.alkitab.base.IsiActivity;
import yuku.alkitab.base.S;
import yuku.alkitab.base.widget.AttributeView;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.ProgressMark;

import java.util.Date;

public class ProgressMarkRenameDialog extends DialogFragment {
	public static final String TAG = ProgressMarkRenameDialog.class.getSimpleName();

	public interface Listener {
		void onOked();
		void onDeleted();
	}

	public static void show(final Activity activity, final ProgressMark progressMark, final Listener listener) {
		final String caption;
		if (TextUtils.isEmpty(progressMark.caption)) {
			caption = activity.getString(AttributeView.getDefaultProgressMarkStringResource(progressMark.preset_id));
		} else {
			caption = progressMark.caption;
		}

		final MaterialDialog dialog = new MaterialDialog.Builder(activity)
			.customView(R.layout.dialog_progress_mark_edit, false)
			.positiveText(R.string.ok)
			.neutralText(R.string.delete)
			.callback(new MaterialDialog.ButtonCallback() {
				@Override
				public void onPositive(final MaterialDialog dialog) {
					final TextView tCaption = V.get(dialog.getCustomView(), R.id.tCaption);

					final String name = String.valueOf(tCaption.getText());
					if (TextUtils.isEmpty(name.trim())) {
						progressMark.caption = null;
					} else {
						progressMark.caption = name;
					}
					progressMark.modifyTime = new Date();
					S.getDb().insertOrUpdateProgressMark(progressMark);

					// Since updating database is the responsibility here,
					// announcing it will also be here.
					App.getLbm().sendBroadcast(new Intent(IsiActivity.ACTION_ATTRIBUTE_MAP_CHANGED));

					if (listener != null) {
						listener.onOked();
					}
				}

				@Override
				public void onNeutral(final MaterialDialog dialog) {
					final String caption = progressMark.caption != null ? progressMark.caption : activity.getString(AttributeView.getDefaultProgressMarkStringResource(progressMark.preset_id));
					new AlertDialogWrapper.Builder(activity)
						.setMessage(TextUtils.expandTemplate(activity.getText(R.string.pm_delete_progress_confirm), caption))
						.setPositiveButton(R.string.ok, (_unused_, which) -> {
							progressMark.ari = 0;
							progressMark.caption = null;
							progressMark.modifyTime = new Date();
							S.getDb().insertOrUpdateProgressMark(progressMark);

							// Since updating database is the responsibility here,
							// announcing it will also be here.
							App.getLbm().sendBroadcast(new Intent(IsiActivity.ACTION_ATTRIBUTE_MAP_CHANGED));

							if (listener != null) {
								listener.onDeleted();
							}
						})
						.setNegativeButton(R.string.cancel, null)
						.show();
				}
			})
			.show();

		final TextView tCaption = V.get(dialog.getCustomView(), R.id.tCaption);
		tCaption.setText(caption);
	}
}
