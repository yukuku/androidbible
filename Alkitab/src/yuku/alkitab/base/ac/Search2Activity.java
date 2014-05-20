package yuku.alkitab.base.ac;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CheckedTextView;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.TextView;
import android.widget.Toast;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.VersionsActivity.MVersionInternal;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.util.Appearances;
import yuku.alkitab.base.util.BookNameSorter;
import yuku.alkitab.base.util.QueryTokenizer;
import yuku.alkitab.base.util.Search2Engine;
import yuku.alkitab.base.util.Search2Engine.Query;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Book;
import yuku.alkitab.util.Ari;
import yuku.alkitab.util.IntArrayList;
import yuku.alkitabintegration.display.Launcher;
import yuku.androidsdk.searchbar.SearchBar;
import yuku.androidsdk.searchbar.SearchBar.OnSearchListener;

import java.util.Arrays;

public class Search2Activity extends BaseActivity {
	public static final String TAG = Search2Activity.class.getSimpleName();
	
	private static final String EXTRA_openedBookId = "openedBookId"; //$NON-NLS-1$

	ListView lsSearchResults;
	SearchBar searchBar;
	View panelFilter;
	CheckBox cFilterOlds;
	CheckBox cFilterNews;
	CheckBox cFilterSingleBook;
	TextView tFilterAdvanced;
	View bEditFilter;
	
	int hiliteColor;
	SparseBooleanArray selectedBookIds = new SparseBooleanArray();
	int openedBookId;
	int filterUserAction = 0; // when it's not user action, set to nonzero
	Search2Adapter adapter;

	public static Intent createIntent(int openedBookId) {
		Intent res = new Intent(App.context, Search2Activity.class);
		res.putExtra(EXTRA_openedBookId, openedBookId);
		return res;
	}

	@TargetApi(11) class Api11_compat {
		SearchView searchView;

		public void configureSearchView() {
			searchView = V.get(Search2Activity.this, R.id.searchView);
			searchView.setSubmitButtonEnabled(true);
			searchView.setOnQueryTextListener(new OnQueryTextListener() {
				@Override public boolean onQueryTextSubmit(String query) {
					search(query);
					return true;
				}
				
				@Override public boolean onQueryTextChange(String newText) {
					return false;
				}
			});
		}

		public String getSearchViewQuery() {
			return searchView.getQuery().toString();
		}

		public void hideSoftInputFromSearchView(InputMethodManager inputManager) {
			inputManager.hideSoftInputFromWindow(searchView.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
			searchView.clearFocus();
		}

		public void setQueryHint(final String hint) {
			searchView.setQueryHint(hint);
		}
	}
	
	Api11_compat api11_compat;
	
	@Override protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_search2);

		lsSearchResults = V.get(this, R.id.lsSearchResults);
		panelFilter = V.get(this, R.id.panelFilter);
		cFilterOlds = V.get(this, R.id.cFilterOlds);
		cFilterNews = V.get(this, R.id.cFilterNews);
		cFilterSingleBook = V.get(this, R.id.cFilterSingleBook);
		tFilterAdvanced = V.get(this, R.id.tFilterAdvanced);
		bEditFilter = V.get(this, R.id.bEditFilter);
		
		if (usingSearchView()) {
			api11_compat = new Api11_compat();
			api11_compat.configureSearchView();
		} else {
			searchBar = V.get(this, R.id.searchBar);
			((ViewGroup) panelFilter.getParent()).removeView(panelFilter);
			searchBar.setBottomView(panelFilter);
			searchBar.setOnSearchListener(new OnSearchListener() {
				@Override public void onSearch(SearchBar searchBar, Editable text) {
					search(text.toString());
				}
			});
			// the background of the search bar is bright, so let's make all text black
			cFilterOlds.setTextColor(0xff000000);
			cFilterNews.setTextColor(0xff000000);
			cFilterSingleBook.setTextColor(0xff000000);
			tFilterAdvanced.setTextColor(0xff000000);
		}
		
		lsSearchResults.setBackgroundColor(S.applied.backgroundColor);
		lsSearchResults.setCacheColorHint(S.applied.backgroundColor);
		
		hiliteColor = U.getHighlightColorByBrightness(S.applied.backgroundBrightness);
		
