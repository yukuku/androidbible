package yuku.alkitab.base.fr;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.Spinner;
import android.widget.TextView;

import yuku.afw.App;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.alkitab.R;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.fr.base.BaseGotoFragment;
import yuku.alkitab.base.model.Book;
import yuku.alkitab.base.util.BookNameSorter;

public class GotoDialerFragment extends BaseGotoFragment {
	public static final String TAG = GotoDialerFragment.class.getSimpleName();
	
	private static final String EXTRA_verse = "verse"; //$NON-NLS-1$
	private static final String EXTRA_chapter = "chapter"; //$NON-NLS-1$
	private static final String EXTRA_bookId = "bookId"; //$NON-NLS-1$

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
		
		bOk = V.get(res, R.id.bOk);
		tChapter = V.get(res, R.id.tChapter);
		tChapterLabel = V.get(res, R.id.tChapterLabel);
		tVerse = V.get(res, R.id.tVerse);
		tVerseLabel = V.get(res, R.id.tVerseLabel);
		cbBook = V.get(res, R.id.cbBook);
		cbBook.setAdapter(adapter = new BookAdapter());

		tChapter.setOnClickListener(tChapter_click);
		if (tChapterLabel != null) { // not always present in layout
			tChapterLabel.setOnClickListener(tChapter_click);
		}

		tVerse.setOnClickListener(tVerse_click);
		if (tVerseLabel != null) { // not always present in layout
			tVerseLabel.setOnClickListener(tVerse_click);
		}

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
		res.findViewById(R.id.bDigitC).setOnClickListener(button_click);
		res.findViewById(R.id.bDigitSwitch).setOnClickListener(button_click);

		return res;
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

		bOk.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View v) {
				int chapter = 0;
				int verse = 0;

				try {
					chapter = Integer.parseInt(tChapter.getText().toString());
					verse = Integer.parseInt(tVerse.getText().toString());
				} catch (NumberFormatException e) {
					// let it still be 0
				}

				int bookId = adapter.getItem(cbBook.getSelectedItemPosition()).bookId;

				((GotoFinishListener) getActivity()).onGotoFinished(GotoFinishListener.GOTO_TAB_dialer, bookId, chapter, verse);
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

	View.OnClickListener tChapter_click = new View.OnClickListener() {
		@Override public void onClick(View v) {
			activate(tChapter, tVerse);
		}
	};
	
	View.OnClickListener tVerse_click = new View.OnClickListener() {
		@Override public void onClick(View v) {
			activate(tVerse, tChapter);
		}
	};

	View.OnClickListener button_click = new View.OnClickListener() {
		@Override public void onClick(View v) {
			int id = v.getId();
			if (id == R.id.bDigit0) press("0"); //$NON-NLS-1$
			if (id == R.id.bDigit1) press("1"); //$NON-NLS-1$
			if (id == R.id.bDigit2) press("2"); //$NON-NLS-1$
			if (id == R.id.bDigit3) press("3"); //$NON-NLS-1$
			if (id == R.id.bDigit4) press("4"); //$NON-NLS-1$
			if (id == R.id.bDigit5) press("5"); //$NON-NLS-1$
			if (id == R.id.bDigit6) press("6"); //$NON-NLS-1$
			if (id == R.id.bDigit7) press("7"); //$NON-NLS-1$
			if (id == R.id.bDigit8) press("8"); //$NON-NLS-1$
			if (id == R.id.bDigit9) press("9"); //$NON-NLS-1$
			if (id == R.id.bDigitC) press("C"); //$NON-NLS-1$
			if (id == R.id.bDigitSwitch) press(":"); //$NON-NLS-1$
		}
	};
	
//	TODO (move to activity to support keyboard) @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
//		if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
//			pencet(String.valueOf((char) ('0' + keyCode - KeyEvent.KEYCODE_0)));
//			return true;
//		} else if (keyCode == KeyEvent.KEYCODE_STAR) {
//			pencet("C"); //$NON-NLS-1$
//			return true;
//		} else if (keyCode == KeyEvent.KEYCODE_POUND) {
//			pencet(":"); //$NON-NLS-1$
//			return true;
//		}
//
//		return super.onKeyDown(keyCode, event);
//	}

	int tryReadChapter() {
		try {
			return Integer.parseInt("0" + tChapter.getText().toString()); //$NON-NLS-1$
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	int tryReadVerse() {
		try {
			return Integer.parseInt("0" + tVerse.getText().toString()); //$NON-NLS-1$
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
			if (s.equals("C")) { //$NON-NLS-1$
				active.setText(""); //$NON-NLS-1$
				return;
			} else if (s.equals(":")) { //$NON-NLS-1$
				if (passive != null) {
					activate(passive, active);
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
		if (active != null) active.setBackgroundColor(0xff33b5e5);
		if (passive != null) passive.setBackgroundColor(0x0);
	}

	private class BookAdapter extends BaseAdapter {
		Book[] booksc_;
		
		public BookAdapter() {
			Book[] booksc = S.activeVersion.getConsecutiveBooks();
			
			if (Preferences.getBoolean(App.context.getString(R.string.pref_alphabeticBookSort_key), App.context.getResources().getBoolean(R.bool.pref_sortKitabAlfabet_default))) {
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
			TextView res = (TextView) (convertView != null ? convertView : LayoutInflater.from(getActivity()).inflate(android.R.layout.simple_spinner_item, parent, false));
			res.setText(booksc_[position].shortName);
			res.setTextSize(18);
			return res;
		}

		@Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
			CheckedTextView res = (CheckedTextView) (convertView != null ? convertView : LayoutInflater.from(getActivity()).inflate(android.R.layout.simple_spinner_dropdown_item, parent, false));

			Book k = getItem(position);
			res.setText(k.shortName);
			res.setTextSize(18);
			res.setTextColor(U.getColorBasedOnBookId(k.bookId));

			return res;
		}
	}
}
