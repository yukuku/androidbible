package yuku.alkitab.base.ac;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.UnderlineSpan;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.TextView;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.ColorUtils;
import androidx.cursoradapter.widget.CursorAdapter;
import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import yuku.afw.storage.Preferences;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.model.MVersionInternal;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.Appearances;
import yuku.alkitab.base.util.ClipboardUtil;
import yuku.alkitab.base.util.FormattedVerseText;
import yuku.alkitab.base.util.Jumper;
import static yuku.alkitab.base.util.Literals.Array;
import yuku.alkitab.base.util.QueryTokenizer;
import yuku.alkitab.base.util.SearchEngine;
import yuku.alkitab.base.util.TextColorUtil;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Book;
import yuku.alkitab.model.Version;
import yuku.alkitab.util.Ari;
import yuku.alkitab.util.IntArrayList;
import yuku.alkitabintegration.display.Launcher;

public class SearchActivity extends BaseActivity {
    private static final String EXTRA_openedBookId = "openedBookId";
    private static final int REQCODE_bookFilter = 1;

    private static final long ID_CLEAR_HISTORY = -1L;
    private static final int COLINDEX_ID = 0;
    private static final int COLINDEX_QUERY_STRING = 1;

    View root;
    TextView bVersion;
    SearchView searchView;
    ListView lsSearchResults;
    TextView tSearchTips;
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
    SearchAdapter adapter;
    Version searchInVersion;
    String searchInVersionId;
    float textSizeMult;
    SearchHistoryAdapter searchHistoryAdapter;
    ActionMode actionMode;

