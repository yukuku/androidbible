package yuku.alkitab.base.fr;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.TextView;

import yuku.afw.V;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.R;
import yuku.alkitab.base.S;
import yuku.alkitab.base.fr.base.BaseGotoFragment;
import yuku.alkitab.base.model.Book;

public class GotoGridFragment extends BaseGotoFragment {
	public static final String TAG = GotoGridFragment.class.getSimpleName();
	
	private static final String EXTRA_verse = "verse"; //$NON-NLS-1$
	private static final String EXTRA_chapter = "chapter"; //$NON-NLS-1$
	private static final String EXTRA_bookId = "bookId"; //$NON-NLS-1$

	View panelChapterVerse;
	TextView lCurrentSelection;
	GridView gridBook;
	GridView gridChapter;
	GridView gridVerse;

	Book[] xkitab;
	BookAdapter bookAdapter;
	ChapterAdapter chapterAdapter;
	VerseAdapter verseAdapter;
	
	Book selectedBook;
	int selectedChapter;
	
	private AdapterView.OnItemClickListener gridBook_itemClick = new AdapterView.OnItemClickListener() {
		@Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			Book book = bookAdapter.getItem(position);
			selectedBook = book;
			displaySelectedBookAndChapter();
			
			transitionBookToChapter();
		}
	};
	
	private AdapterView.OnItemClickListener gridChapter_itemClick = new AdapterView.OnItemClickListener() {
		@Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			selectedChapter = position + 1;
			displaySelectedBookAndChapter();
			
			transitionChapterToVerse();
		}
	};

	private AdapterView.OnItemClickListener gridVerse_itemClick = new AdapterView.OnItemClickListener() {
		@Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			int selectedVerse = position + 1;
			((GotoFinishListener) getActivity()).onGotoFinished(GotoFinishListener.GOTO_TAB_grid, selectedBook.bookId, selectedChapter, selectedVerse); 
		}
	};
	
	void transitionBookToChapter() {
		// TODO Animate
		gridBook.setVisibility(View.INVISIBLE);
		panelChapterVerse.setVisibility(View.VISIBLE);
		gridChapter.setVisibility(View.VISIBLE);
		gridChapter.setAdapter(chapterAdapter = new ChapterAdapter(selectedBook));
		gridVerse.setVisibility(View.INVISIBLE);
	}
	
	void transitionChapterToVerse() {
		// TODO Animate
		gridBook.setVisibility(View.INVISIBLE);
		panelChapterVerse.setVisibility(View.VISIBLE);
		gridChapter.setVisibility(View.INVISIBLE);
		gridVerse.setVisibility(View.VISIBLE);
		gridVerse.setAdapter(verseAdapter = new VerseAdapter(selectedBook, selectedChapter));
	}

	public static Bundle createArgs(int bookId, int chapter_1, int verse_1) {
		Bundle args = new Bundle();
		args.putInt(EXTRA_bookId, bookId);
		args.putInt(EXTRA_chapter, chapter_1);
		args.putInt(EXTRA_verse, verse_1);
		return args;
	}
	
	protected void displaySelectedBookAndChapter() {
		if (selectedChapter == 0) {
			lCurrentSelection.setText(selectedBook.judul);
		} else {
			lCurrentSelection.setText(S.alamat(selectedBook, selectedChapter));
		}
	}

	@Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View res = inflater.inflate(R.layout.fragment_goto_grid, container, false);
		panelChapterVerse = V.get(res, R.id.panelChapterVerse);
		lCurrentSelection = V.get(res, R.id.lCurrentSelection);
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
		
		return res;
	}
	
	@Override public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		xkitab = S.activeVersion.getConsecutiveBooks();
		gridBook.setAdapter(bookAdapter = new BookAdapter());
	}
	
	abstract class GridAdapter extends EasyAdapter {
		float density = getResources().getDisplayMetrics().density;
		
		@Override public View newView(int position, ViewGroup parent) {
			TextView res = new TextView(getActivity());
			res.setLayoutParams(new GridView.LayoutParams((int)(64.f * density), (int)(40 * density)));
			res.setGravity(Gravity.CENTER);
			res.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
			return res;
		}
		
		@Override public void bindView(View view, int position, ViewGroup parent) {
			TextView lName = (TextView) view;
			lName.setText(textForView(position));
			lName.setTextColor(textColorForView(position));
		}
		
		abstract CharSequence textForView(int position);
		
		int textColorForView(int position) {
			return 0xffffffff;
		}
	}
	
	class BookAdapter extends GridAdapter {
		final int[] numberedBookMap;
		final String[] numberedBookStartsWiths = {null, "I", "II", "III", "IV", "V"}; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
		final String[] numberedBookReplaceWiths = {null, "1", "2", "3", "4", "5"}; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
		
		public BookAdapter() {
			// for these book numbers, replace "I", "II", "III", "IV", "V" with numbers 
			// to save space, to make them still understandable when truncated
			int[] numberedBooks_1 = {0 /*moses*/, 8, 10, 12, 45, 51, 53, 59, 61, 66, 70, };
			int[] numberedBooks_2 = {1 /*moses*/, 9, 11, 13, 46, 52, 54, 60, 62, 67, 71, }; 
			int[] numberedBooks_3 = {2 /*moses*/, 63 /*john*/, 72 /*makabe*/, };
			int[] numberedBooks_4 = {3 /*moses*/, 73 /*makabe*/, };
			int[] numberedBooks_5 = {4 /*moses*/, };
			
			numberedBookMap = new int[74]; // as large as the max number above + 1
			for (int bookId: numberedBooks_1) numberedBookMap[bookId] = 1;
			for (int bookId: numberedBooks_2) numberedBookMap[bookId] = 2;
			for (int bookId: numberedBooks_3) numberedBookMap[bookId] = 3;
			for (int bookId: numberedBooks_4) numberedBookMap[bookId] = 4;
			for (int bookId: numberedBooks_5) numberedBookMap[bookId] = 5;
		}
		
		@Override public int getCount() {
			return xkitab.length;
		}

		@Override public Book getItem(int position) {
			return xkitab[position];
		}

		@Override CharSequence textForView(int position) {
			Book book = getItem(position);
			String title = book.judul;
			
			// remove spaces and '.'
			if (book.judul.indexOf(' ') != -1) {
				title = title.replace(" ", ""); //$NON-NLS-1$ //$NON-NLS-2$
			} 
			if (book.judul.indexOf('.') != -1) {
				title = title.replace(".", ""); //$NON-NLS-1$ //$NON-NLS-2$
			}
			
			int numberedBookCategory = position >= numberedBookMap.length? 0: numberedBookMap[position];
			if (numberedBookCategory > 0) {
				String startsWith = numberedBookStartsWiths[numberedBookCategory];
				String replaceWith = numberedBookReplaceWiths[numberedBookCategory];
				if (title.startsWith(startsWith)) {
					title = replaceWith + title.substring(startsWith.length());
				}
			}
			
			if (title.length() > 3) title = title.substring(0, 3);
			return title;
		}
		
		@Override int textColorForView(int position) {
			if (position >= 0 && position < 39) {
				return 0xffffcccf;
			} else if (position >= 39 && position < 66) {
				return 0xffccccff;
			} else {
				return 0xffffffff;
			}
		}
	}
	
	class ChapterAdapter extends GridAdapter {
		private final Book book;
		
		public ChapterAdapter(Book book) {
			this.book = book;
		}
		
		@Override public int getCount() {
			return book.nchapter;
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
			return chapter_0 < 0 || chapter_0 >= book.nverses.length? 0: book.nverses[chapter_0];
		}

		@Override CharSequence textForView(int position) {
			return String.valueOf(position + 1);
		}
	}
}
