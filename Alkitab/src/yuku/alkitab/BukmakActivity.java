
package yuku.alkitab;

import yuku.alkitab.BukmakEditor.Listener;
import yuku.alkitab.model.*;
import yuku.andoutil.*;
import android.app.*;
import android.content.*;
import android.database.*;
import android.os.*;
import android.provider.*;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.*;

public class BukmakActivity extends ListActivity {
	public static final String EXTRA_ariTerpilih = "ariTerpilih";

	private static final String[] cursorColumnsMapFrom = {AlkitabDb.KOLOM_Bukmak2_ari, AlkitabDb.KOLOM_Bukmak2_tulisan, AlkitabDb.KOLOM_Bukmak2_waktuUbah};
	private static final int[] cursorColumnsMapTo = {R.id.lCuplikan, R.id.lTulisan, R.id.lTanggal};
	private static final String[] cursorColumnsSelect;
	SimpleCursorAdapter adapter;
	AlkitabDb alkitabDb;
	Cursor cursor;
	
	static {
		cursorColumnsSelect = new String[cursorColumnsMapFrom.length+1];
		for (int i = 0; i < cursorColumnsMapFrom.length; i++) {
			cursorColumnsSelect[i+1] = cursorColumnsMapFrom[i];
		}
		cursorColumnsSelect[0] = BaseColumns._ID;
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.bukmak);
		
		S.siapinEdisi(getResources());
		S.siapinKitab(getResources());
		S.bacaPengaturan(this);
		S.siapinPengirimFidbek(this);
		
		alkitabDb = AlkitabDb.getInstance(this);
		cursor = alkitabDb.getDatabase().query(AlkitabDb.TABEL_Bukmak2, cursorColumnsSelect, AlkitabDb.KOLOM_Bukmak2_jenis + "=?", new String[] {String.valueOf(AlkitabDb.ENUM_Bukmak2_jenis_bukmak)}, null, null, AlkitabDb.KOLOM_Bukmak2_waktuUbah + " desc");
		startManagingCursor(cursor);
		
		adapter = new SimpleCursorAdapter(this, R.layout.bukmak_item, cursor, cursorColumnsMapFrom, cursorColumnsMapTo);
		adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
			@Override
			public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
				if (cursorColumnsSelect[columnIndex] == AlkitabDb.KOLOM_Bukmak2_waktuUbah) {
					String text = Sqlitil.toLocaleDateMedium(cursor.getInt(columnIndex));
					
					TextView tv = (TextView) view;
					tv.setText(text);
					IsiActivity.aturTampilanTeksTanggalBukmak(tv);
					return true;
				} else if (cursorColumnsSelect[columnIndex] == AlkitabDb.KOLOM_Bukmak2_tulisan) {
					TextView tv = (TextView) view;
					
					tv.setText(cursor.getString(columnIndex));
					IsiActivity.aturTampilanTeksJudulBukmak(tv);
					return true;
				} else if (cursorColumnsSelect[columnIndex] == AlkitabDb.KOLOM_Bukmak2_ari) {
					int ari = cursor.getInt(columnIndex);
					Kitab kitab = S.xkitab[Ari.toKitab(ari)];
					String[] xayat = S.muatTeks(getResources(), kitab, Ari.toPasal(ari));
					int ayat_1 = Ari.toAyat(ari);
					String isi = ayat_1 > xayat.length? "(...)": xayat[ayat_1 - 1];
					isi = U.buangKodeKusus(isi);
					
					TextView tv = (TextView) view;
					String alamat = S.alamat(ari);
					IsiActivity.aturIsiDanTampilanCuplikanBukmak(tv, alamat, isi);
					return true;
				}
				return false;
			}
		});
		setListAdapter(adapter);

		getListView().setBackgroundColor(S.penerapan.warnaLatar);
		getListView().setCacheColorHint(S.penerapan.warnaLatar);

		registerForContextMenu(getListView());
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
			alkitabDb.getDatabase().delete(AlkitabDb.TABEL_Bukmak2, "_id=?", new String[] {String.valueOf(menuInfo.id)});
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
