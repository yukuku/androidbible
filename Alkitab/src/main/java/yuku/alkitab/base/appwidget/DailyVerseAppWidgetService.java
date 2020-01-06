package yuku.alkitab.base.appwidget;

import android.content.Intent;
import android.widget.RemoteViewsService;

public class DailyVerseAppWidgetService extends RemoteViewsService {
	public static final String EXTRA_direction = "direction";

	@Override
	public RemoteViewsFactory onGetViewFactory(final Intent intent) {
		return new DailyVerseFactory(intent);
	}
}