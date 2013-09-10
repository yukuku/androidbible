package yuku.alkitab.base.widget;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.BufferType;

import yuku.alkitab.R;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.model.PericopeBlock;
import yuku.alkitab.base.util.Appearances;

/**
 * This has been completely superseded by {@link SingleViewVerseAdapter}, but because
 * of bugs in SEMC 2.3.x handsets, we must use this for those phones.
 */
public class LegacyVerseAdapter extends VerseAdapter {
	public static final String TAG = LegacyVerseAdapter.class.getSimpleName();
	
	public LegacyVerseAdapter(Context context) {
		super(context);
	}

	@Override public synchronized View getView(int position, View convertView, ViewGroup parent) {
		// Harus tentukan apakah ini perikop ato ayat.
		int id = itemPointer_[position];

		if (id >= 0) {
			// AYAT. bukan judul perikop.

			VerseItem res;

			String text = verses_.getVerse(id);

			boolean withHighlight = attributeMap_ != null && (attributeMap_[id] & 0x4) != 0;
			int highlightColor = withHighlight ? (highlightMap_ == null ? 0 : U.alphaMixHighlight(highlightMap_[id])) : 0;

			boolean checked = false;
			if (parent instanceof ListView) {
				checked = ((ListView) parent).isItemChecked(position);
			}

			// Udah ditentukan bahwa ini ayat dan bukan perikop, sekarang tinggal tentukan
			// apakah ayat ini pake formating biasa (tanpa menjorok dsb) atau ada formating
			if (text.length() > 0 && text.charAt(0) == '@') {
				// karakter kedua harus '@' juga, kalo bukan ada ngaco
				if (text.charAt(1) != '@') {
					throw new RuntimeException("Karakter kedua bukan @. Isi ayat: " + text); //$NON-NLS-1$
				}

				if (convertView == null || convertView.getId() != R.layout.item_legacy_verse_tiled) {
					res = (VerseItem) LayoutInflater.from(context_).inflate(R.layout.item_legacy_verse_tiled, null);
					res.setId(R.layout.item_legacy_verse_tiled);
				} else {
					res = (VerseItem) convertView;
				}

				boolean rightAfterPericope = position > 0 && itemPointer_[position - 1] < 0;
				
				tiledVerseDisplay(res.findViewById(R.id.sebelahKiri), id + 1, text, highlightColor, checked, rightAfterPericope);

			} else {
				if (convertView == null || convertView.getId() != R.layout.item_legacy_verse_simple) {
					res = (VerseItem) LayoutInflater.from(context_).inflate(R.layout.item_legacy_verse_simple, null);
					res.setId(R.layout.item_legacy_verse_simple);
				} else {
					res = (VerseItem) convertView;
				}

				TextView lIsiAyat = (TextView) res.findViewById(R.id.lIsiAyat);
				simpleVerseDisplay(lIsiAyat, id + 1, text, highlightColor, checked);

				Appearances.applyTextAppearance(lIsiAyat);
				if (checked) lIsiAyat.setTextColor(0xff000000); // override with black!
			}

			AttributeView attributeView = (AttributeView) res.findViewById(R.id.view_attributes);
			attributeView.showBookmark(attributeMap_ != null && (attributeMap_[id] & 0x1) != 0);
			attributeView.showNote(attributeMap_ != null && (attributeMap_[id] & 0x2) != 0);
			attributeView.showProgressMarks(attributeMap_ == null? 0: attributeMap_[id]);
			attributeView.setAttributeListener(attributeListener_, book_, chapter_1_, id + 1);

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

			TextView lJudul = (TextView) res.findViewById(R.id.lCaption);
			TextView lXparalel = (TextView) res.findViewById(R.id.lParallels);

			PericopeRenderer.render(lJudul, pericopeBlock.title);

			// matikan padding atas kalau position == 0 ATAU sebelum ini juga judul perikop
			if (position == 0 || itemPointer_[position - 1] < 0) {
				lJudul.setPadding(0, 0, 0, 0);
			} else {
				lJudul.setPadding(0, (int) (S.applied.fontSize2dp * density_), 0, 0);
			}

			Appearances.applyPericopeTitleAppearance(lJudul);

			// gonekan paralel kalo ga ada
			if (pericopeBlock.parallels.length == 0) {
				lXparalel.setVisibility(View.GONE);
			} else {
				lXparalel.setVisibility(View.VISIBLE);

				SpannableStringBuilder sb = new SpannableStringBuilder("("); //$NON-NLS-1$
				int len = 1;

				int total = pericopeBlock.parallels.length;
				for (int i = 0; i < total; i++) {
					String parallel = pericopeBlock.parallels[i];

					if (i > 0) {
						// paksa new line untuk pola2 paralel tertentu
						if ((total == 6 && i == 3) || (total == 4 && i == 2) || (total == 5 && i == 3)) {
							sb.append("; \n"); //$NON-NLS-1$
							len += 3;
						} else {
							sb.append("; "); //$NON-NLS-1$
							len += 2;
						}
					}

					sb.append(parallel);
					sb.setSpan(new CallbackSpan(parallel, parallelListener_), len, len + parallel.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
					len += parallel.length();
				}
				sb.append(')');
				len += 1;

				lXparalel.setText(sb, BufferType.SPANNABLE);
				Appearances.applyPericopeParallelTextAppearance(lXparalel);
			}

			return res;
		}
	}

	public void tiledVerseDisplay(View res, int ayat_1, String text, int warnaStabilo, boolean checked, boolean rightAfterPericope) {
		// Don't forget to modify indexOfPenehel below too
		// @@ = start a verse containing tiles or formatting
		// @0 = start with indent 0 [tiler]
		// @1 = start with indent 1 [tiler]
		// @2 = start with indent 2 [tiler]
		// @3 = start with indent 3 [tiler]
		// @4 = start with indent 4 [tiler]
		// @6 = start of red text [formatting]
		// @5 = end of red text   [formatting]
		// @9 = start of italic [formatting]
		// @7 = end of italic   [formatting]
		// @8 = put a blank line to the next verse [formatting]
		// @^ = start-of-paragraph marker
		int parsingPos = 2; // we start after "@@"
		int indent = 0;
		boolean pleaseExit = false;
		boolean noTileYet = true;
		boolean verseNumberWritten = false;

		LinearLayout tileContainer = (LinearLayout) res.findViewById(R.id.tempatTehel);
		tileContainer.removeAllViews();

		char[] text_c = text.toCharArray();

		while (true) {
			// cari posisi penehel berikutnya
			int pos_until = indexOfTiler(text, parsingPos);

			if (pos_until == -1) {
				// abis
				pos_until = text_c.length;
				pleaseExit = true;
			}

			if (parsingPos == pos_until) {
				// di awal, belum ada apa2!
			} else {
				{ // create a tile
					TextView tile = new TextView(context_);
					if (indent == 1) {
						tile.setPadding(S.applied.indentSpacing1 + (ayat_1 >= 100 ? S.applied.indentSpacingExtra : 0), 0, 0, 0);
					} else if (indent == 2) {
						tile.setPadding(S.applied.indentSpacing2 + (ayat_1 >= 100 ? S.applied.indentSpacingExtra : 0), 0, 0, 0);
					} else if (indent == 3) {
						tile.setPadding(S.applied.indentSpacing3 + (ayat_1 >= 100 ? S.applied.indentSpacingExtra : 0), 0, 0, 0);
					} else if (indent == 4) {
						tile.setPadding(S.applied.indentSpacing4 + (ayat_1 >= 100 ? S.applied.indentSpacingExtra : 0), 0, 0, 0);
					}

					// case: no tile yet and the first tile has 0 indent
					if (noTileYet && indent == 0) {
						// # kasih no ayat di depannya
						SpannableStringBuilder s = new SpannableStringBuilder();
						String verse_s = String.valueOf(ayat_1);
						s.append(verse_s).append(' ');
						
						// special case: if @^ is at the beginning
						if (!rightAfterPericope && text_c[parsingPos] == '@' && text_c[parsingPos+1] == '^') {
							tile.setPadding(tile.getPaddingLeft(), S.applied.paragraphSpacingBefore, 0, 0);
						}

						appendFormattedText2(s, text, text_c, parsingPos, pos_until);
						if (!checked) {
							s.setSpan(new ForegroundColorSpan(S.applied.verseNumberColor), 0, verse_s.length(), 0);
						}
						s.setSpan(new LeadingMarginSpan.Standard(0, S.applied.indentParagraphRest), 0, s.length(), 0);
						if (warnaStabilo != 0) {
							s.setSpan(new BackgroundColorSpan(warnaStabilo), verse_s.length() + 1, s.length(), 0);
						}
						tile.setText(s, BufferType.SPANNABLE);

						// kasi tanda biar nanti ga tulis nomer ayat lagi
						verseNumberWritten = true;
					} else {
						SpannableStringBuilder s = new SpannableStringBuilder();
						appendFormattedText2(s, text, text_c, parsingPos, pos_until);
						if (warnaStabilo != 0) {
							s.setSpan(new BackgroundColorSpan(warnaStabilo), 0, s.length(), 0);
						}
						tile.setText(s);
					}

					Appearances.applyTextAppearance(tile);
					if (checked) tile.setTextColor(0xff000000); // override with black!

					tileContainer.addView(tile);
				}

				noTileYet = false;
			}

			if (pleaseExit) break;

			char marker = text_c[pos_until + 1];
			if (marker == '1') {
				indent = 1;
			} else if (marker == '2') {
				indent = 2;
			} else if (marker == '3') {
				indent = 3;
			} else if (marker == '4') {
				indent = 4;
			}

			parsingPos = pos_until + 2;
		}

		TextView lAyat = (TextView) res.findViewById(R.id.lAyat);
		if (verseNumberWritten) {
			lAyat.setText(""); //$NON-NLS-1$
		} else {
			lAyat.setText(String.valueOf(ayat_1));
			
            lAyat.setTypeface(S.applied.fontFace, S.applied.fontBold);
            lAyat.setTextSize(TypedValue.COMPLEX_UNIT_DIP, S.applied.fontSize2dp);
            lAyat.setIncludeFontPadding(false);
            lAyat.setTextColor(S.applied.verseNumberColor);

			if (checked) lAyat.setTextColor(0xff000000);
		}
	}

	/**
	 * taro teks dari text[pos_from..pos_until] dengan format 6 atau 5 atau 9 atau 7 atau 8 ke s
	 * 
	 * @param text_c
	 *            string yang dari posDari sampe sebelum posSampe hanya berisi 6 atau 5 atau 9 atau 7 atau 8 tanpa mengandung @ lain.
	 */
	private void appendFormattedText2(SpannableStringBuilder s, String text, char[] text_c, int pos_from, int pos_until) {
		int redStart = -1; // posisi basis s. -1 artinya belum ketemu
		int italicStart = -1; // posisi basis s. -1 artinya belum ketemu
		
		for (int i = pos_from; i < pos_until; i++) {
			// coba templok aja sampe ketemu @ berikutnya. Jadi jangan satu2.
			{
				int nextAtPos = text.indexOf('@', i);
				if (nextAtPos == -1) {
					// udah ga ada lagi, tumplekin semua dan keluar dari method ini
					s.append(text, i, pos_until);
					return;
				} else {
					// tumplekin sampe sebelum @
					if (nextAtPos != i) { // kalo ga 0 panjangnya
						s.append(text, i, nextAtPos);
					}
					i = nextAtPos;
				}
			}

			i++; // satu char setelah @
			if (i >= pos_until) {
				// out of bounds
				break;
			}

			char d = text_c[i];
			if (d == '8') {
				s.append('\n');
				continue;
			}
			
			if (d == '^' && i >= pos_from + 2) { // the other case where @^ happens at the beginning is already handled outside this method
				s.append("\n\n");
			}

			if (d == '6') { // merah start
				redStart = s.length();
				continue;
			}

			if (d == '9') { // italic start
				italicStart = s.length();
				continue;
			}

			if (d == '5') { // merah ends
				if (redStart != -1) {
					s.setSpan(new ForegroundColorSpan(S.applied.fontRedColor), redStart, s.length(), 0);
					redStart = -1; // reset
				}
				continue;
			}

			if (d == '7') { // italic ends
				if (italicStart != -1) {
					s.setSpan(new StyleSpan(Typeface.ITALIC), italicStart, s.length(), 0);
					italicStart = -1; // reset
				}
				continue;
			}
		}
	}
	/** index of penehel berikutnya */
	private int indexOfTiler(String text, int start) {
		int length = text.length();
		while (true) {
			int pos = text.indexOf('@', start);
			if (pos == -1) {
				return -1;
			} else if (pos >= length - 1) {
				return -1; // tepat di akhir string, maka anggaplah tidak ada
			} else {
				char c = text.charAt(pos + 1);
				if (c >= '0' && c <= '4') {
					return pos;
				} else {
					start = pos + 2;
				}
			}
		}
	}

	static void simpleVerseDisplay(TextView lIsiAyat, int verse_1, String text, int highlightColor, boolean checked) {
		SpannableStringBuilder verse = new SpannableStringBuilder(
			"1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890" + //$NON-NLS-1$
			"1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890" + //$NON-NLS-1$
			"1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890" //$NON-NLS-1$
		); // pre-allocate
		
		verse.clear();

		// nomer ayat
		String verse_s = String.valueOf(verse_1);
		verse.append(verse_s).append(' ').append(text);
		if (!checked) {
			verse.setSpan(new ForegroundColorSpan(S.applied.verseNumberColor), 0, verse_s.length(), 0);
		}

		// teks
		verse.setSpan(new LeadingMarginSpan.Standard(0, S.applied.indentParagraphRest), 0, verse.length(), 0);

		if (highlightColor != 0) {
			verse.setSpan(new BackgroundColorSpan(highlightColor), verse_s.length() + 1, verse.length(), 0);
		}

		lIsiAyat.setText(verse, BufferType.SPANNABLE);
	}

}