package yuku.alkitab.base.appwidget

import android.content.Intent
import android.widget.RemoteViewsService

class DailyVerseAppWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = DailyVerseFactory(intent)
}
