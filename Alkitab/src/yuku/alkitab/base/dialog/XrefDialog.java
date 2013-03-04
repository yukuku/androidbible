package yuku.alkitab.base.dialog;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import yuku.afw.D;
import yuku.afw.V;
import yuku.alkitab.R;
import yuku.alkitab.base.S;
import yuku.alkitab.base.dialog.base.BaseDialog;
import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.model.Book;
import yuku.alkitab.base.model.SingleChapterVerses;
import yuku.alkitab.base.model.XrefEntry;
import yuku.alkitab.base.util.Base64Mod;
import yuku.alkitab.base.util.IntArrayList;
import yuku.alkitab.base.util.LidToAri;
import yuku.alkitab.base.widget.VersesView;

public class XrefDialog extends BaseDialog {
	public static final String TAG = XrefDialog.class.getSimpleName();

	private static final String EXTRA_ari = "ari"; //$NON-NLS-1$
	private static final String EXTRA_which = "which"; //$NON-NLS-1$

	public interface XrefDialogListener {
		void onVerseSelected(int ari);
	}
	
	TextView tXrefText;
	VersesView versesView;
	
	XrefDialogListener listener;

	XrefEntry xrefEntry;
	int displayedLinkPos = -1; // -1 indicates that we should auto-select the first link
	
	public XrefDialog() {
	}
	
	public static XrefDialog newInstance(int ari, int which) {
		XrefDialog res = new XrefDialog();
		
        Bundle args = new Bundle();
        args.putInt(EXTRA_ari, ari);
        args.putInt(EXTRA_which, which);
        res.setArguments(args);

		return res;
	}
	
	@Override public void onAttach(Activity activity) {
		super.onAttach(activity);
		
		if (getParentFragment() instanceof XrefDialogListener) {
			listener = (XrefDialogListener) getParentFragment();
		} else {
			listener = (XrefDialogListener) activity;
		}
	}
	
	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setStyle(DialogFragment.STYLE_NO_TITLE, 0);

