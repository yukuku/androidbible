package yuku.alkitab.base.ac;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.app.ShareCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.NestedScrollView;
import android.support.v7.app.ActionBar;
import android.support.v7.app.NotificationCompat;
import android.support.v7.widget.Toolbar;
import android.text.format.DateFormat;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.gms.analytics.HitBuilders;
import yuku.afw.V;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.base.BaseLeftDrawerActivity;
import yuku.alkitab.base.devotion.ArticleMeidA;
import yuku.alkitab.base.devotion.ArticleMorningEveningEnglish;
import yuku.alkitab.base.devotion.ArticleRefheart;
import yuku.alkitab.base.devotion.ArticleRenunganHarian;
import yuku.alkitab.base.devotion.ArticleRoc;
import yuku.alkitab.base.devotion.ArticleSantapanHarian;
import yuku.alkitab.base.devotion.DevotionArticle;
import yuku.alkitab.base.devotion.DevotionDownloader;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.Background;
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

public class DevotionActivity extends BaseLeftDrawerActivity implements LeftDrawer.Devotion.Listener {
	public static final String TAG = DevotionActivity.class.getSimpleName();

	private static final int REQCODE_share = 1;
	public static final DevotionDownloader devotionDownloader = new DevotionDownloader();

	static final ThreadLocal<SimpleDateFormat> yyyymmdd = new ThreadLocal<SimpleDateFormat>() {
		@Override
		protected SimpleDateFormat initialValue() {
			return new SimpleDateFormat("yyyyMMdd", Locale.US);
		}
	};

	TwofingerLinearLayout.Listener root_listener = new TwofingerLinearLayout.OnefingerListener() {
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
		currentDate.setTime(currentDate.getTime() - 3600 * 24 * 1000);
		display();
	}

