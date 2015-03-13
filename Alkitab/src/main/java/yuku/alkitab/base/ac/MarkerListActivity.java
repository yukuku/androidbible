package yuku.alkitab.base.ac;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;
import com.afollestad.materialdialogs.AlertDialogWrapper;
import com.afollestad.materialdialogs.MaterialDialog;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.base.App;
import yuku.alkitab.base.IsiActivity;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.dialog.TypeBookmarkDialog;
import yuku.alkitab.base.dialog.TypeHighlightDialog;
import yuku.alkitab.base.storage.Db;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.Appearances;
import yuku.alkitab.base.util.QueryTokenizer;
import yuku.alkitab.base.util.SearchEngine;
import yuku.alkitab.base.util.Sqlitil;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Book;
import yuku.alkitab.model.Label;
import yuku.alkitab.model.Marker;
import yuku.alkitab.util.Ari;
import yuku.alkitab.util.IntArrayList;
import yuku.alkitabintegration.display.Launcher;
import yuku.devoxx.flowlayout.FlowLayout;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MarkerListActivity extends BaseActivity {
	public static final String TAG = MarkerListActivity.class.getSimpleName();

	private static final int REQCODE_edit_note = 1;

	// in
	private static final String EXTRA_filter_kind = "filter_kind";
	private static final String EXTRA_filter_labelId = "filter_labelId";

	public static final int LABELID_noLabel = -1;

	/** Action to broadcast when marker list needs to be reloaded due to some background changes */
	public static final String ACTION_RELOAD = MarkerListActivity.class.getName() + ".action.RELOAD";

	View root;
	View empty;
	TextView tEmpty;
	View bClearFilter;
	SearchView searchView;
	ListView lv;
	View emptyView;

	MarkerListAdapter adapter;

	String sort_column;
	boolean sort_ascending;
	int sort_columnId;
	String currentlyUsedFilter;

	List<Marker> allMarkers;
	Marker.Kind filter_kind;
	long filter_labelId;

	int hiliteColor;


	public static Intent createIntent(Context context, Marker.Kind filter_kind, long filter_labelId) {
		Intent res = new Intent(context, MarkerListActivity.class);
		res.putExtra(EXTRA_filter_kind, filter_kind.code);
		res.putExtra(EXTRA_filter_labelId, filter_labelId);
		return res;
	}

	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreateWithNonToolbarUpButton(savedInstanceState);

		setContentView(R.layout.activity_marker_list);

		root = V.get(this, R.id.root);
		empty = V.get(this, android.R.id.empty);
		tEmpty = V.get(this, R.id.tEmpty);
		bClearFilter = V.get(this, R.id.bClearFilter);
		lv = V.get(this, android.R.id.list);
		emptyView = V.get(this, android.R.id.empty);

		filter_kind = Marker.Kind.fromCode(getIntent().getIntExtra(EXTRA_filter_kind, 0));
		filter_labelId = getIntent().getLongExtra(EXTRA_filter_labelId, 0);

		bClearFilter.setOnClickListener(bClearFilter_click);

		setTitleAndNothingText();

		// default sort ...
		sort_column = Db.Marker.createTime;
		sort_ascending = false;
		sort_columnId = R.string.menuSortCreateTime;

		{ // .. but probably there is a stored preferences about the last sort used
			String pref_sort_column = Preferences.getString(Prefkey.marker_list_sort_column);
			if (pref_sort_column != null) {
				sort_ascending = Preferences.getBoolean(Prefkey.marker_list_sort_ascending, false);
				switch (pref_sort_column) {
					case "waktuTambah": // add time (for compat when upgrading from prev ver)
					case Db.Marker.createTime:
						sort_column = Db.Marker.createTime;
						sort_columnId = R.string.menuSortCreateTime;
						break;
					case "waktuUbah": // modify time (for compat when upgrading from prev ver)
					case Db.Marker.modifyTime:
						sort_column = Db.Marker.modifyTime;
						sort_columnId = R.string.menuSortModifyTime;
						break;
					case Db.Marker.ari:
						sort_column = Db.Marker.ari;
						sort_columnId = R.string.menuSortAri;
						break;
					case "tulisan": // caption (for compat when upgrading from prev ver)
					case Db.Marker.caption:
						sort_column = Db.Marker.caption;
						sort_columnId = R.string.menuSortCaption;
						break;
					default:
						// do nothing!
				}
			}
		}

		adapter = new MarkerListAdapter();
		lv.setAdapter(adapter);
		lv.setCacheColorHint(S.applied.backgroundColor);
		lv.setOnItemClickListener(lv_click);
		lv.setEmptyView(emptyView);

		registerForContextMenu(lv);

		App.getLbm().registerReceiver(br, new IntentFilter(ACTION_RELOAD));
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		App.getLbm().unregisterReceiver(br);
	}

	BroadcastReceiver br = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			if (ACTION_RELOAD.equals(intent.getAction())) {
				loadAndFilter();
			}
		}
	};

	@Override
	protected void onStart() {
		super.onStart();

		{ // apply background color, and clear window background to prevent overdraw
			getWindow().setBackgroundDrawableResource(android.R.color.transparent);
			root.setBackgroundColor(S.applied.backgroundColor);
		}

		tEmpty.setTextColor(S.applied.fontColor);

		hiliteColor = U.getHighlightColorByBrightness(S.applied.backgroundBrightness);

		loadAndFilter();
	}

	private void loadAndFilter() {
		allMarkers = S.getDb().listMarkers(filter_kind, filter_labelId, sort_column, sort_ascending);
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
		final MaterialDialog pd = new MaterialDialog.Builder(this)
			.content(R.string.bl_filtering_titiktiga)
			.cancelable(false)
			.progress(true, 0)
			.show();
		adapter.filterAsync(currentlyUsedFilter, pd::dismiss);
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

	public static View getLabelView(LayoutInflater inflater, FlowLayout panelLabels, Label label) {
		final View res = inflater.inflate(R.layout.label, panelLabels, false);
		res.setLayoutParams(panelLabels.generateDefaultLayoutParams());

		final TextView lCaption = V.get(res, R.id.lCaption);
		lCaption.setText(label.title);

		U.applyLabelColor(label, lCaption);

		return res;
	}

	private void buildMenu(Menu menu) {
		menu.clear();
		getMenuInflater().inflate(R.menu.activity_marker_list, menu);

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

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		if (requestCode == REQCODE_edit_note && resultCode == RESULT_OK) {
			loadAndFilter();
			if (currentlyUsedFilter != null) filterUsingCurrentlyUsedFilter();
			App.getLbm().sendBroadcast(new Intent(IsiActivity.ACTION_ATTRIBUTE_MAP_CHANGED));
		}

		super.onActivityResult(requestCode, resultCode, data);
	}

	private void openSortDialog() {
		final List<String> labels = new ArrayList<>();
		final IntArrayList values = new IntArrayList();

		labels.add(getString(R.string.menuSortAri));
		values.add(R.string.menuSortAri);

		if (filter_kind == Marker.Kind.bookmark) {
			labels.add(getString(R.string.menuSortCaption));
			values.add(R.string.menuSortCaption);
		}

		if (filter_kind == Marker.Kind.highlight) {
			labels.add(getString(R.string.menuSortCaption_color));
			values.add(R.string.menuSortCaption);
		}

		labels.add(getString(R.string.menuSortCreateTime));
		values.add(R.string.menuSortCreateTime);

		labels.add(getString(R.string.menuSortModifyTime));
		values.add(R.string.menuSortModifyTime);

		int selected = -1;
		for (int i = 0, len = values.size(); i < len; i++) {
			if (sort_columnId == values.get(i)) {
				selected = i;
				break;
			}
		}

		new AlertDialogWrapper.Builder(this)
			.setSingleChoiceItems(labels.toArray(new String[labels.size()]), selected, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					if (which == -1) return;
					int value = values.get(which);
					switch (value) {
						case R.string.menuSortAri:
							sort(Db.Marker.ari, true, value);
							break;
						case R.string.menuSortCaption:
							sort(Db.Marker.caption, true, value);
							break;
						case R.string.menuSortCreateTime:
							sort(Db.Marker.createTime, false, value);
							break;
						case R.string.menuSortModifyTime:
							sort(Db.Marker.modifyTime, false, value);
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

	private AdapterView.OnItemClickListener lv_click = new AdapterView.OnItemClickListener() {
		@Override public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
			Marker marker = adapter.getItem(position);

			startActivity(Launcher.openAppAtBibleLocationWithVerseSelected(marker.ari, marker.verseCount));
		}
	};

	@Override public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
		getMenuInflater().inflate(R.menu.context_marker_list, menu);

		// set menu item titles based on the kind of marker
		final MenuItem menuDeleteMarker = menu.findItem(R.id.menuDeleteMarker);
		switch (filter_kind) {
			case bookmark:
				menuDeleteMarker.setTitle(R.string.hapus_pembatas_buku);
				break;
			case note:
				menuDeleteMarker.setTitle(R.string.hapus_catatan);
				break;
			case highlight:
				menuDeleteMarker.setTitle(R.string.hapus_stabilo);
				break;
		}

		final MenuItem menuEditMarker = menu.findItem(R.id.menuEditMarker);
		switch (filter_kind) {
			case bookmark:
				menuEditMarker.setTitle(R.string.edit_bookmark);
				break;
			case note:
				menuEditMarker.setTitle(R.string.edit_note);
				break;
			case highlight:
				menuEditMarker.setTitle(R.string.edit_highlight);
				break;
		}
	}

	@Override public boolean onContextItemSelected(MenuItem item) {
		final Marker marker = adapter.getItem(((AdapterView.AdapterContextMenuInfo) item.getMenuInfo()).position);
		final int itemId = item.getItemId();

		if (itemId == R.id.menuDeleteMarker) {
			// whatever the kind is, the way to delete is the same
			S.getDb().deleteMarkerById(marker._id);
			loadAndFilter();
			if (currentlyUsedFilter != null) filterUsingCurrentlyUsedFilter();
			App.getLbm().sendBroadcast(new Intent(IsiActivity.ACTION_ATTRIBUTE_MAP_CHANGED));

			return true;
		} else if (itemId == R.id.menuEditMarker) {
			if (filter_kind == Marker.Kind.bookmark) {
				TypeBookmarkDialog dialog = TypeBookmarkDialog.EditExisting(this, marker._id);
				dialog.setListener(() -> {
					loadAndFilter();
					if (currentlyUsedFilter != null) filterUsingCurrentlyUsedFilter();
					App.getLbm().sendBroadcast(new Intent(IsiActivity.ACTION_ATTRIBUTE_MAP_CHANGED));
				});
				dialog.show();

			} else if (filter_kind == Marker.Kind.note) {
				startActivityForResult(NoteActivity.createEditExistingIntent(marker._id), REQCODE_edit_note);

			} else if (filter_kind == Marker.Kind.highlight) {
				final int ari = marker.ari;
				int colorRgb = U.decodeHighlight(marker.caption);
				String reference = S.activeVersion.referenceWithVerseCount(ari, marker.verseCount);

				new TypeHighlightDialog(this, ari, newColorRgb -> {
					loadAndFilter();
					if (currentlyUsedFilter != null) filterUsingCurrentlyUsedFilter();
					App.getLbm().sendBroadcast(new Intent(IsiActivity.ACTION_ATTRIBUTE_MAP_CHANGED));
				}, colorRgb, reference);
			}

			return true;
		}

		return false;
	}

	/** The real work of filtering happens here */
	public static List<Marker> filterEngine(List<Marker> allMarkers, Marker.Kind filter_kind, String[] tokens) {
		List<Marker> res = new ArrayList<>();

		if (tokens == null || tokens.length == 0) {
			res.addAll(allMarkers);
			return res;
		}

		for (final Marker marker : allMarkers) {
			if (filter_kind != Marker.Kind.highlight) { // "caption" in highlights only stores color information, so it's useless to check
				String caption_lc = marker.caption.toLowerCase(Locale.getDefault());
				if (SearchEngine.satisfiesQuery(caption_lc, tokens)) {
					res.add(marker);
					continue;
				}
			}

			// try the verse text!
			String verseText = S.activeVersion.loadVerseText(marker.ari);
			if (verseText != null) { // this can be null! so beware.
				String verseText_lc = verseText.toLowerCase(Locale.getDefault());
				if (SearchEngine.satisfiesQuery(verseText_lc, tokens)) {
					res.add(marker);
				}
			}
		}

		return res;
	}


	class MarkerListAdapter extends EasyAdapter {
		List<Marker> filteredMarkers = new ArrayList<>();
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

		public void filterAsync(final String query, final Runnable callback) {
			final List<Marker> allMarkers = MarkerListActivity.this.allMarkers;
			final Marker.Kind filter_kind = MarkerListActivity.this.filter_kind;

			new Thread(new Runnable() {
				@Override
				public void run() {
					setupTokens(query);

					filteredMarkers = filterEngine(allMarkers, filter_kind, tokens);

					runOnUiThread(() -> {
						notifyDataSetChanged();
						callback.run();
					});
				}
			}).start();
		}

		@Override
		public Marker getItem(final int position) {
			return filteredMarkers.get(position);
		}

		@Override
		public View newView(final int position, final ViewGroup parent) {
			return getLayoutInflater().inflate(R.layout.item_marker, parent, false);
		}

		@Override
		public void bindView(final View view, final int position, final ViewGroup parent) {
			final TextView lDate = V.get(view, R.id.lDate);
			final TextView lCaption = V.get(view, R.id.lCaption);
			final TextView lSnippet = V.get(view, R.id.lSnippet);
			final FlowLayout panelLabels = V.get(view, R.id.panelLabels);

			final Marker marker = getItem(position);

			{
				final Date addTime = marker.createTime;
				final Date modifyTime = marker.modifyTime;

				if (addTime.equals(modifyTime)) {
					lDate.setText(Sqlitil.toLocaleDateMedium(addTime));
				} else {
					lDate.setText(getString(R.string.create_edited_modified_time, Sqlitil.toLocaleDateMedium(addTime), Sqlitil.toLocaleDateMedium(modifyTime)));
				}

				Appearances.applyMarkerDateTextAppearance(lDate);
			}

			final int ari = marker.ari;
			final Book book = S.activeVersion.getBook(Ari.toBook(ari));
			final String reference = S.activeVersion.referenceWithVerseCount(ari, marker.verseCount);
			final String caption = marker.caption;

			String verseText = S.activeVersion.loadVerseText(book, Ari.toChapter(ari), Ari.toVerse(ari));
			if (verseText == null) {
				verseText = getString(R.string.generic_verse_not_available_in_this_version);
			} else {
				verseText = U.removeSpecialCodes(verseText);
			}

			if (filter_kind == Marker.Kind.bookmark) {
				lCaption.setText(currentlyUsedFilter != null? SearchEngine.hilite(caption, tokens, hiliteColor): caption);
				Appearances.applyMarkerTitleTextAppearance(lCaption);
				CharSequence snippet = currentlyUsedFilter != null? SearchEngine.hilite(verseText, tokens, hiliteColor): verseText;

				Appearances.applyMarkerSnippetContentAndAppearance(lSnippet, reference, snippet);

				final List<Label> labels = S.getDb().listLabelsByMarker(marker);
				if (labels.size() != 0) {
					panelLabels.setVisibility(View.VISIBLE);
					panelLabels.removeAllViews();
					for (Label label : labels) {
						panelLabels.addView(getLabelView(getLayoutInflater(), panelLabels, label));
					}
				} else {
					panelLabels.setVisibility(View.GONE);
				}

			} else if (filter_kind == Marker.Kind.note) {
				lCaption.setText(reference);
				Appearances.applyMarkerTitleTextAppearance(lCaption);
				lSnippet.setText(currentlyUsedFilter != null? SearchEngine.hilite(caption, tokens, hiliteColor): caption);
				Appearances.applyTextAppearance(lSnippet);

			} else if (filter_kind == Marker.Kind.highlight) {
				lCaption.setText(reference);
				Appearances.applyMarkerTitleTextAppearance(lCaption);

				SpannableStringBuilder snippet = currentlyUsedFilter != null? SearchEngine.hilite(verseText, tokens, hiliteColor): new SpannableStringBuilder(verseText);
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
			return filteredMarkers.size();
		}
	}

}
