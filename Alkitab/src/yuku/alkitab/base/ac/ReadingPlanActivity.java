package yuku.alkitab.base.ac;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import yuku.afw.App;
import yuku.afw.V;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.base.IsiActivity;
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
	private TodayReadingsAdapter todayReadingsAdapter;
	private ImageButton bLeft;
	private ImageButton bRight;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_reading_plan);
		ListView lsTodayReadings = V.get(this, R.id.lsTodayReadings);
		ListView lsDailyPlan = V.get(this, R.id.lsDailyPlan);

		loadDayNumber();
		loadReadingPlan();

		//List view
		todayReadingsAdapter = new TodayReadingsAdapter();
		todayReadingsAdapter.load();
		lsTodayReadings.setAdapter(todayReadingsAdapter);

		final DailyPlanAdapter dailyPlanAdapter = new DailyPlanAdapter();
		lsDailyPlan.setAdapter(dailyPlanAdapter);

		setListViewHeightBasedOnChildren(lsTodayReadings);
		setListViewHeightBasedOnChildren(lsDailyPlan);

		lsTodayReadings.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
				int ari = todayReadingsAdapter.todayReadings[position * 2];

				Intent intent = IsiActivity.createIntent(ari);
				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivity(intent);
			}
		});

		lsDailyPlan.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
				int ari = readingPlan.dailyVerses.get(position)[0];

				Intent intent = IsiActivity.createIntent(ari);
				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivity(intent);
			}
		});

		//button
		bLeft = V.get(this, R.id.bLeft);
		bRight = V.get(this, R.id.bRight);

		updateButtonStatus();

		bLeft.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				changeDay(-1);
			}
		});

		bRight.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				changeDay(+1);
			}
		});

	}

	private void changeDay(int day) {
		dayNumber += day;
		todayReadingsAdapter.load();
		todayReadingsAdapter.notifyDataSetChanged();

		updateButtonStatus();
	}

	private void updateButtonStatus() {            //TODO look disabled
		if (dayNumber == 0) {
			bLeft.setEnabled(false);
			bRight.setEnabled(true);
		} else if (dayNumber == readingPlan.duration - 1) {
			bLeft.setEnabled(true);
			bRight.setEnabled(false);
		} else {
			bLeft.setEnabled(true);
			bRight.setEnabled(true);
		}
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
					text += "; ";
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
		String book = version.getBook(Ari.toBook(ari[0])).shortName;
		sb.append(book);
		int startChapter = Ari.toChapter(ari[0]);
		int startVerse = Ari.toVerse(ari[0]);
		int lastVerse = Ari.toVerse(ari[1]);
		int lastChapter = Ari.toChapter(ari[1]);

		sb.append(" " + startChapter);

		if (startVerse == 0) {
			if (lastVerse == 0) {
				if (startChapter != lastChapter) {
					sb.append("-" + lastChapter);
				}
			} else {
				sb.append("-" + lastChapter + ":" + lastVerse);
			}
		} else {
			if (startChapter == lastChapter) {
				sb.append(":" + startVerse + "-" + lastVerse);
			} else {
				sb.append(":" + startVerse + "-" + lastChapter + ":" + lastVerse);
			}
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