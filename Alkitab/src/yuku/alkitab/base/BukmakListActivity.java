package yuku.alkitab.base;

import android.app.*;
import android.content.*;
import android.database.*;
import android.os.*;
import android.provider.*;
import android.text.*;
import android.text.style.*;
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

import yuku.alkitab.R;
import yuku.alkitab.base.JenisBukmakDialog.Listener;
import yuku.alkitab.base.model.*;
import yuku.alkitab.base.storage.*;
import yuku.andoutil.*;
import yuku.devoxx.flowlayout.*;

public class BukmakListActivity extends ListActivity {
	public static final String TAG = BukmakListActivity.class.getSimpleName();
	
    // out
	public static final String EXTRA_ariTerpilih = "ariTerpilih"; //$NON-NLS-1$

    // in
    private static final String EXTRA_filter_jenis = "filter_jenis";
    private static final String EXTRA_filter_labelId = "filter_labelId";

    public static final int LABELID_noLabel = -1;

	CursorAdapter adapter;
	Cursor cursor;

    int filter_jenis;
    long filter_labelId;

    public static Intent createIntent(Context context, int filter_jenis, long filter_labelId) {
    	Intent res = new Intent(context, BukmakListActivity.class);
    	res.putExtra(EXTRA_filter_jenis, filter_jenis);
    	res.putExtra(EXTRA_filter_labelId, filter_labelId);
    	return res;
    }
    
    @Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		S.siapinKitab();
		S.bacaPengaturan(this);
		S.siapinPengirimFidbek(this);
		
		setContentView(R.layout.activity_bukmaklist);

        filter_jenis = getIntent().getIntExtra(EXTRA_filter_jenis, 0);
        filter_labelId = getIntent().getLongExtra(EXTRA_filter_labelId, 0);

        {
            String title = null;

            // atur judul berdasarkan filter
            if (filter_jenis == Db.Bukmak2.jenis_catatan) {
                title = "Notes";
            } else if (filter_jenis == Db.Bukmak2.jenis_stabilo) {
                title = "Highlightings";
            } else if (filter_jenis == Db.Bukmak2.jenis_bukmak) {
                if (filter_labelId == 0) {
                    title = "All bookmarks";
                } else if (filter_labelId == LABELID_noLabel) {
                    title = "All bookmarks without labels";
                } else {
                    Label label = S.getDb().getLabelById(filter_labelId);
                    if (label != null) {
                        title = "Bookmarks labeled '" + label.judul + "'";
                    }
                }
            }

            if (title != null) {
                setTitle(title);
            } else {
                finish();
                return;
            }
        }

		cursor = S.getDb().listBukmak(filter_jenis, filter_labelId);
		startManagingCursor(cursor);
		
		final int col__id = cursor.getColumnIndexOrThrow(BaseColumns._ID);
		final int col_ari = cursor.getColumnIndexOrThrow(Db.Bukmak2.ari);
		final int col_tulisan = cursor.getColumnIndexOrThrow(Db.Bukmak2.tulisan);
		final int col_waktuUbah = cursor.getColumnIndexOrThrow(Db.Bukmak2.waktuUbah);
		
