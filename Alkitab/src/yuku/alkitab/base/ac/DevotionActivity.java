package yuku.alkitab.base.ac;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v4.app.ShareCompat;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TextView.BufferType;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import yuku.afw.storage.Preferences;
import yuku.alkitab.R;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.base.BaseActivity;
import yuku.alkitab.base.devotion.ArticleRenunganHarian;
import yuku.alkitab.base.devotion.ArticleSantapanHarian;
import yuku.alkitab.base.devotion.Downloader;
import yuku.alkitab.base.devotion.Downloader.OnStatusDonlotListener;
import yuku.alkitab.base.devotion.IArticle;
import yuku.alkitab.base.model.Ari;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.Jumper;
import yuku.alkitab.base.widget.CallbackSpan;
import yuku.alkitab.base.widget.DevotionSelectPopup;
import yuku.alkitab.base.widget.DevotionSelectPopup.DevotionSelectPopupListener;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

public class DevotionActivity extends BaseActivity implements OnStatusDonlotListener {
	public static final String TAG = DevotionActivity.class.getSimpleName();

	public static final String EXTRA_ari = "ari"; //$NON-NLS-1$
	private static final int REQCODE_share = 1;

	public static class Result {
		public int ari;
	}
	
	public static Result obtainResult(Intent data) {
		if (data == null) return null;
		Result res = new Result();
		res.ari = data.getIntExtra(EXTRA_ari, 0);
		return res;
	}
	
	static ThreadLocal<SimpleDateFormat> date_format = new ThreadLocal<SimpleDateFormat>() {
		@Override protected SimpleDateFormat initialValue() {
			return new SimpleDateFormat("yyyyMMdd", Locale.US); //$NON-NLS-1$
		}
	};
	
	public static final String[] AVAILABLE_NAMES = {
		"sh", "rh",  //$NON-NLS-1$//$NON-NLS-2$
	};
	public static final String DEFAULT = "sh"; //$NON-NLS-1$
	private static final String[] AVAILABLE_TITLES = {
		"Santapan Harian", "Renungan Harian", //$NON-NLS-1$ //$NON-NLS-2$
	};

	TextView lContent;
	ScrollView scrollContent;
	TextView lStatus;
	
	DevotionSelectPopup popup;
	
	boolean renderSucceeded = false;
	long lastTryToDisplay = 0;
	Animation fadeOutAnim;
	
	// currently shown
	String currentName;
	Date currentDate;

	static class DisplayRepeater extends Handler {
		final WeakReference<DevotionActivity> ac;
		
		public DisplayRepeater(DevotionActivity activity) {
			ac = new WeakReference<DevotionActivity>(activity);
		}
		
		@Override public void handleMessage(Message msg) {
			DevotionActivity activity = ac.get();
			if (activity == null) return;
			
			{
				long kini = SystemClock.currentThreadTimeMillis();
				if (kini - activity.lastTryToDisplay < 500) {
					return; // ANEH. Terlalu cepat.
				}
				
				activity.lastTryToDisplay = kini;
			}
			
			activity.goTo(true, 0);
			
			if (!activity.renderSucceeded) {
				activity.displayRepeater.sendEmptyMessageDelayed(0, 12000);
			}
		}
	}

	Handler displayRepeater = new DisplayRepeater(this);
	
	static class DownloadStatusDisplayer extends Handler {
		private WeakReference<DevotionActivity> ac;

		public DownloadStatusDisplayer(DevotionActivity ac) {
			this.ac = new WeakReference<DevotionActivity>(ac);
		}
		
		@Override public void handleMessage(Message msg) {
			DevotionActivity ac = this.ac.get();
			if (ac == null) return;
			
			String s = (String) msg.obj;
			if (s != null) {
				ac.lStatus.setText(s);
				ac.lStatus.setVisibility(View.VISIBLE);
				ac.lStatus.startAnimation(ac.fadeOutAnim);
			}
		}
	}
	
