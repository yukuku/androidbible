package yuku.alkitab.base.widget;

import androidx.recyclerview.widget.RecyclerView;
import com.afollestad.materialdialogs.MaterialDialog;

public class MaterialDialogAdapterHelper {
	public static abstract class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
		private MaterialDialog dialog;

		public void setDialog(MaterialDialog dialog) {
			this.dialog = dialog;
		}

		public void dismissDialog() {
			this.dialog.dismiss();
		}
	}

	public static MaterialDialog show(MaterialDialog.Builder builder, Adapter adapter) {
		final MaterialDialog dialog = builder
			.adapter(adapter, null)
			.build();

		adapter.setDialog(dialog);

		dialog.show();

		return dialog;
	}
}
