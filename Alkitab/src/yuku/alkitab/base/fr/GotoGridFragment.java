package yuku.alkitab.base.fr;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.style.UnderlineSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.TextView;
import yuku.afw.App;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.fr.base.BaseGotoFragment;
import yuku.alkitab.base.util.BookNameSorter;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Book;

public class GotoGridFragment extends BaseGotoFragment {
	public static final String TAG = GotoGridFragment.class.getSimpleName();
	
	private static final String EXTRA_verse = "verse"; //$NON-NLS-1$
	private static final String EXTRA_chapter = "chapter"; //$NON-NLS-1$
	private static final String EXTRA_bookId = "bookId"; //$NON-NLS-1$

	private static final int ANIM_DURATION = 200;

	View panelChapterVerse;
	TextView lSelectedBook;
	TextView lSelectedChapter;
	GridView gridBook;
	GridView gridChapter;
	GridView gridVerse;

	Book[] books;
	BookAdapter bookAdapter;
	ChapterAdapter chapterAdapter;
	VerseAdapter verseAdapter;
	
	Book selectedBook;
	int selectedChapter;
	
	private AdapterView.OnItemClickListener gridBook_itemClick = new AdapterView.OnItemClickListener() {
		@Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			Book book = bookAdapter.getItem(position);
			selectedBook = book;
			transitionBookToChapter();
		}
	};
	
	private AdapterView.OnItemClickListener gridChapter_itemClick = new AdapterView.OnItemClickListener() {
		@Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			selectedChapter = position + 1;
			transitionChapterToVerse();
		}
	};

	private AdapterView.OnItemClickListener gridVerse_itemClick = new AdapterView.OnItemClickListener() {
		@Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			int selectedVerse = position + 1;
			((GotoFinishListener) getActivity()).onGotoFinished(GotoFinishListener.GOTO_TAB_grid, selectedBook.bookId, selectedChapter, selectedVerse); 
		}
	};
	
	private View.OnClickListener lSelectedBook_click = new View.OnClickListener() {
		@Override public void onClick(View v) {
			selectedBook = null;
			selectedChapter = 0;
			transitionChapterToBook();
		}
	};
	
	private View.OnClickListener lSelectedChapter_click = new View.OnClickListener() {
		@Override public void onClick(View v) {
			selectedChapter = 0;
			transitionVerseToChapter();
		}
	};
	
	@TargetApi(12) void transitionBookToChapter() {
		gridBook.setVisibility(View.INVISIBLE);
		panelChapterVerse.setVisibility(View.VISIBLE);
		gridChapter.setVisibility(View.VISIBLE);
		gridChapter.setAdapter(chapterAdapter = new ChapterAdapter(selectedBook));
		gridVerse.setVisibility(View.INVISIBLE);
		
		if (Build.VERSION.SDK_INT >= 12) { // animate
			animateFadeOutAndSlideLeft(gridBook, gridChapter);
			lSelectedBook.setAlpha(0.f);
			lSelectedBook.animate().alpha(1.f).setDuration(ANIM_DURATION);
		}
		
		displaySelectedBookAndChapter();
	}

	void transitionChapterToBook() {
		// TODO Animate
		gridBook.setVisibility(View.VISIBLE);
		panelChapterVerse.setVisibility(View.INVISIBLE);
	}
	
	void transitionChapterToVerse() {
		gridBook.setVisibility(View.INVISIBLE);
		panelChapterVerse.setVisibility(View.VISIBLE);
		gridChapter.setVisibility(View.INVISIBLE);
		gridVerse.setVisibility(View.VISIBLE);
		gridVerse.setAdapter(verseAdapter = new VerseAdapter(selectedBook, selectedChapter));

		if (Build.VERSION.SDK_INT >= 12) { // animate
			animateFadeOutAndSlideLeft(gridChapter, gridVerse);
		}
		
		displaySelectedBookAndChapter();
	}
	
	void transitionVerseToChapter() {
		// TODO Animate
		gridBook.setVisibility(View.INVISIBLE);
		panelChapterVerse.setVisibility(View.VISIBLE);
		gridChapter.setVisibility(View.VISIBLE);
		gridChapter.setAdapter(chapterAdapter = new ChapterAdapter(selectedBook));
		gridVerse.setVisibility(View.INVISIBLE);
		displaySelectedBookAndChapter();
	}

	@TargetApi(12) static void animateFadeOutAndSlideLeft(final GridView fadingOut, final GridView slidingLeft) {
		fadingOut.setVisibility(View.VISIBLE);
		fadingOut.animate().alpha(0.f).setDuration(ANIM_DURATION).setListener(new AnimatorListenerAdapter() {
			@Override public void onAnimationEnd(Animator animation) {
				fadingOut.setAlpha(1.f);
				fadingOut.setVisibility(View.INVISIBLE);
			}
		});
		slidingLeft.setX(slidingLeft.getWidth());
		slidingLeft.animate().translationXBy(-slidingLeft.getWidth()).setDuration(ANIM_DURATION).setListener(new AnimatorListenerAdapter() {
			@Override public void onAnimationEnd(Animator animation) {
				slidingLeft.setVisibility(View.VISIBLE);
			}
		});
	}

	public static Bundle createArgs(int bookId, int chapter_1, int verse_1) {
		Bundle args = new Bundle();
		args.putInt(EXTRA_bookId, bookId);
		args.putInt(EXTRA_chapter, chapter_1);
		args.putInt(EXTRA_verse, verse_1);
		return args;
	}
	
	protected void displaySelectedBookAndChapter() {
		lSelectedBook.setText(underline(selectedBook.shortName));
		lSelectedBook.setTextColor(U.getForegroundColorByBookId(selectedBook.bookId));
		if (selectedChapter == 0) {
			lSelectedChapter.setVisibility(View.GONE);
		} else {
			lSelectedChapter.setVisibility(View.VISIBLE);
			lSelectedChapter.setText(underline("" + selectedChapter));
		}
	}

	private CharSequence underline(CharSequence cs) {
		SpannableStringBuilder sb = SpannableStringBuilder.valueOf(cs);
		sb.setSpan(new UnderlineSpan(), 0, cs.length(), 0);
		return sb;
	}

	@Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View res = inflater.inflate(R.layout.fragment_goto_grid, container, false);
		panelChapterVerse = V.get(res, R.id.panelChapterVerse);
		lSelectedBook = V.get(res, R.id.lSelectedBook);
		lSelectedChapter = V.get(res, R.id.lSelectedChapter);
		gridBook = V.get(res, R.id.gridBook);
		gridChapter = V.get(res, R.id.gridChapter);
		gridVerse = V.get(res, R.id.gridVerse);
		
		panelChapterVerse.setVisibility(View.INVISIBLE);
		gridBook.setOnItemClickListener(gridBook_itemClick);
		gridBook.setVisibility(View.VISIBLE);
		gridChapter.setVisibility(View.INVISIBLE);
		gridChapter.setOnItemClickListener(gridChapter_itemClick);
		gridVerse.setVisibility(View.INVISIBLE);
		gridVerse.setOnItemClickListener(gridVerse_itemClick);
		
		lSelectedBook.setOnClickListener(lSelectedBook_click);
		lSelectedChapter.setOnClickListener(lSelectedChapter_click);
		
		return res;
	}
	
	@Override public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		books = S.activeVersion.getConsecutiveBooks();
		gridBook.setAdapter(bookAdapter = new BookAdapter());
	}
	
	abstract class GridAdapter extends EasyAdapter {
		@Override public View newView(int position, ViewGroup parent) {
			TextView res = new TextView(getActivity());
			res.setLayoutParams(new GridView.LayoutParams(getResources().getDimensionPixelSize(R.dimen.goto_grid_cell_width), getResources().getDimensionPixelSize(R.dimen.goto_grid_cell_height)));
			res.setGravity(Gravity.CENTER);
			res.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
			return res;
		}
		
		@Override public void bindView(View view, int position, ViewGroup parent) {
			TextView lName = (TextView) view;
			lName.setText(textForView(position));
			lName.setTextColor(textColorForView(position));
			final ColorDrawable color = new ColorDrawable(backgroundColorForView(position));
			final InsetDrawable bg = new InsetDrawable(color, getResources().getDimensionPixelOffset(R.dimen.goto_grid_cell_inset));
			lName.setBackgroundDrawable(bg);
		}
		
		abstract CharSequence textForView(int position);
		
		int textColorForView(int position) {
			return 0xffffffff;
		}

		int backgroundColorForView(int position) {
			return 0xff666666;
		}
	}
	
	class BookAdapter extends GridAdapter {
		final Book[] books_grid;
		
		public BookAdapter() {
			// sort or not based on pref
			if (Preferences.getBoolean(App.context.getString(R.string.pref_alphabeticBookSort_key), App.context.getResources().getBoolean(R.bool.pref_alphabeticBookSort_default))) {
				books_grid = BookNameSorter.sortAlphabetically(books);
			} else {
				books_grid = books.clone();
			}
		}
		
		@Override public int getCount() {
			return books_grid.length;
		}

		@Override public Book getItem(int position) {
			return books_grid[position];
		}

		@Override CharSequence textForView(int position) {
			Book book = getItem(position);
			
			return BookNameSorter.getBookAbbr(book);
		}

		@Override
		int backgroundColorForView(final int position) {
			final Book book = getItem(position);
			return U.getBackgroundColorByBookId(book.bookId);
		}
	}
	
	class ChapterAdapter extends GridAdapter {
		private final Book book;
		
		public ChapterAdapter(Book book) {
			this.book = book;
		}
		
		@Override public int getCount() {
			return book.chapter_count;
		}
		
		@Override CharSequence textForView(int position) {
			return String.valueOf(position + 1);
		}
	}
	
	class VerseAdapter extends GridAdapter {
		private final Book book;
		private final int chapter_1;

		public VerseAdapter(Book book, int chapter_1) {
			this.book = book;
			this.chapter_1 = chapter_1;
		}

		@Override public int getCount() {
			int chapter_0 = chapter_1 - 1;
			return chapter_0 < 0 || chapter_0 >= book.verse_counts.length? 0: book.verse_counts[chapter_0];
		}

		@Override CharSequence textForView(int position) {
			return String.valueOf(position + 1);
		}
	}
}
