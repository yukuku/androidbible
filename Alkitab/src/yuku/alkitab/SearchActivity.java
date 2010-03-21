
package yuku.alkitab;

import yuku.alkitab.model.*;
import android.app.Activity;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.*;
import android.view.View.OnClickListener;
import android.view.inputmethod.*;
import android.widget.*;
import android.widget.TextView.BufferType;

public class SearchActivity extends Activity {
	ListView lsHasilCari;
	ImageButton bCari;
	EditText tCarian;

	private SQLiteDatabase db;
	private Cursor cursor;
	private int resultCode;
	private Intent returnValue;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.search);
		
		//# default buat return
		resultCode = RESULT_CANCELED;
		returnValue = new Intent();
		
		Intent intent = getIntent();
		
		db = SearchDb.getInstance().getDatabase();

		lsHasilCari = (ListView) findViewById(R.id.lsHasilCari);
		lsHasilCari.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Cursor cursor = (Cursor) parent.getItemAtPosition(position);
				int ari = cursor.getInt(cursor.getColumnIndexOrThrow(SearchDb.KOLOM_ari));
				
				resultCode = RESULT_OK;
				returnValue.putExtra("terpilih.ari", ari);
				
				setResult(resultCode, returnValue);
				Log.d("alki", "panggil finish");
				finish();
			}
		});
		
		final OnClickListener bCari_click = new View.OnClickListener() {

			@Override
			public synchronized void onClick(View v) {
				String carian = tCarian.getText().toString();
				
				if (cursor != null) {
					lsHasilCari.setAdapter(null);
					stopManagingCursor(cursor);
					cursor.close();
				}
				
				// baru
				cursor = db.rawQuery(String.format("select *, docid as _id, snippet(%s, '<u><b>', '</b></u>', '...', -1, -40) as snip from %s where %s match ? limit 100", SearchDb.TABEL_Fts, SearchDb.TABEL_Fts, SearchDb.KOLOM_content), new String[] {carian});
				startManagingCursor(cursor);
				
				final String[] xkolom = new String[] {"snip", SearchDb.KOLOM_ari};
				SimpleCursorAdapter adapter = new SimpleCursorAdapter(SearchActivity.this, R.layout.search_item, cursor,
						xkolom,
						new int[] {R.id.lCuplikan, R.id.lAlamat}
				);
				
				adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
					@Override
					public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
						if (view.getId() == R.id.lAlamat) {
							((TextView)view).setText(Ari.toAlamat(cursor.getInt(columnIndex)));
							return true;
						} else if (view.getId() == R.id.lCuplikan) {
							((TextView)view).setText(Html.fromHtml(cursor.getString(columnIndex)), BufferType.SPANNABLE);
							return true;
						}
						return false;
					}
				});
				
				lsHasilCari.setAdapter(adapter);
			}
		};
		
		tCarian = (EditText) findViewById(R.id.tCarian);
		{
			String carian = intent.getStringExtra("carian");
			
			if (carian != null) {
				tCarian.setText(carian);
				tCarian.selectAll();
				bCari_click.onClick(bCari);
			}
		}
		tCarian.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_SEARCH) {
					//# close soft keyboard 
					InputMethodManager inputManager = (InputMethodManager) SearchActivity.this.getSystemService(Context.INPUT_METHOD_SERVICE); 
					inputManager.hideSoftInputFromWindow(tCarian.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
					
					bCari_click.onClick(bCari);
				}
				return false;
			}
		});
		
		bCari = (ImageButton) findViewById(R.id.bCari);
		bCari.setOnClickListener(bCari_click);
	}
	
	@Override
	public void finish() {
		Log.d("alki", "finish");
		returnValue.putExtra("carian", tCarian.getText().toString());
		
		setResult(resultCode, returnValue);
		super.finish();
	}
}
