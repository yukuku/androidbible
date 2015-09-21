package yuku.alkitab.base.sv;

import android.content.Intent;
import android.widget.RemoteViewsService;
import yuku.alkitab.base.widget.DailyVerseFactory;

public class DailyVerseAppWidgetService extends RemoteViewsService {
	@Override
	public RemoteViewsFactory onGetViewFactory(final Intent intent) {
		return new DailyVerseFactory(intent);
	}
}