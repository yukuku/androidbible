package yuku.alkitab.base.ac;

import android.app.DatePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.afollestad.materialdialogs.MaterialDialog;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.ac.base.BaseLeftDrawerActivity;
import yuku.alkitab.base.model.ReadingPlan;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.Background;
import yuku.alkitab.base.util.CurrentReading;
import yuku.alkitab.base.util.Foreground;
import yuku.alkitab.base.util.ReadingPlanManager;
import yuku.alkitab.base.util.Sqlitil;
import yuku.alkitab.base.widget.LeftDrawer;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.debug.R;
import yuku.alkitab.util.IntArrayList;
import yuku.alkitabintegration.display.Launcher;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class ReadingPlanActivity extends BaseLeftDrawerActivity implements LeftDrawer.ReadingPlan.Listener {
	public static final String TAG = ReadingPlanActivity.class.getSimpleName();

	public static final String ACTION_READING_PLAN_PROGRESS_CHANGED = ReadingPlanActivity.class.getName() + ".action.READING_PLAN_PROGRESS_CHANGED";

	private static final int REQCODE_openList = 1;

	DrawerLayout drawerLayout;
	LeftDrawer.ReadingPlan leftDrawer;

	private ReadingPlan readingPlan;
	private List<ReadingPlan.ReadingPlanInfo> downloadedReadingPlanInfos;
	private int todayNumber;
	private int dayNumber;

	/**
	 * List of reading codes that is read for the current reading plan.
	 * A reading code is a combination of day (left-bit-shifted by 8) and the reading sequence for that day starting from 0.
	 */
	private IntArrayList readReadingCodes;
	private boolean newDropDownItems;

	private ImageButton bLeft;
	private ImageButton bRight;
	private TextView bToday;
	private ListView lsReadingPlan;
	private ReadingPlanAdapter readingPlanAdapter;
	private ActionBar actionBar;
	private LinearLayout llNavigations;
	private FrameLayout flNoData;
	private Button bDownload;
	private boolean showDetails;

	float density;

	public static Intent createIntent() {
		return new Intent(App.context, ReadingPlanActivity.class);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_reading_plan);

		density = getResources().getDisplayMetrics().density;

		drawerLayout = V.get(this, R.id.drawerLayout);
		leftDrawer = V.get(this, R.id.left_drawer);
		leftDrawer.configure(this, drawerLayout);

		final Toolbar toolbar = V.get(this, R.id.toolbar);
		setSupportActionBar(toolbar);

		actionBar = getSupportActionBar();
		assert actionBar != null;
		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp);

		llNavigations = V.get(this, R.id.llNavigations);
		flNoData = V.get(this, R.id.flNoDataContainer);

		lsReadingPlan = V.get(this, R.id.lsTodayReadings);
		lsReadingPlan.setAdapter(readingPlanAdapter = new ReadingPlanAdapter());

		bToday = V.get(this, R.id.bToday);
		bToday.setOnClickListener(v -> new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				final PopupMenu popupMenu = new PopupMenu(ReadingPlanActivity.this, v);
				final Menu menu = popupMenu.getMenu();
				menu.add(0, 1, 0, getString(R.string.rp_showCalendar));
				menu.add(0, 3, 0, getString(R.string.rp_gotoToday));
				menu.add(0, 2, 0, getString(R.string.rp_gotoFirstUnread));
				menu.add(0, 5, 0, getString(R.string.rp_menuCatchMeUp));
				menu.add(0, 4, 0, getString(R.string.rp_setStartDate));

				popupMenu.setOnMenuItemClickListener(menuItem -> {
					popupMenu.dismiss();
					int itemId = menuItem.getItemId();
					switch (itemId) {
						case 1:
							showCalendar();
							break;
						case 2:
							gotoFirstUnread();
							break;
						case 3:
							gotoToday();
							break;
						case 4:
							showSetStartDateDialog();
							break;
						case 5:
							catchMeUp();
							break;
					}
					return true;
				});
				popupMenu.show();
			}

			private void gotoToday() {
				loadDayNumber();
				changeDay(0);
			}

			private void gotoFirstUnread() {
				dayNumber = findFirstUnreadDay();
				changeDay(0);
			}

			private void showCalendar() {
				Calendar calendar = GregorianCalendar.getInstance();
				calendar.setTimeInMillis(readingPlan.info.startTime);
				calendar.add(Calendar.DATE, dayNumber);

				DatePickerDialog.OnDateSetListener dateSetListener = (view, year, monthOfYear, dayOfMonth) -> {
					Calendar newCalendar = new GregorianCalendar(year, monthOfYear, dayOfMonth);
					Calendar startCalendar = GregorianCalendar.getInstance();
					startCalendar.setTimeInMillis(readingPlan.info.startTime);

					int newDay = calculateDaysDiff(startCalendar, newCalendar);
					if (newDay < 0) {
						newDay = 0;
					} else if (newDay >= readingPlan.info.duration) {
						newDay = readingPlan.info.duration - 1;
					}
					dayNumber = newDay;
					changeDay(0);
				};

				DatePickerDialog datePickerDialog = new DatePickerDialog(ReadingPlanActivity.this, dateSetListener, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
				datePickerDialog.show();
			}

			private void showSetStartDateDialog() {
				final Calendar today = GregorianCalendar.getInstance();
				today.setTimeInMillis(readingPlan.info.startTime);

				final DatePickerDialog.OnDateSetListener dateSetListener = (view, year, monthOfYear, dayOfMonth) -> {
					final Calendar newDate = new GregorianCalendar(year, monthOfYear, dayOfMonth, 2, 0, 0); // plus 2 hours to prevent DST-related problems
					if (readingPlan == null) {
						return;
					}

					final long startTime = newDate.getTimeInMillis();
					readingPlan.info.startTime = startTime;
					S.getDb().updateReadingPlanStartDate(readingPlan.info.id, startTime);
					changeDay(0);
					loadDayNumber();
					updateButtonStatus();
				};

				new DatePickerDialog(ReadingPlanActivity.this, dateSetListener, today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH)).show();
			}

			private void catchMeUp() {
				new MaterialDialog.Builder(ReadingPlanActivity.this)
					.content(R.string.rp_reset)
					.positiveText(R.string.ok)
					.onPositive((dialog, which) -> {
						int firstUnreadDay = findFirstUnreadDay();
						Calendar calendar = GregorianCalendar.getInstance();
						calendar.add(Calendar.DATE, -firstUnreadDay);
						S.getDb().updateReadingPlanStartDate(readingPlan.info.id, calendar.getTime().getTime());
						loadReadingPlan(readingPlan.info.id);
						loadDayNumber();
						readingPlanAdapter.load();

						updateButtonStatus();
					})
					.negativeText(R.string.cancel)
					.show();
			}
		});

		bLeft = V.get(this, R.id.bLeft);
		bLeft.setOnClickListener(v -> changeDay(-1));

		bRight = V.get(this, R.id.bRight);
		bRight.setOnClickListener(v -> changeDay(+1));

		bDownload = V.get(this, R.id.bDownload);

		final long id = Preferences.getLong(Prefkey.active_reading_plan_id, 0);
		loadReadingPlan(id);
		prepareDropDownNavigation();
		loadDayNumber();

		App.getLbm().registerReceiver(reload, new IntentFilter(ACTION_READING_PLAN_PROGRESS_CHANGED));
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		App.getLbm().unregisterReceiver(reload);
	}

	final BroadcastReceiver reload = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			reload();
		}
	};

	@Override
	protected void onStart() {
		super.onStart();

		reload();
	}

	void reload() {
		if (readingPlan != null) {
			loadReadingPlan(readingPlan.info.id); // so startTime can change
		}

		loadReadingPlanProgress();
		prepareDisplay();
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		getMenuInflater().inflate(R.menu.activity_reading_plan, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(final Menu menu) {
		final boolean anyReadingPlan = downloadedReadingPlanInfos.size() != 0;
		menu.findItem(R.id.menuDelete).setVisible(anyReadingPlan);

		if (!anyReadingPlan) {
			leftDrawer.getHandle().setDescription(null);
		}

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		final int itemId = item.getItemId();
		if (itemId == android.R.id.home) {
			leftDrawer.toggleDrawer();
			return true;

		} else if (itemId == R.id.menuDownload) {
			downloadReadingPlanList();
			return true;
		} else if (itemId == R.id.menuDelete) {
			deleteReadingPlan();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void loadReadingPlan(long id) {
		downloadedReadingPlanInfos = S.getDb().listAllReadingPlanInfo();

		if (downloadedReadingPlanInfos.size() == 0) {
			return;
		}

		Pair<String, byte[]> nameAndData = S.getDb().getReadingPlanNameAndData(id);

		long startTime = 0;
		if (id == 0 || nameAndData == null) {
			id = downloadedReadingPlanInfos.get(0).id;
			startTime = downloadedReadingPlanInfos.get(0).startTime;

			nameAndData = S.getDb().getReadingPlanNameAndData(id);
		} else {
			for (ReadingPlan.ReadingPlanInfo info : downloadedReadingPlanInfos) {
				if (id == info.id) {
					startTime = info.startTime;
				}
			}
		}

		final InputStream inputStream = new ByteArrayInputStream(nameAndData.second);
		final ReadingPlan res = ReadingPlanManager.readVersion1(inputStream, nameAndData.first);
		res.info.id = id;
		res.info.startTime = startTime;
		readingPlan = res;
		Preferences.setLong(Prefkey.active_reading_plan_id, id);

		leftDrawer.getHandle().setDescription(
			TextUtils.expandTemplate(
				getText(R.string.rp_description_with_id),
				readingPlan.info.title,
				String.valueOf(readingPlan.info.duration),
				readingPlan.info.name,
				readingPlan.info.description
			)
		);
	}

	private void loadReadingPlanProgress() {
		if (readingPlan == null) {
			return;
		}
		readReadingCodes = S.getDb().getAllReadingCodesByReadingPlanProgressGid(ReadingPlan.gidFromName(readingPlan.info.name));
	}

	public void goToIsiActivity(final int dayNumber, final int sequence) {
		final int[] selectedVerses = readingPlan.dailyVerses[dayNumber];
		final int ari_start = selectedVerses[sequence * 2];
		final int ari_end = selectedVerses[sequence * 2 + 1];

		CurrentReading.set(ari_start, ari_end);

		startActivity(Launcher.openAppAtBibleLocation(ari_start));
	}

	private void loadDayNumber() {
		if (readingPlan == null) {
			return;
		}

		Calendar startCalendar = GregorianCalendar.getInstance();
		startCalendar.setTimeInMillis(readingPlan.info.startTime);

		int tn = calculateDaysDiff(startCalendar, GregorianCalendar.getInstance());
		if (tn >= readingPlan.info.duration) {
			tn = readingPlan.info.duration - 1;
		} else if (tn < 0) {
			tn = 0;
		}

		dayNumber = todayNumber = tn;
	}

	private int calculateDaysDiff(Calendar startCalendar, Calendar endCalendar) {
		startCalendar.set(Calendar.HOUR_OF_DAY, 0);
		startCalendar.set(Calendar.MINUTE, 0);
		startCalendar.set(Calendar.SECOND, 0);
		startCalendar.set(Calendar.MILLISECOND, 0);

		endCalendar.set(Calendar.HOUR_OF_DAY, 0);
		endCalendar.set(Calendar.MINUTE, 0);
		endCalendar.set(Calendar.SECOND, 0);
		endCalendar.set(Calendar.MILLISECOND, 0);

		// add 2 hours to prevent DST-related problems
		return (int) ((2 * 3600 * 1000 + endCalendar.getTime().getTime() - startCalendar.getTime().getTime()) / (1000 * 60 * 60 * 24));
	}


	public boolean prepareDropDownNavigation() {
		if (downloadedReadingPlanInfos.size() == 0) {
			actionBar.setDisplayShowTitleEnabled(true);
			actionBar.setTitle(R.string.rp_activity_title);
			actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
			return true;
		}

		actionBar.setDisplayShowTitleEnabled(false);
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);

		long id = Preferences.getLong(Prefkey.active_reading_plan_id, 0);
		int itemNumber = 0;
		//Drop-down navigation
		List<String> titles = new ArrayList<>();
		for (int i = 0; i < downloadedReadingPlanInfos.size(); i++) {
			ReadingPlan.ReadingPlanInfo info = downloadedReadingPlanInfos.get(i);
			titles.add(info.title);
			if (info.id == id) {
				itemNumber = i;
			}
		}

		final ArrayAdapter<String> navigationAdapter = new ArrayAdapter<>(actionBar.getThemedContext(), android.R.layout.simple_spinner_dropdown_item, titles);

		newDropDownItems = false;
		actionBar.setListNavigationCallbacks(navigationAdapter, (i, l) -> {
			if (newDropDownItems) {
				loadReadingPlan(downloadedReadingPlanInfos.get(i).id);
				loadDayNumber();
				reload();
			}
			newDropDownItems = true;
			return true;
		});
		actionBar.setSelectedNavigationItem(itemNumber);
		return false;
	}

	public void prepareDisplay() {
		if (readingPlan == null) {
			llNavigations.setVisibility(View.GONE);
			lsReadingPlan.setVisibility(View.GONE);
			flNoData.setVisibility(View.VISIBLE);

			bDownload.setOnClickListener(v -> downloadReadingPlanList());
		} else {
			llNavigations.setVisibility(View.VISIBLE);
			lsReadingPlan.setVisibility(View.VISIBLE);
			flNoData.setVisibility(View.GONE);
		}

		readingPlanAdapter.load();

		updateButtonStatus();
	}

	private int findFirstUnreadDay() {
		for (int i = 0; i < readingPlan.info.duration - 1; i++) {
			boolean[] readMarks = new boolean[readingPlan.dailyVerses[i].length / 2];
			ReadingPlanManager.writeReadMarksByDay(readReadingCodes, readMarks, i);
			for (boolean readMark : readMarks) {
				if (!readMark) {
					return i;
				}
			}
		}
		return readingPlan.info.duration - 1;
	}

	private void deleteReadingPlan() {
		new MaterialDialog.Builder(this)
			.content(getString(R.string.rp_deletePlan, readingPlan.info.title))
			.positiveText(R.string.delete)
			.onPositive((dialog, which) -> {
				S.getDb().deleteReadingPlanById(readingPlan.info.id);
				readingPlan = null;
				Preferences.remove(Prefkey.active_reading_plan_id);
				loadReadingPlan(0);
				loadReadingPlanProgress();
				loadDayNumber();
				prepareDropDownNavigation();
				prepareDisplay();
				supportInvalidateOptionsMenu();
			})
			.negativeText(R.string.cancel)
			.show();
	}

	private void changeDay(int day) {
		int newDay = dayNumber + day;
		if (newDay < 0 || newDay >= readingPlan.info.duration) {
			return;
		}
		dayNumber = newDay;
		readingPlanAdapter.load();

		updateButtonStatus();
	}

	private void updateButtonStatus() {
		if (readingPlan == null) {
			return;
		}

		bLeft.setEnabled(dayNumber != 0);
		bRight.setEnabled(dayNumber != readingPlan.info.duration - 1);
		bToday.setText(getReadingDateHeader(dayNumber));
	}

	@Override
	public void bRestart_click() {
		new MaterialDialog.Builder(this)
			.content(R.string.rp_restart_desc)
			.positiveText(R.string.ok)
			.onPositive((dialog, which) -> {
				S.getDb().deleteAllReadingPlanProgressForGid(ReadingPlan.gidFromName(readingPlan.info.name));
				S.getDb().updateReadingPlanStartDate(readingPlan.info.id, System.currentTimeMillis());
				loadReadingPlan(readingPlan.info.id);
				loadDayNumber();
				readingPlanAdapter.load();
				reload();

				updateButtonStatus();
			})
			.negativeText(R.string.cancel)
			.show();

		leftDrawer.closeDrawer();
	}

	@Override
	protected LeftDrawer getLeftDrawer() {
		return leftDrawer;
	}

	private void downloadReadingPlanList() {
		startActivityForResult(HelpActivity.createIntent(BuildConfig.SERVER_HOST + "rp/downloads?app_versionCode=" + App.getVersionCode() + "&app_versionName=" + Uri.encode(App.getVersionName()), getString(R.string.rp_menuDownload)), REQCODE_openList);
	}

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		if (requestCode == REQCODE_openList && resultCode == RESULT_OK) {
			final Uri uri = data.getData();
			if (uri != null) {
				downloadByAlkitabUri(uri);
			}
			return;
		}

		super.onActivityResult(requestCode, resultCode, data);
	}

	private void downloadByAlkitabUri(final Uri uri) {
		if (!"alkitab".equals(uri.getScheme()) || !"/addon/download".equals(uri.getPath()) || !"readingplan".equals(uri.getQueryParameter("kind")) || !"rpb".equals(uri.getQueryParameter("type")) || uri.getQueryParameter("name") == null) {
			new MaterialDialog.Builder(this)
				.content("Invalid uri:\n\n" + uri)
				.positiveText(R.string.ok)
				.show();
			return;
		}

		final String name = uri.getQueryParameter("name");

		downloadReadingPlanFromServer(name);
	}

	void downloadReadingPlanFromServer(final String name) {
		if (S.getDb().listReadingPlanNames().contains(name)) {
			new MaterialDialog.Builder(this)
				.content(R.string.rp_download_already_have)
				.positiveText(R.string.ok)
				.show();

			return;
		}

		final AtomicBoolean cancelled = new AtomicBoolean(false);

		final MaterialDialog pd = new MaterialDialog.Builder(this)
			.content(R.string.rp_download_reading_plan_progress)
			.progress(true, 0)
			.dismissListener(dialog -> cancelled.set(true))
			.show();

		Background.run(() -> {
			try {
				final byte[] data = App.downloadBytes(BuildConfig.SERVER_HOST + "rp/get_rp?name=" + name);
				if (cancelled.get()) return;
				Foreground.run(() -> {
					final long id = ReadingPlanManager.insertReadingPlanToDb(data, name);

					if (id == 0) {
						new MaterialDialog.Builder(ReadingPlanActivity.this)
							.content(getString(R.string.rp_download_reading_plan_data_corrupted))
							.positiveText(R.string.ok)
							.show();
						return;
					}

					Preferences.setLong(Prefkey.active_reading_plan_id, id);
					loadReadingPlan(id);
					loadReadingPlanProgress();
					loadDayNumber();
					prepareDropDownNavigation();
					prepareDisplay();
					supportInvalidateOptionsMenu();
				});
			} catch (Exception e) {
				if (!cancelled.get()) {
					Log.e(TAG, "downloading reading plan data", e);
					Foreground.run(() -> new MaterialDialog.Builder(ReadingPlanActivity.this)
						.content(getString(R.string.rp_download_reading_plan_failed))
						.positiveText(R.string.ok)
						.show()
					);
				}
			} finally {
				pd.dismiss();
			}
		});
	}

	private float getActualPercentage() {
		return 100.f * readReadingCodes.size() / countAllReadings();
	}

	private float getTargetPercentage() {
		return 100.f * countTarget() / countAllReadings();
	}

	private int countTarget() {
		int doubledCount = 0;
		for (int i = 0; i <= todayNumber; i++) {
			doubledCount += readingPlan.dailyVerses[i].length;
		}
		return doubledCount / 2;
	}

	private int countAllReadings() {
		int doubledCount = 0;
		for (int i = 0; i < readingPlan.info.duration; i++) {
			doubledCount += readingPlan.dailyVerses[i].length;
		}
		return doubledCount / 2;
	}

	public String getReadingDateHeader(final int dayNumber) {
		Calendar calendar = GregorianCalendar.getInstance();
		calendar.setTimeInMillis(readingPlan.info.startTime);
		calendar.add(Calendar.DATE, dayNumber);

		return getString(R.string.rp_dayHeader, (dayNumber + 1), Sqlitil.toLocaleDateMedium(calendar.getTime()));
	}

	void one_reading_longClick(final int day, final int sequence) {
		new MaterialDialog.Builder(this)
			.content(R.string.rp_mark_as_read_up_to)
			.positiveText(R.string.ok)
			.negativeText(R.string.cancel)
			.onPositive((dialog, which) -> {
				ReadingPlanManager.markAsReadUpTo(readingPlan.info.name, readingPlan.dailyVerses, day, sequence);
				reload();
			})
			.show();
	}

	class ReadingPlanAdapter extends EasyAdapter {
		private int[] todayReadings;
		ColorStateList originalCommentTextColor = null;

		public void load() {
			if (readingPlan == null) {
				todayReadings = null;
			} else {
				todayReadings = readingPlan.dailyVerses[dayNumber];
			}
			notifyDataSetChanged();
		}

		@Override
		public int getCount() {
			if (todayReadings == null) return 0;

			if (showDetails) {
				return (todayReadings.length / 2) + readingPlan.info.duration + 1;
			} else {
				return (todayReadings.length / 2) + 1;
			}
		}

		@Override
		public View newView(final int position, final ViewGroup parent) {
			final int itemViewType = getItemViewType(position);
			if (itemViewType == 0) {
				return getLayoutInflater().inflate(R.layout.item_reading_plan_one_reading, parent, false);
			} else if (itemViewType == 1) {
				return getLayoutInflater().inflate(R.layout.item_reading_plan_summary, parent, false);
			} else if (itemViewType == 2) {
				return getLayoutInflater().inflate(R.layout.item_reading_plan_one_day, parent, false);
			}
			return null;
		}

		@Override
		public void bindView(final View res, final int position, final ViewGroup parent) {
			final int itemViewType = getItemViewType(position);
			if (itemViewType == 0) {
				final Button bReference = V.get(res, R.id.bReference);
				final CheckBox checkbox = V.get(res, R.id.checkbox);

				final boolean[] readMarks = new boolean[todayReadings.length / 2];
				ReadingPlanManager.writeReadMarksByDay(readReadingCodes, readMarks, dayNumber);

				bReference.setText(S.activeVersion.referenceRange(todayReadings[position * 2], todayReadings[position * 2 + 1]));

				bReference.setOnClickListener(v -> {
					final int todayReadingsSize = readingPlan.dailyVerses[dayNumber].length / 2;
					if (position < todayReadingsSize) {
						goToIsiActivity(dayNumber, position);
					} else if (position > todayReadingsSize) {
						goToIsiActivity(position - todayReadingsSize - 1, 0);
					}
				});
				checkbox.setOnCheckedChangeListener(null);
				checkbox.setChecked(readMarks[position]);

				checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
					ReadingPlanManager.updateReadingPlanProgress(readingPlan.info.name, dayNumber, position, isChecked);
					loadReadingPlanProgress();
					load();
				});
			} else if (itemViewType == 1) {
				final ProgressBar pbReadingProgress = V.get(res, R.id.pbReadingProgress);
				final TextView tActual = V.get(res, R.id.tActual);
				final TextView tTarget = V.get(res, R.id.tTarget);
				final TextView tComment = V.get(res, R.id.tComment);
				final TextView tDetail = V.get(res, R.id.tDetail);

				float actualPercentage = getActualPercentage();
				float targetPercentage = getTargetPercentage();

				pbReadingProgress.setMax(10000);
				pbReadingProgress.setProgress((int) (actualPercentage * 100));
				pbReadingProgress.setSecondaryProgress((int) (targetPercentage * 100));

				tActual.setText(getString(R.string.rp_commentActual, String.format(Locale.US, "%.2f", actualPercentage)));
				tTarget.setText(getString(R.string.rp_commentTarget, String.format(Locale.US, "%.2f", targetPercentage)));

				if (originalCommentTextColor == null) {
					originalCommentTextColor = tComment.getTextColors();
				}

				if (actualPercentage == targetPercentage) {
					tComment.setText(R.string.rp_commentOnSchedule);
					tComment.setTextColor(ResourcesCompat.getColor(getResources(), R.color.escape, getTheme()));
				} else if (actualPercentage < targetPercentage) {
					tComment.setText(getString(R.string.rp_commentBehindSchedule, String.format(Locale.US, "%.2f", targetPercentage - actualPercentage)));
					tComment.setTextColor(originalCommentTextColor);
				} else {
					tComment.setText(getString(R.string.rp_commentAheadSchedule, String.format(Locale.US, "%.2f", actualPercentage - targetPercentage)));
					tComment.setTextColor(ResourcesCompat.getColor(getResources(), R.color.escape, getTheme()));
				}

				tDetail.setOnClickListener(v -> {
					showDetails = !showDetails;
					if (showDetails) {
						tDetail.setText(R.string.rp_hideDetails);
					} else {
						tDetail.setText(R.string.rp_showDetails);
					}
					notifyDataSetChanged();
				});

			} else if (itemViewType == 2) {
				final LinearLayout layout = V.get(res, R.id.llOneDayReadingPlan);

				final int day = position - todayReadings.length / 2 - 1;

				//Text title
				TextView tTitle = V.get(res, android.R.id.text1);
				tTitle.setText(getReadingDateHeader(day));

				//Text reading
				int[] ariRanges = readingPlan.dailyVerses[day];
				final int checkbox_count = ariRanges.length / 2;

				{ // remove extra checkboxes
					for (int i = layout.getChildCount() - 1; i >= 0; i--) {
						final View view = layout.getChildAt(i);
						if (view instanceof CheckBox && view.getTag() != null) {
							Integer tag = (Integer) view.getTag();
							if (tag >= checkbox_count) layout.removeViewAt(i);
						}
					}
				}

				final boolean[] readMarks = new boolean[checkbox_count];
				ReadingPlanManager.writeReadMarksByDay(readReadingCodes, readMarks, day);

				for (int i = 0; i < checkbox_count; i++) {
					final int sequence = i;

					CheckBox checkBox = (CheckBox) layout.findViewWithTag(i);
					if (checkBox == null) {
						checkBox = (CheckBox) getLayoutInflater().inflate(R.layout.item_reading_plan_one_reading_checkbox, layout, false);
						checkBox.setTag(i);
						layout.addView(checkBox);
					}

					checkBox.setOnCheckedChangeListener(null);
					checkBox.setChecked(readMarks[sequence]);
					checkBox.setText(S.activeVersion.referenceRange(ariRanges[sequence * 2], ariRanges[sequence * 2 + 1]));
					checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
						ReadingPlanManager.updateReadingPlanProgress(readingPlan.info.name, day, sequence, isChecked);
						loadReadingPlanProgress();
						load();
					});
					checkBox.setOnLongClickListener(v -> {
						one_reading_longClick(day, sequence);
						return true;
					});
				}
			}
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

		@Override
		public boolean isEnabled(final int position) {
			return false;
		}
	}

}