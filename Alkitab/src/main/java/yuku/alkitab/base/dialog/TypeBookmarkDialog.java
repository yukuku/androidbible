package yuku.alkitab.base.dialog;

import android.app.Dialog;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import com.afollestad.materialdialogs.MaterialDialog;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.widget.MaterialDialogAdapterHelper;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Label;
import yuku.alkitab.model.Marker;
import yuku.devoxx.flowlayout.FlowLayout;

import java.util.Date;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

public class TypeBookmarkDialog {
	public interface Listener {
		/** Called when this dialog is closed with the bookmark modified or deleted */
		void onModifiedOrDeleted();
	}

	final Context context;
	final Dialog dialog;
	FlowLayout panelLabels;
	EditText tCaption;

	Marker marker;
	int ariForNewBookmark;
	int verseCountForNewBookmark;
	String defaultCaption;

	// optional
	Listener listener;

	// current labels (can be not in the db)
	SortedSet<Label> labels = new TreeSet<>();

	/**
	 * Open the bookmark edit dialog, editing existing bookmark.
	 * @param context Activity context to create dialogs
	 */
	public static TypeBookmarkDialog EditExisting(Context context, long _id) {
		return new TypeBookmarkDialog(context, S.getDb().getMarkerById(_id), null);
	}

	/**
	 * Open the bookmark edit dialog for a new bookmark by ari.
	 */
	public static TypeBookmarkDialog NewBookmark(Context context, int ari, final int verseCount) {
		final TypeBookmarkDialog res = new TypeBookmarkDialog(context, null, S.activeVersion().referenceWithVerseCount(ari, verseCount));
		res.ariForNewBookmark = ari;
		res.verseCountForNewBookmark = verseCount;
		return res;
	}

	private TypeBookmarkDialog(final Context context, final Marker marker, String reference) {
		this.context = context;
		this.marker = marker;

		if (reference == null) {
			reference = S.activeVersion().referenceWithVerseCount(marker.ari, marker.verseCount);
		}
		defaultCaption = reference;

		View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_bookmark, null);
		this.panelLabels = dialogView.findViewById(R.id.panelLabels);

		tCaption = dialogView.findViewById(R.id.tCaption);
		final Button bAddLabel = dialogView.findViewById(R.id.bAddLabel);

		bAddLabel.setOnClickListener(v -> MaterialDialogAdapterHelper.show(new MaterialDialog.Builder(context).title(R.string.add_label_title), new LabelAdapter()));

		if (marker != null) {
			labels = new TreeSet<>();
			final List<Label> ll = S.getDb().listLabelsByMarker(marker);
			labels.addAll(ll);
		}
		setLabelsText();

		tCaption.setText(marker != null? marker.caption: reference);

