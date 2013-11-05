package yuku.alkitab.base.ac;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import yuku.afw.V;
import yuku.alkitab.base.S;
import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.model.ReadingPlan;
import yuku.alkitab.base.model.Version;
import yuku.alkitab.base.util.IntArrayList;
import yuku.alkitab.base.util.ReadingPlanManager;
import yuku.alkitab.debug.R;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class ReadingPlanActivity extends Activity {
	public static final String READING_PLAN_ARI_RANGES = "reading_plan_ari_ranges";
	public static final String READING_PLAN_ID = "reading_plan_id";
	public static final String READING_PLAN_DAY_NUMBER = "reading_plan_day_number";

	private ReadingPlan readingPlan;
	private int dayNumber;
	private ReadingPlanAdapter readingPlanAdapter;
	private ImageButton bLeft;
	private ImageButton bRight;
	private ListView lsTodayReadings;
	private IntArrayList readingCodes;
	private Button bToday;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_reading_plan);
		lsTodayReadings = V.get(this, R.id.lsTodayReadings);
		bToday = V.get(this, R.id.bToday);


		loadReadingPlan();
		loadReadingPlanProgress();
		loadDayNumber();

		if (readingPlan == null) {
			return;
		}

		prepareDisplay();

	}

	public void goToIsiActivity(final int dayNumber, final int sequence) {
		final int[] selectedVerses = readingPlan.dailyVerses.get(dayNumber);
		int ari = selectedVerses[sequence * 2];

		Intent intent = new Intent();
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		intent.putExtra("ari", ari);
		intent.putExtra(READING_PLAN_ID, readingPlan.info.id);
		intent.putExtra(READING_PLAN_DAY_NUMBER, dayNumber);
		intent.putExtra(READING_PLAN_ARI_RANGES, selectedVerses);
		setResult(RESULT_OK, intent);
		finish();
	}

	public void prepareDisplay() {

		//Listviews
		readingPlanAdapter = new ReadingPlanAdapter();
		readingPlanAdapter.load();
		lsTodayReadings.setAdapter(readingPlanAdapter);

		lsTodayReadings.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
				final int todayReadingsSize = readingPlan.dailyVerses.get(dayNumber).length / 2;
				if (position < todayReadingsSize) {
					goToIsiActivity(dayNumber, position);
				} else if (position > todayReadingsSize) {
					goToIsiActivity(position - todayReadingsSize - 1, 0);
				}
			}

		});

		//buttons
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

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		getMenuInflater().inflate(R.menu.activity_reading_plan, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		if (item.getItemId() == R.id.menuDownload) {
			downloadReadingPlan();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void changeDay(int day) {
		dayNumber += day;
		readingPlanAdapter.load();
		readingPlanAdapter.notifyDataSetChanged();

		updateButtonStatus();
	}

	private void updateButtonStatus() {            //TODO look disabled
		if (dayNumber == 0) {
			bLeft.setEnabled(false);
			bRight.setEnabled(true);
		} else if (dayNumber == readingPlan.info.duration - 1) {
			bLeft.setEnabled(true);
			bRight.setEnabled(false);
		} else {
			bLeft.setEnabled(true);
			bRight.setEnabled(true);
		}

		bToday.setText(getReadingDateHeader(dayNumber));

	}

	private void loadDayNumber() {
		//TODO: proper method. Testing only
		if (readingPlan == null) {
			return;
		}
		dayNumber = (int) ((new Date().getTime() - readingPlan.info.startDate) / (1000 * 60 * 60 * 24));
	}

	private void downloadReadingPlan() {
		//TODO proper method. Testing only
		ReadingPlanManager.copyReadingPlanToDb(R.raw.wsts);
		loadDayNumber();
		loadReadingPlan();
		loadReadingPlanProgress();
		prepareDisplay();
	}
	private void loadReadingPlan() {
		//TODO: proper method. Testing only

		List<ReadingPlan.ReadingPlanInfo> infos = S.getDb().listAllReadingPlanInfo();
		long id = 0;
		long startDate = 0;
		for (ReadingPlan.ReadingPlanInfo info : infos) {
			id = info.id;
			startDate = info.startDate;
		}

		if (infos.size() == 0) {
			return;
		}

		byte[] binaryReadingPlan = S.getDb().getBinaryReadingPlanById(id);

		InputStream inputStream = new ByteArrayInputStream(binaryReadingPlan);
		ReadingPlan res = ReadingPlanManager.readVersion1(inputStream);
		res.info.id = id;
		res.info.startDate = startDate;
		readingPlan = res;
	}
	
	private void loadReadingPlanProgress() {
		//TODO proper method. Testing only
		if (readingPlan == null) {
			return;
		}
		readingCodes = S.getDb().getAllReadingCodesByReadingPlanId(readingPlan.info.id);
	}

	class ReadingPlanAdapter extends BaseAdapter {

		private int[] todayReadings;

		public void load() {
			todayReadings = readingPlan.dailyVerses.get(dayNumber);
		}

		@Override
		public int getCount() {
			return (todayReadings.length / 2) + readingPlan.info.duration + 1;
		}

		@Override
		public View getView(final int position, View convertView, final ViewGroup parent) {
			final int itemViewType = getItemViewType(position);

			if (itemViewType == 0) {
				if (convertView == null) {
					convertView = getLayoutInflater().inflate(R.layout.item_today_reading, parent, false);
				}

				final ImageView bTick = V.get(convertView, R.id.bTick);
				boolean[] readMarks = new boolean[todayReadings.length];
				ReadingPlanManager.writeReadMarksByDay(readingCodes, readMarks, dayNumber);
				if (readMarks[position * 2]) {
					bTick.setImageResource(R.drawable.ic_checked);
				} else {
					bTick.setImageResource(R.drawable.ic_unchecked);
				}

				bTick.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(final View v) {
						boolean[] readMarks = new boolean[todayReadings.length];
						ReadingPlanManager.writeReadMarksByDay(readingCodes, readMarks, dayNumber);
						boolean ticked = !readMarks[position * 2];

						ReadingPlanManager.updateReadingPlanProgress(readingPlan.info.id, dayNumber, position, ticked);
						loadReadingPlanProgress();
						load();
						notifyDataSetChanged();
					}
				});

				TextView textView = V.get(convertView, R.id.text1);
				int start = position * 2;
				int[] aris = {todayReadings[start], todayReadings[start + 1]};
				textView.setText(getReference(S.activeVersion, aris));

			} else if (itemViewType == 1) {
				if (convertView == null) {
					convertView = getLayoutInflater().inflate(R.layout.item_reading_plan_summary, parent, false);

				}

			} else if (itemViewType == 2) {
				if (convertView == null) {
					convertView = getLayoutInflater().inflate(R.layout.item_reading_plan_one_day, parent, false);
				}

				final LinearLayout layout = V.get(convertView, R.id.llOneDayReadingPlan);

				final int currentViewTypePosition = position - todayReadings.length / 2 - 1;

				//Text title
				TextView tTitle = V.get(convertView, android.R.id.text1);
				tTitle.setText(getReadingDateHeader(currentViewTypePosition));

				//Text reading
				while (true) {
					final View reading = layout.findViewWithTag("reading");
					if (reading != null) {
						layout.removeView(reading);
					} else {
						break;
					}
				}

				int[] aris = readingPlan.dailyVerses.get(currentViewTypePosition);
				for (int i = 0; i < aris.length / 2; i++) {
					final int ariPosition = i;
					int[] ariStartEnd = {aris[i * 2], aris[i * 2 + 1]};
					final SpannableStringBuilder reference = getReference(S.activeVersion, ariStartEnd);
					CheckBox checkBox = new CheckBox(ReadingPlanActivity.this);
					checkBox.setText(reference);
					checkBox.setTag("reading");

					boolean[] readMarks = new boolean[aris.length];
					ReadingPlanManager.writeReadMarksByDay(readingCodes, readMarks, currentViewTypePosition);
					checkBox.setChecked(readMarks[ariPosition * 2]);
					checkBox.setFocusable(false);
					LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
					checkBox.setLayoutParams(layoutParams);
					checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
						@Override
						public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
							ReadingPlanManager.updateReadingPlanProgress(readingPlan.info.id, currentViewTypePosition, ariPosition, isChecked);
							loadReadingPlanProgress();
							load();
							notifyDataSetChanged();
						}
					});
					layout.addView(checkBox);
				}
			}

			return convertView;
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
		public int getViewTypeCount() {
			return 3;
		}

		@Override
		public int getItemViewType(final int position) {
			if (position < todayReadings.length / 2) {
				return 0;
			} else if (position == todayReadings.length / 2) {
				return 1;
			} else {
				return 2;
			}
		}
	}

	public String getReadingDateHeader(final int dayNumber) {
		String date = "Day " + (dayNumber + 1) + ": ";
		if (readingPlan.info.version == 1) {
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(new Date(readingPlan.info.startDate));
			calendar.add(Calendar.DATE, dayNumber);

			date += new SimpleDateFormat("MMMM dd, yyyy").format(calendar.getTime());
		}
		return date;
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

}