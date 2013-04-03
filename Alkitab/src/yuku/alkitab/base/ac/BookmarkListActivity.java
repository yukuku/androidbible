package yuku.alkitab.base.ac;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CursorAdapter;
import android.widget.Filter.FilterListener;
import android.widget.FilterQueryProvider;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import yuku.afw.V;
import yuku.alkitab.R;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.dialog.TypeBookmarkDialog;
import yuku.alkitab.base.dialog.TypeBookmarkDialog.Listener;
import yuku.alkitab.base.dialog.TypeHighlightDialog;
import yuku.alkitab.base.dialog.TypeHighlightDialog.Callback;
import yuku.alkitab.base.dialog.TypeNoteDialog;
import yuku.alkitab.base.dialog.TypeNoteDialog.RefreshCallback;
import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.model.Book;
import yuku.alkitab.base.model.Label;
import yuku.alkitab.base.storage.Db;
import yuku.alkitab.base.util.Appearances;
import yuku.alkitab.base.util.IntArrayList;
import yuku.alkitab.base.util.QueryTokenizer;
import yuku.alkitab.base.util.Search2Engine;
import yuku.alkitab.base.util.Sqlitil;
import yuku.devoxx.flowlayout.FlowLayout;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.widget.SearchView;

public class BookmarkListActivity extends BaseActivity {
	public static final String TAG = BookmarkListActivity.class.getSimpleName();
	
    // out
	public static final String EXTRA_ariTerpilih = "ariTerpilih"; //$NON-NLS-1$

    // in
    private static final String EXTRA_filter_jenis = "filter_jenis"; //$NON-NLS-1$
    private static final String EXTRA_filter_labelId = "filter_labelId"; //$NON-NLS-1$

    public static final int LABELID_noLabel = -1;

    View panelList;
    View empty;
    TextView tEmpty;
    View bClearFilter;
	SearchView searchView;
	ListView lv;
	View emptyView;
    
	CursorAdapter adapter;
	Cursor cursor;
	
	String sort_column;
	boolean sort_ascending;
	int sort_columnId;
	String lagiPakeFilter;

    int filter_jenis;
    long filter_labelId;

	int warnaHilite;


    public static Intent createIntent(Context context, int filter_jenis, long filter_labelId) {
    	Intent res = new Intent(context, BookmarkListActivity.class);
    	res.putExtra(EXTRA_filter_jenis, filter_jenis);
    	res.putExtra(EXTRA_filter_labelId, filter_labelId);
    	return res;
    }
    
