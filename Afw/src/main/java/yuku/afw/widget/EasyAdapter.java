package yuku.afw.widget;

import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

public abstract class EasyAdapter extends BaseAdapter {
	@Override
	public Object getItem(int position) {
		return null;
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		if (convertView == null) {
			convertView = newView(position, parent);
		}
		bindView(convertView, position, parent);
		return convertView;
	}

	@Override
	public View getDropDownView(int position, View convertView, ViewGroup parent) {
		if (convertView == null) {
			convertView = newDropDownView(position, parent);
		}
		bindDropDownView(convertView, position, parent);
		return convertView;
	}

	public View newDropDownView(int position, ViewGroup parent) {
		return newView(position, parent);
	}

	public void bindDropDownView(View view, int position, ViewGroup parent) {
		bindView(view, position, parent);
	}

	public abstract View newView(int position, ViewGroup parent);

	public abstract void bindView(View view, int position, ViewGroup parent);
}
