package yuku.alkitab.base.dialog;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.fragment.app.DialogFragment;
import com.afollestad.materialdialogs.MaterialDialog;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import yuku.alkitab.base.S;
import yuku.alkitab.base.dialog.base.BaseDialog;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.base.util.Appearances;
import yuku.alkitab.base.util.TargetDecoder;
import yuku.alkitab.base.widget.OldVersesView;
import yuku.alkitab.base.widget.VerseRenderer;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Version;
import yuku.alkitab.model.XrefEntry;
import yuku.alkitab.util.Ari;
import yuku.alkitab.util.IntArrayList;

public class OldXrefDialog extends BaseDialog {
	static final String TAG = XrefDialog.class.getSimpleName();

	private static final String EXTRA_arif = "arif";

	public interface XrefDialogListener {
		void onVerseSelected(OldXrefDialog dialog, int arif_source, int ari_target);
	}

	TextView tXrefText;
	OldVersesView versesView;

	XrefDialogListener listener;

	int arif_source;
	XrefEntry xrefEntry;
	int displayedLinkPos = -1; // -1 indicates that we should auto-select the first link
	List<String> displayedVerseTexts;
	List<String> displayedVerseNumberTexts;
	IntArrayList displayedRealAris;
	Version sourceVersion = S.activeVersion();
	String sourceVersionId = S.activeVersionId();
	float textSizeMult = S.getDb().getPerVersionSettings(sourceVersionId).fontSizeMultiplier;

	public OldXrefDialog() {
	}

	public static OldXrefDialog newInstance(int arif) {
		OldXrefDialog res = new OldXrefDialog();

		Bundle args = new Bundle();
		args.putInt(EXTRA_arif, arif);
		res.setArguments(args);

		return res;
	}

	@Override
	public void onAttach(final Context context) {
		super.onAttach(context);

		if (getParentFragment() instanceof XrefDialogListener) {
			listener = (XrefDialogListener) getParentFragment();
		} else {
			listener = (XrefDialogListener) context;
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setStyle(DialogFragment.STYLE_NO_TITLE, 0);

		arif_source = getArguments().getInt(EXTRA_arif);
		xrefEntry = sourceVersion.getXrefEntry(arif_source);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View res = inflater.inflate(R.layout.dialog_old_xref, container, false);

		tXrefText = res.findViewById(R.id.tXrefText);
		versesView = res.findViewById(R.id.versesView);

		res.setBackgroundColor(S.applied().backgroundColor);
		versesView.setCacheColorHint(S.applied().backgroundColor);
		versesView.setVerseSelectionMode(OldVersesView.VerseSelectionMode.singleClick);
		versesView.setSelectedVersesListener(versesView_selectedVerses);
		tXrefText.setMovementMethod(LinkMovementMethod.getInstance());

		if (xrefEntry != null) {
			renderXrefText();
		} else {
			new MaterialDialog.Builder(getActivity())
				.content(String.format(Locale.US, "Error: xref at arif 0x%08x couldn't be loaded", arif_source))
				.positiveText(R.string.ok)
				.show();
		}

		return res;
	}

	@Override
	public void onActivityCreated(final Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		if (xrefEntry == null) {
			dismiss();
		}
	}

	void renderXrefText() {
		final SpannableStringBuilder sb = new SpannableStringBuilder();
		sb.append(VerseRenderer.XREF_MARK);
		sb.append(" ");

		final int[] linkPos = {0};
		findTags(xrefEntry.content, new FindTagsListener() {
			@Override
			public void onTaggedText(final String tag, int start, int end) {
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
							@Override
							public void onClick(View widget) {
								showVerses(thisLinkPos, encodedTarget);
							}
						}, sb_len, sb.length(), 0);
					}
				}
			}

			@Override
			public void onPlainText(int start, int end) {
				sb.append(xrefEntry.content, start, end);
			}
		});

		Appearances.applyTextAppearance(tXrefText, textSizeMult);

		tXrefText.setText(sb);
	}

	void showVerses(int linkPos, String encodedTarget) {
		displayedLinkPos = linkPos;

		final IntArrayList ranges = decodeTarget(encodedTarget);

		if (BuildConfig.DEBUG) {
			AppLog.d(TAG, "linkPos " + linkPos + " target=" + encodedTarget + " ranges=" + ranges);
		}

		displayedVerseTexts = new ArrayList<>();
		displayedVerseNumberTexts = new ArrayList<>();
		displayedRealAris = new IntArrayList();

		int verse_count = sourceVersion.loadVersesByAriRanges(ranges, displayedRealAris, displayedVerseTexts);
		if (verse_count > 0) {
			// set up verse number texts
			for (int i = 0; i < verse_count; i++) {
				int ari = displayedRealAris.get(i);
				displayedVerseNumberTexts.add(Ari.toChapter(ari) + ":" + Ari.toVerse(ari));
			}

			int firstAri = displayedRealAris.get(0);
			final String notAvailableText = getString(R.string.generic_verse_not_available_in_this_version);

			final XrefDialogVerses verses = new XrefDialogVerses(notAvailableText, displayedVerseTexts, displayedVerseNumberTexts);
			versesView.setData(Ari.toBookChapter(firstAri), verses, null, null, 0, sourceVersion, sourceVersionId);
		}

		renderXrefText();
	}

	private IntArrayList decodeTarget(final String encodedTarget) {
		return TargetDecoder.decode(encodedTarget);
	}

	OldVersesView.SelectedVersesListener versesView_selectedVerses = new OldVersesView.DefaultSelectedVersesListener() {
		@Override
		public void onVerseSingleClick(OldVersesView v, int verse_1) {
			listener.onVerseSelected(OldXrefDialog.this, arif_source, displayedRealAris.get(verse_1 - 1));
		}
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

			int q = s.indexOf("@>", p + 2);
			if (q == -1) break;
			int r = s.indexOf("@/", q + 2);
			if (r == -1) break;

			listener.onTaggedText(s.substring(p + 2, q), q + 2, r);

			pos = r + 2;
		}

		listener.onPlainText(pos, s.length());
	}

	public void setSourceVersion(Version sourceVersion, String sourceVersionId) {
		this.sourceVersion = sourceVersion;
		this.sourceVersionId = sourceVersionId;
		textSizeMult = S.getDb().getPerVersionSettings(sourceVersionId).fontSizeMultiplier;
	}
}
