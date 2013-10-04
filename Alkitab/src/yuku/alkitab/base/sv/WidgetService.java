package yuku.alkitab.base.sv;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.widget.RemoteViewsService;
import yuku.alkitab.base.widget.VerseFactory;

@SuppressLint("NewApi")
public class WidgetService extends RemoteViewsService {

	@Override
	public RemoteViewsFactory onGetViewFactory(final Intent intent) {
		return (new VerseFactory(this.getApplicationContext(), intent));
	}
}