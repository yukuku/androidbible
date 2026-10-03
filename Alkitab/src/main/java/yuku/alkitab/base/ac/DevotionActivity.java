package yuku.alkitab.base.ac;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ShareCompat;
import androidx.core.widget.NestedScrollView;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import yuku.afw.storage.Preferences;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.ac.base.BaseLeftDrawerActivity;
import yuku.alkitab.base.devotion.ArticleMeidA;
import yuku.alkitab.base.devotion.ArticleMorningEveningEnglish;
import yuku.alkitab.base.devotion.ArticleRenunganHarian;
import yuku.alkitab.base.devotion.ArticleRoc;
import yuku.alkitab.base.devotion.ArticleSantapanHarian;
import yuku.alkitab.base.devotion.DevotionArticle;
import yuku.alkitab.base.devotion.DevotionDownloader;
import yuku.alkitab.base.events.AppEvents;
import yuku.alkitab.base.settings.SettingsActivity;
import yuku.alkitab.base.storage.Prefkey;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.base.util.Background;
import yuku.alkitab.base.util.ClipboardUtil;
import yuku.alkitab.base.util.Jumper;
import yuku.alkitab.base.widget.CallbackSpan;
import yuku.alkitab.base.widget.LeftDrawer;
import yuku.alkitab.base.widget.MaterialDialogJavaHelper;
import yuku.alkitab.base.widget.TwofingerLinearLayout;
import yuku.alkitab.debug.R;
import yuku.alkitab.util.Ari;
import yuku.alkitabintegration.display.Launcher;

public class DevotionActivity extends BaseLeftDrawerActivity implements LeftDrawer.Devotion.Listener {
    static final String TAG = DevotionActivity.class.getSimpleName();

    public static final DevotionDownloader devotionDownloader = new DevotionDownloader();

