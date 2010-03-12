
package yuku.alkitab;

import yuku.alkitab.model.AlkitabDb;
import yuku.andoutil.Sqlitil;
import android.app.ListActivity;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.view.View;
import android.widget.*;

public class BukmakActivity extends ListActivity {
	SimpleCursorAdapter adapter;
	AlkitabDb db;
	Cursor cursor;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		db = new AlkitabDb(this);
		final String[] cursorColumns = {BaseColumns._ID, AlkitabDb.KOLOM_alamat, AlkitabDb.KOLOM_cuplikan, AlkitabDb.KOLOM_waktuTambah};
		cursor = db.getWritableDatabase().query(AlkitabDb.TABEL_Bukmak, cursorColumns, null, null, null, null, AlkitabDb.KOLOM_waktuTambah + " desc");
		startManagingCursor(cursor);
		
		adapter = new SimpleCursorAdapter(this, R.layout.bukmak_item, cursor, new String[] {AlkitabDb.KOLOM_alamat, AlkitabDb.KOLOM_cuplikan, AlkitabDb.KOLOM_waktuTambah}, new int[] {R.id.lAlamat, R.id.lCuplikan, R.id.lTanggal});
		adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
			@Override
			public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
				if (cursorColumns[columnIndex] == AlkitabDb.KOLOM_waktuTambah) {
					((TextView)view).setText(Sqlitil.toLocaleDateMedium(cursor.getInt(columnIndex)));
					return true;
				}
				return false;
			}
		});
		setListAdapter(adapter);
	}

}
