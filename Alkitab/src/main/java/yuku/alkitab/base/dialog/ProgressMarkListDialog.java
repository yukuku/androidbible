package yuku.alkitab.base.dialog;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import yuku.afw.V;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.util.Appearances;
import yuku.alkitab.base.util.Sqlitil;
import yuku.alkitab.base.widget.AttributeView;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.ProgressMark;

import java.util.ArrayList;
import java.util.List;

public class ProgressMarkListDialog extends DialogFragment {
	public static final String TAG = ProgressMarkListDialog.class.getSimpleName();

	LayoutInflater inflater;

	public interface Listener {
		void onProgressMarkSelected(int preset_id);

		void onProgressMarkDeleted();
	}

	Listener progressMarkListener;

	@Override
	public void onAttach(final Activity activity) {
		super.onAttach(activity);
		progressMarkListener = (Listener) activity;
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		this.inflater = inflater;

		getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		final View view = inflater.inflate(R.layout.dialog_progress_mark, container, false);
		final ListView lsProgressMark = V.get(view, R.id.lsProgressMark);
		final ProgressMarkAdapter adapter = new ProgressMarkAdapter();
		lsProgressMark.setAdapter(adapter);
		lsProgressMark.setBackgroundColor(S.applied.backgroundColor);
		lsProgressMark.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
				final ProgressMark progressMark = adapter.progressMarks.get(position);
				progressMarkListener.onProgressMarkSelected(progressMark.preset_id);
				getDialog().dismiss();
			}
		});
		lsProgressMark.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(final AdapterView<?> parent, final View view, final int position, final long id) {
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
			}
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
			TextView tCaption = V.get(view, R.id.lCaption);
			TextView tDate = V.get(view, R.id.lDate);
			TextView tVerseText = V.get(view, R.id.lSnippet);
			ImageView imgIcon = V.get(view, R.id.imgIcon);

			final ProgressMark progressMark = progressMarks.get(position);

			imgIcon.setImageResource(AttributeView.getProgressMarkIconResource(progressMark.preset_id));

			if (progressMark.ari == 0 || TextUtils.isEmpty(progressMark.caption)) {
				tCaption.setText(AttributeView.getDefaultProgressMarkStringResource(progressMark.preset_id));
			} else {
				tCaption.setText(progressMark.caption);
			}
			Appearances.applyMarkerTitleTextAppearance(tCaption);

			int ari = progressMark.ari;
			String verseText = "";
			String date = "";
			if (ari != 0) {
				date = Sqlitil.toLocaleDateMedium(progressMark.modifyTime);
				tDate.setText(date);

				String reference = S.activeVersion.reference(ari);
				verseText = U.removeSpecialCodes(S.activeVersion.loadVerseText(ari));
				Appearances.applyMarkerSnippetContentAndAppearance(tVerseText, reference, verseText);
				view.setEnabled(false);
			} else {
				tVerseText.setText(verseText);
				view.setEnabled(true);
			}
			tDate.setText(date);
			Appearances.applyMarkerDateTextAppearance(tDate);
		}
	}

}
