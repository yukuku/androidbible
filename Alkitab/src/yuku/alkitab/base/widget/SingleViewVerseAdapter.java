package yuku.alkitab.base.widget;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.BufferType;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.model.PericopeBlock;
import yuku.alkitab.base.util.Appearances;
import yuku.alkitab.base.util.IntArrayList;
import yuku.alkitab.base.util.TargetDecoder;
import yuku.alkitab.debug.R;


public class SingleViewVerseAdapter extends VerseAdapter {
	public static final String TAG = SingleViewVerseAdapter.class.getSimpleName();
	
	public SingleViewVerseAdapter(Context context) {
		super(context);
	}

	@Override public synchronized View getView(int position, View convertView, ViewGroup parent) {
		// Need to determine this is pericope or verse
		int id = itemPointer_[position];

		if (id >= 0) {
			// VERSE. not pericope
			int verse_1 = id + 1;

			boolean checked = false;
			if (parent instanceof ListView) {
				checked = ((ListView) parent).isItemChecked(position);
			}

			VerseItem res;
			if (convertView == null || convertView.getId() != R.layout.item_verse) {
				res = (VerseItem) inflater_.inflate(R.layout.item_verse, parent, false);
				res.setId(R.layout.item_verse);
			} else {
				res = (VerseItem) convertView;
			}

			VerseTextView lText = (VerseTextView) res.findViewById(R.id.lText);
			TextView lVerseNumber = (TextView) res.findViewById(R.id.lVerseNumber);

			int ari = Ari.encode(book_.bookId, chapter_1_, verse_1);
			String text = verses_.getVerse(id);
			String verseNumberText = verses_.getVerseNumberText(id);
			boolean dontPutSpacingBefore = (position > 0 && itemPointer_[position - 1] < 0) || position == 0;
			boolean withHighlight = attributeMap_ != null && (attributeMap_[id] & 0x4) != 0;
			int highlightColor = withHighlight ? (highlightMap_ == null ? 0 : U.alphaMixHighlight(highlightMap_[id])) : 0;
			VerseRenderer.render(lText, lVerseNumber, ari, text, verseNumberText, highlightColor, checked, dontPutSpacingBefore, inlineLinkSpanFactory_, owner_);

			Appearances.applyTextAppearance(lText);
			if (checked) {
				lText.setTextColor(0xff000000); // override with black!
			}

			res.setShaded(checkShaded(ari));

			final AttributeView attributeView = (AttributeView) res.findViewById(R.id.view_attributes);
			attributeView.showBookmark(attributeMap_ != null && (attributeMap_[id] & 0x1) != 0);
			attributeView.showNote(attributeMap_ != null && (attributeMap_[id] & 0x2) != 0);
			attributeView.showProgressMarks(attributeMap_ == null? 0: attributeMap_[id]);
			attributeView.setAttributeListener(attributeListener_, book_, chapter_1_, verse_1);

			res.setCollapsed(text.length() == 0 && !attributeView.isShowingSomething());

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

			View res;
			if (convertView == null || convertView.getId() != R.layout.item_pericope_header) {
				res = LayoutInflater.from(context_).inflate(R.layout.item_pericope_header, parent, false);
				res.setId(R.layout.item_pericope_header);
			} else {
				res = convertView;
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

			if (checkShaded(Ari.encode(book_.bookId, chapter_1_, itemPointer_[position + 1]) + 1)) {
				res.setBackgroundResource(R.drawable.shade_verse);
			} else {
				res.setBackgroundResource(0);
			}

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

	public boolean checkShaded(final int ari) {
		if (ariRangesReadingPlan != null) {

			int ariStart = ariRangesReadingPlan[0];
			int ariEnd = ariRangesReadingPlan[1];

			int ariEndVerse = Ari.toVerse(ariEnd);
			int ariEndChapter = Ari.toChapter(ariEnd);

			if (Ari.toBook(ari) != Ari.toBook(ariRangesReadingPlan[0])) {
				return true;
			}

			if (ari < ariStart) {
				return true;
			} else if (ariEndVerse == 0) {
				if (Ari.toChapter(ari) > ariEndChapter) {
					return true;
				}
			} else if (ariEndChapter != 0) {
				if (ari > ariEnd) {
					return true;
				}
			}

		}
		return false;
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
                sb.setSpan(new CallbackSpan(ariRanges.get(0), parallelListener_), sb_len, sb.length(), 0);
                return; // do not remove this
            }
        }

        // fallback if the above code fails
        sb.append(parallel);
        sb.setSpan(new CallbackSpan(parallel, parallelListener_), sb_len, sb.length(), 0);
    }
}
