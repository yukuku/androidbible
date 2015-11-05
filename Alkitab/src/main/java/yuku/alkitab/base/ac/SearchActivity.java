package yuku.alkitab.base.ac;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.Toolbar;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;
import com.afollestad.materialdialogs.AlertDialogWrapper;
import com.afollestad.materialdialogs.MaterialDialog;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.model.MVersion;
import yuku.alkitab.base.model.MVersionInternal;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.Appearances;
import yuku.alkitab.base.util.Debouncer;
import yuku.alkitab.base.util.Jumper;
import yuku.alkitab.base.util.QueryTokenizer;
import yuku.alkitab.base.util.SearchEngine;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Book;
import yuku.alkitab.model.Version;
import yuku.alkitab.util.Ari;
import yuku.alkitab.util.IntArrayList;
import yuku.alkitabintegration.display.Launcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SearchActivity extends BaseActivity {
	public static final String TAG = SearchActivity.class.getSimpleName();

	private static final String EXTRA_openedBookId = "openedBookId";
	private static int REQCODE_bookFilter = 1;

	final String COLUMN_QUERY_STRING = "query_string";

	TextView bVersion;
	SearchView searchView;
	ListView lsSearchResults;
	View empty;
	TextView tSearchTips;
	View panelFilter;
	CheckBox cFilterOlds;
	CheckBox cFilterNews;
	CheckBox cFilterSingleBook;
	TextView tFilterAdvanced;
	View bEditFilter;

	Debouncer<String, Pair<IntArrayList, String[]>> searchDebouncer = new Debouncer<String, Pair<IntArrayList, String[]>>(200) {
		@Override
		public Pair<IntArrayList, String[]> process(String query_string) {
			synchronized (SearchActivity.this) {
				String[] tokens = QueryTokenizer.tokenize(query_string);
				SearchEngine.Query query = getQuery();
				if (usingRevIndex()) {
					return new Pair<>(SearchEngine.searchByRevIndex(searchInVersion, query), tokens);
				} else {
					return new Pair<>(SearchEngine.searchByGrep(searchInVersion, query), tokens);
				}
			}
		}

		@Override
		public void onResult(Pair<IntArrayList, String[]> result) {
			//If no token passed, display empty view
			if(result.second == null || result.second.length == 0) {
				lsSearchResults.setAdapter(adapter = null);
			} else {
				lsSearchResults.setAdapter(adapter = new SearchAdapter(result.first, result.second));
			}
		}
	};

	int hiliteColor;
	SparseBooleanArray selectedBookIds = new SparseBooleanArray();
	int openedBookId;
	int filterUserAction = 0; // when it's not user action, set to nonzero
	SearchAdapter adapter;
	Version searchInVersion;
	String searchInVersionId;
	SearchHistoryAdapter searchHistoryAdapter;

	static class SearchHistory {
		public static class Entry {
			public String query_string;
		}

		public List<Entry> entries = new ArrayList<>();
	}

	class SearchHistoryAdapter extends CursorAdapter {
		List<SearchHistory.Entry> entries = new ArrayList<>();
		String query_string;

		public SearchHistoryAdapter() {
			super(App.context, null, 0);
		}

		@Override
		public View newView(final Context context, final Cursor cursor, final ViewGroup parent) {
			return getLayoutInflater().inflate(android.R.layout.simple_list_item_1, parent, false);
		}

		@Override
		public void bindView(final View view, final Context context, final Cursor cursor) {
			TextView text1 = V.get(view, android.R.id.text1);
			text1.setText(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_QUERY_STRING)));
		}

		@Override
		public CharSequence convertToString(final Cursor cursor) {
			return cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_QUERY_STRING));
		}

		public void setData(final SearchHistory searchHistory) {
			entries.clear();
			entries.addAll(searchHistory.entries);
			filter();
		}

		public void setQuery(final String query_string) {
			this.query_string = query_string;
			filter();
		}

		private void filter() {
			final MatrixCursor mc = new MatrixCursor(new String[]{"_id", COLUMN_QUERY_STRING});
			for (int i = 0; i < entries.size(); i++) {
				final SearchHistory.Entry entry = entries.get(i);
				if (TextUtils.isEmpty(query_string) || entry.query_string.toLowerCase().startsWith(query_string.toLowerCase())) {
					mc.addRow(new Object[]{(long) i, entry.query_string});
				}
			}

			// sometimes this is called from bg. So we need to make sure this is run on UI thread.
			runOnUiThread(() -> swapCursor(mc));
		}
	}

	public static Intent createIntent(int openedBookId) {
		Intent res = new Intent(App.context, SearchActivity.class);
		res.putExtra(EXTRA_openedBookId, openedBookId);
		return res;
	}

	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_search);

		lsSearchResults = V.get(this, R.id.lsSearchResults);
		empty = V.get(this, android.R.id.empty);
		tSearchTips = V.get(this, R.id.tSearchTips);
		panelFilter = V.get(this, R.id.panelFilter);
		cFilterOlds = V.get(this, R.id.cFilterOlds);
		cFilterNews = V.get(this, R.id.cFilterNews);
		cFilterSingleBook = V.get(this, R.id.cFilterSingleBook);
		tFilterAdvanced = V.get(this, R.id.tFilterAdvanced);
		bEditFilter = V.get(this, R.id.bEditFilter);

		final Toolbar toolbar = V.get(this, R.id.toolbar);
		setSupportActionBar(toolbar); // must be done first before below lines
		toolbar.setNavigationIcon(R.drawable.abc_ic_ab_back_mtrl_am_alpha);
		toolbar.setNavigationOnClickListener(v -> navigateUp());

		bVersion = V.get(this, R.id.bVersion);

		searchInVersion = S.activeVersion;
		searchInVersionId = S.activeVersionId;
		bVersion.setOnClickListener(bVersion_click);

		searchView = V.get(SearchActivity.this, R.id.searchView);
		searchView.setSubmitButtonEnabled(true);
		final AutoCompleteTextView autoCompleteTextView = findAutoCompleteTextView(searchView);
		if (autoCompleteTextView != null) {
			autoCompleteTextView.setThreshold(0);
		}
		searchView.setSuggestionsAdapter(searchHistoryAdapter = new SearchHistoryAdapter());
		searchView.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
			@Override
			public boolean onSuggestionSelect(final int position) {
				return false;
			}

			@Override
			public boolean onSuggestionClick(final int position) {
				final Cursor c = searchHistoryAdapter.getCursor();
				if (c == null) return false;

				final boolean ok = c.moveToPosition(position);
				if (!ok) return false;

				searchView.setQuery(c.getString(c.getColumnIndexOrThrow(COLUMN_QUERY_STRING)), true);

				return true;
			}
		});
		searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
			@Override
			public boolean onQueryTextSubmit(String query1) {
				search(query1);
				return true;
			}

			@Override
			public boolean onQueryTextChange(String newText) {
				searchHistoryAdapter.setQuery(newText);
				searchDebouncer.submit(newText);
				return false;
			}
		});

		// stop opening suggestion dropdown
		searchView.post(() -> {
			final AutoCompleteTextView searchEditText = findAutoCompleteTextViewRecursive(searchView);
			if (searchEditText != null) {
				searchEditText.dismissDropDown();
			}
		});

		{
			SpannableStringBuilder sb = new SpannableStringBuilder(tSearchTips.getText());
			while (true) {
				final int pos = TextUtils.indexOf(sb, "[q]");
				if (pos < 0) break;
				sb.replace(pos, pos + 3, "\"");
			}
			tSearchTips.setText(sb);
		}

		empty.setBackgroundColor(S.applied.backgroundColor);
		lsSearchResults.setBackgroundColor(S.applied.backgroundColor);
		lsSearchResults.setCacheColorHint(S.applied.backgroundColor);
		lsSearchResults.setEmptyView(empty);
		Appearances.applyTextAppearance(tSearchTips);

		hiliteColor = U.getSearchKeywordTextColorByBrightness(S.applied.backgroundBrightness);

		lsSearchResults.setOnItemClickListener((parent, view, position, id) -> {
			int ari = adapter.getSearchResults().get(position);
			startActivity(Launcher.openAppAtBibleLocationWithVerseSelected(ari));
		});
		bEditFilter.setOnClickListener(v -> bEditFilter_click());
		cFilterOlds.setOnCheckedChangeListener(cFilterOlds_checkedChange);
		cFilterNews.setOnCheckedChangeListener(cFilterNews_checkedChange);
		cFilterSingleBook.setOnCheckedChangeListener(cFilterSingleBook_checkedChange);

		{
			openedBookId = getIntent().getIntExtra(EXTRA_openedBookId, -1);

			final Book book = S.activeVersion.getBook(openedBookId);
			if (book == null) { // active version has changed somehow when this activity fainted. so, invalidate openedBookId
				openedBookId = -1;
				cFilterSingleBook.setEnabled(false);
			} else {
				cFilterSingleBook.setText(getString(R.string.search_bookname_only, book.shortName));
			}
		}

		for (final Book book : searchInVersion.getConsecutiveBooks()) {
			selectedBookIds.put(book.bookId, true);
		}

		configureFilterDisplayOldNewTest();

		if (usingRevIndex()) {
			SearchEngine.preloadRevIndex();
		}

		displaySearchInVersion();
	}

	static AutoCompleteTextView findAutoCompleteTextViewRecursive(final ViewGroup group) {
		for (int i = 0; i < group.getChildCount(); i++) {
			final View child = group.getChildAt(i);
			if (child instanceof AutoCompleteTextView) {
				return (AutoCompleteTextView) child;
			}
			if (child instanceof ViewGroup) {
				final AutoCompleteTextView res = findAutoCompleteTextViewRecursive((ViewGroup) child);
				if (res != null) {
					return res;
				}
			}
		}
		return null;
	}

	AutoCompleteTextView findAutoCompleteTextView(ViewGroup group) {
		for (int i = 0, len = group.getChildCount(); i < len; i++) {
			final View child = group.getChildAt(i);
			if (child instanceof AutoCompleteTextView) {
				return (AutoCompleteTextView) child;
			} else if (child instanceof ViewGroup) {
				return findAutoCompleteTextView((ViewGroup) child);
			}
		}
		return null;
	}

	@Override
	protected void onStart() {
		super.onStart();

		searchHistoryAdapter.setData(loadSearchHistory());
	}

	void displaySearchInVersion() {
		final String versionInitials = S.getVersionInitials(searchInVersion);

		bVersion.setText(versionInitials);
		searchView.setQueryHint(getString(R.string.search_in_version_short_name_placeholder, versionInitials));

		if (adapter != null) {
			adapter.notifyDataSetChanged();
		}
	}

	void configureFilterDisplayOldNewTest() {
		// the following variables will have value:
		// if some are off and some are on -> null.
		// if all on -> true.
		// if all off -> false.
		Boolean olds = null;
		Boolean news = null;
		int oneOfThemOn = -1;

		{
			int c_on = 0, c_off = 0;
			for (int i = 0; i < 39; i++) {
				boolean on = selectedBookIds.get(i, false);
				if (on) c_on++; else c_off++;
			}
			if (c_on == 39) olds = true;
			if (c_off == 39) olds = false;
		}

		{
			int c_on = 0, c_off = 0;
			for (int i = 39; i < 66; i++) {
				boolean nyala = selectedBookIds.get(i, false);
				if (nyala) c_on++; else c_off++;
			}
			if (c_on == 27) news = true;
			if (c_off == 27) news = false;
		}

		{
			int c = 0;
			int k = 0;
			for (int i = 0, len = selectedBookIds.size(); i < len; i++) {
				if (selectedBookIds.valueAt(i)) {
					k = selectedBookIds.keyAt(i);
					c++;
					if (c > 1) break;
				}
			}
			if (c == 1) {
				oneOfThemOn = k;
			}
		}

		filterUserAction++; {
			if (olds != null && news != null) {	// both are either true or false
				cFilterOlds.setVisibility(View.VISIBLE);
				cFilterOlds.setChecked(olds);
				cFilterNews.setVisibility(View.VISIBLE);
				cFilterNews.setChecked(news);
				cFilterSingleBook.setVisibility(View.VISIBLE);
				cFilterSingleBook.setChecked(false);
				tFilterAdvanced.setVisibility(View.GONE);
			} else {
				if (oneOfThemOn != -1 && oneOfThemOn == openedBookId) {
					cFilterOlds.setVisibility(View.VISIBLE);
					cFilterOlds.setChecked(false);
					cFilterNews.setVisibility(View.VISIBLE);
					cFilterNews.setChecked(false);
					cFilterSingleBook.setVisibility(View.VISIBLE);
					cFilterSingleBook.setChecked(true);
					tFilterAdvanced.setVisibility(View.GONE);
				} else {
					cFilterOlds.setVisibility(View.VISIBLE);
					cFilterOlds.setChecked(false);
					cFilterNews.setVisibility(View.VISIBLE);
					cFilterNews.setChecked(false);
					cFilterSingleBook.setVisibility(View.GONE);
					tFilterAdvanced.setVisibility(View.VISIBLE);

					int cnt = 0;
					int bookId = 0;
					for (int i = 0, len = selectedBookIds.size(); i < len; i++) {
						if (selectedBookIds.valueAt(i)) {
							cnt++;
							bookId = selectedBookIds.keyAt(i);
						}
					}
					if (cnt != 1) {
						tFilterAdvanced.setText(getString(R.string.search_filter_multiple_books_selected, cnt));
					} else {
						tFilterAdvanced.setText(searchInVersion.reference(bookId, 0, 0));
					}
				}
			} filterUserAction--;
		}

		final String singleBookReference = searchInVersion.reference(openedBookId, 0, 0);
		cFilterSingleBook.setText(getString(R.string.search_bookname_only, singleBookReference));
	}

	private CompoundButton.OnCheckedChangeListener cFilterOlds_checkedChange = new CompoundButton.OnCheckedChangeListener() {
		@Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			if (filterUserAction != 0) return;

			filterUserAction++; {
				if (isChecked) {
					cFilterSingleBook.setVisibility(View.VISIBLE);
					cFilterSingleBook.setChecked(false);
					tFilterAdvanced.setVisibility(View.GONE);
				}

				setSelectedBookIdsBasedOnFilter();
			} filterUserAction--;
		}
	};

	private CompoundButton.OnCheckedChangeListener cFilterNews_checkedChange = new CompoundButton.OnCheckedChangeListener() {
		@Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			if (filterUserAction != 0) return;

			filterUserAction++; {
				if (isChecked) {
					cFilterSingleBook.setVisibility(View.VISIBLE);
					cFilterSingleBook.setChecked(false);
					tFilterAdvanced.setVisibility(View.GONE);
				}

				setSelectedBookIdsBasedOnFilter();
			} filterUserAction--;
		}
	};

	private CompoundButton.OnCheckedChangeListener cFilterSingleBook_checkedChange = new CompoundButton.OnCheckedChangeListener() {
		@Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			if (filterUserAction != 0) return;

			filterUserAction++; {
				if (isChecked) {
					cFilterOlds.setChecked(false);
					cFilterNews.setChecked(false);
				}

				setSelectedBookIdsBasedOnFilter();
			} filterUserAction--;
		}
	};

	final View.OnClickListener bVersion_click = new View.OnClickListener() {
		@Override
		public void onClick(final View v) {
			S.openVersionsDialog(SearchActivity.this, false, searchInVersionId, new S.VersionDialogListener() {
				@Override
				public void onVersionSelected(final MVersion mv) {
					final Version selectedVersion = mv.getVersion();

					if (selectedVersion == null) {
						new AlertDialogWrapper.Builder(SearchActivity.this)
								.setMessage(getString(R.string.version_error_opening, mv.longName))
								.setPositiveButton(R.string.ok, null)
								.show();
						return;
					}

					searchInVersion = selectedVersion;
					searchInVersionId = mv.getVersionId();

					displaySearchInVersion();
					configureFilterDisplayOldNewTest();
					bVersion.setText(S.getVersionInitials(searchInVersion));
					if (adapter != null) {
						adapter.notifyDataSetChanged();
					}
				}
			});
		}
	};

	protected void setSelectedBookIdsBasedOnFilter() {
		selectedBookIds.clear();
		if (cFilterOlds.isChecked()) for (int i = 0; i < 39; i++) selectedBookIds.put(i, true);
		if (cFilterNews.isChecked()) for (int i = 39; i < 66; i++) selectedBookIds.put(i, true);
		if (openedBookId != -1) {
			if (cFilterSingleBook.isChecked()) selectedBookIds.put(openedBookId, true);
		}
	}

	protected SearchEngine.Query getQuery() {
		SearchEngine.Query res = new SearchEngine.Query();
		res.query_string = searchView.getQuery().toString();
		res.bookIds = selectedBookIds;
		return res;
	}

	public void bEditFilter_click() {
		startActivityForResult(SearchBookFilterActivity.createIntent(selectedBookIds, searchInVersion.getConsecutiveBooks()), REQCODE_bookFilter);
	}

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		if (requestCode == REQCODE_bookFilter && resultCode == RESULT_OK) {
			final SearchBookFilterActivity.Result result = SearchBookFilterActivity.obtainResult(data);
			if (result != null) {
				selectedBookIds = result.selectedBookIds;
				configureFilterDisplayOldNewTest();
			}

			return;
		}

		super.onActivityResult(requestCode, resultCode, data);
	}

	protected void search(final String query_string) {
		if (query_string.trim().length() == 0) {
			return;
		}

		{ // check if there is anything chosen
			int firstSelected = selectedBookIds.indexOfValue(true);
			if (firstSelected < 0) {
				new AlertDialogWrapper.Builder(this)
						.setMessage(R.string.pilih_setidaknya_satu_kitab)
						.setPositiveButton(R.string.ok, null)
						.show();
				return;
			}
		}

		final String[] tokens = QueryTokenizer.tokenize(query_string);

		final MaterialDialog pd = new MaterialDialog.Builder(this)
				.content(getString(R.string.search_searching_tokens, Arrays.toString(tokens)))
				.cancelable(false)
				.progress(true, 0).build();

		new AsyncTask<Void, Void, IntArrayList>() {
			@Override protected IntArrayList doInBackground(Void... params) {
				searchHistoryAdapter.setData(addSearchHistoryEntry(query_string));

				synchronized (SearchActivity.this) {
					if (usingRevIndex()) {
						return SearchEngine.searchByRevIndex(searchInVersion, getQuery());
					} else {
						return SearchEngine.searchByGrep(searchInVersion, getQuery());
					}
				}
			}

			@Override protected void onPostExecute(IntArrayList result) {
				if (result == null) {
					result = new IntArrayList(); // empty result
				}

				lsSearchResults.setAdapter(adapter = new SearchAdapter(result, tokens));

				if (result.size() > 0) {
					Snackbar.make(lsSearchResults, getString(R.string.size_hasil, result.size()), Snackbar.LENGTH_LONG).show();

					//# close soft keyboard
					InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
					inputManager.hideSoftInputFromWindow(searchView.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
					searchView.clearFocus();
					lsSearchResults.requestFocus();
				} else {
					final Jumper jumper = new Jumper(query_string);
					CharSequence noresult = getText(R.string.search_no_result);
					noresult = TextUtils.expandTemplate(noresult, query_string);

					final int fallbackAri = shouldShowFallback(jumper);

					if (fallbackAri != 0) {
						final SpannableStringBuilder sb = new SpannableStringBuilder();
						sb.append(noresult);
						sb.append("\n\n");

						CharSequence fallback = getText(R.string.search_no_result_fallback);
						fallback = TextUtils.expandTemplate(fallback, searchInVersion.reference(fallbackAri));
						sb.append(fallback);

						tSearchTips.setText(sb);
						tSearchTips.setOnClickListener(v -> {
							if (Ari.toVerse(fallbackAri) == 0) {
								startActivity(Launcher.openAppAtBibleLocation(fallbackAri));
							} else {
								startActivity(Launcher.openAppAtBibleLocationWithVerseSelected(fallbackAri));
							}
						});
					} else {
						tSearchTips.setText(noresult);
						tSearchTips.setClickable(false);
						tSearchTips.setOnClickListener(null);
					}
				}

				pd.setOnDismissListener(null);
				pd.dismiss();
			}

			/**
			 * @return ari not 0 if fallback is to be shown
			 */
			int shouldShowFallback(final Jumper jumper) {
				if (!jumper.getParseSucceeded()) {
					return 0;
				}

				final int chapter_1 = jumper.getChapter();
				if (chapter_1 == 0) return 0;

				final Version version = searchInVersion;

				final int bookId = jumper.getBookId(version.getConsecutiveBooks());
				if (bookId == -1) return 0;

				final Book book = version.getBook(bookId);
				if (book == null) return 0;

				if (chapter_1 > book.chapter_count) return 0;

				final int verse_1 = jumper.getVerse();
				if (verse_1 != 0 && verse_1 > book.verse_counts[chapter_1 - 1]) return 0;

				return Ari.encode(bookId, chapter_1, verse_1);
			}
		}.execute();
	}

	SearchHistory loadSearchHistory() {
		final String json = Preferences.getString(Prefkey.searchHistory, null);
		if (json == null) {
			return new SearchHistory();
		}

		return App.getDefaultGson().fromJson(json, SearchHistory.class);
	}

	void saveSearchHistory(SearchHistory sh) {
		final String json = App.getDefaultGson().toJson(sh);
		Preferences.setString(Prefkey.searchHistory, json);
	}

	// returns the modified SearchHistory
	SearchHistory addSearchHistoryEntry(final String query_string) {
		final SearchHistory sh = loadSearchHistory();
		// look for this query_string and remove
		for (int i = sh.entries.size() - 1; i >= 0; i--) {
			if (U.equals(sh.entries.get(i).query_string, query_string)) {
				sh.entries.remove(i);
			}
		}
		final SearchHistory.Entry entry = new SearchHistory.Entry();
		entry.query_string = query_string;
		sh.entries.add(0, entry);

		// if more than MAX, remove last
		while (sh.entries.size() > 20) {
			sh.entries.remove(sh.entries.size() - 1);
		}
		saveSearchHistory(sh);
		return sh;
	}

	boolean usingRevIndex() {
		return searchInVersionId == null || searchInVersionId.equals(MVersionInternal.getVersionInternalId());
	}

	class SearchAdapter extends EasyAdapter {
		IntArrayList searchResults;
		String[] tokens;

		public SearchAdapter(IntArrayList searchResults, String[] tokens) {
			this.searchResults = searchResults;
			this.tokens = tokens;
		}

		@Override
		public int getCount() {
			return searchResults.size();
		}

		@Override public View newView(int position, ViewGroup parent) {
			return getLayoutInflater().inflate(R.layout.item_search_result, parent, false);
		}

		@Override public void bindView(View view, int position, ViewGroup parent) {
			TextView lReference = V.get(view, R.id.lReference);
			TextView lSnippet = V.get(view, R.id.lSnippet);

			int ari = searchResults.get(position);

			final SpannableStringBuilder sb = new SpannableStringBuilder(searchInVersion.reference(ari));
			Appearances.applySearchResultReferenceAppearance(lReference, sb);

			final String verseText = U.removeSpecialCodes(searchInVersion.loadVerseText(ari));
			if (verseText != null) {
				lSnippet.setText(SearchEngine.hilite(verseText, tokens, hiliteColor));
			} else {
				lSnippet.setText(R.string.generic_verse_not_available_in_this_version);
			}

			Appearances.applyTextAppearance(lSnippet);
		}

		IntArrayList getSearchResults() {
			return searchResults;
		}
	}
}
