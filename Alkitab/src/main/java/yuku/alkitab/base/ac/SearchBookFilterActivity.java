package yuku.alkitab.base.ac;

import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.SparseBooleanArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import yuku.afw.App;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.util.BookNameSorter;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Book;

public class SearchBookFilterActivity extends BaseActivity {
	private static final String EXTRA_selectedBookIds = "selectedBookIds";
	private static final String EXTRA_books = "books";

	SparseBooleanArray selectedBookIds;
	BookAdapter adapter;

	static final int[][] bookCategoryMappings = {
		{R.id.cOldTestament, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38},
		{R.id.cOldPentateuch, 0, 1, 2, 3, 4},
		{R.id.cOldHistory, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16},
		{R.id.cOldWisdom, 17, 18, 19, 20, 21},
		{R.id.cOldMajorProphets, 22, 23, 24, 25, 26},
		{R.id.cOldMinorProphets, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38},
		{R.id.cNewTestament, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65},
		{R.id.cNewGospels, 39, 40, 41, 42},
		{R.id.cNewActs, 43},
		{R.id.cNewPaulineEpistles, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56},
		{R.id.cNewGeneralEpistles, 57, 58, 59, 60, 61, 62, 63, 64},
		{R.id.cNewApocalypse, 65},
	};

	public static Intent createIntent(final SparseBooleanArray selectedBookIds, final Book[] books) {
		final Intent res = new Intent(App.context, SearchBookFilterActivity.class);
		res.putExtra(EXTRA_selectedBookIds, sparseBooleanArrayToIntArray(selectedBookIds));
		res.putExtra(EXTRA_books, books);
		return res;
	}

	public static class Result {
		SparseBooleanArray selectedBookIds;
	}

	public static Result obtainResult(Intent data) {
		if (data == null) return null;
		final Result res = new Result();
		res.selectedBookIds = intArrayToSparseBooleanArray(data.getIntArrayExtra(EXTRA_selectedBookIds));
		return res;
	}

	static int[] sparseBooleanArrayToIntArray(final SparseBooleanArray sba) {
		int cnt = 0;
		for (int i = 0, len = sba.size(); i < len; i++) {
			if (sba.valueAt(i)) {
				cnt++;
			}
		}
		int[] ids = new int[cnt];
		cnt = 0;
		for (int i = 0, len = sba.size(); i < len; i++) {
			if (sba.valueAt(i)) {
				ids[cnt++] = sba.keyAt(i);
			}
		}
		return ids;
	}

	static SparseBooleanArray intArrayToSparseBooleanArray(final int[] ids) {
		final SparseBooleanArray sba = new SparseBooleanArray();
		for (final int a : ids) {
			sba.put(a, true);
		}
		return sba;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_search_book_filter);

		final Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		final ActionBar ab = getSupportActionBar();
		assert ab != null;
		ab.setDisplayHomeAsUpEnabled(true);

		selectedBookIds = intArrayToSparseBooleanArray(getIntent().getIntArrayExtra(EXTRA_selectedBookIds));
		final Parcelable[] booksParcelable = getIntent().getParcelableArrayExtra(EXTRA_books);
		final Book[] books = new Book[booksParcelable.length];
		//noinspection SuspiciousSystemArraycopy
		System.arraycopy(booksParcelable, 0, books, 0, booksParcelable.length);

		final RecyclerView gridBook = findViewById(R.id.gridBook);
		adapter = new BookAdapter(books);
		final GridLayoutManager manager = new GridLayoutManager(getApplication(), 6);
		manager.setSpanSizeLookup(adapter.spanSizeLookup);
		gridBook.setLayoutManager(manager);
		gridBook.setAdapter(adapter);

