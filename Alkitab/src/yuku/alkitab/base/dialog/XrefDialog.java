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
import yuku.afw.D;
import yuku.afw.V;
import yuku.alkitab.debug.R;
import yuku.alkitab.base.S;
import yuku.alkitab.base.dialog.base.BaseDialog;
import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.model.Book;
import yuku.alkitab.base.model.SingleChapterVerses;
import yuku.alkitab.base.model.Version;
import yuku.alkitab.base.model.XrefEntry;
import yuku.alkitab.base.util.Appearances;
import yuku.alkitab.base.util.IntArrayList;
import yuku.alkitab.base.util.TargetDecoder;
import yuku.alkitab.base.widget.VerseRenderer;
import yuku.alkitab.base.widget.VersesView;
import yuku.alkitab.base.widget.VersesView.VerseSelectionMode;

import java.util.ArrayList;
import java.util.List;

public class XrefDialog extends BaseDialog {
	public static final String TAG = XrefDialog.class.getSimpleName();

	private static final String EXTRA_arif = "arif"; //$NON-NLS-1$

	public interface XrefDialogListener {
		void onVerseSelected(XrefDialog dialog, int arif_source, int ari_target);
	}
	
	TextView tXrefText;
	VersesView versesView;
	
	XrefDialogListener listener;

	int arif_source;
	XrefEntry xrefEntry;
	int displayedLinkPos = -1; // -1 indicates that we should auto-select the first link
	List<String> displayedVerseTexts;
	List<String> displayedVerseNumberTexts;
	IntArrayList displayedRealAris;
	Version sourceVersion = S.activeVersion;
	
	public XrefDialog() {
	}
	
	public static XrefDialog newInstance(int arif) {
		XrefDialog res = new XrefDialog();
		
        Bundle args = new Bundle();
        args.putInt(EXTRA_arif, arif);
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

		arif_source = getArguments().getInt(EXTRA_arif);
		xrefEntry = sourceVersion.getXrefEntry(arif_source);
	}
	
	@Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View res = inflater.inflate(R.layout.dialog_xref, null);

		tXrefText = V.get(res, R.id.tXrefText);
		versesView = V.get(res, R.id.versesView);
		
		res.setBackgroundColor(S.applied.backgroundColor);
		versesView.setCacheColorHint(S.applied.backgroundColor);
		versesView.setVerseSelectionMode(VerseSelectionMode.singleClick);
		versesView.setSelectedVersesListener(versesView_selectedVerses);
		tXrefText.setMovementMethod(LinkMovementMethod.getInstance());
		renderXrefText();
		
		return res;
	}
	
	void renderXrefText() {
		final SpannableStringBuilder sb = new SpannableStringBuilder();
		sb.append(VerseRenderer.XREF_MARK);
		sb.append(" ");
		
		final int[] linkPos = {0};
		findTags(xrefEntry.content, new FindTagsListener() {
			@Override public void onTaggedText(final String tag, int start, int end) {
				final int thisLinkPos = linkPos[0];
				linkPos[0]++;
				
				int sb_len = sb.length();
				sb.append(xrefEntry.content, start, end);
				
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
				sb.append(xrefEntry.content, start, end);
			}
		});
		
		Appearances.applyTextAppearance(tXrefText);
		
		tXrefText.setText(sb);
	}

	void showVerses(int linkPos, String encodedTarget) {
		displayedLinkPos = linkPos;
		
		final IntArrayList ranges = decodeTarget(encodedTarget);

		if (D.EBUG) {
			Log.d(TAG, "linkPos " + linkPos + " target=" + encodedTarget + " ranges=" + ranges);
		}
		
		displayedVerseTexts = new ArrayList<String>();
		displayedVerseNumberTexts = new ArrayList<String>();
		displayedRealAris = new IntArrayList();

		int verse_count = sourceVersion.loadVersesByAriRanges(ranges, displayedRealAris, displayedVerseTexts);
		if (verse_count > 0) {
			// set up verse number texts
			for (int i = 0; i < verse_count; i++) {
				int ari = displayedRealAris.get(i);
				displayedVerseNumberTexts.add(Ari.toChapter(ari) + ":" + Ari.toVerse(ari));
			}
		
			class Verses extends SingleChapterVerses {
				@Override public String getVerse(int verse_0) {
					return displayedVerseTexts.get(verse_0);
				}
				
				@Override public int getVerseCount() {
					return displayedVerseTexts.size();
				}
				
				@Override public String getVerseNumberText(int verse_0) {
					return displayedVerseNumberTexts.get(verse_0);
				}
			}
	
			int firstAri = displayedRealAris.get(0);
			Book book = sourceVersion.getBook(Ari.toBook(firstAri));
			int chapter_1 = Ari.toChapter(firstAri);
			
			versesView.setData(book, chapter_1, new Verses(), null, null, 0);
		}
		
		renderXrefText();
	}

	private IntArrayList decodeTarget(final String encodedTarget) {
		return TargetDecoder.decode(encodedTarget);
	}

	VersesView.SelectedVersesListener versesView_selectedVerses = new VersesView.SelectedVersesListener() {
		@Override public void onVerseSingleClick(VersesView v, int verse_1) {
			listener.onVerseSelected(XrefDialog.this, arif_source, displayedRealAris.get(verse_1 - 1));
		}
		
		@Override public void onSomeVersesSelected(VersesView v) {}
		
		@Override public void onNoVersesSelected(VersesView v) {}
	};

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

	public void setSourceVersion(Version sourceVersion) {
		this.sourceVersion = sourceVersion;
	}
}
