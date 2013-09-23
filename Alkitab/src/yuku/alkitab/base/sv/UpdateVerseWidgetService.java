package yuku.alkitab.base.sv;

import android.app.IntentService;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.UnderlineSpan;
import android.widget.RemoteViews;
import yuku.alkitab.R;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.ac.VersionsActivity;
import yuku.alkitab.base.config.AppConfig;
import yuku.alkitab.base.model.Version;
import yuku.alkitab.base.widget.DailyVerseAppWidget;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class UpdateVerseWidgetService extends IntentService {

	public UpdateVerseWidgetService() {
		super("UpdateVerseWidgetService");
	}

	@Override
	protected void onHandleIntent(final Intent intent) {
		buildUpdate();
	}

	private void buildUpdate() {

		AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this.getApplicationContext());
		ComponentName componentName = new ComponentName(this, DailyVerseAppWidget.class);

		String lastVersion = App.getPreferencesInstan().getString("edisiTerakhir", null);
		Version bibleVersion = loadLastVersion(lastVersion);
		if (bibleVersion == null) {
			bibleVersion = S.activeVersion;
		}
		Integer[] aris = getVerse();
		SpannableStringBuilder verseText = getText(bibleVersion, aris);

		RemoteViews remoteViews = new RemoteViews(this.getPackageName(), R.layout.app_widget);
		SimpleDateFormat format = new SimpleDateFormat("hh:mm:ss dd-MM-yyyy");
		remoteViews.setTextViewText(R.id.tAppWidgetVerse, verseText + " gen at " + format.format(new Date())); //TODO delete last strings. for debugging only

		Intent intent = new Intent(this, DailyVerseAppWidget.class);
		intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);

		intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetManager.getAppWidgetIds(componentName));

		PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
		remoteViews.setOnClickPendingIntent(R.id.bRandom, pendingIntent);

		Intent intentOpenBible = new Intent("yuku.alkitab.action.VIEW");
		intentOpenBible.putExtra("ari", aris[0]);
		PendingIntent pendingIntentOpenBible = PendingIntent.getActivity(this, 0, intentOpenBible, PendingIntent.FLAG_CANCEL_CURRENT);

		remoteViews.setOnClickPendingIntent(R.id.tAppWidgetVerse, pendingIntentOpenBible);


		appWidgetManager.updateAppWidget(componentName, remoteViews);

	}

	private SpannableStringBuilder getText(Version version, Integer[] aris) {
		SpannableStringBuilder sb = new SpannableStringBuilder();
		for (int ari : aris) {
			int length = sb.length();
			if (length != 0) {
				sb.append("\n");
			}
			String reference = version.reference(ari);
			sb.append(reference + " ");
			sb.setSpan(new UnderlineSpan(), length, length + reference.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			String verseText = U.removeSpecialCodes(version.loadVerseText(ari));
			sb.append(verseText);
		}

		return sb;
	}

	private Integer[] getVerse() {
		List<Integer[]> aris = new ArrayList<Integer[]>();

		aris.add(new Integer[] {2753296});
		aris.add(new Integer[] {257});
		aris.add(new Integer[] {2884375, 257});
		aris.add(new Integer[] {3146258,3146259});
		aris.add(new Integer[] {2952461, 2952461});
		aris.add(new Integer[] {2557449});
		aris.add(new Integer[] {2557473});
		aris.add(new Integer[] {3211787});
		aris.add(new Integer[] {1185537,1185538});
		aris.add(new Integer[] {1245702});
		aris.add(new Integer[] {527384});

		int random = (int) (Math.random() * aris.size());
		return aris.get(random);
	}

	private Version loadLastVersion(String lastVersion) {
		AppConfig c = AppConfig.get();

		if (lastVersion == null || VersionsActivity.MVersionInternal.getVersionInternalId().equals(lastVersion)) {
			return null;
		}

		// coba preset dulu!
		for (VersionsActivity.MVersionPreset preset: c.presets) { // 2. preset
			if (preset.getVersionId().equals(lastVersion)) {
				if (preset.hasDataFile()) {
					return preset.getVersion();
				} else {
					return null;
				}
			}
		}

		// masih belum cocok, mari kita cari di daftar yes
		List<VersionsActivity.MVersionYes> yeses = S.getDb().listAllVersions();
		for (VersionsActivity.MVersionYes yes: yeses) {
			if (yes.getVersionId().equals(lastVersion)) {
				if (yes.hasDataFile()) {
					return yes.getVersion();
				} else {
					return null;
				}
			}
		}
		return null;
	}
}
