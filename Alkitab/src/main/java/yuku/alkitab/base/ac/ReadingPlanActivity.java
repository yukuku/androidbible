package yuku.alkitab.base.ac;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
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
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.afollestad.materialdialogs.AlertDialogWrapper;
import com.afollestad.materialdialogs.MaterialDialog;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.ac.base.BaseLeftDrawerActivity;
import yuku.alkitab.base.model.ReadingPlan;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.ReadingPlanManager;
import yuku.alkitab.base.util.Sqlitil;
import yuku.alkitab.base.widget.LeftDrawer;
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

	private static final int REQCODE_openList = 1;

	DrawerLayout drawerLayout;
	ActionBarDrawerToggle drawerToggle;
	LeftDrawer.ReadingPlan leftDrawer;

	private ReadingPlan readingPlan;
	private List<ReadingPlan.ReadingPlanInfo> downloadedReadingPlanInfos;
	private int todayNumber;
	private int dayNumber;
	private IntArrayList readingCodes;
	private boolean newDropDownItems;

	private ImageButton bLeft;
	private ImageButton bRight;
	private Button bToday;
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

		drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.drawer_open, R.string.drawer_close);
		drawerLayout.setDrawerListener(drawerToggle);

		llNavigations = V.get(this, R.id.llNavigations);
		flNoData = V.get(this, R.id.flNoDataContainer);

		lsReadingPlan = V.get(this, R.id.lsTodayReadings);
		bToday = V.get(this, R.id.bToday);
		bLeft = V.get(this, R.id.bLeft);
		bRight = V.get(this, R.id.bRight);
		bDownload = V.get(this, R.id.bDownload);

		actionBar = getSupportActionBar();
		actionBar.setDisplayShowHomeEnabled(Build.VERSION.SDK_INT < 18);
		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setHomeButtonEnabled(true);

		final long id = Preferences.getLong(Prefkey.active_reading_plan_id, 0);
		loadReadingPlan(id);
		prepareDropDownNavigation();
		loadDayNumber();
	}

	@Override
	protected void onStart() {
		super.onStart();

		loadReadingPlanProgress();
		prepareDisplay();
	}

	@Override
	protected void onPostCreate(final Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		drawerToggle.syncState();
	}

	@Override
	public void onConfigurationChanged(final Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		drawerToggle.onConfigurationChanged(newConfig);
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
		if (drawerToggle.onOptionsItemSelected(item)) {
			return true;
		}

		final int itemId = item.getItemId();
		if (itemId == R.id.menuDownload) {
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
		readingCodes = S.getDb().getAllReadingCodesByReadingPlanId(readingPlan.info.id);
	}

	public void goToIsiActivity(final int dayNumber, final int sequence) {
		final int[] selectedVerses = readingPlan.dailyVerses[dayNumber];
		final int ari = selectedVerses[sequence * 2];

		startActivity(Launcher.openAppAtBibleLocation(ari));
	}

	private void loadDayNumber() {
		if (readingPlan == null) {
			return;
		}

		Calendar startCalendar = GregorianCalendar.getInstance();
		startCalendar.setTimeInMillis(readingPlan.info.startTime);

		todayNumber = calculateDaysDiff(startCalendar, GregorianCalendar.getInstance());
		if (todayNumber >= readingPlan.info.duration) {
			todayNumber = readingPlan.info.duration - 1;
		} else if (todayNumber < 0) {
			todayNumber = 0;
		}

		dayNumber = todayNumber;
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
			actionBar.setTitle(R.string.rp_menuReadingPlan);
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
				loadReadingPlanProgress();
				loadDayNumber();
				prepareDisplay();
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
			return;
		}
		llNavigations.setVisibility(View.VISIBLE);
		lsReadingPlan.setVisibility(View.VISIBLE);
		flNoData.setVisibility(View.GONE);

		//Listviews
		readingPlanAdapter = new ReadingPlanAdapter();
		readingPlanAdapter.load();
		lsReadingPlan.setAdapter(readingPlanAdapter);

		//buttons
		updateButtonStatus();

		bToday.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				final PopupMenu popupMenu = new PopupMenu(ReadingPlanActivity.this, v);
				popupMenu.getMenu().add(Menu.NONE, 1, 1, getString(R.string.rp_showCalendar));
				popupMenu.getMenu().add(Menu.NONE, 2, 2, getString(R.string.rp_gotoFirstUnread));
				popupMenu.getMenu().add(Menu.NONE, 3, 3, getString(R.string.rp_gotoToday));

				popupMenu.setOnMenuItemClickListener(menuItem -> {
					popupMenu.dismiss();
					int itemId = menuItem.getItemId();
					if (itemId == 1) {
						showCalendar();
					} else if (itemId == 2) {
						gotoFirstUnread();
					} else if (itemId == 3) {
						gotoToday();
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
		});

		bLeft.setOnClickListener(v -> changeDay(-1));

		bRight.setOnClickListener(v -> changeDay(+1));
	}

	private void resetReadingPlan() {
		new AlertDialogWrapper.Builder(this)
			.setMessage(R.string.rp_reset)
			.setPositiveButton(R.string.ok, (dialog, which) -> {
				int firstUnreadDay = findFirstUnreadDay();
				Calendar calendar = GregorianCalendar.getInstance();
				calendar.add(Calendar.DATE, -firstUnreadDay);
				S.getDb().updateStartDate(readingPlan.info.id, calendar.getTime().getTime());
				loadReadingPlan(readingPlan.info.id);
				loadDayNumber();
				readingPlanAdapter.load();
				readingPlanAdapter.notifyDataSetChanged();

				updateButtonStatus();
			})
			.setNegativeButton(R.string.cancel, null)
			.show();
	}

	private int findFirstUnreadDay() {

		for (int i = 0; i < readingPlan.info.duration - 1; i++) {
			boolean[] readMarks = new boolean[readingPlan.dailyVerses[i].length / 2];
			ReadingPlanManager.writeReadMarksByDay(readingCodes, readMarks, i);
			for (boolean readMark : readMarks) {
				if (!readMark) {
					return i;
				}
			}
		}
		return readingPlan.info.duration - 1;
	}

	private void deleteReadingPlan() {
		new AlertDialogWrapper.Builder(this)
			.setMessage(getString(R.string.rp_deletePlan, readingPlan.info.title))
			.setPositiveButton(R.string.delete, (dialog, which) -> {
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
			.setNegativeButton(R.string.cancel, null)
			.show();
	}

	private void changeDay(int day) {
		int newDay = dayNumber + day;
		if (newDay < 0 || newDay >= readingPlan.info.duration) {
			return;
		}
		dayNumber = newDay;
		readingPlanAdapter.load();
		readingPlanAdapter.notifyDataSetChanged();

		updateButtonStatus();
	}

	private void updateButtonStatus() {            //TODO look disabled
		bLeft.setEnabled(dayNumber != 0);
		bRight.setEnabled(dayNumber != readingPlan.info.duration - 1);

		bToday.setText(getReadingDateHeader(dayNumber));

	}

	@Override
	public void bCatchMeUp_click() {
		resetReadingPlan();
	}

	@Override
	protected LeftDrawer getLeftDrawer() {
		return leftDrawer;
	}

	private void downloadReadingPlanList() {
		startActivityForResult(HelpActivity.createIntent("https://alkitab-host.appspot.com/rp/downloads?app_versionCode=" + App.getVersionCode() + "&app_versionName=" + Uri.encode(App.getVersionName())), REQCODE_openList);
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

		new Thread() {
			@Override
			public void run() {
				try {
					download();
				} catch (Exception e) {
					if (cancelled.get()) {
						Log.e(TAG, "downloading reading plan data", e);
						new AlertDialogWrapper.Builder(ReadingPlanActivity.this)
							.setMessage(getString(R.string.rp_download_reading_plan_failed))
							.setPositiveButton(R.string.ok, null)
							.show();
					}
				} finally {
					pd.dismiss();
				}
			}

			/**
			 * run on bg thread
			 */
			void download() throws Exception {
				final byte[] bytes = App.downloadBytes("https://alkitab-host.appspot.com/rp/get_rp?name=" + name);

				if (cancelled.get()) return;
				runOnUiThread(() -> onReadingPlanDownloadFinished(bytes));
			}

			/**
			 * run on ui thread
			 */
			void onReadingPlanDownloadFinished(final byte[] data) {
				if (cancelled.get()) return;

				final long id = ReadingPlanManager.insertReadingPlanToDb(data, name);

				if (id == 0) {
					new AlertDialogWrapper.Builder(ReadingPlanActivity.this)
						.setMessage(getString(R.string.rp_download_reading_plan_data_corrupted))
						.setPositiveButton(R.string.ok, null)
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
			}
		}.start();
	}

	private float getActualPercentage() {
		return 100.f * countRead() / countAllReadings();
	}

	private float getTargetPercentage() {
		return 100.f * countTarget() / countAllReadings();
	}

	private int countRead() {
		IntArrayList filteredReadingCodes = ReadingPlanManager.filterReadingCodesByDayStartEnd(readingCodes, 0, todayNumber);
		return filteredReadingCodes.size();
	}

	private int countTarget() {
		int res = 0;
		for (int i = 0; i <= todayNumber; i++) {
			res += readingPlan.dailyVerses[i].length / 2;
		}
		return res;
	}

	private int countAllReadings() {
		int res = 0;
		for (int i = 0; i < readingPlan.info.duration; i++) {
			res += readingPlan.dailyVerses[i].length / 2;
		}
		return res;
	}

	public String getReadingDateHeader(final int dayNumber) {
		Calendar calendar = GregorianCalendar.getInstance();
		calendar.setTimeInMillis(readingPlan.info.startTime);
		calendar.add(Calendar.DATE, dayNumber);

		return getString(R.string.rp_dayHeader, (dayNumber + 1), Sqlitil.toLocaleDateMedium(calendar.getTime()));
	}

	class ReadingPlanAdapter extends EasyAdapter {
		private int[] todayReadings;

		public void load() {
			todayReadings = readingPlan.dailyVerses[dayNumber];
		}

		@Override
		public int getCount() {
			if (showDetails) {
				return (todayReadings.length / 2) + readingPlan.info.duration + 1;
			} else {
				return (todayReadings.length / 2) +  1;
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
				ReadingPlanManager.writeReadMarksByDay(readingCodes, readMarks, dayNumber);

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
					ReadingPlanManager.updateReadingPlanProgress(readingPlan.info.id, dayNumber, position, isChecked);
					loadReadingPlanProgress();
					load();
					notifyDataSetChanged();
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

				tActual.setText(getString(R.string.rp_commentActual, String.format("%.2f", actualPercentage)));
				tTarget.setText(getString(R.string.rp_commentTarget, String.format("%.2f", targetPercentage)));

				String comment;
				if (actualPercentage == targetPercentage) {
					comment = getString(R.string.rp_commentOnSchedule);
				} else {
					String diff = String.format(Locale.US, "%.2f", targetPercentage - actualPercentage);
					comment = getString(R.string.rp_commentBehindSchedule, diff);
				}

				tComment.setText(comment);

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

				final int currentViewTypePosition = position - todayReadings.length / 2 - 1;

				//Text title
				TextView tTitle = V.get(res, android.R.id.text1);
				tTitle.setText(getReadingDateHeader(currentViewTypePosition));

				//Text reading
				int[] ariRanges = readingPlan.dailyVerses[currentViewTypePosition];
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
				ReadingPlanManager.writeReadMarksByDay(readingCodes, readMarks, currentViewTypePosition);

				for (int i = 0; i < checkbox_count; i++) {
					final int sequence = i;

					CheckBox checkBox = (CheckBox) layout.findViewWithTag(i);
					if (checkBox == null) {
						checkBox = (CheckBox) getLayoutInflater().inflate(R.layout.item_reading_plan_one_day_checkbox, layout, false);
						checkBox.setTag(i);
						layout.addView(checkBox);
					}

					checkBox.setOnCheckedChangeListener(null);
					checkBox.setChecked(readMarks[sequence]);
					checkBox.setText(S.activeVersion.referenceRange(ariRanges[sequence * 2], ariRanges[sequence * 2 + 1]));
					checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
						ReadingPlanManager.updateReadingPlanProgress(readingPlan.info.id, currentViewTypePosition, sequence, isChecked);
						loadReadingPlanProgress();
						load();
						notifyDataSetChanged();
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