		findViewById(R.id.bOk).setOnClickListener(bOk_click);
		findViewById(R.id.bCancel).setOnClickListener(bCancel_click);
	}

	final View.OnClickListener bOk_click = v -> {
		Intent data = new Intent();
		data.putExtra(EXTRA_selectedBookIds, sparseBooleanArrayToIntArray(selectedBookIds));
		setResult(RESULT_OK, data);
		finish();
	};

	final View.OnClickListener bCancel_click = v -> finish();

	static class VH extends RecyclerView.ViewHolder {
		int viewType;

		public VH(final View itemView, final int viewType) {
			super(itemView);
			this.viewType = viewType;
		}
	}

	class BookAdapter extends RecyclerView.Adapter<VH> {
		public static final int TYPE_CATEGORIES = 1;
		public static final int TYPE_BOOK = 2;

		final Book[] books_grid;

		public GridLayoutManager.SpanSizeLookup spanSizeLookup = new GridLayoutManager.SpanSizeLookup() {
			@Override
			public int getSpanSize(final int position) {
				if (position == 0) {
					return 6;
				}
				return 1;
			}
		};

		public BookAdapter(Book[] books) {
			// sort or not based on pref
			if (Preferences.getBoolean(R.string.pref_alphabeticBookSort_key, R.bool.pref_alphabeticBookSort_default)) {
				books_grid = BookNameSorter.sortAlphabetically(books);
			} else {
				books_grid = books.clone();
			}
		}

		@Override
		public int getItemViewType(final int position) {
			if (position == 0) return TYPE_CATEGORIES;
			return TYPE_BOOK;
		}

		@Override
		public VH onCreateViewHolder(final ViewGroup parent, final int viewType) {
			if (viewType == TYPE_CATEGORIES) {
				return new VH(getLayoutInflater().inflate(R.layout.search_book_filter_categories, parent, false), viewType);
			} else {
				final TextView res = new TextView(SearchBookFilterActivity.this);
				res.setLayoutParams(new GridLayoutManager.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, getResources().getDimensionPixelSize(R.dimen.goto_grid_cell_height)));
				res.setGravity(Gravity.CENTER);
				res.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
				return new VH(res, viewType);
			}
		}

		@Override
		public void onBindViewHolder(final VH holder, final int position) {
			if (holder.viewType == TYPE_CATEGORIES) {
				for (int[] bookCategoryMapping : bookCategoryMappings) {
					final CheckBox checkBox = holder.itemView.findViewById(bookCategoryMapping[0]);
					checkBox.setOnCheckedChangeListener(null);

					{ // show current state
						// all books related to this checkbox are on?
						boolean all_on = true;
						for (int i = 1; i < bookCategoryMapping.length; i++) {
							if (!selectedBookIds.get(bookCategoryMapping[i])) {
								all_on = false;
								break;
							}
						}

						checkBox.setChecked(all_on);
					}

					{ // then, put a change listener
						checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
							// all on?
							boolean all_on = true;
							for (int i = 1; i < bookCategoryMapping.length; i++) {
								if (!selectedBookIds.get(bookCategoryMapping[i])) {
									all_on = false;
									break;
								}
							}

							for (int i = 1; i < bookCategoryMapping.length; i++) {
								selectedBookIds.put(bookCategoryMapping[i], !all_on);
							}

							notifyDataSetChanged();
						});
					}
				}
			} else {
				final TextView lName = (TextView) holder.itemView;
				final Book book = getBookFromPosition(position);

				lName.setText(BookNameSorter.getBookAbbr(book));

				if (selectedBookIds.get(book.bookId)) {
					lName.setTextColor(0xffffffff);
					final ColorDrawable color = new ColorDrawable(U.getBackgroundColorByBookId(book.bookId));
					final InsetDrawable bg = new InsetDrawable(color, getResources().getDimensionPixelOffset(R.dimen.goto_grid_cell_inset));
					//noinspection deprecation
					lName.setBackgroundDrawable(bg);
				} else {
					lName.setTextColor(U.getForegroundColorOnDarkBackgroundByBookId(book.bookId));
					lName.setBackgroundColor(0x0);
				}

				lName.setOnClickListener(v -> {
					final boolean oldstate = selectedBookIds.get(book.bookId);
					final boolean newstate = !oldstate;
					selectedBookIds.put(book.bookId, newstate);
					notifyDataSetChanged();
				});
			}
		}

		@Override
		public int getItemCount() {
			return 1 + books_grid.length;
		}

		public Book getBookFromPosition(int position) {
			return books_grid[position - 1];
		}
	}
}