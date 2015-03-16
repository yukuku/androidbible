package yuku.alkitab.base.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import com.afollestad.materialdialogs.AlertDialogWrapper;
import com.afollestad.materialdialogs.MaterialDialog;
import yuku.afw.V;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
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
	final AlertDialog dialog;
	FlowLayout panelLabels;
	LabelAdapter adapter;
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
		final TypeBookmarkDialog res = new TypeBookmarkDialog(context, null, S.activeVersion.referenceWithVerseCount(ari, verseCount));
		res.ariForNewBookmark = ari;
		res.verseCountForNewBookmark = verseCount;
		return res;
	}

	private TypeBookmarkDialog(final Context context, final Marker marker, String reference) {
		this.context = context;
		this.marker = marker;

		if (reference == null) {
			reference = S.activeVersion.referenceWithVerseCount(marker.ari, marker.verseCount);
		}
		defaultCaption = reference;

		View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_bookmark, null);
		this.panelLabels = V.get(dialogView, R.id.panelLabels);

		tCaption = V.get(dialogView, R.id.tCaption);
		final Button bAddLabel = V.get(dialogView, R.id.bAddLabel);

		bAddLabel.setOnClickListener(v -> {
			adapter = new LabelAdapter();

			final MaterialDialog dialog = new MaterialDialog.Builder(context)
				.title(R.string.add_label_title)
				.adapter(adapter)
				.build();

			final ListView listView = dialog.getListView();
			if (listView == null) {
				throw new RuntimeException("ListView must not be null");
			}
			listView.setOnItemClickListener((parent, view, position, id) -> {
				if (position == 0) { // new label
					LabelEditorDialog.show(context, "", context.getString(R.string.create_label_title), title -> {
						final Label newLabel = S.getDb().insertLabel(title, null);
						if (newLabel != null) {
							labels.add(newLabel);
							setLabelsText();
						}
					});
				} else {
					final Label label = adapter.getItem(position);
					labels.add(label);
					setLabelsText();
				}
				dialog.dismiss();
			});

			adapter.setDialogContext(dialog.getContext());

			dialog.show();
		});

		if (marker != null) {
			labels = new TreeSet<>();
			final List<Label> ll = S.getDb().listLabelsByMarker(marker);
			labels.addAll(ll);
		}
		setLabelsText();

		tCaption.setText(marker != null? marker.caption: reference);

		this.dialog = new AlertDialogWrapper.Builder(context)
			.setView(dialogView)
			.setTitle(reference)
			.setIcon(R.drawable.ic_attr_bookmark)
			.setPositiveButton(R.string.ok, (dialog, which) -> bOk_click())
			.setNeutralButton(R.string.delete, (dialog, which) -> bDelete_click(marker))
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

			new AlertDialogWrapper.Builder(context)
				.setMessage(context.getString(R.string.do_you_want_to_remove_the_label_label_from_this_bookmark, label.title))
				.setPositiveButton(R.string.ok, (dialog, which) -> {
					labels.remove(label);
					setLabelsText();
				})
				.setNegativeButton(R.string.cancel, null)
				.show();
		}
	};

	protected void bDelete_click(final Marker marker) {
		if (marker == null) {
			return; // bookmark not saved, so no need to confirm
		}

		new AlertDialogWrapper.Builder(context)
			.setMessage(R.string.bookmark_delete_confirmation)
			.setPositiveButton(R.string.delete, (dialog, which) -> {
				S.getDb().deleteMarkerById(marker._id);

				if (listener != null) listener.onModifiedOrDeleted();
			})
			.setNegativeButton(R.string.cancel, null)
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
			panelLabels.addView(getLabelView(label), pos++);
		}
	}
	
	private View getLabelView(Label label) {
		TextView res = (TextView) LayoutInflater.from(context).inflate(R.layout.label_x, null);
		res.setLayoutParams(panelLabels.generateDefaultLayoutParams());
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

	class LabelAdapter extends EasyAdapter {
		private List<Label> labels;
		private Context dialogContext;

		public LabelAdapter() {
			labels = S.getDb().listAllLabels();
			dialogContext = context;
		}

		public void setDialogContext(Context dialogContext) {
			this.dialogContext = dialogContext;
		}
		
		@Override public int getCount() {
			return 1 + labels.size();
		}

		@Override public Label getItem(int position) {
			return position == 0 ? null : labels.get(position - 1);
		}

		@Override
		public View newView(final int position, final ViewGroup parent) {
			final int type = getItemViewType(position);

			return LayoutInflater.from(dialogContext).inflate(type == 0? R.layout.item_label_chooser: android.R.layout.simple_list_item_1, null);
		}

		@Override
		public void bindView(final View view, final int position, final ViewGroup parent) {
			final int type = getItemViewType(position);

			if (type == 0) {
				final TextView text1 = V.get(view, android.R.id.text1);
				final Label label = getItem(position);
				text1.setText(label.title);
				U.applyLabelColor(label, text1);
			} else {
				final TextView text1 = V.get(view, android.R.id.text1);
				text1.setText(context.getString(R.string.create_label_titik3));
			}
		}

		@Override public int getViewTypeCount() {
			return 2;
		}
		
		@Override public int getItemViewType(int position) {
			if (position == 0) return 1;
			return 0;
		}
	}
}