		lsSearchResults.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				int ari = adapter.getSearchResults().get(position);
				startActivity(Launcher.openAppAtBibleLocation(ari));
			}
		});
		bEditFilter.setOnClickListener(new OnClickListener() {
			@Override public void onClick(View v) {
				bEditFilter_click();
			}
		});
		cFilterOlds.setOnCheckedChangeListener(cFilterOlds_checkedChange);
		cFilterNews.setOnCheckedChangeListener(cFilterNews_checkedChange);
		cFilterSingleBook.setOnCheckedChangeListener(cFilterSingleBook_checkedChange);
		
		{
			openedBookId = getIntent().getIntExtra(EXTRA_openedBookId, -1);

			Book book = S.activeVersion.getBook(openedBookId);
			cFilterSingleBook.setText(getString(R.string.search_bookname_only, book.shortName));

			for (Book k: S.activeVersion.getConsecutiveBooks()) {
				selectedBookIds.put(k.bookId, true);
			}

			configureFilterDisplayOldNewTest();
		}
		
		if (usingRevIndex()) {
			Search2Engine.preloadRevIndex();
		}

		// show current version on search placeholder
		final ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			final String placeholderVersion;
			final String shortName = S.activeVersion.getShortName();
			if (shortName != null) {
				placeholderVersion = shortName;
			} else {
				placeholderVersion = S.activeVersion.getLongName();
			}

			final String placeholder = getString(R.string.search_in_version_short_name_placeholder, placeholderVersion);

			if (usingSearchView()) {
				api11_compat.setQueryHint(placeholder);
			} else {
				searchBar.getSearchField().setHint(placeholder);
			}
		}
	}
	
	boolean usingSearchView() {
		return VERSION.SDK_INT >= 11;
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
			if (olds != null && news != null) {	// 22nya true atau false
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
					// tidak demikian, kita tulis label saja.
					cFilterOlds.setVisibility(View.VISIBLE);
					cFilterOlds.setChecked(false);
					cFilterNews.setVisibility(View.VISIBLE);
					cFilterNews.setChecked(false);
					cFilterSingleBook.setVisibility(View.GONE);
					tFilterAdvanced.setVisibility(View.VISIBLE);
					StringBuilder sb = new StringBuilder();
					for (int i = 0, len = selectedBookIds.size(); i < len; i++) {
						if (selectedBookIds.valueAt(i) == true) {
							int bookId = selectedBookIds.keyAt(i);
							Book book = S.activeVersion.getBook(bookId);
							if (book != null) {
								if (sb.length() != 0) sb.append(", "); //$NON-NLS-1$
								sb.append(book.shortName);
							}
						}
					}
					tFilterAdvanced.setText(sb);
				}
			} filterUserAction--;
		}
	}

	private OnCheckedChangeListener cFilterOlds_checkedChange = new OnCheckedChangeListener() {
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
	
	private OnCheckedChangeListener cFilterNews_checkedChange = new OnCheckedChangeListener() {
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
	
	private OnCheckedChangeListener cFilterSingleBook_checkedChange = new OnCheckedChangeListener() {
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
	
	protected void setSelectedBookIdsBasedOnFilter() {
		selectedBookIds.clear();
		if (cFilterOlds.isChecked()) for (int i = 0; i < 39; i++) selectedBookIds.put(i, true);
		if (cFilterNews.isChecked()) for (int i = 39; i < 66; i++) selectedBookIds.put(i, true);
		if (cFilterSingleBook.isChecked()) selectedBookIds.put(openedBookId, true);
	}
	
	protected Query getQuery() {
		Query res = new Query();
		if (!usingSearchView()) {
			res.query_string = searchBar.getText().toString();
		} else {
			res.query_string = api11_compat.getSearchViewQuery();
		}
		res.bookIds = selectedBookIds;
		return res;
	}

	public void bEditFilter_click() {
		final SearchFilterAdapter adapter = new SearchFilterAdapter();
		
		final AlertDialog[] dialog = new AlertDialog[1];
		dialog[0] = new AlertDialog.Builder(this)
		.setTitle(R.string.select_books_to_search)
		.setAdapter(adapter, null)
		.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			@Override public void onClick(DialogInterface _unused_, int which) {
				ListView lv = dialog[0].getListView();
				selectedBookIds.clear();
				SparseBooleanArray poses = lv.getCheckedItemPositions();
				for (int i = 0, len = poses.size(); i < len; i++) {
					if (poses.valueAt(i) == true) {
						int position = poses.keyAt(i);
						Book book = adapter.getItem(position);
						if (book != null) {
							selectedBookIds.put(book.bookId, true);
						}
					}
				}
				configureFilterDisplayOldNewTest();
			}
		})
		.setNegativeButton(R.string.cancel, null)
		.show();
		
		final ListView lv = dialog[0].getListView();
		
		// Enable automatic support for multi choice, and also prevent dismissing the dialog because of
		// the click handler set by the alertdialog builder.
		lv.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
		lv.setOnItemClickListener(null);
		
		// set checked items
		for (int position = 0, count = adapter.getCount(); position < count; position++) {
			Book book = adapter.getItem(position);
			if (book != null && selectedBookIds.get(book.bookId, false) == true) {
				lv.setItemChecked(position, true);
			}
		}
	}
	
	class SearchFilterAdapter extends EasyAdapter {
		private Book[] books;

		public SearchFilterAdapter() {
			Book[] books_original = S.activeVersion.getConsecutiveBooks();
			
			if (Preferences.getBoolean(App.context.getString(R.string.pref_alphabeticBookSort_key), App.context.getResources().getBoolean(R.bool.pref_alphabeticBookSort_default))) {
				books = BookNameSorter.sortAlphabetically(books_original);
			} else {
				books = books_original.clone();
			}
		}
		
		@Override public Book getItem(int position) {
			return books[position];
		}

		@Override public int getCount() {
			return books.length;
		}

		@Override public View newView(int position, ViewGroup parent) {
			return getLayoutInflater().inflate(android.R.layout.simple_list_item_multiple_choice, parent, false);
		}

		@Override public void bindView(View view, int position, ViewGroup parent) {
			CheckedTextView text = (CheckedTextView) view;
			
			Book book = getItem(position);
			text.setText(book.shortName);
			text.setTextColor(U.getColorBasedOnBookId(book.bookId));
		}
	}

	protected void search(String query) {
		if (query.trim().length() == 0) {
			return;
		}
		
		// check if there is anything chosen
		{
			int firstSelected = selectedBookIds.indexOfValue(true);
			if (firstSelected < 0) {
				new AlertDialog.Builder(this)
				.setMessage(R.string.pilih_setidaknya_satu_kitab)
				.setPositiveButton(R.string.ok, null)
				.show();
				return;
			}
		}
		
		final String[] tokens = QueryTokenizer.tokenize(query);
		
		final ProgressDialog pd = new ProgressDialog(this);
		pd.setMessage(getString(R.string.search_searching_tokens, Arrays.toString(tokens)));
		pd.setCancelable(false);
		pd.setIndeterminate(true);
		pd.show();
		
		new AsyncTask<Void, Void, IntArrayList>() {
			@Override protected IntArrayList doInBackground(Void... params) {
				synchronized (Search2Activity.this) {
					if (usingRevIndex()) {
						return Search2Engine.searchByRevIndex(getQuery());
					} else {
						return Search2Engine.searchByGrep(getQuery());
					}
				}
			}

			@Override protected void onPostExecute(IntArrayList result) {
				if (result == null) {
					result = new IntArrayList(); // empty result
				}
				
				lsSearchResults.setAdapter(adapter = new Search2Adapter(result, tokens));
				Toast.makeText(Search2Activity.this, getString(R.string.size_hasil, result.size()), Toast.LENGTH_SHORT).show();
				
				if (result.size() > 0) {
					//# close soft keyboard
					InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
					if (!usingSearchView()) {
						inputManager.hideSoftInputFromWindow(searchBar.getSearchField().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
					} else {
						api11_compat.hideSoftInputFromSearchView(inputManager);
						lsSearchResults.requestFocus();
					}
				}
				
				pd.setOnDismissListener(null);
				pd.dismiss();
			};
		}.execute();
	}
	
	boolean usingRevIndex() {
		return S.activeVersionId == null || S.activeVersionId.equals(MVersionInternal.getVersionInternalId());
	}

	class Search2Adapter extends EasyAdapter {
		IntArrayList searchResults;
		String[] tokens;
		
		public Search2Adapter(IntArrayList searchResults, String[] tokens) {
			this.searchResults = searchResults;
			this.tokens = tokens;
		}

		@Override
		public int getCount() {
			return searchResults.size();
		}
		
		@Override public View newView(int position, ViewGroup parent) {
			return getLayoutInflater().inflate(R.layout.item_search2, parent, false);
		}
		
		@Override public void bindView(View view, int position, ViewGroup parent) {
			TextView lReference = V.get(view, R.id.lReference);
			TextView lSnippet = V.get(view, R.id.lSnippet);
			
			int ari = searchResults.get(position);
			Book book = S.activeVersion.getBook(Ari.toBook(ari));
			int chapter_1 = Ari.toChapter(ari);
			int verse_1 = Ari.toVerse(ari);
			SpannableStringBuilder sb = new SpannableStringBuilder(book.reference(chapter_1, verse_1));
			Appearances.applySearchResultReferenceAppearance(lReference, sb);
			
			String verseText = S.activeVersion.loadVerseText(book, chapter_1, verse_1);
			verseText = U.removeSpecialCodes(verseText);
			lSnippet.setText(Search2Engine.hilite(verseText, tokens, hiliteColor));
			Appearances.applyTextAppearance(lSnippet);
		}
		
		IntArrayList getSearchResults() {
			return searchResults;
		}
	}
}
