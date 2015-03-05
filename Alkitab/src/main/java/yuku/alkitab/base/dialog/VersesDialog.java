package yuku.alkitab.base.dialog;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import yuku.afw.V;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.dialog.base.BaseDialog;
import yuku.alkitab.base.model.MVersion;
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
import java.util.Collections;
import java.util.List;

/**
 * Dialog that shows a list of verses. There are two modes:
 * "normal mode" that is created via {@link #newInstance(yuku.alkitab.util.IntArrayList)} to show a list of verses from a single version.
 * -- field ariRanges is used, compareMode==false.
 * "compare mode" that is created via {@link #newCompareInstance(int)} to show a list of different version of a verse.
 * -- field ari is used, compareMode==true.
 */
public class VersesDialog extends BaseDialog {
	public static final String TAG = VersesDialog.class.getSimpleName();

	private static final String EXTRA_ariRanges = "ariRanges";
	private static final String EXTRA_ari = "ari";
	private static final String EXTRA_compareMode = "compareMode";

	public static abstract class VersesDialogListener {
		public void onVerseSelected(VersesDialog dialog, int ari) {}
		public void onComparedVerseSelected(VersesDialog dialog, int ari, MVersion mversion) {}
	}

	TextView tReference;
	VersesView versesView;

	VersesDialogListener listener;

	int ari;
	boolean compareMode;
	IntArrayList ariRanges;

	// data that will be passed when one verse is clicked
	List<Object> customCallbackData;

	Version sourceVersion = S.activeVersion;
	String sourceVersionId = S.activeVersionId;

	DialogInterface.OnDismissListener onDismissListener;

	public VersesDialog() {
	}
	
	public static VersesDialog newInstance(final IntArrayList ariRanges) {
		VersesDialog res = new VersesDialog();
		
        Bundle args = new Bundle();
        args.putParcelable(EXTRA_ariRanges, ariRanges);
        res.setArguments(args);

		return res;
	}

	@Override
	public void onDismiss(final DialogInterface dialog) {
		super.onDismiss(dialog);

		if (onDismissListener != null) {
			onDismissListener.onDismiss(dialog);
		}
	}

	public static VersesDialog newCompareInstance(final int ari) {
		VersesDialog res = new VersesDialog();

		Bundle args = new Bundle();
		args.putInt(EXTRA_ari, ari);
		args.putBoolean(EXTRA_compareMode, true);
		res.setArguments(args);

		return res;
	}

	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setStyle(DialogFragment.STYLE_NO_TITLE, 0);

