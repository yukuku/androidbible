
package yuku.alkitab;

import yuku.alkitab.model.*;
import android.app.Activity;
import android.content.*;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.database.sqlite.*;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.*;
import android.text.style.*;
import android.util.Log;
import android.view.*;
import android.view.View.OnClickListener;
import android.view.inputmethod.*;
import android.widget.*;
import android.widget.TextView.BufferType;

public class SearchActivity extends Activity {
	private static final String NAMAPREF_pakeSnippet = "pakeSnippet";
	ListView lsHasilCari;
	ImageButton bCari;
	EditText tCarian;

	private SQLiteDatabase db;
	private Cursor cursor;
	private SimpleCursorAdapter adapter;
	private int resultCode;
	private Intent returnValue;
	
	private boolean pakeSnippet_ = false;
	
	private static int[] xwarna;

	static {
		xwarna = new int[12];
		
//		{
//			int w = 0x66ff66;
//			for (int i = 0; i < 6; i++) {
//				xwarna[i] = 0xff000000 | w;
//				w = (w >> 5) | (w << 19);
//			}
//		}
//		
//		{
//			int w = 0x6f666f;
//			for (int i = 6; i < 12; i++) {
//				xwarna[i] = 0xff000000 | w;
//				w = (w >> 5) | (w << 19);
//			}
//		}
		
		for (int i = 0; i < xwarna.length; i++) {
			xwarna[i] = 0xff66ff66;
		}
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.search);
		
		//# default buat return
		resultCode = RESULT_CANCELED;
		returnValue = new Intent();
		
		Intent intent = getIntent();
		
		db = SearchDb.getInstance(this).getDatabase();

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
		
		{
			SharedPreferences preferences = getSharedPreferences(S.NAMA_PREFERENCES, 0);
			pakeSnippet_ = preferences.getBoolean(NAMAPREF_pakeSnippet, false);
		}
		
		final OnClickListener bCari_click = new View.OnClickListener() {

			@Override
			public synchronized void onClick(View v) {
				String carian = tCarian.getText().toString();
				
				if (cursor != null) {
					lsHasilCari.setAdapter(null);
					stopManagingCursor(cursor);
					cursor.close();
				}
				
				cursor = db.rawQuery(String.format("select *, docid as _id, snippet(%s, '<font color=\"#66ff66\"><b>', '</b></font>', '...', -1, -40) as snip, offsets(%s) as xoff from %s where %s match ? limit 300", SearchDb.TABEL_Fts, SearchDb.TABEL_Fts, SearchDb.TABEL_Fts, SearchDb.KOLOM_content), new String[] {carian});
				startManagingCursor(cursor);
				
				// panggil getCount untuk tau cursor ini sah ga
				try {
					cursor.getCount();
				} catch (SQLiteException e) {
					Log.w("alki", "carian ga sah", e);
					//Toast.makeText(SearchActivity.this, "Bentuk carian tidak sah", Toast.LENGTH_SHORT).show();
					tCarian.setError("Bentuk carian tidak sah");
					tCarian.requestFocus();
					return;
				}
				
				tCarian.setError(null);
				
				adapter = new SimpleCursorAdapter(SearchActivity.this, R.layout.search_item, cursor,
						new String[] {SearchDb.KOLOM_content, SearchDb.KOLOM_ari},
						new int[] {R.id.lCuplikan, R.id.lAlamat}
				);
				
				adapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
					@Override
					public boolean setViewValue(View view, Cursor cursor, int columnIndex) {
						if (view.getId() == R.id.lAlamat) {
							S.siapinKitab(getResources());
							
							((TextView)view).setText(Ari.toAlamat(S.xkitab, cursor.getInt(columnIndex)));
							return true;
						} else if (view.getId() == R.id.lCuplikan) {
							
							if (pakeSnippet_) {
								((TextView)view).setText(Html.fromHtml(cursor.getString(cursor.getColumnIndexOrThrow("snip"))), BufferType.SPANNABLE);
							} else {
								SpannableString ss = SpannableString.valueOf(cursor.getString(columnIndex));
								stabilo(ss, cursor.getString(cursor.getColumnIndexOrThrow("xoff")));
								((TextView)view).setText(ss, BufferType.SPANNABLE);
							}
							
							return true;
						}
						return false;
					}
					
					private void stabilo(SpannableString ss, String xoff) {
						/*
						 * 0 The column number that the term instance occurs in (0 for the leftmost column of the FTS3 table, 1 for the next leftmost, etc.).
						 * 1 The term number of the matching term within the full-text query expression. Terms within a query expression are numbered starting from 0 in the
						 * order that they occur.
						 * 2 The byte offset of the matching term within the column.
						 * 3 The size of the matching term in bytes.
						 */
						
						int termNumber = 0;
						int offset = 0;
						int size = 0;
						
						int pos = 0;
						int num = 0;
						char[] cc = xoff.toCharArray();
						
						while (true) {
							if (pos >= cc.length) {
								break;
							}
							
							{
								char c = cc[pos++];
								num = (c - '0');
								while (true) {
									c = cc[pos++];
									if (c == ' ') {
										break;
									} else {
										num = num*10 + (c-'0');
									}
								}
							}
							
							{
								char c = cc[pos++];
								num = (c - '0');
								while (true) {
									c = cc[pos++];
									if (c == ' ') {
										break;
									} else {
										num = num*10 + (c-'0');
									}
								}
								
								termNumber = num;
							}
							
							{
								char c = cc[pos++];
								num = (c - '0');
								while (true) {
									c = cc[pos++];
									if (c == ' ') {
										break;
									} else {
										num = num*10 + (c-'0');
									}
								}
								
								offset = num;
							}
							
							{
								char c = cc[pos++];
								num = (c - '0');
								while (true) {
									if (pos >= cc.length) {
										break;
									}
									
									c = cc[pos++];
									if (c == ' ') {
										break;
									} else {
										num = num*10 + (c-'0');
									}
								}
								
								size = num;
							}
							
							//# sudah dapet data2nya, mari lakukan sesuatu
							{
								ForegroundColorSpan color = new ForegroundColorSpan(xwarna[termNumber >= 12? 11: termNumber]);
								ss.setSpan(color, offset, offset+size, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
								ss.setSpan(new StyleSpan(Typeface.BOLD), offset, offset+size, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
							}
						}
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
		
		{
			SharedPreferences preferences = getSharedPreferences(S.NAMA_PREFERENCES, 0);
			Editor editor = preferences.edit();
			editor.putBoolean(NAMAPREF_pakeSnippet, pakeSnippet_);
			editor.commit();
		}
		
		returnValue.putExtra("carian", tCarian.getText().toString());
		setResult(resultCode, returnValue);
		super.finish();
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		new MenuInflater(this).inflate(R.menu.search, menu);
		
		return true;
	}
	
	@Override
	public boolean onMenuOpened(int featureId, Menu menu) {
		MenuItem menuTukarTampilCarian = menu.findItem(R.id.menuTukarTampilCarian);
		if (pakeSnippet_) {
			menuTukarTampilCarian.setTitle("Lengkap");
		} else {
			menuTukarTampilCarian.setTitle("Sepotong");
		}
		
		return super.onMenuOpened(featureId, menu);
	}
	
	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		if (item.getItemId() == R.id.menuTukarTampilCarian) {
			pakeSnippet_ = !pakeSnippet_;
			
			if (adapter != null) {
				adapter.notifyDataSetChanged();
			}
			
			return true;
		}
		
		return false;
	}
}
