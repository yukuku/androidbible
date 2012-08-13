package yuku.alkitab.base.fr;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.TextView;

import yuku.afw.V;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.R;
import yuku.alkitab.base.S;
import yuku.alkitab.base.fr.base.BaseFragment;
import yuku.alkitab.base.model.Kitab;

public class GotoGridFragment extends BaseFragment {
	public static final String TAG = GotoGridFragment.class.getSimpleName();
	
	GridView grid;

	Kitab[] xkitab;
	KitabAdapter kitabAdapter;
	
	@Override public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		xkitab = S.edisiAktif.getConsecutiveXkitab();
	}
	
	@Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View res = inflater.inflate(R.layout.fragment_goto_grid, container, false);
		grid = V.get(res, R.id.grid);
		grid.setAdapter(kitabAdapter = new KitabAdapter());
		return res;
	}
	
	@Override public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
	}
	
	class KitabAdapter extends EasyAdapter {
		float density = getResources().getDisplayMetrics().density;
		
		@Override public int getCount() {
			return xkitab.length;
		}

		@Override public Kitab getItem(int position) {
			return xkitab[position];
		}
		
		@Override public View newView(int position, ViewGroup parent) {
			TextView res = new TextView(getActivity());
			res.setLayoutParams(new GridView.LayoutParams((int)(64.f * density), (int)(40 * density)));
			res.setGravity(Gravity.CENTER);
			res.setTextAppearance(getActivity(), android.R.attr.textAppearanceMedium);
			return res;
		}

		@Override public void bindView(View view, int position, ViewGroup parent) {
			TextView lName = (TextView) view;
			
			Kitab kitab = getItem(position);
			CharSequence judul = kitab.judul;
			if (kitab.judul.indexOf(' ') != -1) {
				String nospace = kitab.judul.replace(" ", "");
				judul = nospace.length() <= 3? nospace: nospace.substring(0, 3);
			} else {
				if (judul.length() > 3) judul = judul.subSequence(0, 3);
			}
			
			lName.setText(judul);
		}
	}
}
