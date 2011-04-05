
package yuku.alkitab.base;

import java.io.*;

import org.xml.sax.*;
import org.xml.sax.ext.DefaultHandler2;
import org.xmlpull.v1.XmlSerializer;

import yuku.alkitab.R;
import yuku.alkitab.base.BukmakEditor.Listener;
import yuku.alkitab.base.model.*;
import yuku.alkitab.base.storage.AlkitabDb;
import yuku.andoutil.Sqlitil;
import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.database.Cursor;
import android.os.*;
import android.provider.BaseColumns;
import android.util.Xml;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.*;

public class BukmakActivity extends ListActivity {
	public static final String EXTRA_ariTerpilih = "ariTerpilih"; //$NON-NLS-1$

	private static final String[] cursorColumnsMapFrom = {AlkitabDb.KOLOM_Bukmak2_ari, AlkitabDb.KOLOM_Bukmak2_tulisan, AlkitabDb.KOLOM_Bukmak2_waktuUbah};
	private static final int[] cursorColumnsMapTo = {R.id.lCuplikan, R.id.lTulisan, R.id.lTanggal};
	private static final String[] cursorColumnsSelect;

	SimpleCursorAdapter adapter;
	AlkitabDb alkitabDb;
	Cursor cursor;
	Handler handler = new Handler();

	private boolean perluReloadMenuWaktuOnMenuOpened = false;
	
