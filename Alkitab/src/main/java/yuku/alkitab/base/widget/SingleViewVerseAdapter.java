package yuku.alkitab.base.widget;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.util.SparseBooleanArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.BufferType;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.util.Appearances;
import yuku.alkitab.base.util.TargetDecoder;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.PericopeBlock;
import yuku.alkitab.util.Ari;
import yuku.alkitab.util.IntArrayList;


public class SingleViewVerseAdapter extends VerseAdapter {
	public static final String TAG = SingleViewVerseAdapter.class.getSimpleName();
	private SparseBooleanArray dictionaryModeAris;

	public static class DictionaryLinkInfo {
		public String orig_text;
		public String key;

		public DictionaryLinkInfo(final String orig_text, final String key) {
			this.orig_text = orig_text;
			this.key = key;
		}
	}

	CallbackSpan.OnClickListener<DictionaryLinkInfo> dictionaryListener_;

	public SingleViewVerseAdapter(Context context) {
		super(context);
	}

	@Override public synchronized View getView(int position, View convertView, ViewGroup parent) {
		// Need to determine this is pericope or verse
		int id = itemPointer_[position];

		if (id >= 0) {
			// VERSE. not pericope
			final int verse_1 = id + 1;

			boolean checked = false;
			if (parent instanceof ListView) {
				checked = ((ListView) parent).isItemChecked(position);
			}

			final VerseItem res;
			if (convertView == null || convertView.getId() != R.id.itemVerse) {
				res = (VerseItem) inflater_.inflate(R.layout.item_verse, parent, false);
			} else {
				res = (VerseItem) convertView;
			}

			final int ari = Ari.encode(book_.bookId, chapter_1_, verse_1);
			final String text = verses_.getVerse(id);
			final String verseNumberText = verses_.getVerseNumberText(id);
			final boolean dontPutSpacingBefore = (position > 0 && itemPointer_[position - 1] < 0) || position == 0;
			final int highlightColor = (highlightColorMap_ != null && highlightColorMap_[id] != -1) ? U.alphaMixHighlight(highlightColorMap_[id]) : -1;

			final VerseTextView lText = res.lText;
			final int startVerseTextPos = VerseRenderer.render(lText, res.lVerseNumber, ari, text, verseNumberText, highlightColor, checked, dontPutSpacingBefore, inlineLinkSpanFactory_, owner_);

			Appearances.applyTextAppearance(lText);
			if (checked) {
				lText.setTextColor(0xff000000); // override with black!
			}

			final AttributeView attributeView = res.attributeView;
			attributeView.setBookmarkCount(bookmarkCountMap_ == null ? 0 : bookmarkCountMap_[id]);
			attributeView.setNoteCount(noteCountMap_ == null ? 0 : noteCountMap_[id]);
			attributeView.setProgressMarkBits(progressMarkBitsMap_ == null ? 0 : progressMarkBitsMap_[id]);
			attributeView.setAttributeListener(attributeListener_, book_, chapter_1_, verse_1);

			res.setCollapsed(text.length() == 0 && !attributeView.isShowingSomething());

			res.setAri(ari);

			/*
			 * Dictionary mode is activated on either of these conditions:
			 * 1. user manually activate dictionary mode after selecting verses
			 * 2. automatic lookup is on and this verse is selected (checked)
 			 */
			if ((dictionaryModeAris != null && dictionaryModeAris.get(ari))
				|| (checked && Preferences.getBoolean(res.getContext().getString(R.string.pref_autoDictionaryAnalyze_key), res.getContext().getResources().getBoolean(R.bool.pref_autoDictionaryAnalyze_default)))
				) {
				final ContentResolver cr = res.getContext().getContentResolver();

				final CharSequence renderedText = lText.getText();
				final SpannableStringBuilder verseText = renderedText instanceof SpannableStringBuilder ? (SpannableStringBuilder) renderedText : new SpannableStringBuilder(renderedText);

				// we have to exclude the verse numbers from analyze text
				final String analyzeString = verseText.toString().substring(startVerseTextPos);

				final Uri uri = Uri.parse("content://org.sabda.kamus.provider/analyze").buildUpon().appendQueryParameter("text", analyzeString).build();
				final Cursor c = cr.query(uri, null, null, null, null);
				if (c != null) {
					try {
						final int col_offset = c.getColumnIndexOrThrow("offset");
						final int col_len = c.getColumnIndexOrThrow("len");
						final int col_key = c.getColumnIndexOrThrow("key");

						while (c.moveToNext()) {
							final int offset = c.getInt(col_offset);
							final int len = c.getInt(col_len);
							final String key = c.getString(col_key);

							verseText.setSpan(new CallbackSpan<>(new DictionaryLinkInfo(analyzeString.substring(offset, offset + len), key), dictionaryListener_), startVerseTextPos + offset, startVerseTextPos + offset + len, 0);
						}
					} finally {
						c.close();
					}

					lText.setText(verseText);
				}
			}

//			{ // DUMP
//				Log.d(TAG, "==== DUMP verse " + (id + 1));
//				SpannedString sb = (SpannedString) lText.getText();
//				Object[] spans = sb.getSpans(0, sb.length(), Object.class);
//				for (Object span: spans) {
//					int start = sb.getSpanStart(span);
//					int end = sb.getSpanEnd(span);
//					Log.d(TAG, "Span " + span.getClass().getSimpleName() + " " + start + ".." + end + ": " + sb.toString().substring(start, end));
//				}
//			}

			return res;
		} else {
			// PERICOPE. not verse.

			final PericopeHeaderItem res;
			if (convertView == null || convertView.getId() != R.id.itemPericopeHeader) {
				res = (PericopeHeaderItem) inflater_.inflate(R.layout.item_pericope_header, parent, false);
			} else {
				res = (PericopeHeaderItem) convertView;
			}

			PericopeBlock pericopeBlock = pericopeBlocks_[-id - 1];

			TextView lCaption = (TextView) res.findViewById(R.id.lCaption);
			TextView lParallels = (TextView) res.findViewById(R.id.lParallels);

			lCaption.setText(FormattedTextRenderer.render(pericopeBlock.title));

			int paddingTop;
			// turn off top padding if the position == 0 OR before this is also a pericope title
			if (position == 0 || itemPointer_[position - 1] < 0) {
				paddingTop = 0;
			} else {
				paddingTop = S.applied.pericopeSpacingTop;
			}

			res.setPadding(0, paddingTop, 0, S.applied.pericopeSpacingBottom);

			Appearances.applyPericopeTitleAppearance(lCaption);

			// make parallel gone if not exist
			if (pericopeBlock.parallels.length == 0) {
				lParallels.setVisibility(View.GONE);
			} else {
				lParallels.setVisibility(View.VISIBLE);

				SpannableStringBuilder sb = new SpannableStringBuilder("("); //$NON-NLS-1$

				int total = pericopeBlock.parallels.length;
				for (int i = 0; i < total; i++) {
					String parallel = pericopeBlock.parallels[i];

					if (i > 0) {
						// force new line for certain parallel patterns
						if ((total == 6 && i == 3) || (total == 4 && i == 2) || (total == 5 && i == 3)) {
							sb.append("; \n"); //$NON-NLS-1$
						} else {
							sb.append("; "); //$NON-NLS-1$
						}
					}

                    appendParallel(sb, parallel);
				}
				sb.append(')');

				lParallels.setText(sb, BufferType.SPANNABLE);
				Appearances.applyPericopeParallelTextAppearance(lParallels);
			}

			return res;
		}
	}

