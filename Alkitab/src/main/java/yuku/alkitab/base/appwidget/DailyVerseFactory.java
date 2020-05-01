package yuku.alkitab.base.appwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.text.style.SuperscriptSpan;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import androidx.annotation.Nullable;
import yuku.alkitab.base.App;
import yuku.alkitab.base.util.AppLog;
import yuku.alkitab.base.util.FormattedVerseText;
import yuku.alkitab.base.widget.Localized;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Version;
import yuku.alkitab.util.Ari;
import yuku.alkitabintegration.display.Launcher;

public class DailyVerseFactory implements RemoteViewsService.RemoteViewsFactory {
	static final String TAG = DailyVerseFactory.class.getSimpleName();

	private int appWidgetId;
	private int direction;
	private DailyVerseData.SavedState savedState;
	private Version version;
	@Nullable private int[] aris;

	public DailyVerseFactory(final Intent intent) {
		this.appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
		this.direction = intent.getIntExtra(DailyVerseAppWidgetService.EXTRA_direction, 0);
	}

	private static SpannableStringBuilder getText(Version version, int ari, boolean showVerseNumber) {
		final SpannableStringBuilder sb = new SpannableStringBuilder();

		String verseText = FormattedVerseText.removeSpecialCodes(version.loadVerseText(ari));
		if (verseText == null) {
			verseText = Localized.string(R.string.generic_verse_not_available_in_this_version);
		}

		if (showVerseNumber) {
			final String verseNumber = "" + Ari.toVerse(ari);
			sb.append(verseNumber).append(" ").append(verseText);
			sb.setSpan(new RelativeSizeSpan(0.7f), 0, verseNumber.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
			sb.setSpan(new SuperscriptSpan(), 0, verseNumber.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		} else {
			sb.append(verseText);
		}
		return sb;
	}

	@Override
	public void onCreate() {
		AppLog.d(TAG, "@@onCreate");
		reload();
	}

	@Override
	public void onDataSetChanged() {
		AppLog.d(TAG, "@@onDataSetChanged");
		reload();
	}

	private void reload() {
		savedState = DailyVerseData.loadSavedState(appWidgetId);
		version = DailyVerseData.getVersion(savedState.versionId);
		aris = DailyVerseData.getAris(appWidgetId, savedState, version, 1);
	}

	@Override
	public void onDestroy() {
	}

	@Override
	public int getCount() {
		return 1 + (aris == null ? 0 : aris.length);
	}

	@Override
	public RemoteViews getViewAt(final int position) {
		if (position == 0) {
			return getHeaderView();
		} else {
			return getVerseViewAt(position - 1);
		}
	}

	RemoteViews getHeaderView() {
		final RemoteViews rv = new RemoteViews(App.context.getPackageName(), R.layout.appwidget_daily_verse_item_header);

		// get saved state
		final DailyVerseData.SavedState savedState = DailyVerseData.loadSavedState(appWidgetId);

		if (savedState.darkText) {
			rv.setTextColor(R.id.tReference, Color.BLACK);
			rv.setImageViewResource(R.id.bPrev, R.drawable.ic_navigate_before_black_24dp);
			rv.setImageViewResource(R.id.bNext, R.drawable.ic_navigate_next_black_24dp);
		} else {
			rv.setTextColor(R.id.tReference, Color.WHITE);
		}

		//--App logo button
		rv.setViewVisibility(R.id.imgLogo, savedState.hideAppIcon ? View.GONE : View.VISIBLE);
		rv.setOnClickFillInIntent(R.id.imgLogo, new Intent().putExtra("app_widget_action", "open_app"));

		final int[] appWidgetIds = {appWidgetId};

		//--Prev button
		{
			final Intent intent = new Intent();
			intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
			intent.putExtra("app_widget_action", "update_widget");
			intent.putExtra("app_widget_button", 1);

			rv.setOnClickFillInIntent(R.id.bPrev, intent);
		}

		//--Next button
		{
			final Intent intent = new Intent();
			intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
			intent.putExtra("app_widget_action", "update_widget");
			intent.putExtra("app_widget_button", 2);

			rv.setOnClickFillInIntent(R.id.bNext, intent);
		}

		rv.setFloat(R.id.tReference, "setTextSize", savedState.textSize);

		final Version version = DailyVerseData.getVersion(savedState.versionId);
		final int[] aris = DailyVerseData.getAris(appWidgetId, savedState, version, direction);
		if (aris != null) {
			rv.setTextViewText(R.id.tReference, version.referenceWithVerseCount(aris[0], aris.length));

			final Intent viewVerseIntent = Launcher.openAppAtBibleLocation(aris[0]);
			rv.setOnClickPendingIntent(R.id.tReference, PendingIntent.getActivity(App.context, appWidgetId + 10000, viewVerseIntent, PendingIntent.FLAG_UPDATE_CURRENT));
		} else {
			rv.setTextViewText(R.id.tReference, Localized.string(R.string.generic_verse_not_available_in_this_version));
		}

		return rv;
	}

	RemoteViews getVerseViewAt(final int position) {
		final RemoteViews row = new RemoteViews(App.context.getPackageName(), R.layout.appwidget_daily_verse_item_verse);
		assert aris != null;
		final boolean showVerseNumber = aris.length > 1;

		// prevent crash: sometimes position is out of range for the aris array.
		if (position < 0 || position >= aris.length) {
			row.setTextViewText(R.id.text1, "");
			row.setOnClickFillInIntent(R.id.text1, new Intent());

		} else {
			final int ari = aris[position];
			row.setTextViewText(R.id.text1, getText(version, ari, showVerseNumber));
			row.setTextColor(R.id.text1, savedState.darkText ? Color.BLACK : Color.WHITE);
			row.setFloat(R.id.text1, "setTextSize", savedState.textSize);
			row.setOnClickFillInIntent(R.id.text1, new Intent().putExtra("app_widget_action", "open_verse").putExtra("ari", ari));
		}

		return row;
	}

	@Override
	public RemoteViews getLoadingView() {
		return null;
	}

	@Override
	public int getViewTypeCount() {
		return 2;
	}

	@Override
	public long getItemId(final int position) {
		return position;
	}

	@Override
	public boolean hasStableIds() {
		return false;
	}

}