		adapter = new CursorAdapter(this, cursor, false) {
			@Override public View newView(Context context, Cursor cursor, ViewGroup parent) {
				return getLayoutInflater().inflate(R.layout.item_bukmak, null);
			}
			
			@Override public void bindView(View view, Context context, Cursor cursor) {
				TextView lTanggal = U.getView(view, R.id.lTanggal);
				TextView lTulisan = U.getView(view, R.id.lTulisan);
				TextView lCuplikan = U.getView(view, R.id.lCuplikan);
				FlowLayout panelLabels = U.getView(view, R.id.panelLabels);
				
				lTanggal.setText(Sqlitil.toLocaleDateMedium(cursor.getInt(col_waktuUbah)));
				PengaturTampilan.aturTampilanTeksTanggalBukmak(lTanggal);
				
				int ari = cursor.getInt(col_ari);
				Kitab kitab = S.edisiAktif.getKitab(Ari.toKitab(ari));
				String alamat = S.alamat(S.edisiAktif, ari);
				
				String isi = S.muatSatuAyat(S.edisiAktif, kitab, Ari.toPasal(ari), Ari.toAyat(ari));
				isi = U.buangKodeKusus(isi);
				
				String tulisan = cursor.getString(col_tulisan);
				
				if (filter_jenis == Db.Bukmak2.jenis_bukmak) {
					lTulisan.setText(tulisan);
					PengaturTampilan.aturTampilanTeksJudulBukmak(lTulisan);
					PengaturTampilan.aturIsiDanTampilanCuplikanBukmak(lCuplikan, alamat, isi);
					
					long _id = cursor.getLong(col__id);
					List<Label> labels = S.getDb().listLabels(_id);
					if (labels != null && labels.size() != 0) {
						panelLabels.setVisibility(View.VISIBLE);
						panelLabels.removeAllViews();
						for (int i = 0, len = labels.size(); i < len; i++) {
							panelLabels.addView(getLabelView(panelLabels, labels.get(i)));
						}
					} else {
						panelLabels.setVisibility(View.GONE);
					}
					
				} else if (filter_jenis == Db.Bukmak2.jenis_catatan) {
					lTulisan.setText(alamat);
					PengaturTampilan.aturTampilanTeksJudulBukmak(lTulisan);
					lCuplikan.setText(tulisan);
					PengaturTampilan.aturTampilanTeksIsi(lCuplikan);
					
				} else if (filter_jenis == Db.Bukmak2.jenis_stabilo) {
					lTulisan.setText(alamat);
					PengaturTampilan.aturTampilanTeksJudulBukmak(lTulisan);
					
					SpannableStringBuilder cuplikan = new SpannableStringBuilder(isi);
					int warnaStabilo = U.dekodStabilo(tulisan);
					if (warnaStabilo != -1) {
						cuplikan.setSpan(new BackgroundColorSpan(0x80000000 | warnaStabilo), 0, cuplikan.length(), 0);
					}
					lCuplikan.setText(cuplikan);
					PengaturTampilan.aturTampilanTeksIsi(lCuplikan);
				}
			}
		};
		setListAdapter(adapter);

		ListView listView = getListView();
		listView.setBackgroundColor(S.penerapan.warnaLatar);
		listView.setCacheColorHint(S.penerapan.warnaLatar);
		listView.setFastScrollEnabled(true);

		registerForContextMenu(listView);
	}
	
	protected View getLabelView(FlowLayout panelLabels, Label label) {
		View res = LayoutInflater.from(this).inflate(R.layout.label, null);
		res.setLayoutParams(panelLabels.generateDefaultLayoutParams());
		
		TextView lJudul = U.getView(res, R.id.lJudul);
		lJudul.setText(label.judul);
		
		return res;
	}


	private void bikinMenu(Menu menu) {
		menu.clear();
		new MenuInflater(this).inflate(R.menu.activity_bukmak, menu);
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

					new AlertDialog.Builder(BukmakListActivity.this)
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
				dialog = new ProgressDialog(BukmakListActivity.this);
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
				
				adapter.getCursor().requery();
			}
		}.execute((Boolean)tumpuk);
	}
	
	public void ekspor() {
		new AsyncTask<Void, Integer, Object>() {
			ProgressDialog dialog;
			
			@Override
			protected void onPreExecute() {
				dialog = new ProgressDialog(BukmakListActivity.this);
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
		}.execute((Void[])null);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		Cursor o = (Cursor) adapter.getItem(position);
		int ari = o.getInt(o.getColumnIndexOrThrow(Db.Bukmak2.ari));
		
		Intent res = new Intent();
		res.putExtra(EXTRA_ariTerpilih, ari);
		
		setResult(RESULT_OK, res);
		finish();
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		new MenuInflater(this).inflate(R.menu.context_bukmaklist, menu);
	}
	
	@Override public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();

		if (item.getItemId() == R.id.menuHapusBukmak) {
			S.getDb().hapusBukmakById(menuInfo.id);
			adapter.getCursor().requery();
			
			return true;
		} else if (item.getItemId() == R.id.menuUbahKeteranganBukmak) {
			JenisBukmakDialog dialog = new JenisBukmakDialog(this, menuInfo.id);
			dialog.setListener(new Listener() {
				@Override
				public void onOk() {
					adapter.getCursor().requery();
				}
			});
			dialog.bukaDialog();
			
			return true;
		}
		
		return false;
	}
}
