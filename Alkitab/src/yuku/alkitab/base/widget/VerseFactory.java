package yuku.alkitab.base.widget;

import android.annotation.SuppressLint;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import yuku.alkitab.R;
import yuku.alkitab.base.model.Version;

@SuppressLint("NewApi")
public class VerseFactory implements RemoteViewsService.RemoteViewsFactory {
	private Context context;
	private int appWidgetId;
	private int[] aris;
	private Version bibleVersion;

	public VerseFactory(final Context context, final Intent intent) {
		this.context = context;
		this.appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
	}

	@Override
	public void onCreate() {
		bibleVersion = DailyVerseAppWidget.getVersion(appWidgetId);
		aris = DailyVerseAppWidget.getVerse(appWidgetId);
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
		RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.item_app_widget);
		boolean showVerseNumber = false;
		if (aris.length > 1) {
			showVerseNumber = true;
		}
		row.setTextViewText(R.id.text1, DailyVerseAppWidget.getText(bibleVersion, aris[position], showVerseNumber));

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