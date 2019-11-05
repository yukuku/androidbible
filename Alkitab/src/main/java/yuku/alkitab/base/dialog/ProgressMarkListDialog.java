package yuku.alkitab.base.dialog;

import android.content.Context;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.util.Appearances;
import yuku.alkitab.base.util.Sqlitil;
import yuku.alkitab.base.widget.OldAttributeView;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.ProgressMark;
import yuku.alkitab.model.Version;

import java.util.ArrayList;
import java.util.List;

public class ProgressMarkListDialog extends DialogFragment {
	public static final String TAG = ProgressMarkListDialog.class.getSimpleName();

	LayoutInflater inflater;

	public interface Listener {
		void onProgressMarkSelected(int preset_id);
	}

	Listener progressMarkListener;

	Version version = S.activeVersion();
	String versionId = S.activeVersionId();
	float textSizeMult = S.getDb().getPerVersionSettings(versionId).fontSizeMultiplier;

	@Override
	public void onAttach(final Context context) {
		super.onAttach(context);
		progressMarkListener = (Listener) context;
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		this.inflater = inflater;

		final Window window = getDialog().getWindow();
		if (window != null) {
			window.requestFeature(Window.FEATURE_NO_TITLE);
		}
		final View view = inflater.inflate(R.layout.dialog_progress_mark, container, false);
		final ListView lsProgressMark = view.findViewById(R.id.lsProgressMark);
		final ProgressMarkAdapter adapter = new ProgressMarkAdapter();
		lsProgressMark.setAdapter(adapter);
		lsProgressMark.setBackgroundColor(S.applied().backgroundColor);
		lsProgressMark.setOnItemClickListener((parent, view1, position, id) -> {
			final ProgressMark progressMark = adapter.progressMarks.get(position);
			progressMarkListener.onProgressMarkSelected(progressMark.preset_id);
			getDialog().dismiss();
		});
		lsProgressMark.setOnItemLongClickListener((parent, view1, position, id) -> {
			final ProgressMark progressMark = adapter.progressMarks.get(position);
			ProgressMarkRenameDialog.show(getActivity(), progressMark, new ProgressMarkRenameDialog.Listener() {
				@Override
				public void onOked() {
					adapter.reload();
				}

				@Override
				public void onDeleted() {
					adapter.reload();

					if (adapter.progressMarks.size() == 0) {
						// no more to show, dismiss this dialog.
						getDialog().dismiss();
					}
				}
			});

			return true;
		});

		return view;
	}

	class ProgressMarkAdapter extends EasyAdapter {

		final List<ProgressMark> progressMarks = new ArrayList<>();

		ProgressMarkAdapter() {
			reload();
		}

		public void reload() {
			progressMarks.clear();
			progressMarks.addAll(S.getDb().listAllProgressMarks());

			notifyDataSetChanged();
		}

		@Override
		public int getCount() {
			return progressMarks.size();
		}

		@Override
		public View newView(final int position, final ViewGroup parent) {
			return inflater.inflate(R.layout.item_progress_mark, parent, false);
		}

		@Override
		public void bindView(final View view, final int position, final ViewGroup parent) {
			TextView tCaption = view.findViewById(R.id.lCaption);
			TextView tDate = view.findViewById(R.id.lDate);
			TextView tVerseText = view.findViewById(R.id.lSnippet);
			ImageView imgIcon = view.findViewById(R.id.imgIcon);

			final ProgressMark progressMark = progressMarks.get(position);

			imgIcon.setImageResource(OldAttributeView.getProgressMarkIconResource(progressMark.preset_id));

			if (progressMark.ari == 0 || TextUtils.isEmpty(progressMark.caption)) {
				tCaption.setText(OldAttributeView.getDefaultProgressMarkStringResource(progressMark.preset_id));
			} else {
				tCaption.setText(progressMark.caption);
			}
			Appearances.applyMarkerTitleTextAppearance(tCaption, textSizeMult);

			int ari = progressMark.ari;
			String verseText = "";
			String date = "";
			if (ari != 0) {
				date = Sqlitil.toLocaleDateMedium(progressMark.modifyTime);
				tDate.setText(date);

				final String reference = version.reference(ari);
				verseText = U.removeSpecialCodes(version.loadVerseText(ari));
				Appearances.applyMarkerSnippetContentAndAppearance(tVerseText, reference, verseText, textSizeMult);
				view.setEnabled(false);
			} else {
				tVerseText.setText(verseText);
				view.setEnabled(true);
			}
			tDate.setText(date);
			Appearances.applyMarkerDateTextAppearance(tDate, textSizeMult);
		}
	}

}