    @Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_bookmark_list);
		
		panelList = V.get(this, R.id.panelList);
		empty = V.get(this, android.R.id.empty);
		tEmpty = V.get(this, R.id.tEmpty);
		bClearFilter = V.get(this, R.id.bClearFilter);
		lv = V.get(this, android.R.id.list);
		emptyView = V.get(this, android.R.id.empty);
		
		filter_jenis = getIntent().getIntExtra(EXTRA_filter_jenis, 0);
		filter_labelId = getIntent().getLongExtra(EXTRA_filter_labelId, 0);
		
		bClearFilter.setOnClickListener(bClearFilter_click);
		
        setTitleAndNothingText();

        // default sort
        sort_column = Db.Bookmark2.addTime;
        sort_ascending = false;
        sort_columnId = R.string.menuSortWaktuTambah;
		gantiCursor();
		
		adapter = new BukmakListAdapter(this, cursor);

		panelList.setBackgroundColor(S.applied.backgroundColor);
		tEmpty.setTextColor(S.applied.fontColor);
		
		warnaHilite = U.getHighlightColorByBrightness(S.applied.backgroundBrightness);
		
		lv.setAdapter(adapter);
		lv.setCacheColorHint(S.applied.backgroundColor);
		lv.setOnItemClickListener(lv_click);
		lv.setEmptyView(emptyView);

		registerForContextMenu(lv);
	}

	void setTitleAndNothingText() {
        String title = null;
        String nothingText = null;
        
        // atur judul berdasarkan filter
        if (filter_jenis == Db.Bookmark2.kind_note) {
            title = getString(R.string.bmcat_notes);
            nothingText = getString(R.string.bl_no_notes_written_yet);
        } else if (filter_jenis == Db.Bookmark2.kind_highlight) {
            title = getString(R.string.bmcat_highlights);
            nothingText = getString(R.string.bl_no_highlighted_verses);
        } else if (filter_jenis == Db.Bookmark2.kind_bookmark) {
            if (filter_labelId == 0) {
                title = getString(R.string.bmcat_all_bookmarks);
                nothingText = getString(R.string.belum_ada_pembatas_buku);
            } else if (filter_labelId == LABELID_noLabel) {
                title = getString(R.string.bmcat_unlabeled_bookmarks);
                nothingText = getString(R.string.bl_there_are_no_bookmarks_without_any_labels);
            } else {
                Label label = S.getDb().getLabelById(filter_labelId);
                if (label != null) {
                    title = label.title;
                    nothingText = getString(R.string.bl_there_are_no_bookmarks_with_the_label_label, label.title);
                }
            }
        }
        
        // kalau lagi pake filter teks (bukan filter jenis), nothingTextnya lain
        if (lagiPakeFilter != null) {
        	nothingText = getString(R.string.bl_no_items_match_the_filter_above);
        	bClearFilter.setVisibility(View.VISIBLE);
        } else {
        	bClearFilter.setVisibility(View.GONE);
        }

        if (title != null && nothingText != null) {
            setTitle(title);
            tEmpty.setText(nothingText);
        } else {
            finish(); // shouldn't happen
            return;
        }
	}

	boolean searchView_search(String query) {
		String carian = query.toString().trim();
		if (carian.length() == 0) {
			buangFilter();
			return true;
		}
		
		String[] xtoken = QueryTokenizer.tokenize(carian);
		if (xtoken.length == 0) {
			buangFilter();
			return true;
		}
		
		pasangFilter(carian);
		return true;
	}

	OnClickListener bClearFilter_click = new OnClickListener() {
		@Override public void onClick(View v) {
			searchView.setQuery("", false); //$NON-NLS-1$
			buangFilter();
		}
	};

	protected void buangFilter() {
		adapter.getFilter().filter(null);
		lagiPakeFilter = null;
		setTitleAndNothingText();
	}
	
	protected void pasangFilter(String carian) {
		lagiPakeFilter = carian;
		filterPakeLagiPakeFilter();
		setTitleAndNothingText();
	}

	void filterPakeLagiPakeFilter() {
		final ProgressDialog pd = ProgressDialog.show(this, null, getString(R.string.bl_filtering_titiktiga), true, false);
		adapter.getFilter().filter(lagiPakeFilter, new FilterListener() {
			@Override public void onFilterComplete(int count) {
				pd.dismiss();
			}
		});
	}

	@SuppressWarnings("deprecation") void gantiCursor() {
		if (cursor != null) {
			stopManagingCursor(cursor);
		}
		
		cursor = S.getDb().listBookmarks(filter_jenis, filter_labelId, sort_column, sort_ascending);
		startManagingCursor(cursor);
		
		if (adapter != null) {
			adapter.changeCursor(cursor);
		}
	}

	protected View getLabelView(FlowLayout panelLabels, Label label) {
		View res = LayoutInflater.from(this).inflate(R.layout.label, null);
		res.setLayoutParams(panelLabels.generateDefaultLayoutParams());
		
		TextView lJudul = V.get(res, R.id.lJudul);
		lJudul.setText(label.title);
		
		U.applyLabelColor(label, lJudul);
		
		return res;
	}

	private void bikinMenu(Menu menu) {
		menu.clear();
		getSupportMenuInflater().inflate(R.menu.activity_bookmark_list, menu);
		
        final MenuItem menuSearch = menu.findItem(R.id.menuSearch);
        if (menuSearch != null) {
			searchView = (SearchView) menuSearch.getActionView();
	        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
				@Override public boolean onQueryTextChange(String newText) {
					if (newText.length() == 0) {
						return searchView_search(newText);
					}
					return false;
				}
	
			    @Override public boolean onQueryTextSubmit(String query) {
			    	return searchView_search(query);
				}
			});
        }
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		bikinMenu(menu);
		
		return true;
	}
	
	@Override public boolean onPrepareOptionsMenu(Menu menu) {
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
		
		return super.onOptionsItemSelected(item);
	}

	private void openSortDialog() {
		List<String> labels = new ArrayList<String>();
		final IntArrayList values = new IntArrayList();
		
		labels.add(getString(R.string.menuSortAri));
		values.add(R.string.menuSortAri);
		
		if (filter_jenis == Db.Bookmark2.kind_bookmark) {
			labels.add(getString(R.string.menuSortTulisan));
			values.add(R.string.menuSortTulisan);
		} else if (filter_jenis == Db.Bookmark2.kind_note) {
			// nop
		} else if (filter_jenis == Db.Bookmark2.kind_highlight) {
			labels.add(getString(R.string.menuSortTulisan_warna));
			values.add(R.string.menuSortTulisan);
		}
		
		labels.add(getString(R.string.menuSortWaktuTambah));
		values.add(R.string.menuSortWaktuTambah);
		
		labels.add(getString(R.string.menuSortWaktuUbah));
		values.add(R.string.menuSortWaktuUbah);
		
		int selected = -1;
		for (int i = 0, len = values.size(); i < len; i++) {
			if (sort_columnId == values.get(i)) {
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
					sort(Db.Bookmark2.ari, true, value);
					break;
				case R.string.menuSortTulisan:
					sort(Db.Bookmark2.caption, true, value);
					break;
				case R.string.menuSortWaktuTambah:
					sort(Db.Bookmark2.addTime, false, value);
					break;
				case R.string.menuSortWaktuUbah:
					sort(Db.Bookmark2.modifyTime, false, value);
					break;
				}
				dialog.dismiss();
			}

			private void sort(String column, boolean ascending, int columnId) {
				searchView.setQuery("", false); //$NON-NLS-1$
				lagiPakeFilter = null;
				setTitleAndNothingText();
				sort_column = column;
				sort_ascending = ascending;
				sort_columnId = columnId;
				gantiCursor();
			}
		})
		.setTitle(R.string.menuSort)
		.show();
	}

	private OnItemClickListener lv_click = new OnItemClickListener() {
		@Override public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
			Cursor o = (Cursor) adapter.getItem(position);
			int ari = o.getInt(o.getColumnIndexOrThrow(Db.Bookmark2.ari));
			
			Intent res = new Intent();
			res.putExtra(EXTRA_ariTerpilih, ari);
			
			setResult(RESULT_OK, res);
			finish();
		}
	};
	
	@Override public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		getMenuInflater().inflate(R.menu.context_bookmark_list, menu);
		
		// sesuaikan string berdasarkan jenis.
		android.view.MenuItem menuHapusBukmak = menu.findItem(R.id.menuHapusBukmak);
		if (filter_jenis == Db.Bookmark2.kind_bookmark) menuHapusBukmak.setTitle(R.string.hapus_pembatas_buku);
		if (filter_jenis == Db.Bookmark2.kind_note) menuHapusBukmak.setTitle(R.string.hapus_catatan);
		if (filter_jenis == Db.Bookmark2.kind_highlight) menuHapusBukmak.setTitle(R.string.hapus_stabilo);

		android.view.MenuItem menuUbahBukmak = menu.findItem(R.id.menuUbahBukmak);
		if (filter_jenis == Db.Bookmark2.kind_bookmark) menuUbahBukmak.setTitle(R.string.ubah_bukmak);
		if (filter_jenis == Db.Bookmark2.kind_note) menuUbahBukmak.setTitle(R.string.ubah_catatan);
		if (filter_jenis == Db.Bookmark2.kind_highlight) menuUbahBukmak.setTitle(R.string.ubah_stabilo);
	}
	
	@SuppressWarnings("deprecation") @Override public boolean onContextItemSelected(android.view.MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		int itemId = item.getItemId();
		
		if (itemId == R.id.menuHapusBukmak) {
			// jenisnya apapun, cara hapusnya sama
			S.getDb().deleteBookmarkById(info.id);
			adapter.getCursor().requery();
			if (lagiPakeFilter != null) filterPakeLagiPakeFilter();
			
			return true;
		} else if (itemId == R.id.menuUbahBukmak) {
			if (filter_jenis == Db.Bookmark2.kind_bookmark) {
				TypeBookmarkDialog dialog = new TypeBookmarkDialog(this, info.id);
				dialog.setListener(new Listener() {
					@Override public void onOk() {
						adapter.getCursor().requery();
						if (lagiPakeFilter != null) filterPakeLagiPakeFilter();
					}
				});
				dialog.show();
				
			} else if (filter_jenis == Db.Bookmark2.kind_note) {
				Cursor cursor = (Cursor) adapter.getItem(info.position);
				int ari = cursor.getInt(cursor.getColumnIndexOrThrow(Db.Bookmark2.ari));
				
				TypeNoteDialog dialog = new TypeNoteDialog(this, S.activeVersion.getBook(Ari.toBook(ari)), Ari.toChapter(ari), Ari.toVerse(ari), new RefreshCallback() {
					@Override public void onDone() {
						adapter.getCursor().requery();
						if (lagiPakeFilter != null) filterPakeLagiPakeFilter();
					}
				});
				dialog.show();
				
			} else if (filter_jenis == Db.Bookmark2.kind_highlight) {
				Cursor cursor = (Cursor) adapter.getItem(info.position);
				int ari = cursor.getInt(cursor.getColumnIndexOrThrow(Db.Bookmark2.ari));
				int warnaRgb = U.decodeHighlight(cursor.getString(cursor.getColumnIndexOrThrow(Db.Bookmark2.caption)));
				String alamat = S.reference(S.activeVersion, ari);
				
				new TypeHighlightDialog(this, ari, new Callback() {
					@Override public void onOk(int warnaRgb) {
						adapter.getCursor().requery();
						if (lagiPakeFilter != null) filterPakeLagiPakeFilter();
					}
				}, warnaRgb, alamat).bukaDialog();
			}
			
			return true;
		}
		
		return false;
	}
	
	class BukmakListAdapter extends CursorAdapter {
		BukmakFilterQueryProvider filterQueryProvider;
		
		// must also modify FilterQueryProvider below!!!
		private int col__id;
		private int col_ari;
		private int col_tulisan;
		private int col_waktuTambah;
		private int col_waktuUbah;
		//////////////////////////////
		
		BukmakListAdapter(Context context, Cursor cursor) {
			super(context, cursor, false);
			
			getColumnIndexes();
			
			setFilterQueryProvider(filterQueryProvider = new BukmakFilterQueryProvider());
		}
		
		@Override public void notifyDataSetChanged() {
			getColumnIndexes();
			
			super.notifyDataSetChanged();
		}

		private void getColumnIndexes() {
			Cursor c = getCursor();
			if (c != null) {
				col__id = c.getColumnIndexOrThrow(BaseColumns._ID);
				col_ari = c.getColumnIndexOrThrow(Db.Bookmark2.ari);
				col_tulisan = c.getColumnIndexOrThrow(Db.Bookmark2.caption);
				col_waktuTambah = c.getColumnIndexOrThrow(Db.Bookmark2.addTime);
				col_waktuUbah = c.getColumnIndexOrThrow(Db.Bookmark2.modifyTime);
			}
		}
		
		@Override public View newView(Context context, Cursor cursor, ViewGroup parent) {
			return getLayoutInflater().inflate(R.layout.item_bookmark, null);
		}
		
		@Override public void bindView(View view, Context context, Cursor cursor) {
			TextView lTanggal = V.get(view, R.id.lTanggal);
			TextView lTulisan = V.get(view, R.id.lTulisan);
			TextView lCuplikan = V.get(view, R.id.lCuplikan);
			FlowLayout panelLabels = V.get(view, R.id.panelLabels);
			
			{
				int waktuTambah_i = cursor.getInt(col_waktuTambah);
				int waktuUbah_i = cursor.getInt(col_waktuUbah);
				
				if (waktuTambah_i == waktuUbah_i) {
					lTanggal.setText(Sqlitil.toLocaleDateMedium(waktuTambah_i));
				} else {
					lTanggal.setText(getString(R.string.waktuTambah_edited_waktuUbah, Sqlitil.toLocaleDateMedium(waktuTambah_i), Sqlitil.toLocaleDateMedium(waktuUbah_i)));
				}
				
				Appearances.applyBookmarkDateTextAppearance(lTanggal);
			}
			
			int ari = cursor.getInt(col_ari);
			Book book = S.activeVersion.getBook(Ari.toBook(ari));
			String alamat = S.reference(S.activeVersion, ari);
			
			String isi = S.loadVerseText(S.activeVersion, book, Ari.toChapter(ari), Ari.toVerse(ari));
			isi = U.removeSpecialCodes(isi);
			
			String tulisan = cursor.getString(col_tulisan);
			
			if (filter_jenis == Db.Bookmark2.kind_bookmark) {
				lTulisan.setText(lagiPakeFilter != null? Search2Engine.hilite(tulisan, filterQueryProvider.getXkata(), warnaHilite): tulisan);
				Appearances.applyBookmarkTitleTextAppearance(lTulisan);
				CharSequence cuplikan = lagiPakeFilter != null? Search2Engine.hilite(isi, filterQueryProvider.getXkata(), warnaHilite): isi;

				Appearances.applyBookmarkSnippetContentAndAppearance(lCuplikan, alamat, cuplikan);
				
				long _id = cursor.getLong(col__id);
				List<Label> labels = S.getDb().listLabelsByBookmarkId(_id);
				if (labels != null && labels.size() != 0) {
					panelLabels.setVisibility(View.VISIBLE);
					panelLabels.removeAllViews();
					for (int i = 0, len = labels.size(); i < len; i++) {
						panelLabels.addView(getLabelView(panelLabels, labels.get(i)));
					}
				} else {
					panelLabels.setVisibility(View.GONE);
				}
				
			} else if (filter_jenis == Db.Bookmark2.kind_note) {
				lTulisan.setText(alamat);
				Appearances.applyBookmarkTitleTextAppearance(lTulisan);
				lCuplikan.setText(lagiPakeFilter != null? Search2Engine.hilite(tulisan, filterQueryProvider.getXkata(), warnaHilite): tulisan);
				Appearances.applyTextAppearance(lCuplikan);
				
			} else if (filter_jenis == Db.Bookmark2.kind_highlight) {
				lTulisan.setText(alamat);
				Appearances.applyBookmarkTitleTextAppearance(lTulisan);
				
				SpannableStringBuilder cuplikan = lagiPakeFilter != null? Search2Engine.hilite(isi, filterQueryProvider.getXkata(), warnaHilite): new SpannableStringBuilder(isi);
				int warnaStabilo = U.decodeHighlight(tulisan);
				if (warnaStabilo != -1) {
					cuplikan.setSpan(new BackgroundColorSpan(U.alphaMixHighlight(warnaStabilo)), 0, cuplikan.length(), 0);
				}
				lCuplikan.setText(cuplikan);
				Appearances.applyTextAppearance(lCuplikan);
			}
		}
	};
	
	class BukmakFilterQueryProvider implements FilterQueryProvider {
		private String[] xkata;
		
		public String[] getXkata() {
			return xkata;
		}
		
		@Override public Cursor runQuery(CharSequence constraint) {
			if (constraint == null || constraint.length() == 0) {
				this.xkata = null;
				return S.getDb().listBookmarks(filter_jenis, filter_labelId, sort_column, sort_ascending);
			}
			
			String[] xkata = QueryTokenizer.tokenize(constraint.toString());
			for (int i = 0; i < xkata.length; i++) {
				xkata[i] = xkata[i].toLowerCase(Locale.getDefault());
			}
			this.xkata = xkata;
			
			MatrixCursor res = new MatrixCursor(new String[] {BaseColumns._ID, Db.Bookmark2.ari, Db.Bookmark2.caption, Db.Bookmark2.addTime, Db.Bookmark2.modifyTime});
			Cursor c = S.getDb().listBookmarks(filter_jenis, filter_labelId, sort_column, sort_ascending);
			try {
				int col__id = c.getColumnIndexOrThrow(BaseColumns._ID);
				int col_ari = c.getColumnIndexOrThrow(Db.Bookmark2.ari);
				int col_tulisan = c.getColumnIndexOrThrow(Db.Bookmark2.caption);
				int col_waktuTambah = c.getColumnIndexOrThrow(Db.Bookmark2.addTime);
				int col_waktuUbah = c.getColumnIndexOrThrow(Db.Bookmark2.modifyTime);
				
				while (c.moveToNext()) {
					boolean memenuhi = false;
					
					String tulisan = c.getString(col_tulisan);
					
					if (filter_jenis != Db.Bookmark2.kind_highlight) { // "tulisan" di stabilo cuma simpen info tentang warna, jadi ga ada gunanya dicek.
						String tulisan_lc = tulisan.toLowerCase(Locale.getDefault());
						if (Search2Engine.satisfiesQuery(tulisan_lc, xkata)) {
							memenuhi = true;
						}
					}
					
					int ari = c.getInt(col_ari);
					if (!memenuhi) {
						// coba isi ayatnya!
						String ayat = S.loadVerseText(S.activeVersion, ari);
						String ayat_lc = ayat.toLowerCase(Locale.getDefault());
						if (Search2Engine.satisfiesQuery(ayat_lc, xkata)) {
							memenuhi = true;
						}
					}
					
					if (memenuhi) {
						res.newRow()
						.add(c.getLong(col__id))
						.add(ari)
						.add(tulisan)
						.add(c.getInt(col_waktuTambah))
						.add(c.getInt(col_waktuUbah));
					}
				}
			} finally {
				c.close();
			}

			return res;
		}
	}
}
