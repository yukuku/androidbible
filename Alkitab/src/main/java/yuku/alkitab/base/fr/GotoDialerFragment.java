package yuku.alkitab.base.fr;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.Spinner;
import android.widget.TextView;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.S;
import yuku.alkitab.base.fr.base.BaseGotoFragment;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.BookColorUtil;
import yuku.alkitab.base.util.BookNameSorter;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Book;

public class GotoDialerFragment extends BaseGotoFragment {
	private static final String EXTRA_verse = "verse";
	private static final String EXTRA_chapter = "chapter";
	private static final String EXTRA_bookId = "bookId";

	TextView active;
	TextView passive;

	Button bOk;
	TextView tChapter;
	View tChapterLabel;
	TextView tVerse;
	View tVerseLabel;
	Spinner cbBook;

	boolean tChapter_firstTime = true;
	boolean tVerse_firstTime = true;

	int maxChapter = 0;
	int maxVerse = 0;
	BookAdapter adapter;
	
	int bookId;
	int chapter_1;
	int verse_1;
	
	public static Bundle createArgs(int bookId, int chapter_1, int verse_1) {
		Bundle args = new Bundle();
		args.putInt(EXTRA_bookId, bookId);
		args.putInt(EXTRA_chapter, chapter_1);
		args.putInt(EXTRA_verse, verse_1);
		return args;
	}

	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Bundle args = getArguments();
		if (args != null) {
			bookId = args.getInt(EXTRA_bookId, -1);
			chapter_1 = args.getInt(EXTRA_chapter);
			verse_1 = args.getInt(EXTRA_verse);
		}
	}

	@Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View res = inflater.inflate(R.layout.fragment_goto_dialer, container, false);

		bOk = res.findViewById(R.id.bOk);
		tChapter = res.findViewById(R.id.tChapter);
		tChapterLabel = res.findViewById(R.id.tChapterLabel);
		tVerse = res.findViewById(R.id.tVerse);
		tVerseLabel = res.findViewById(R.id.tVerseLabel);
		cbBook = res.findViewById(R.id.cbBook);
		cbBook.setAdapter(adapter = new BookAdapter());

		tChapter.setOnClickListener(tChapter_click);
		tChapterLabel.setOnClickListener(tChapter_click);

		tVerse.setOnClickListener(tVerse_click);
		tVerseLabel.setOnClickListener(tVerse_click);

		res.findViewById(R.id.bDigit0).setOnClickListener(button_click);
		res.findViewById(R.id.bDigit1).setOnClickListener(button_click);
		res.findViewById(R.id.bDigit2).setOnClickListener(button_click);
		res.findViewById(R.id.bDigit3).setOnClickListener(button_click);
		res.findViewById(R.id.bDigit4).setOnClickListener(button_click);
		res.findViewById(R.id.bDigit5).setOnClickListener(button_click);
		res.findViewById(R.id.bDigit6).setOnClickListener(button_click);
		res.findViewById(R.id.bDigit7).setOnClickListener(button_click);
		res.findViewById(R.id.bDigit8).setOnClickListener(button_click);
		res.findViewById(R.id.bDigit9).setOnClickListener(button_click);
		res.findViewById(R.id.bDigitBackspace).setOnClickListener(button_click);

		showOrHideVerse();
		Preferences.registerObserver(preferenceChangeListener);

		return res;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		Preferences.unregisterObserver(preferenceChangeListener);
	}

	final SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener = (sharedPreferences, key) -> {
		if (key.equals(Prefkey.gotoAskForVerse.name())) {
			showOrHideVerse();
		}
	};

	void showOrHideVerse() {
		if (Preferences.getBoolean(Prefkey.gotoAskForVerse, Prefkey.GOTO_ASK_FOR_VERSE_DEFAULT)) {
			tVerse.setVisibility(View.VISIBLE);
			tVerseLabel.setVisibility(View.VISIBLE);
		} else {
			if (active == tVerse) {
				activate(tChapter, tVerse);
			}
			tVerse.setVisibility(View.GONE);
			tVerseLabel.setVisibility(View.GONE);
		}
	}

	@Override public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		// check current bookId, chapter, and verse
		cbBook.setSelection(adapter.getPositionFromBookId(bookId));
		cbBook.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override public void onNothingSelected(AdapterView<?> parent) {}

			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				Book book = adapter.getItem(position);
				maxChapter = book.chapter_count;

				int chapter_0 = tryReadChapter() - 1;
				if (chapter_0 >= 0 && chapter_0 < book.chapter_count) {
					maxVerse = book.verse_counts[chapter_0];
				}

				fixChapterOverflow();
				fixVerseOverflow();
			}
		});

		bOk.setOnClickListener(v -> {
			int selectedChapter_1 = 0;
			int selectedVerse_1 = 0;

			try {
				selectedChapter_1 = Integer.parseInt(tChapter.getText().toString());

				if (Preferences.getBoolean(Prefkey.gotoAskForVerse, Prefkey.GOTO_ASK_FOR_VERSE_DEFAULT)) {
					selectedVerse_1 = Integer.parseInt(tVerse.getText().toString());
				}
			} catch (NumberFormatException e) {
				// let it still be 0
			}

			final int selectedBookId = adapter.getItem(cbBook.getSelectedItemPosition()).bookId;

			final GotoFinishListener activity = (GotoFinishListener) getActivity();
			if (activity != null) {
				activity.onGotoFinished(GotoFinishListener.GOTO_TAB_dialer, selectedBookId, selectedChapter_1, selectedVerse_1);
			}
		});

		active = tChapter;
		passive = tVerse;

		{
			if (chapter_1 != 0) {
				tChapter.setText(String.valueOf(chapter_1));
			}
			if (verse_1 != 0) {
				tVerse.setText(String.valueOf(verse_1));
			}
		}

		colorize();
	}

	final View.OnClickListener tChapter_click = v -> activate(tChapter, tVerse);

	final View.OnClickListener tVerse_click = v -> activate(tVerse, tChapter);

	final View.OnClickListener button_click = v -> {
		final int id = v.getId();
		if (id == R.id.bDigit0) press("0");
		else if (id == R.id.bDigit1) press("1");
		else if (id == R.id.bDigit2) press("2");
		else if (id == R.id.bDigit3) press("3");
		else if (id == R.id.bDigit4) press("4");
		else if (id == R.id.bDigit5) press("5");
		else if (id == R.id.bDigit6) press("6");
		else if (id == R.id.bDigit7) press("7");
		else if (id == R.id.bDigit8) press("8");
		else if (id == R.id.bDigit9) press("9");
		else if (id == R.id.bDigitBackspace) press("backspace");
	};

	int tryReadChapter() {
		try {
			return Integer.parseInt("0" + tChapter.getText().toString());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	int tryReadVerse() {
		try {
			return Integer.parseInt("0" + tVerse.getText().toString());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	void fixVerseOverflow() {
		int verse = tryReadVerse();

		if (verse > maxVerse) {
			verse = maxVerse;
		} else if (verse <= 0) {
			verse = 1;
		}

		tVerse.setText(String.valueOf(verse));
	}

	void fixChapterOverflow() {
		int chapter = tryReadChapter();

		if (chapter > maxChapter) {
			chapter = maxChapter;
		} else if (chapter <= 0) {
			chapter = 1;
		}

		tChapter.setText(String.valueOf(chapter));
	}

	void press(String s) {
		if (active != null) {
			if (s.equals("backspace")) {
				if (active.length() > 0) {
					final CharSequence txt = active.getText();
					active.setText(TextUtils.substring(txt, 0, txt.length() - 1));
				}
				return;
			}

			if (active == tChapter) {
				if (tChapter_firstTime) {
					active.setText(s);
					tChapter_firstTime = false;
				} else {
					active.append(s);
				}

				if (tryReadChapter() > maxChapter || tryReadChapter() <= 0) {
					active.setText(s);
				}
				
				Book book = adapter.getItem(cbBook.getSelectedItemPosition());
				int chapter_1 = tryReadChapter();
				if (chapter_1 >= 1 && chapter_1 <= book.verse_counts.length) {
					maxVerse = book.verse_counts[chapter_1 - 1];
				}
			} else if (active == tVerse) {
				if (tVerse_firstTime) {
					active.setText(s);
					tVerse_firstTime = false;
				} else {
					active.append(s);
				}

				if (tryReadVerse() > maxVerse || tryReadVerse() <= 0) {
					active.setText(s);
				}
			}
		}
	}

	void activate(TextView active, TextView passive) {
		this.active = active;
		this.passive = passive;
		colorize();
	}

	private void colorize() {
		if (active != null) active.setBackgroundResource(R.drawable.goto_dialer_active);
		if (passive != null) passive.setBackgroundColor(0x0);
	}

	private class BookAdapter extends BaseAdapter {
		Book[] booksc_;
		
		public BookAdapter() {
			Book[] booksc = S.activeVersion().getConsecutiveBooks();
			
			if (Preferences.getBoolean(R.string.pref_alphabeticBookSort_key, R.bool.pref_alphabeticBookSort_default)) {
				booksc_ = BookNameSorter.sortAlphabetically(booksc); 
			} else {
				booksc_ = booksc.clone();
			}
		}

		/**
		 * @return 0 when not found (not -1, because we just want to select the first book)
		 */
		public int getPositionFromBookId(int pos) {
			for (int i = 0; i < booksc_.length; i++) {
				if (booksc_[i].bookId == pos) {
					return i;
				}
			}
			return 0;
		}

		@Override public int getCount() {
			return booksc_.length;
		}

		@Override public Book getItem(int position) {
			return booksc_[position];
		}

		@Override public long getItemId(int position) {
			return position;
		}

		@Override public View getView(int position, View convertView, ViewGroup parent) {
			TextView res = (TextView) (convertView != null ? convertView : LayoutInflater.from(parent.getContext()).inflate(R.layout.item_goto_dialer_book, parent, false));

			final Book book = getItem(position);
			res.setText(booksc_[position].shortName);
			res.setTextColor(BookColorUtil.getForegroundOnDark(book.bookId));

			return res;
		}

		@Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
			CheckedTextView res = (CheckedTextView) (convertView != null ? convertView : LayoutInflater.from(parent.getContext()).inflate(R.layout.item_goto_dialer_book_dropdown, parent, false));

			final Book book = getItem(position);
			res.setText(book.shortName);
			res.setTextColor(BookColorUtil.getForegroundOnDark(book.bookId));

			return res;
		}
	}
}