	static {
		cursorColumnsSelect = new String[cursorColumnsMapFrom.length+1];
		System.arraycopy(cursorColumnsMapFrom, 0, cursorColumnsSelect, 1, cursorColumnsMapFrom.length);
		cursorColumnsSelect[0] = BaseColumns._ID;
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		S.siapinEdisi(getApplicationContext());
		S.siapinKitab(getApplicationContext());
		S.bacaPengaturan(this);
		S.terapkanPengaturanBahasa(this, handler, 2);
		S.siapinPengirimFidbek(this);
		
		setContentView(R.layout.bukmak);
		setTitle(R.string.pembatas_buku);
		
		alkitabDb = AlkitabDb.getInstance(this);
		cursor = alkitabDb.getDatabase().query(AlkitabDb.TABEL_Bukmak2, cursorColumnsSelect, AlkitabDb.KOLOM_Bukmak2_jenis + "=?", new String[] {String.valueOf(AlkitabDb.ENUM_Bukmak2_jenis_bukmak)}, null, null, AlkitabDb.KOLOM_Bukmak2_waktuUbah + " desc"); //$NON-NLS-1$ //$NON-NLS-2$
		startManagingCursor(cursor);
		
		adapter = new SimpleCursorAdapter(this, R.layout.bukmak_item, cursor, cursorColumnsMapFrom, cursorColumnsMapTo);
		adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
			@Override
			public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
				if (cursorColumnsSelect[columnIndex] == AlkitabDb.KOLOM_Bukmak2_waktuUbah) { // $codepro.audit.disable stringComparison
					String text = Sqlitil.toLocaleDateMedium(cursor.getInt(columnIndex));
					
					TextView tv = (TextView) view;
					tv.setText(text);
					IsiActivity.aturTampilanTeksTanggalBukmak(tv);
					return true;
				} else if (cursorColumnsSelect[columnIndex] == AlkitabDb.KOLOM_Bukmak2_tulisan) { // $codepro.audit.disable stringComparison
					TextView tv = (TextView) view;
					
					tv.setText(cursor.getString(columnIndex));
					IsiActivity.aturTampilanTeksJudulBukmak(tv);
					return true;
				} else if (cursorColumnsSelect[columnIndex] == AlkitabDb.KOLOM_Bukmak2_ari) { // $codepro.audit.disable stringComparison
					int ari = cursor.getInt(columnIndex);
					Kitab kitab = S.edisiAktif.volatile_xkitab[Ari.toKitab(ari)];
					String[] xayat = S.muatTeks(S.edisiAktif, kitab, Ari.toPasal(ari));
					int ayat_1 = Ari.toAyat(ari);
					String isi = ayat_1 > xayat.length? "(...)": xayat[ayat_1 - 1]; //$NON-NLS-1$
					isi = U.buangKodeKusus(isi);
					
					TextView tv = (TextView) view;
					String alamat = S.alamat(S.edisiAktif, ari);
					IsiActivity.aturIsiDanTampilanCuplikanBukmak(tv, alamat, isi);
					return true;
				}
				return false;
			}
		});
		setListAdapter(adapter);

		ListView listView = getListView();
		listView.setBackgroundColor(S.penerapan.warnaLatar);
		listView.setCacheColorHint(S.penerapan.warnaLatar);
		listView.setFastScrollEnabled(true);

		registerForContextMenu(listView);
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		S.terapkanPengaturanBahasa(this, handler, 2);
		perluReloadMenuWaktuOnMenuOpened = true;
		
		super.onConfigurationChanged(newConfig);
	}

	private void bikinMenu(Menu menu) {
		menu.clear();
		new MenuInflater(this).inflate(R.menu.bukmak, menu);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		bikinMenu(menu);
		
		return true;
	}
	
	@Override
	public boolean onMenuOpened(int featureId, Menu menu) {
		if (menu != null) {
			if (perluReloadMenuWaktuOnMenuOpened) {
				bikinMenu(menu);
				perluReloadMenuWaktuOnMenuOpened = false;
			}
		}
		
		return true;
	}
	
	private void msgbox(String title, String message) {
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
	
	private File getFileBackup() {
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
				try {
					File in = getFileBackup();
					FileInputStream fis = new FileInputStream(in);
					final int[] c = new int[1];
					final boolean tumpuk = params[0];
					
					alkitabDb.getDatabase().beginTransaction();
					try {
						Xml.parse(fis, Xml.Encoding.UTF_8, new DefaultHandler2() {
							String where = AlkitabDb.KOLOM_Bukmak2_ari + "=? and " + AlkitabDb.KOLOM_Bukmak2_jenis + "=?"; //$NON-NLS-1$ //$NON-NLS-2$
							String[] plc = new String[2];
							
							@Override
							public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
								if (!localName.equals(Bukmak2.XMLTAG_Bukmak2)) {
									return;
								}
								
								Bukmak2 bukmak2 = Bukmak2.dariAttributes(attributes);
								plc[0] = String.valueOf(bukmak2.ari);
								plc[1] = String.valueOf(bukmak2.jenis);
								
								boolean ada = false;
								Cursor cursor = alkitabDb.getDatabase().query(AlkitabDb.TABEL_Bukmak2, null, where, plc, null, null, null);
								if (cursor.moveToNext()) {
									// ada, maka kita perlu hapus
									ada = true;
								}
								cursor.close();
								
								//  ada  tumpuk:     delete insert
								//  ada !tumpuk: (nop)
								// !ada  tumpuk:            insert
								// !ada !tumpuk:            insert
								
								if (ada && tumpuk) {
									alkitabDb.getDatabase().delete(AlkitabDb.TABEL_Bukmak2, where, plc);
								}
								if ((ada && tumpuk) || (!ada)) {
									alkitabDb.getDatabase().insert(AlkitabDb.TABEL_Bukmak2, null, bukmak2.toContentValues());
								}
								
								c[0]++;
							}
						});
						alkitabDb.getDatabase().setTransactionSuccessful();
					} finally {
						alkitabDb.getDatabase().endTransaction();
					}
					
					fis.close();
				
					return c[0];
				} catch (Exception e) {
					return e;
				}
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
					
					Cursor cursor = alkitabDb.getDatabase().query(AlkitabDb.TABEL_Bukmak2, null, null, null, null, null, null);
					while (cursor.moveToNext()) {
						Bukmak2 bukmak2 = Bukmak2.dariCursor(cursor);
						bukmak2.writeXml(xml);
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
		int ari = o.getInt(o.getColumnIndexOrThrow(AlkitabDb.KOLOM_Bukmak2_ari));
		
		Intent res = new Intent();
		res.putExtra(EXTRA_ariTerpilih, ari);
		
		setResult(RESULT_OK, res);
		finish();
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		new MenuInflater(this).inflate(R.menu.context_bukmak, menu);
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();

		if (item.getItemId() == R.id.menuHapusBukmak) {
			alkitabDb.getDatabase().delete(AlkitabDb.TABEL_Bukmak2, "_id=?", new String[] {String.valueOf(menuInfo.id)}); //$NON-NLS-1$
			adapter.getCursor().requery();
			
			return true;
		} else if (item.getItemId() == R.id.menuUbahKeteranganBukmak) {
			BukmakEditor editor = new BukmakEditor(this, alkitabDb, menuInfo.id);
			editor.setListener(new Listener() {
				@Override
				public void onOk() {
					adapter.getCursor().requery();
				}
			});
			editor.bukaDialog();
			
			return true;
		}
		
		return super.onContextItemSelected(item);
	}
}
