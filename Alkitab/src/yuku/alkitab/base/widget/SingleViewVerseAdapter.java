package yuku.alkitab.base.widget;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.BufferType;

import yuku.alkitab.R;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.model.PericopeBlock;
import yuku.alkitab.base.util.Appearances;


public class SingleViewVerseAdapter extends VerseAdapter {
	public static final String TAG = SingleViewVerseAdapter.class.getSimpleName();
	
	public SingleViewVerseAdapter(Context context) {
		super(context);
	}

	@Override public synchronized View getView(int position, View convertView, ViewGroup parent) {
		// Harus tentukan apakah ini perikop ato ayat.
		int id = itemPointer_[position];
		
		if (id >= 0) {
			// AYAT. bukan judul perikop.

			String text = verses_.getVerse(id);
			boolean withBookmark = attributeMap_ == null ? false : (attributeMap_[id] & 0x1) != 0;
			boolean withNote = attributeMap_ == null ? false : (attributeMap_[id] & 0x2) != 0;
			boolean withHighlight = attributeMap_ == null ? false : (attributeMap_[id] & 0x4) != 0;
			int withXref = /* FIXME */ id % 3;
			int highlightColor = withHighlight ? (highlightMap_ == null ? 0 : U.alphaMixHighlight(highlightMap_[id])) : 0;

			boolean checked = false;
			if (parent instanceof ListView) {
				checked = ((ListView) parent).isItemChecked(position);
			}

			VerseItem res;
			if (convertView == null || convertView.getId() != R.layout.item_verse) {
				res = (VerseItem) inflater_.inflate(R.layout.item_verse, null);
				res.setId(R.layout.item_verse);
			} else {
				res = (VerseItem) convertView;
			}
			
			VerseTextView lText = (VerseTextView) res.findViewById(R.id.lText);
			TextView lVerseNumber = (TextView) res.findViewById(R.id.lVerseNumber);
			
			// Udah ditentukan bahwa ini ayat dan bukan perikop, sekarang tinggal tentukan
			// apakah ayat ini pake formating biasa (tanpa menjorok dsb) atau ada formating
			if (text.length() > 0 && text.charAt(0) == '@') {
				// karakter kedua harus '@' juga, kalo bukan ada ngaco
				if (text.charAt(1) != '@') {
					throw new RuntimeException("Karakter kedua bukan @. Isi ayat: " + text); //$NON-NLS-1$
				}

				boolean dontPutSpacingBefore = (position > 0 && itemPointer_[position - 1] < 0) || position == 0;
				
				tiledVerseDisplay(lText, lVerseNumber, id + 1, text, highlightColor, checked, dontPutSpacingBefore, withXref);
			} else {
				simpleVerseDisplay(lText, lVerseNumber, id + 1, text, highlightColor, checked, withXref);
			}
			
			Appearances.applyTextAppearance(lText);
			if (checked) {
				lText.setTextColor(0xff000000); // override with black!
			}

			View imgAttributeBookmark = res.findViewById(R.id.imgAtributBukmak);
			imgAttributeBookmark.setVisibility(withBookmark ? View.VISIBLE : View.GONE);
			if (withBookmark) {
				setClickListenerForBookmark(imgAttributeBookmark, chapter_1_, id + 1);
			}
			View imgAttributeNote = res.findViewById(R.id.imgAtributCatatan);
			imgAttributeNote.setVisibility(withNote ? View.VISIBLE : View.GONE);
			if (withNote) {
				setClickListenerForNote(imgAttributeNote, chapter_1_, id + 1);
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
			// JUDUL PERIKOP. bukan ayat.

			View res;
			if (convertView == null || convertView.getId() != R.layout.item_pericope_header) {
				res = LayoutInflater.from(context_).inflate(R.layout.item_pericope_header, null);
				res.setId(R.layout.item_pericope_header);
			} else {
				res = convertView;
			}

			PericopeBlock pericopeBlock = pericopeBlocks_[-id - 1];

			TextView lJudul = (TextView) res.findViewById(R.id.lJudul);
			TextView lXparalel = (TextView) res.findViewById(R.id.lXparalel);

			lJudul.setText(pericopeBlock.title);

			int paddingTop;
			// turn off top padding if the position == 0 OR before this is also a pericope title
			if (position == 0 || itemPointer_[position - 1] < 0) {
				paddingTop = 0;
			} else {
				paddingTop = S.applied.pericopeSpacingTop;
			}
			
			res.setPadding(0, paddingTop, 0, S.applied.pericopeSpacingBottom);

			Appearances.applyPericopeTitleAppearance(lJudul);

			// gonekan paralel kalo ga ada
			if (pericopeBlock.parallels.length == 0) {
				lXparalel.setVisibility(View.GONE);
			} else {
				lXparalel.setVisibility(View.VISIBLE);

				SpannableStringBuilder sb = new SpannableStringBuilder("("); //$NON-NLS-1$

				int total = pericopeBlock.parallels.length;
				for (int i = 0; i < total; i++) {
					String parallel = pericopeBlock.parallels[i];

					if (i > 0) {
						// paksa new line untuk pola2 paralel tertentu
						if ((total == 6 && i == 3) || (total == 4 && i == 2) || (total == 5 && i == 3)) {
							sb.append("; \n"); //$NON-NLS-1$
						} else {
							sb.append("; "); //$NON-NLS-1$
						}
					}

                    appendParallel(sb, parallel);
				}
				sb.append(')');

				lXparalel.setText(sb, BufferType.SPANNABLE);
				Appearances.applyPericopeParallelTextAppearance(lXparalel);
			}

			return res;
		}
	}

    private void appendParallel(SpannableStringBuilder sb, String parallel) {
        int sb_len = sb.length();

        linked: {
            if (parallel.startsWith("@")) {
                Object data;
                String display;

                if (parallel.startsWith("@o:")) { // osis ref
                    int space = parallel.indexOf(' ');
                    if (space != -1) {
                        String osis = parallel.substring(3, space);
                        int dash = osis.indexOf('-');
                        if (dash != -1) {
                            osis = osis.substring(0, dash);
                        }
                        ParallelTypeOsis d = new ParallelTypeOsis();
                        d.osisStart = osis;
                        data = d;
                        display = parallel.substring(space + 1);
                    } else {
                        break linked;
                    }
                } else if (parallel.startsWith("@a:")) { // ari ref
                    int space = parallel.indexOf(' ');
                    if (space != -1) {
                        String ari_s = parallel.substring(3, space);
                        int dash = ari_s.indexOf('-');
                        if (dash != -1) {
                            ari_s = ari_s.substring(0, dash);
                        }
                        int ari = Ari.parseInt(ari_s, 0);
                        if (ari == 0) {
                            break linked;
                        }
                        ParallelTypeAri d = new ParallelTypeAri();
                        d.ariStart = ari;
                        data = d;
                        display = parallel.substring(space + 1);
                    } else {
                        break linked;
                    }
                } else if (parallel.startsWith("@lid:")) { // lid ref
                    int space = parallel.indexOf(' ');
                    if (space != -1) {
                        String lid_s = parallel.substring(5, space);
                        int dash = lid_s.indexOf('-');
                        if (dash != -1) {
                            lid_s = lid_s.substring(0, dash);
                        }
                        int lid = Ari.parseInt(lid_s, 0);
                        if (lid == 0) {
                            break linked;
                        }
                        ParallelTypeLid d = new ParallelTypeLid();
                        d.lidStart = lid;
                        data = d;
                        display = parallel.substring(space + 1);
                    } else {
                        break linked;
                    }
                } else {
                    break linked;
                }

                // if we reach this, data and display should have values, and we must not go to fallback below
                sb.append(display);
                sb.setSpan(new CallbackSpan(data, parallelListener_), sb_len, sb.length(), 0);
                return; // do not remove this
            }
        }

        // fallback if the above code fails
        sb.append(parallel);
        sb.setSpan(new CallbackSpan(parallel, parallelListener_), sb_len, sb.length(), 0);
    }


    /**
	 * @param dontPutSpacingBefore this verse is right after a pericope title or on the 0th position
     * @param withXref 
	 */
	private static void tiledVerseDisplay(TextView lText, TextView lVerseNumber, int verse_1, String text, int highlightColor, boolean checked, boolean dontPutSpacingBefore, int withXref) {
		// @@ = start a verse containing paragraphs or formatting
		// @0 = start with indent 0 [paragraph]
		// @1 = start with indent 1 [paragraph]
		// @2 = start with indent 2 [paragraph]
		// @3 = start with indent 3 [paragraph]
		// @4 = start with indent 4 [paragraph]
		// @6 = start of red text [formatting]
		// @5 = end of red text   [formatting]
		// @9 = start of italic [formatting]
		// @7 = end of italic   [formatting]
		// @8 = put a blank line to the next verse [formatting]
		// @^ = start-of-paragraph marker
		
		// optimization, to prevent repeated calls to charAt()
		char[] text_c = text.toCharArray();
		
		/**
		 * '0'..'4', '^' indent 0..4 or new para
		 * -1 undefined
		 */
		int paraType = -1; 
		/**
		 * position of start of paragraph
		 */
		int startPara = 0;
		/**
		 * position of start red marker
		 */
		int startRed = -1;
		/**
		 * position of start italic marker
		 */
		int startItalic = -1;
		
		SpannableStringBuilder s = new SpannableStringBuilder();

		// this has two uses
		// - to check whether a verse number has been written
		// - to check whether we need to put a new line when encountering a new para 
		int startPosAfterVerseNumber = 0;
		String verseNumber_s = Integer.toString(verse_1);

		int pos = 2; // we start after "@@"

		// write verse number inline only when no @[1234^] on the beginning of text
		if (text_c.length >= 4 && text_c[pos] == '@' && (text_c[pos+1] == '^' || (text_c[pos+1] >= '1' && text_c[pos+1] <= '4'))) {
			// don't write verse number now
		} else {
			s.append(verseNumber_s);
			s.setSpan(new VerseNumberSpan(!checked), 0, s.length(), 0);
			s.append("  ");
			startPosAfterVerseNumber = s.length();
		}

		
		// initialize lVerseNumber to have no padding first
		lVerseNumber.setPadding(0, 0, 0, 0);
		
		while (true) {
			if (pos >= text_c.length) {
				break;
			}

			int nextAt = text.indexOf('@', pos);
			
			if (nextAt == -1) { // no more, just append till the end of everything and exit
				s.append(text, pos, text.length());
				break;
			}
			
			// insert all text until the nextAt
			if (nextAt != pos) /* optimization */ {
				s.append(text, pos, nextAt);
				pos = nextAt;
			}
			
			pos++;
			// just in case 
			if (pos >= text_c.length) {
				break;
			}
			
			char marker = text_c[pos];
			switch (marker) {
			case '0':
			case '1':
			case '2':
			case '3':
			case '4':
			case '^':
				// apply previous
				applyParaStyle(s, paraType, startPara, verse_1, startPosAfterVerseNumber > 0, dontPutSpacingBefore && startPara <= startPosAfterVerseNumber, startPara <= startPosAfterVerseNumber, lVerseNumber);
				if (s.length() > startPosAfterVerseNumber) {
					s.append("\n");
				}
				// store current
				paraType = marker;
				startPara = s.length();
				break;
			case '6':
				startRed = s.length();
				break;
			case '9':
				startItalic = s.length();
				break;
			case '5':
				if (startRed != -1) {
					if (!checked) {
						s.setSpan(new ForegroundColorSpan(S.applied.fontRedColor), startRed, s.length(), 0);
					}
					startRed = -1;
				}
				break;
			case '7':
				if (startItalic != -1) {
					s.setSpan(new StyleSpan(Typeface.ITALIC), startItalic, s.length(), 0);
					startItalic = -1;
				}
				break;
			case '8':
				s.append("\n");
				break;
			}
			
			pos++;
		}
		
		// apply unapplied
		applyParaStyle(s, paraType, startPara, verse_1, startPosAfterVerseNumber > 0, dontPutSpacingBefore && startPara <= startPosAfterVerseNumber, startPara <= startPosAfterVerseNumber, lVerseNumber);

		if (highlightColor != 0) {
			s.setSpan(new BackgroundColorSpan(highlightColor), startPosAfterVerseNumber == 0? 0: verseNumber_s.length() + 1, s.length(), 0);
		}
		
		for (int i = 0; i < withXref; i++) {
			addXrefLink(lText.getContext(), s, verse_1, i);
		}

		lText.setText(s);
		
		// show verse on lVerseNumber if not shown in lText yet
		if (startPosAfterVerseNumber > 0) {
			lVerseNumber.setText(""); //$NON-NLS-1$
		} else {
			lVerseNumber.setText(verseNumber_s);
			Appearances.applyVerseNumberAppearance(lVerseNumber);
			if (checked) {
				lVerseNumber.setTextColor(0xff000000); // override with black!
			}
		}
	}
	
	/**
	 * @param paraType if -1, will apply the same thing as when paraType is 0 and firstLineWithVerseNumber is true.
	 * @param firstLineWithVerseNumber If this is formatting for the first paragraph of a verse and that paragraph contains a verse number, so we can apply more lefty first-line indent.
	 * This only applies if the paraType is 0.
	 * @param dontPutSpacingBefore if this paragraph is just after pericope title or on the 0th position, in this case we don't apply paragraph spacing before.
	 * @return whether we should put a top-spacing to the detached verse number too
	 */
	private static void applyParaStyle(SpannableStringBuilder sb, int paraType, int startPara, int verse_1, boolean firstLineWithVerseNumber, boolean dontPutSpacingBefore, boolean firstParagraph, TextView lVerseNumber) {
		int len = sb.length();
		
		if (startPara == len) return;
		
		switch (paraType) {
		case -1:
			sb.setSpan(createLeadingMarginSpan(0, S.applied.indentParagraphRest), startPara, len, 0);
			break;
		case '0':
			if (firstLineWithVerseNumber) {
				sb.setSpan(createLeadingMarginSpan(0, S.applied.indentParagraphRest), startPara, len, 0);
			} else {
				sb.setSpan(createLeadingMarginSpan(S.applied.indentParagraphRest), startPara, len, 0);
			}
			break;
		case '1':
			sb.setSpan(createLeadingMarginSpan(S.applied.indentSpacing1 + (verse_1 >= 100 ? S.applied.indentSpacingExtra : 0)), startPara, len, 0);
			break;
		case '2':
			sb.setSpan(createLeadingMarginSpan(S.applied.indentSpacing2 + (verse_1 >= 100 ? S.applied.indentSpacingExtra : 0)), startPara, len, 0);
			break;
		case '3':
			sb.setSpan(createLeadingMarginSpan(S.applied.indentSpacing3 + (verse_1 >= 100 ? S.applied.indentSpacingExtra : 0)), startPara, len, 0);
			break;
		case '4':
			sb.setSpan(createLeadingMarginSpan(S.applied.indentSpacing4 + (verse_1 >= 100 ? S.applied.indentSpacingExtra : 0)), startPara, len, 0);
			break;
		case '^':
			if (!dontPutSpacingBefore) {
				sb.setSpan(new ParagraphSpacingBefore(S.applied.paragraphSpacingBefore), startPara, len, 0);
				if (firstParagraph) {
					lVerseNumber.setPadding(0, S.applied.paragraphSpacingBefore, 0, 0);
				}
			}
			sb.setSpan(createLeadingMarginSpan(S.applied.indentParagraphFirst, S.applied.indentParagraphRest), startPara, len, 0);
			break;
		}
	}

	protected static void simpleVerseDisplay(TextView lText, TextView lVerseNumber, int verse_1, String text, int highlightColor, boolean checked, int withXref) {
		// initialize lVerseNumber to have no padding first
		lVerseNumber.setPadding(0, 0, 0, 0);
		
		SpannableStringBuilder s = new SpannableStringBuilder();

		// verse number
		String verse_s = Integer.toString(verse_1);
		s.append(verse_s).append("  ").append(text);
		s.setSpan(new VerseNumberSpan(!checked), 0, verse_s.length(), 0);

		// verse text
		s.setSpan(createLeadingMarginSpan(0, S.applied.indentParagraphRest), 0, s.length(), 0);

		if (highlightColor != 0) {
			s.setSpan(new BackgroundColorSpan(highlightColor), verse_s.length() + 1, s.length(), 0);
		}
		
		for (int i = 0; i < withXref; i++) {
			addXrefLink(lText.getContext(), s, verse_1, i);
		}

		lText.setText(s);
		lVerseNumber.setText("");
	}
	
	static void addXrefLink(final Context context, SpannableStringBuilder sb, final int verse_1, final int pos) {
		// if last char of this sb is newline, move back.
		int sb_start = sb.length();
		if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
			sb_start --;
		}
		
		sb.insert(sb_start, "\u2022 "); // append space after it to prevent false click detection
		int sb_end = sb_start+1;
		
		sb.setSpan(new ImageSpan(context, R.drawable.ic_btn_search, DynamicDrawableSpan.ALIGN_BASELINE), sb_start, sb_start+1, 0);
		sb.setSpan(new ClickableSpan() {
			@Override public void onClick(View widget) {
				Log.d(TAG, "CLICK! " + verse_1 + " " + pos);
				
				new AlertDialog.Builder(context)
				.setMessage("click verse_1=" + verse_1 + " pos=" + pos)
				.setPositiveButton("OK", null)
				.show();
			}
		}, sb_start, sb_end, 0);
	}
}