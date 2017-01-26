package yuku.alkitab.base.fr;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.os.Bundle;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.fr.base.BaseGotoFragment;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.BookNameSorter;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Book;

public class GotoGridFragment extends BaseGotoFragment {
	public static final String TAG = GotoGridFragment.class.getSimpleName();
	
	private static final String EXTRA_verse = "verse";
	private static final String EXTRA_chapter = "chapter";
	private static final String EXTRA_bookId = "bookId";

	private static final int ANIM_DURATION = 200;

	View panelChapterVerse;
	TextView lSelectedBook;
	TextView lSelectedChapter;
	RecyclerView gridBook;
	RecyclerView gridChapter;
	RecyclerView gridVerse;

	Book[] books;
	BookAdapter bookAdapter;
	ChapterAdapter chapterAdapter;
	VerseAdapter verseAdapter;
	
	Book selectedBook;
	int selectedChapter;

	final View.OnClickListener lSelectedBook_click = v -> {
		selectedBook = null;
		selectedChapter = 0;
		transitionChapterToBook();
	};

	final View.OnClickListener lSelectedChapter_click = v -> {
		selectedChapter = 0;
		transitionVerseToChapter();
	};
	
	void transitionBookToChapter() {
		gridBook.setVisibility(View.INVISIBLE);
		panelChapterVerse.setVisibility(View.VISIBLE);
		gridChapter.setVisibility(View.VISIBLE);
		gridChapter.setAdapter(chapterAdapter = new ChapterAdapter(selectedBook));
		gridVerse.setVisibility(View.INVISIBLE);
		
		animateFadeOutAndSlideLeft(gridBook, gridChapter);
		ViewCompat.jumpDrawablesToCurrentState(lSelectedBook);
		lSelectedBook.setAlpha(0.f);
		lSelectedBook.animate().alpha(1.f).setDuration(ANIM_DURATION);

		displaySelectedBookAndChapter();
	}

	void transitionBookToVerse() {
		gridBook.setVisibility(View.INVISIBLE);
		panelChapterVerse.setVisibility(View.VISIBLE);
		gridVerse.setVisibility(View.VISIBLE);
		gridVerse.setAdapter(verseAdapter = new VerseAdapter(selectedBook, selectedChapter));
		gridChapter.setVisibility(View.INVISIBLE);

		animateFadeOutAndSlideLeft(gridBook, gridVerse);
		ViewCompat.jumpDrawablesToCurrentState(lSelectedBook);
		lSelectedBook.setAlpha(0.f);
		lSelectedBook.animate().alpha(1.f).setDuration(ANIM_DURATION);

		displaySelectedBookAndChapter();
	}

	void transitionChapterToBook() {
		gridBook.setVisibility(View.VISIBLE);
		panelChapterVerse.setVisibility(View.INVISIBLE);

		animateFadeOutAndSlideRight(gridChapter, gridBook);
	}
	
	void transitionChapterToVerse() {
		gridBook.setVisibility(View.INVISIBLE);
		panelChapterVerse.setVisibility(View.VISIBLE);
		gridChapter.setVisibility(View.INVISIBLE);
		gridVerse.setVisibility(View.VISIBLE);
		gridVerse.setAdapter(verseAdapter = new VerseAdapter(selectedBook, selectedChapter));

		animateFadeOutAndSlideLeft(gridChapter, gridVerse);

		displaySelectedBookAndChapter();
	}
	
	void transitionVerseToChapter() {
		gridBook.setVisibility(View.INVISIBLE);
		panelChapterVerse.setVisibility(View.VISIBLE);
		gridChapter.setVisibility(View.VISIBLE);
		gridChapter.setAdapter(chapterAdapter = new ChapterAdapter(selectedBook));
		gridVerse.setVisibility(View.INVISIBLE);

		animateFadeOutAndSlideRight(gridVerse, gridChapter);

		displaySelectedBookAndChapter();
	}

	static void animateFadeOutAndSlideLeft(final View fadingOut, final View slidingLeft) {
		fadingOut.setVisibility(View.VISIBLE);
		fadingOut.animate().alpha(0.f).setDuration(ANIM_DURATION).setListener(new AnimatorListenerAdapter() {
			@Override
			public void onAnimationEnd(Animator animation) {
				fadingOut.setAlpha(1.f);
				fadingOut.setVisibility(View.INVISIBLE);
			}
		});
		slidingLeft.setX(slidingLeft.getWidth());
		slidingLeft.animate().translationXBy(-slidingLeft.getWidth()).setDuration(ANIM_DURATION).setListener(new AnimatorListenerAdapter() {
			@Override
			public void onAnimationEnd(Animator animation) {
				slidingLeft.setVisibility(View.VISIBLE);
			}
		});
	}

