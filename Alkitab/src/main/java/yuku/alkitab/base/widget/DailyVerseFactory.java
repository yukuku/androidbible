package yuku.alkitab.base.widget;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.text.style.SuperscriptSpan;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import yuku.alkitab.base.App;
import yuku.alkitab.base.U;
import yuku.alkitab.base.util.DailyVerseData;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Version;
import yuku.alkitab.util.Ari;

public class DailyVerseFactory implements RemoteViewsService.RemoteViewsFactory {
	static final String TAG = DailyVerseFactory.class.getSimpleName();

	private int appWidgetId;
	private DailyVerseData.SavedState savedState;
	private Version version;
	@Nullable private int[] aris;

	public DailyVerseFactory(final Intent intent) {
		this.appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
	}

	public static SpannableStringBuilder getText(Version version, int ari, boolean showVerseNumber) {
		final SpannableStringBuilder sb = new SpannableStringBuilder();

		String verseText = U.removeSpecialCodes(version.loadVerseText(ari));
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
		Log.d(TAG, "@@onCreate");
		reload();
	}

	@Override
	public void onDataSetChanged() {
		Log.d(TAG, "@@onDataSetChanged");
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
		return aris == null? 0: aris.length;
	}

	@Override
	public RemoteViews getViewAt(final int position) {
		final RemoteViews row = new RemoteViews(App.context.getPackageName(), R.layout.item_daily_verse_app_widget);

		assert aris != null; // getCount returns 0 if aris == null
		final boolean showVerseNumber = aris.length > 1;

		row.setTextViewText(R.id.text1, getText(version, aris[position], showVerseNumber));
		if (savedState.darkText) {
			row.setTextColor(R.id.text1, Color.BLACK);
		} else {
			row.setTextColor(R.id.text1, Color.WHITE);
		}
		row.setFloat(R.id.text1, "setTextSize", savedState.textSize);

		final Intent intent = new Intent();
		final Bundle extras = new Bundle();
		extras.putInt("ari", aris[position]);
		intent.putExtras(extras);
		row.setOnClickFillInIntent(R.id.text1, intent);

		return row;
	}

	@Override
	public RemoteViews getLoadingView() {
		return null;
	}

	@Override
	public int getViewTypeCount() {
		return 1;
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