		ariRanges = getArguments().getParcelable(EXTRA_ariRanges);
		ari = getArguments().getInt(EXTRA_ari);
		compareMode = getArguments().getBoolean(EXTRA_compareMode);
	}

	@Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View res = inflater.inflate(R.layout.dialog_verses, null);

		tReference = V.get(res, R.id.tReference);
		versesView = V.get(res, R.id.versesView);

		res.setBackgroundColor(S.applied.backgroundColor);
		versesView.setCacheColorHint(S.applied.backgroundColor);
		versesView.setVerseSelectionMode(VerseSelectionMode.singleClick);
		versesView.setSelectedVersesListener(versesView_selectedVerses);

		// build reference label
		final StringBuilder sb = new StringBuilder();

		if (!compareMode) {
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
		} else {
			sb.append(sourceVersion.reference(ari));
		}

		Appearances.applyTextAppearance(tReference);
		tReference.setText(sb);


		if (!compareMode) {
			final IntArrayList displayedAris = new IntArrayList();
			final List<String> displayedVerseTexts = new ArrayList<>();
			final List<String> displayedVerseNumberTexts = new ArrayList<>();
			customCallbackData = new ArrayList<>();

			final int verse_count = sourceVersion.loadVersesByAriRanges(ariRanges, displayedAris, displayedVerseTexts);
			if (verse_count > 0) {
				// set up verse number texts

				for (int i = 0; i < verse_count; i++) {
					final int ari = displayedAris.get(i);
					displayedVerseNumberTexts.add(Ari.toChapter(ari) + ":" + Ari.toVerse(ari));
					customCallbackData.add(ari);
				}

				class Verses extends SingleChapterVerses {
					@Override
					public String getVerse(int verse_0) {
						return displayedVerseTexts.get(verse_0);
					}

					@Override
					public int getVerseCount() {
						return displayedVerseTexts.size();
					}

					@Override
					public String getVerseNumberText(int verse_0) {
						return displayedVerseNumberTexts.get(verse_0);
					}
				}

				final int firstAri = ariRanges.get(0);
				final Book book = sourceVersion.getBook(Ari.toBook(firstAri));
				final int chapter_1 = Ari.toChapter(firstAri);

				versesView.setData(book, chapter_1, new Verses(), null, null, 0);
			}
		} else {
			// read each version and display it. First version must be the sourceVersion.
			final List<String> displayedVersionShortNames = new ArrayList<>();
			final List<String> displayedVerseTexts = new ArrayList<>();
			customCallbackData = new ArrayList<>();

			final List<MVersion> mversions = S.getAvailableVersions();

			// sort such that sourceVersion is first
			Collections.sort(mversions, (lhs, rhs) -> {
				int a = U.equals(lhs.getVersionId(), sourceVersionId)? -1: 0;
				int b = U.equals(rhs.getVersionId(), sourceVersionId)? -1: 0;
				return a - b;
			});

			for (final MVersion mversion : mversions) {
				final Version version = mversion.getVersion();
				if (version == null) continue;

				String shortName = version.getShortName();
				if (shortName == null) {
					shortName = mversion.shortName;
				}
				if (shortName == null) { // still null???
					shortName = version.getLongName(); // this one may not be null.
				}

				String verseText = version.loadVerseText(ari);
				final boolean verseIsAvailable = verseText != null;
				if (verseText == null) {
					verseText = getString(R.string.generic_verse_not_available_in_this_version);
				}

				// these need to be added in parallel
				displayedVersionShortNames.add(shortName);
				displayedVerseTexts.add(verseText);
				customCallbackData.add(verseIsAvailable ? mversion : null);
			}

			class Verses extends SingleChapterVerses {
				@Override
				public String getVerse(int verse_0) {
					return displayedVerseTexts.get(verse_0);
				}

				@Override
				public int getVerseCount() {
					return displayedVerseTexts.size();
				}

				@Override
				public String getVerseNumberText(int verse_0) {
					return displayedVersionShortNames.get(verse_0);
				}
			}

			final Book book = sourceVersion.getBook(Ari.toBook(ari));
			final int chapter_1 = Ari.toChapter(ari);

			versesView.setData(book, chapter_1, new Verses(), null, null, 0);
		}

		return res;
	}

	VersesView.SelectedVersesListener versesView_selectedVerses = new VersesView.SelectedVersesListener() {
		@Override public void onVerseSingleClick(VersesView v, int verse_1 /* this is actually position+1, not necessaryly verse_1 */) {
			if (listener != null) {
				if (!compareMode) {
					listener.onVerseSelected(VersesDialog.this, (Integer) customCallbackData.get(verse_1 - 1));
				} else {
					final MVersion mversion = (MVersion) customCallbackData.get(verse_1 - 1);
					if (mversion != null) {
						// only if the verse is available in this version. See the add call to customCallbackData.
						listener.onComparedVerseSelected(VersesDialog.this, ari, mversion);
					}
				}
			}
		}
		
		@Override public void onSomeVersesSelected(VersesView v) {}
		
		@Override public void onNoVersesSelected(VersesView v) {}
	};

	public void setListener(final VersesDialogListener listener) {
		this.listener = listener;
	}

	public void setOnDismissListener(final DialogInterface.OnDismissListener onDismissListener) {
		this.onDismissListener = onDismissListener;
	}
}
