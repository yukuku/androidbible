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
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.base.util.Appearances;
import yuku.alkitab.base.util.Highlights;
import yuku.alkitab.base.util.TargetDecoder;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.PericopeBlock;
import yuku.alkitab.model.SingleChapterVerses;
import yuku.alkitab.util.Ari;
import yuku.alkitab.util.IntArrayList;


public class SingleViewVerseAdapter extends VerseAdapter {
	public static final String TAG = SingleViewVerseAdapter.class.getSimpleName();

	public static final int TYPE_VERSE_TEXT = 0;
	public static final int TYPE_PERICOPE = 1;

	public SingleViewVerseAdapter(Context context) {
		super(context);
	}

	@Override
	public int getViewTypeCount() {
		return 2;
	}

	@Override
	public int getItemViewType(final int position) {
		final int id = itemPointer_[position];
		if (id >= 0) {
			return TYPE_VERSE_TEXT;
		} else {
			return TYPE_PERICOPE;
		}
	}

	@Override public synchronized View getView(int position, View convertView, ViewGroup parent) {
		// Need to determine this is pericope or verse
		final int id = itemPointer_[position];

		if (id >= 0) {
			// VERSE. not pericope
			final int verse_1 = id + 1;

			boolean checked = false;
			if (parent instanceof ListView) {
				checked = ((ListView) parent).isItemChecked(position);
			}

			final OldVerseItem res;
			if (convertView == null) {
				res = (OldVerseItem) inflater_.inflate(R.layout.item_old_verse, parent, false);
			} else {
				res = (OldVerseItem) convertView;
			}

			final int ari = Ari.encodeWithBc(ari_bc_, verse_1);
			final String text = verses_.getVerse(id);
			final String verseNumberText = verses_.getVerseNumberText(id);
			final Highlights.Info highlightInfo = highlightInfoMap_ == null ? null : highlightInfoMap_[id];

			final VerseTextView lText = res.lText;
			final TextView lVerseNumber = res.lVerseNumber;

			final int startVerseTextPos = VerseRenderer.render(lText, lVerseNumber, ari, text, verseNumberText, highlightInfo, checked, inlineLinkSpanFactory_, null);

			final float textSizeMult;
			if (verses_ instanceof SingleChapterVerses.WithTextSizeMult) {
				textSizeMult = ((SingleChapterVerses.WithTextSizeMult) verses_).getTextSizeMult(id);
			} else {
				textSizeMult = textSizeMult_;
			}

			Appearances.applyTextAppearance(lText, textSizeMult);
			Appearances.applyVerseNumberAppearance(lVerseNumber, textSizeMult);

			if (checked) { // override text color with black or white!
				final int selectedTextColor = U.getTextColorForSelectedVerse(Preferences.getInt(R.string.pref_selectedVerseBgColor_key, R.integer.pref_selectedVerseBgColor_default));
				lText.setTextColor(selectedTextColor);
				lVerseNumber.setTextColor(selectedTextColor);
			}

			final OldAttributeView attributeView = res.attributeView;
			attributeView.setScale(scaleForAttributeView(S.applied().fontSize2dp * textSizeMult_));
			attributeView.setBookmarkCount(bookmarkCountMap_ == null ? 0 : bookmarkCountMap_[id]);
			attributeView.setNoteCount(noteCountMap_ == null ? 0 : noteCountMap_[id]);
			attributeView.setProgressMarkBits(progressMarkBitsMap_ == null ? 0 : progressMarkBitsMap_[id]);
			attributeView.setHasMaps(hasMapsMap_ != null && hasMapsMap_[id]);
			attributeView.setAttributeListener(attributeListener_, version_, versionId_, ari);

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
				Cursor c = null;
				try {
					c = cr.query(uri, null, null, null, null);
				} catch (Exception e) {
					AppLog.e(TAG, "Error when querying dictionary content provider", e);
				}

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

			// Do we need to call attention?
			if (attentionStart_ != 0 && attentionPositions_ != null && attentionPositions_.contains(position)) {
				res.callAttention(attentionStart_);
			} else {
				res.callAttention(0);
			}

			return res;
		} else {
			// PERICOPE. not verse.

			final PericopeHeaderItem res;
			if (convertView == null) {
				res = (PericopeHeaderItem) inflater_.inflate(R.layout.item_pericope_header, parent, false);
			} else {
				res = (PericopeHeaderItem) convertView;
			}

			PericopeBlock pericopeBlock = pericopeBlocks_[~id];

			TextView lCaption = res.findViewById(R.id.lCaption);
			TextView lParallels = res.findViewById(R.id.lParallels);

			lCaption.setText(FormattedTextRenderer.render(pericopeBlock.title));

			int paddingTop;
			// turn off top padding if the position == 0 OR before this is also a pericope title
			if (position == 0 || itemPointer_[position - 1] < 0) {
				paddingTop = 0;
			} else {
				paddingTop = S.applied().pericopeSpacingTop;
			}

			res.setPadding(0, paddingTop, 0, S.applied().pericopeSpacingBottom);

			Appearances.applyPericopeTitleAppearance(lCaption, textSizeMult_);

			// make parallel gone if not exist
			if (pericopeBlock.parallels.length == 0) {
				lParallels.setVisibility(View.GONE);
			} else {
				lParallels.setVisibility(View.VISIBLE);

				SpannableStringBuilder sb = new SpannableStringBuilder("(");

				int total = pericopeBlock.parallels.length;
				for (int i = 0; i < total; i++) {
					String parallel = pericopeBlock.parallels[i];

					if (i > 0) {
						// force new line for certain parallel patterns
						if ((total == 6 && i == 3) || (total == 4 && i == 2) || (total == 5 && i == 3)) {
							sb.append("; \n");
						} else {
							sb.append("; ");
						}
					}

					appendParallel(sb, parallel);
				}
				sb.append(')');

				lParallels.setText(sb, TextView.BufferType.SPANNABLE);
				Appearances.applyPericopeParallelTextAppearance(lParallels, textSizeMult_);
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


	static float scaleForAttributeView(final float fontSizeDp) {
		if (fontSizeDp >= 13 /* 72% */ && fontSizeDp < 24 /* 133% */) {
			return 1.f;
		}

		if (fontSizeDp < 8) return 0.5f; // 0 ~ 44%
		if (fontSizeDp < 18) return 0.75f; // 44% ~ 72%
		if (fontSizeDp >= 36) return 2.f; // 200% ~
		return 1.5f; // 24 to 36 // 133% ~ 200%
	}

	private SparseBooleanArray dictionaryModeAris;

	CallbackSpan.OnClickListener<DictionaryLinkInfo> dictionaryListener_;

	public void setDictionaryModeAris(final SparseBooleanArray aris) {
		this.dictionaryModeAris = aris;
		notifyDataSetChanged();
	}

	public void setDictionaryListener(final CallbackSpan.OnClickListener<DictionaryLinkInfo> listener) {
		this.dictionaryListener_ = listener;
		notifyDataSetChanged();
	}

	// ################## migration marker
}