	static void animateFadeOutAndSlideRight(final View fadingOut, final View slidingRight) {
		fadingOut.setVisibility(View.VISIBLE);
		fadingOut.animate().alpha(0.f).setDuration(ANIM_DURATION).setListener(new AnimatorListenerAdapter() {
			@Override public void onAnimationEnd(Animator animation) {
				fadingOut.setAlpha(1.f);
				fadingOut.setVisibility(View.INVISIBLE);
			}
		});
		slidingRight.setX(-slidingRight.getWidth());
		slidingRight.animate().translationXBy(slidingRight.getWidth()).setDuration(ANIM_DURATION).setListener(new AnimatorListenerAdapter() {
			@Override public void onAnimationEnd(Animator animation) {
				slidingRight.setVisibility(View.VISIBLE);
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
		lSelectedBook.setText(selectedBook.shortName);
		lSelectedBook.setTextColor(U.getForegroundColorOnDarkBackgroundByBookId(selectedBook.bookId));
		if (selectedChapter == 0) {
			lSelectedChapter.setVisibility(View.GONE);
		} else {
			lSelectedChapter.setVisibility(View.VISIBLE);
			ViewCompat.jumpDrawablesToCurrentState(lSelectedChapter);
			lSelectedChapter.setText("" + selectedChapter);
		}
	}

	GridLayoutManager createLayoutManagerForNumbers() {
		return new GridLayoutManager(getActivity(), getResources().getInteger(R.integer.goto_grid_numeric_num_columns));
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
		gridBook.setVisibility(View.VISIBLE);
		gridChapter.setVisibility(View.INVISIBLE);
		gridVerse.setVisibility(View.INVISIBLE);

		gridBook.setLayoutManager(new GridLayoutManager(getActivity(), 6));
		gridChapter.setLayoutManager(createLayoutManagerForNumbers());
		gridVerse.setLayoutManager(createLayoutManagerForNumbers());

		lSelectedBook.setOnClickListener(lSelectedBook_click);
		lSelectedChapter.setOnClickListener(lSelectedChapter_click);
		
		return res;
	}
	
	@Override public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		books = S.activeVersion.getConsecutiveBooks();
		gridBook.setAdapter(bookAdapter = new BookAdapter());
	}

	public class VH extends RecyclerView.ViewHolder {
		public VH(final View itemView) {
			super(itemView);
		}
	}

	abstract class GridAdapter extends RecyclerView.Adapter<VH> {
		@Override
		public VH onCreateViewHolder(final ViewGroup parent, final int viewType) {
			return new VH(getActivity().getLayoutInflater().inflate(R.layout.item_goto_grid_cell, parent, false));
		}

		@Override
		public void onBindViewHolder(final VH holder, final int position) {
			final TextView lName = (TextView) holder.itemView;
			lName.setText(textForView(position));
			lName.setTextColor(textColorForView(position));
		}
		
		abstract CharSequence textForView(int position);
		
		int textColorForView(int position) {
			return 0xffffffff;
		}
	}
	
	class BookAdapter extends GridAdapter {
		final Book[] books_grid;
		
		public BookAdapter() {
			// sort or not based on pref
			if (Preferences.getBoolean(R.string.pref_alphabeticBookSort_key, R.bool.pref_alphabeticBookSort_default)) {
				books_grid = BookNameSorter.sortAlphabetically(books);
			} else {
				books_grid = books.clone();
			}
		}

		@Override
		public int getItemCount() {
			return books_grid.length;
		}

		@Override
		public void onBindViewHolder(final VH holder, final int position) {
			super.onBindViewHolder(holder, position); // must call this

			holder.itemView.setOnClickListener(v -> {
				selectedBook = bookAdapter.getItem(position);
				if (selectedBook.chapter_count == 1) {
					// for single-chapter books, jump directly to verse selection
					selectedChapter = 1;
					transitionBookToVerse();
				} else {
					transitionBookToChapter();
				}
			});
		}

		public Book getItem(int position) {
			return books_grid[position];
		}

		@Override CharSequence textForView(int position) {
			Book book = getItem(position);
			
			return BookNameSorter.getBookAbbr(book);
		}

		@Override
		int textColorForView(final int position) {
			final Book book = getItem(position);
			return U.getForegroundColorOnDarkBackgroundByBookId(book.bookId);
		}
	}
	
	class ChapterAdapter extends GridAdapter {
		private final Book book;
		
		public ChapterAdapter(Book book) {
			this.book = book;
		}

		@Override
		public int getItemCount() {
			return book.chapter_count;
		}

		@Override
		public void onBindViewHolder(final VH holder, final int position) {
			super.onBindViewHolder(holder, position); // must call this

			holder.itemView.setOnClickListener(v -> {
				selectedChapter = position + 1;

				if (Preferences.getBoolean(Prefkey.gotoAskForVerse, Prefkey.GOTO_ASK_FOR_VERSE_DEFAULT)) {
					transitionChapterToVerse();
				} else {
					((GotoFinishListener) getActivity()).onGotoFinished(GotoFinishListener.GOTO_TAB_grid, selectedBook.bookId, selectedChapter, 0);
				}
			});
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

		@Override
		public int getItemCount() {
			int chapter_0 = chapter_1 - 1;
			return chapter_0 < 0 || chapter_0 >= book.verse_counts.length? 0: book.verse_counts[chapter_0];
		}

		@Override
		public void onBindViewHolder(final VH holder, final int position) {
			super.onBindViewHolder(holder, position);

			holder.itemView.setOnClickListener(v -> {
				final int selectedVerse = position + 1;
				((GotoFinishListener) getActivity()).onGotoFinished(GotoFinishListener.GOTO_TAB_grid, selectedBook.bookId, selectedChapter, selectedVerse);
			});
		}

		@Override CharSequence textForView(int position) {
			return String.valueOf(position + 1);
		}
	}
}
