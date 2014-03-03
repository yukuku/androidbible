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
import android.view.Menu;
import android.view.MenuItem;
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
import android.widget.SearchView;
import android.widget.TextView;
import yuku.afw.V;
import yuku.alkitab.base.IsiActivity;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.dialog.TypeBookmarkDialog;
import yuku.alkitab.base.dialog.TypeBookmarkDialog.Listener;
import yuku.alkitab.base.dialog.TypeHighlightDialog;
import yuku.alkitab.base.dialog.TypeNoteDialog;
import yuku.alkitab.base.storage.Db;
import yuku.alkitab.base.util.Appearances;
import yuku.alkitab.base.util.QueryTokenizer;
import yuku.alkitab.base.util.Search2Engine;
import yuku.alkitab.base.util.Sqlitil;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Book;
import yuku.alkitab.model.Label;
import yuku.alkitab.model.Marker;
import yuku.alkitab.util.Ari;
import yuku.alkitab.util.IntArrayList;
import yuku.devoxx.flowlayout.FlowLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BookmarkListActivity extends BaseActivity {
	public static final String TAG = BookmarkListActivity.class.getSimpleName();
	
    // in
    private static final String EXTRA_filter_kind = "filter_kind"; //$NON-NLS-1$
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
	String currentlyUsedFilter;

    Marker.Kind filter_kind;
    long filter_labelId;

	int hiliteColor;


    public static Intent createIntent(Context context, Marker.Kind filter_kind, long filter_labelId) {
    	Intent res = new Intent(context, BookmarkListActivity.class);
    	res.putExtra(EXTRA_filter_kind, filter_kind.code);
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

	    filter_kind = Marker.Kind.fromCode(getIntent().getIntExtra(EXTRA_filter_kind, 0));
		filter_labelId = getIntent().getLongExtra(EXTRA_filter_labelId, 0);
		
		bClearFilter.setOnClickListener(bClearFilter_click);
		
        setTitleAndNothingText();

        // default sort
        sort_column = Db.Marker.createTime;
        sort_ascending = false;
        sort_columnId = R.string.menuSortWaktuTambah;
		replaceCursor();
		
		adapter = new BukmakListAdapter(this, cursor);

		panelList.setBackgroundColor(S.applied.backgroundColor);
		tEmpty.setTextColor(S.applied.fontColor);
		
		hiliteColor = U.getHighlightColorByBrightness(S.applied.backgroundBrightness);
		
		lv.setAdapter(adapter);
		lv.setCacheColorHint(S.applied.backgroundColor);
		lv.setOnItemClickListener(lv_click);
		lv.setEmptyView(emptyView);

		registerForContextMenu(lv);
	}

	void setTitleAndNothingText() {
        String title = null;
        String nothingText = null;
        
        // set title based on filter
        if (filter_kind == Marker.Kind.note) {
            title = getString(R.string.bmcat_notes);
            nothingText = getString(R.string.bl_no_notes_written_yet);
        } else if (filter_kind == Marker.Kind.highlight) {
            title = getString(R.string.bmcat_highlights);
            nothingText = getString(R.string.bl_no_highlighted_verses);
        } else if (filter_kind == Marker.Kind.bookmark) {
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
        
        // if we're using text filter (as opposed to kind filter), we use a different nothingText
        if (currentlyUsedFilter != null) {
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
        }
	}

	boolean searchView_search(String query) {
		query = query.trim();
		if (query.length() == 0) {
			removeFilter();
			return true;
		}
		
		String[] tokens = QueryTokenizer.tokenize(query);
		if (tokens.length == 0) {
			removeFilter();
			return true;
		}
		
		applyFilter(query);
		return true;
	}

	OnClickListener bClearFilter_click = new OnClickListener() {
		@Override public void onClick(View v) {
			searchView.setQuery("", false); //$NON-NLS-1$
			removeFilter();
		}
	};

	protected void removeFilter() {
		adapter.getFilter().filter(null);
		currentlyUsedFilter = null;
		setTitleAndNothingText();
	}
	
	protected void applyFilter(String query) {
		currentlyUsedFilter = query;
		filterUsingCurrentlyUsedFilter();
		setTitleAndNothingText();
	}

	void filterUsingCurrentlyUsedFilter() {
		final ProgressDialog pd = ProgressDialog.show(this, null, getString(R.string.bl_filtering_titiktiga), true, false);
		adapter.getFilter().filter(currentlyUsedFilter, new FilterListener() {
			@Override public void onFilterComplete(int count) {
				pd.dismiss();
			}
		});
	}

	@SuppressWarnings("deprecation") void replaceCursor() {
		if (cursor != null) {
			stopManagingCursor(cursor);
		}
		
		cursor = S.getDb().listMarkers(filter_kind, filter_labelId, sort_column, sort_ascending);
		startManagingCursor(cursor);
		
		if (adapter != null) {
			adapter.changeCursor(cursor);
		}
	}

	protected View getLabelView(FlowLayout panelLabels, Label label) {
		View res = LayoutInflater.from(this).inflate(R.layout.label, null);
		res.setLayoutParams(panelLabels.generateDefaultLayoutParams());
		
		TextView lJudul = V.get(res, R.id.lCaption);
		lJudul.setText(label.title);
		
		U.applyLabelColor(label, lJudul);
		
		return res;
	}

	private void buildMenu(Menu menu) {
		menu.clear();
		getMenuInflater().inflate(R.menu.activity_bookmark_list, menu);
		
        final MenuItem menuSearch = menu.findItem(R.id.menuSearch);
        if (menuSearch != null) {
	        searchView = (SearchView) menuSearch.getActionView();
	        searchView.setQueryHint(getString(R.string.bl_filter_by_some_keywords));
	        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
		        @Override
		        public boolean onQueryTextChange(String newText) {
			        if (newText.length() == 0) {
				        return searchView_search(newText);
			        }
			        return false;
		        }

		        @Override
		        public boolean onQueryTextSubmit(String query) {
			        return searchView_search(query);
		        }
	        });
        }
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		buildMenu(menu);
		
		return true;
	}
	
	@Override public boolean onPrepareOptionsMenu(Menu menu) {
		if (menu != null) {
			buildMenu(menu);
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
		List<String> labels = new ArrayList<>();
		final IntArrayList values = new IntArrayList();
		
		labels.add(getString(R.string.menuSortAri));
		values.add(R.string.menuSortAri);
		
		if (filter_kind == Marker.Kind.bookmark) {
			labels.add(getString(R.string.menuSortTulisan));
			values.add(R.string.menuSortTulisan);
		} else if (filter_kind == Marker.Kind.note) {
			// nop
		} else if (filter_kind == Marker.Kind.highlight) {
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
					sort(Db.Marker.ari, true, value);
					break;
				case R.string.menuSortTulisan:
					sort(Db.Marker.caption, true, value);
					break;
				case R.string.menuSortWaktuTambah:
					sort(Db.Marker.createTime, false, value);
					break;
				case R.string.menuSortWaktuUbah:
					sort(Db.Marker.modifyTime, false, value);
					break;
				}
				dialog.dismiss();
			}

			private void sort(String column, boolean ascending, int columnId) {
				searchView.setQuery("", false); //$NON-NLS-1$
				currentlyUsedFilter = null;
				setTitleAndNothingText();
				sort_column = column;
				sort_ascending = ascending;
				sort_columnId = columnId;
				replaceCursor();
			}
		})
		.setTitle(R.string.menuSort)
		.show();
	}

	private OnItemClickListener lv_click = new OnItemClickListener() {
		@Override public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
			Cursor o = (Cursor) adapter.getItem(position);
			int ari = o.getInt(o.getColumnIndexOrThrow(Db.Marker.ari));
			
			Intent intent = IsiActivity.createIntent(ari);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);
		}
	};
	
	@Override public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		getMenuInflater().inflate(R.menu.context_bookmark_list, menu);
		
		// sesuaikan string berdasarkan jenis.
		android.view.MenuItem menuDeleteBookmark = menu.findItem(R.id.menuDeleteBookmark);
		if (filter_kind == Marker.Kind.bookmark) menuDeleteBookmark.setTitle(R.string.hapus_pembatas_buku);
		if (filter_kind == Marker.Kind.note) menuDeleteBookmark.setTitle(R.string.hapus_catatan);
		if (filter_kind == Marker.Kind.highlight) menuDeleteBookmark.setTitle(R.string.hapus_stabilo);

		android.view.MenuItem menuModifyBookmark = menu.findItem(R.id.menuModifyBookmark);
		if (filter_kind == Marker.Kind.bookmark) menuModifyBookmark.setTitle(R.string.ubah_bukmak);
		if (filter_kind == Marker.Kind.note) menuModifyBookmark.setTitle(R.string.ubah_catatan);
		if (filter_kind == Marker.Kind.highlight) menuModifyBookmark.setTitle(R.string.ubah_stabilo);
	}
	
	@SuppressWarnings("deprecation") @Override public boolean onContextItemSelected(android.view.MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		int itemId = item.getItemId();
		
		if (itemId == R.id.menuDeleteBookmark) {
			// whatever the kind is, the way to delete is the same
			S.getDb().deleteBookmarkById(info.id);
			adapter.getCursor().requery();
			if (currentlyUsedFilter != null) filterUsingCurrentlyUsedFilter();
			
			return true;
		} else if (itemId == R.id.menuModifyBookmark) {
			if (filter_kind == Marker.Kind.bookmark) {
				TypeBookmarkDialog dialog = new TypeBookmarkDialog(this, info.id);
				dialog.setListener(new Listener() {
					@Override public void onOk() {
						adapter.getCursor().requery();
						if (currentlyUsedFilter != null) filterUsingCurrentlyUsedFilter();
					}
				});
				dialog.show();
				
			} else if (filter_kind == Marker.Kind.note) {
				Cursor cursor = (Cursor) adapter.getItem(info.position);
				long _id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));

				TypeNoteDialog dialog = new TypeNoteDialog(this, _id, new TypeNoteDialog.Listener() {
					@Override public void onDone() {
						adapter.getCursor().requery();
						if (currentlyUsedFilter != null) filterUsingCurrentlyUsedFilter();
					}
				});
				dialog.show();
				
			} else if (filter_kind == Marker.Kind.highlight) {
				Cursor cursor = (Cursor) adapter.getItem(info.position);
				int ari = cursor.getInt(cursor.getColumnIndexOrThrow(Db.Marker.ari));
				int colorRgb = U.decodeHighlight(cursor.getString(cursor.getColumnIndexOrThrow(Db.Marker.caption)));
				String reference = S.activeVersion.reference(ari);
				
				new TypeHighlightDialog(this, ari, new TypeHighlightDialog.Listener() {
					@Override public void onOk(int warnaRgb) {
						adapter.getCursor().requery();
						if (currentlyUsedFilter != null) filterUsingCurrentlyUsedFilter();
					}
				}, colorRgb, reference).show();
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
		private int col_caption;
		private int col_createTime;
		private int col_modifyTime;
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
				col_ari = c.getColumnIndexOrThrow(Db.Marker.ari);
				col_caption = c.getColumnIndexOrThrow(Db.Marker.caption);
				col_createTime = c.getColumnIndexOrThrow(Db.Marker.createTime);
				col_modifyTime = c.getColumnIndexOrThrow(Db.Marker.modifyTime);
			}
		}
		
		@Override public View newView(Context context, Cursor cursor, ViewGroup parent) {
			return getLayoutInflater().inflate(R.layout.item_bookmark, null);
		}
		
		@Override public void bindView(View view, Context context, Cursor cursor) {
			TextView lDate = V.get(view, R.id.lDate);
			TextView lCaption = V.get(view, R.id.lCaption);
			TextView lSnippet = V.get(view, R.id.lSnippet);
			FlowLayout panelLabels = V.get(view, R.id.panelLabels);
			
			{
				int addTime_i = cursor.getInt(col_createTime);
				int modifyTime_i = cursor.getInt(col_modifyTime);
				
				if (addTime_i == modifyTime_i) {
					lDate.setText(Sqlitil.toLocaleDateMedium(addTime_i));
				} else {
					lDate.setText(getString(R.string.waktuTambah_edited_waktuUbah, Sqlitil.toLocaleDateMedium(addTime_i), Sqlitil.toLocaleDateMedium(modifyTime_i)));
				}
				
				Appearances.applyBookmarkDateTextAppearance(lDate);
			}
			
			int ari = cursor.getInt(col_ari);
			Book book = S.activeVersion.getBook(Ari.toBook(ari));
			String reference = S.activeVersion.reference(ari);
			
			String verseText = S.activeVersion.loadVerseText(book, Ari.toChapter(ari), Ari.toVerse(ari));
			verseText = U.removeSpecialCodes(verseText);
			
			String caption = cursor.getString(col_caption);
			
			if (filter_kind == Marker.Kind.bookmark) {
				lCaption.setText(currentlyUsedFilter != null? Search2Engine.hilite(caption, filterQueryProvider.getTokens(), hiliteColor): caption);
				Appearances.applyBookmarkTitleTextAppearance(lCaption);
				CharSequence snippet = currentlyUsedFilter != null? Search2Engine.hilite(verseText, filterQueryProvider.getTokens(), hiliteColor): verseText;

				Appearances.applyBookmarkSnippetContentAndAppearance(lSnippet, reference, snippet);
				
				long _id = cursor.getLong(col__id);
				List<Label> labels = S.getDb().listLabelsByMarkerId(_id);
				if (labels != null && labels.size() != 0) {
					panelLabels.setVisibility(View.VISIBLE);
					panelLabels.removeAllViews();
					for (Label label : labels) {
						panelLabels.addView(getLabelView(panelLabels, label));
					}
				} else {
					panelLabels.setVisibility(View.GONE);
				}
				
			} else if (filter_kind == Marker.Kind.note) {
				lCaption.setText(reference);
				Appearances.applyBookmarkTitleTextAppearance(lCaption);
				lSnippet.setText(currentlyUsedFilter != null? Search2Engine.hilite(caption, filterQueryProvider.getTokens(), hiliteColor): caption);
				Appearances.applyTextAppearance(lSnippet);
				
			} else if (filter_kind == Marker.Kind.highlight) {
				lCaption.setText(reference);
				Appearances.applyBookmarkTitleTextAppearance(lCaption);
				
				SpannableStringBuilder snippet = currentlyUsedFilter != null? Search2Engine.hilite(verseText, filterQueryProvider.getTokens(), hiliteColor): new SpannableStringBuilder(verseText);
				int highlightColor = U.decodeHighlight(caption);
				if (highlightColor != -1) {
					snippet.setSpan(new BackgroundColorSpan(U.alphaMixHighlight(highlightColor)), 0, snippet.length(), 0);
				}
				lSnippet.setText(snippet);
				Appearances.applyTextAppearance(lSnippet);
			}
		}
	}
	
	class BukmakFilterQueryProvider implements FilterQueryProvider {
		private String[] tokens;
		
		public String[] getTokens() {
			return tokens;
		}
		
		@Override public Cursor runQuery(CharSequence constraint) {
			if (constraint == null || constraint.length() == 0) {
				this.tokens = null;
				return S.getDb().listMarkers(filter_kind, filter_labelId, sort_column, sort_ascending);
			}
			
			String[] tokens = QueryTokenizer.tokenize(constraint.toString());
			for (int i = 0; i < tokens.length; i++) {
				tokens[i] = tokens[i].toLowerCase(Locale.getDefault());
			}
			this.tokens = tokens;
			
			MatrixCursor res = new MatrixCursor(new String[] {BaseColumns._ID, Db.Marker.ari, Db.Marker.caption, Db.Marker.createTime, Db.Marker.modifyTime});
			Cursor c = S.getDb().listMarkers(filter_kind, filter_labelId, sort_column, sort_ascending);
			try {
				int col__id = c.getColumnIndexOrThrow(BaseColumns._ID);
				int col_ari = c.getColumnIndexOrThrow(Db.Marker.ari);
				int col_caption = c.getColumnIndexOrThrow(Db.Marker.caption);
				int col_createTime = c.getColumnIndexOrThrow(Db.Marker.createTime);
				int col_modifyTime = c.getColumnIndexOrThrow(Db.Marker.modifyTime);
				
				while (c.moveToNext()) {
					boolean fulfills = false;
					
					String caption = c.getString(col_caption);
					
					if (filter_kind != Marker.Kind.highlight) { // "caption" in highlights only stores color information, so it's useless to check
						String tulisan_lc = caption.toLowerCase(Locale.getDefault());
						if (Search2Engine.satisfiesQuery(tulisan_lc, tokens)) {
							fulfills = true;
						}
					}
					
					int ari = c.getInt(col_ari);
					if (!fulfills) {
						// try the verse text!
						String verseText = S.activeVersion.loadVerseText(ari);
						String verseText_lc = verseText.toLowerCase(Locale.getDefault());
						if (Search2Engine.satisfiesQuery(verseText_lc, tokens)) {
							fulfills = true;
						}
					}
					
					if (fulfills) {
						res.newRow()
						.add(c.getLong(col__id))
						.add(ari)
						.add(caption)
						.add(c.getInt(col_createTime))
						.add(c.getInt(col_modifyTime));
					}
				}
			} finally {
				c.close();
			}

			return res;
		}
	}
}
