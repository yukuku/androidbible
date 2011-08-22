package yuku.alkitab.base;

import android.app.*;
import android.content.*;
import android.content.DialogInterface.OnClickListener;
import android.text.*;
import android.view.*;
import android.view.ViewGroup.LayoutParams;
import android.widget.*;

import java.util.*;

import yuku.alkitab.base.model.*;
import yuku.alkitab.base.storage.*;
import yuku.devoxx.flowlayout.*;

public class BukmakEditor {
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
	
	public BukmakEditor(Context context, String alamat, int ari) {
		// wajib
		this.context = context;
		
		// pilihan
		this.alamat = alamat;
		this.ari = ari;
	}

	public BukmakEditor(Context context, long id) {
		// wajib
		this.context = context;

		// pilihan
		this.alamat = null;
		this.id = id;
	}
	
	public void setListener(Listener listener) {
		this.listener = listener;
	}
	
	private OnClickListener bAddLabel_dialog_itemSelected = new OnClickListener() {
		@Override public void onClick(DialogInterface _unused_, int which) {
			if (which == adapter.getCount() - 1) { // new label
				View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_label_ubah, null);
				final EditText tJudul = U.getView(dialogView, R.id.tJudul);
				
				final AlertDialog dialog = new AlertDialog.Builder(context)
				.setView(dialogView)
				.setPositiveButton(R.string.ok, new OnClickListener() {
					@Override public void onClick(DialogInterface dialog, int which) {
						Label labelBaru = S.getDb().tambahLabel(tJudul.getText().toString());
						if (labelBaru != null) {
							labels.add(labelBaru);
							setLabelsText();
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
							for (Label label: semuaLabel) {
								if (label.judul.equals(s.toString())) {
									bOk.setEnabled(false);
									return;
								}
							}
						}
						bOk.setEnabled(true);
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
			.setTitle("Remove label")
			.setMessage(String.format("Do you want to remove the label '%s' from this bookmark?", label.judul))
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

	public void bukaDialog() {
		final Bukmak2 bukmak = this.ari == 0? S.getDb().getBukmakById(id): S.getDb().getBukmakByAri(ari, yuku.alkitab.base.storage.Db.Bukmak2.jenis_bukmak);
		
		// set yang belum diset
		if (this.ari == 0 && bukmak != null) {
			this.ari = bukmak.ari;
			this.alamat = S.alamat(S.edisiAktif, bukmak.ari);
		}
		
		View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_bukmak_ubah, null);
		this.panelLabels = U.getView(dialogView, R.id.panelLabels);
		
		final EditText tTulisan = U.getView(dialogView, R.id.tTulisan);
		final Button bAddLabel = U.getView(dialogView, R.id.bAddLabel);
		
		bAddLabel.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) {
				adapter = new LabelAdapter();
				
				new AlertDialog.Builder(context)
				.setTitle("Add label")
				.setItems(adapter.getItems(), bAddLabel_dialog_itemSelected)
				.setNegativeButton(R.string.cancel, null)
				.show();
			}
		});
		
		if (bukmak != null) {
			labels = new TreeSet<Label>(S.getDb().listLabels(bukmak._id));
		}
		setLabelsText();
		
		tTulisan.setText(bukmak != null? bukmak.tulisan: alamat);
		
		new AlertDialog.Builder(context)
		.setView(dialogView)
		.setTitle(alamat)
		.setIcon(R.drawable.bukmak)
		.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			@Override public void onClick(DialogInterface dialog, int which) {
				Bukmak2 bukmakGaFinal = bukmak;
				String tulisan = tTulisan.getText().toString();
				
				// kalo ga ada tulisan, kasi alamat aja.
				if (tulisan.length() == 0 || tulisan.trim().length() == 0) {
					tulisan = alamat;
				}
				
				if (bukmakGaFinal != null) {
					bukmakGaFinal.tulisan = tulisan;
					bukmakGaFinal.waktuUbah = new Date();
					S.getDb().updateBukmak(bukmakGaFinal);
				} else {
					bukmakGaFinal = S.getDb().insertBukmak(ari, Db.Bukmak2.jenis_bukmak, tulisan, new Date(), new Date());
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
	
	private void setLabelsText() {
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
		View res = LayoutInflater.from(context).inflate(R.layout.label_x, null);
		res.setLayoutParams(panelLabels.generateDefaultLayoutParams());
		
		TextView lJudul = U.getView(res, R.id.lJudul);
		lJudul.setText(label.judul);
		
		res.setTag(R.id.TAG_label, label);
		res.setOnClickListener(lJudul_click );
		
		return res;
	}

	class LabelAdapter extends BaseAdapter {
		private List<Label> labels;

		public LabelAdapter() {
			labels = S.getDb().listSemuaLabel();
		}
		
		public CharSequence[] getItems() {
			String[] res = new String[getCount()];
			for (int i = 0, len = labels.size(); i < len; i++) {
				res[i] = labels.get(i).judul;
			}
			res[res.length - 1] = "New label\u2026";
			return res;
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
			View res = convertView != null? convertView: LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, null);

			TextView text1 = U.getView(res, android.R.id.text1); 
			setText1(text1, position);
			
			return res;
		}

		private void setText1(TextView text1, int position) {
			if (position == getCount() - 1) {
				text1.setText("New label\u2026");
			} else {
				Label label = getItem(position);
				text1.setText(label.judul);
			}
		}
	}
}
