package yuku.alkitab.base.ac;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.PopupMenu;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.google.gson.GsonBuilder;
import com.squareup.okhttp.OkHttpClient;
import yuku.afw.App;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.afw.widget.EasyAdapter;
import yuku.alkitab.base.S;
import yuku.alkitab.base.model.ReadingPlan;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.ReadingPlanManager;
import yuku.alkitab.base.util.Sqlitil;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Version;
import yuku.alkitab.util.Ari;
import yuku.alkitab.util.IntArrayList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ReadingPlanActivity extends ActionBarActivity {
	public static final String TAG = ReadingPlanActivity.class.getSimpleName();

	public static final String READING_PLAN_ARI_RANGES = "reading_plan_ari_ranges";
	public static final String READING_PLAN_ID = "reading_plan_id";
	public static final String READING_PLAN_DAY_NUMBER = "reading_plan_day_number";

	private ReadingPlan readingPlan;
	private List<ReadingPlan.ReadingPlanInfo> downloadedReadingPlanInfos;
	private int todayNumber;
	private int dayNumber;
	private IntArrayList readingCodes;
	private boolean newDropDownItems;

	private Menu menu;
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

	public static Intent createIntent(int dayNumber) {
		Intent intent = new Intent(App.context, ReadingPlanActivity.class);
		intent.putExtra(READING_PLAN_DAY_NUMBER, dayNumber);
		return intent;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_reading_plan);
		llNavigations = V.get(this, R.id.llNavigations);
		flNoData = V.get(this, R.id.flNoDataContainer);

		lsReadingPlan = V.get(this, R.id.lsTodayReadings);
		bToday = V.get(this, R.id.bToday);
		bLeft = V.get(this, R.id.bLeft);
		bRight = V.get(this, R.id.bRight);
		bDownload = V.get(this, R.id.bDownload);
		actionBar = getSupportActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);

		long id = Preferences.getLong(Prefkey.active_reading_plan_id, 0);
		loadReadingPlan(id);
		loadReadingPlanProgress();
		prepareDropDownNavigation();
		loadDayNumber(false);
		prepareDisplay();

	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {
		getMenuInflater().inflate(R.menu.activity_reading_plan, menu);
		this.menu = menu;
		setReadingPlanMenuVisibility();
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		int itemId = item.getItemId();
		if (itemId == android.R.id.home) {
			finish();
			return true;
		} else if (itemId == R.id.menuReset) {
			resetReadingPlan();
			return true;
		} else if (itemId == R.id.menuDownload) {
			downloadReadingPlanList();
			return true;
		} else if (itemId == R.id.menuDelete) {
			deleteReadingPlan();
			return true;
		} else if (itemId == R.id.menuAbout) {
			showAbout();
		}
		return super.onOptionsItemSelected(item);
	}

	private void loadReadingPlan(long id) {

		downloadedReadingPlanInfos = S.getDb().listAllReadingPlanInfo();

		if (downloadedReadingPlanInfos.size() == 0) {
			return;
		}

		byte[] binaryReadingPlan = S.getDb().getBinaryReadingPlanById(id);

		long startTime = 0;
		if (id == 0 || binaryReadingPlan == null) {
			id = downloadedReadingPlanInfos.get(0).id;
			startTime = downloadedReadingPlanInfos.get(0).startTime;

			binaryReadingPlan = S.getDb().getBinaryReadingPlanById(id);
		} else {
			for (ReadingPlan.ReadingPlanInfo info : downloadedReadingPlanInfos) {
				if (id == info.id) {
					startTime = info.startTime;
				}
			}
		}

		InputStream inputStream = new ByteArrayInputStream(binaryReadingPlan);
		ReadingPlan res = ReadingPlanManager.readVersion1(inputStream);
		res.info.id = id;
		res.info.startTime = startTime;
		readingPlan = res;
		Preferences.setLong(Prefkey.active_reading_plan_id, id);
	}

	private void loadReadingPlanProgress() {
		if (readingPlan == null) {
			return;
		}
		readingCodes = S.getDb().getAllReadingCodesByReadingPlanId(readingPlan.info.id);
	}

	public void goToIsiActivity(final int dayNumber, final int sequence) {
		final int[] selectedVerses = readingPlan.dailyVerses[dayNumber];
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

	private void loadDayNumber(boolean setAsToday) {
		if (readingPlan == null) {
			return;
		}

		Calendar startCalendar = GregorianCalendar.getInstance();
		startCalendar.setTime(new Date(readingPlan.info.startTime));

		todayNumber = calculateDaysDiff(startCalendar, GregorianCalendar.getInstance());
		if (todayNumber >= readingPlan.info.duration) {
			todayNumber = readingPlan.info.duration - 1;
		} else if (todayNumber < 0) {
			todayNumber = 0;
		}

		if (setAsToday) {
			dayNumber = todayNumber;
		} else {
			dayNumber = getIntent().getIntExtra(READING_PLAN_DAY_NUMBER, -1);
			if (dayNumber == -1) {
				dayNumber = todayNumber;
			}
		}
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

		return (int) ((endCalendar.getTime().getTime() - startCalendar.getTime().getTime()) / (1000 * 60 * 60 * 24));
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
		List<String> titles = new ArrayList<String>();
		for (int i = 0; i < downloadedReadingPlanInfos.size(); i++) {
			ReadingPlan.ReadingPlanInfo info = downloadedReadingPlanInfos.get(i);
			titles.add(info.title);
			if (info.id == id) {
				itemNumber = i;
			}
		}

		ArrayAdapter<String> navigationAdapter = new ArrayAdapter<String>(actionBar.getThemedContext(), R.layout.support_simple_spinner_dropdown_item, titles);

		newDropDownItems = false;
		actionBar.setListNavigationCallbacks(navigationAdapter, new ActionBar.OnNavigationListener() {
			@Override
			public boolean onNavigationItemSelected(final int i, final long l) {
				if (newDropDownItems) {
					loadReadingPlan(downloadedReadingPlanInfos.get(i).id);
					loadReadingPlanProgress();
					loadDayNumber(true);
					prepareDisplay();
				}
				newDropDownItems = true;
				return true;
			}
		});
		actionBar.setSelectedNavigationItem(itemNumber);
		return false;
	}

	private void setReadingPlanMenuVisibility() {
		boolean visible = downloadedReadingPlanInfos.size() != 0;
		menu.findItem(R.id.menuReset).setVisible(visible);
		menu.findItem(R.id.menuDelete).setVisible(visible);
		menu.findItem(R.id.menuAbout).setVisible(visible);
	}

	public void prepareDisplay() {
		if (readingPlan == null) {
			llNavigations.setVisibility(View.GONE);
			lsReadingPlan.setVisibility(View.GONE);
			flNoData.setVisibility(View.VISIBLE);

			bDownload.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(final View v) {
					downloadReadingPlanList();
				}
			});
			return;
		}
		llNavigations.setVisibility(View.VISIBLE);
		lsReadingPlan.setVisibility(View.VISIBLE);
		flNoData.setVisibility(View.GONE);

		//Listviews
		readingPlanAdapter = new ReadingPlanAdapter();
		readingPlanAdapter.load();
		lsReadingPlan.setAdapter(readingPlanAdapter);

		lsReadingPlan.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
				final int todayReadingsSize = readingPlan.dailyVerses[dayNumber].length / 2;
				if (position < todayReadingsSize) {
					goToIsiActivity(dayNumber, position);
				} else if (position > todayReadingsSize) {
					goToIsiActivity(position - todayReadingsSize - 1, 0);
				}
			}

		});

		//buttons

		updateButtonStatus();

		bToday.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				final PopupMenu popupMenu = new PopupMenu(ReadingPlanActivity.this, v);
				popupMenu.getMenu().add(Menu.NONE, 1, 1, getString(R.string.rp_showCalendar));
				popupMenu.getMenu().add(Menu.NONE, 2, 2, getString(R.string.rp_gotoFirstUnread));
				popupMenu.getMenu().add(Menu.NONE, 3, 3, getString(R.string.rp_gotoToday));

				popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
					@Override
					public boolean onMenuItemClick(final MenuItem menuItem) {
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
					}
				});
				popupMenu.show();
			}

			private void gotoToday() {
				loadDayNumber(true);
				changeDay(0);
			}

			private void gotoFirstUnread() {
				dayNumber = findFirstUnreadDay();
				changeDay(0);
			}

			private void showCalendar() {
				Calendar calendar = Calendar.getInstance();
				calendar.setTime(new Date(readingPlan.info.startTime));
				calendar.add(Calendar.DATE, dayNumber);

				DatePickerDialog.OnDateSetListener dateSetListener = new DatePickerDialog.OnDateSetListener() {
			        @Override
			        public void onDateSet(final DatePicker view, final int year, final int monthOfYear, final int dayOfMonth) {
				        Calendar newCalendar = new GregorianCalendar(year, monthOfYear, dayOfMonth);
				        Calendar startCalendar = GregorianCalendar.getInstance();
				        startCalendar.setTime(new Date(readingPlan.info.startTime));

				        int newDay = calculateDaysDiff(startCalendar, newCalendar);
				        if (newDay < 0) {
					        newDay = 0;
				        } else if (newDay >= readingPlan.info.duration) {
					        newDay = readingPlan.info.duration - 1;
				        }
				        dayNumber = newDay;
				        changeDay(0);
			        }
		        };

				DatePickerDialog datePickerDialog = new DatePickerDialog(ReadingPlanActivity.this, dateSetListener, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
				datePickerDialog.show();
			}
		});

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

	private void resetReadingPlan() {
		new AlertDialog.Builder(this)
		.setMessage(getString(R.string.rp_reset))
		.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(final DialogInterface dialog, final int which) {
				int firstUnreadDay = findFirstUnreadDay();
				Calendar calendar = GregorianCalendar.getInstance();
				calendar.add(Calendar.DATE, -firstUnreadDay);
				S.getDb().updateStartDate(readingPlan.info.id, calendar.getTime().getTime());
				loadReadingPlan(readingPlan.info.id);
				loadDayNumber(true);
				readingPlanAdapter.load();
				readingPlanAdapter.notifyDataSetChanged();

				updateButtonStatus();
			}
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
		new AlertDialog.Builder(this)
		.setMessage(getString(R.string.rp_deletePlan, readingPlan.info.title))
		.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(final DialogInterface dialog, final int which) {
				S.getDb().deleteReadingPlanById(readingPlan.info.id);
				readingPlan = null;
				Preferences.remove(Prefkey.active_reading_plan_id);
				loadReadingPlan(0);
				loadReadingPlanProgress();
				loadDayNumber(true);
				prepareDropDownNavigation();
				prepareDisplay();
				setReadingPlanMenuVisibility();
			}
		})
		.setNegativeButton(R.string.cancel, null)
		.show();
	}

	private void showAbout() {
		String message = getString(R.string.rp_aboutPlanMessage, readingPlan.info.title, readingPlan.info.description, readingPlan.info.duration);

		new AlertDialog.Builder(ReadingPlanActivity.this)
		.setMessage(message)
		.setPositiveButton(R.string.ok, null)
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

	class ReadingPlanServerEntry {
		public String name;
		public String title;
		public String description;
		public int day_count;
	}

	private void downloadReadingPlanList() {
		final AtomicBoolean cancelled = new AtomicBoolean(false);
		final ProgressDialog pd = ProgressDialog.show(this, null, getString(R.string.rp_download_reading_plan_list_progress), true, true);
		pd.setOnDismissListener(new DialogInterface.OnDismissListener() {
			@Override
			public void onDismiss(final DialogInterface dialog) {
				cancelled.set(true);
			}
		});

		new Thread() {
			@Override
			public void run() {
				try {
					download();
				} catch (Exception e) {
					Log.e(TAG, "downloading reading plan list", e);
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							new AlertDialog.Builder(ReadingPlanActivity.this)
							.setMessage(getString(R.string.rp_download_reading_plan_list_failed))
							.setPositiveButton(R.string.ok, null)
							.show();
						}
					});
				} finally {
					pd.dismiss();
				}
			}

			/** run on bg thread */
			void download() throws Exception {
				final OkHttpClient client = new OkHttpClient();
				final HttpURLConnection conn = client.open(new URL("http://alkitab-host.appspot.com/rp/list"));
				final ReadingPlanServerEntry[] entries = new GsonBuilder().create().fromJson(new InputStreamReader(conn.getInputStream(), "utf-8"), ReadingPlanServerEntry[].class);

				if (entries == null) return;
				if (cancelled.get()) return;
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						onReadingPlanListDownloadFinished(entries);
					}
				});
			}

			/** run on ui thread */
			void onReadingPlanListDownloadFinished(final ReadingPlanServerEntry[] entries) {
				final List<ReadingPlanServerEntry> readingPlanDownloadableEntries = new ArrayList<ReadingPlanServerEntry>();
				final List<String> downloadedNames = S.getDb().listReadingPlanNames();
				for (final ReadingPlanServerEntry entry : entries) {
					if (!downloadedNames.contains(entry.name)) {
						readingPlanDownloadableEntries.add(entry);
					}
				}

				if (readingPlanDownloadableEntries.size() == 0) {
					new AlertDialog.Builder(ReadingPlanActivity.this)
					.setMessage(getString(R.string.rp_noReadingPlanAvailable))
					.setPositiveButton(R.string.ok, null)
					.show();
					return;
				}

				final AlertDialog.Builder builder = new AlertDialog.Builder(ReadingPlanActivity.this);
				builder.setAdapter(new EasyAdapter() {
					@Override
					public View newView(final int position, final ViewGroup parent) {
						return getLayoutInflater().inflate(android.R.layout.simple_list_item_1, parent, false);
					}

					@Override
					public void bindView(final View view, final int position, final ViewGroup parent) {
						final TextView textView = (TextView) view;
						final ReadingPlanServerEntry entry = readingPlanDownloadableEntries.get(position);
						final SpannableStringBuilder sb = new SpannableStringBuilder();
						sb.append(entry.title);
						final int sb_len = sb.length();
						sb.append(" ");
						sb.append(getString(R.string.rp_download_day_count_days, entry.day_count));
						sb.append("\n");
						sb.append(entry.description);
						sb.setSpan(new RelativeSizeSpan(0.6f), sb_len, sb.length(), 0);
						textView.setText(sb);
					}

					@Override
					public int getCount() {
						return readingPlanDownloadableEntries.size();
					}
				}, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(final DialogInterface dialog, final int which) {
						onReadingPlanSelected(readingPlanDownloadableEntries.get(which));
					}
				})
				.setNegativeButton(R.string.cancel, null)
				.show();
			}

			/** run on ui thread */
			void onReadingPlanSelected(final ReadingPlanServerEntry entry) {
				downloadReadingPlanFromServer(entry);
			}
		}.start();
	}

	void downloadReadingPlanFromServer(final ReadingPlanServerEntry entry) {
		final AtomicBoolean cancelled = new AtomicBoolean(false);
		final ProgressDialog pd = ProgressDialog.show(this, null, getString(R.string.rp_download_reading_plan_progress), true, true);
		pd.setOnDismissListener(new DialogInterface.OnDismissListener() {
			@Override
			public void onDismiss(final DialogInterface dialog) {
				cancelled.set(true);
			}
		});

		new Thread() {
			@Override
			public void run() {
				try {
					download();
				} catch (Exception e) {
					Log.e(TAG, "downloading reading plan data", e);
					new AlertDialog.Builder(ReadingPlanActivity.this)
					.setMessage(getString(R.string.rp_download_reading_plan_failed))
					.setPositiveButton(R.string.ok, null)
					.show();
				} finally {
					pd.dismiss();
				}
			}

			/** run on bg thread */
			void download() throws Exception {
				final OkHttpClient client = new OkHttpClient();
				final HttpURLConnection conn = client.open(new URL("http://alkitab-host.appspot.com/rp/get_rp?name=" + entry.name));
				final InputStream input = conn.getInputStream();
				final ByteArrayOutputStream baos = new ByteArrayOutputStream();
				final byte[] buf = new byte[1024];
				while (true) {
					final int read = input.read(buf);
					if (read < 0) break;
					baos.write(buf, 0, read);
				}

				if (cancelled.get()) return;
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						onReadingPlanDownloadFinished(baos.toByteArray());
					}
				});
			}

			/** run on ui thread */
			void onReadingPlanDownloadFinished(final byte[] data) {
				final long id = ReadingPlanManager.insertReadingPlanToDb(data);

				if (id == 0) {
					new AlertDialog.Builder(ReadingPlanActivity.this)
					.setMessage(getString(R.string.rp_download_reading_plan_data_corrupted))
					.setPositiveButton(R.string.ok, null)
					.show();
					return;
				}

				Preferences.setLong(Prefkey.active_reading_plan_id, id);
				loadReadingPlan(id);
				loadReadingPlanProgress();
				loadDayNumber(true);
				prepareDropDownNavigation();
				prepareDisplay();
				setReadingPlanMenuVisibility();
			}
		}.start();
	}

	private float getActualPercentage() {
		float res = 100.f * countRead() / countAllReadings();
		return res;
	}

	private float getTargetPercentage() {
		float res = 100.f * countTarget() / countAllReadings();
		return res;
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
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(new Date(readingPlan.info.startTime));
		calendar.add(Calendar.DATE, dayNumber);

		String date = getString(R.string.rp_dayHeader, (dayNumber + 1), Sqlitil.toLocaleDateMedium(calendar.getTime()));
		return date;
	}

	public static StringBuilder getReference(final Version version, final int ari_start, final int ari_end) {
		final StringBuilder sb = new StringBuilder();
		sb.append(version.reference(ari_start));
		int startChapter = Ari.toChapter(ari_start);
		int startVerse = Ari.toVerse(ari_start);
		int lastVerse = Ari.toVerse(ari_end);
		int lastChapter = Ari.toChapter(ari_end);

		if (startVerse == 0) {
			if (lastVerse == 0) {
				if (startChapter != lastChapter) {
					sb.append("-").append(lastChapter);
				}
			} else {
				sb.append("-").append(lastChapter).append(":").append(lastVerse);
			}
		} else {
			if (startChapter == lastChapter) {
				sb.append("-").append(lastVerse);
			} else {
				sb.append("-").append(lastChapter).append(":").append(lastVerse);
			}
		}

		return sb;
	}

	class ReadingPlanAdapter extends BaseAdapter {

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
		public View getView(final int position, View convertView, final ViewGroup parent) {
			final int itemViewType = getItemViewType(position);
			final View res;
			if (itemViewType == 0) {
				res = convertView != null? convertView: getLayoutInflater().inflate(R.layout.item_reading_plan_one_reading, parent, false);
				final CheckBox checkbox = V.get(res, R.id.checkbox);

				final boolean[] readMarks = new boolean[todayReadings.length / 2];
				ReadingPlanManager.writeReadMarksByDay(readingCodes, readMarks, dayNumber);

				checkbox.setOnCheckedChangeListener(null);
				checkbox.setChecked(readMarks[position]);
				checkbox.setText(getReference(S.activeVersion, todayReadings[position * 2], todayReadings[position * 2 + 1]));

				checkbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
						ReadingPlanManager.updateReadingPlanProgress(readingPlan.info.id, dayNumber, position, isChecked);
						loadReadingPlanProgress();
						load();
						notifyDataSetChanged();
					}
				});

			} else if (itemViewType == 1) {
				res = convertView != null? convertView: getLayoutInflater().inflate(R.layout.item_reading_plan_summary, parent, false);

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
					String diff = String.format("%.2f", targetPercentage - actualPercentage);
					comment = getString(R.string.rp_commentBehindSchedule, diff);
				}

				tComment.setText(comment);

				tDetail.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(final View v) {
						showDetails = !showDetails;
						if (showDetails) {
							tDetail.setText(R.string.rp_hideDetails);
						} else {
							tDetail.setText(R.string.rp_showDetails);
						}
						notifyDataSetChanged();
					}
				});

			} else if (itemViewType == 2) {
				res = convertView != null? convertView: getLayoutInflater().inflate(R.layout.item_reading_plan_one_day, parent, false);

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
					    checkBox = new CheckBox(ReadingPlanActivity.this);
						checkBox.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
						checkBox.setTag(i);
						checkBox.setFocusable(false);
						layout.addView(checkBox);
					}

					checkBox.setOnCheckedChangeListener(null);
					checkBox.setChecked(readMarks[sequence]);
					checkBox.setText(getReference(S.activeVersion, ariRanges[sequence * 2], ariRanges[sequence * 2 + 1]));
					checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
						@Override
						public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
							ReadingPlanManager.updateReadingPlanProgress(readingPlan.info.id, currentViewTypePosition, sequence, isChecked);
							loadReadingPlanProgress();
							load();
							notifyDataSetChanged();
						}
					});
				}
			} else {
				res = null;
			}

			return res;
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

}