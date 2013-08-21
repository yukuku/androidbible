package yuku.alkitab.base.dialog;

import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import yuku.afw.V;
import yuku.alkitab.R;
import yuku.alkitab.base.model.ProgressMark;

import java.util.List;

public class ProgressMarkChooserDialog extends DialogFragment{
	public static final String TAG = ProgressMarkChooserDialog.class.getSimpleName();
	LayoutInflater inflater;
	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.dialog_progress_mark, container, false);
		ListView lsProgressMark = V.get(view, R.id.lsProgressMark);


		return view;
	}

	class ProgressMarkAdapter extends BaseAdapter {

		List<ProgressMark> progressMarks;
		@Override
		public int getCount() {
			return 0;
		}

		@Override
		public Object getItem(final int position) {
			return null;
		}

		@Override
		public long getItemId(final int position) {
			return 0;
		}

		@Override
		public View getView(final int position, final View convertView, final ViewGroup parent) {
			View rowView;
			if (convertView == null) {
				rowView = inflater.inflate(android.R.layout.simple_list_item_1, parent, false);
			} else {
				rowView = convertView;
			}

			TextView textView = (TextView) rowView.findViewById(android.R.id.text1);
			textView.setText();

			return rowView
		}
	}

}
