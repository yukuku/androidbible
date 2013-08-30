package yuku.alkitab.base.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
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
import yuku.alkitab.R;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.model.ProgressMark;
import yuku.alkitab.base.util.Appearances;
import yuku.alkitab.base.util.Sqlitil;

import java.util.Date;
import java.util.List;

public class ProgressMarkDialog extends DialogFragment{
	public static final String TAG = ProgressMarkDialog.class.getSimpleName();

	LayoutInflater inflater;

	public interface ProgressMarkDialogListener {
		void onProgressMarkSelected(int ari);

		void onProgressMarkDeleted(int ari);
	}

	ProgressMarkDialogListener progressMarkListener;

	@Override
	public void onAttach(final Activity activity) {
		super.onAttach(activity);
		progressMarkListener = (ProgressMarkDialogListener) activity;
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {

		this.inflater = inflater;

		getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		View view = inflater.inflate(R.layout.dialog_progress_mark, container, false);
		ListView lsProgressMark = V.get(view, R.id.lsProgressMark);
		final ProgressMarkAdapter adapter = new ProgressMarkAdapter();
		adapter.load();
		lsProgressMark.setBackgroundColor(S.applied.backgroundColor);
		lsProgressMark.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
				int ari = adapter.progressMarks.get(position).ari;
				if (ari != 0) {
					progressMarkListener.onProgressMarkSelected(ari);
					getDialog().dismiss();
				} else {
					AlertDialog.Builder dialog = new AlertDialog.Builder(getActivity());
					dialog.setMessage(getString(R.string.pm_activate_tutorial))
					.setPositiveButton(getString(R.string.ok), null)
					.show();
				}
			}
		});
		lsProgressMark.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(final AdapterView<?> parent, final View view, final int position, final long id) {
				final ProgressMark progressMark = adapter.progressMarks.get(position);
				final int ari = progressMark.ari;
				if (ari != 0) {
					String[] menuContext = {getString(R.string.pm_edit_name), getString(R.string.pm_delete_progress)};
					AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
					builder.setItems(menuContext, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(final DialogInterface dialog, final int which) {
							if (which == 0) {
								final View v = inflater.inflate(R.layout.item_progress_mark_edit, container, false);
								final TextView tCaption = V.get(v, R.id.tCaption);
								final String originalCaption;
								final String caption = progressMark.caption;
								if (TextUtils.isEmpty(caption)) {
									originalCaption = getString(ProgressMark.getDefaultProgressMarkResource(position));
								} else {
									originalCaption = caption;
								}
								tCaption.setText(originalCaption);
								AlertDialog.Builder editDialog = new AlertDialog.Builder(getActivity());
								editDialog.setView(v)
								.setNegativeButton(getString(R.string.cancel), null)
								.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
									@Override
									public void onClick(final DialogInterface dialog, final int which) {
										final String name = String.valueOf(tCaption.getText());
										if (originalCaption != null && !TextUtils.isEmpty(name) && !originalCaption.equals(name)) {
											progressMark.caption = name;
											progressMark.modifyTime = new Date();
											S.getDb().updateProgressMark(progressMark);
											adapter.notifyDataSetChanged();
										}
									}
								})
								.show();
							} else {
								progressMark.ari = 0;
								progressMark.caption = "";
								S.getDb().updateProgressMark(progressMark);
								adapter.notifyDataSetChanged();
								progressMarkListener.onProgressMarkDeleted(ari);
							}
						}
					}).show();
				}
				return true;
			}
		});
		lsProgressMark.setAdapter(adapter);

		return view;
	}

	class ProgressMarkAdapter extends EasyAdapter {

		List<ProgressMark> progressMarks;
		public void load() {
			progressMarks = S.getDb().listAllProgressMarks();
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

			imgIcon.setImageResource(ProgressMark.getProgressMarkIconResource(position));

			final ProgressMark progressMark = progressMarks.get(position);

			if (progressMark.ari == 0 || TextUtils.isEmpty(progressMark.caption)) {
				tCaption.setText(ProgressMark.getDefaultProgressMarkResource(position));
			} else {
				tCaption.setText(progressMark.caption);
			}
			Appearances.applyBookmarkTitleTextAppearance(tCaption);

			int ari = progressMark.ari;
			String verseText = "";
			String date = "";
			if (ari != 0) {
				date = Sqlitil.toLocaleDateMedium(progressMark.modifyTime);
				tDate.setText(date);

				String reference = S.activeVersion.reference(ari);
				verseText = U.removeSpecialCodes(S.activeVersion.loadVerseText(ari));
				Appearances.applyBookmarkSnippetContentAndAppearance(tVerseText, reference, verseText);
				view.setEnabled(false);
			} else {
				tVerseText.setText(verseText);
				view.setEnabled(true);
			}
			tDate.setText(date);
			Appearances.applyBookmarkDateTextAppearance(tDate);
		}

	}

}
