package yuku.alkitab.base.dialog;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Build.VERSION;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.util.Date;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import yuku.afw.V;
import yuku.alkitab.R;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.compat.Api11;
import yuku.alkitab.base.dialog.LabelEditorDialog.OkListener;
import yuku.alkitab.base.model.Bookmark2;
import yuku.alkitab.base.model.Label;
import yuku.alkitab.base.storage.Db;
import yuku.devoxx.flowlayout.FlowLayout;

public class TypeBookmarkDialog {
	public interface Listener {
		void onOk();
	}
	
	final Context context;
	FlowLayout panelLabels;
	LabelAdapter adapter;

	// init ini...
	String alamat = null;
	int ari = 0;
	//... atau ini
	long id = -1;
	
	// optional
	Listener listener;

	// current labels (can be not in the db)
	SortedSet<Label> labels = new TreeSet<Label>();
	
	public TypeBookmarkDialog(Context context, CharSequence alamat, int ari) {
		// wajib
		this.context = context;
		
		// pilihan
		this.alamat = alamat.toString();
		this.ari = ari;
	}

	public TypeBookmarkDialog(Context context, long id) {
		// wajib
		this.context = context;

		// pilihan
		this.alamat = null;
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
						Label labelBaru = S.getDb().tambahLabel(judul, null);
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
			.setTitle(R.string.remove_label_title)
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
		final Bookmark2 bukmak = this.ari == 0? S.getDb().getBukmakById(id): S.getDb().getBukmakByAri(ari, Db.Bookmark2.kind_bookmark);
		
		// set yang belum diset
		if (this.ari == 0 && bukmak != null) {
			this.ari = bukmak.ari;
			this.alamat = S.reference(S.activeVersion, bukmak.ari);
		}
		
		View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_bookmark, null);
		this.panelLabels = V.get(dialogView, R.id.panelLabels);
		
		final EditText tTulisan = V.get(dialogView, R.id.tTulisan);
		final Button bAddLabel = V.get(dialogView, R.id.bAddLabel);
		
		bAddLabel.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) {
				adapter = new LabelAdapter();
				
				AlertDialog.Builder b = new AlertDialog.Builder(context)
				.setTitle(R.string.add_label_title)
				.setAdapter(adapter, bAddLabel_dialog_itemSelected)
				.setNegativeButton(R.string.cancel, null);
				
				if (VERSION.SDK_INT >= 11) {
					adapter.setDialogContext(Api11.AlertDialog_Builder_getContext(b));
				}
				
				b.show();
			}
		});
		
		if (bukmak != null) {
			labels = new TreeSet<Label>();
			List<Label> ll = S.getDb().getLabels(bukmak._id);
			if (ll != null) labels.addAll(ll);
		}
		setLabelsText();
		
		tTulisan.setText(bukmak != null? bukmak.caption: alamat);
		
		new AlertDialog.Builder(context)
		.setView(dialogView)
		.setTitle(alamat)
		.setIcon(R.drawable.attribute_type_bookmark)
		.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			@Override public void onClick(DialogInterface dialog, int which) {
				Bookmark2 bukmakGaFinal = bukmak;
				String tulisan = tTulisan.getText().toString();
				
				// kalo ga ada tulisan, kasi alamat aja.
				if (tulisan.length() == 0 || tulisan.trim().length() == 0) {
					tulisan = alamat;
				}
				
				if (bukmakGaFinal != null) {
					bukmakGaFinal.caption = tulisan;
					bukmakGaFinal.modifyTime = new Date();
					S.getDb().updateBukmak(bukmakGaFinal);
				} else {
					bukmakGaFinal = S.getDb().insertBukmak(ari, Db.Bookmark2.kind_bookmark, tulisan, new Date(), new Date());
				}
				
				if (bukmakGaFinal != null) {
					S.getDb().updateLabels(bukmakGaFinal, labels);
				}
				
				if (listener != null) listener.onOk();
			}
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
		res.setOnClickListener(lJudul_click);
		
		U.applyLabelColor(label, res);
		
		return res;
	}

	class LabelAdapter extends BaseAdapter {
		private List<Label> labels;
		private Context dialogContext;

		public LabelAdapter() {
			labels = S.getDb().getAllLabels();
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
				
				// for API 10 or lower, forcefully set text color
				if (VERSION.SDK_INT <= 10) {
					text1.setTextColor(0xff000000);
				}
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
