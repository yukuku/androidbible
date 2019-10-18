package yuku.alkitab.base.dialog;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.fragment.app.DialogFragment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.dialog.base.BaseDialog;
import yuku.alkitab.base.model.MVersion;
import yuku.alkitab.base.util.Appearances;
import yuku.alkitab.base.widget.OldVersesView;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.SingleChapterVerses;
import yuku.alkitab.model.Version;
import yuku.alkitab.util.Ari;
import yuku.alkitab.util.IntArrayList;

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
		public void onVerseSelected(VersesDialog dialog, int ari) {
		}

		public void onComparedVerseSelected(VersesDialog dialog, int ari, MVersion mversion) {
		}
	}

	TextView tReference;
	OldVersesView versesView;

	VersesDialogListener listener;

	int ari;
	boolean compareMode;
	IntArrayList ariRanges;

	// data that will be passed when one verse is clicked
	Object[] customCallbackData;

	Version sourceVersion = S.activeVersion();
	String sourceVersionId = S.activeVersionId();
	float textSizeMult = S.getDb().getPerVersionSettings(sourceVersionId).fontSizeMultiplier;

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

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setStyle(DialogFragment.STYLE_NO_TITLE, 0);

		ariRanges = getArguments().getParcelable(EXTRA_ariRanges);
		ari = getArguments().getInt(EXTRA_ari);
		compareMode = getArguments().getBoolean(EXTRA_compareMode);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View res = inflater.inflate(R.layout.dialog_verses, container, false);

		tReference = res.findViewById(R.id.tReference);
		versesView = res.findViewById(R.id.versesView);

		res.setBackgroundColor(S.applied().backgroundColor);
		versesView.setCacheColorHint(S.applied().backgroundColor);
		versesView.setVerseSelectionMode(OldVersesView.VerseSelectionMode.singleClick);
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

				sb.append(sourceVersion.referenceRange(ari_start, ari_end));
			}
		} else {
			sb.append(sourceVersion.reference(ari));
		}

		Appearances.applyTextAppearance(tReference, textSizeMult);
		tReference.setText(sb);


		if (!compareMode) {
			final IntArrayList displayedAris = new IntArrayList();
			final List<String> displayedVerseTexts = new ArrayList<>();
			final List<String> displayedVerseNumberTexts = new ArrayList<>();

			final int verse_count = sourceVersion.loadVersesByAriRanges(ariRanges, displayedAris, displayedVerseTexts);
			customCallbackData = new Object[verse_count];
			if (verse_count > 0) {
				// set up verse number texts

				for (int i = 0; i < verse_count; i++) {
					final int ari = displayedAris.get(i);
					displayedVerseNumberTexts.add(Ari.toChapter(ari) + ":" + Ari.toVerse(ari));
					customCallbackData[i] = ari;
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

				versesView.setData(Ari.toBookChapter(firstAri), new Verses(), null, null, 0, sourceVersion, sourceVersionId);
			}
		} else {
			// read each version and display it. First version must be the sourceVersion.
			final List<MVersion> mversions = S.getAvailableVersions();

			// sort such that sourceVersion is first
			Collections.sort(mversions, (lhs, rhs) -> {
				int a = U.equals(lhs.getVersionId(), sourceVersionId) ? -1 : 0;
				int b = U.equals(rhs.getVersionId(), sourceVersionId) ? -1 : 0;
				return a - b;
			});

			final Version[] displayedVersion = new Version[mversions.size()];
			customCallbackData = new Object[mversions.size()];
			for (int i = 0; i < customCallbackData.length; i++) {
				customCallbackData[i] = mversions.get(i);
			}

			class Verses extends SingleChapterVerses implements SingleChapterVerses.WithTextSizeMult {
				@Override
				public String getVerse(int verse_0) {
					// load version or take from existing if already loaded
					final MVersion mversion = mversions.get(verse_0);
					final Version loaded = displayedVersion[verse_0];

					final Version version;
					if (loaded == null) {
						version = mversion.getVersion();
						displayedVersion[verse_0] = version;
					} else {
						version = loaded;
					}

					if (version == null) {
						return getString(R.string.version_error_opening, mversion.getVersionId());
					}

					final String res = version.loadVerseText(ari);
					if (res == null) {
						return getString(R.string.generic_verse_not_available_in_this_version);
					}

					return res;
				}

				@Override
				public int getVerseCount() {
					return mversions.size();
				}

				@Override
				public String getVerseNumberText(int verse_0) {
					// load version or take from existing if already loaded
					final MVersion mversion = mversions.get(verse_0);
					final Version loaded = displayedVersion[verse_0];

					final Version version;
					if (loaded == null) {
						version = mversion.getVersion();
						displayedVersion[verse_0] = version;
					} else {
						version = loaded;
					}

					if (version == null) {
						return "ERROR"; // could not load version
					}

					String res = version.getShortName();
					if (res == null) {
						res = mversion.shortName;
					}
					if (res == null) { // still null???
						res = version.getLongName(); // this one may not be null.
					}

					return res;
				}

				@Override
				public float getTextSizeMult(final int verse_0) {
					final MVersion mversion = mversions.get(verse_0);
					return S.getDb().getPerVersionSettings(mversion.getVersionId()).fontSizeMultiplier;
				}
			}

			versesView.setData(Ari.toBookChapter(ari), new Verses(), null, null, 0, null, null);
		}

		return res;
	}

	OldVersesView.SelectedVersesListener versesView_selectedVerses = new OldVersesView.DefaultSelectedVersesListener() {
		@Override
		public void onVerseSingleClick(OldVersesView v, int verse_1 /* this is actually position+1, not necessaryly verse_1 */) {
			if (listener != null) {
				if (!compareMode) {
					listener.onVerseSelected(VersesDialog.this, (Integer) customCallbackData[verse_1 - 1]);
				} else {
					// only if the verse is available in this version.
					final MVersion mversion = (MVersion) customCallbackData[verse_1 - 1];
					final Version version = mversion.getVersion();
					if (version != null && version.loadVerseText(ari) != null) {
						listener.onComparedVerseSelected(VersesDialog.this, ari, mversion);
					}
				}
			}
		}
	};

	public void setListener(final VersesDialogListener listener) {
		this.listener = listener;
	}

	public void setOnDismissListener(final DialogInterface.OnDismissListener onDismissListener) {
		this.onDismissListener = onDismissListener;
	}
}
