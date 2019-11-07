package yuku.alkitab.base.ac;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;
import com.afollestad.materialdialogs.MaterialDialog;
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
import yuku.alkitab.base.util.Debouncer;
import yuku.alkitab.base.util.Highlights;
import yuku.alkitab.base.util.LabelColorUtil;
import yuku.alkitab.base.util.QueryTokenizer;
import yuku.alkitab.base.util.SearchEngine;
import yuku.alkitab.base.util.Sqlitil;
import yuku.alkitab.base.util.TextColorUtil;
import yuku.alkitab.base.widget.VerseRenderer;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Label;
import yuku.alkitab.model.Marker;
import yuku.alkitab.model.Version;
import yuku.alkitab.util.Ari;
import yuku.alkitabintegration.display.Launcher;
import yuku.devoxx.flowlayout.FlowLayout;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MarkerListActivity extends BaseActivity {
	private static final int REQCODE_edit_note = 1;

	// in
	private static final String EXTRA_filter_kind = "filter_kind";
	private static final String EXTRA_filter_labelId = "filter_labelId";

	public static final int LABELID_noLabel = -1;

	/**
	 * Action to broadcast when marker list needs to be reloaded due to some background changes
	 */
	public static final String ACTION_RELOAD = MarkerListActivity.class.getName() + ".action.RELOAD";

	View root;
	View empty;
	TextView tEmpty;
	View bClearFilter;
	View progress;
	SearchView searchView;
	ListView lv;
	View emptyView;

	MarkerListAdapter adapter;

	String sort_column;
	boolean sort_ascending;
	@IdRes int sort_columnId;
	String currentlyUsedFilter;

	List<Marker> allMarkers;
	Marker.Kind filter_kind;
	long filter_labelId;

	int hiliteColor;
	Version version = S.activeVersion();
	String versionId = S.activeVersionId();
	float textSizeMult = S.getDb().getPerVersionSettings(versionId).fontSizeMultiplier;

	public static Intent createIntent(Context context, Marker.Kind filter_kind, long filter_labelId) {
		Intent res = new Intent(context, MarkerListActivity.class);
		res.putExtra(EXTRA_filter_kind, filter_kind.code);
		res.putExtra(EXTRA_filter_labelId, filter_labelId);
		return res;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_marker_list);

		final Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		final ActionBar ab = getSupportActionBar();
		assert ab != null;
		ab.setDisplayHomeAsUpEnabled(true);

		root = findViewById(R.id.root);
		empty = findViewById(android.R.id.empty);
		tEmpty = findViewById(R.id.tEmpty);
		bClearFilter = findViewById(R.id.bClearFilter);
		progress = findViewById(R.id.progress);
		lv = findViewById(android.R.id.list);
		emptyView = findViewById(android.R.id.empty);

		filter_kind = Marker.Kind.fromCode(getIntent().getIntExtra(EXTRA_filter_kind, 0));
		filter_labelId = getIntent().getLongExtra(EXTRA_filter_labelId, 0);

		bClearFilter.setOnClickListener(v -> searchView.setQuery("", true));

		setTitleAndNothingText();

		// default sort ...
		sort_column = Db.Marker.createTime;
		sort_ascending = false;
		sort_columnId = R.id.menuSortCreateTime;

		{ // .. but probably there is a stored preferences about the last sort used
			String pref_sort_column = Preferences.getString(Prefkey.marker_list_sort_column);
			if (pref_sort_column != null) {
				sort_ascending = Preferences.getBoolean(Prefkey.marker_list_sort_ascending, false);
				switch (pref_sort_column) {
					case "waktuTambah": // add time (for compat when upgrading from prev ver)
					case Db.Marker.createTime:
						sort_column = Db.Marker.createTime;
						sort_columnId = R.id.menuSortCreateTime;
						break;
					case "waktuUbah": // modify time (for compat when upgrading from prev ver)
					case Db.Marker.modifyTime:
						sort_column = Db.Marker.modifyTime;
						sort_columnId = R.id.menuSortModifyTime;
						break;
					case Db.Marker.ari:
						sort_column = Db.Marker.ari;
						sort_columnId = R.id.menuSortAri;
						break;
					case "tulisan": // caption (for compat when upgrading from prev ver)
					case Db.Marker.caption:
						sort_column = Db.Marker.caption;
						sort_columnId = R.id.menuSortCaption;
						break;
					default:
						// do nothing!
				}
			}
		}

		adapter = new MarkerListAdapter();
		lv.setAdapter(adapter);
		lv.setCacheColorHint(S.applied().backgroundColor);
		lv.setOnItemClickListener(lv_itemClick);
		lv.setOnItemLongClickListener(lv_itemLongClick);
		lv.setEmptyView(emptyView);

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

		final S.CalculatedDimensions applied = S.applied();

		{ // apply background color, and clear window background to prevent overdraw
			getWindow().setBackgroundDrawableResource(android.R.color.transparent);
			root.setBackgroundColor(applied.backgroundColor);
		}

		tEmpty.setTextColor(applied.fontColor);
		hiliteColor = TextColorUtil.getSearchKeywordByBrightness(applied.backgroundBrightness);

		loadAndFilter();
	}

	void loadAndFilter() {
		loadAndFilter(false);
	}

	void loadAndFilter(final boolean immediate) {
		allMarkers = S.getDb().listMarkers(filter_kind, filter_labelId, sort_column, sort_ascending);

		if (immediate) {
			filter.submit(currentlyUsedFilter, 0);
		} else {
			filter.submit(currentlyUsedFilter);
		}
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

		if (title != null) {
			setTitle(title);
			tEmpty.setText(nothingText);
		} else {
			finish(); // shouldn't happen
		}
	}

	static class FilterResult {
		String query;
		boolean needFilter;
		List<Marker> filteredMarkers;
		SearchEngine.ReadyTokens rt;
	}

	final Debouncer<String, FilterResult> filter = new Debouncer<String, FilterResult>(200) {
		@Override
		public FilterResult process(@Nullable final String payload) {
			final boolean needFilter;

			final String query = payload == null? "": payload.trim();
			if (query.length() == 0) {
				needFilter = false;
			} else {
				final String[] tokens = QueryTokenizer.tokenize(query);
				if (tokens.length == 0) {
					needFilter = false;
				} else {
					needFilter = true;
				}
			}

			final String[] tokens;
			if (query.length() == 0) {
				tokens = null;
			} else {
				tokens = QueryTokenizer.tokenize(query);
			}

			final SearchEngine.ReadyTokens rt = tokens == null || tokens.length == 0 ? null : new SearchEngine.ReadyTokens(tokens);

			final List<Marker> filteredMarkers = filterEngine(version, allMarkers, filter_kind, rt);

			final FilterResult res = new FilterResult();
			res.query = query;
			res.needFilter = needFilter;
			res.filteredMarkers = filteredMarkers;
			res.rt = rt;
			return res;
		}

		@Override
		public void onResult(final FilterResult result) {
			if (result.needFilter) {
				currentlyUsedFilter = result.query;
			} else {
				currentlyUsedFilter = null;
			}

			setTitleAndNothingText();
			adapter.setData(result.filteredMarkers, result.rt);
		}
	};

	public static View getLabelView(LayoutInflater inflater, FlowLayout panelLabels, Label label) {
		final View res = inflater.inflate(R.layout.label, panelLabels, false);
		res.setLayoutParams(panelLabels.generateDefaultLayoutParams());

		final TextView lCaption = res.findViewById(R.id.lCaption);
		lCaption.setText(label.title);

		LabelColorUtil.apply(label, lCaption);

		return res;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_marker_list, menu);

		final MenuItem menuSearch = menu.findItem(R.id.menuSearch);
		searchView = (SearchView) menuSearch.getActionView();
		searchView.setQueryHint(getString(R.string.bl_filter_by_some_keywords));
		searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
			@Override
			public boolean onQueryTextChange(String newText) {
				filter.submit(newText);
				return true;
			}

			@Override
			public boolean onQueryTextSubmit(String query) {
				filter.submit(query);
				return true;
			}
		});

		return true;
	}

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		if (requestCode == REQCODE_edit_note && resultCode == RESULT_OK) {
			loadAndFilter();
			App.getLbm().sendBroadcast(new Intent(IsiActivity.ACTION_ATTRIBUTE_MAP_CHANGED));
		}

		super.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	public boolean onPrepareOptionsMenu(final Menu menu) {
		final MenuItem menuSortCaption = menu.findItem(R.id.menuSortCaption);
		if (filter_kind == Marker.Kind.bookmark) {
			menuSortCaption.setVisible(true);
			menuSortCaption.setTitle(R.string.menuSortCaption);
		} else if (filter_kind == Marker.Kind.highlight) {
			menuSortCaption.setVisible(true);
			menuSortCaption.setTitle(R.string.menuSortCaption_color);
		} else {
			menuSortCaption.setVisible(false);
		}

		checkSortMenuItem(menu, sort_columnId, R.id.menuSortAri);
		checkSortMenuItem(menu, sort_columnId, R.id.menuSortCaption);
		checkSortMenuItem(menu, sort_columnId, R.id.menuSortCreateTime);
		checkSortMenuItem(menu, sort_columnId, R.id.menuSortModifyTime);

		return true;
	}

	private void checkSortMenuItem(final Menu menu, final int checkThis, final int whenThis) {
		if (checkThis == whenThis) {
			final MenuItem item = menu.findItem(whenThis);
			if (item != null) {
				item.setChecked(true);
			}
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		final int itemId = item.getItemId();
		switch (itemId) {
			case R.id.menuSortAri:
				sort(Db.Marker.ari, true, itemId);
				return true;
			case R.id.menuSortCaption:
				sort(Db.Marker.caption, true, itemId);
				return true;
			case R.id.menuSortCreateTime:
				sort(Db.Marker.createTime, false, itemId);
				return true;
			case R.id.menuSortModifyTime:
				sort(Db.Marker.modifyTime, false, itemId);
				return true;
		}

		return super.onOptionsItemSelected(item);
	}

	void sort(String column, boolean ascending, int columnId) {
		// store for next time use
		Preferences.setString(Prefkey.marker_list_sort_column, column);
		Preferences.setBoolean(Prefkey.marker_list_sort_ascending, ascending);

		searchView.setQuery("", true);
		currentlyUsedFilter = null;
		setTitleAndNothingText();
		sort_column = column;
		sort_ascending = ascending;
		sort_columnId = columnId;
		loadAndFilter();

		supportInvalidateOptionsMenu();
	}

	final AdapterView.OnItemClickListener lv_itemClick = (parent, view, position, id) -> {
		Marker marker = adapter.getItem(position);

		startActivity(Launcher.openAppAtBibleLocationWithVerseSelected(marker.ari, marker.verseCount));
	};

	final AdapterView.OnItemLongClickListener lv_itemLongClick = (parent, view, position, id) -> {

		// set menu item titles based on the kind of marker
		String deleteMarker;
		String editMarker;

		switch (filter_kind) {
			case bookmark:
				deleteMarker = getString(R.string.hapus_pembatas_buku);
				editMarker = getString(R.string.edit_bookmark);
				break;
			case note:
				deleteMarker = getString(R.string.hapus_catatan);
				editMarker = getString(R.string.edit_note);
				break;
			case highlight:
				deleteMarker = getString(R.string.hapus_stabilo);
				editMarker = getString(R.string.edit_highlight);
				break;
			default:
				throw new RuntimeException("Unknown kind: " + filter_kind);
		}

		new MaterialDialog.Builder(this)
			.items(deleteMarker, editMarker)
			.itemsCallback((dialog, itemView, which, text) -> {
				final Marker marker = adapter.getItem(position);

				if (which == 0) {
					// whatever the kind is, the way to delete is the same
					S.getDb().deleteMarkerById(marker._id);
					loadAndFilter();
					App.getLbm().sendBroadcast(new Intent(IsiActivity.ACTION_ATTRIBUTE_MAP_CHANGED));

				} else if (which == 1) {
					if (filter_kind == Marker.Kind.bookmark) {
						TypeBookmarkDialog dialog1 = TypeBookmarkDialog.EditExisting(this, marker._id);
						dialog1.setListener(() -> {
							loadAndFilter();
							App.getLbm().sendBroadcast(new Intent(IsiActivity.ACTION_ATTRIBUTE_MAP_CHANGED));
						});
						dialog1.show();

					} else if (filter_kind == Marker.Kind.note) {
						startActivityForResult(NoteActivity.createEditExistingIntent(marker._id), REQCODE_edit_note);

					} else if (filter_kind == Marker.Kind.highlight) {
						final int ari = marker.ari;
						final Highlights.Info info = Highlights.decode(marker.caption);
						final String reference = version.referenceWithVerseCount(ari, marker.verseCount);
						final String rawVerseText = version.loadVerseText(ari);
						final VerseRenderer.FormattedTextResult ftr = new VerseRenderer.FormattedTextResult();

						if (rawVerseText != null) {
							VerseRenderer.render(null, null, ari, rawVerseText, "" + Ari.toVerse(ari), null, false, null, ftr);
						} else {
							ftr.result = ""; // verse not available
						}

						new TypeHighlightDialog(this, ari, newColorRgb -> {
							loadAndFilter();
							App.getLbm().sendBroadcast(new Intent(IsiActivity.ACTION_ATTRIBUTE_MAP_CHANGED));
						}, info.colorRgb, info, reference, ftr.result);
					}
				}
			})
			.show();

		return true;
	};

	/**
	 * The real work of filtering happens here.
	 * @param rt Tokens have to be already lowercased.
	 */
	public static List<Marker> filterEngine(Version version, List<Marker> allMarkers, Marker.Kind filter_kind, @Nullable SearchEngine.ReadyTokens rt) {
		final List<Marker> res = new ArrayList<>();

		if (rt == null) {
			res.addAll(allMarkers);
			return res;
		}

		for (final Marker marker : allMarkers) {
			if (filter_kind != Marker.Kind.highlight) { // "caption" in highlights only stores color information, so it's useless to check
				String caption_lc = marker.caption.toLowerCase(Locale.getDefault());
				if (SearchEngine.satisfiesTokens(caption_lc, rt)) {
					res.add(marker);
					continue;
				}
			}

			// try the verse text!
			String verseText = version.loadVerseText(marker.ari);
			if (verseText != null) { // this can be null! so beware.
				String verseText_lc = verseText.toLowerCase(Locale.getDefault());
				if (SearchEngine.satisfiesTokens(verseText_lc, rt)) {
					res.add(marker);
				}
			}
		}

		return res;
	}

	class MarkerListAdapter extends EasyAdapter {
		List<Marker> filteredMarkers = new ArrayList<>();
		SearchEngine.ReadyTokens rt;

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
			final TextView lDate = view.findViewById(R.id.lDate);
			final TextView lCaption = view.findViewById(R.id.lCaption);
			final TextView lSnippet = view.findViewById(R.id.lSnippet);
			final FlowLayout panelLabels = view.findViewById(R.id.panelLabels);

			final Marker marker = getItem(position);

			{
				final Date createTime = marker.createTime;
				final Date modifyTime = marker.modifyTime;

				final String createTimeDisplay = Sqlitil.toLocaleDateMedium(createTime);

				if (createTime.equals(modifyTime)) {
					lDate.setText(createTimeDisplay);
				} else {
					final String modifyTimeDisplay = Sqlitil.toLocaleDateMedium(modifyTime);
					if (U.equals(createTimeDisplay, modifyTimeDisplay)) {
						// show time for modifyTime when createTime and modifyTime is on the same day
						lDate.setText(getString(R.string.create_edited_modified_time, createTimeDisplay, Sqlitil.toLocaleTime(modifyTime)));
					} else {
						lDate.setText(getString(R.string.create_edited_modified_time, createTimeDisplay, modifyTimeDisplay));
					}
				}

				Appearances.applyMarkerDateTextAppearance(lDate, textSizeMult);
			}

			final int ari = marker.ari;
			final String reference = version.referenceWithVerseCount(ari, marker.verseCount);
			final String caption = marker.caption;

			final String rawVerseText = version.loadVerseText(ari);
			final CharSequence verseText;
			if (rawVerseText == null) {
				verseText = getString(R.string.generic_verse_not_available_in_this_version);
			} else {
				final VerseRenderer.FormattedTextResult ftr = new VerseRenderer.FormattedTextResult();
				VerseRenderer.render(null, null, ari, rawVerseText, "" + Ari.toVerse(ari), null, false, null, ftr);
				verseText = ftr.result;
			}

			if (filter_kind == Marker.Kind.bookmark) {
				lCaption.setText(currentlyUsedFilter != null ? SearchEngine.hilite(caption, rt, hiliteColor) : caption);
				Appearances.applyMarkerTitleTextAppearance(lCaption, textSizeMult);
				CharSequence snippet = currentlyUsedFilter != null ? SearchEngine.hilite(verseText, rt, hiliteColor) : verseText;

				Appearances.applyMarkerSnippetContentAndAppearance(lSnippet, reference, snippet, textSizeMult);

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
				Appearances.applyMarkerTitleTextAppearance(lCaption, textSizeMult);
				lSnippet.setText(currentlyUsedFilter != null ? SearchEngine.hilite(caption, rt, hiliteColor) : caption);
				Appearances.applyTextAppearance(lSnippet, textSizeMult);

			} else if (filter_kind == Marker.Kind.highlight) {
				lCaption.setText(reference);
				Appearances.applyMarkerTitleTextAppearance(lCaption, textSizeMult);

				final SpannableStringBuilder snippet = currentlyUsedFilter != null ? SearchEngine.hilite(verseText, rt, hiliteColor) : new SpannableStringBuilder(verseText);
				final Highlights.Info info = Highlights.decode(caption);
				if (info != null) {
					final BackgroundColorSpan span = new BackgroundColorSpan(Highlights.alphaMix(info.colorRgb));
					if (info.shouldRenderAsPartialForVerseText(verseText)) {
						snippet.setSpan(span, info.partial.startOffset, info.partial.endOffset, 0);
					} else {
						snippet.setSpan(span, 0, snippet.length(), 0);
					}
				}
				lSnippet.setText(snippet);
				Appearances.applyTextAppearance(lSnippet, textSizeMult);
			}
		}

		@Override
		public int getCount() {
			return filteredMarkers.size();
		}

		public void setData(List<Marker> filteredMarkers, SearchEngine.ReadyTokens rt) {
			this.filteredMarkers = filteredMarkers;
			this.rt = rt;

			// set up empty view to make sure it does not show loading progress again
			tEmpty.setVisibility(View.VISIBLE);
			progress.setVisibility(View.GONE);

			notifyDataSetChanged();
		}
	}

}
