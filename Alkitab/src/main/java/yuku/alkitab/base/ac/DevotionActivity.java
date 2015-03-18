package yuku.alkitab.base.ac;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v4.app.ShareCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.text.format.DateFormat;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.afollestad.materialdialogs.AlertDialogWrapper;
import com.google.android.gms.analytics.HitBuilders;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.base.BaseLeftDrawerActivity;
import yuku.alkitab.base.devotion.ArticleMeidA;
import yuku.alkitab.base.devotion.ArticleMorningEveningEnglish;
import yuku.alkitab.base.devotion.ArticleRenunganHarian;
import yuku.alkitab.base.devotion.ArticleSantapanHarian;
import yuku.alkitab.base.devotion.DevotionArticle;
import yuku.alkitab.base.devotion.DevotionDownloader;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.Jumper;
import yuku.alkitab.base.widget.CallbackSpan;
import yuku.alkitab.base.widget.LeftDrawer;
import yuku.alkitab.base.widget.TwofingerLinearLayout;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.debug.R;
import yuku.alkitab.reminder.ac.DevotionReminderActivity;
import yuku.alkitab.util.Ari;
import yuku.alkitabintegration.display.Launcher;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DevotionActivity extends BaseLeftDrawerActivity implements DevotionDownloader.DownloadStatusListener, LeftDrawer.Devotion.Listener {
	public static final String TAG = DevotionActivity.class.getSimpleName();

	private static final int REQCODE_share = 1;

	static final ThreadLocal<SimpleDateFormat> yyyymmdd = new ThreadLocal<SimpleDateFormat>() {
		@Override protected SimpleDateFormat initialValue() {
			return new SimpleDateFormat("yyyyMMdd", Locale.US); //$NON-NLS-1$
		}
	};

	TwofingerLinearLayout.Listener devotion_root_listener = new TwofingerLinearLayout.OnefingerListener() {
		@Override
		public void onOnefingerLeft() {
			bNext_click();
		}

		@Override
		public void onOnefingerRight() {
			bPrev_click();
		}
	};

	public static Intent createIntent() {
		return new Intent(App.context, DevotionActivity.class);
	}

	@Override
	public void bPrev_click() {
		currentDate.setTime(currentDate.getTime() - 3600*24*1000);
		display();
	}

	@Override
	public void bNext_click() {
		currentDate.setTime(currentDate.getTime() + 3600*24*1000);
		display();
	}

	@Override
	public void bReload_click() {
		willNeed(this.currentKind, yyyymmdd.get().format(currentDate), true);
	}

	@Override
	public void cbKind_itemSelected(final DevotionKind kind) {
		currentKind = kind;
		Preferences.setString(Prefkey.devotion_last_kind_name, currentKind.name);
		display();
	}

	@Override
	protected LeftDrawer getLeftDrawer() {
		return leftDrawer;
	}

	public enum DevotionKind {
		SH("sh", "Santapan Harian", "Persekutuan Pembaca Alkitab") {
			@Override
			public DevotionArticle getArticle(final String date) {
				return new ArticleSantapanHarian(date);
			}

			@Override
			public String getShareUrl(final SimpleDateFormat format, final Date date) {
				return "http://www.sabda.org/publikasi/e-sh/print/?edisi=" + yyyymmdd.get().format(date);
			}
		},
		MEID_A("meid-a", "Renungan Pagi", "Charles H. Spurgeon") {
			@Override
			public DevotionArticle getArticle(final String date) {
				return new ArticleMeidA(date);
			}

			@Override
			public String getShareUrl(final SimpleDateFormat format, final Date date) {
				return "http://www.bibleforandroid.com/renunganpagi/" + yyyymmdd.get().format(date).substring(4);
			}
		},
		RH("rh", "Renungan Harian", "Yayasan Gloria") {
			@Override
			public DevotionArticle getArticle(final String date) {
				return new ArticleRenunganHarian(date);
			}

			@Override
			public String getShareUrl(final SimpleDateFormat format, final Date date) {
				return "http://www.sabda.org/publikasi/e-rh/print/?edisi=" + yyyymmdd.get().format(date);
			}
		},
		ME_EN("me-en", "Morning & Evening", "Charles H. Spurgeon") {
			@Override
			public DevotionArticle getArticle(final String date) {
				return new ArticleMorningEveningEnglish(date);
			}

			@Override
			public String getShareUrl(final SimpleDateFormat format, final Date date) {
				return "http://www.ccel.org/ccel/spurgeon/morneve.d" + yyyymmdd.get().format(date) + "am.html";
			}
		},
		;

		public final String name;
		public final String title;
		public final String subtitle;

		DevotionKind(final String name, final String title, final String subtitle) {
			this.name = name;
			this.title = title;
			this.subtitle = subtitle;
		}

		public static DevotionKind getByName(String name) {
			if (name == null) return null;
			for (final DevotionKind kind : values()) {
				if (name.equals(kind.name)) {
					return kind;
				}
			}
			return null;
		}

		public abstract DevotionArticle getArticle(final String date);

		public abstract String getShareUrl(SimpleDateFormat format, Date date);
	}

	public static final DevotionKind DEFAULT_DEVOTION_KIND = DevotionKind.SH;

	DrawerLayout drawerLayout;
	ActionBarDrawerToggle drawerToggle;
	LeftDrawer.Devotion leftDrawer;

	TwofingerLinearLayout devotion_root;
	TextView lContent;
	ScrollView scrollContent;
	TextView lStatus;
	
	boolean renderSucceeded = false;
	long lastTryToDisplay = 0;

	// currently shown
	DevotionKind currentKind;
	Date currentDate;

	static class DisplayRepeater extends Handler {
		final WeakReference<DevotionActivity> ac;
		
		public DisplayRepeater(DevotionActivity activity) {
			ac = new WeakReference<>(activity);
		}
		
		@Override public void handleMessage(Message msg) {
			DevotionActivity activity = ac.get();
			if (activity == null) return;
			
			{
				long now = SystemClock.currentThreadTimeMillis();
				if (now - activity.lastTryToDisplay < 500) {
					return; // ANEH. Terlalu cepat.
				}
				
				activity.lastTryToDisplay = now;
			}
			
			activity.goTo();
			
			if (!activity.renderSucceeded) {
				activity.displayRepeater.sendEmptyMessageDelayed(0, 12000);
			}
		}
	}

	final Handler displayRepeater = new DisplayRepeater(this);

	static class LongReadChecker extends Handler {
		DevotionKind startKind;
		String startDate;

		final WeakReference<DevotionActivity> ac;

		public LongReadChecker(DevotionActivity activity) {
			ac = new WeakReference<>(activity);
		}

		/** This will be called 30 seconds after startKind and startDate are set. */
		@Override
		public void handleMessage(final Message msg) {
			final DevotionActivity ac = this.ac.get();
			if (ac == null) return;
			if (ac.isFinishing()) {
				Log.d(TAG, "Activity is already closed");
				return;
			}

			final String currentDate = yyyymmdd.get().format(ac.currentDate);
			if (U.equals(startKind, ac.currentKind) && U.equals(startDate, currentDate)) {
				Log.d(TAG, "Long read detected: now=[" + ac.currentKind + " " + currentDate + "]");
				App.getTracker().send(new HitBuilders.EventBuilder("devotion-longread", startKind.name).setLabel(startDate).setValue(30L).build());
			} else {
				Log.d(TAG, "Not long enough for long read: previous=[" + startKind + " " + startDate + "] now=[" + ac.currentKind + " " + currentDate + "]");
			}
		}

		public void start() {
			final DevotionActivity ac = this.ac.get();
			if (ac == null) return;

			startKind = ac.currentKind;
			startDate = yyyymmdd.get().format(ac.currentDate);

			removeMessages(1);
			sendEmptyMessageDelayed(1, BuildConfig.DEBUG ? 10000 : 30000);
		}
	}

	final LongReadChecker longReadChecker = new LongReadChecker(this);

	static class DownloadStatusDisplayer extends Handler {
		private WeakReference<DevotionActivity> ac;
		static int MSG_SHOW = 1;
		static int MSG_HIDE = 2;

		public DownloadStatusDisplayer(DevotionActivity ac) {
			this.ac = new WeakReference<>(ac);
		}
		
		@Override public void handleMessage(Message msg) {
			final DevotionActivity ac = this.ac.get();
			if (ac == null) return;

			if (msg.what == MSG_SHOW) {
				final String s = (String) msg.obj;
				if (s != null) {
					ac.lStatus.setText(s);
					ac.lStatus.setVisibility(View.VISIBLE);

					removeMessages(MSG_HIDE);
					sendEmptyMessageDelayed(MSG_HIDE, 2000);
				}
			} else if (msg.what == MSG_HIDE) {
				ac.lStatus.setVisibility(View.GONE);
			}
		}
	}
	
	Handler downloadStatusDisplayer = new DownloadStatusDisplayer(this);

	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_devotion);

		drawerLayout = V.get(this, R.id.drawerLayout);
		leftDrawer = V.get(this, R.id.left_drawer);
		leftDrawer.configure(this, drawerLayout);

		final Toolbar toolbar = V.get(this, R.id.toolbar);
		setSupportActionBar(toolbar);

		drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.drawer_open, R.string.drawer_close);
		drawerLayout.setDrawerListener(drawerToggle);

		final ActionBar actionBar = getSupportActionBar();
		actionBar.setDisplayShowHomeEnabled(Build.VERSION.SDK_INT < 18);
		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setHomeButtonEnabled(true);

		devotion_root = V.get(this, R.id.devotion_root);
		lContent = V.get(this, R.id.lContent);
		scrollContent = V.get(this, R.id.scrollContent);
		lStatus = V.get(this, R.id.lStatus);

		devotion_root.setTwofingerEnabled(false);
		devotion_root.setListener(devotion_root_listener);

		final DevotionKind storedKind = DevotionKind.getByName(Preferences.getString(Prefkey.devotion_last_kind_name, DEFAULT_DEVOTION_KIND.name));

		currentKind = storedKind == null? DEFAULT_DEVOTION_KIND: storedKind;
		currentDate = new Date();

		// Workaround for crashes due to html tags in the title
		// We remove all rows that contain '<' in the title
		if (!Preferences.getBoolean(Prefkey.patch_devotionSlippedHtmlTags, false)) {
			int deleted = S.getDb().deleteDevotionsWithLessThanInTitle();
			Log.d(TAG, "patch_devotionSlippedHtmlTags: deleted " + deleted);
			Preferences.setBoolean(Prefkey.patch_devotionSlippedHtmlTags, true);
		}
		
		new Prefetcher(currentKind).start();
		
		{ // fix  ui update
			if (devotionDownloader != null) {
				devotionDownloader.setListener(this);
			}
		}
		
		display();
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

	@Override protected void onStart() {
		super.onStart();

		{ // apply background color, and clear window background to prevent overdraw
			getWindow().setBackgroundDrawableResource(android.R.color.transparent);
			scrollContent.setBackgroundColor(S.applied.backgroundColor);
		}

		// text formats
		lContent.setTextColor(S.applied.fontColor);
		lContent.setTypeface(S.applied.fontFace, S.applied.fontBold);
		lContent.setTextSize(TypedValue.COMPLEX_UNIT_DIP, S.applied.fontSize2dp);
		lContent.setLineSpacing(0, S.applied.lineSpacingMult);

		SettingsActivity.setPaddingBasedOnPreferences(lContent);

		getWindow().getDecorView().setKeepScreenOn(Preferences.getBoolean(getString(R.string.pref_keepScreenOn_key), getResources().getBoolean(R.bool.pref_keepScreenOn_default)));
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_devotion, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (drawerToggle.onOptionsItemSelected(item)) {
			return true;
		}

		final int itemId = item.getItemId();
		if (itemId == R.id.menuCopy) {
			U.copyToClipboard(currentKind.title + "\n" + lContent.getText());
			
			Toast.makeText(this, R.string.renungan_sudah_disalin, Toast.LENGTH_SHORT).show();
			
			return true;
		} else if (itemId == R.id.menuShare) {
			Intent intent = ShareCompat.IntentBuilder.from(DevotionActivity.this)
				.setType("text/plain")
				.setSubject(currentKind.title)
				.setText(currentKind.title + '\n' + getCurrentDateDisplay() + '\n' + currentKind.getShareUrl(yyyymmdd.get(), currentDate) + "\n\n" + lContent.getText())
				.getIntent();
			startActivityForResult(ShareActivity.createIntent(intent, getString(R.string.bagikan_renungan)), REQCODE_share);
			return true;
		} else if (itemId == R.id.menuReminder) {
			startActivity(DevotionReminderActivity.createIntent());
			return true;
		}
		
		return super.onOptionsItemSelected(item);
	}

	void display() {
		displayRepeater.removeMessages(0);
		
		goTo();
	}

	void goTo() {
		String date = yyyymmdd.get().format(currentDate);
		DevotionArticle article = S.getDb().tryGetDevotion(currentKind.name, date);
		if (article == null || !article.getReadyToUse()) {
			willNeed(currentKind, date, true);
			displayRepeater.sendEmptyMessageDelayed(0, 3000);
		} else {
			Log.d(TAG, "sudah siap tampil, kita syuh yang tersisa dari pengulang tampil"); //$NON-NLS-1$
			displayRepeater.removeMessages(0);
		}

		if (article == null) {
			Log.d(TAG, "rendering null article"); //$NON-NLS-1$
		} else {
			Log.d(TAG, "rendering article name=" + article.getKind().name + " date=" + article.getDate() + " readyToUse=" + article.getReadyToUse());
		}

		if (article != null && article.getReadyToUse()) {
			renderSucceeded = true;

			lContent.setText(article.getContent(verseClickListener), TextView.BufferType.SPANNABLE);
			lContent.setLinksClickable(true);
			lContent.setMovementMethod(LinkMovementMethod.getInstance());
		} else {
			renderSucceeded  = false;

			if (article == null) {
				lContent.setText(R.string.belum_tersedia_menunggu_pengambilan_data_lewat_internet_pastikan_ada);
			} else { // berarti belum siap pakai
				lContent.setText(R.string.belum_tersedia_mungkin_tanggal_yang_diminta_belum_disiapkan);
			}
		}

		{ // widget texts
			final String dateDisplay = getCurrentDateDisplay();

			// action bar
			final ActionBar actionBar = getSupportActionBar();
			if (actionBar != null) {
				actionBar.setTitle(currentKind.title);
				actionBar.setSubtitle(dateDisplay);
			}

			// drawer texts
			final LeftDrawer.Devotion.Handle handle = leftDrawer.getHandle();
			handle.setDevotionKind(currentKind);
			handle.setDevotionDate(dateDisplay);
		}

		if (renderSucceeded) {
			App.getTracker().send(new HitBuilders.EventBuilder("devotion-render", currentKind.name).setLabel(yyyymmdd.get().format(currentDate)).setValue(0L).build());
			longReadChecker.start();
		}
	}

	private String getCurrentDateDisplay() {
		return dayOfWeekName(currentDate) + ", " + DateFormat.getDateFormat(this).format(currentDate);
	}

	static class PatchTextExtraInfoJson {
		String type;
		String kind;
		String date;
	}

	CallbackSpan.OnClickListener<String> verseClickListener = new CallbackSpan.OnClickListener<String>() {
		@Override
		public void onClick(View widget, String reference) {
			Log.d(TAG, "Clicked verse reference inside devotion: " + reference); //$NON-NLS-1$

			if (reference.startsWith("patchtext:")) {
				final Uri uri = Uri.parse(reference);
				final String referenceUrl = uri.getQueryParameter("referenceUrl");

				final PatchTextExtraInfoJson extraInfo = new PatchTextExtraInfoJson();
				extraInfo.type = "devotion";
				extraInfo.kind = currentKind.name;
				extraInfo.date = yyyymmdd.get().format(currentDate);
				startActivity(PatchTextActivity.createIntent(lContent.getText(), App.getDefaultGson().toJson(extraInfo), referenceUrl));
			} else {
				int ari;
				if (reference.startsWith("ari:")) {
					ari = Integer.parseInt(reference.substring(4));
					startActivity(Launcher.openAppAtBibleLocationWithVerseSelected(ari));

				} else { // we need to parse it manually by text
					final Jumper jumper = new Jumper(reference);
					if (!jumper.getParseSucceeded()) {
						new AlertDialogWrapper.Builder(DevotionActivity.this)
							.setMessage(getString(R.string.alamat_tidak_sah_alamat, reference))
							.setPositiveButton(R.string.ok, null)
							.show();
						return;
					}

					// Make sure references are parsed using Indonesian book names.
					String[] bookNames = getResources().getStringArray(R.array.standard_book_names_in);
					int[] bookIds = new int[bookNames.length];
					for (int i = 0, len = bookNames.length; i < len; i++) {
						bookIds[i] = i;
					}

					final int bookId = jumper.getBookId(bookNames, bookIds);
					final int chapter_1 = jumper.getChapter();
					final int verse_1 = jumper.getVerse();
					ari = Ari.encode(bookId, chapter_1, verse_1);

					final boolean hasRange = jumper.getHasRange();
					if (hasRange) {
						startActivity(Launcher.openAppAtBibleLocation(ari));
					} else {
						startActivity(Launcher.openAppAtBibleLocationWithVerseSelected(ari));
					}
				}
			}
		}
	};

	private static final int[] WEEKDAY_NAMES_RESIDS = {R.string.hari_minggu, R.string.hari_senin, R.string.hari_selasa, R.string.hari_rabu, R.string.hari_kamis, R.string.hari_jumat, R.string.hari_sabtu};

	private String dayOfWeekName(Date date) {
		@SuppressWarnings("deprecation") int day = date.getDay();
		return getString(WEEKDAY_NAMES_RESIDS[day]);
	}

	synchronized void willNeed(DevotionKind kind, String date, boolean prioritize) {
		if (devotionDownloader == null) {
			devotionDownloader = new DevotionDownloader(this, this);
			devotionDownloader.start();
		}

		final DevotionArticle article = kind.getArticle(date);
		boolean added = devotionDownloader.add(article, prioritize);
		if (added) devotionDownloader.interruptWhenIdle();
	}
	
	static boolean prefetcherRunning = false;
	
	class Prefetcher extends Thread {
		private final DevotionKind prefetchKind;

		public Prefetcher(final DevotionKind kind) {
			prefetchKind = kind;
		}

		@Override public void run() {
			if (prefetcherRunning) {
				Log.d(TAG, "prefetcher is now running"); //$NON-NLS-1$
			}
			
			// diem dulu 6 detik
			SystemClock.sleep(6000);
			
			Date today = new Date();
			
			// hapus yang sudah lebih lama dari 6 bulan (180 hari)!
			int deleted = S.getDb().deleteDevotionsWithTouchTimeBefore(new Date(today.getTime() - 180 * 86400000L));
			if (deleted > 0) {
				Log.d(TAG, "old devotions deleted: " + deleted); //$NON-NLS-1$
			}
			
			prefetcherRunning = true;
			try {
				int DAYS = 31;
				if (prefetchKind == DevotionKind.RH) {
					DAYS = 3;
				}

				for (int i = 0; i < DAYS; i++) {
					String date = yyyymmdd.get().format(today);
					if (S.getDb().tryGetDevotion(prefetchKind.name, date) == null) {
						Log.d(TAG, "Prefetcher need to get " + date); //$NON-NLS-1$
						willNeed(prefetchKind, date, false);
						
						SystemClock.sleep(1000);
					} else {
						SystemClock.sleep(100); // biar ga berbeban aja
					}
					
					// maju ke besoknya
					today.setTime(today.getTime() + 3600*24*1000);
				}
			} finally {
				prefetcherRunning = false;
			}
		}
	}

	public static DevotionDownloader devotionDownloader;

	@Override public void onDownloadStatus(final String s) {
		final Message msg = Message.obtain(downloadStatusDisplayer, DownloadStatusDisplayer.MSG_SHOW);
		msg.obj = s;
		msg.sendToTarget();
	}
	
	@Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQCODE_share) {
			if (resultCode == RESULT_OK) {
				ShareActivity.Result result = ShareActivity.obtainResult(data);
				if (result != null && result.chosenIntent != null) {
					Intent chosenIntent = result.chosenIntent;
					if (U.equals(chosenIntent.getComponent().getPackageName(), "com.facebook.katana")) {
						chosenIntent.putExtra(Intent.EXTRA_TEXT, currentKind.getShareUrl(yyyymmdd.get(), currentDate));
					}
					startActivity(chosenIntent);
				}
			}
		}
	}
}
