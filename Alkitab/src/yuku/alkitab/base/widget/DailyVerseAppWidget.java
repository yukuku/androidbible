package yuku.alkitab.base.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.widget.RemoteViews;
import yuku.alkitab.R;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;

public class DailyVerseAppWidget extends AppWidgetProvider {

	@Override
	public void onUpdate(final Context context, final AppWidgetManager appWidgetManager, final int[] appWidgetIds) {
		ComponentName componentName = new ComponentName(context, DailyVerseAppWidget.class);
		RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.app_widget);
		String verseText = U.removeSpecialCodes(S.activeVersion.loadVerseText(ari1));
		remoteViews.setTextViewText(R.id.tAppWidgetVerse, verseText);
		appWidgetManager.updateAppWidget(componentName, remoteViews);
//		for (int i = 0; i < appWidgetIds.length; i++) {
//			RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.app_widget);
//			String verseText = U.removeSpecialCodes(S.activeVersion.loadVerseText(ari1));
//			remoteViews.setTextViewText(R.id.tAppWidgetVerse, verseText);
//			Log.d("APPPP WIDGET", "Id app widgetnya: " + appWidgetIds[i]);
//			appWidgetManager.updateAppWidget(appWidgetIds[i], remoteViews);
//		}
	}

	int ari1=2753296;
	int ari2=257;

}