	Handler downloadStatusDisplayer = new DownloadStatusDisplayer(this);

	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_devotion);
		
		fadeOutAnim = AnimationUtils.loadAnimation(this, R.anim.fade_out);

		lContent = (TextView) findViewById(R.id.lContent);
		scrollContent = (ScrollView) findViewById(R.id.scrollContent);
		lStatus = (TextView) findViewById(R.id.lStatus);
		
		scrollContent.setBackgroundColor(S.applied.backgroundColor);
		
		popup = new DevotionSelectPopup(this);
		popup.setDevotionSelectListener(popup_listener);
		
		if (S.temporary.devotion_date == null) S.temporary.devotion_date = new Date();
		if (S.temporary.devotion_name == null) S.temporary.devotion_name = DEFAULT;
		
		currentName = S.temporary.devotion_name;
		currentDate = S.temporary.devotion_date;
		
		// Workaround for crashes due to html tags in the title
		// We remove all rows that contain '<' in the judul
		if (Preferences.getBoolean(Prefkey.patch_devotionSlippedHtmlTags, false) == false) {
			int deleted = S.getDb().deleteDevotionsWithLessThanInTitle();
			Log.d(TAG, "patch_devotionSlippedHtmlTags: deleted " + deleted);
			Preferences.setBoolean(Prefkey.patch_devotionSlippedHtmlTags, true);
		}
		
		new Prefetcher().start();
		
		{ // betulin ui update
			Downloader td = S.downloader;
			if (td != null) {
				td.setListener(this);
			}
		}
		
		display(S.temporary.devotion_scroll);
	}
	
	@Override protected void onStart() {
		super.onStart();
		
		if (Preferences.getBoolean(getString(R.string.pref_nyalakanTerusLayar_key), getResources().getBoolean(R.bool.pref_nyalakanTerusLayar_default))) {
			lContent.setKeepScreenOn(true);
		}
	}
	
	@Override protected void onDestroy() {
		super.onDestroy();
		
		S.temporary.devotion_name = currentName;
		S.temporary.devotion_date = currentDate;
		S.temporary.devotion_scroll = scrollContent.getScrollY();
	}
		
	private void buildMenu(Menu menu) {
		menu.clear();
		getSupportMenuInflater().inflate(R.menu.activity_devotion, menu);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		buildMenu(menu);
		
		return true;
	}
	
	@Override public boolean onPrepareOptionsMenu(Menu menu) {
		if (menu != null) {
			buildMenu(menu);
		}
		
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int itemId = item.getItemId();
		if (itemId == R.id.menuChangeDate) {
			View anchor = findViewById(R.id.menuChangeDate);
			popup.show(anchor);
			
			return true;
		} else if (itemId == R.id.menuCopy) {
			String toCopy = getSupportActionBar().getTitle() + "\n" + lContent.getText();
			U.copyToClipboard(toCopy);
			
			Toast.makeText(this, R.string.renungan_sudah_disalin, Toast.LENGTH_SHORT).show();
			
			return true;
		} else if (itemId == R.id.menuShare) {
			Intent intent = ShareCompat.IntentBuilder.from(DevotionActivity.this)
			.setType("text/plain") //$NON-NLS-1$
			.setSubject(getSupportActionBar().getTitle().toString())
			.setText(getSupportActionBar().getTitle().toString() + '\n' + lContent.getText())
			.getIntent();
			startActivityForResult(ShareActivity.createIntent(intent, getString(R.string.bagikan_renungan)), REQCODE_share);
			
			return true;
		} else if (itemId == R.id.menuRedownload) {
			willNeed(this.currentName, date_format.get().format(currentDate), true);
			
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	DevotionSelectPopupListener popup_listener = new DevotionSelectPopupListener() {
		@Override public void onDismiss(DevotionSelectPopup popup) {
		}
		
		@Override public void onButtonClick(DevotionSelectPopup popup, View v) {
			int id = v.getId();
			if (id == R.id.bPrev) {
				currentDate.setTime(currentDate.getTime() - 3600*24*1000);
				display(0);
			} else if (id == R.id.bNext) {
				currentDate.setTime(currentDate.getTime() + 3600*24*1000);
				display(0);
			} else if (id == R.id.bChange) {
				int index = 0;
				
				for (int i = 0; i < AVAILABLE_NAMES.length; i++) {
					if (AVAILABLE_NAMES[i].equals(currentName)) {
						index = i;
						break;
					}
				}
				
				index = (index + 1) % AVAILABLE_NAMES.length;
				currentName = AVAILABLE_NAMES[index];
				display(0);
			}
		}
	};

	
	void display(int skrol) {
		displayRepeater.removeMessages(0);
		
		goTo(true, skrol);
	}

	void goTo(boolean prioritize, int scroll) {
		String tgl = date_format.get().format(currentDate);
		IArticle artikel = S.getDb().tryGetDevotion(currentName, tgl);
		if (artikel == null || !artikel.getReadyToUse()) {
			willNeed(currentName, tgl, prioritize);
			render(artikel, scroll);
			
			displayRepeater.sendEmptyMessageDelayed(0, 3000);
		} else {
			Log.d(TAG, "sudah siap tampil, kita syuh yang tersisa dari pengulang tampil"); //$NON-NLS-1$
			displayRepeater.removeMessages(0);
			
			render(artikel, scroll);
		}
	}

	CallbackSpan.OnClickListener verseClickListener = new CallbackSpan.OnClickListener() {
		@Override
		public void onClick(View widget, Object _data) {
			String reference = (String) _data;
			
			Log.d(TAG, "Clicked verse reference inside devotion: " + reference); //$NON-NLS-1$

			Jumper jumper = new Jumper(reference);
			if (! jumper.getParseSucceeded()) {
				new AlertDialog.Builder(DevotionActivity.this)
				.setMessage(getString(R.string.alamat_tidak_sah_alamat, reference))
				.setPositiveButton(R.string.ok, null)
				.show();
				return;
			}
			
			// TODO support english devotions too
			String[] bookNames = getResources().getStringArray(R.array.nama_kitab_standar_in);
			int[] bookIds = new int[bookNames.length];
			for (int i = 0, len = bookNames.length; i < len; i++) {
				bookIds[i] = i;
			}

			int bookId = jumper.getBookId(bookNames, bookIds);
			int chapter_1 = jumper.getChapter();
			int verse_1 = jumper.getVerse();
			
			Intent data = new Intent();
			data.putExtra(EXTRA_ari, Ari.encode(bookId, chapter_1, verse_1));
			setResult(RESULT_OK, data);
			finish();
		}
	};

	private void render(IArticle article, final int skrol) {
		if (article == null) {
			Log.d(TAG, "rendering null article"); //$NON-NLS-1$
		} else {
			Log.d(TAG, "rendering article name=" + article.getName() + " date=" + article.getDate() + " readyToUse=" + article.getReadyToUse()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
		
		if (article != null && article.getReadyToUse()) {
			renderSucceeded = true;
			
			Spanned header = Html.fromHtml(article.getHeaderHtml());
			SpannableStringBuilder ss = new SpannableStringBuilder(header);
			
			if (article.getName().equals("sh")) { //$NON-NLS-1$
				SpannableStringBuilder title = new SpannableStringBuilder(Html.fromHtml("<h3>" + article.getTitle() + "</h3>")); //$NON-NLS-1$ //$NON-NLS-2$
				title.setSpan(new CallbackSpan(article.getTitle(), verseClickListener), 0, title.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				
				ss.append(title);
			} else if (article.getName().equals("rh")) { //$NON-NLS-1$
				// cari "Bacaan Setahun : " dst
				{
					String s = header.toString();
					Matcher m = Pattern.compile("Bacaan\\s+Setahun\\s*:\\s*(.*?)\\s*$", Pattern.MULTILINE).matcher(s); //$NON-NLS-1$
					while (m.find()) {
						// di dalem daftar ayat, kita cari lagi, harusnya sih dipisahkan titik-koma.
						String t = m.group(1);
						Matcher n = Pattern.compile("\\s*(\\S.*?)\\s*(;|$)", Pattern.MULTILINE).matcher(t); //$NON-NLS-1$
						
						while (n.find()) {
							Log.d(TAG, "Ketemu salah satu bacaan setahun: #" + n.group(1) + "#"); //$NON-NLS-1$ //$NON-NLS-2$
							CallbackSpan span = new CallbackSpan(n.group(1), verseClickListener);
							ss.setSpan(span, m.start(1) + n.start(1), m.start(1) + n.end(1), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
						}
					}
				}
				
				ss.append(Html.fromHtml("<br/><h3>" + article.getTitle() + "</h3><br/>"));  //$NON-NLS-1$//$NON-NLS-2$
			}
			
			int offsetBeforeBody = ss.length();
			
			Spanned contentAndCopyright = Html.fromHtml(article.getBodyHtml() + "<br/><br/>" + article.getCopyrightHtml()); //$NON-NLS-1$
			ss.append(contentAndCopyright);
			
			// cari "Bacaan : " dst dan pasang link
			{
				String s = contentAndCopyright.toString();
				Matcher m = Pattern.compile("Bacaan\\s*:\\s*(.*?)\\s*$", Pattern.MULTILINE).matcher(s); //$NON-NLS-1$
				while (m.find()) {
					Log.d(TAG, "Ketemu \"Bacaan : \": #" + m.group(1) + "#"); //$NON-NLS-1$ //$NON-NLS-2$
					CallbackSpan span = new CallbackSpan(m.group(1), verseClickListener);
					ss.setSpan(span, offsetBeforeBody + m.start(1), offsetBeforeBody + m.end(1), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				}
			}
			
			lContent.setText(ss, BufferType.SPANNABLE);
			lContent.setLinksClickable(true);
			lContent.setMovementMethod(LinkMovementMethod.getInstance());
			
			// text formats
			lContent.setTextColor(S.applied.fontColor);
			lContent.setBackgroundColor(S.applied.backgroundColor);
			lContent.setTypeface(S.applied.fontFace, S.applied.fontBold);
			lContent.setTextSize(TypedValue.COMPLEX_UNIT_DIP, S.applied.fontSize2dp);
			lContent.setLineSpacing(0, S.applied.lineSpacingMult);
			
			if (skrol != 0) {
				scrollContent.post(new Runnable() {
					@Override public void run() {
						scrollContent.scrollTo(0, skrol);
					}
				});
			}
		} else {
			renderSucceeded  = false;
			
			if (article == null) {
				lContent.setText(R.string.belum_tersedia_menunggu_pengambilan_data_lewat_internet_pastikan_ada);
			} else { // berarti belum siap pakai
				lContent.setText(R.string.belum_tersedia_mungkin_tanggal_yang_diminta_belum_disiapkan);
			}
		}
		
		String title = ""; //$NON-NLS-1$
		for (int i = 0; i < AVAILABLE_NAMES.length; i++) {
			if (AVAILABLE_NAMES[i].equals(currentName)) {
				title = AVAILABLE_TITLES[i];
			}
		}
		
		{ // widget texts
			String dateDisplay = namaHari(currentDate) + ", " + DateFormat.getDateFormat(this).format(currentDate);  //$NON-NLS-1$
			
			// action bar
			getSupportActionBar().setTitle(title);
			getSupportActionBar().setSubtitle(dateDisplay);
			
			// popup texts
			popup.setDevotionName(title);
			popup.setDevotionDate(dateDisplay);
		}
	}

	private static final int[] WEEKDAY_NAMES_RESIDS = {R.string.hari_minggu, R.string.hari_senin, R.string.hari_selasa, R.string.hari_rabu, R.string.hari_kamis, R.string.hari_jumat, R.string.hari_sabtu};

	private String namaHari(Date date) {
		@SuppressWarnings("deprecation") int day = date.getDay();
		return getString(WEEKDAY_NAMES_RESIDS[day]);
	}

	synchronized void willNeed(String name, String date, boolean prioritize) {
		if (S.downloader == null) {
			S.downloader = new Downloader(this, this);
			S.downloader.start();
		}

		IArticle article = null;
		if (name.equals("rh")) { //$NON-NLS-1$
			article = new ArticleRenunganHarian(date);
		} else if (name.equals("sh")) { //$NON-NLS-1$
			article = new ArticleSantapanHarian(date);
		}

		if (article != null) {
			boolean added = S.downloader.add(article, prioritize);
			if (added) S.downloader.interruptWhenIdle();
		}
	}
	
	static boolean prefetcherRunning = false;
	
	class Prefetcher extends Thread {
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
				for (int i = 0; i < 31; i++) { // 31 hari ke depan
					String date = date_format.get().format(today);
					if (S.getDb().tryGetDevotion(currentName, date) == null) {
						Log.d(TAG, "Prefetcher need to get " + date); //$NON-NLS-1$
						willNeed(currentName, date, false);
						
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

	@Override public void onDownloadStatus(final String s) {
		Message msg = Message.obtain(downloadStatusDisplayer);
		msg.obj = s;
		msg.sendToTarget();
	}
	
	@Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQCODE_share) {
			if (resultCode == RESULT_OK) {
				ShareActivity.Result result = ShareActivity.obtainResult(data);
				if (result != null && result.chosenIntent != null) {
					Intent chosenIntent = result.chosenIntent;
					if (U.equals(chosenIntent.getComponent().getPackageName(), "com.facebook.katana")) { //$NON-NLS-1$
						if (U.equals(currentName, "sh")) chosenIntent.putExtra(Intent.EXTRA_TEXT, "http://www.sabda.org/publikasi/e-sh/print/?edisi=" + date_format.get().format(currentDate)); // change text to url //$NON-NLS-1$ //$NON-NLS-2$
						if (U.equals(currentName, "rh")) chosenIntent.putExtra(Intent.EXTRA_TEXT, "http://www.sabda.org/publikasi/e-rh/print/?edisi=" + date_format.get().format(currentDate)); // change text to url //$NON-NLS-1$ //$NON-NLS-2$
					}
					startActivity(chosenIntent);
				}
			}
		}
	}
}
