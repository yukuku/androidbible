package yuku.alkitab.base.widget;

import android.annotation.SuppressLint;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import yuku.alkitab.base.br.DailyVerseAppWidgetReceiver;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Version;

@SuppressLint("NewApi")
public class DailyVerseFactory implements RemoteViewsService.RemoteViewsFactory {
	private Context context;
	private int appWidgetId;
	private int[] aris;
	private Version bibleVersion;
	private boolean optionDarkText;
	private float optionTextSize;

	public DailyVerseFactory(final Context context, final Intent intent) {
		this.context = context;
		this.appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
	}

	@Override
	public void onCreate() {
		bibleVersion = DailyVerseAppWidgetReceiver.getVersion(appWidgetId);
		aris = DailyVerseAppWidgetReceiver.getVerse(appWidgetId);
		optionDarkText = DailyVerseAppWidgetReceiver.getOptionDarkText(appWidgetId);
		optionTextSize = DailyVerseAppWidgetReceiver.getOptionTextSize(appWidgetId);
	}

	@Override
	public void onDataSetChanged() {}

	@Override
	public void onDestroy() {}

	@Override
	public int getCount() {
		return aris.length;
	}

	@Override
	public RemoteViews getViewAt(final int position) {
		RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.item_daily_verse_app_widget);
		boolean showVerseNumber = false;
		if (aris.length > 1) {
			showVerseNumber = true;
		}
		row.setTextViewText(R.id.text1, DailyVerseAppWidgetReceiver.getText(bibleVersion, aris[position], showVerseNumber));
		if (optionDarkText) {
			row.setTextColor(R.id.text1, 0xff000000);
		}
		row.setFloat(R.id.text1, "setTextSize", optionTextSize);

		Intent intent = new Intent();
		Bundle extras = new Bundle();
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
		return true;
	}

}