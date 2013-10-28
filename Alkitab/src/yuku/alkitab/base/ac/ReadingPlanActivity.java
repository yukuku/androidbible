package yuku.alkitab.base.ac;

import android.app.Activity;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import yuku.afw.App;
import yuku.afw.V;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.base.S;
import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.model.ReadingPlan;
import yuku.alkitab.base.model.Version;
import yuku.alkitab.base.util.ReadingPlanManager;
import yuku.alkitab.debug.R;

import java.io.InputStream;

public class ReadingPlanActivity extends Activity {

	private ReadingPlan readingPlan;
	private int dayNumber;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_reading_plan);
		ListView lsTodayReadings = V.get(this, R.id.lsTodayReadings);
		ListView lsDailyPlan = V.get(this, R.id.lsDailyPlan);


		loadDayNumber();
		loadReadingPlan();

		TodayReadingsAdapter todayReadingsAdapter = new TodayReadingsAdapter();
		todayReadingsAdapter.load();
		lsTodayReadings.setAdapter(todayReadingsAdapter);

		DailyPlanAdapter dailyPlanAdapter = new DailyPlanAdapter();
		lsDailyPlan.setAdapter(dailyPlanAdapter);

		setListViewHeightBasedOnChildren(lsTodayReadings);
		setListViewHeightBasedOnChildren(lsDailyPlan);
	}

	private void loadDayNumber() {
		//TODO: proper method. Testing only
		dayNumber = 0;
	}

	private void loadReadingPlan() {
		//TODO: proper method. Testing only
		InputStream is = App.context.getResources().openRawResource(R.raw.wsts);
		ReadingPlan res = ReadingPlanManager.readVersion1(is);

		readingPlan = res;
	}

	class TodayReadingsAdapter extends EasyAdapter {

		private int[] todayReadings;

		public void load() {
			todayReadings = readingPlan.dailyVerses.get(dayNumber);
		}

		@Override
		public View newView(final int position, final ViewGroup parent) {
			return getLayoutInflater().inflate(android.R.layout.simple_list_item_1, parent, false);
		}

		@Override
		public void bindView(final View view, final int position, final ViewGroup parent) {
			TextView textView = V.get(view, android.R.id.text1);
			int start = position * 2;
			Log.d(TAG, "position: " + position);
			Log.d(TAG, "jumlah: " + todayReadings.length);
			int[] ari = {todayReadings[start], todayReadings[start+1]};
			textView.setText(getReference(S.activeVersion, ari));
		}

		@Override
		public int getCount() {
			return todayReadings.length / 2;
		}
	}

	class DailyPlanAdapter extends EasyAdapter {
		@Override
		public View newView(final int position, final ViewGroup parent) {
			return getLayoutInflater().inflate(android.R.layout.simple_list_item_1, parent, false);
		}

		@Override
		public void bindView(final View view, final int position, final ViewGroup parent) {
			TextView textView = V.get(view, android.R.id.text1);
			String text = "";
			int[] aris = readingPlan.dailyVerses.get(position);
			for (int i = 0; i < aris.length/2; i++) {
				int[] ariStartEnd = {aris[i*2], aris[i*2+1]};
				if (i > 0) {
					text += ";";
				}
				text += getReference(S.activeVersion, ariStartEnd);
			}
			textView.setText(text);
		}

		@Override
		public int getCount() {
			return readingPlan.duration;
		}
	}

	public static SpannableStringBuilder getReference(Version version, int[] ari) {
		SpannableStringBuilder sb = new SpannableStringBuilder();
		String reference = version.reference(ari[0]);
		sb.append(reference);
		if (ari.length > 1) {
			String lastVerse = Ari.toChapter(ari[1]) + ":" + Ari.toVerse(ari[1]);
			sb.append("-" + lastVerse);
		}
		return sb;
	}

	public static void setListViewHeightBasedOnChildren(ListView listView) {
		ListAdapter listAdapter = listView.getAdapter();
		if (listAdapter == null) {
			return;
		}

		int totalHeight = 0;
		for (int i = 0; i < listAdapter.getCount(); i++) {
			View listItem = listAdapter.getView(i, null, listView);
			listItem.measure(0, 0);
			totalHeight += listItem.getMeasuredHeight();
		}

		ViewGroup.LayoutParams params = listView.getLayoutParams();
		params.height = totalHeight + (listView.getDividerHeight() * (listAdapter.getCount() - 1));
		listView.setLayoutParams(params);
	}
}