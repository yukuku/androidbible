package yuku.alkitab.base.ac;

import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.SparseBooleanArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.GridView;
import android.widget.TextView;
import yuku.afw.App;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.afw.widget.EasyAdapter;
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

	int[][] bookCategoryMappings = {
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

	CompoundButton.OnCheckedChangeListener category_checkedChange = new CompoundButton.OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
			int[] bookCategoryMapping = null;
			for (int[] mapping : bookCategoryMappings) {
				if (mapping[0] == buttonView.getId()) {
					bookCategoryMapping = mapping;
					break;
				}
			}
			assert bookCategoryMapping != null;

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

			display();
		}
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

		selectedBookIds = intArrayToSparseBooleanArray(getIntent().getIntArrayExtra(EXTRA_selectedBookIds));
		final Parcelable[] booksParcelable = getIntent().getParcelableArrayExtra(EXTRA_books);
		final Book[] books = new Book[booksParcelable.length];
		//noinspection SuspiciousSystemArraycopy
		System.arraycopy(booksParcelable, 0, books, 0, booksParcelable.length);

		GridView gridBook = V.get(this, R.id.gridBook);
		gridBook.setAdapter(adapter = new BookAdapter(books));
		gridBook.setOnItemClickListener(gridBook_itemClick);

		for (int[] bookCategoryMapping : bookCategoryMappings) {
			V.<CheckBox>get(this, bookCategoryMapping[0]).setOnCheckedChangeListener(category_checkedChange);
		}

		V.get(this, R.id.bOk).setOnClickListener(bOk_click);
		V.get(this, R.id.bCancel).setOnClickListener(bCancel_click);

		display();
	}

	void display() {
		for (int[] bookCategoryMapping : bookCategoryMappings) {
			final CheckBox checkBox = V.get(this, bookCategoryMapping[0]);
			checkBox.setOnCheckedChangeListener(null);

			// all books related to this checkbox are on?
			boolean all_on = true;
			for (int i = 1; i < bookCategoryMapping.length; i++) {
				if (!selectedBookIds.get(bookCategoryMapping[i])) {
					all_on = false;
					break;
				}
			}

			checkBox.setChecked(all_on);
			checkBox.setOnCheckedChangeListener(category_checkedChange);
		}

		adapter.notifyDataSetChanged();
	}

	AdapterView.OnItemClickListener gridBook_itemClick = new AdapterView.OnItemClickListener() {
		@Override
		public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
			final Book book = adapter.getItem(position);
			final boolean oldstate = selectedBookIds.get(book.bookId);
			final boolean newstate = !oldstate;
			selectedBookIds.put(book.bookId, newstate);
			display();
		}
	};

	View.OnClickListener bOk_click = new View.OnClickListener() {
		@Override
		public void onClick(final View v) {
			Intent data = new Intent();
			data.putExtra(EXTRA_selectedBookIds, sparseBooleanArrayToIntArray(selectedBookIds));
			setResult(RESULT_OK, data);
			finish();
		}
	};

	View.OnClickListener bCancel_click = new View.OnClickListener() {
		@Override
		public void onClick(final View v) {
			finish();
		}
	};

	class BookAdapter extends EasyAdapter {
		final Book[] books_grid;

		public BookAdapter(Book[] books) {
			// sort or not based on pref
			if (Preferences.getBoolean(App.context.getString(R.string.pref_alphabeticBookSort_key), App.context.getResources().getBoolean(R.bool.pref_alphabeticBookSort_default))) {
				books_grid = BookNameSorter.sortAlphabetically(books);
			} else {
				books_grid = books.clone();
			}
		}

		@Override
		public View newView(int position, ViewGroup parent) {
			TextView res = new TextView(SearchBookFilterActivity.this);
			res.setLayoutParams(new GridView.LayoutParams(getResources().getDimensionPixelSize(R.dimen.goto_grid_cell_width_book), getResources().getDimensionPixelSize(R.dimen.goto_grid_cell_height)));
			res.setGravity(Gravity.CENTER);
			res.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
			return res;
		}

		@Override
		public void bindView(View view, int position, ViewGroup parent) {
			final TextView lName = (TextView) view;

			final Book book = getItem(position);

			lName.setText(BookNameSorter.getBookAbbr(book));

			if (selectedBookIds.get(book.bookId)) {
				lName.setTextColor(0xffffffff);
				final ColorDrawable color = new ColorDrawable(U.getBackgroundColorByBookId(book.bookId));
				final InsetDrawable bg = new InsetDrawable(color, getResources().getDimensionPixelOffset(R.dimen.goto_grid_cell_inset));
				lName.setBackgroundDrawable(bg);
			} else {
				lName.setTextColor(U.getForegroundColorOnDarkBackgroundByBookId(book.bookId));
				lName.setBackgroundColor(0x0);
			}
		}

		@Override
		public int getCount() {
			return books_grid.length;
		}

		@Override
		public Book getItem(int position) {
			return books_grid[position];
		}

	}
}