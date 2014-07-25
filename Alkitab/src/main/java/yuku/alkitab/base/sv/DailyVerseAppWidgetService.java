package yuku.alkitab.base.sv;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.widget.RemoteViewsService;
import yuku.alkitab.base.widget.DailyVerseFactory;

@SuppressLint("NewApi")
public class DailyVerseAppWidgetService extends RemoteViewsService {

	@Override
	public RemoteViewsFactory onGetViewFactory(final Intent intent) {
		return new DailyVerseFactory(this.getApplicationContext(), intent);
	}
}