	private void appendParallel(SpannableStringBuilder sb, String parallel) {
        int sb_len = sb.length();

        linked: {
            if (parallel.startsWith("@")) {
	            // look for the end
	            int targetEndPos = parallel.indexOf(' ', 1);
	            if (targetEndPos == -1) {
		            break linked;
	            }

	            final String target = parallel.substring(1, targetEndPos);
	            final IntArrayList ariRanges = TargetDecoder.decode(target);
	            if (ariRanges == null || ariRanges.size() == 0) {
		            break linked;
	            }

	            final String display = parallel.substring(targetEndPos + 1);

                // if we reach this, data and display should have values, and we must not go to fallback below
                sb.append(display);
                sb.setSpan(new CallbackSpan<>(ariRanges.get(0), parallelListener_), sb_len, sb.length(), 0);
                return; // do not remove this
            }
        }

        // fallback if the above code fails
        sb.append(parallel);
        sb.setSpan(new CallbackSpan<>(parallel, parallelListener_), sb_len, sb.length(), 0);
    }

	public void setDictionaryModeAris(final SparseBooleanArray aris) {
		this.dictionaryModeAris = aris;
		notifyDataSetChanged();
	}

	public void setDictionaryListener(final CallbackSpan.OnClickListener<DictionaryLinkInfo> listener) {
		this.dictionaryListener_ = listener;
		notifyDataSetChanged();
	}
}
