package yuku.alkitab.base.dialog;

import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import yuku.afw.V;
import yuku.alkitab.base.S;
import yuku.alkitab.base.dialog.base.BaseDialog;
import yuku.alkitab.base.util.Appearances;
import yuku.alkitab.base.widget.VersesView;
import yuku.alkitab.base.widget.VersesView.VerseSelectionMode;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Book;
import yuku.alkitab.model.SingleChapterVerses;
import yuku.alkitab.model.Version;
import yuku.alkitab.util.Ari;
import yuku.alkitab.util.IntArrayList;

import java.util.ArrayList;
import java.util.List;

public class VersesDialog extends BaseDialog {
	public static final String TAG = VersesDialog.class.getSimpleName();

	private static final String EXTRA_ariRanges = "ariRanges"; //$NON-NLS-1$

	public interface VersesDialogListener {
		void onVerseSelected(VersesDialog dialog, int ari);
	}

	TextView tReference;
	VersesView versesView;

	VersesDialogListener listener;

	IntArrayList ariRanges;
	IntArrayList displayedAris;
	List<String> displayedVerseTexts;
	List<String> displayedVerseNumberTexts;
	Version sourceVersion = S.activeVersion;

	public VersesDialog() {
	}
	
	public static VersesDialog newInstance(final IntArrayList ariRanges) {
		VersesDialog res = new VersesDialog();
		
        Bundle args = new Bundle();
        args.putParcelable(EXTRA_ariRanges, ariRanges);
        res.setArguments(args);

		return res;
	}
	
	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setStyle(DialogFragment.STYLE_NO_TITLE, 0);

		ariRanges = getArguments().getParcelable(EXTRA_ariRanges);
	}
	
	@Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View res = inflater.inflate(R.layout.dialog_verses, null);

		tReference = V.get(res, R.id.tReference);
		versesView = V.get(res, R.id.versesView);

		res.setBackgroundColor(S.applied.backgroundColor);
		versesView.setCacheColorHint(S.applied.backgroundColor);
		versesView.setVerseSelectionMode(VerseSelectionMode.singleClick);
		versesView.setSelectedVersesListener(versesView_selectedVerses);

		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < ariRanges.size(); i += 2) {
			final int ari_start = ariRanges.get(i);
			final int ari_end = ariRanges.get(i + 1);
			if (sb.length() > 0) {
				sb.append("; ");
			}

			sb.append(sourceVersion.reference(ari_start));
			if (ari_start != ari_end) {
				sb.append("–");
				sb.append(sourceVersion.reference(ari_end));
			}
		}

		Appearances.applyTextAppearance(tReference);
		tReference.setText(sb);

		displayedAris = new IntArrayList();
		displayedVerseTexts = new ArrayList<String>();
		displayedVerseNumberTexts = new ArrayList<String>();
		final int verse_count = sourceVersion.loadVersesByAriRanges(ariRanges, displayedAris, displayedVerseTexts);

		if (verse_count > 0) {
			// set up verse number texts
			for (int i = 0; i < verse_count; i++) {
				final int ari = displayedAris.get(i);
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
	
			final int firstAri = ariRanges.get(0);
			final Book book = sourceVersion.getBook(Ari.toBook(firstAri));
			final int chapter_1 = Ari.toChapter(firstAri);
			
			versesView.setData(book, chapter_1, new Verses(), null, null, 0);
		}

		return res;
	}

	VersesView.SelectedVersesListener versesView_selectedVerses = new VersesView.SelectedVersesListener() {
		@Override public void onVerseSingleClick(VersesView v, int verse_1) {
			listener.onVerseSelected(VersesDialog.this, displayedAris.get(verse_1 - 1));
		}
		
		@Override public void onSomeVersesSelected(VersesView v) {}
		
		@Override public void onNoVersesSelected(VersesView v) {}
	};

	public void setListener(final VersesDialogListener listener) {
		this.listener = listener;
	}
}
