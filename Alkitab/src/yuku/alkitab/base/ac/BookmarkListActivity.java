package yuku.alkitab.base.ac;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.SearchView;
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
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.dialog.TypeBookmarkDialog;
import yuku.alkitab.base.dialog.TypeBookmarkDialog.Listener;
import yuku.alkitab.base.dialog.TypeHighlightDialog;
import yuku.alkitab.base.dialog.TypeNoteDialog;
import yuku.alkitab.base.storage.Db;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.Appearances;
import yuku.alkitab.base.util.QueryTokenizer;
import yuku.alkitab.base.util.Search2Engine;
import yuku.alkitab.base.util.Sqlitil;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Book;
import yuku.alkitab.model.Bookmark2;
import yuku.alkitab.model.Label;
import yuku.alkitab.util.Ari;
import yuku.alkitab.util.IntArrayList;
import yuku.alkitabintegration.display.Launcher;
import yuku.devoxx.flowlayout.FlowLayout;

import java.util.ArrayList;
import java.util.Date;
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
    
	BookmarkListAdapter adapter;

	String sort_column;
	boolean sort_ascending;
	int sort_columnId;
	String currentlyUsedFilter;

	List<Bookmark2> allBookmarks;
    int filter_kind;
    long filter_labelId;

	int hiliteColor;


    public static Intent createIntent(Context context, int filter_kind, long filter_labelId) {
    	Intent res = new Intent(context, BookmarkListActivity.class);
    	res.putExtra(EXTRA_filter_kind, filter_kind);
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
		
		filter_kind = getIntent().getIntExtra(EXTRA_filter_kind, 0);
		filter_labelId = getIntent().getLongExtra(EXTRA_filter_labelId, 0);
		
		bClearFilter.setOnClickListener(bClearFilter_click);
		
        setTitleAndNothingText();

        // default sort ...
        sort_column = Db.Bookmark2.addTime;
        sort_ascending = false;
        sort_columnId = R.string.menuSortWaktuTambah;

	    { // .. but probably there is a stored preferences about the last sort used
		    String pref_sort_column = Preferences.getString(Prefkey.marker_list_sort_column);
		    if (pref_sort_column != null) {
			    sort_ascending = Preferences.getBoolean(Prefkey.marker_list_sort_ascending, false);
			    switch (pref_sort_column) {
				    case Db.Bookmark2.addTime:
					    sort_column = pref_sort_column;
					    sort_columnId = R.string.menuSortWaktuTambah;
					    break;
				    case Db.Bookmark2.modifyTime:
					    sort_column = pref_sort_column;
					    sort_columnId = R.string.menuSortWaktuUbah;
					    break;
				    case Db.Bookmark2.ari:
					    sort_column = pref_sort_column;
					    sort_columnId = R.string.menuSortAri;
					    break;
				    case Db.Bookmark2.caption:
					    sort_column = pref_sort_column;
					    sort_columnId = R.string.menuSortTulisan;
					    break;
				    default:
					    // do nothing!
			    }
		    }
	    }

	    adapter = new BookmarkListAdapter();
	    loadAndFilter();

		panelList.setBackgroundColor(S.applied.backgroundColor);
		tEmpty.setTextColor(S.applied.fontColor);
		
		hiliteColor = U.getHighlightColorByBrightness(S.applied.backgroundBrightness);
		
		lv.setAdapter(adapter);
		lv.setCacheColorHint(S.applied.backgroundColor);
		lv.setOnItemClickListener(lv_click);
		lv.setEmptyView(emptyView);

		registerForContextMenu(lv);
	}

	private void loadAndFilter() {
		allBookmarks = S.getDb().listBookmarks(filter_kind, filter_labelId, sort_column, sort_ascending);
		filterUsingCurrentlyUsedFilter();
	}

	protected void removeFilter() {
		currentlyUsedFilter = null;
		filterUsingCurrentlyUsedFilter();
		setTitleAndNothingText();
	}

	protected void applyFilter(String query) {
		currentlyUsedFilter = query;
		filterUsingCurrentlyUsedFilter();
		setTitleAndNothingText();
	}

	void filterUsingCurrentlyUsedFilter() {
		final ProgressDialog pd = ProgressDialog.show(this, null, getString(R.string.bl_filtering_titiktiga), true, false);
		adapter.filterAsync(currentlyUsedFilter, new Runnable() {
			@Override
			public void run() {
				pd.dismiss();
			}
		});
	}

	void setTitleAndNothingText() {
        String title = null;
        String nothingText = null;
        
        // set title based on filter
        if (filter_kind == Db.Bookmark2.kind_note) {
            title = getString(R.string.bmcat_notes);
            nothingText = getString(R.string.bl_no_notes_written_yet);
        } else if (filter_kind == Db.Bookmark2.kind_highlight) {
            title = getString(R.string.bmcat_highlights);
            nothingText = getString(R.string.bl_no_highlighted_verses);
        } else if (filter_kind == Db.Bookmark2.kind_bookmark) {
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
		List<String> labels = new ArrayList<String>();
		final IntArrayList values = new IntArrayList();
		
		labels.add(getString(R.string.menuSortAri));
		values.add(R.string.menuSortAri);
		
		if (filter_kind == Db.Bookmark2.kind_bookmark) {
			labels.add(getString(R.string.menuSortTulisan));
			values.add(R.string.menuSortTulisan);
		} else if (filter_kind == Db.Bookmark2.kind_note) {
			// nop
		} else if (filter_kind == Db.Bookmark2.kind_highlight) {
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
				// store for next time use
				Preferences.setString(Prefkey.marker_list_sort_column, column);
				Preferences.setBoolean(Prefkey.marker_list_sort_ascending, ascending);

				searchView.setQuery("", false); //$NON-NLS-1$
				currentlyUsedFilter = null;
				setTitleAndNothingText();
				sort_column = column;
				sort_ascending = ascending;
				sort_columnId = columnId;
				loadAndFilter();
			}
		})
		.setTitle(R.string.menuSort)
		.show();
	}

	private OnItemClickListener lv_click = new OnItemClickListener() {
		@Override public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
			Bookmark2 bookmark = adapter.getItem(position);

			startActivity(Launcher.openAppAtBibleLocationWithVerseSelected(bookmark.ari));
		}
	};
	
	@Override public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		getMenuInflater().inflate(R.menu.context_bookmark_list, menu);
		
		// sesuaikan string berdasarkan jenis.
		android.view.MenuItem menuDeleteBookmark = menu.findItem(R.id.menuDeleteBookmark);
		if (filter_kind == Db.Bookmark2.kind_bookmark) menuDeleteBookmark.setTitle(R.string.hapus_pembatas_buku);
		if (filter_kind == Db.Bookmark2.kind_note) menuDeleteBookmark.setTitle(R.string.hapus_catatan);
		if (filter_kind == Db.Bookmark2.kind_highlight) menuDeleteBookmark.setTitle(R.string.hapus_stabilo);

		android.view.MenuItem menuModifyBookmark = menu.findItem(R.id.menuModifyBookmark);
		if (filter_kind == Db.Bookmark2.kind_bookmark) menuModifyBookmark.setTitle(R.string.ubah_bukmak);
		if (filter_kind == Db.Bookmark2.kind_note) menuModifyBookmark.setTitle(R.string.ubah_catatan);
		if (filter_kind == Db.Bookmark2.kind_highlight) menuModifyBookmark.setTitle(R.string.ubah_stabilo);
	}
	
	@Override public boolean onContextItemSelected(android.view.MenuItem item) {
		final Bookmark2 bookmark = adapter.getItem(((AdapterContextMenuInfo) item.getMenuInfo()).position);
		final int itemId = item.getItemId();
		
		if (itemId == R.id.menuDeleteBookmark) {
			// whatever the kind is, the way to delete is the same
			S.getDb().deleteBookmarkById(bookmark._id);
			loadAndFilter();
			if (currentlyUsedFilter != null) filterUsingCurrentlyUsedFilter();

			return true;
		} else if (itemId == R.id.menuModifyBookmark) {
			if (filter_kind == Db.Bookmark2.kind_bookmark) {
				TypeBookmarkDialog dialog = new TypeBookmarkDialog(this, bookmark._id);
				dialog.setListener(new Listener() {
					@Override public void onOk() {
						loadAndFilter();
						if (currentlyUsedFilter != null) filterUsingCurrentlyUsedFilter();
					}
				});
				dialog.show();
				
			} else if (filter_kind == Db.Bookmark2.kind_note) {
				final int ari = bookmark.ari;

				TypeNoteDialog dialog = new TypeNoteDialog(this, S.activeVersion.getBook(Ari.toBook(ari)), Ari.toChapter(ari), Ari.toVerse(ari), new TypeNoteDialog.Listener() {
					@Override
					public void onDone() {
						loadAndFilter();
						if (currentlyUsedFilter != null) filterUsingCurrentlyUsedFilter();
					}
				});
				dialog.show();

			} else if (filter_kind == Db.Bookmark2.kind_highlight) {
				final int ari = bookmark.ari;
				int colorRgb = U.decodeHighlight(bookmark.caption);
				String reference = S.activeVersion.reference(ari);
				
				new TypeHighlightDialog(this, ari, new TypeHighlightDialog.Listener() {
					@Override public void onOk(int warnaRgb) {
						loadAndFilter();
						if (currentlyUsedFilter != null) filterUsingCurrentlyUsedFilter();
					}
				}, colorRgb, reference).show();
			}
			
			return true;
		}
		
		return false;
	}

	/** The real work of filtering happens here */
	public static List<Bookmark2> filterEngine(List<Bookmark2> allBookmarks, int filter_kind, String[] tokens) {
		List<Bookmark2> res = new ArrayList<>();

		if (tokens == null || tokens.length == 0) {
			res.addAll(allBookmarks);
			return res;
		}

		for (final Bookmark2 bookmark : allBookmarks) {
			if (filter_kind != Db.Bookmark2.kind_highlight) { // "caption" in highlights only stores color information, so it's useless to check
				String caption_lc = bookmark.caption.toLowerCase(Locale.getDefault());
				if (Search2Engine.satisfiesQuery(caption_lc, tokens)) {
					res.add(bookmark);
					continue;
				}
			}

			// try the verse text!
			String verseText = S.activeVersion.loadVerseText(bookmark.ari);
			if (verseText != null) { // this can be null! so beware.
				String verseText_lc = verseText.toLowerCase(Locale.getDefault());
				if (Search2Engine.satisfiesQuery(verseText_lc, tokens)) {
					res.add(bookmark);
				}
			}
		}

		return res;
	}


	class BookmarkListAdapter extends EasyAdapter {
		List<Bookmark2> filteredBookmarks = new ArrayList<>();
		String[] tokens;

		void setupTokens(final String query) {
			if (query == null || query.length() == 0) {
				this.tokens = null;
			} else {
				this.tokens = QueryTokenizer.tokenize(query);
				for (int i = 0; i < tokens.length; i++) {
					tokens[i] = tokens[i].toLowerCase(Locale.getDefault());
				}
			}
		}

		public void filterSync(String query) {
			setupTokens(query);

			filteredBookmarks = filterEngine(allBookmarks, filter_kind, tokens);
			notifyDataSetChanged();
		}

		public void filterAsync(final String query, final Runnable callback) {
			final List<Bookmark2> allBookmarks = BookmarkListActivity.this.allBookmarks;
			final int filter_kind = BookmarkListActivity.this.filter_kind;

			new Thread(new Runnable() {
				@Override
				public void run() {
					setupTokens(query);

					filteredBookmarks = filterEngine(allBookmarks, filter_kind, tokens);

					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							notifyDataSetChanged();
							callback.run();
						}
					});
				}
			}).start();
		}

		@Override
		public Bookmark2 getItem(final int position) {
			return filteredBookmarks.get(position);
		}

		@Override
		public View newView(final int position, final ViewGroup parent) {
			return getLayoutInflater().inflate(R.layout.item_bookmark, null);
		}

		@Override
		public void bindView(final View view, final int position, final ViewGroup parent) {
			TextView lDate = V.get(view, R.id.lDate);
			TextView lCaption = V.get(view, R.id.lCaption);
			TextView lSnippet = V.get(view, R.id.lSnippet);
			FlowLayout panelLabels = V.get(view, R.id.panelLabels);

			final Bookmark2 bookmark = filteredBookmarks.get(position);

			{
				final Date addTime = bookmark.addTime;
				final Date modifyTime = bookmark.modifyTime;

				if (addTime.equals(modifyTime)) {
					lDate.setText(Sqlitil.toLocaleDateMedium(addTime));
				} else {
					lDate.setText(getString(R.string.waktuTambah_edited_waktuUbah, Sqlitil.toLocaleDateMedium(addTime), Sqlitil.toLocaleDateMedium(modifyTime)));
				}

				Appearances.applyBookmarkDateTextAppearance(lDate);
			}

			final int ari = bookmark.ari;
			Book book = S.activeVersion.getBook(Ari.toBook(ari));
			String reference = S.activeVersion.reference(ari);

			String verseText = S.activeVersion.loadVerseText(book, Ari.toChapter(ari), Ari.toVerse(ari));
			if (verseText == null) {
				verseText = getString(R.string.generic_verse_not_available_in_this_version);
			} else {
				verseText = U.removeSpecialCodes(verseText);
			}

			final String caption = bookmark.caption;
			if (filter_kind == Db.Bookmark2.kind_bookmark) {
				lCaption.setText(currentlyUsedFilter != null? Search2Engine.hilite(caption, tokens, hiliteColor): caption);
				Appearances.applyBookmarkTitleTextAppearance(lCaption);
				CharSequence snippet = currentlyUsedFilter != null? Search2Engine.hilite(verseText, tokens, hiliteColor): verseText;

				Appearances.applyBookmarkSnippetContentAndAppearance(lSnippet, reference, snippet);

				final List<Label> labels = S.getDb().listLabelsByBookmarkId(bookmark._id);
				if (labels != null && labels.size() != 0) {
					panelLabels.setVisibility(View.VISIBLE);
					panelLabels.removeAllViews();
					for (Label label : labels) {
						panelLabels.addView(getLabelView(panelLabels, label));
					}
				} else {
					panelLabels.setVisibility(View.GONE);
				}

			} else if (filter_kind == Db.Bookmark2.kind_note) {
				lCaption.setText(reference);
				Appearances.applyBookmarkTitleTextAppearance(lCaption);
				lSnippet.setText(currentlyUsedFilter != null? Search2Engine.hilite(caption, tokens, hiliteColor): caption);
				Appearances.applyTextAppearance(lSnippet);

			} else if (filter_kind == Db.Bookmark2.kind_highlight) {
				lCaption.setText(reference);
				Appearances.applyBookmarkTitleTextAppearance(lCaption);

				SpannableStringBuilder snippet = currentlyUsedFilter != null? Search2Engine.hilite(verseText, tokens, hiliteColor): new SpannableStringBuilder(verseText);
				int highlightColor = U.decodeHighlight(caption);
				if (highlightColor != -1) {
					snippet.setSpan(new BackgroundColorSpan(U.alphaMixHighlight(highlightColor)), 0, snippet.length(), 0);
				}
				lSnippet.setText(snippet);
				Appearances.applyTextAppearance(lSnippet);
			}
		}

		@Override
		public int getCount() {
			return filteredBookmarks.size();
		}
	}

}