		this.dialog = new MaterialDialog.Builder(context)
			.customView(dialogView, false)
			.title(reference)
			.iconRes(R.drawable.ic_attr_bookmark)
			.positiveText(R.string.ok)
			.onPositive((dialog, which) -> bOk_click())
			.neutralText(R.string.delete)
			.onNeutral((dialog, which) -> bDelete_click(marker))
			.show();
	}

	void bOk_click() {
		String caption = tCaption.getText().toString();

		// If there is no caption, show reference
		if (caption.length() == 0 || caption.trim().length() == 0) {
			caption = defaultCaption;
		}

		final Date now = new Date();
		if (marker != null) { // update existing
			marker.caption = caption;
			marker.modifyTime = now;
			S.getDb().insertOrUpdateMarker(marker);
		} else { // add new
			marker = S.getDb().insertMarker(ariForNewBookmark, Marker.Kind.bookmark, caption, verseCountForNewBookmark, now, now);
		}

		S.getDb().updateLabels(marker, labels);

		if (listener != null) listener.onModifiedOrDeleted();
	}

	public void show() {
		dialog.show();
	}

	public void setListener(Listener listener) {
		this.listener = listener;
	}
	
	private View.OnClickListener label_click = new View.OnClickListener() {
		@Override public void onClick(View v) {
			final Label label = (Label) v.getTag(R.id.TAG_label);
			if (label == null) return;

			new MaterialDialog.Builder(context)
				.content(context.getString(R.string.do_you_want_to_remove_the_label_label_from_this_bookmark, label.title))
				.positiveText(R.string.ok)
				.onPositive((dialog, which) -> {
					labels.remove(label);
					setLabelsText();
				})
				.negativeText(R.string.cancel)
				.show();
		}
	};

	protected void bDelete_click(final Marker marker) {
		if (marker == null) {
			return; // bookmark not saved, so no need to confirm
		}

		new MaterialDialog.Builder(context)
			.content(R.string.bookmark_delete_confirmation)
			.positiveText(R.string.delete)
			.onPositive((dialog, which) -> {
				S.getDb().deleteMarkerById(marker._id);

				if (listener != null) listener.onModifiedOrDeleted();
			})
			.negativeText(R.string.cancel)
			.show();
	}

	void setLabelsText() {
		// remove all first
		final int childCount = panelLabels.getChildCount();
		if (childCount > 1) {
			panelLabels.removeViews(1, childCount - 2);
		}
		
		int pos = 1;
		for (Label label: labels) {
			panelLabels.addView(getLabelView(label, panelLabels), pos++);
		}
	}
	
	private View getLabelView(Label label, final ViewGroup parent) {
		TextView res = (TextView) LayoutInflater.from(context).inflate(R.layout.label_x, parent, false);
		res.setLayoutParams(this.panelLabels.generateDefaultLayoutParams());
		res.setText(label.title);
		res.setTag(R.id.TAG_label, label);
		res.setOnClickListener(label_click);
        final Drawable drawableRight = res.getCompoundDrawables()[2];
        final int labelColor = U.applyLabelColor(label, res);
        if (drawableRight != null && labelColor != 0) {
            drawableRight.mutate();
            drawableRight.setColorFilter(labelColor, PorterDuff.Mode.MULTIPLY);
        }
        return res;
    }

	static class LabelHolder extends RecyclerView.ViewHolder {
		final TextView text1;

		public LabelHolder(final View itemView) {
			super(itemView);

			text1 = itemView.findViewById(android.R.id.text1);
		}
	}

	class LabelAdapter extends MaterialDialogAdapterHelper.Adapter {
		private List<Label> availableLabels = S.getDb().listAllLabels();

		@Override
		public int getItemCount() {
			return 1 + availableLabels.size();
		}

		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
			return new LabelHolder(LayoutInflater.from(parent.getContext()).inflate(viewType == 0 ? R.layout.item_label_chooser : android.R.layout.simple_list_item_1, parent, false));
		}

		@Override
		public void onBindViewHolder(final RecyclerView.ViewHolder _holder_, final int position) {
			final int type = getItemViewType(position);

			final LabelHolder holder = (LabelHolder) _holder_;

			{
				if (type == 0) {
					final Label label = availableLabels.get(position - 1);
					holder.text1.setText(label.title);
					U.applyLabelColor(label, holder.text1);
				} else {
					holder.text1.setText(context.getString(R.string.create_label_titik3));
				}
			}

			holder.itemView.setOnClickListener(v -> {
				dismissDialog();

				final int which = holder.getAdapterPosition();
				if (which == 0) { // new label
					LabelEditorDialog.show(context, "", context.getString(R.string.create_label_title), title -> {
						final Label newLabel = S.getDb().insertLabel(title, null);
						if (newLabel != null) {
							labels.add(newLabel);
							setLabelsText();
						}
					});
				} else {
					final Label label = availableLabels.get(which - 1);
					labels.add(label);
					setLabelsText();
				}
			});
		}

		@Override
		public int getItemViewType(final int position) {
			if (position == 0) return 1;
			return 0;
		}
	}
}
