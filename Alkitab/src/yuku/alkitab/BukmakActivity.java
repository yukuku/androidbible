
package yuku.alkitab;

import yuku.alkitab.model.AlkitabDb;
import yuku.andoutil.Sqlitil;
import android.app.ListActivity;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.*;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class BukmakActivity extends ListActivity {
	SimpleCursorAdapter adapter;
	SQLiteDatabase db;
	Cursor cursor;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		db = AlkitabDb.getInstance(this).getDatabase();
		final String[] cursorColumns = {BaseColumns._ID, AlkitabDb.KOLOM_Bukmak_alamat, AlkitabDb.KOLOM_Bukmak_cuplikan, AlkitabDb.KOLOM_Bukmak_waktuTambah};
		cursor = db.query(AlkitabDb.TABEL_Bukmak, cursorColumns, null, null, null, null, AlkitabDb.KOLOM_Bukmak_waktuTambah + " desc");
		startManagingCursor(cursor);
		
		adapter = new SimpleCursorAdapter(this, R.layout.bukmak_item, cursor, new String[] {AlkitabDb.KOLOM_Bukmak_alamat, AlkitabDb.KOLOM_Bukmak_cuplikan, AlkitabDb.KOLOM_Bukmak_waktuTambah}, new int[] {R.id.lAlamat, R.id.lCuplikan, R.id.lTanggal});
		adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
			@Override
			public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
				if (cursorColumns[columnIndex] == AlkitabDb.KOLOM_Bukmak_waktuTambah) {
					((TextView)view).setText(Sqlitil.toLocaleDateMedium(cursor.getInt(columnIndex)));
					return true;
				}
				return false;
			}
		});
		setListAdapter(adapter);
		
		registerForContextMenu(getListView());
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		Cursor o = (Cursor) adapter.getItem(position);
		String alamat = o.getString(o.getColumnIndexOrThrow(AlkitabDb.KOLOM_Bukmak_alamat));
		
		Intent res = new Intent();
		res.putExtra("terpilih.alamat", alamat);
		
		setResult(RESULT_OK, res);
		finish();
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		new MenuInflater(this).inflate(R.menu.context_bukmak, menu);
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.menuHapusBukmak) {
			AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
			
			db.delete(AlkitabDb.TABEL_Bukmak, "_id=?", new String[] {"" + menuInfo.id});
			adapter.getCursor().requery();
			
			return true;
		}
		
		return super.onContextItemSelected(item);
	}
}
