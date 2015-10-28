package yuku.alkitab.base.fr;

import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;
import com.afollestad.materialdialogs.AlertDialogWrapper;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import yuku.afw.V;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.base.S;
import yuku.alkitab.base.fr.base.BaseGotoFragment;
import yuku.alkitab.base.util.Jumper;
import yuku.alkitab.base.util.Levenshtein;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Book;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GotoDirectFragment extends BaseGotoFragment {
	public static final String TAG = GotoDirectFragment.class.getSimpleName();
	
	private static final String EXTRA_verse = "verse";
	private static final String EXTRA_chapter = "chapter";
	private static final String EXTRA_bookId = "bookId";

	TextView lDirectSample;
	AutoCompleteTextView tDirectReference;
	View bOk;

	AutoCompleteAdapter adapter;

	int bookId;
	int chapter_1;
	int verse_1;
	private Activity activity;

	static class Candidate {
		String title;
		int score;
		boolean bookOnly;
	}

	public static Bundle createArgs(int bookId, int chapter_1, int verse_1) {
		Bundle args = new Bundle();
		args.putInt(EXTRA_bookId, bookId);
		args.putInt(EXTRA_chapter, chapter_1);
		args.putInt(EXTRA_verse, verse_1);
		return args;
	}

	@Override
	public void onAttach(final Activity activity) {
		super.onAttach(activity);
		this.activity = activity;
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
		View res = inflater.inflate(R.layout.fragment_goto_direct, container, false);
		lDirectSample = V.get(res, R.id.lDirectSample);

		tDirectReference = V.get(res, R.id.tDirectReference);
		tDirectReference.setAdapter(adapter = new AutoCompleteAdapter());
		tDirectReference.setOnItemClickListener((parent, view, position, id) -> {
			if (!adapter.getItem(position).bookOnly) {
				bOk.performClick();
			}
		});

		bOk = V.get(res, R.id.bOk);
		bOk.setOnClickListener(bOk_click);

		tDirectReference.setOnEditorActionListener((v, actionId, event) -> {
			bOk_click.onClick(bOk);
			return true;
		});
		return res;
	}

	@Override public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		final String example = S.activeVersion.reference(bookId, chapter_1, verse_1);
		final CharSequence text = getText(R.string.jump_to_prompt);
		SpannableStringBuilder sb = new SpannableStringBuilder();
		sb.append(text);
		final CharSequence text2 = TextUtils.expandTemplate(text, example);
		lDirectSample.setText(text2);
	}
	
	View.OnClickListener bOk_click = new View.OnClickListener() {
		public Pattern nobookPattern;

		@Override public void onClick(View v) {
			String reference = tDirectReference.getText().toString();
			
			if (reference.trim().length() == 0) {
				return; // do nothing
			}

			// typing chapter or chapter:verse was broken sometime. Let us make a special case to handle this.
			if (nobookPattern == null) {
				nobookPattern = Pattern.compile("(\\d+)(?:[ :.]+(\\d+))?");
			}

			final Matcher m = nobookPattern.matcher(reference.trim());
			if (m.matches()) {
				try {
					final String chapter_s = m.group(1);
					final int chapter_1 = Integer.parseInt(chapter_s);

					final String verse_s = m.group(2);
					final int verse_1;
					if (verse_s != null) {
						verse_1 = Integer.parseInt(verse_s);
					} else {
						verse_1 = 0;
					}

					((GotoFinishListener) activity).onGotoFinished(GotoFinishListener.GOTO_TAB_direct, bookId, chapter_1, verse_1);
					return;
				} catch (NumberFormatException ignored) {}
			}

			final Jumper jumper = new Jumper(reference);
			if (! jumper.getParseSucceeded()) {
				new AlertDialogWrapper.Builder(getActivity())
					.setMessage(getString(R.string.alamat_tidak_sah_alamat, reference))
					.setPositiveButton(R.string.ok, null)
					.show();
				return;
			}
			
			final int bookId = jumper.getBookId(S.activeVersion.getConsecutiveBooks());
			final int chapter = jumper.getChapter();
			final int verse = jumper.getVerse();

			((GotoFinishListener) activity).onGotoFinished(GotoFinishListener.GOTO_TAB_direct, bookId, chapter, verse);
		}
	};

	class AutoCompleteAdapter extends EasyAdapter implements Filterable {
		final List<Candidate> candidates = new ArrayList<>();

		@Override
		public View newView(final int position, final ViewGroup parent) {
			return activity.getLayoutInflater().inflate(android.R.layout.simple_dropdown_item_1line, parent, false);
		}

		@Override
		public void bindView(final View view, final int position, final ViewGroup parent) {
			final TextView tv = (TextView) view;
			final Candidate candidate = getItem(position);
			tv.setText(candidate.title);
		}

		@Override
		public int getCount() {
			return candidates == null ? 0 : candidates.size();
		}

		/**
		 * This may not be removed, or the {@link android.widget.AutoCompleteTextView#setOnItemClickListener(android.widget.AdapterView.OnItemClickListener)} won't work!
		 */
		@Override
		public Candidate getItem(final int position) {
			return candidates.get(position);
		}

		@Override
		public Filter getFilter() {
			return new Filter() {
				final Book[] books = S.activeVersion.getConsecutiveBooks();
				final TIntObjectMap<Book> bookIndex = new TIntObjectHashMap<>();

				{
					for (final Book book : books) {
						bookIndex.put(book.bookId, book);
					}
				}

				List<Jumper.BookRef> bookRefs;

				@Override
				protected FilterResults performFiltering(@Nullable final CharSequence constraint) {
					final FilterResults res = new FilterResults();

					if (constraint == null) {
						res.values = null;
						res.count = 0;
						return res;
					}

					final Jumper jumper = new Jumper(constraint.toString());
					String bookName = jumper.getUnparsedBook();

					final ArrayList<Candidate> candidates = new ArrayList<>();
					if (bookName != null) {
						bookName = bookName.trim().toLowerCase();
						if (bookName.length() >= 1) {
							final TIntSet addedBookIds = new TIntHashSet();

							for (final Book book : books) {
								String title = null;
								int score = 0;

								final String n = book.shortName.toLowerCase();
								if (n.startsWith(bookName)) {
									title = book.shortName;
									score = 20;
								} else if (n.contains(bookName)) {
									title = book.shortName;
									score = 10;
								}

								if (score != 0) {
									addCandidate(jumper, candidates, title, score, book);
									addedBookIds.add(book.bookId);
								}
							}

							// now try the levenstein
							if (candidates.size() < 5) {
								List<Jumper.BookRef> _bookRefs = this.bookRefs;
								if (_bookRefs == null) {
									this.bookRefs = _bookRefs = Jumper.createBookCandidates(books);
								}

								for (final Jumper.BookRef bookRef : _bookRefs) {
									if (addedBookIds.contains(bookRef.bookId)) continue;

									final int distance = Levenshtein.distance(bookName, bookRef.condensed);
									final Book book = bookIndex.get(bookRef.bookId);
									addCandidate(jumper, candidates, book.shortName, -distance, book);
									addedBookIds.add(bookRef.bookId);
								}
							}
						}
					}

					Collections.sort(candidates, (lhs, rhs) -> rhs.score - lhs.score);

					res.count = candidates.size();
					res.values = candidates;
					return res;
				}

				private void addCandidate(final Jumper jumper, final ArrayList<Candidate> values, String title, final int score, final Book book) {
					boolean bookOnly = true;

					// try to add chapter and verse
					final int chapter_1 = jumper.getChapter();
					if (chapter_1 != 0) {
						bookOnly = false;
						title += " " + chapter_1;
						final int verse_1 = jumper.getVerse();
						if (verse_1 != 0) {
							title += ":" + verse_1;
						}

						// do not add if the chapter is unavailable
						if (chapter_1 < 1 || chapter_1 > book.chapter_count) {
							return;
						} else {
							if (verse_1 != 0 && (verse_1 < 1 || verse_1 > book.verse_counts[chapter_1 - 1])) {
								return;
							}
						}
					}

					final Candidate c = new Candidate();
					c.title = title;
					c.score = score;
					c.bookOnly = bookOnly;
					values.add(c);
				}

				@Override
				protected void publishResults(final CharSequence constraint, final FilterResults results) {
					candidates.clear();

					if (results.values != null) {
						//noinspection unchecked
						candidates.addAll((List<Candidate>) results.values);
					}

					notifyDataSetChanged();
				}

				@Override
				public CharSequence convertResultToString(final Object resultValue) {
					final Candidate c = (Candidate) resultValue;
					if (c.bookOnly) {
						return c.title + " "; // for user to start typing the chapter number
					}

					return c.title;
				}
			};
		}
	}
}
