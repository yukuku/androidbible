package yuku.alkitab.base;

import android.app.*;
import android.content.*;
import android.database.*;
import android.os.*;
import android.provider.*;
import android.text.*;
import android.text.style.*;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.*;

import java.util.*;

import yuku.alkitab.R;
import yuku.alkitab.base.JenisBukmakDialog.Listener;
import yuku.alkitab.base.JenisCatatanDialog.RefreshCallback;
import yuku.alkitab.base.JenisStabiloDialog.JenisStabiloCallback;
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
	int sortColumnIdKini;

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
		S.bacaPengaturan();
		
		setContentView(R.layout.activity_bukmaklist);

        filter_jenis = getIntent().getIntExtra(EXTRA_filter_jenis, 0);
        filter_labelId = getIntent().getLongExtra(EXTRA_filter_labelId, 0);

        {
            String title = null;
            String nothingText = null;

            // atur judul berdasarkan filter
            if (filter_jenis == Db.Bukmak2.jenis_catatan) {
                title = "Notes";
                nothingText = "No notes written yet.\nLong-press a verse to write a note.";
            } else if (filter_jenis == Db.Bukmak2.jenis_stabilo) {
                title = "Highlightings";
                nothingText = "No highlighted verses.\nLong-press a verse to highlight it.";
            } else if (filter_jenis == Db.Bukmak2.jenis_bukmak) {
                if (filter_labelId == 0) {
                    title = "All bookmarks";
                    nothingText = getString(R.string.belum_ada_pembatas_buku);
                } else if (filter_labelId == LABELID_noLabel) {
                    title = "All bookmarks without labels";
                    nothingText = "There are no bookmarks without any labels";
                } else {
                    Label label = S.getDb().getLabelById(filter_labelId);
                    if (label != null) {
                        title = "Bookmarks labeled '" + label.judul + "'";
                        nothingText = "There are no bookmarks with the label '" + label.judul + "'";
                    }
                }
            }

            if (title != null && nothingText != null) {
                setTitle(title);
                TextView empty = U.getView(this, android.R.id.empty);
                empty.setText(nothingText);
            } else {
                finish();
                return;
            }
        }

		gantiCursor(Db.Bukmak2.waktuTambah, false, R.string.menuSortWaktuTambah);
		
		final int col__id = cursor.getColumnIndexOrThrow(BaseColumns._ID);
		final int col_ari = cursor.getColumnIndexOrThrow(Db.Bukmak2.ari);
		final int col_tulisan = cursor.getColumnIndexOrThrow(Db.Bukmak2.tulisan);
		final int col_waktuTambah = cursor.getColumnIndexOrThrow(Db.Bukmak2.waktuTambah);
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
				
				{
					int waktuTambah_i = cursor.getInt(col_waktuTambah);
					int waktuUbah_i = cursor.getInt(col_waktuUbah);
					
					if (waktuTambah_i == waktuUbah_i) {
						lTanggal.setText(Sqlitil.toLocaleDateMedium(waktuTambah_i));
					} else {
						lTanggal.setText(getString(R.string.waktuTambah_edited_waktuUbah, Sqlitil.toLocaleDateMedium(waktuTambah_i), Sqlitil.toLocaleDateMedium(waktuUbah_i)));
					}
					
					PengaturTampilan.aturTampilanTeksTanggalBukmak(lTanggal);
				}
				
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

	private void gantiCursor(String sortColumn, boolean ascending, int sortColumnId) {
		if (cursor != null) {
			stopManagingCursor(cursor);
		}
		
		cursor = S.getDb().listBukmak(filter_jenis, filter_labelId, sortColumn, ascending);
		sortColumnIdKini = sortColumnId;
		startManagingCursor(cursor);
		
		if (adapter != null) {
			adapter.changeCursor(cursor);
		}
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
		new MenuInflater(this).inflate(R.menu.activity_bukmaklist, menu);
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
	
	@Override public boolean onOptionsItemSelected(MenuItem item) {
		int itemId = item.getItemId();
		
		switch (itemId) {
		case R.id.menuSort:
			openSortDialog();
			return true;
		}
		
		return false;
	}

	private void openSortDialog() {
		List<String> labels = new ArrayList<String>();
		final IntArrayList values = new IntArrayList();
		
		labels.add(getString(R.string.menuSortAri));
		values.add(R.string.menuSortAri);
		
		if (filter_jenis == Db.Bukmak2.jenis_bukmak) {
			labels.add(getString(R.string.menuSortTulisan));
			values.add(R.string.menuSortTulisan);
		} else if (filter_jenis == Db.Bukmak2.jenis_catatan) {
			// nop
		} else if (filter_jenis == Db.Bukmak2.jenis_stabilo) {
			labels.add(getString(R.string.menuSortTulisan_warna));
			values.add(R.string.menuSortTulisan);
		}
		
		labels.add(getString(R.string.menuSortWaktuTambah));
		values.add(R.string.menuSortWaktuTambah);
		
		labels.add(getString(R.string.menuSortWaktuUbah));
		values.add(R.string.menuSortWaktuUbah);
		
		int selected = -1;
		for (int i = 0, len = values.size(); i < len; i++) {
			if (sortColumnIdKini == values.get(i)) {
				selected = i;
				break;
			}
		}
		
		new AlertDialog.Builder(this)
		.setSingleChoiceItems(labels.toArray(new String[labels.size()]), selected, new DialogInterface.OnClickListener() {
			@Override public void onClick(DialogInterface dialog, int which) {
				if (which == -1) return;
				int value = values.get(which);
				switch (value) {
				case R.string.menuSortAri:
					gantiCursor(Db.Bukmak2.ari, true, value);
					break;
				case R.string.menuSortTulisan:
					gantiCursor(Db.Bukmak2.tulisan, true, value);
					break;
				case R.string.menuSortWaktuTambah:
					gantiCursor(Db.Bukmak2.waktuTambah, false, value);
					break;
				case R.string.menuSortWaktuUbah:
					gantiCursor(Db.Bukmak2.waktuUbah, false, value);
					break;
				}
				dialog.dismiss();
			}
		})
		.setTitle(R.string.menuSort)
		.show();
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
		
		// sesuaikan string berdasarkan jenis.
		MenuItem menuHapusBukmak = menu.findItem(R.id.menuHapusBukmak);
		if (filter_jenis == Db.Bukmak2.jenis_bukmak) menuHapusBukmak.setTitle(R.string.hapus_pembatas_buku);
		if (filter_jenis == Db.Bukmak2.jenis_catatan) menuHapusBukmak.setTitle(R.string.hapus_catatan);
		if (filter_jenis == Db.Bukmak2.jenis_stabilo) menuHapusBukmak.setTitle(R.string.hapus_stabilo);

		MenuItem menuUbahBukmak = menu.findItem(R.id.menuUbahBukmak);
		if (filter_jenis == Db.Bukmak2.jenis_bukmak) menuUbahBukmak.setTitle(R.string.ubah_bukmak);
		if (filter_jenis == Db.Bukmak2.jenis_catatan) menuUbahBukmak.setTitle(R.string.ubah_catatan);
		if (filter_jenis == Db.Bukmak2.jenis_stabilo) menuUbahBukmak.setTitle(R.string.ubah_stabilo);
	}
	
	@Override public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		int itemId = item.getItemId();
		
		if (itemId == R.id.menuHapusBukmak) {
			// jenisnya apapun, cara hapusnya sama
			S.getDb().hapusBukmakById(info.id);
			adapter.getCursor().requery();
			
			return true;
		} else if (itemId == R.id.menuUbahBukmak) {
			if (filter_jenis == Db.Bukmak2.jenis_bukmak) {
				JenisBukmakDialog dialog = new JenisBukmakDialog(this, info.id);
				dialog.setListener(new Listener() {
					@Override public void onOk() {
						adapter.getCursor().requery();
					}
				});
				dialog.bukaDialog();
				
			} else if (filter_jenis == Db.Bukmak2.jenis_catatan) {
				Cursor cursor = (Cursor) adapter.getItem(info.position);
				int ari = cursor.getInt(cursor.getColumnIndexOrThrow(Db.Bukmak2.ari));
				
				JenisCatatanDialog dialog = new JenisCatatanDialog(this, S.edisiAktif.getKitab(Ari.toKitab(ari)), Ari.toPasal(ari), Ari.toAyat(ari), new RefreshCallback() {
					@Override public void udahan() {
						adapter.getCursor().requery();
					}
				});
				dialog.bukaDialog();
				
			} else if (filter_jenis == Db.Bukmak2.jenis_stabilo) {
				Cursor cursor = (Cursor) adapter.getItem(info.position);
				int ari = cursor.getInt(cursor.getColumnIndexOrThrow(Db.Bukmak2.ari));
				int warnaRgb = U.dekodStabilo(cursor.getString(cursor.getColumnIndexOrThrow(Db.Bukmak2.tulisan)));
				
				JenisStabiloDialog dialog = new JenisStabiloDialog(this, ari, new JenisStabiloCallback() {
					@Override public void onOk(int ari, int warnaRgb) {
						adapter.getCursor().requery();
					}
				}, warnaRgb);
				dialog.bukaDialog();
			}
			
			return true;
		}
		
		return false;
	}
}