    final AdapterView.OnItemLongClickListener lsSearchResults_itemLongClick = (parent, view, position, id) -> {
        if (actionMode == null) {
            actionMode = startSupportActionMode(new ActionMode.Callback() {
                @Override
                public boolean onCreateActionMode(final ActionMode mode, final Menu menu) {
                    getMenuInflater().inflate(R.menu.context_search, menu);
                    return true;
                }

                @Override
                public boolean onPrepareActionMode(final ActionMode mode, final Menu menu) {
                    final int checked_count = lsSearchResults.getCheckedItemCount();

                    if (checked_count == 1) {
                        mode.setTitle(R.string.verse_select_one_verse_selected);
                    } else {
                        mode.setTitle(getString(R.string.verse_select_multiple_verse_selected, checked_count));
                    }

                    return true;
                }

                @Override
                public boolean onActionItemClicked(final ActionMode mode, final MenuItem item) {
                    final int itemId = item.getItemId();
                    if (itemId == R.id.menuSelectAll) {
                        for (int i = 0, size = adapter.getCount(); i < size; i++) {
                            lsSearchResults.setItemChecked(i, true);
                        }
                        onCheckedVerseChanged();

                    } else if (itemId == R.id.menuCopy) {
                        final SpannableStringBuilder sb = new SpannableStringBuilder();

                        final IntArrayList aris = adapter.getSearchResults();
                        final SparseBooleanArray checkeds = lsSearchResults.getCheckedItemPositions();
                        for (int i = 0, size = checkeds.size(); i < size; i++) {
                            if (!checkeds.valueAt(i)) continue;
                            final int position = checkeds.keyAt(i);
                            final int ari = aris.get(position);

                            final String reference = searchInVersion.reference(ari);
                            final String verseText = FormattedVerseText.removeSpecialCodes(searchInVersion.loadVerseText(ari));

                            final int sb_len = sb.length();
                            sb.append(reference).append("\n").append(verseText).append("\n\n");

                            if (size < 1000) { // too much spans is very slow
                                sb.setSpan(new UnderlineSpan(), sb_len, sb_len + reference.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            }
                        }

                        ClipboardUtil.copyToClipboard(sb);
                        Snackbar.make(root, R.string.search_selected_verse_copied, Snackbar.LENGTH_SHORT).show();

                        mode.finish();
                        return true;
                    }
                    return false;
                }

                @Override
                public void onDestroyActionMode(final ActionMode mode) {
                    uncheckAllVerses();
                    actionMode = null;
                }
            });
        }

        final boolean old = lsSearchResults.isItemChecked(position);
        lsSearchResults.setItemChecked(position, !old);

        onCheckedVerseChanged();

        return true;
    };

    private void uncheckAllVerses() {
        final SparseBooleanArray checkeds = lsSearchResults.getCheckedItemPositions();
        for (int i = checkeds.size() - 1; i >= 0; i--) {
            if (checkeds.valueAt(i)) lsSearchResults.setItemChecked(checkeds.keyAt(i), false);
        }
    }

    private void onCheckedVerseChanged() {
        adapter.notifyDataSetChanged();
        if (actionMode != null) {
            if (lsSearchResults.getCheckedItemCount() == 0) {
                actionMode.finish();
            } else {
                actionMode.invalidate();
            }
        }
    }

    @Keep
    static class SearchHistory {
        @Keep
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
            final TextView text1 = view.findViewById(android.R.id.text1);
            final long _id = cursor.getLong(COLINDEX_ID);

            final CharSequence text;
            if (_id == -1) {
                final SpannableStringBuilder sb = new SpannableStringBuilder(getString(R.string.search_clear_history));
                sb.setSpan(new ForegroundColorSpan(ResourcesCompat.getColor(getResources(), R.color.escape, getTheme())), 0, sb.length(), 0);
                text = sb;
            } else {
                text = cursor.getString(COLINDEX_QUERY_STRING);
            }

            text1.setText(text);
        }

        @Override
        public CharSequence convertToString(final Cursor cursor) {
            return cursor.getString(COLINDEX_QUERY_STRING);
        }

        public void setData(@NonNull final SearchHistory searchHistory) {
            entries.clear();
            entries.addAll(searchHistory.entries);
            filter();
        }

        public void setQuery(final String query_string) {
            this.query_string = query_string;
            filter();
        }

        private void filter() {
            final MatrixCursor mc = new MatrixCursor(Array("_id", "query_string") /* Can be any string, but this must correspond to COLINDEX_ID and COLINDEX_QUERY_STRING */);
            for (int i = 0; i < entries.size(); i++) {
                final SearchHistory.Entry entry = entries.get(i);
                if (TextUtils.isEmpty(query_string) || entry.query_string.toLowerCase().startsWith(query_string.toLowerCase())) {
                    mc.addRow(Array((long) i, entry.query_string));
                }
            }

            // add last item to clear search history only if there is something else
            if (mc.getCount() > 0) {
                mc.addRow(Array(ID_CLEAR_HISTORY, ""));
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

        root = findViewById(R.id.root);
        lsSearchResults = findViewById(R.id.lsSearchResults);
        tSearchTips = findViewById(R.id.tSearchTips);
        panelFilter = findViewById(R.id.panelFilter);
        cFilterOlds = findViewById(R.id.cFilterOlds);
        cFilterNews = findViewById(R.id.cFilterNews);
        cFilterSingleBook = findViewById(R.id.cFilterSingleBook);
        tFilterAdvanced = findViewById(R.id.tFilterAdvanced);
        bEditFilter = findViewById(R.id.bEditFilter);

        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        final ActionBar ab = getSupportActionBar();
        assert ab != null;
        ab.setDisplayHomeAsUpEnabled(true);

        bVersion = findViewById(R.id.bVersion);

        searchInVersion = S.activeVersion();
        searchInVersionId = S.activeVersionId();
        textSizeMult = S.getDb().getPerVersionSettings(searchInVersionId).fontSizeMultiplier;
        bVersion.setOnClickListener(bVersion_click);

        searchView = findViewById(R.id.searchView);
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

                final long _id = c.getLong(COLINDEX_ID);
                if (_id == ID_CLEAR_HISTORY) {
                    saveSearchHistory(null);
                    searchHistoryAdapter.setData(loadSearchHistory());
                } else {
                    searchView.setQuery(c.getString(COLINDEX_QUERY_STRING), true);
                }

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

        final S.CalculatedDimensions applied = S.applied();

        tSearchTips.setBackgroundColor(applied.backgroundColor);

        lsSearchResults.setBackgroundColor(applied.backgroundColor);
        lsSearchResults.setCacheColorHint(applied.backgroundColor);
        lsSearchResults.setEmptyView(tSearchTips);
        Appearances.applyTextAppearance(tSearchTips, textSizeMult);

        hiliteColor = TextColorUtil.getSearchKeywordByBrightness(applied.backgroundBrightness);

        lsSearchResults.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
        lsSearchResults.setOnItemClickListener((parent, view, position, id) -> {
            if (actionMode != null) {
                // By default setItemChecked will be called when action mode is on.
                // We just need to invalidate the view and the selected verse count.
                onCheckedVerseChanged();
            } else {
                final int ari = adapter.getSearchResults().get(position);
                startActivity(Launcher.openAppAtBibleLocationWithVerseSelected(ari));
                // Because we are in CHOICE_MODE_MULTIPLE, this verse is automatically marked as checked.
                // so we have to manually uncheck this.
                uncheckAllVerses();
            }
        });
        lsSearchResults.setOnItemLongClickListener(lsSearchResults_itemLongClick);

        bEditFilter.setOnClickListener(v -> bEditFilter_click());
        cFilterOlds.setOnCheckedChangeListener(cFilterOlds_checkedChange);
        cFilterNews.setOnCheckedChangeListener(cFilterNews_checkedChange);
        cFilterSingleBook.setOnCheckedChangeListener(cFilterSingleBook_checkedChange);

        {
            openedBookId = getIntent().getIntExtra(EXTRA_openedBookId, -1);

            final Book book = S.activeVersion().getBook(openedBookId);
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
        final String versionInitials = searchInVersion.getInitials();

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

    private final CompoundButton.OnCheckedChangeListener cFilterOlds_checkedChange = new CompoundButton.OnCheckedChangeListener() {
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

    private final CompoundButton.OnCheckedChangeListener cFilterNews_checkedChange = new CompoundButton.OnCheckedChangeListener() {
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

    private final CompoundButton.OnCheckedChangeListener cFilterSingleBook_checkedChange = new CompoundButton.OnCheckedChangeListener() {
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

    final View.OnClickListener bVersion_click = v -> S.openVersionsDialog(this, false, searchInVersionId, mv -> {
        final Version selectedVersion = mv.getVersion();

        if (selectedVersion == null) {
            new MaterialDialog.Builder(SearchActivity.this)
                .content(getString(R.string.version_error_opening, mv.longName))
                .positiveText(R.string.ok)
                .show();
            return;
        }

        searchInVersion = selectedVersion;
        searchInVersionId = mv.getVersionId();
        textSizeMult = S.getDb().getPerVersionSettings(searchInVersionId).fontSizeMultiplier;
        Appearances.applyTextAppearance(tSearchTips, textSizeMult);

        displaySearchInVersion();
        configureFilterDisplayOldNewTest();
        bVersion.setText(selectedVersion.getInitials());
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    });

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

    protected void search(@NonNull final String query_string) {
        if (query_string.trim().length() == 0) {
            return;
        }

        { // check if there is anything chosen
            int firstSelected = selectedBookIds.indexOfValue(true);
            if (firstSelected < 0) {
                new MaterialDialog.Builder(this)
                    .content(R.string.pilih_setidaknya_satu_kitab)
                    .positiveText(R.string.ok)
                    .show();
                return;
            }
        }

        final String[] tokens = QueryTokenizer.tokenize(query_string);

        final MaterialDialog pd = new MaterialDialog.Builder(this)
            .content(getString(R.string.search_searching_tokens, Arrays.toString(tokens)))
            .cancelable(false)
            .progress(true, 0)
            .show();

        new AsyncTask<Void, Void, IntArrayList>() {
            boolean debugstats_revIndexUsed;
            long debugstats_totalTimeMs;
            long debugstats_cpuTimeMs;

            @Override protected IntArrayList doInBackground(Void... params) {
                searchHistoryAdapter.setData(addSearchHistoryEntry(query_string));

                final long totalMs = System.currentTimeMillis();
                final long cpuMs = SystemClock.currentThreadTimeMillis();
                final IntArrayList res;

                synchronized (SearchActivity.this) {
                    if (usingRevIndex()) {
                        debugstats_revIndexUsed = true;
                        res = SearchEngine.searchByRevIndex(searchInVersion, getQuery());
                    } else {
                        debugstats_revIndexUsed = false;
                        res = SearchEngine.searchByGrep(searchInVersion, getQuery());
                    }
                }

                debugstats_totalTimeMs = System.currentTimeMillis() - totalMs;
                debugstats_cpuTimeMs = SystemClock.currentThreadTimeMillis() - cpuMs;

                return res;
            }

            @Override protected void onPostExecute(IntArrayList result) {
                if (result == null) {
                    result = new IntArrayList(); // empty result
                }

                if (actionMode != null) {
                    actionMode.finish();
                }

                uncheckAllVerses();
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

                if (BuildConfig.DEBUG) {
                    new MaterialDialog.Builder(SearchActivity.this)
                        .content("This msg is shown only on DEBUG build\n\n" +
                            "Search results: " + result.size() + "\n" +
                            "Method: " + (debugstats_revIndexUsed? "revindex": "grep") + "\n" +
                            "Total time: " + debugstats_totalTimeMs + " ms\n" +
                            "CPU (thread) time: " + debugstats_cpuTimeMs + " ms")
                        .positiveText(R.string.ok)
                        .show();
                }

                pd.setOnDismissListener(null);
                try {
                    pd.dismiss();
                } catch (Exception ignored) {
                }
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

    @NonNull SearchHistory loadSearchHistory() {
        final String json = Preferences.getString(Prefkey.searchHistory, null);
        if (json == null) {
            return new SearchHistory();
        }

        return App.getDefaultGson().fromJson(json, SearchHistory.class);
    }

    void saveSearchHistory(@Nullable SearchHistory sh) {
        if (sh == null) {
            Preferences.remove(Prefkey.searchHistory);
        } else {
            final String json = App.getDefaultGson().toJson(sh);
            Preferences.setString(Prefkey.searchHistory, json);
        }
    }

    // returns the modified SearchHistory
    SearchHistory addSearchHistoryEntry(final String query_string) {
        final SearchHistory sh = loadSearchHistory();
        // look for this query_string and remove
        for (int i = sh.entries.size() - 1; i >= 0; i--) {
            if (query_string.equals(sh.entries.get(i).query_string)) {
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
        final IntArrayList searchResults;
        final SearchEngine.ReadyTokens rt;

        public SearchAdapter(IntArrayList searchResults, String[] tokens) {
            this.searchResults = searchResults;
            this.rt = tokens == null ? null : new SearchEngine.ReadyTokens(tokens);
        }

        @Override
        public int getCount() {
            return searchResults.size();
        }

        @Override public View newView(int position, ViewGroup parent) {
            return getLayoutInflater().inflate(R.layout.item_search_result, parent, false);
        }

        @Override public void bindView(View view, int position, ViewGroup parent) {
            final boolean checked = lsSearchResults.isItemChecked(position);
            final int checkedBgColor;
            final int checkedTextColor;

            if (checked) {
                final int colorRgb = Preferences.getInt(R.string.pref_selectedVerseBgColor_key, R.integer.pref_selectedVerseBgColor_default);
                checkedBgColor = ColorUtils.setAlphaComponent(colorRgb, 0xa0);
                checkedTextColor = TextColorUtil.getForCheckedVerse(checkedBgColor);
            } else {
                // no need to calculate
                checkedBgColor = 0;
                checkedTextColor = 0;
            }

            final TextView lReference = view.findViewById(R.id.lReference);
            final TextView lSnippet = view.findViewById(R.id.lSnippet);

            final int ari = searchResults.get(position);

            final SpannableStringBuilder sb = new SpannableStringBuilder(searchInVersion.reference(ari));
            Appearances.applySearchResultReferenceAppearance(lReference, sb, textSizeMult);
            if (checked) {
                lReference.setTextColor(checkedTextColor);
            }

            Appearances.applyTextAppearance(lSnippet, textSizeMult);
            if (checked) {
                lSnippet.setTextColor(checkedTextColor);
            }

            final String verseText = FormattedVerseText.removeSpecialCodes(searchInVersion.loadVerseText(ari));
            if (verseText != null) {
                lSnippet.setText(SearchEngine.hilite(verseText, rt, checked? checkedTextColor: hiliteColor));
            } else {
                lSnippet.setText(R.string.generic_verse_not_available_in_this_version);
            }


            if (checked) {
                view.setBackgroundColor(checkedBgColor);
            } else {
                view.setBackgroundColor(0x0);
            }
        }

        IntArrayList getSearchResults() {
            return searchResults;
        }
    }
}
