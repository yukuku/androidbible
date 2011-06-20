
package yuku.alkitab.base;

import android.app.*;
import android.content.*;
import android.database.*;
import android.os.*;
import android.provider.*;
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
import yuku.alkitab.base.BukmakEditor.Listener;
import yuku.alkitab.base.model.*;
import yuku.alkitab.base.storage.*;
import yuku.andoutil.*;

public class BukmakActivity extends ListActivity {
	public static final String EXTRA_ariTerpilih = "ariTerpilih"; //$NON-NLS-1$

	private static final String[] cursorColumnsMapFrom = {Db.Bukmak2.ari, Db.Bukmak2.tulisan, Db.Bukmak2.waktuUbah};
	private static final int[] cursorColumnsMapTo = {R.id.lCuplikan, R.id.lTulisan, R.id.lTanggal};
	private static final String[] cursorColumnsSelect;

	SimpleCursorAdapter adapter;
	Cursor cursor;

	static {
		cursorColumnsSelect = new String[cursorColumnsMapFrom.length+1];
		System.arraycopy(cursorColumnsMapFrom, 0, cursorColumnsSelect, 1, cursorColumnsMapFrom.length);
		cursorColumnsSelect[0] = BaseColumns._ID;
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		S.siapinKitab();
		S.bacaPengaturan(this);
		S.siapinPengirimFidbek(this);
		
		setContentView(R.layout.activity_bukmak);
		setTitle(R.string.pembatas_buku);
		
		cursor = S.getDb().listBukmak(cursorColumnsSelect, Db.Bukmak2.jenis_bukmak);
		startManagingCursor(cursor);
		
		adapter = new SimpleCursorAdapter(this, R.layout.item_bukmak, cursor, cursorColumnsMapFrom, cursorColumnsMapTo);
		adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
			@Override
			public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
				if (cursorColumnsSelect[columnIndex] == Db.Bukmak2.waktuUbah) { // $codepro.audit.disable stringComparison
					String text = Sqlitil.toLocaleDateMedium(cursor.getInt(columnIndex));
					
					TextView tv = (TextView) view;
					tv.setText(text);
					IsiActivity.aturTampilanTeksTanggalBukmak(tv);
					return true;
				} else if (cursorColumnsSelect[columnIndex] == Db.Bukmak2.tulisan) { // $codepro.audit.disable stringComparison
					TextView tv = (TextView) view;
					
					tv.setText(cursor.getString(columnIndex));
					IsiActivity.aturTampilanTeksJudulBukmak(tv);
					return true;
				} else if (cursorColumnsSelect[columnIndex] == Db.Bukmak2.ari) { // $codepro.audit.disable stringComparison
					int ari = cursor.getInt(columnIndex);
					Kitab kitab = S.edisiAktif.getKitab(Ari.toKitab(ari));
					String isi = S.muatSatuAyat(S.edisiAktif, kitab, Ari.toPasal(ari), Ari.toAyat(ari));
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
		new MenuInflater(this).inflate(R.menu.context_bukmak, menu);
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();

		if (item.getItemId() == R.id.menuHapusBukmak) {
			S.getDb().hapusBukmakById(menuInfo.id);
			adapter.getCursor().requery();
			
			return true;
		} else if (item.getItemId() == R.id.menuUbahKeteranganBukmak) {
			BukmakEditor editor = new BukmakEditor(this, menuInfo.id);
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
