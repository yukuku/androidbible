
package yuku.alkitab.base;

import android.app.*;
import android.content.*;
import android.database.*;
import android.os.*;
import android.util.*;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.*;

import java.io.*;
import java.util.*;

import org.xml.sax.*;
import org.xml.sax.ext.*;
import org.xmlpull.v1.*;

import yuku.alkitab.*;
import yuku.alkitab.base.LabelEditorDialog.OkListener;
import yuku.alkitab.base.model.*;
import yuku.alkitab.base.storage.*;

public class BukmakActivity extends ListActivity {
    // out
	public static final String EXTRA_ariTerpilih = "ariTerpilih"; //$NON-NLS-1$

	private static final int REQCODE_bukmakList = 1;

	BukmakFilterAdapter adapter;
	
	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		S.siapinKitab();
		S.bacaPengaturan();
		
		setContentView(R.layout.activity_bukmak);
		
		adapter = new BukmakFilterAdapter();
		adapter.reload();
		
		ListView listView = getListView();
		listView.setAdapter(adapter);

		registerForContextMenu(listView);
	}

	private void bikinMenu(Menu menu) {
		menu.clear();
		getMenuInflater().inflate(R.menu.activity_bukmak, menu);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		bikinMenu(menu);
		
		return true;
	}
	
	@Override
	public boolean onMenuOpened(int featureId, Menu menu) {
		if (menu != null) {
			bikinMenu(menu);
		}
		
		return true;
	}
	
	void msgbox(String title, String message) {
		new AlertDialog.Builder(this)
		.setTitle(title)
		.setMessage(message)
		.setPositiveButton(R.string.ok, null)
		.show();
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.menuImpor) {
			final File f = getFileBackup();
			
			new AlertDialog.Builder(this)
			.setTitle(R.string.impor_judul)
			.setMessage(getString(R.string.impor_pembatas_buku_dan_catatan_dari_tanya, f.getAbsolutePath()))
			.setNegativeButton(R.string.no, null)
			.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					if (!f.exists() || !f.canRead()) {
						msgbox(getString(R.string.impor_judul), getString(R.string.file_tidak_bisa_dibaca_file, f.getAbsolutePath()));
						return;
					}

					new AlertDialog.Builder(BukmakActivity.this)
					.setTitle(R.string.impor_judul)
					.setMessage(R.string.apakah_anda_mau_menumpuk_pembatas_buku_dan_catatan_tanya)
					.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							impor(false);
						}
					})
					.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							impor(true);
						}
					})
					.show();
				}
			})
			.show();
			
			return true;
		} else if (item.getItemId() == R.id.menuEkspor) {
			new AlertDialog.Builder(this)
			.setTitle(R.string.ekspor_judul)
			.setMessage(R.string.ekspor_pembatas_buku_dan_catatan_tanya)
			.setNegativeButton(R.string.no, null)
			.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					ekspor();
				}
			})
			.show();
			
			return true;
		}
		return false;
	}
	
	File getFileBackup() {
		File dir = new File(Environment.getExternalStorageDirectory(), "bible"); //$NON-NLS-1$
		if (!dir.exists()) {
			dir.mkdir();
		}
		
		return new File(dir, getPackageName() + "-backup.xml"); //$NON-NLS-1$
	}

	public void impor(boolean tumpuk) {
		new AsyncTask<Boolean, Integer, Object>() {
			ProgressDialog dialog;
			
			@Override
			protected void onPreExecute() {
				dialog = new ProgressDialog(BukmakActivity.this);
				dialog.setTitle(R.string.impor_judul);
				dialog.setMessage(getString(R.string.mengimpor_titiktiga));
				dialog.setIndeterminate(true);
				dialog.setCancelable(false);
				dialog.show();
			}
			
			@Override
			protected Object doInBackground(Boolean... params) {
				final List<Bukmak2> list = new ArrayList<Bukmak2>();
				final boolean tumpuk = params[0];
				final int[] c = new int[1];

				try {
					File in = getFileBackup();
					FileInputStream fis = new FileInputStream(in);
					
					Xml.parse(fis, Xml.Encoding.UTF_8, new DefaultHandler2() {
						@Override
						public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
							if (!localName.equals(Bukmak2.XMLTAG_Bukmak2)) {
								return;
							}
							
							Bukmak2 bukmak2 = Bukmak2.dariAttributes(attributes);
							list.add(bukmak2);
							
							c[0]++;
						}
					});
					fis.close();
				} catch (Exception e) {
					return e;
				}
				
				S.getDb().importBukmak(list, tumpuk);
			
				return c[0];
			}

			@Override
			protected void onPostExecute(Object result) {
				dialog.dismiss();
				
				if (result instanceof Integer) {
					msgbox(getString(R.string.impor_judul), getString(R.string.impor_berhasil_angka_diproses, result));
				} else if (result instanceof Exception) {
					msgbox(getString(R.string.impor_judul), getString(R.string.terjadi_kesalahan_ketika_mengimpor_pesan, ((Exception) result).getMessage()));
				}
				
				adapter.reload();
			}
		}.execute((Boolean)tumpuk);
	}
	
	public void ekspor() {
		new AsyncTask<Void, Integer, Object>() {
			ProgressDialog dialog;
			
			@Override
			protected void onPreExecute() {
				dialog = new ProgressDialog(BukmakActivity.this);
				dialog.setTitle(R.string.ekspor_judul);
				dialog.setMessage(getString(R.string.mengekspor_titiktiga));
				dialog.setIndeterminate(true);
				dialog.setCancelable(false);
				dialog.show();
			}
			
			@Override
			protected Object doInBackground(Void... params) {
				File out = getFileBackup();
				try {
					FileOutputStream fos = new FileOutputStream(out);
					
					XmlSerializer xml = Xml.newSerializer();
					xml.setOutput(fos, "utf-8"); //$NON-NLS-1$
					xml.startDocument("utf-8", null); //$NON-NLS-1$
					xml.startTag(null, "backup"); //$NON-NLS-1$
					
					Cursor cursor = S.getDb().listSemuaBukmak();
					while (cursor.moveToNext()) {
						Bukmak2.dariCursor(cursor).writeXml(xml);
					}
					cursor.close();
					
					xml.endTag(null, "backup"); //$NON-NLS-1$
					xml.endDocument();
					fos.close();

					return out.getAbsolutePath();
				} catch (Exception e) {
					return e;
				}
			}
			
			@Override
			protected void onPostExecute(Object result) {
				dialog.dismiss();
				
				if (result instanceof String) {
					msgbox(getString(R.string.ekspor_judul), getString(R.string.ekspor_berhasil_file_yang_dihasilkan_file, result));
				} else if (result instanceof Exception) {
					msgbox(getString(R.string.ekspor_judul), getString(R.string.terjadi_kesalahan_ketika_mengekspor_pesan, ((Exception) result).getMessage()));
				}
			}
		}.execute();
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		Intent intent;
		if (position == 0) {
			intent = BukmakListActivity.createIntent(this, Db.Bukmak2.jenis_bukmak, 0);
		} else if (position == 1) {
			intent = BukmakListActivity.createIntent(this, Db.Bukmak2.jenis_catatan, 0);
		} else if (position == 2) {
			intent = BukmakListActivity.createIntent(this, Db.Bukmak2.jenis_stabilo, 0);
		} else if (position == 3) {
			intent = BukmakListActivity.createIntent(this, Db.Bukmak2.jenis_bukmak, BukmakListActivity.LABELID_noLabel);
		} else {
			Label label = adapter.getItem(position);
			if (label != null) {
				intent = BukmakListActivity.createIntent(this, Db.Bukmak2.jenis_bukmak, label._id);
			} else {
				return;
			}
		}
		startActivityForResult(intent, REQCODE_bukmakList);
	}
	
	@Override public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		getMenuInflater().inflate(R.menu.context_bukmak, menu);

		MenuItem menuRenameLabel = menu.findItem(R.id.menuRenameLabel);
		MenuItem menuDeleteLabel = menu.findItem(R.id.menuDeleteLabel);

		AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
		if (info.position < 4) {
			menuRenameLabel.setEnabled(false);
			menuDeleteLabel.setEnabled(false);
		} else {
			menuRenameLabel.setEnabled(true);
			menuDeleteLabel.setEnabled(true);
		}
	}

	@Override public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();

		int itemId = item.getItemId();
		if (itemId == R.id.menuRenameLabel) {
			final Label label = adapter.getItem(info.position);
			if (label == null) {
				return true;
			}
			
			LabelEditorDialog.show(this, label.judul, "Rename label", new OkListener() {
				@Override public void onOk(String judul) {
					S.getDb().renameLabel(label, judul);
					adapter.reload();
				}
			});

			return true;
		} else if (itemId == R.id.menuDeleteLabel) {
			final Label label = adapter.getItem(info.position);
			if (label == null) {
				return true;
			}

			new AlertDialog.Builder(this)
			.setTitle("Delete label")
			.setMessage(String.format("Are you sure you want to delete the label '%s'? This label will be unassigned from all bookmarks.", label.judul))
			.setNegativeButton(R.string.cancel, null)
			.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
				@Override public void onClick(DialogInterface dialog, int which) {
					S.getDb().hapusLabelById(label._id);
					adapter.reload();
				}
			})
			.show();
			return true;
		}

		return false;
	}
	
	@Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQCODE_bukmakList) {
			if (resultCode == RESULT_OK) {
				int ari = data.getIntExtra(BukmakActivity.EXTRA_ariTerpilih, 0);
				if (ari != 0) { // 0 berarti ga ada apa2, karena ga ada pasal 0 ayat 0
					Intent res = new Intent();
					res.putExtra(EXTRA_ariTerpilih, ari);
					
					setResult(RESULT_OK, res);
					finish();
				}
			}
		}
		
		adapter.reload();
	}
	
	class BukmakFilterAdapter extends BaseAdapter {
		// 0. [icon] All bookmarks
		// 1. [icon] Notes
		// 2. [icon] Highlightings
		// 3. Unlabeled bookmarks
		// 4. dst label2
		
		List<Label> labels;
		private String[] presetCaptions = {
			"All bookmarks",
			"Notes",
			"Highlightings",
			"Unlabeled bookmarks",
		};
		
		private boolean hasLabels() {
			return labels != null && labels.size() > 0;
		}
		
		@Override public int getCount() {
			return 3 + (hasLabels()? 1 + labels.size(): 0);
		}

		@Override public Label getItem(int position) {
			if (position < 4) return null;
			return labels.get(position - 4);
		}

		@Override public long getItemId(int position) {
			return position;
		}

		@Override public View getView(int position, View convertView, ViewGroup parent) {
			View res = convertView != null? convertView: getLayoutInflater().inflate(R.layout.item_bukmakfilter, null);
			
			ImageView imgFilterIcon = U.getView(res, R.id.imgFilterIcon);
			if (position < 3) {
				imgFilterIcon.setVisibility(View.VISIBLE);
				imgFilterIcon.setImageResource(position == 0? R.drawable.jenis_bukmak: position == 1? R.drawable.jenis_catatan: position == 2? R.drawable.warnastabilo_checked: 0);
				imgFilterIcon.setBackgroundColor(position == 2? 0xffffff00: 0);
			} else {
				imgFilterIcon.setVisibility(View.GONE);
			}
			
			TextView lFilterCaption = U.getView(res, R.id.lFilterCaption);
			if (position < 4) {
				lFilterCaption.setVisibility(View.VISIBLE);
				lFilterCaption.setText(presetCaptions[position]);
			} else {
				lFilterCaption.setVisibility(View.GONE);
			}
			
			TextView lFilterLabel = U.getView(res, R.id.lFilterLabel);
			if (position < 4) {
				lFilterLabel.setVisibility(View.GONE);
			} else {
				Label label = getItem(position);
				lFilterLabel.setVisibility(View.VISIBLE);
				lFilterLabel.setText(label.judul);
			}
			
			return res;
		}
		
		private void reload() {
			labels = S.getDb().listSemuaLabel();
			notifyDataSetChanged();
		}
	}
}
