package yuku.alkitab.base.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Build;
import android.os.Build.VERSION;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import yuku.afw.V;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.dialog.LabelEditorDialog.OkListener;
import yuku.alkitab.base.storage.Db;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Bookmark2;
import yuku.alkitab.model.Label;
import yuku.devoxx.flowlayout.FlowLayout;

import java.util.Date;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

public class TypeBookmarkDialog {
	public interface Listener {
		void onOk();
	}
	
	final Context context;
	FlowLayout panelLabels;
	LabelAdapter adapter;

	// init this...
	String reference = null;
	int ari = 0;
	//... or this
	long id = -1;
	
	// optional
	Listener listener;

	// current labels (can be not in the db)
	SortedSet<Label> labels = new TreeSet<Label>();
	
	public TypeBookmarkDialog(Context context, String reference, int ari) {
		// required
		this.context = context;
		
		// optional
		this.reference = reference;
		this.ari = ari;
	}

	public TypeBookmarkDialog(Context context, long id) {
		// required
		this.context = context;

		// optional
		this.reference = null;
		this.id = id;
	}
	
	public void setListener(Listener listener) {
		this.listener = listener;
	}
	
	OnClickListener bAddLabel_dialog_itemSelected = new OnClickListener() {
		@Override public void onClick(DialogInterface _unused_, int which) {
			if (which == adapter.getCount() - 1) { // new label
				LabelEditorDialog.show(context, "", context.getString(R.string.create_label_title), new OkListener() { //$NON-NLS-1$
					@Override public void onOk(String judul) {
						Label labelBaru = S.getDb().insertLabel(judul, null);
						if (labelBaru != null) {
							labels.add(labelBaru);
							setLabelsText();
						}
					}
				});
			} else {
				Label label = adapter.getItem(which);
				labels.add(label);
				setLabelsText();
			}
		}
	};
	
	private View.OnClickListener lJudul_click = new View.OnClickListener() {
		@Override public void onClick(View v) {
			final Label label = (Label) v.getTag(R.id.TAG_label);
			if (label == null) return;
			
			new AlertDialog.Builder(context)
			.setMessage(context.getString(R.string.do_you_want_to_remove_the_label_label_from_this_bookmark, label.title))
			.setPositiveButton(R.string.ok, new OnClickListener() {
				@Override public void onClick(DialogInterface dialog, int which) {
					labels.remove(label);
					setLabelsText();
				}
			})
			.setNegativeButton(R.string.cancel, null)
			.show();
		}
	};

	public void show() {
		final Bookmark2 bookmark = this.ari == 0? S.getDb().getBookmarkById(id): S.getDb().getBookmarkByAri(ari, Db.Bookmark2.kind_bookmark);
		
		// set yang belum diset
		if (this.ari == 0 && bookmark != null) {
			this.ari = bookmark.ari;
			this.reference = S.activeVersion.reference(bookmark.ari);
		}

		final AlertDialog.Builder builder = new AlertDialog.Builder(context);
		final Context contextForLayout = Build.VERSION.SDK_INT >= 11? builder.getContext(): context;

		View dialogView = LayoutInflater.from(contextForLayout).inflate(R.layout.dialog_edit_bookmark, null);
		this.panelLabels = V.get(dialogView, R.id.panelLabels);
		
		final EditText tCaption = V.get(dialogView, R.id.tCaption);
		final Button bAddLabel = V.get(dialogView, R.id.bAddLabel);
		
		bAddLabel.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				adapter = new LabelAdapter();

				AlertDialog.Builder b = new AlertDialog.Builder(context)
				.setTitle(R.string.add_label_title)
				.setAdapter(adapter, bAddLabel_dialog_itemSelected)
				.setNegativeButton(R.string.cancel, null);

				adapter.setDialogContext(b.getContext());

				b.show();
			}
		});
		
		if (bookmark != null) {
			labels = new TreeSet<Label>();
			List<Label> ll = S.getDb().listLabelsByBookmarkId(bookmark._id);
			if (ll != null) labels.addAll(ll);
		}
		setLabelsText();
		
		tCaption.setText(bookmark != null? bookmark.caption: reference);
		
		builder
		.setView(dialogView)
		.setTitle(reference)
		.setIcon(R.drawable.ic_attr_bookmark)
		.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			@Override public void onClick(DialogInterface dialog, int which) {
				Bookmark2 nonfinalBookmark = bookmark;
				String caption = tCaption.getText().toString();
				
				// If there is no caption, show reference
				if (caption.length() == 0 || caption.trim().length() == 0) {
					caption = reference;
				}
				
				if (nonfinalBookmark != null) {
					nonfinalBookmark.caption = caption;
					nonfinalBookmark.modifyTime = new Date();
					S.getDb().updateBookmark(nonfinalBookmark);
				} else {
					nonfinalBookmark = S.getDb().insertBookmark(ari, Db.Bookmark2.kind_bookmark, caption, new Date(), new Date());
				}
				
				if (nonfinalBookmark != null) {
					S.getDb().updateLabels(nonfinalBookmark, labels);
				}
				
				if (listener != null) listener.onOk();
			}
		})
		.setNegativeButton(R.string.delete, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				bDelete_click(bookmark);
			}
		})
		.show();
	}

	protected void bDelete_click(final Bookmark2 bookmark) {
		if (bookmark == null) {
			return; // bookmark not saved, so no need to confirm
		}

		new AlertDialog.Builder(context)
		.setMessage(R.string.bookmark_delete_confirmation)
		.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				S.getDb().deleteBookmarkById(bookmark._id);

				if (listener != null) listener.onOk();
			}
		})
		.setNegativeButton(R.string.no, null)
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
		res.setOnClickListener(lJudul_click);
		
		U.applyLabelColor(label, res);
		
		return res;
	}

	class LabelAdapter extends BaseAdapter {
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
			return labels.size() + 1;
		}

		@Override public Label getItem(int position) {
			return (position < 0 || position >= labels.size())? null: labels.get(position);
		}

		@Override public long getItemId(int position) {
			return position;
		}

		@Override public View getView(int position, View convertView, ViewGroup parent) {
			int type = getItemViewType(position);
			View res = convertView != null? convertView: LayoutInflater.from(dialogContext).inflate(type == 0? R.layout.item_label_chooser: android.R.layout.simple_list_item_1, null);

			if (type == 0) {
				TextView text1 = V.get(res, android.R.id.text1);
				Label label = getItem(position);
				text1.setText(label.title);
				U.applyLabelColor(label, text1);
			} else {
				TextView text1 = V.get(res, android.R.id.text1);
				text1.setText(context.getString(R.string.create_label_titik3));
			}
			
			return res;
		}
		
		@Override public int getViewTypeCount() {
			return 2;
		}
		
		@Override public int getItemViewType(int position) {
			if (position == getCount() - 1) return 1;
			return 0;
		}
	}
}