		int ari = getArguments().getInt(EXTRA_ari);
		int which = getArguments().getInt(EXTRA_which);
		xrefEntry = S.activeVersion.getXrefEntry(ari, which);
	}
	
	@Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View res = inflater.inflate(R.layout.dialog_xref, null);

		tXrefText = V.get(res, R.id.tXrefText);
		versesView = V.get(res, R.id.versesView);
		
		res.setBackgroundColor(S.applied.backgroundColor);
		versesView.setCacheColorHint(S.applied.backgroundColor);
		tXrefText.setMovementMethod(LinkMovementMethod.getInstance());
		renderXrefText();
		
		return res;
	}
	
	void renderXrefText() {
		final SpannableStringBuilder sb = new SpannableStringBuilder();
		
		sb.append(xrefEntry.source);
		sb.append(" ");
		
		final int[] linkPos = {0};
		findTags(xrefEntry.target, new FindTagsListener() {
			@Override public void onTaggedText(final String tag, int start, int end) {
				final int thisLinkPos = linkPos[0];
				linkPos[0]++;
				
				int sb_len = sb.length();
				sb.append(xrefEntry.target, start, end);
				
				if (tag.startsWith("t")) { // the only supported tag at the moment
					final String encodedTarget = tag.substring(1);
					
					if (thisLinkPos == displayedLinkPos || (displayedLinkPos == -1 && thisLinkPos == 0)) { 
						// just make it bold, because this is the currently displayed link
						sb.setSpan(new StyleSpan(Typeface.BOLD), sb_len, sb.length(), 0); 
						
						if (displayedLinkPos == -1) {
							showVerses(0, encodedTarget);
						}
					} else {
						sb.setSpan(new ClickableSpan() {
							@Override public void onClick(View widget) {
								showVerses(thisLinkPos, encodedTarget);
							}
						}, sb_len, sb.length(), 0);
					}
				}
			}
			
			@Override public void onPlainText(int start, int end) {
				sb.append(xrefEntry.target, start, end);
			}
		});
		
		tXrefText.setText(sb);
	}
	
	IntArrayList decodeTarget(String lid_s) {
		char[] lid_c = lid_s.toCharArray();
		int pos = 0;
		int last_lid = 0;
		IntArrayList res = new IntArrayList(8);
		
		boolean isStart = true;
		boolean endWritten = false;
		for (;; isStart = !isStart) {
			int lid;
			
			if (isStart) {
				endWritten = false;
				// 00 <addr 4bit> = 1char positive delta from last lid (max 16)
				// 01 <addr 4bit> = 1char positive delta from last lid (max 16), end == start
				// 100 <addr 9bit> = 2char positive delta from last lid (max 512)
				// 101 <addr 9bit> = 2char positive delta from last lid (max 512), end == start
				// 110 <addr 15bit> = 3char absolute
				// 111 <addr 15bit> = 3char absolute, end == start
				int b0 = Base64Mod.decodeFromChars(lid_c, pos++, 1);
				if ((b0 & 0x20) == 0x00) {
					lid = last_lid + (b0 & 0x0f);
					if ((b0 & 0x10) == 0x10) {
						endWritten = true;
					}
				} else if ((b0 & 0x30) == 0x20) {
					int b1 = Base64Mod.decodeFromChars(lid_c, pos++, 1);
					lid = last_lid + ((b0 & 0x7) << 6 | b1);
					if ((b0 & 0x08) == 0x08) {
						endWritten = true;
					}
				} else if ((b0 & 0x30) == 0x30) {
					int b1 = Base64Mod.decodeFromChars(lid_c, pos, 2);
					pos += 2;
					lid = (b0 & 0x7) << 12 | b1;
					if ((b0 & 0x08) == 0x08) {
						endWritten = true;
					}
				} else {
					lid = 0;
				}
			} else {
				if (endWritten) {
					lid = last_lid;
				} else {
					// 0 <delta 5bit> = 1char positive delta from start (min 1, max 32)
					// 10 <delta 10bit> = 2char positive delta from start (max 1024)
					// 110 <addr 15bit> = 3char absolute
					int b0 = Base64Mod.decodeFromChars(lid_c, pos++, 1);
					if ((b0 & 0x20) == 0x00) {
						lid = last_lid + (b0 & 0x1f);
					} else if ((b0 & 0x30) == 0x20) {
						int b1 = Base64Mod.decodeFromChars(lid_c, pos++, 1);
						lid = last_lid + ((b0 & 0xf) << 6 | b1);
					} else if ((b0 & 0x30) == 0x30) {
						int b1 = Base64Mod.decodeFromChars(lid_c, pos, 2);
						pos += 2;
						lid = (b0 & 0x7) << 12 | b1;
					} else {
						lid = 0;
					}
				}
			}
			
			res.add(lid);
			last_lid = lid;
			
			if (!isStart && pos >= lid_c.length) break;
		}
		
		return res;
	}

	void showVerses(int linkPos, String encodedTarget) {
		displayedLinkPos = linkPos;
		
		final IntArrayList ranges = decodeTarget(encodedTarget);
		for (int i = 0, len = ranges.size(); i < len; i++) {
			int ari = LidToAri.lidToAri(ranges.get(i));
			ranges.set(i, ari);
		}
		
		if (D.EBUG) {
			Log.d(TAG, "linkPos " + linkPos + " target=" + encodedTarget + " ranges=" + ranges);
		}
		
		Book b = null;
		int chapter_1 = 0;
		final List<String> verses = new ArrayList<String>();
		for (int i = 0, len = ranges.size(); i < len; i += 2) {
			int ari_start = ranges.get(i);
			int ari_end = ranges.get(i + 1);
			if (b == null) {
				b = S.activeVersion.getBook(Ari.toBook(ari_start));
			}
			if (chapter_1 == 0) {
				chapter_1 = Ari.toChapter(ari_start);
			}
			
			for (int ari = ari_start; ari <= ari_end; ari++) {
				String verseText = S.activeVersion.loadVerseText(ari);
				verses.add(verseText);
			}
		}
		
		class Verses extends SingleChapterVerses {
			@Override public String getVerse(int verse_0) {
				return verses.get(verse_0);
			}
			
			@Override public int getVerseCount() {
				return verses.size();
			}
		}

		versesView.setData(b, chapter_1, new Verses(), null, null, 0, null); 
		
		renderXrefText();
	}
	
	interface FindTagsListener {
		void onPlainText(int start, int end);
		void onTaggedText(String tag, int start, int end);
	}
	
	// look for "<@" "@>" "@/" tags
	void findTags(String s, FindTagsListener listener) {
		int pos = 0;
		while (true) {
			int p = s.indexOf("@<", pos);
			if (p == -1) break;
			
			listener.onPlainText(pos, p);
			
			int q = s.indexOf("@>", p+2);
			if (q == -1) break;
			int r = s.indexOf("@/", q+2);
			if (r == -1) break;
			
			listener.onTaggedText(s.substring(p+2, q), q+2, r);
			
			pos = r+2;
		}
		
		listener.onPlainText(pos, s.length());
	}
}
