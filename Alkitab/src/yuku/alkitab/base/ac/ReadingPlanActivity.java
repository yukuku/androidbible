package yuku.alkitab.base.ac;

import android.app.Activity;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import yuku.afw.V;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.base.S;
import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.model.ReadingPlan;
import yuku.alkitab.base.model.Version;
import yuku.alkitab.base.util.IntArrayList;
import yuku.alkitab.debug.R;

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
		ReadingPlan res = new ReadingPlan();
		res.totalDays = 2;

		IntArrayList aris = new IntArrayList();
		aris.set(0, 257);
		aris.set(1, 277);
		aris.set(2, 2886657);
		aris.set(3, 2886659);
		aris.set(4, 3738625);
		aris.set(5, 3738626);
		res.dailyVerse.add(new Pair<Integer, IntArrayList>(3, aris));

		IntArrayList aris2 = new IntArrayList();
		aris2.set(0, 3738569);
		aris2.set(1, 3738572);
		aris2.set(2, 3539204);
		aris2.set(3, 3539207);
		res.dailyVerse.add(new Pair<Integer, IntArrayList>(2, aris2));
		readingPlan = res;
	}

	class TodayReadingsAdapter extends EasyAdapter {

		private Pair<Integer,IntArrayList> todayReadings;

		public void load() {
			todayReadings = readingPlan.dailyVerse.get(dayNumber);
		}

		@Override
		public View newView(final int position, final ViewGroup parent) {
			return getLayoutInflater().inflate(android.R.layout.simple_list_item_1, parent, false);
		}

		@Override
		public void bindView(final View view, final int position, final ViewGroup parent) {
			TextView textView = V.get(view, android.R.id.text1);
			int start = position * 2;
			int[] ari = {todayReadings.second.get(start), todayReadings.second.get(start + 1)};
			textView.setText(getReference(S.activeVersion, ari));
		}

		@Override
		public int getCount() {
			return todayReadings.first;
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
			Pair<Integer, IntArrayList> pair = readingPlan.dailyVerse.get(position);
			for (int i = 0; i < pair.first; i++) {
				int[] ari = {pair.second.get(i), pair.second.get(i * 2)};
				if (i > 0) {
					text += ";";
				}
				text += getReference(S.activeVersion, ari);
			}
			textView.setText(text);

		}

		@Override
		public int getCount() {
			return readingPlan.totalDays;
		}
	}

	public static SpannableStringBuilder getReference(Version version, int[] ari) {
		SpannableStringBuilder sb = new SpannableStringBuilder();
		String reference = version.reference(ari[0]);
		sb.append(reference);
		if (ari.length > 1) {
			int lastVerse = Ari.toVerse(ari[0]) + ari.length - 1;
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