    private static final ThreadLocal<SimpleDateFormat> yyyymmdd = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyyMMdd", Locale.US));

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
        getDownloader().retry(currentKind.name, getDateFormat().format(currentDate));
        display();
    }

    @Override
    public void cbKind_itemSelected(final DevotionKind kind) {
        currentKind = kind;
        Preferences.setString(Prefkey.devotion_last_kind_name, currentKind.name);

        display();
        startPrefetch(kind);
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
                return "https://www.sabda.org/publikasi/e-sh/print/?edisi=" + getDateFormat().format(date);
            }
        },
        MEID_A("meid-a", "Renungan Pagi", "Charles H. Spurgeon") {
            @Override
            public DevotionArticle getArticle(final String date) {
                return new ArticleMeidA(date);
            }

            @Override
            public String getShareUrl(final SimpleDateFormat format, final Date date) {
                return "https://alkitab.app/renunganpagi/" + getDateFormat().format(date).substring(4);
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
                return "https://www.sabda.org/publikasi/e-rh/print/?edisi=" + getDateFormat().format(date);
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
                return "https://www.ccel.org/ccel/spurgeon/morneve.d" + getDateFormat().format(date) + "am.html";
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

        @Nullable
        public abstract String getShareUrl(SimpleDateFormat format, Date date);

        public int getPrefetchDays() {
            return 15;
        }
    }

    public static final DevotionKind DEFAULT_DEVOTION_KIND = DevotionKind.SH;

    DrawerLayout drawerLayout;
    LeftDrawer.Devotion leftDrawer;

    TwofingerLinearLayout root;
    TextView lContent;
    NestedScrollView scrollContent;
    View downloadStatus;
    TextView lDownloadStatus;
    MaterialButton bRetry;
    CircularProgressIndicator downloadProgress;
    String displayedKey;
    String renderedKey;
    String renderedBody;

    boolean renderSucceeded = false;

    // currently shown
    DevotionKind currentKind;
    Date currentDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_devotion);

        drawerLayout = findViewById(R.id.drawerLayout);
        leftDrawer = findViewById(R.id.left_drawer);
        leftDrawer.configure(this, drawerLayout);
        applySafeAreaPadding(leftDrawer);

        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setupEdgeToEdgeDisplayWithoutBottomInset(toolbar);

        final ActionBar actionBar = getSupportActionBar();
        assert actionBar != null;
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp);

        root = findViewById(R.id.root);
        lContent = findViewById(R.id.lContent);
        scrollContent = findViewById(R.id.scrollContent);
        downloadStatus = findViewById(R.id.downloadStatus);
        lDownloadStatus = findViewById(R.id.lDownloadStatus);
        bRetry = findViewById(R.id.bRetry);
        downloadProgress = findViewById(R.id.downloadProgress);
        bRetry.setOnClickListener(v -> bReload_click());
        applyScrollPastBottomInset(scrollContent);

        root.setTwofingerEnabled(false);
        root.setListener(root_listener);

        final DevotionKind storedKind = DevotionKind.getByName(Preferences.getString(Prefkey.devotion_last_kind_name, DEFAULT_DEVOTION_KIND.name));

        final DevotionKind restoredKind = savedInstanceState == null ? null : DevotionKind.getByName(savedInstanceState.getString("devotionKind"));
        currentKind = restoredKind != null ? restoredKind : storedKind == null ? DEFAULT_DEVOTION_KIND : storedKind;
        currentDate = savedInstanceState == null ? new Date() : new Date(savedInstanceState.getLong("devotionDate", System.currentTimeMillis()));

        display();
        final DevotionKind prefetchKind = currentKind;
        startPrefetch(prefetchKind);

        AppEvents.observeWhileStarted(this, getDownloader().getChanges(), this::display);
    }

    @Override
    protected void onStart() {
        super.onStart();

        final S.CalculatedDimensions applied = App.services.uiDimensions.applied();

        { // apply background color, and clear window background to prevent overdraw
            getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            root.setBackgroundColor(applied.backgroundColor);
            scrollContent.setBackgroundColor(applied.backgroundColor);
        }

        lDownloadStatus.setTextColor(applied.fontColor);
        bRetry.setTextColor(applied.fontColor);
        bRetry.setStrokeColor(ColorStateList.valueOf(applied.fontColor));
        downloadProgress.setIndicatorColor(applied.fontColor);

        // text formats
        lContent.setTextColor(applied.fontColor);
        lContent.setTypeface(applied.fontFace, applied.fontBold);
        lContent.setTextSize(TypedValue.COMPLEX_UNIT_DIP, applied.fontSize2dp);
        lContent.setLineSpacing(0, applied.lineSpacingMult);

        final Rect padding = SettingsActivity.getPaddingBasedOnPreferences();
        lContent.setPadding(padding.left, padding.top, padding.right, padding.bottom);

        display();

        getWindow().getDecorView().setKeepScreenOn(Preferences.getBoolean(getString(R.string.pref_keepScreenOn_key), getResources().getBoolean(R.bool.pref_keepScreenOn_default)));
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString("devotionKind", currentKind.name);
        outState.putLong("devotionDate", currentDate.getTime());
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onPrepareOptionsMenu(@NonNull Menu menu) {
        menu.findItem(R.id.menuCopy).setEnabled(renderSucceeded);
        menu.findItem(R.id.menuShare).setEnabled(renderSucceeded);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
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
            ClipboardUtil.copyToClipboard(currentKind.title + "\n" + lContent.getText());

            Snackbar.make(root, R.string.renungan_sudah_disalin, Snackbar.LENGTH_SHORT).show();

            return true;
        } else if (itemId == R.id.menuShare) {
            final String shareUrl = currentKind.getShareUrl(getDateFormat(), currentDate);

            new ShareCompat.IntentBuilder(DevotionActivity.this)
                .setType("text/plain")
                .setSubject(currentKind.title)
                .setText(currentKind.title + '\n' + getCurrentDateDisplay() + (shareUrl == null ? "" : ('\n' + shareUrl)) + "\n\n" + lContent.getText())
                .setChooserTitle(getString(R.string.bagikan_renungan))
                .startChooser();

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    void display() {
        final String date = getDateFormat().format(currentDate);
        final DevotionArticle article = App.services.storage.getDb().tryGetDevotion(currentKind.name, date);
        final String key = currentKind.name + ":" + date;
        if (!key.equals(displayedKey)) {
            displayedKey = key;
            scrollContent.scrollTo(0, 0);
        }
        getDownloader().select(currentKind.name, date, article == null);
        final DevotionDownloader.State state = getDownloader().getState(currentKind.name, date);

        if (article == null) {
            AppLog.d(TAG, "rendering null article");
        } else {
            AppLog.d(TAG, "rendering article name=" + article.getKind().name + " date=" + article.getDate() + " readyToUse=" + article.getReadyToUse());
        }

        renderSucceeded = article != null && article.getReadyToUse();
        lContent.setVisibility(renderSucceeded ? View.VISIBLE : View.GONE);
        if (renderSucceeded && (!key.equals(renderedKey) || !Objects.equals(article.getBody(), renderedBody))) {
            renderedKey = key;
            renderedBody = article.getBody();
            lContent.setText(article.getContent(verseClickListener), TextView.BufferType.SPANNABLE);
            lContent.setLinksClickable(true);
            lContent.setMovementMethod(LinkMovementMethod.getInstance());
        } else if (!renderSucceeded) {
            renderedKey = null;
            renderedBody = null;
            lContent.setText("");
        }

        final boolean waiting = state == DevotionDownloader.State.QUEUED;
        final boolean downloading = state == DevotionDownloader.State.DOWNLOADING;
        final boolean failed = state == DevotionDownloader.State.FAILED;
        final boolean unavailable = !waiting && !downloading && (state == DevotionDownloader.State.UNAVAILABLE || (article != null && !article.getReadyToUse()));
        final boolean showStatus = waiting || downloading || failed || unavailable;
        downloadStatus.setVisibility(showStatus ? View.VISIBLE : View.GONE);
        downloadProgress.setVisibility(waiting || downloading ? View.VISIBLE : View.GONE);
        bRetry.setVisibility(failed || unavailable ? View.VISIBLE : View.GONE);
        if (waiting) lDownloadStatus.setText(R.string.devotion_download_queued);
        else if (downloading) lDownloadStatus.setText(R.string.devotion_downloading);
        else if (failed) lDownloadStatus.setText(R.string.devotion_download_failed);
        else if (unavailable) lDownloadStatus.setText(R.string.devotion_unavailable);
        invalidateOptionsMenu();

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
    }

    private String getCurrentDateDisplay() {
        return dayOfWeekName(currentDate) + ", " + DateFormat.getDateFormat(this).format(currentDate);
    }

    @Keep
    static class PatchTextExtraInfoJson {
        String type;
        String kind;
        String date;
    }

    final CallbackSpan.OnClickListener<String> verseClickListener = (widget, reference) -> {
        AppLog.d(TAG, "Clicked verse reference inside devotion: " + reference);

        if (reference.startsWith("patchtext:")) {
            final Uri uri = Uri.parse(reference);
            final String referenceUrl = uri.getQueryParameter("referenceUrl");

            final PatchTextExtraInfoJson extraInfo = new PatchTextExtraInfoJson();
            extraInfo.type = "devotion";
            extraInfo.kind = currentKind.name;
            extraInfo.date = getDateFormat().format(currentDate);
            startActivity(PatchTextActivity.createIntent(lContent.getText(), App.getDefaultGson().toJson(extraInfo), referenceUrl));
        } else {
            int ari;
            if (reference.startsWith("ari:")) {
                ari = Integer.parseInt(reference.substring(4));
                startActivity(Launcher.openAppAtBibleLocationWithVerseSelected(ari));

            } else { // we need to parse it manually by text
                final Jumper jumper = new Jumper(reference);
                if (!jumper.getParseSucceeded()) {
                    MaterialDialogJavaHelper.showOkDialog(DevotionActivity.this, getString(R.string.alamat_tidak_sah_alamat, reference));
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
    };

    private static final int[] WEEKDAY_NAMES_RESIDS = {R.string.hari_minggu, R.string.hari_senin, R.string.hari_selasa, R.string.hari_rabu, R.string.hari_kamis, R.string.hari_jumat, R.string.hari_sabtu};

    private String dayOfWeekName(Date date) {
        @SuppressWarnings("deprecation") int day = date.getDay();
        return getString(WEEKDAY_NAMES_RESIDS[day]);
    }

    protected DevotionDownloader getDownloader() {
        return devotionDownloader;
    }

    protected void startPrefetch(DevotionKind kind) {
        Background.run(() -> prefetch(kind));
    }

    private static void prefetch(DevotionKind kind) {
        final Date today = new Date();

        // delete those older than 180 days!
        final int deleted = App.services.storage.getDb().deleteDevotionsWithTouchTimeBefore(new Date(today.getTime() - 180 * 86400_000L));
        if (deleted > 0) {
            AppLog.d(TAG, "old devotions deleted: " + deleted);
        }

        for (int i = 0; i < kind.getPrefetchDays(); i++) {
            final String date = getDateFormat().format(today);
            if (App.services.storage.getDb().tryGetDevotion(kind.name, date) == null) {
                AppLog.d(TAG, "Prefetcher need to get " + kind + " " + date);
                devotionDownloader.addPrefetch(kind.name, date);
            }

            // go to the next day
            today.setTime(today.getTime() + 86400_000L);
        }
    }

    private static SimpleDateFormat getDateFormat() {
        return Objects.requireNonNull(yyyymmdd.get());
    }
}