	@Override
	public void bNext_click() {
		currentDate.setTime(currentDate.getTime() + 3600 * 24 * 1000);
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

		Background.run(() -> prefetch(currentKind));

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
		REFHEART("refheart", "Reforming Heart", "STEMI Pemuda") {
			@Override
			public DevotionArticle getArticle(final String date) {
				return new ArticleRefheart(date);
			}

			@Override
			public String getShareUrl(final SimpleDateFormat format, final Date date) {
				return null; // TODO create redirect url
			}

			@Override
			public int getPrefetchDays() {
				return 1;
			}
		},
		ROC("roc", "My Utmost (B. Indonesia)", "Oswald Chambers") {
			@Override
			public DevotionArticle getArticle(final String date) {
				return new ArticleRoc(date);
			}

			@Override
			public String getShareUrl(final SimpleDateFormat format, final Date date) {
				return null;
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

			@Override
			public int getPrefetchDays() {
				return 3;
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
		},;

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

		@Nullable
		public abstract String getShareUrl(SimpleDateFormat format, Date date);

		public int getPrefetchDays() {
			return 31;
		}
	}

	public static final DevotionKind DEFAULT_DEVOTION_KIND = DevotionKind.SH;

	DrawerLayout drawerLayout;
	LeftDrawer.Devotion leftDrawer;

	TwofingerLinearLayout root;
	TextView lContent;
	NestedScrollView scrollContent;

	boolean renderSucceeded = false;

	// currently shown
	DevotionKind currentKind;
	Date currentDate;

	static class LongReadChecker extends Handler {
		DevotionKind startKind;
		String startDate;

		final WeakReference<DevotionActivity> ac;

		public LongReadChecker(DevotionActivity activity) {
			ac = new WeakReference<>(activity);
		}

		/**
		 * This will be called 30 seconds after startKind and startDate are set.
		 */
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

	final BroadcastReceiver br = new BroadcastReceiver() {
		private static final String NOTIFY_TAG = "devotion_downloader";
		private static final int NOTIFY_ID = 0;

		NotificationManagerCompat nm;

		@Override
		public void onReceive(final Context context, final Intent intent) {
			if (nm == null) {
				nm = NotificationManagerCompat.from(context);
			}

			final String action = intent.getAction();
			if (DevotionDownloader.ACTION_DOWNLOAD_STATUS.equals(action)) {
				final CharSequence title = intent.getCharSequenceExtra("title");
				final CharSequence subtitle = intent.getCharSequenceExtra("subtitle");

				final Notification n = new NotificationCompat.Builder(DevotionActivity.this)
					.setContentTitle(title)
					.setContentText(subtitle)
					.setProgress(0, 0, true)
					.setSmallIcon(android.R.drawable.stat_sys_download)
					.setStyle(new NotificationCompat.BigTextStyle()
						.bigText(subtitle)
					)
					.build();

				nm.notify(NOTIFY_TAG, NOTIFY_ID, n);

			} else if (DevotionDownloader.ACTION_DOWNLOADED.equals(action)) {
				// is it for us?
				final String name = intent.getStringExtra("name");
				final String date = intent.getStringExtra("date");

				if (yyyymmdd.get().format(currentDate).equals(date) && currentKind.name.equals(name)) {
					display();
				}
			} else if (DevotionDownloader.ACTION_QUEUE_FINISHED.equals(action)) {
				nm.cancel(NOTIFY_TAG, NOTIFY_ID);
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_devotion);

		drawerLayout = V.get(this, R.id.drawerLayout);
		leftDrawer = V.get(this, R.id.left_drawer);
		leftDrawer.configure(this, drawerLayout);

		final Toolbar toolbar = V.get(this, R.id.toolbar);
		setSupportActionBar(toolbar);

		final ActionBar actionBar = getSupportActionBar();
		assert actionBar != null;
		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp);

		root = V.get(this, R.id.root);
		lContent = V.get(this, R.id.lContent);
		scrollContent = V.get(this, R.id.scrollContent);

		root.setTwofingerEnabled(false);
		root.setListener(root_listener);

		final DevotionKind storedKind = DevotionKind.getByName(Preferences.getString(Prefkey.devotion_last_kind_name, DEFAULT_DEVOTION_KIND.name));

		currentKind = storedKind == null ? DEFAULT_DEVOTION_KIND : storedKind;
		currentDate = new Date();

		Background.run(() -> prefetch(currentKind));

		display();
	}

	@Override
	protected void onStart() {
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

		App.getLbm().registerReceiver(br, new IntentFilter(DevotionDownloader.ACTION_DOWNLOAD_STATUS));
		App.getLbm().registerReceiver(br, new IntentFilter(DevotionDownloader.ACTION_DOWNLOADED));
		App.getLbm().registerReceiver(br, new IntentFilter(DevotionDownloader.ACTION_QUEUE_FINISHED));
	}

	@Override
	protected void onStop() {
		super.onStop();

		App.getLbm().unregisterReceiver(br);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_devotion, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		final int itemId = item.getItemId();
		if (itemId == android.R.id.home) {
			leftDrawer.toggleDrawer();
			return true;
		} else if (itemId == R.id.menuCopy) {
			U.copyToClipboard(currentKind.title + "\n" + lContent.getText());

			Snackbar.make(root, R.string.renungan_sudah_disalin, Snackbar.LENGTH_SHORT).show();

			return true;
		} else if (itemId == R.id.menuShare) {
			final String shareUrl = currentKind.getShareUrl(yyyymmdd.get(), currentDate);

			final Intent intent = ShareCompat.IntentBuilder.from(DevotionActivity.this)
				.setType("text/plain")
				.setSubject(currentKind.title)
				.setText(currentKind.title + '\n' + getCurrentDateDisplay() + (shareUrl == null ? "" : ('\n' + shareUrl)) + "\n\n" + lContent.getText())
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
		final String date = yyyymmdd.get().format(currentDate);
		final DevotionArticle article = S.getDb().tryGetDevotion(currentKind.name, date);
		if (article == null || !article.getReadyToUse()) {
			willNeed(currentKind, date, true);
		}

		if (article == null) {
			Log.d(TAG, "rendering null article");
		} else {
			Log.d(TAG, "rendering article name=" + article.getKind().name + " date=" + article.getDate() + " readyToUse=" + article.getReadyToUse());
		}

		if (article != null && article.getReadyToUse()) {
			renderSucceeded = true;

			lContent.setText(article.getContent(verseClickListener), TextView.BufferType.SPANNABLE);
			lContent.setLinksClickable(true);
			lContent.setMovementMethod(LinkMovementMethod.getInstance());
		} else {
			renderSucceeded = false;

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
			Log.d(TAG, "Clicked verse reference inside devotion: " + reference);

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
						new MaterialDialog.Builder(DevotionActivity.this)
							.content(getString(R.string.alamat_tidak_sah_alamat, reference))
							.positiveText(R.string.ok)
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
					if (hasRange || verse_1 == 0) {
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

	synchronized void willNeed(final DevotionKind kind, final String date, final boolean prioritize) {
		final DevotionArticle article = kind.getArticle(date);
		devotionDownloader.add(article, prioritize);
	}

	public void prefetch(DevotionKind kind) {
		final Date today = new Date();

		// delete those older than 180 days!
		final int deleted = S.getDb().deleteDevotionsWithTouchTimeBefore(new Date(today.getTime() - 180 * 86400_000L));
		if (deleted > 0) {
			Log.d(TAG, "old devotions deleted: " + deleted);
		}

		for (int i = 0; i < kind.getPrefetchDays(); i++) {
			final String date = yyyymmdd.get().format(today);
			if (S.getDb().tryGetDevotion(kind.name, date) == null) {
				Log.d(TAG, "Prefetcher need to get " + kind + " " + date);
				willNeed(kind, date, false);
			}

			// go to the next day
			today.setTime(today.getTime() + 86400_000L);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQCODE_share && resultCode == RESULT_OK) {
			final ShareActivity.Result result = ShareActivity.obtainResult(data);
			if (result != null && result.chosenIntent != null) {
				final Intent chosenIntent = result.chosenIntent;
				if (U.equals(chosenIntent.getComponent().getPackageName(), "com.facebook.katana")) {
					final String shareUrl = currentKind.getShareUrl(yyyymmdd.get(), currentDate);
					if (shareUrl != null) {
						chosenIntent.putExtra(Intent.EXTRA_TEXT, shareUrl);
						startActivity(chosenIntent);
					} else {
						new MaterialDialog.Builder(this)
							.content(R.string.no_url_for_facebook)
							.positiveText(R.string.ok)
							.show();
					}
				} else {
					startActivity(chosenIntent);
				}
			}
		}
	}
}
